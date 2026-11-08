package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A compact, dependency-free QR Code encoder (byte mode, error-correction level M, versions 1-10).
 *
 * <p>It produces a square boolean matrix where {@code true} is a dark module. Only the features needed
 * to encode a short LAN URL are included, but they are the full QR specification: finder/timing/
 * alignment patterns, Reed-Solomon over GF(256), all eight masks with penalty scoring, and format/
 * version information.
 */
final class QrCode {
    private QrCode() { }

    /** Error-correction level M encoded as the two-bit format value 00. */
    private static final int EC_LEVEL = 0x00;

    // Per version (index 0 = version 1): {total data codewords, ec codewords per block,
    // group1 blocks, group1 data/block, group2 blocks, group2 data/block}.
    private static final int[][] BLOCKS = {
            {16, 10, 1, 16, 0, 0},
            {28, 16, 1, 28, 0, 0},
            {44, 26, 1, 44, 0, 0},
            {64, 18, 2, 32, 0, 0},
            {86, 24, 2, 43, 0, 0},
            {128, 16, 4, 32, 0, 0},
            {156, 18, 4, 37, 0, 0},
            {194, 22, 2, 37, 2, 38},
            {232, 22, 3, 37, 2, 38},
            {274, 26, 4, 41, 2, 42},
    };

    // Alignment-pattern centre coordinates per version (empty for version 1).
    private static final int[][] ALIGNMENT = {
            {},
            {6, 18},
            {6, 22},
            {6, 26},
            {6, 30},
            {6, 34},
            {6, 22, 38},
            {6, 24, 42},
            {6, 26, 46},
            {6, 28, 50},
    };

    // GF(256) tables, primitive polynomial 0x11D.
    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = x;
            LOG[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) x ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    /** Max payload bytes in byte mode for a version: data bits minus the mode (4) and count (8/16) overhead. */
    private static int byteCapacity(int version) {
        int countBits = version + 1 <= 9 ? 8 : 16;
        return (BLOCKS[version][0] * 8 - 4 - countBits) / 8;
    }

    /** Encode text as UTF-8 and return the dark-module matrix for the smallest suitable version. */
    static boolean[][] encode(String text) {
        byte[] data = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int version = -1;
        for (int v = 0; v < BLOCKS.length; v++) {
            if (data.length <= byteCapacity(v)) {
                version = v;
                break;
            }
        }
        if (version < 0) throw new IllegalArgumentException("text too long for versions 1-10");
        int size = 17 + 4 * (version + 1);

        List<Integer> codewords = buildCodewords(version, data);
        List<Integer> ecc = addErrorCorrection(version, codewords);

        // Interleave data and EC codewords, then lay them onto the matrix.
        boolean[][] matrix = new boolean[size][size];
        boolean[][] reserved = new boolean[size][size];
        placeFixedPatterns(version, matrix, reserved);
        placeCodewords(size, interleave(version, codewords, ecc), matrix, reserved);

        // Pick the mask with the lowest penalty, then write format (and version) information.
        boolean[][] best = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = applyMask(matrix, reserved, mask);
            writeFormat(version, mask, candidate, reserved);
            if (version + 1 >= 7) writeVersion(version, candidate, reserved);
            int penalty = penalty(candidate);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                best = candidate;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ data encoding

    private static List<Integer> buildCodewords(int version, byte[] data) {
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4); // byte mode indicator
        int countBits = version + 1 <= 9 ? 8 : 16;
        bits.append(data.length, countBits);
        for (byte b : data) bits.append(b & 0xFF, 8);

        int totalDataBits = BLOCKS[version][0] * 8;
        bits.append(0, Math.min(4, totalDataBits - bits.length)); // terminator
        while (bits.length % 8 != 0) bits.append(0, 1); // pad to byte boundary

        List<Integer> words = new ArrayList<>();
        for (int i = 0; i < bits.length / 8; i++) words.add(bits.byteAt(i));
        for (int pad = 0; words.size() < BLOCKS[version][0]; pad++) {
            words.add((pad & 1) == 0 ? 0xEC : 0x11);
        }
        return words;
    }

    private static List<Integer> addErrorCorrection(int version, List<Integer> data) {
        int[] spec = BLOCKS[version];
        int ecPerBlock = spec[1];
        int[] generator = rsPolynomial(ecPerBlock);

        List<Integer> allEcc = new ArrayList<>();
        int[][] blocks = splitBlocks(version, data);
        for (int[] block : blocks) {
            int[] buffer = new int[block.length + ecPerBlock];
            System.arraycopy(block, 0, buffer, 0, block.length);
            for (int i = 0; i < block.length; i++) {
                int factor = buffer[i];
                if (factor != 0) {
                    for (int j = 0; j < generator.length; j++) {
                        buffer[i + j] ^= EXP[LOG[factor] + LOG[generator[j]]];
                    }
                }
            }
            for (int i = 0; i < ecPerBlock; i++) allEcc.add(buffer[block.length + i]);
        }
        return allEcc;
    }

