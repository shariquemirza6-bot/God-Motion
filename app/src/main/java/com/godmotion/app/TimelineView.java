package com.godmotion.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class TimelineView extends View {

    public interface OnScrubListener { void onScrub(long ms); }

    static class Clip {
        String name; int color; long start, end; int row;
        Clip(String n, int c, long s, long e, int r) { name = n; color = c; start = s; end = e; row = r; }
    }

    private static final int NONE = 0, MOVE = 1, TRIM_L = 2, TRIM_R = 3, SCRUB = 4, SCROLL = 5;
    private static final int MAX_ROWS = 5;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d, pxPerSec, rulerH, layerH, edgeZone;
    private final List<Clip> clips = new ArrayList<>();
    private Clip selected = null;
    private int mode = NONE;
    private float offsetX = 0, lastX;
    private long playheadMs = 0, durationMs = 30000;
    private OnScrubListener listener;

    public TimelineView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        pxPerSec = 80 * d;
        rulerH = 32 * d;
        layerH = 52 * d;
        edgeZone = 24 * d;
        paint.setTextSize(11 * d);
        clips.add(new Clip("Video", 0xFF3B82F6, 0, 30000, 0));
    }

    public void setOnScrubListener(OnScrubListener l) { listener = l; }

    public void setDuration(long ms) {
        durationMs = Math.max(ms, 1000);
        clips.get(0).end = durationMs;
        invalidate();
    }

    public void setPlayheadMs(long ms) { playheadMs = ms; invalidate(); }

    public void addClip(String name, int color) {
        int row = Math.min(rowCount(), MAX_ROWS - 1);
        Clip c = new Clip(name, color, playheadMs, playheadMs + 3000, row);
        clips.add(c);
        selected = c;
        invalidate();
    }

    public void splitAtPlayhead() {
        if (selected == null) return;
        if (playheadMs <= selected.start + 200 || playheadMs >= selected.end - 200) return;
        Clip right = new Clip(selected.name, selected.color, playheadMs, selected.end, selected.row);
        selected.end = playheadMs;
        clips.add(right);
        selected = right;
        invalidate();
    }

    public void deleteSelected() {
        if (selected == null || selected == clips.get(0)) return;
        clips.remove(selected);
        selected = null;
        invalidate();
    }

    private int rowCount() {
        int m = 0;
        for (Clip c : clips) m = Math.max(m, c.row);
        return m + 1;
    }

    private float xOf(long ms) { return ms / 1000f * pxPerSec - offsetX; }

    private long maxEnd() {
        long m = durationMs;
        for (Clip c : clips) m = Math.max(m, c.end);
        return m;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0xFF121212);
        int rows = rowCount();

        for (int i = 0; i < rows; i++) {
            float top = rulerH + i * layerH;
            paint.setColor(i % 2 == 0 ? 0xFF1A1A1A : 0xFF222222);
            c.drawRect(0, top, getWidth(), top + layerH, paint);
        }

        for (Clip k : clips) {
            float top = rulerH + k.row * layerH;
            RectF r = new RectF(xOf(k.start), top + 6 * d, xOf(k.end), top + layerH - 6 * d);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(k.color);
            c.drawRoundRect(r, 8 * d, 8 * d, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawText(k.name, Math.max(r.left, 0) + 14 * d, r.centerY() + 4 * d, paint);
            if (k == selected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * d);
                paint.setColor(0xFFFFFFFF);
                c.drawRoundRect(r, 8 * d, 8 * d, paint);
                paint.setStyle(Paint.Style.FILL);
                c.drawRect(r.left, r.top + 8 * d, r.left + 5 * d, r.bottom - 8 * d, paint);
                c.drawRect(r.right - 5 * d, r.top + 8 * d, r.right, r.bottom - 8 * d, paint);
                paint.setStrokeWidth(0);
            }
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2A2A2A);
        c.drawRect(0, 0, getWidth(), rulerH, paint);
        for (int s = 0; s <= maxEnd() / 1000 + 5; s++) {
            float x = s * pxPerSec - offsetX;
            if (x < -50 * d || x > getWidth() + 50 * d) continue;
            paint.setColor(0xFF888888);
            c.drawLine(x, rulerH - 10 * d, x, rulerH, paint);
            paint.setColor(0xFFCCCCCC);
            c.drawText(s + "s", x + 3 * d, rulerH - 14 * d, paint);
        }

        float px = xOf(playheadMs);
        paint.setColor(0xFFFF3B30);
        paint.setStrokeWidth(2 * d);
        c.drawLine(px, 0, px, getHeight(), paint);
        c.drawCircle(px, 8 * d, 7 * d, paint);
        paint.setStrokeWidth(0);
    }

    private void scrubTo(float x) {
        long ms = (long) ((x + offsetX) / pxPerSec * 1000);
        playheadMs = Math.max(0, Math.min(ms, durationMs));
        if (listener != null) listener.onScrub(playheadMs);
        invalidate();
    }

    private Clip hit(float x, float y) {
        int row = (int) ((y - rulerH) / layerH);
        for (int i = clips.size() - 1; i >= 0; i--) {
            Clip k = clips.get(i);
            if (k.row == row && x >= xOf(k.start) && x <= xOf(k.end)) return k;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = x;
                if (y < rulerH) {
                    mode = SCRUB;
                    scrubTo(x);
                } else {
                    Clip k = hit(x, y);
                    selected = k;
                    if (k == null) {
                        mode = SCROLL;
                    } else {
                        float zone = Math.min(edgeZone, (xOf(k.end) - xOf(k.start)) / 3f);
                        if (x - xOf(k.start) < zone) mode = TRIM_L;
                        else if (xOf(k.end) - x < zone) mode = TRIM_R;
                        else mode = MOVE;
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                long dms = (long) (dx / pxPerSec * 1000);
                if (mode == SCRUB) {
                    scrubTo(x);
                } else if (mode == SCROLL) {
                    offsetX -= dx;
                    float max = maxEnd() / 1000f * pxPerSec;
                    offsetX = Math.max(0, Math.min(offsetX, max));
                    lastX = x;
                    invalidate();
                } else if (selected != null) {
                    if (mode == MOVE) {
                        long len = selected.end - selected.start;
                        selected.start = Math.max(0, selected.start + dms);
                        selected.end = selected.start + len;
                    } else if (mode == TRIM_L) {
                        selected.start = Math.max(0, Math.min(selected.start + dms, selected.end - 300));
                    } else if (mode == TRIM_R) {
                        selected.end = Math.max(selected.start + 300, selected.end + dms);
                    }
                    lastX = x;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mode = NONE;
                return true;
        }
        return true;
    }
}
