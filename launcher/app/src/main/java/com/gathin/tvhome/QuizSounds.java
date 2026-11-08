package com.gathin.tvhome;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Short cartoon sound effects for the quiz, synthesised at runtime so the APK needs no audio
 * files. Each effect is a tiny note sequence rendered on a background thread and released at once.
 */
public final class QuizSounds {

    private static final int SAMPLE_RATE = 44100;

    private QuizSounds() {
    }

    /** A bright rising two-note chime for a right answer. */
    public static void playCorrect() {
        play(new double[]{523.25, 659.25}, new int[]{90, 120}, 0.5f);
    }

    /** A soft, low falling pair for a wrong answer; kept gentle so it never startles a child. */
    public static void playWrong() {
        play(new double[]{220.0, 196.0}, new int[]{120, 160}, 0.38f);
    }

    /** A high bell-like "ding" when a reward star pops in. */
    public static void playStar() {
        play(new double[]{1318.51}, new int[]{160}, 0.46f);
    }

    private static void play(final double[] freqs, final int[] ms, final float gain) {
        Thread worker = new Thread(() -> {
            int total = 0;
            for (int d : ms) total += d;
            int count = SAMPLE_RATE * total / 1000;
            short[] buffer = new short[count];
            int pos = 0;
            for (int k = 0; k < freqs.length; k++) {
                int len = SAMPLE_RATE * ms[k] / 1000;
                int attack = Math.max(1, SAMPLE_RATE / 200);   // ~5 ms fade in
                int release = Math.max(1, SAMPLE_RATE / 25);    // ~40 ms fade out
                for (int i = 0; i < len; i++) {
                    double env = 1.0;
                    if (i < attack) env = (double) i / attack;
                    if (i > len - release) env = Math.max(0.0, (double) (len - i) / release);
                    double wave = Math.sin(2.0 * Math.PI * freqs[k] * i / SAMPLE_RATE);
                    wave += 0.3 * Math.sin(2.0 * Math.PI * freqs[k] * 2.0 * i / SAMPLE_RATE);
                    double value = wave * gain * env;
                    if (pos + i < count) {
                        buffer[pos + i] = (short) Math.max(Short.MIN_VALUE,
                                Math.min(Short.MAX_VALUE, value * Short.MAX_VALUE));
                    }
                }
                pos += len;
            }

            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(count * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(buffer, 0, count);
            track.play();
            try {
                Thread.sleep(total + 40L);
            } catch (InterruptedException ignored) {
            }
            track.release();
        }, "quiz-sound");
        worker.start();
    }
}