    private static int[][] splitBlocks(int version, List<Integer> data) {
        int[] spec = BLOCKS[version];
        int g1 = spec[2], g1Len = spec[3], g2 = spec[4], g2Len = spec[5];
        int[][] blocks = new int[g1 + g2][];
        int offset = 0;
        for (int i = 0; i < g1; i++) {
            blocks[i] = slice(data, offset, g1Len);
            offset += g1Len;
        }
        for (int i = 0; i < g2; i++) {
            blocks[g1 + i] = slice(data, offset, g2Len);
            offset += g2Len;
        }
        return blocks;
    }

    private static int[] slice(List<Integer> data, int offset, int length) {
        int[] out = new int[length];
        for (int i = 0; i < length; i++) out[i] = data.get(offset + i);
        return out;
    }

    /** Interleave data blocks then append EC codewords, matching the QR block-interleave rule. */
    private static List<Integer> interleave(int version, List<Integer> data, List<Integer> ecc) {
        int[] spec = BLOCKS[version];
        int g1 = spec[2], g1Len = spec[3], g2 = spec[4], g2Len = spec[5];
        int maxLen = Math.max(g1Len, g2Len);
        int[][] blocks = splitBlocks(version, data);

        List<Integer> out = new ArrayList<>();
        for (int col = 0; col < maxLen; col++) {
            for (int b = 0; b < blocks.length; b++) {
                if (col < blocks[b].length) out.add(blocks[b][col]);
            }
        }
        // EC blocks all have equal length, interleave them column by column.
        int ecPerBlock = spec[1];
        for (int col = 0; col < ecPerBlock; col++) {
            for (int b = 0; b < g1 + g2; b++) out.add(ecc.get(b * ecPerBlock + col));
        }
        return out;
    }

