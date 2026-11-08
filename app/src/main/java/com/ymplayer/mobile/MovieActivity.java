package com.ymplayer.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Full-bleed movie page: backdrop, title, details, Play / Resume / Favourite. */
public class MovieActivity extends Activity {
    private Prefs prefs;
    private XtreamApi api;
    private Item movie;

    private ImageView backdrop;
    private TextView title, meta, plot, credits;
    private LinearLayout buttons;
    private Button playBtn, favBtn;
    private String trailer;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = new Prefs(this);
        api = new XtreamApi(prefs);
        try {
            movie = Item.fromJson(new JSONObject(getIntent().getStringExtra("item")));
        } catch (Exception e) {
            finish();
            return;
        }

        Ui.edgeToEdge(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, Ui.dp(this, 32));
        scroll.addView(page);
        root.addView(scroll, match());

        // Backdrop across the top, fading into the page
        FrameLayout top = new BrowsePage.AspectFrame(this, 9f / 16f);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        top.addView(backdrop, match());
        View fade = new View(this);
        fade.setBackground(Ui.pageScrimBottom());
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        top.addView(fade, flp);
        View topShade = new View(this);
        topShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        top.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.statusBar(this) + Ui.dp(this, 60), Gravity.TOP));
        page.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 16);
        col.setPadding(side, Ui.dp(this, 4), side, 0);
        page.addView(col);

        title = Ui.lines(Ui.title(this, movie.name, 24), 3);
        meta = Ui.lines(Ui.medium(this, movie.info == null ? "" : movie.info, 13, Ui.TEXT2), 2);
        meta.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 14));
        plot = Ui.text(this, "", 14, Ui.TEXT);
        plot.setLineSpacing(0, 1.3f);
        plot.setPadding(0, Ui.dp(this, 16), 0, 0);
        credits = Ui.text(this, "", 12, Ui.TEXT2);
        credits.setLineSpacing(0, 1.3f);
        credits.setPadding(0, Ui.dp(this, 12), 0, 0);
        col.addView(title);
        col.addView(meta);

        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        col.addView(buttons);
        col.addView(plot);
        col.addView(credits);

        root.addView(DetailUi.backButton(this), DetailUi.backParams(this));
        setContentView(root);
        buildButtons();
        loadInfo();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void buildButtons() {
        buttons.removeAllViews();
        final long pos = prefs.position(movie);
        long dur = prefs.duration(movie);
        playBtn = Ui.primary(this, pos > 0 ? "▶  Resume" : "▶  Play");
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { play(false); }
        });
        buttons.addView(playBtn, DetailUi.fullWidth(this, 0));
        if (pos > 0 && dur > 0) {
            View bar = Cards.progressBar(this, pos / (float) dur);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3));
            bl.topMargin = Ui.dp(this, 10);
            buttons.addView(bar, bl);
            TextView left = Ui.text(this, SeriesActivity.fmt(Math.max(0, dur - pos)) + " left", 12, Ui.TEXT2);
            left.setPadding(0, Ui.dp(this, 6), 0, 0);
            buttons.addView(left);
        }
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, Ui.dp(this, 14), 0, 0);
        final boolean fav = prefs.isFav(movie);
        actions.addView(DetailUi.action(this, fav ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART, "Favourite", fav ? Ui.RED : Ui.TEXT, new View.OnClickListener() {
            public void onClick(View v) {
                prefs.toggleFav(movie);
                buildButtons();
            }
        }));
        DownloadButton dl = new DownloadButton(this, api, movie, true);
        Coach.show(this, "m_movie", false,
                new Coach.Step(playBtn, "Play", "Starts the film, or carries on where you left off."),
                new Coach.Step(dl, "Download", "Saves the film to your phone to watch without internet. Downloads are in the Downloads tab."));
        actions.addView(dl, new LinearLayout.LayoutParams(Ui.dp(this, 84), ViewGroup.LayoutParams.WRAP_CONTENT));
        if (trailer != null) {
            actions.addView(DetailUi.action(this, Icon.Kind.TRAILER, "Trailer", Ui.TEXT, new View.OnClickListener() {
                public void onClick(View v) { TrailerActivity.open(MovieActivity.this, trailer); }
            }));
        }
        if (pos > 0) {
            actions.addView(DetailUi.action(this, Icon.Kind.RESTART, "Start Over", Ui.TEXT, new View.OnClickListener() {
                public void onClick(View v) { play(true); }
            }));
        }
        buttons.addView(actions);
    }

    private void play(boolean fromStart) {
        if (fromStart) prefs.setPosition(movie, 0);
        List<Item> q = new ArrayList<>();
        q.add(movie);
        Open.play(this, q, 0);
    }

    private void loadInfo() {
        if (movie.icon != null) loadBackdrop(movie.icon);
        Async.run(new Async.Work<JSONObject>() {
            public JSONObject run() throws Exception { return api.vodInfo(movie.id); }
        }, new Async.Done<JSONObject>() {
            public void done(JSONObject r, Exception e) {
                if (isFinishing() || r == null) return;
                JSONObject info = r.optJSONObject("info");
                if (info == null) return;
                String p = str(info, "plot", str(info, "description", null));
                if (p != null) {
                    plot.setText(p);
                    movie.plot = p;
                }
                String year = str(info, "releasedate", str(info, "release_date", null));
                if (year != null && year.length() >= 4) year = year.substring(0, 4);
                String dur = str(info, "duration", null);
                if (dur != null && dur.startsWith("0") && dur.length() == 8) dur = shortDuration(dur);
                String m = XtreamApi.joinNonEmpty(year, dur, str(info, "genre", null),
                        XtreamApi.rating(str(info, "rating", null)));
                if (m != null) meta.setText(m);
                String cr = XtreamApi.joinNonEmpty(
                        str(info, "director", null) == null ? null : "Director: " + str(info, "director", null),
                        str(info, "cast", str(info, "actors", null)) == null ? null : "Cast: " + str(info, "cast", str(info, "actors", null)));
                credits.setText(cr == null ? "" : cr);
                String bd = null;
                JSONArray arr = info.optJSONArray("backdrop_path");
                if (arr != null && arr.length() > 0) bd = arr.optString(0, null);
                if (bd == null || bd.isEmpty()) bd = str(info, "movie_image", movie.icon);
                if (bd != null) loadBackdrop(bd);
                String t = TrailerActivity.youtubeId(str(info, "youtube_trailer", str(info, "trailer", null)));
                if (t != null && trailer == null) {
                    trailer = t;
                    buildButtons();
                }
                JSONObject data = r.optJSONObject("movie_data");
                if (data != null && movie.ext == null) movie.ext = str(data, "container_extension", null);
            }
        });
    }

    private void loadBackdrop(String url) {
        ImageLoader.load(backdrop, url, 1280, 720);
        backdrop.animate().alpha(1f).setDuration(500).start();
    }

    static String shortDuration(String hms) {
        try {
            String[] p = hms.split(":");
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h > 0 ? h + " hr " + m + " min" : m + " min";
        } catch (Exception e) {
            return hms;
        }
    }

    static String str(JSONObject o, String k, String def) {
        if (o == null || o.isNull(k)) return def;
        String s = o.optString(k, "").trim();
        return s.isEmpty() || "null".equals(s) ? def : s;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (buttons != null && buttons.getChildCount() > 0) buildButtons();
    }
}
