package com.example.myapplication.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.audiofx.Visualizer;
import android.util.AttributeSet;
import android.view.View;

public class AudioWaveformView extends View {
    private byte[] waveform;
    private Paint paint = new Paint();

    public AudioWaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(2f);
    }

    public void updateWaveform(byte[] waveform) {
        this.waveform = waveform;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (waveform == null) return;
        float width = getWidth();
        float height = getHeight();
        float centerY = height / 2f;
        float xIncrement = width / (float) waveform.length;
        float x = 0;
        for (int i = 0; i < waveform.length; i++) {
            float y = centerY + ((byte) (waveform[i] + 128)) * (centerY / 128);
            canvas.drawLine(x, centerY, x, y, paint);
            x += xIncrement;
        }
    }
}