    /** Reed-Solomon generator polynomial (x - alpha^0)...(x - alpha^(degree-1)), built by repeated multiplication. */
    private static int[] rsPolynomial(int degree) {
        int[] poly = {1};
        for (int i = 0; i < degree; i++) {
            int[] next = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= poly[j]; // times the x term (coefficient 1)
                next[j + 1] ^= EXP[LOG[poly[j]] + i]; // times alpha^i
            }
            poly = next;
        }
        return poly;
    }

    // ------------------------------------------------------------------ matrix placement

    private static void placeFixedPatterns(int version, boolean[][] m, boolean[][] reserved) {
        int n = m.length;
        // Finder patterns (three corners) with their separators.
        placeFinder(m, reserved, 0, 0);
        placeFinder(m, reserved, n - 7, 0);
        placeFinder(m, reserved, 0, n - 7);

        // Timing patterns.
        for (int i = 8; i < n - 8; i++) {
            boolean dark = (i & 1) == 0;
            set(m, reserved, 6, i, dark);
            set(m, reserved, i, 6, dark);
        }
        // Dark module.
        set(m, reserved, n - 8, 8, true);

        // Alignment patterns.
        int[] centers = ALIGNMENT[version];
        for (int row : centers) {
            for (int col : centers) {
                if (reserved[row][col]) continue;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        boolean edge = Math.max(Math.abs(dr), Math.abs(dc)) == 1;
                        set(m, reserved, row + dr, col + dc, !edge);
                    }
                }
            }
        }

        // Reserve format areas (filled later); coordinates mirror writeFormat exactly.
        for (int i = 0; i < 15; i++) {
            int r;
            if (i < 6) r = i;
            else if (i < 8) r = i + 1;
            else r = n - 15 + i;
            reserve(reserved, r, 8);
        }
        for (int i = 0; i < 15; i++) {
            int c;
            if (i < 8) c = n - 1 - i;
            else if (i == 8) c = 7;
            else c = 14 - i;
            reserve(reserved, 8, c);
        }
        if (version + 1 >= 7) {
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 3; j++) {
                    reserve(reserved, i, n - 11 + j);
                    reserve(reserved, n - 11 + j, i);
                }
            }
        }
    }

    private static void placeFinder(boolean[][] m, boolean[][] reserved, int r, int c) {
        for (int dr = -1; dr <= 7; dr++) {
            for (int dc = -1; dc <= 7; dc++) {
                int rr = r + dr, cc = c + dc;
                if (rr < 0 || cc < 0 || rr >= m.length || cc >= m.length) continue;
                boolean inside = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
                boolean ring = inside && (dr == 0 || dr == 6 || dc == 0 || dc == 6);
                boolean core = inside && dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
                set(m, reserved, rr, cc, ring || core);
            }
        }
    }

    private static void placeCodewords(int n, List<Integer> words, boolean[][] m, boolean[][] reserved) {
        BitBuffer bits = new BitBuffer();
        for (int w : words) bits.append(w, 8);
        int bit = 0;
        boolean upward = true;
        int col = n - 1;
        while (col > 0) {
            if (col == 6) col--; // skip timing column
            for (int step = 0; step < n; step++) {
                int row = upward ? n - 1 - step : step;
                for (int which = 0; which < 2; which++) {
                    int c = col - which;
                    if (reserved[row][c]) continue;
                    if (bit < bits.length) {
                        m[row][c] = bits.bitAt(bit);
                        bit++;
                    }
                }
            }
            col -= 2;
            upward = !upward;
        }
    }

    private static boolean[][] applyMask(boolean[][] base, boolean[][] reserved, int mask) {
        int n = base.length;
        boolean[][] out = new boolean[n][n];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                out[r][c] = base[r][c];
                if (!reserved[r][c] && maskBit(mask, r, c)) out[r][c] = !out[r][c];
            }
        }
        return out;
    }

    private static boolean maskBit(int mask, int r, int c) {
        switch (mask) {
            case 0: return ((r + c) & 1) == 0;
            case 1: return (r & 1) == 0;
            case 2: return c % 3 == 0;
            case 3: return (r + c) % 3 == 0;
            case 4: return ((r / 2 + c / 3) & 1) == 0;
            case 5: return ((r * c) & 1) + ((r * c) % 3) == 0;
            case 6: return (((r * c) & 1) + ((r * c) % 3)) % 2 == 0;
            default: return (((r + c) & 1) + ((r * c) % 3)) % 2 == 0;
        }
    }

    private static void writeFormat(int version, int mask, boolean[][] m, boolean[][] reserved) {
        int data = (EC_LEVEL << 3) | mask;
        // Polynomial long division against G15 (0x537) to get the 10-bit BCH remainder.
        int d = data << 10;
        for (int i = 4; i >= 0; i--) {
            if ((d & (1 << (i + 10))) != 0) d ^= 0x537 << i;
        }
        int bits = ((data << 10) | (d & 0x3FF)) ^ 0x5412;
        int n = m.length;

        // Vertical strip (column 8).
        for (int i = 0; i < 15; i++) {
            boolean dark = ((bits >> i) & 1) != 0;
            int r;
            if (i < 6) r = i;
            else if (i < 8) r = i + 1;
            else r = n - 15 + i;
            set(m, reserved, r, 8, dark);
        }
        // Horizontal strip (row 8).
        for (int i = 0; i < 15; i++) {
            boolean dark = ((bits >> i) & 1) != 0;
            int c;
            if (i < 8) c = n - 1 - i;
            else if (i == 8) c = 7;
            else c = 14 - i;
            set(m, reserved, 8, c, dark);
        }
    }

    private static void writeVersion(int version, boolean[][] m, boolean[][] reserved) {
        int data = version + 1;
        int rem = data;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem & 1 << 5) != 0 ? 0x1F25 : 0);
        int bits = (data << 12) | (rem & 0xFFF);
        int n = m.length;
        for (int i = 0; i < 18; i++) {
            boolean dark = ((bits >> i) & 1) != 0;
            int a = i / 3, b = i % 3;
            set(m, reserved, a, n - 11 + b, dark);
            set(m, reserved, n - 11 + b, a, dark);
        }
    }

    // ------------------------------------------------------------------ penalty

    private static int penalty(boolean[][] m) {
        int n = m.length;
        int score = 0;
        for (int r = 0; r < n; r++) {
            int runColor = -1, run = 0;
            for (int c = 0; c < n; c++) {
                int v = m[r][c] ? 1 : 0;
                if (v == runColor) {
                    run++;
                    if (run == 5) score += 3;
                    else if (run > 5) score++;
                } else {
                    runColor = v;
                    run = 1;
                }
            }
        }
        for (int c = 0; c < n; c++) {
            int runColor = -1, run = 0;
            for (int r = 0; r < n; r++) {
                int v = m[r][c] ? 1 : 0;
                if (v == runColor) {
                    run++;
                    if (run == 5) score += 3;
                    else if (run > 5) score++;
                } else {
                    runColor = v;
                    run = 1;
                }
            }
        }
        for (int r = 0; r < n - 1; r++) {
            for (int c = 0; c < n - 1; c++) {
                if (m[r][c] == m[r + 1][c] && m[r][c] == m[r][c + 1] && m[r][c] == m[r + 1][c + 1]) score += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : m) for (boolean v : row) if (v) dark++;
        int pct = dark * 100 / (n * n);
        score += (Math.abs(pct - 50) / 5) * 10;
        return score;
    }

    // ------------------------------------------------------------------ helpers

    private static void set(boolean[][] m, boolean[][] reserved, int r, int c, boolean dark) {
        m[r][c] = dark;
        reserved[r][c] = true;
    }

    private static void reserve(boolean[][] reserved, int r, int c) {
        reserved[r][c] = true;
    }

    /** Mutable bit stream with the append/access helpers the encoder needs. */
    private static final class BitBuffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int length;

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                bytes.write(((value >> i) & 1));
                length++;
            }
        }

        int length() { return length; }
        int bitAt(int i) { return bytes.toByteArray()[i]; }
        int byteAt(int i) {
            int v = 0;
            byte[] a = bytes.toByteArray();
            for (int b = 0; b < 8; b++) v |= a[i * 8 + b] << (7 - b);
            return v;
        }
    }
}
