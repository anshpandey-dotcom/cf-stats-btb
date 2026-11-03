package com.anshpandey.cleanaf.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public final class WaveformView extends View {
    public interface SeekListener {
        void onSeek(float fraction);
    }

    private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rest = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float[] peaks = new float[0];
    private float[] bars = new float[0];
    private float position;
    private SeekListener listener;

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        played.setColor(color(androidx.appcompat.R.attr.colorPrimary));
        rest.setColor(color(com.google.android.material.R.attr.colorOutlineVariant));
    }

    private int color(int attr) {
        TypedValue v = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }

    public void setListener(SeekListener l) {
        listener = l;
    }

    public void setPeaks(float[] p) {
        peaks = p == null ? new float[0] : p;
        rebuild();
        invalidate();
    }

    public void setPosition(float fraction) {
        position = fraction;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        rebuild();
    }

    private void rebuild() {
        float d = getResources().getDisplayMetrics().density;
        int count = Math.max(1, (int) (getWidth() / (5 * d)));
        bars = new float[count];
        if (peaks.length == 0 || getWidth() == 0) return;
        float max = 1e-6f;
        for (int i = 0; i < count; i++) {
            int a = (int) ((long) i * peaks.length / count);
            int b = Math.max(a + 1, (int) ((long) (i + 1) * peaks.length / count));
            float m = 0;
            for (int k = a; k < b && k < peaks.length; k++) m = Math.max(m, peaks[k]);
            bars[i] = m;
            max = Math.max(max, m);
        }
        for (int i = 0; i < count; i++) bars[i] = (float) Math.pow(bars[i] / max, 0.7);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bars.length == 0) return;
        float d = getResources().getDisplayMetrics().density;
        float step = (float) getWidth() / bars.length;
        float bw = Math.max(d, step * 0.55f);
        float h = getHeight();
        float px = position * getWidth();
        for (int i = 0; i < bars.length; i++) {
            float x = i * step + (step - bw) / 2;
            float bh = Math.max(2 * d, bars[i] * h * 0.92f);
            rect.set(x, (h - bh) / 2, x + bw, (h + bh) / 2);
            c.drawRoundRect(rect, bw / 2, bw / 2, x < px ? played : rest);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float f = Math.max(0f, Math.min(1f, e.getX() / Math.max(1, getWidth())));
            position = f;
            invalidate();
            if (listener != null) listener.onSeek(f);
            return true;
        }
        return e.getAction() == MotionEvent.ACTION_UP || super.onTouchEvent(e);
    }
}
