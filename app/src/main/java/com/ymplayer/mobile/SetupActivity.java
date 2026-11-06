package com.ymplayer.mobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * "Getting everything ready": downloads Live TV, Movies and Series once, one after another,
 * right after a playlist is added. From then on every screen opens instantly from the copy
 * saved on the device, and updates happen quietly in the background.
 */
public class SetupActivity extends Activity {
    private static final String[] TYPES = {Item.LIVE, Item.MOVIE, Item.SERIES};
    private static final String[] NAMES = {"Live TV", "Movies", "Series"};
    private static final String[] UNITS = {"channels", "movies", "series"};

    private Prefs prefs;
    private XtreamApi api;
    private final ProgressBar[] spinners = new ProgressBar[3];
    private final TextView[] marks = new TextView[3], counts = new TextView[3];
    private TextView status;
    private LinearLayout actions;
    private volatile boolean stopped;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Ui.edgeToEdge(this);
        Ambient.install(this, Ambient.HOME);
        prefs = new Prefs(this);
        api = new XtreamApi(prefs);

        FrameLayout root = new FrameLayout(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setClipChildren(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        clp.leftMargin = clp.rightMargin = Ui.dp(this, 20);
        root.addView(col, clp);

        ImageView logo = new ImageView(this);
        try (InputStream in = getAssets().open("logo.png")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {
        }
        int s = Ui.dp(this, 76);
        col.addView(logo, new LinearLayout.LayoutParams(s, s));

        TextView title = Ui.title(this, "Getting everything ready", 24);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(this, 20), 0, Ui.dp(this, 8));
        col.addView(title);
        TextView sub = Ui.text(this, "This only happens when you add a playlist. After this, everything opens instantly.", 14, Ui.TEXT2);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, Ui.dp(this, 24));
        col.addView(sub);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackground(Ui.rounded(this, Ui.SURFACE_LO, 0, 16));
        int lp = Ui.dp(this, 6);
        list.setPadding(lp, lp, lp, lp);
        for (int i = 0; i < 3; i++) list.addView(row(i));
        col.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = Ui.text(this, "", 13, Ui.TEXT2);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 20), 0, 0);
        col.addView(status);

        actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setClipChildren(false);
        actions.setPadding(0, Ui.dp(this, 18), 0, 0);
        col.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        Repo.setSetupListener(listener);
        status.post(ticker);
        start(java.util.Arrays.asList(TYPES));
    }

    private View row(int i) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int p = Ui.dp(this, 12);
        r.setPadding(p, p, p, p);
        FrameLayout icon = new FrameLayout(this);
        spinners[i] = new ProgressBar(this);
        spinners[i].setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT));
        spinners[i].setVisibility(View.INVISIBLE);
        icon.addView(spinners[i], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        marks[i] = Ui.title(this, "", 17);
        marks[i].setGravity(Gravity.CENTER);
        icon.addView(marks[i], new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24));
        ilp.rightMargin = Ui.dp(this, 14);
        r.addView(icon, ilp);
        TextView n = Ui.medium(this, NAMES[i], 16, Ui.TEXT);
        n.setTypeface(Ui.semibold());
        r.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        counts[i] = Ui.medium(this, "Waiting", 13, Ui.TEXT3);
        r.addView(counts[i]);
        return r;
    }

    private final boolean[] running = new boolean[3], done = new boolean[3];
    private final java.util.List<String> failedTypes = new java.util.ArrayList<>();

    /** Updates the "so far" counters while lists are downloading. */
    private final Runnable ticker = new Runnable() {
        public void run() {
            for (int i = 0; i < 3; i++) {
                if (!running[i]) continue;
                int n = XtreamApi.progress(TYPES[i]);
                counts[i].setText(n == 0 ? "Waiting for your provider…"
                        : NumberFormat.getIntegerInstance(Locale.UK).format(n) + " " + UNITS[i] + " so far…");
            }
            status.postDelayed(this, 300);
        }
    };

    private final Repo.SetupListener listener = new Repo.SetupListener() {
        public void started(String type) {
            int i = index(type);
            running[i] = true;
            spinners[i].setVisibility(View.VISIBLE);
            marks[i].setText("");
            counts[i].setTextColor(Ui.TEXT2);
            counts[i].setText("Waiting for your provider…");
        }

        public void finished(String type, int n, Exception e) {
            int i = index(type);
            running[i] = false;
            spinners[i].setVisibility(View.INVISIBLE);
            if (e != null) {
                marks[i].setText("✕");
                marks[i].setTextColor(Ui.RED);
                counts[i].setText("Didn't load");
                failedTypes.add(type);
                status.setText("Couldn't download " + NAMES[i] + ": " + Async.message(e));
                return;
            }
            done[i] = true;
            marks[i].setText("✓");
            marks[i].setTextColor(0xFF34C759);
            counts[i].setText(NumberFormat.getIntegerInstance(Locale.UK).format(n) + " " + UNITS[i]);
            if (Item.LIVE.equals(type) && (running[1] || running[2] || !done[1] || !done[2])) offerContinue();
        }

        public void allDone(boolean ok) {
            if (ok) {
                actions.removeAllViews();
                status.setText("All set");
                status.setTextColor(Ui.TEXT);
                status.postDelayed(new Runnable() {
                    public void run() { goHome(); }
                }, 700);
            } else {
                offerRetry();
            }
        }
    };

    private int index(String type) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(type)) return i;
        return 0;
    }

    private void start(java.util.List<String> types) {
        failedTypes.clear();
        actions.removeAllViews();
        status.setText("");
        for (String t : types) {
            int i = index(t);
            marks[i].setText("");
            counts[i].setText("Waiting");
            counts[i].setTextColor(Ui.TEXT3);
        }
        Repo.startSetup(api, prefs, types);
    }

    /** Live TV is ready: let you start watching while Movies and Series finish. */
    private void offerContinue() {
        actions.removeAllViews();
        Button go = Ui.primary(this, "Start Watching");
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { goHome(); }
        });
        actions.addView(go, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView note = Ui.text(this, "Movies and Series will finish in the background.", 12, Ui.TEXT3);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, Ui.dp(this, 8), 0, 0);
        actions.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        go.requestFocus();
    }

    private void offerRetry() {
        actions.removeAllViews();
        final java.util.List<String> retryTypes = new java.util.ArrayList<>(failedTypes);
        Button retry = Ui.button(this, "Try Again");
        retry.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { start(retryTypes); }
        });
        Button skip = Ui.button(this, "Continue Anyway");
        skip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { goHome(); }
        });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = l.rightMargin = Ui.dp(this, 10);
        actions.addView(retry, l);
        actions.addView(skip, l);
        retry.requestFocus();
    }

    private void goHome() {
        if (isFinishing()) return;
        Intent home = new Intent(this, MainActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        Repo.setSetupListener(null); // downloads carry on in the background
        status.removeCallbacks(ticker);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Keep going: the download is needed for the app to be fast.
    }
}
