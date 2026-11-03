package com.anshpandey.cleanaf.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.anshpandey.cleanaf.R;

public final class LevelView extends View {
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mic;
    private float level;
    private float shown;
    private boolean live;

    public LevelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        base.setColor(color(com.google.android.material.R.attr.colorSurfaceContainerHigh));
        ring.setColor(color(androidx.appcompat.R.attr.colorPrimary));
        mic = ContextCompat.getDrawable(context, R.drawable.ic_mic).mutate();
        mic.setTint(color(com.google.android.material.R.attr.colorOnSurface));
    }

    private int color(int attr) {
        TypedValue v = new TypedValue();
        getContext().getTheme().resolveAttribute(attr, v, true);
        return v.data;
    }

    public void setLevel(float value, boolean recording) {
        level = Math.max(0f, Math.min(1f, value));
        live = recording;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        shown += (level - shown) * 0.35f;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy);
        float inner = r * 0.62f;
        ring.setAlpha(live ? 70 : 35);
        c.drawCircle(cx, cy, inner + (r - inner) * shown, ring);
        c.drawCircle(cx, cy, inner, base);
        int s = (int) (inner * 0.6f);
        mic.setBounds((int) cx - s / 2, (int) cy - s / 2, (int) cx + s / 2, (int) cy + s / 2);
        mic.draw(c);
        if (Math.abs(level - shown) > 0.005f) postInvalidateOnAnimation();
    }
}
