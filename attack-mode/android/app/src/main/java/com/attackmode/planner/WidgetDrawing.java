package com.attackmode.planner;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.TextPaint;

import org.json.JSONObject;

final class WidgetDrawing {

    private static final String[] LETTERS = {"S", "M", "T", "W", "T", "F", "S"};

    private WidgetDrawing() {}

    static int paper(boolean dark) {
        return dark ? 0xFF0A0A0A : 0xFFFFFDF8;
    }

    static int ink(boolean dark) {
        return dark ? 0xFFF4F1EA : 0xFF191816;
    }

    static int muted(boolean dark) {
        return dark ? 0xFF9D978B : 0xFF756E61;
    }

    static int faint(boolean dark) {
        return dark ? 0xFF1C1B18 : 0xFFE8E1D3;
    }

    static int habitColor(JSONObject habit) {
        try {
            return Color.parseColor(habit.optString("color", "#e4572e"));
        } catch (IllegalArgumentException error) {
            return 0xFFE4572E;
        }
    }

    private static Paint fill(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private static TextPaint text(int color, float size) {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        return paint;
    }

    private static void frame(Canvas canvas, int width, int height, boolean dark) {
        float radius = Math.min(width, height) * 0.14f;
        RectF outer = new RectF(0, 0, width, height);
        canvas.drawRoundRect(outer, radius, radius, fill(0xFF2A2A30));
        float edge = Math.max(1.5f, width / 220f);
        RectF inner = new RectF(edge, edge, width - edge, height - edge);
        canvas.drawRoundRect(inner, radius - edge, radius - edge, fill(0xFF0E0E11));
    }

    private static String fit(String value, TextPaint paint, float width) {
        return TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString();
    }

    static void checkmark(Canvas canvas, float left, float top, float size, String state, int color, boolean dark) {
        Paint border = fill(dark ? 0xFF3A372F : 0xFF191816);
        float edge = Math.max(2f, size / 14f);
        boolean checked = "y".equals(state);
        canvas.drawRect(left, top, left + size, top + size, checked ? fill(color) : border);
        if (!checked) {
            canvas.drawRect(left + edge, top + edge, left + size - edge, top + size - edge, fill(paper(dark)));
        }

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(3f, size / 8f));
        stroke.setStrokeCap(Paint.Cap.SQUARE);
        Path path = new Path();
        if (checked) {
            stroke.setColor(0xFFFFFFFF);
            path.moveTo(left + size * 0.24f, top + size * 0.54f);
            path.lineTo(left + size * 0.43f, top + size * 0.72f);
            path.lineTo(left + size * 0.78f, top + size * 0.30f);
        } else if ("n".equals(state)) {
            stroke.setColor(muted(dark));
            path.moveTo(left + size * 0.28f, top + size * 0.28f);
            path.lineTo(left + size * 0.72f, top + size * 0.72f);
            path.moveTo(left + size * 0.72f, top + size * 0.28f);
            path.lineTo(left + size * 0.28f, top + size * 0.72f);
        } else if ("s".equals(state)) {
            stroke.setColor(muted(dark));
            path.moveTo(left + size * 0.28f, top + size * 0.5f);
            path.lineTo(left + size * 0.72f, top + size * 0.5f);
        }
        canvas.drawPath(path, stroke);
    }

