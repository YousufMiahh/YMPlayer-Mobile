package com.ymplayer.mobile;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pieces shared by the movie and series pages. */
final class DetailUi {
    private DetailUi() {}

    /** Round back arrow floating over the artwork. */
    static View backButton(final Activity a) {
        Icon i = new Icon(a, Icon.Kind.BACK, Ui.W1);
        int p = Ui.dp(a, 9);
        i.setPadding(p, p, p - Ui.dp(a, 2), p);
        Ui.ripple(i, Ui.rounded(a, 0x66000000, 0, 20), 20);
        i.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { a.onBackPressed(); }
        });
        i.setElevation(Ui.dp(a, 4));
        return i;
    }

    static FrameLayout.LayoutParams backParams(Activity a) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 40), Gravity.TOP | Gravity.START);
        lp.topMargin = Ui.statusBar(a) + Ui.dp(a, 8);
        lp.leftMargin = Ui.dp(a, 12);
        return lp;
    }

    static LinearLayout.LayoutParams fullWidth(Activity a, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(a, topMarginDp);
        return lp;
    }

    /** Netflix-style icon-over-label action ("♡ Favourite", "Trailer"…). */
    static View action(Activity a, Icon.Kind k, String label, int iconColor, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(a);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        b.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8));
        Ui.ripple(b, null, 12);
        Icon i = new Icon(a, k, iconColor);
        b.addView(i, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
        TextView t = Ui.oneLine(Ui.medium(a, label, 11.5f, Ui.TEXT2));
        t.setPadding(0, Ui.dp(a, 6), 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 84), ViewGroup.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }
}
