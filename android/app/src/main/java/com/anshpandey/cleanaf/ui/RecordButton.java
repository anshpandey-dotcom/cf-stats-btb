package com.anshpandey.cleanaf.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.anshpandey.cleanaf.R;

public final class RecordButton extends View {
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float t;
    private ValueAnimator anim;

    public RecordButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedValue v = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, v, true);
        outline.setColor(v.data);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeWidth(getResources().getDisplayMetrics().density * 4);
        fill.setColor(ContextCompat.getColor(context, R.color.record_red));
        setClickable(true);
        setFocusable(true);
    }

    public void setRecording(boolean on) {
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(t, on ? 1f : 0f).setDuration(220);
        anim.addUpdateListener(a -> {
            t = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - outline.getStrokeWidth();
        c.drawCircle(cx, cy, r, outline);
        float big = r * 0.82f;
        float small = r * 0.42f;
        float half = big + (small - big) * t;
        float corner = half * (1f - 0.7f * t);
        rect.set(cx - half, cy - half, cx + half, cy + half);
        c.drawRoundRect(rect, corner, corner, fill);
    }
}
