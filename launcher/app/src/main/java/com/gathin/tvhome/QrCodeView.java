package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * Renders the dark-module matrix produced by {@link QrCode} as a crisp black-and-white square with a
 * white quiet zone. No bitmap or third-party library is involved; each module is drawn as a rectangle.
 */
public class QrCodeView extends View {
    private static final int QUIET_ZONE = 4;

    private boolean[][] modules;
    private final Paint darkPaint = new Paint();

    public QrCodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        darkPaint.setColor(Color.BLACK);
        darkPaint.setStyle(Paint.Style.FILL);
        darkPaint.setAntiAlias(false);
    }

    void setModules(boolean[][] value) {
        modules = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        canvas.drawColor(Color.WHITE);
        if (modules == null || modules.length == 0) return;

        int n = modules.length;
        int span = n + QUIET_ZONE * 2;
        float cell = Math.min(width, height) / (float) span;
        float codeSize = cell * n;
        float originX = (width - codeSize) / 2f;
        float originY = (height - codeSize) / 2f;

        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                if (modules[r][c]) {
                    float left = originX + c * cell;
                    float top = originY + r * cell;
                    canvas.drawRect(left, top, left + cell, top + cell, darkPaint);
                }
            }
        }
    }
}
