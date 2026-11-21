package com.ymplayer.mobile;

import android.app.Activity;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The film's or series' logo on its page (the artwork Netflix and Apple TV show instead of the
 * name, from TMDB) and the bar it shrinks into: scroll down and, as the big logo goes under the top
 * of the screen, a small one fades and grows into a bar along the top, which stays there. Tied to
 * the scroll position, so it follows your finger both ways. With no logo (or the light theme) the
 * name is used instead.
 */
final class LogoHeader {
    private final Activity a;
    private final ScrollView scroll;
    private final TextView title;
    private final ImageView big;
    private final FrameLayout bar;
    private final ImageView small;
    private final TextView smallName;
    private final int barH;
    private boolean haveLogo;

    LogoHeader(Activity a, FrameLayout root, ScrollView scroll, TextView title, View backButton,
               String kind, String name, String tmdb) {
        this.a = a;
        this.scroll = scroll;
        this.title = title;

        big = new ImageView(a);
        big.setAdjustViewBounds(true);
        big.setScaleType(ImageView.ScaleType.FIT_CENTER);
        big.setMaxWidth(Ui.dp(a, 280));
        big.setMaxHeight(Ui.dp(a, 110));
        big.setVisibility(View.GONE);
        ViewGroup parent = (ViewGroup) title.getParent();
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.CENTER_HORIZONTAL;
        blp.topMargin = Ui.dp(a, 4);
        blp.bottomMargin = Ui.dp(a, 6);
        parent.addView(big, parent.indexOfChild(title), blp);

        barH = Ui.statusBar(a) + Ui.dp(a, 56);
        bar = new FrameLayout(a);
        bar.setClickable(false);
        small = new ImageView(a);
        small.setAdjustViewBounds(true);
        small.setScaleType(ImageView.ScaleType.FIT_CENTER);
        small.setMaxWidth(Ui.dp(a, 170));
        small.setMaxHeight(Ui.dp(a, 34));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        slp.bottomMargin = Ui.dp(a, 11);
        bar.addView(small, slp);
        smallName = Ui.oneLine(Ui.medium(a, name, 16, Ui.TEXT));
        smallName.setTypeface(Ui.semibold());
        smallName.setGravity(Gravity.CENTER);
        smallName.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        nlp.leftMargin = nlp.rightMargin = Ui.dp(a, 64);
        nlp.bottomMargin = Ui.dp(a, 16);
        bar.addView(smallName, nlp);
        small.setVisibility(View.GONE);
        // under the back button, over the page
        root.addView(bar, root.indexOfChild(backButton) < 0 ? root.getChildCount() : root.indexOfChild(backButton),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH, Gravity.TOP));

        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int ox, int oy) { update(); }
        });
        scroll.post(new Runnable() { public void run() { update(); } });
        if (new Prefs(a).darkTheme()) load(kind, name, tmdb); // logos are made for dark backgrounds
    }

    private void load(final String kind, final String name, final String tmdb) {
        Async.run(new Async.Work<String>() {
            public String run() {
                String id = Tmdb.findId(a, kind, name, tmdb);
                return id == null ? null : Tmdb.logo(a, kind, id);
            }
        }, new Async.Done<String>() {
            public void done(final String url, Exception e) {
                if (url == null || a.isFinishing()) return;
                Bitmap have = ImageLoader.cached(url);
                if (have != null) { show(have); return; }
                ImageLoader.prefetch(url, 900, 360, new Runnable() {
                    public void run() {
                        Bitmap bm = ImageLoader.cached(url);
                        if (bm != null && !a.isFinishing()) show(bm);
                    }
                });
            }
        });
    }

    private void show(Bitmap bm) {
        haveLogo = true;
        big.setImageBitmap(bm);
        small.setImageBitmap(bm);
        big.setAlpha(0f);
        big.setVisibility(View.VISIBLE);
        big.animate().alpha(1f).setDuration(350).start();
        title.setVisibility(View.GONE);
        small.setVisibility(View.VISIBLE);
        smallName.setVisibility(View.GONE);
        scroll.post(new Runnable() { public void run() { update(); } });
    }

    /** 0 while the big logo (or name) is clear of the bar, 1 once it has gone under it. */
    private void update() {
        View target = haveLogo ? big : title;
        int[] at = new int[2];
        target.getLocationInWindow(at);
        int h = Math.max(1, target.getHeight());
        float p = (barH - at[1]) / (float) h;
        p = Math.max(0f, Math.min(1f, p));
        int alpha = Math.round(p * 255);
        bar.setBackgroundColor((alpha << 24) | (Ui.BG & 0xFFFFFF));
        View s = haveLogo ? small : smallName;
        s.setAlpha(p);
        float sc = 0.82f + 0.18f * p;
        s.setScaleX(sc);
        s.setScaleY(sc);
        s.setTranslationY((1 - p) * Ui.dp(a, 8));
        bar.setElevation(p >= 1f ? Ui.dp(a, 2) : 0);
    }
}
