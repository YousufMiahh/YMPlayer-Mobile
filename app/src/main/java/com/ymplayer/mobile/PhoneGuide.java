package com.ymplayer.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Phone-sized TV guide: channel logos down the left, programmes across a timeline, a line for
 * "now". Drag or flick up/down through channels and sideways through time; tap a programme
 * for details, tap a logo to watch. Tap the time in the corner to jump back to now.
 */
public class PhoneGuide extends View {
    public interface Listener {
        void onChannel(int row);
        void onProgramme(int row, XtreamApi.Programme p);
        void onNeedListings(List<Item> channels);
    }

    private static final long HALF_HOUR = 1800, WINDOW = 2 * 3600, AHEAD = 24 * 3600;

    private List<Item> channels = new ArrayList<>();
    private Listener listener;
    private float scrollY, scrollX; // px: rows down, time across
    private long base; // unix seconds at scrollX = 0 (now, rounded down to the half hour)
    private final int rowH, headerH, chanW;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Rect src = new Rect();
    private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.UK);
    private final GestureDetector gestures;
    private final OverScroller scroller;
    private final Runnable askListings = new Runnable() { public void run() { requestListings(); } };
    private final Runnable tick = new Runnable() { public void run() { invalidate(); } };

    public PhoneGuide(Context c) {
        super(c);
        rowH = Ui.dp(c, 62);
        headerH = Ui.dp(c, 34);
        chanW = Ui.dp(c, 78);
        text.setTypeface(Ui.medium());
        text.setTextSize(Ui.sp(c, 13.5f));
        small.setTypeface(Ui.medium());
        small.setTextSize(Ui.sp(c, 11.5f));
        line.setStrokeWidth(Ui.dp(c, 1.5f));
        scroller = new OverScroller(c);
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                scrollBy(dx, dy);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }

            @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                scroller.fling((int) scrollX, (int) scrollY, (int) -vx, (int) -vy, 0, (int) maxX(), 0, (int) maxY());
                postInvalidateOnAnimation();
                return true;
            }

            @Override public boolean onSingleTapUp(MotionEvent e) {
                tap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setListener(Listener l) { listener = l; }

    public void setChannels(List<Item> list) { setChannels(list, 0); }

    /** @param firstRow the channel to start at (where you were in the list). */
    public void setChannels(List<Item> list, int firstRow) {
        channels = list == null ? new ArrayList<Item>() : list;
        scrollY = Math.max(0, firstRow) * rowH;
        if (getHeight() > 0) scrollY = Math.min(scrollY, maxY());
        jumpToNow();
        post(askListings);
    }

    public void listingsChanged() { invalidate(); }

    /** The channel at the top of the guide (to keep your place when switching back to the list). */
    public int firstVisibleRow() { return (int) (scrollY / rowH + 0.5f); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (scrollY > maxY()) scrollY = maxY();
    }

    private void jumpToNow() {
        long now = System.currentTimeMillis() / 1000L;
        base = now - now % HALF_HOUR;
        scrollX = 0;
        invalidate();
    }

    private float perSec() { return (getWidth() - chanW) / (float) WINDOW; }

    private float maxX() { return AHEAD * perSec(); }

    private float maxY() { return Math.max(0, channels.size() * rowH - (getHeight() - headerH)); }

    private void scrollBy(float dx, float dy) {
        scrollX = Math.max(0, Math.min(maxX(), scrollX + dx));
        scrollY = Math.max(0, Math.min(maxY(), scrollY + dy));
        removeCallbacks(askListings);
        postDelayed(askListings, 250);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollX = scroller.getCurrX();
            scrollY = scroller.getCurrY();
            removeCallbacks(askListings);
            postDelayed(askListings, 250);
            postInvalidateOnAnimation();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e) || super.onTouchEvent(e);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        post(askListings);
    }

    private void requestListings() {
        if (listener == null || channels.isEmpty()) return;
        int first = (int) (scrollY / rowH), vis = (getHeight() - headerH) / rowH + 2;
        int from = Math.max(0, first - 2), to = Math.min(channels.size(), first + vis + 4);
        listener.onNeedListings(new ArrayList<>(channels.subList(from, to)));
    }

    private long timeAt(float x) { return base + (long) ((x - chanW + scrollX) / perSec()); }

    private void tap(float x, float y) {
        if (y < headerH) {
            if (x < chanW) jumpToNow(); // "Now" in the corner
            return;
        }
        int row = (int) ((y - headerH + scrollY) / rowH);
        if (row < 0 || row >= channels.size() || listener == null) return;
        if (x < chanW) {
            listener.onChannel(row);
            return;
        }
        long t = timeAt(x);
        List<XtreamApi.Programme> l = Guide.get(channels.get(row).id);
        XtreamApi.Programme hit = null;
        if (l != null) for (XtreamApi.Programme p : l) if (p.start <= t && t < p.stop) hit = p;
        if (hit == null) listener.onChannel(row);
        else listener.onProgramme(row, hit);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        Context ctx = getContext();
        float ps = perSec(), gap = Ui.dp(ctx, 2), rad = Ui.dp(ctx, 8);
        long now = System.currentTimeMillis() / 1000L;
        long viewStart = base + (long) (scrollX / ps), viewEnd = viewStart + WINDOW;

        // rows
        c.save();
        c.clipRect(0, headerH, w, h);
        int first = Math.max(0, (int) (scrollY / rowH)), last = Math.min(channels.size() - 1, first + (h - headerH) / rowH + 1);
        for (int i = first; i <= last; i++) {
            float y = headerH + i * rowH - scrollY;
            Item ch = channels.get(i);
            List<XtreamApi.Programme> l = Guide.get(ch.id);
            c.save();
            c.clipRect(chanW, y, w, y + rowH);
            if (l == null || l.isEmpty()) {
                r.set(chanW + gap, y + gap, w - gap, y + rowH - gap);
                fill.setColor(Ui.SURFACE_LO);
                c.drawRoundRect(r, rad, rad, fill);
                small.setColor(Ui.TEXT3);
                c.drawText(l == null ? "Loading…" : "No information", chanW + Ui.dp(ctx, 12), y + rowH / 2f + small.getTextSize() / 3, small);
            } else {
                for (XtreamApi.Programme p : l) {
                    if (p.stop <= viewStart || p.start >= viewEnd) continue;
                    float x1 = chanW + (p.start - base) * ps - scrollX, x2 = chanW + (p.stop - base) * ps - scrollX;
                    r.set(x1 + gap, y + gap, x2 - gap, y + rowH - gap);
                    boolean onNow = p.start <= now && now < p.stop;
                    fill.setColor(onNow ? Ui.SURFACE_HI : Ui.SURFACE);
                    c.drawRoundRect(r, rad, rad, fill);
                    float left = Math.max(r.left, chanW) + Ui.dp(ctx, 9);
                    float avail = r.right - left - Ui.dp(ctx, 6);
                    if (avail < Ui.dp(ctx, 16)) continue;
                    text.setColor(Ui.TEXT);
                    CharSequence t = TextUtils.ellipsize(p.title == null ? "" : p.title, text, avail, TextUtils.TruncateAt.END);
                    c.drawText(t, 0, t.length(), left, y + rowH / 2f - Ui.dp(ctx, 1), text);
                    small.setColor(Ui.TEXT2);
                    CharSequence tm = TextUtils.ellipsize(p.timeRange(), small, avail, TextUtils.TruncateAt.END);
                    c.drawText(tm, 0, tm.length(), left, y + rowH / 2f + small.getTextSize() + Ui.dp(ctx, 1), small);
                }
            }
            c.restore();
            // channel cell (logo, or number + initials)
            r.set(gap, y + gap, chanW - gap, y + rowH - gap);
            fill.setColor(Ui.SURFACE_LO);
            c.drawRoundRect(r, rad, rad, fill);
            Bitmap logo = ImageLoader.cached(ch.icon);
            if (logo != null) {
                float lw = chanW - Ui.dp(ctx, 18), lh = rowH - Ui.dp(ctx, 22);
                float sc = Math.min(lw / logo.getWidth(), lh / logo.getHeight());
                float dw = logo.getWidth() * sc, dh = logo.getHeight() * sc;
                r.set((chanW - dw) / 2, y + (rowH - dh) / 2, (chanW + dw) / 2, y + (rowH + dh) / 2);
                src.set(0, 0, logo.getWidth(), logo.getHeight());
                c.drawBitmap(logo, src, r, null);
            } else {
                if (ch.icon != null) ImageLoader.prefetch(ch.icon, 160, 96, new Runnable() { public void run() { invalidate(); } });
                text.setColor(Ui.TEXT2);
                String n = Cards.initials(ch.name);
                c.drawText(n, (chanW - text.measureText(n)) / 2, y + rowH / 2f + text.getTextSize() / 3, text);
            }
        }
        c.restore();

        // header: "Now" corner + half-hour marks
        fill.setColor(Ui.DARK ? 0xF20B0B12 : 0xF5F2F2F7);
        c.drawRect(0, 0, w, headerH, fill);
        small.setColor(scrollX > 1 ? Ui.ACCENT : Ui.TEXT2);
        c.drawText(scrollX > 1 ? "◀ Now" : hm.format(new Date(now * 1000L)), Ui.dp(ctx, 10), headerH * 0.66f, small);
        c.save();
        c.clipRect(chanW, 0, w, headerH);
        small.setColor(Ui.TEXT2);
        for (long t = viewStart - viewStart % HALF_HOUR; t < viewEnd; t += HALF_HOUR) {
            float x = chanW + (t - base) * ps - scrollX;
            c.drawText(hm.format(new Date(t * 1000L)), x + Ui.dp(ctx, 6), headerH * 0.66f, small);
            line.setColor(Ui.SURFACE_HI);
            c.drawLine(x, headerH * 0.8f, x, headerH, line);
        }
        c.restore();

        // now line
        float nx = chanW + (now - base) * ps - scrollX;
        if (nx >= chanW && nx <= w) {
            line.setColor(Ui.ACCENT);
            c.drawLine(nx, headerH * 0.75f, nx, h, line);
        }
        removeCallbacks(tick);
        postDelayed(tick, 30_000);
    }
}
