package com.ymplayer.mobile;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Netflix-style download button for a movie or episode: an arrow, then a filling ring while
 * it downloads, then a tick. Tapping it starts, cancels or deletes the download.
 */
class DownloadButton extends LinearLayout {
    private final Activity a;
    private final XtreamApi api;
    private Item item;
    private final Icon icon;
    private final Ring ring;
    private final TextView label;
    private int state = -2;
    private final Runnable poll = new Runnable() {
        public void run() {
            refresh();
            if (isAttachedToWindow() && (state == Downloads.RUNNING || state == Downloads.QUEUED || state == Downloads.PAUSED)) {
                Async.MAIN.postDelayed(this, 1000);
            }
        }
    };

    /** @param withLabel true for the movie page ("Download" under the icon), false for episode rows. */
    DownloadButton(Activity a, XtreamApi api, Item item, boolean withLabel) {
        super(a);
        this.a = a;
        this.api = api;
        this.item = item;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout box = new FrameLayout(a);
        ring = new Ring(a);
        box.addView(ring, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        icon = new Icon(a, Icon.Kind.DOWNLOAD, Ui.TEXT);
        int ip = Ui.dp(a, withLabel ? 1 : 3);
        icon.setPadding(ip, ip, ip, ip);
        box.addView(icon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int s = Ui.dp(a, withLabel ? 24 : 26);
        addView(box, new LinearLayout.LayoutParams(s, s));
        label = Ui.oneLine(Ui.medium(a, "Download", 11.5f, Ui.TEXT2));
        label.setPadding(0, Ui.dp(a, 6), 0, 0);
        label.setVisibility(withLabel ? VISIBLE : GONE);
        addView(label);
        int p = withLabel ? Ui.dp(a, 8) : Ui.dp(a, 10);
        setPadding(Ui.dp(a, 6), p, Ui.dp(a, 6), p);
        Ui.ripple(this, null, withLabel ? 12 : 22);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) { tap(); }
        });
        refresh();
    }

    void setItem(Item it) {
        item = it;
        refresh();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Async.MAIN.post(poll);
    }

    @Override
    protected void onDetachedFromWindow() {
        Async.MAIN.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    private void refresh() {
        Downloads.Entry e = Downloads.find(a, item);
        Downloads.Status s = e == null ? null : Downloads.status(a, e);
        int st = s == null ? -1 : s.state;
        state = st;
        ring.progress = s == null ? 0 : s.fraction();
        ring.show = st == Downloads.RUNNING || st == Downloads.QUEUED || st == Downloads.PAUSED;
        ring.invalidate();
        switch (st) {
            case Downloads.DONE:
                icon.setKind(Icon.Kind.CHECK);
                icon.setColor(Ui.ACCENT);
                label.setText("Downloaded");
                break;
            case Downloads.FAILED:
                icon.setKind(Icon.Kind.RESTART);
                icon.setColor(Ui.RED);
                label.setText("Retry");
                break;
            case Downloads.RUNNING:
            case Downloads.QUEUED:
            case Downloads.PAUSED:
                icon.setKind(Icon.Kind.CLOSE);
                icon.setColor(Ui.TEXT2);
                int pc = Math.round(ring.progress * 100);
                label.setText(st == Downloads.PAUSED ? "Waiting…" : pc > 0 ? pc + "%" : "Starting…");
                break;
            default:
                icon.setKind(Icon.Kind.DOWNLOAD);
                icon.setColor(Ui.TEXT);
                label.setText("Download");
        }
        int pad = ring.show ? Ui.dp(a, 7) : Ui.dp(a, label.getVisibility() == VISIBLE ? 1 : 3);
        icon.setPadding(pad, pad, pad, pad);
    }

    private void tap() {
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        if (state == -1) {
            Downloads.start(a, api, item);
            Toast.makeText(a, Downloads.wifiOnly(a) ? "Downloading — over Wi-Fi only (change this in Settings)" : "Downloading", Toast.LENGTH_SHORT).show();
            refresh();
            Async.MAIN.removeCallbacks(poll);
            Async.MAIN.post(poll);
            return;
        }
        if (state == Downloads.FAILED) {
            Downloads.retry(a, api, item);
            refresh();
            Async.MAIN.post(poll);
            return;
        }
        List<Sheet.Option> o = new ArrayList<>();
        if (state == Downloads.DONE) {
            o.add(new Sheet.Option("Delete download", false, new Runnable() {
                public void run() {
                    Downloads.remove(a, item);
                    refresh();
                }
            }, true));
            o.add(new Sheet.Option("Keep it", false, null));
        } else {
            o.add(new Sheet.Option("Cancel download", false, new Runnable() {
                public void run() {
                    Downloads.remove(a, item);
                    refresh();
                }
            }, true));
            o.add(new Sheet.Option("Keep downloading", false, null));
        }
        Sheet.show(a, item.name, null, o, 0);
    }

    /** The progress ring around the icon while downloading. */
    private static class Ring extends View {
        float progress;
        boolean show;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Ring(android.content.Context c) {
            super(c);
            float w = Ui.dp(c, 2.2f);
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(w);
            track.setColor(Ui.SURFACE_HI);
            arc.set(track);
            arc.setColor(Ui.ACCENT);
            arc.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!show) return;
            float w = track.getStrokeWidth();
            r.set(w, w, getWidth() - w, getHeight() - w);
            c.drawOval(r, track);
            c.drawArc(r, -90, Math.max(6, 360 * progress), false, arc);
        }
    }
}
