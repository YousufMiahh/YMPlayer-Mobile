package com.ymplayer.mobile;

import android.app.Activity;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A bottom sheet that slides up over the screen (categories, "hold" menus, confirmations).
 * Tap outside, swipe it down or press Back to close.
 */
public final class Sheet {
    public static class Option {
        final String label;
        final boolean checked;
        final Runnable action;
        final boolean destructive;

        public Option(String label, boolean checked, Runnable action) { this(label, checked, action, false); }

        public Option(String label, boolean checked, Runnable action, boolean destructive) {
            this.label = label;
            this.checked = checked;
            this.action = action;
            this.destructive = destructive;
        }
    }

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2.2f);

    private final Activity a;
    private final FrameLayout layer;
    private final View scrim;
    private final LinearLayout panel;
    private boolean closing;

    private static final java.util.WeakHashMap<Activity, Sheet> OPEN = new java.util.WeakHashMap<>();

    /** Closes the sheet on top, if any (call from onBackPressed). */
    public static boolean dismiss(Activity a) {
        Sheet s = OPEN.get(a);
        if (s == null) return false;
        s.close();
        return true;
    }

    public static Sheet show(Activity a, String title, String subtitle, List<Option> options, int scrollTo) {
        Sheet s = new Sheet(a, title, subtitle, options, scrollTo);
        OPEN.put(a, s);
        return s;
    }

    private Sheet(final Activity a, String title, String subtitle, final List<Option> options, int scrollTo) {
        this.a = a;
        ViewGroup content = a.findViewById(android.R.id.content);
        layer = new FrameLayout(a);
        scrim = new View(a);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(); } });
        layer.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.DARK ? 0xFF1C1C22 : 0xFFFFFFFF);
        float r = Ui.dp(a, 18);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setClickable(true);
        panel.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));

        View grip = new View(a);
        grip.setBackground(Ui.rounded(a, Ui.TEXT3, 0, 3));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = Ui.dp(a, 10);
        panel.addView(grip, glp);

        if (title != null) {
            TextView t = Ui.oneLine(Ui.title(a, title, 18));
            t.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), subtitle == null ? Ui.dp(a, 8) : 0);
            panel.addView(t);
        }
        if (subtitle != null) {
            TextView st = Ui.lines(Ui.text(a, subtitle, 13, Ui.TEXT2), 3);
            st.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 10));
            panel.addView(st);
        }

        final ListView list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        list.setAdapter(new BaseAdapter() {
            public int getCount() { return options.size(); }
            public Object getItem(int i) { return options.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                Option o = options.get(i);
                LinearLayout row = new LinearLayout(a);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);
                row.setMinimumHeight(Ui.dp(a, 50));
                Ui.ripple(row, null, 0);
                row.setClickable(false);
                TextView l = Ui.oneLine(Ui.medium(a, o.label, 16, o.destructive ? Ui.RED : Ui.TEXT));
                if (o.checked) l.setTypeface(Ui.bold());
                row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (o.checked) {
                    Icon ic = new Icon(a, Icon.Kind.CHECK, Ui.TEXT);
                    row.addView(ic, new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20)));
                }
                return row;
            }
        });
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                final Runnable act = options.get(i).action;
                close();
                if (act != null) act.run();
            }
        });
        int rowH = Ui.dp(a, 50);
        int maxH = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        int h = Math.min(maxH, rowH * options.size());
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        if (scrollTo > 2) list.setSelection(scrollTo - 2);

        // Swipe the sheet down to close it
        panel.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: startY = e.getRawY(); return true;
                    case MotionEvent.ACTION_MOVE:
                        panel.setTranslationY(Math.max(0, e.getRawY() - startY));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (panel.getTranslationY() > panel.getHeight() * 0.25f) close();
                        else panel.animate().translationY(0).setDuration(180).start();
                        return true;
                }
                return false;
            }
        });

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int maxW = Ui.dp(a, 560);
        if (a.getResources().getDisplayMetrics().widthPixels > maxW) {
            plp.width = maxW;
            plp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        layer.addView(panel, plp);
        layer.setElevation(Ui.dp(a, 30));
        content.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel.setVisibility(View.INVISIBLE);
        panel.post(new Runnable() {
            public void run() {
                panel.setTranslationY(panel.getHeight());
                panel.setVisibility(View.VISIBLE);
                panel.animate().translationY(0).setDuration(300).setInterpolator(EASE).start();
                scrim.animate().alpha(1f).setDuration(250).start();
            }
        });
    }

    public void close() {
        if (closing) return;
        closing = true;
        if (OPEN.get(a) == this) OPEN.remove(a);
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationY(panel.getHeight()).setDuration(220).setInterpolator(EASE).withEndAction(new Runnable() {
            public void run() {
                ViewGroup p = (ViewGroup) layer.getParent();
                if (p != null) p.removeView(layer);
            }
        }).start();
    }

    static int navBar(Activity a) {
        if (a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return 0;
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    /** Small helper for "Are you sure?" sheets. */
    public static void confirm(Activity a, String title, String subtitle, String yes, Runnable action) {
        List<Option> o = new ArrayList<>();
        o.add(new Option(yes, false, action, true));
        o.add(new Option("Cancel", false, null));
        show(a, title, subtitle, o, 0);
    }
}
