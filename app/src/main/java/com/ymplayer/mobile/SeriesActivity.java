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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Series page: backdrop and details, season pills, and a shelf of episodes. */
public class SeriesActivity extends Activity {
    private Prefs prefs;
    private XtreamApi api;
    private Item series;

    private ImageView backdrop;
    private TextView title, meta, plot, epLabel;
    private LinearLayout buttons, seasonRow, episodeRow;
    private Button playBtn;
    private String trailer;
    private final TreeMap<Integer, List<Item>> seasons = new TreeMap<>();
    private int activeSeason = Integer.MIN_VALUE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = new Prefs(this);
        api = new XtreamApi(prefs);
        try {
            series = Item.fromJson(new JSONObject(getIntent().getStringExtra("series")));
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

        FrameLayout top = new BrowsePage.AspectFrame(this, 9f / 16f);
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdrop.setAlpha(0f);
        top.addView(backdrop, match());
        View fade = new View(this);
        fade.setBackground(Ui.pageScrimBottom());
        top.addView(fade, match());
        View topShade = new View(this);
        topShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x99000000, 0x00000000}));
        top.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.statusBar(this) + Ui.dp(this, 60), Gravity.TOP));
        page.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(this, 16);
        info.setPadding(side, Ui.dp(this, 4), side, 0);
        title = Ui.lines(Ui.title(this, series.name, 24), 3);
        meta = Ui.lines(Ui.medium(this, series.info == null ? "" : series.info, 13, Ui.TEXT2), 2);
        meta.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 14));
        epLabel = Ui.oneLine(Ui.medium(this, "", 13, Ui.TEXT2));
        epLabel.setVisibility(View.GONE);
        plot = Ui.lines(Ui.text(this, series.plot == null ? "" : series.plot, 14, Ui.TEXT), 6);
        plot.setLineSpacing(0, 1.3f);
        plot.setPadding(0, Ui.dp(this, 14), 0, 0);
        buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        info.addView(title);
        info.addView(meta);
        info.addView(buttons);
        info.addView(epLabel);
        info.addView(plot);
        page.addView(info);

        // Season picker + episode list
        LinearLayout seasonBar = new LinearLayout(this);
        seasonBar.setGravity(Gravity.CENTER_VERTICAL);
        seasonBar.setPadding(side, Ui.dp(this, 22), side, Ui.dp(this, 6));
        seasonRow = seasonBar;
        page.addView(seasonBar);
        episodeRow = new LinearLayout(this);
        episodeRow.setOrientation(LinearLayout.VERTICAL);
        page.addView(episodeRow);

        root.addView(DetailUi.backButton(this), DetailUi.backParams(this));
        setContentView(root);
        loadBackdrop(series.icon);
        buildButtons();
        load();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void loadBackdrop(String url) {
        if (url == null) return;
        ImageLoader.load(backdrop, url, 1280, 720);
        backdrop.animate().alpha(1f).setDuration(500).start();
    }

    // ------------------------------------------------------------ buttons

    private Item lastWatched() {
        for (Item h : prefs.history(false)) {
            if (Item.EPISODE.equals(h.type) && series.id.equals(h.parentId)) return h;
        }
        return null;
    }

    private void buildButtons() {
        buttons.removeAllViews();
        final Item last = lastWatched();
        String label = "▶  Play";
        if (last != null) {
            Item match = find(last.id);
            label = "▶  " + (prefs.position(last) > 0 ? "Resume " : "Play ") + (match != null ? shortName(match) : "");
        }
        playBtn = Ui.primary(this, label.trim());
        playBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Item target = last != null ? find(last.id) : null;
                if (target == null && !seasons.isEmpty()) target = seasons.firstEntry().getValue().get(0);
                if (target != null) playEpisode(target);
            }
        });
        buttons.addView(playBtn, DetailUi.fullWidth(this, 0));
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, Ui.dp(this, 14), 0, 0);
        final boolean fav = prefs.isFav(series);
        actions.addView(DetailUi.action(this, fav ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART, "Favourite", fav ? Ui.RED : Ui.TEXT, new View.OnClickListener() {
            public void onClick(View v) {
                prefs.toggleFav(series);
                buildButtons();
            }
        }));
        if (trailer != null) {
            actions.addView(DetailUi.action(this, Icon.Kind.TRAILER, "Trailer", Ui.TEXT, new View.OnClickListener() {
                public void onClick(View v) { TrailerActivity.open(SeriesActivity.this, trailer); }
            }));
        }
        buttons.addView(actions);
    }

    private String shortName(Item ep) {
        for (Map.Entry<Integer, List<Item>> s : seasons.entrySet()) {
            if (s.getValue().contains(ep)) return "S" + s.getKey() + " · E" + ep.num;
        }
        return "E" + ep.num;
    }

    private Item find(String episodeId) {
        for (List<Item> l : seasons.values()) for (Item e : l) if (e.id.equals(episodeId)) return e;
        return null;
    }

    private void playEpisode(Item ep) {
        for (List<Item> l : seasons.values()) {
            int i = l.indexOf(ep);
            if (i >= 0) {
                Open.play(this, l, i);
                return;
            }
        }
    }

    // ------------------------------------------------------------ data

    private void load() {
        meta.setText("Loading episodes…");
        Async.run(new Async.Work<JSONObject>() {
            public JSONObject run() throws Exception { return api.seriesInfo(series.id); }
        }, new Async.Done<JSONObject>() {
            public void done(JSONObject r, Exception e) {
                if (isFinishing()) return;
                if (e != null || r == null) {
                    meta.setText("Couldn't load episodes — " + Async.message(e));
                    return;
                }
                parse(r);
            }
        });
    }

    private void parse(JSONObject r) {
        JSONObject info = r.optJSONObject("info");
        if (info != null) {
            String p = MovieActivity.str(info, "plot", null);
            if (p != null) {
                series.plot = p;
                plot.setText(p);
            }
            String year = MovieActivity.str(info, "releaseDate", MovieActivity.str(info, "releasedate", null));
            if (year != null && year.length() >= 4) year = year.substring(0, 4);
            String m = XtreamApi.joinNonEmpty(year, MovieActivity.str(info, "genre", null),
                    XtreamApi.rating(MovieActivity.str(info, "rating", null)));
            meta.setText(m == null ? "" : m);
            JSONArray bd = info.optJSONArray("backdrop_path");
            String bdu = bd != null && bd.length() > 0 ? bd.optString(0, null) : null;
            if (bdu != null && !bdu.isEmpty()) loadBackdrop(bdu);
            else if (series.icon == null) loadBackdrop(MovieActivity.str(info, "cover", null));
            if (series.icon == null) series.icon = MovieActivity.str(info, "cover", null);
            trailer = TrailerActivity.youtubeId(MovieActivity.str(info, "youtube_trailer", MovieActivity.str(info, "trailer", null)));
        } else {
            meta.setText(series.info == null ? "" : series.info);
        }

        Object eps = r.opt("episodes");
        if (eps instanceof JSONObject) {
            JSONObject o = (JSONObject) eps;
            Iterator<String> keys = o.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                addEpisodes(o.optJSONArray(k), parseInt(k, 0));
            }
        } else if (eps instanceof JSONArray) {
            JSONArray a = (JSONArray) eps;
            for (int i = 0; i < a.length(); i++) {
                Object x = a.opt(i);
                if (x instanceof JSONArray) addEpisodes((JSONArray) x, i + 1);
                else if (x instanceof JSONObject) {
                    JSONArray one = new JSONArray();
                    one.put(x);
                    addEpisodes(one, 0);
                }
            }
        }

        seasonRow.removeAllViews();
        if (seasons.isEmpty()) {
            meta.setText("No episodes found for this series");
            return;
        }
        Item last = lastWatched();
        Item lastEp = last == null ? null : find(last.id);
        int startSeason = seasons.firstKey();
        if (lastEp != null) {
            for (Map.Entry<Integer, List<Item>> s : seasons.entrySet()) if (s.getValue().contains(lastEp)) startSeason = s.getKey();
        }
        showSeason(startSeason);
        buildButtons();
    }

    private static String seasonName(int k) { return k == 0 ? "Specials" : "Season " + k; }

    private TextView seasonPicker;

    private void buildSeasonBar() {
        seasonRow.removeAllViews();
        if (seasons.size() == 1) {
            TextView t = Ui.title(this, seasonName(activeSeason), 17);
            seasonRow.addView(t);
        } else {
            seasonPicker = Page.dropdown(this, seasonName(activeSeason));
            seasonPicker.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    List<Sheet.Option> o = new ArrayList<>();
                    int i = 0, at = 0;
                    for (final Integer k : seasons.keySet()) {
                        if (k == activeSeason) at = i;
                        i++;
                        o.add(new Sheet.Option(seasonName(k) + "  ·  " + seasons.get(k).size() + " episodes", k == activeSeason, new Runnable() {
                            public void run() { showSeason(k); }
                        }));
                    }
                    Sheet.show(SeriesActivity.this, "Seasons", null, o, at);
                }
            });
            seasonRow.addView(seasonPicker);
        }
        List<Item> eps = seasons.get(activeSeason);
        TextView n = Ui.text(this, eps == null ? "" : eps.size() + (eps.size() == 1 ? " episode" : " episodes"), 13, Ui.TEXT3);
        n.setGravity(Gravity.END);
        seasonRow.addView(n, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private void addEpisodes(JSONArray arr, int fallbackSeason) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i);
            if (e == null) continue;
            int season = e.has("season") ? e.optInt("season", fallbackSeason) : fallbackSeason;
            Item it = new Item(Item.EPISODE, e.optString("id"), "");
            String num = e.optString("episode_num", String.valueOf(i + 1));
            String t = MovieActivity.str(e, "title", series.name);
            it.num = num;
            it.name = t;
            it.ext = e.optString("container_extension", "mp4");
            it.parentId = series.id;
            it.parentName = series.name;
            it.parentIcon = series.icon;
            JSONObject ei = e.optJSONObject("info");
            if (ei != null) {
                String d = MovieActivity.str(ei, "duration", null);
                if (d != null && d.length() == 8) d = MovieActivity.shortDuration(d);
                it.duration = d;
                it.plot = MovieActivity.str(ei, "plot", null);
                it.icon = MovieActivity.str(ei, "movie_image", null);
            }
            if (it.id.isEmpty()) continue;
            List<Item> list = seasons.get(season);
            if (list == null) {
                list = new ArrayList<>();
                seasons.put(season, list);
            }
            list.add(it);
        }
    }

    private void showSeason(int key) {
        boolean changed = key != activeSeason;
        activeSeason = key;
        buildSeasonBar();
        buildEpisodes();
        if (changed) {
            episodeRow.setAlpha(0f);
            episodeRow.setTranslationY(Ui.dp(this, 16));
            episodeRow.animate().alpha(1f).translationY(0).setDuration(260)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
        }
    }

    /** Netflix-style episode list: thumbnail, title and time on one line, the plot underneath. */
    private void buildEpisodes() {
        episodeRow.removeAllViews();
        final List<Item> eps = seasons.get(activeSeason);
        if (eps == null) return;
        int side = Ui.dp(this, 16);
        for (final Item ep : eps) {
            long pos = prefs.position(ep), dur = prefs.duration(ep);
            String sub = pos > 0 && dur > 0 ? fmt(Math.max(0, dur - pos)) + " left" : ep.duration;
            String img = ep.icon != null ? ep.icon : series.icon;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(side, Ui.dp(this, 10), side, Ui.dp(this, 10));
            Ui.ripple(row, null, 0);
            LinearLayout line = new LinearLayout(this);
            line.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout thumb = new FrameLayout(this);
            thumb.setBackground(Ui.rounded(this, Ui.SURFACE, 0, 6));
            thumb.setClipToOutline(true);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.addView(iv, match());
            Icon play = new Icon(this, Icon.Kind.PLAY, Ui.W1);
            int pp = Ui.dp(this, 9);
            play.setPadding(pp + Ui.dp(this, 2), pp, pp, pp);
            play.setBackground(Ui.rounded(this, 0x80000000, 0, 18));
            thumb.addView(play, new FrameLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34), Gravity.CENTER));
            if (pos > 0 && dur > 0) {
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3), Gravity.BOTTOM);
                thumb.addView(Cards.progressBar(this, pos / (float) dur), bp);
            }
            ImageLoader.load(iv, img, 400, 225);
            line.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(this, 128), Ui.dp(this, 72)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(this, 12), 0, 0, 0);
            TextView t = Ui.lines(Ui.medium(this, ep.num + ". " + ep.name, 14, Ui.TEXT), 2);
            texts.addView(t);
            if (sub != null && !sub.isEmpty()) {
                TextView d = Ui.text(this, sub, 12, Ui.TEXT2);
                d.setPadding(0, Ui.dp(this, 4), 0, 0);
                texts.addView(d);
            }
            line.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.addView(line);
            if (ep.plot != null && !ep.plot.isEmpty()) {
                TextView pl = Ui.lines(Ui.text(this, ep.plot, 12.5f, Ui.TEXT2), 3);
                pl.setLineSpacing(0, 1.25f);
                pl.setPadding(0, Ui.dp(this, 8), 0, 0);
                row.addView(pl);
            }
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { Open.play(SeriesActivity.this, eps, eps.indexOf(ep)); }
            });
            episodeRow.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    static String fmt(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.UK, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.UK, "%d:%02d", s / 60, s % 60);
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!seasons.isEmpty()) {
            buildEpisodes();
            buildButtons();
        }
    }
}
