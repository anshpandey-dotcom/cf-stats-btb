package com.anshpandey.stopit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ChartView extends View {

    interface Formatter {
        String format(long value);
    }

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;

    private String[] labels = new String[0];
    private long[] values = new long[0];
    private Formatter formatter = String::valueOf;
    private int selected = -1;

    public ChartView(Context context) {
        this(context, null);
    }

    public ChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        bar.setColor(context.getColor(R.color.accent));
        axis.setColor(0x33FFFFFF);
        axis.setStrokeWidth(density);
        label.setColor(context.getColor(R.color.muted));
        label.setTextSize(11 * getResources().getDisplayMetrics().scaledDensity);
        label.setTextAlign(Paint.Align.CENTER);
        value.setColor(context.getColor(R.color.text));
        value.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        value.setFakeBoldText(true);
        value.setTextAlign(Paint.Align.CENTER);
    }

    void setData(String[] labels, long[] values, Formatter formatter) {
        this.labels = labels;
        this.values = values;
        this.formatter = formatter;
        selected = values.length - 1;
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                description.append(", ");
            }
            description.append(labels[i]).append(' ').append(formatter.format(values[i]));
        }
        setContentDescription(description);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, (int) (170 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int count = values.length;
        if (count == 0) {
            return;
        }
        float top = 26 * density;
        float bottom = getHeight() - 24 * density;
        float slot = (float) getWidth() / count;
        float barWidth = Math.min(slot * 0.46f, 28 * density);
        float radius = 4 * density;
        long max = 1;
        for (long v : values) {
            max = Math.max(max, v);
        }
        canvas.drawLine(0, bottom, getWidth(), bottom, axis);
        for (int i = 0; i < count; i++) {
            float center = slot * i + slot / 2f;
            float height = (bottom - top) * values[i] / max;
            bar.setAlpha(i == selected ? 255 : 150);
            if (height > 0) {
                height = Math.max(height, 2 * density);
                rect.set(center - barWidth / 2f, bottom - height, center + barWidth / 2f, bottom);
                float r = Math.min(radius, height);
                path.reset();
                path.addRoundRect(rect, new float[]{r, r, r, r, 0, 0, 0, 0}, Path.Direction.CW);
                canvas.drawPath(path, bar);
            }
            canvas.drawText(labels[i], center, getHeight() - 6 * density, label);
            if (i == selected) {
                float y = Math.max(value.getTextSize(), bottom - height - 8 * density);
                canvas.drawText(formatter.format(values[i]), clamp(center), y, value);
            }
        }
    }

    private float clamp(float center) {
        float half = 34 * density;
        return Math.max(half, Math.min(getWidth() - half, center));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (values.length == 0) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            int index = (int) (event.getX() / ((float) getWidth() / values.length));
            index = Math.max(0, Math.min(values.length - 1, index));
            if (index != selected) {
                selected = index;
                invalidate();
            }
            getParent().requestDisallowInterceptTouchEvent(event.getAction() == MotionEvent.ACTION_MOVE);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
