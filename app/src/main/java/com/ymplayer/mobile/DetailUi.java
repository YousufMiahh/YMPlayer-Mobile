package com.ymplayer.mobile;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

    /** Netflix's grey full-width button under Play ("Play From Beginning", "Audio & Subtitles"). */
    static Button secondary(Activity a, String label, View.OnClickListener l) {
        Button b = Ui.button(a, label);
        b.setOnClickListener(l);
        return b;
    }

    /** Resume's progress: a thin bar and "38 min left". */
    static void progress(Activity a, LinearLayout into, long pos, long dur) {
        if (pos <= 0 || dur <= 0) return;
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(a, 10), 0, 0);
        View bar = Cards.progressBar(a, pos / (float) dur);
        row.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(a, 3), 1f));
        TextView left = Ui.text(a, SeriesActivity.fmt(Math.max(0, dur - pos)) + " left", 12, Ui.TEXT2);
        left.setPadding(Ui.dp(a, 10), 0, 0, 0);
        row.addView(left);
        into.addView(row);
    }

    /** Audio & Subtitles before you start: the languages the player picks when it can. */
    static void audioSubtitles(final Activity a, final Prefs prefs) {
        String sl = prefs.subtitleLanguage(), al = prefs.audioLanguage();
        java.util.List<Sheet.Option> o = new java.util.ArrayList<>();
        o.add(new Sheet.Option("Subtitles: Off", "off".equals(sl), new Runnable() { public void run() { prefs.setSubtitleLanguage("off"); } }));
        o.add(new Sheet.Option("Subtitles: English", "en".equals(sl), new Runnable() { public void run() { prefs.setSubtitleLanguage("en"); } }));
        o.add(new Sheet.Option("Subtitles: as the stream sets them", "".equals(sl), new Runnable() { public void run() { prefs.setSubtitleLanguage(""); } }));
        o.add(new Sheet.Option("Audio: English", "en".equals(al), new Runnable() { public void run() { prefs.setAudioLanguage("en"); } }));
        o.add(new Sheet.Option("Audio: Original (as the stream sets it)", "".equals(al), new Runnable() { public void run() { prefs.setAudioLanguage(""); } }));
        Sheet.show(a, "Audio & Subtitles", "Used from the next thing you play", o, 0);
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
        Ui.pressable(b);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(a, 84), ViewGroup.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }
}
