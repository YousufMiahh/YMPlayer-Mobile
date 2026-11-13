package com.ymplayer.mobile;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One tab of the main screen (Home, Live TV, Movies, Series, Search). */
abstract class Page {
    final MainActivity a;
    final Prefs prefs;
    final XtreamApi api;
    private View view;

    Page(MainActivity a) {
        this.a = a;
        this.prefs = a.prefs;
        this.api = a.api;
    }

    View view() {
        if (view == null) view = build();
        return view;
    }

    boolean built() { return view != null; }

    abstract View build();

    /** Resting background colour for this tab. */
    abstract int ambient();

    /** The tab became visible. */
    void onShown() {}

    /** Coming back from another screen (player, details…). */
    void onResume() {}

    /** Tab tapped again while already on it. */
    void onReselect() {}

    void onStart() {}

    void onStop() {}

    void onDestroy() {}

    boolean onBack() { return false; }

    // ------------------------------------------------------------ shared pieces

    /** Large page title under the status bar, with an optional action on the right. */
    LinearLayout header(String title, View right) {
        LinearLayout h = new LinearLayout(a);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(Ui.dp(a, 16), Ui.statusBar(a) + Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 6));
        TextView t = Ui.oneLine(Ui.title(a, title, 28));
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (right != null) h.addView(right);
        return h;
    }

    /** Round icon button. */
    View iconButton(Icon.Kind k, View.OnClickListener l) {
        Icon i = new Icon(a, k, Ui.TEXT);
        int p = Ui.dp(a, 10);
        i.setPadding(p, p, p, p);
        Ui.ripple(i, null, 22);
        i.setOnClickListener(l);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        return i;
    }

    /** "All Channels ▾" pill that opens the category sheet. */
    static TextView dropdown(android.app.Activity a, String label) {
        TextView t = Ui.oneLine(Ui.medium(a, label + "  ▾", 14, Ui.TEXT));
        t.setTypeface(Ui.semibold());
        int px = Ui.dp(a, 14), py = Ui.dp(a, 8);
        t.setPadding(px, py, px, py);
        android.graphics.drawable.GradientDrawable g = Ui.rounded(a, 0, 0, 18);
        g.setStroke(Ui.dp(a, 1), Ui.DARK ? 0x66EBEBF5 : 0x553C3C43);
        Ui.ripple(t, g, 18);
        t.setMaxWidth((int) (a.getResources().getDisplayMetrics().widthPixels * 0.8f));
        return t;
    }

    void toggleFavMenu(final Item it) {
        ItemMenu.show(a, prefs, it, null, new Runnable() {
            public void run() { a.favouritesChanged(); }
        });
    }
}