    static void ringTile(Canvas canvas, RectF tile, String state, int doneColor, String label) {
        boolean done = "y".equals(state);
        float size = tile.width();
        float radius = size * 0.24f;
        canvas.drawRoundRect(tile, radius, radius, fill(done ? doneColor : 0xFF48484D));

        float cx = tile.centerX();
        float cy = label == null ? tile.centerY() : tile.top + size * 0.43f;
        float ringRadius = size * (label == null ? 0.27f : 0.24f);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, size * 0.05f));
        ring.setColor(done ? 0xFFFFFFFF : 0xB3FFFFFF);
        canvas.drawCircle(cx, cy, ringRadius, ring);

        Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(Math.max(2.5f, size * 0.055f));
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setStrokeJoin(Paint.Join.ROUND);
        mark.setColor(done ? 0xFFFFFFFF : 0xB3FFFFFF);
        Path path = new Path();
        float k = ringRadius * 0.42f;
        if (done) {
            path.moveTo(cx - k * 1.0f, cy + k * 0.05f);
            path.lineTo(cx - k * 0.25f, cy + k * 0.8f);
            path.lineTo(cx + k * 1.1f, cy - k * 0.75f);
        } else {
            path.moveTo(cx - k * 0.8f, cy - k * 0.8f);
            path.lineTo(cx + k * 0.8f, cy + k * 0.8f);
            path.moveTo(cx + k * 0.8f, cy - k * 0.8f);
            path.lineTo(cx - k * 0.8f, cy + k * 0.8f);
        }
        canvas.drawPath(path, mark);

        if (label != null) {
            TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(size * 0.125f);
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            String shown = TextUtils.ellipsize(label, text, size * 0.82f, TextUtils.TruncateAt.END).toString();
            canvas.drawText(shown, cx - text.measureText(shown) / 2f, tile.bottom - size * 0.1f, text);
        }
    }

    static Bitmap check(int width, int height, JSONObject snapshot, JSONObject habit, String state) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float size = Math.min(width, height);
        float pad = size * 0.03f;
        float left = (width - size) / 2f + pad;
        float top = (height - size) / 2f + pad;
        RectF tile = new RectF(left, top, left + size - pad * 2, top + size - pad * 2);
        ringTile(canvas, tile, state, habitColor(habit), habit.optString("name"));
        return bitmap;
    }

    static Bitmap history(int width, int height, JSONObject snapshot, JSONObject habit, JSONObject checks) {
        boolean dark = HabitData.isDark(snapshot);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        frame(canvas, width, height, dark);

        float pad = Math.min(width, height) * 0.08f;
        TextPaint title = text(ink(dark), Math.max(14f, height * 0.12f));
        canvas.drawText(fit(habit.optString("name"), title, width - pad * 2), pad, pad + title.getTextSize(), title);

        float top = pad * 1.6f + title.getTextSize();
        float gridHeight = height - top - pad;
        float cell = gridHeight / 7f;
        int columns = Math.max(1, (int) ((width - pad * 2) / cell));
        float gap = Math.max(2f, cell * 0.12f);
        long today = HabitData.epochDay(HabitData.today());
        int weekStart = snapshot.optInt("weekStart", 1);
        int offset = (HabitData.weekday(today) - weekStart + 7) % 7;
        long lastColumnStart = today - offset;
        int color = habitColor(habit);

        for (int column = 0; column < columns; column++) {
            long columnStart = lastColumnStart - (long) (columns - 1 - column) * 7;
            for (int row = 0; row < 7; row++) {
                long day = columnStart + row;
                if (day > today) {
                    continue;
                }
                String state = checks.optString(HabitData.dayKey(day), "");
                int tint = state.equals("y") ? color : faint(dark);
                float x = pad + column * cell;
                float y = top + row * cell;
                canvas.drawRect(x + gap / 2, y + gap / 2, x + cell - gap / 2, y + cell - gap / 2, fill(tint));
            }
        }
        return bitmap;
    }

    private static void bars(
        Canvas canvas, float left, float top, float width, float height,
        double[] values, double max, int color, boolean dark, String[] labels
    ) {
        int count = values.length;
        float slot = width / count;
        float gap = Math.max(2f, slot * 0.18f);
        float labelHeight = labels == null ? 0 : height * 0.16f;
        TextPaint label = text(muted(dark), Math.max(9f, labelHeight * 0.7f));
        float chart = height - labelHeight;

        for (int index = 0; index < count; index++) {
            float x = left + index * slot;
            canvas.drawRect(x + gap / 2, top, x + slot - gap / 2, top + chart, fill(faint(dark)));
            float ratio = max <= 0 ? 0f : (float) Math.min(1.0, values[index] / max);
            float barHeight = Math.max(chart * 0.03f, chart * ratio);
            canvas.drawRect(x + gap / 2, top + chart - barHeight, x + slot - gap / 2, top + chart, fill(color));
            if (labels != null && labels[index] != null) {
                canvas.drawText(labels[index], x + gap / 2, top + height - 2, label);
            }
        }
    }

    static Bitmap score(int width, int height, JSONObject snapshot, JSONObject habit, JSONObject checks) {
        boolean dark = HabitData.isDark(snapshot);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        frame(canvas, width, height, dark);

        float pad = Math.min(width, height) * 0.08f;
        long today = HabitData.epochDay(HabitData.today());
        double[] weekly = new double[12];
        double[] daily = HabitData.scores(habit, checks, today, 12 * 7);
        for (int index = 0; index < 12; index++) {
            weekly[index] = daily[index * 7 + 6];
        }
        int percent = (int) Math.round(weekly[11] * 100);

        TextPaint title = text(ink(dark), Math.max(14f, height * 0.12f));
        TextPaint big = text(habitColor(habit), Math.max(18f, height * 0.16f));
        String value = percent + "%";
        float valueWidth = big.measureText(value);
        canvas.drawText(value, width - pad - valueWidth, pad + big.getTextSize(), big);
        canvas.drawText(
            fit(habit.optString("name"), title, width - pad * 3 - valueWidth), pad, pad + title.getTextSize(), title
        );

        float top = pad * 1.8f + big.getTextSize();
        bars(canvas, pad, top, width - pad * 2, height - top - pad, weekly, 1.0, habitColor(habit), dark, null);
        return bitmap;
    }

    static Bitmap frequency(int width, int height, JSONObject snapshot, JSONObject habit, JSONObject checks) {
        boolean dark = HabitData.isDark(snapshot);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        frame(canvas, width, height, dark);

        float pad = Math.min(width, height) * 0.08f;
        long today = HabitData.epochDay(HabitData.today());
        int[] counts = HabitData.weekdayCounts(checks, today);
        int weekStart = snapshot.optInt("weekStart", 1);
        double[] values = new double[7];
        String[] labels = new String[7];
        double max = 1;
        for (int index = 0; index < 7; index++) {
            int day = (weekStart + index) % 7;
            values[index] = counts[day];
            labels[index] = LETTERS[day];
            max = Math.max(max, counts[day]);
        }

        TextPaint title = text(ink(dark), Math.max(14f, height * 0.12f));
        canvas.drawText(fit(habit.optString("name"), title, width - pad * 2), pad, pad + title.getTextSize(), title);
        float top = pad * 1.6f + title.getTextSize();
        bars(canvas, pad, top, width - pad * 2, height - top - pad, values, max, habitColor(habit), dark, labels);
        return bitmap;
    }

    static Bitmap rowCheck(int size, JSONObject snapshot, JSONObject habit, String state) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float pad = size * 0.04f;
        ringTile(canvas, new RectF(pad, pad, size - pad, size - pad), state, habitColor(habit), null);
        return bitmap;
    }

    static Bitmap message(int width, int height, boolean dark, String line) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        frame(canvas, width, height, dark);
        TextPaint paint = text(muted(dark), Math.max(14f, Math.min(width, height) * 0.1f));
        String shown = fit(line, paint, width * 0.9f);
        canvas.drawText(shown, (width - paint.measureText(shown)) / 2f, height / 2f, paint);
        return bitmap;
    }
}
