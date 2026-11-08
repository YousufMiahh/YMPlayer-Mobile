package com.ymplayer.mobile;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Downloads tab: everything saved for offline watching, with live progress while downloading. */
class DownloadsPage extends Page {
    private LinearLayout list;
    private TextView summary;
    private LinearLayout empty;
    private final List<Row> rows = new ArrayList<>();
    private final Runnable tick = new Runnable() {
        public void run() {
            Async.MAIN.removeCallbacks(this);
            if (list == null || !list.isShown()) return; // only while the tab is on screen
            boolean active = refreshStatus();
            Async.MAIN.postDelayed(this, active ? 1000 : 4000);
        }
    };

    DownloadsPage(MainActivity a) { super(a); }

    int ambient() { return Ambient.SEARCH; }

    private static class Row {
        Downloads.Entry entry;
        LinearLayout view;
        TextView status;
        FrameLayout bar;
        View barFill;
        Icon icon;
        int state = -1;
    }

    View build() {
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, Ui.dp(a, 24));
        col.addView(header("Downloads", null));
        summary = Ui.text(a, "", 13, Ui.TEXT2);
        summary.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 10));
        col.addView(summary);
        list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);

        empty = new LinearLayout(a);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        empty.setPadding(Ui.dp(a, 36), Ui.dp(a, 70), Ui.dp(a, 36), 0);
        View circle = new FrameLayout(a);
        circle.setBackground(Ui.rounded(a, Ui.SURFACE, 0, 48));
        Icon big = new Icon(a, Icon.Kind.DOWNLOAD, Ui.TEXT2);
        int p = Ui.dp(a, 26);
        big.setPadding(p, p, p, p);
        ((FrameLayout) circle).addView(big, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        empty.addView(circle, new LinearLayout.LayoutParams(Ui.dp(a, 96), Ui.dp(a, 96)));
        TextView t = Ui.title(a, "Nothing downloaded yet", 18);
        t.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 6));
        t.setGravity(Gravity.CENTER);
        empty.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView s = Ui.text(a, "Tap Download on any movie or episode to watch it without a connection — on a train, a plane or anywhere with poor signal.", 14, Ui.TEXT2);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.3f);
        empty.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(empty);

        scroll.addView(col);
        rebuild();
        return scroll;
    }

    @Override
    void onShown() {
        rebuild();
        list.post(tick);
    }

    @Override
    void onResume() {
        rebuild();
        list.post(tick);
    }

    @Override
    void onDestroy() { Async.MAIN.removeCallbacks(tick); }

    private void rebuild() {
        if (list == null) return;
        list.removeAllViews();
        rows.clear();
        List<Downloads.Entry> all = Downloads.list(a);
        empty.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        for (Downloads.Entry e : all) rows.add(row(e));
        refreshStatus();
    }

    /** @return true while something is still downloading (so we check again soon). */
    private boolean refreshStatus() {
        List<Downloads.Entry> entries = new ArrayList<>();
        for (Row r : rows) entries.add(r.entry);
        Map<Long, Downloads.Status> st = Downloads.statuses(a, entries);
        boolean active = false;
        long used = 0;
        for (Row r : rows) {
            Downloads.Status s = st.get(r.entry.id);
            if (s == null) continue;
            if (s.state == Downloads.DONE) used += s.total;
            else used += s.done;
            if (s.state == Downloads.RUNNING || s.state == Downloads.QUEUED || s.state == Downloads.PAUSED) active = true;
            paint(r, s);
        }
        long free = Downloads.freeSpace(a);
        int n = rows.size();
        summary.setText(n == 0 ? "" : n + (n == 1 ? " download" : " downloads") + "  ·  " + Downloads.size(used) + " used"
                + (free >= 0 ? "  ·  " + Downloads.size(free) + " free" : ""));
        summary.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        return active;
    }

    private Row row(final Downloads.Entry e) {
        final Row r = new Row();
        r.entry = e;
        final Item it = e.item;
        LinearLayout v = new LinearLayout(a);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8));
        Ui.ripple(v, null, 0);

        FrameLayout thumb = new FrameLayout(a);
        thumb.setBackground(Ui.rounded(a, Ui.SURFACE, 0, 6));
        thumb.setClipToOutline(true);
        ImageView img = new ImageView(a);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ImageLoader.load(img, it.icon != null ? it.icon : it.parentIcon, 400, 225);
        v.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(a, 116), Ui.dp(a, 66)));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 6), 0);
        boolean ep = Item.EPISODE.equals(it.type);
        if (ep && it.parentName != null) {
            TextView series = Ui.oneLine(Ui.medium(a, it.parentName, 12, Ui.TEXT2));
            texts.addView(series);
        }
        TextView name = Ui.lines(Ui.medium(a, ep && it.num != null ? it.num + ". " + it.name : it.name, 14, Ui.TEXT), 2);
        texts.addView(name);
        r.status = Ui.oneLine(Ui.text(a, "", 12, Ui.TEXT2));
        r.status.setPadding(0, Ui.dp(a, 4), 0, 0);
        texts.addView(r.status);
        r.bar = new FrameLayout(a);
        r.bar.setBackground(Ui.rounded(a, Ui.SURFACE_HI, 0, 2));
        r.bar.setClipToOutline(true);
        r.barFill = new View(a);
        r.barFill.setBackground(Ui.rounded(a, Ui.ACCENT, 0, 2));
        r.bar.addView(r.barFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 3));
        bl.topMargin = Ui.dp(a, 6);
        texts.addView(r.bar, bl);
        v.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        r.icon = new Icon(a, Icon.Kind.PLAY, Ui.TEXT);
        int p = Ui.dp(a, 12);
        r.icon.setPadding(p, p, p, p);
        Ui.ripple(r.icon, null, 22);
        r.icon.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { tap(r); }
        });
        v.addView(r.icon, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));

        v.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { tap(r); }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View x) {
                x.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                menu(r);
                return true;
            }
        });
        r.view = v;
        list.addView(v);
        return r;
    }

    private void paint(Row r, Downloads.Status s) {
        r.state = s.state;
        String sizes = s.total > 0 ? Downloads.size(s.done) + " of " + Downloads.size(s.total) : Downloads.size(s.done);
        switch (s.state) {
            case Downloads.DONE:
                r.status.setText("Downloaded  ·  " + Downloads.size(s.total));
                r.status.setTextColor(Ui.TEXT2);
                r.bar.setVisibility(View.GONE);
                r.icon.setKind(Icon.Kind.PLAY);
                break;
            case Downloads.RUNNING:
                r.status.setText(Math.round(s.fraction() * 100) + "%  ·  " + sizes);
                r.status.setTextColor(Ui.TEXT2);
                r.bar.setVisibility(View.VISIBLE);
                r.icon.setKind(Icon.Kind.CLOSE);
                break;
            case Downloads.PAUSED:
                r.status.setText(s.reason + (s.done > 0 ? "  ·  " + sizes : ""));
                r.status.setTextColor(Ui.TEXT2);
                r.bar.setVisibility(View.VISIBLE);
                r.icon.setKind(Icon.Kind.CLOSE);
                break;
            case Downloads.FAILED:
                r.status.setText(s.reason + "  ·  tap to retry");
                r.status.setTextColor(Ui.RED);
                r.bar.setVisibility(View.GONE);
                r.icon.setKind(Icon.Kind.RESTART);
                break;
            default:
                r.status.setText("Waiting to start…");
                r.status.setTextColor(Ui.TEXT2);
                r.bar.setVisibility(View.VISIBLE);
                r.icon.setKind(Icon.Kind.CLOSE);
        }
        final float f = s.fraction();
        final FrameLayout bar = r.bar;
        final View fill = r.barFill;
        bar.post(new Runnable() {
            public void run() {
                ViewGroup.LayoutParams lp = fill.getLayoutParams();
                lp.width = (int) (bar.getWidth() * f);
                fill.setLayoutParams(lp);
            }
        });
    }

    private void tap(Row r) {
        final Item it = r.entry.item;
        if (r.state == Downloads.DONE) {
            List<Item> q = new ArrayList<>();
            q.add(it);
            Open.play(a, q, 0);
        } else if (r.state == Downloads.FAILED) {
            Downloads.retry(a, api, it);
            rebuild();
            list.post(tick);
        } else {
            List<Sheet.Option> o = new ArrayList<>();
            o.add(new Sheet.Option("Cancel download", false, new Runnable() {
                public void run() {
                    Downloads.remove(a, it);
                    rebuild();
                }
            }, true));
            o.add(new Sheet.Option("Keep downloading", false, null));
            Sheet.show(a, it.name, null, o, 0);
        }
    }

    private void menu(final Row r) {
        final Item it = r.entry.item;
        List<Sheet.Option> o = new ArrayList<>();
        if (r.state == Downloads.DONE) {
            o.add(new Sheet.Option("Play", false, new Runnable() {
                public void run() {
                    List<Item> q = new ArrayList<>();
                    q.add(it);
                    Open.play(a, q, 0);
                }
            }));
        }
        if (Item.EPISODE.equals(it.type) && it.parentId != null) {
            o.add(new Sheet.Option("Go to series", false, new Runnable() {
                public void run() {
                    Item s = new Item(Item.SERIES, it.parentId, it.parentName == null ? "" : it.parentName);
                    s.icon = it.parentIcon;
                    Open.item(a, s, null);
                }
            }));
        } else if (Item.MOVIE.equals(it.type)) {
            o.add(new Sheet.Option("More info", false, new Runnable() {
                public void run() { Open.item(a, it, null); }
            }));
        }
        o.add(new Sheet.Option(r.state == Downloads.DONE ? "Delete download" : "Cancel download", false, new Runnable() {
            public void run() {
                Downloads.remove(a, it);
                rebuild();
            }
        }, true));
        Sheet.show(a, it.name, null, o, 0);
    }
}
