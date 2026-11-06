package com.ymplayer.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Rounded progress bar: track, buffered, played. The bar is drawn centred in the view, so the
 * view can be taller than the bar to make it easy to grab with a finger; an optional round
 * thumb grows while you drag.
 */
public class SeekBarView extends View {
    private float progress, buffered;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buf = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float barHeight = -1;
    private boolean thumb, dragging;

    public SeekBarView(Context c) {
        super(c);
        track.setColor(0x40FFFFFF);
        buf.setColor(0x66FFFFFF);
        fill.setColor(0xFFFFFFFF);
    }

    /** Bar thickness in px (default: the view's full height). */
    public void setBarHeight(float px) { barHeight = px; invalidate(); }

    public void setThumb(boolean on) { thumb = on; invalidate(); }

    public void setDragging(boolean d) { dragging = d; invalidate(); }

    public float progress() { return progress; }

    public void set(float progress, float buffered) {
        this.progress = clamp(progress);
        this.buffered = clamp(buffered);
        invalidate();
    }

    private static float clamp(float f) {
        return Float.isNaN(f) ? 0 : Math.max(0, Math.min(1, f));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), full = getHeight();
        float h = barHeight > 0 ? Math.min(barHeight * (dragging ? 1.6f : 1f), full) : full;
        float pad = thumb ? full / 2.5f : 0;
        float top = (full - h) / 2f, rad = h / 2f, left = pad, width = w - pad * 2;
        r.set(left, top, left + width, top + h);
        c.drawRoundRect(r, rad, rad, track);
        if (buffered > 0) {
            r.set(left, top, left + Math.max(h, width * buffered), top + h);
            c.drawRoundRect(r, rad, rad, buf);
        }
        if (progress > 0) {
            r.set(left, top, left + Math.max(h, width * progress), top + h);
            c.drawRoundRect(r, rad, rad, fill);
        }
        if (thumb) {
            float tr = full / (dragging ? 2.6f : 3.6f);
            c.drawCircle(left + width * progress, full / 2f, tr, fill);
        }
    }

    /** Converts a touch x to a 0–1 position, matching how the bar is drawn. */
    public float fractionAt(float x) {
        float pad = thumb ? getHeight() / 2.5f : 0;
        return clamp((x - pad) / Math.max(1f, getWidth() - pad * 2));
    }
}
