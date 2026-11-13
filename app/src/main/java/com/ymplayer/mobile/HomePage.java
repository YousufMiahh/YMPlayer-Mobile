package com.ymplayer.mobile;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Home: a big featured title at the top (from the Netflix UK Top 10 when your provider has it),
 * then Continue Watching, Top 10s, recent channels, favourites and what's new.
 */
class HomePage extends Page {
    private ScrollView scroll;

    /** For the tutorial: the Cast and Settings buttons, and the first row of cards. */
    private View iconsBox;

    View topIcons() { return iconsBox; }

    View firstRow() {
        if (localRows != null && localRows.getChildCount() > 0) return localRows.getChildAt(0);
        if (remoteRows != null && remoteRows.getChildCount() > 0) return remoteRows.getChildAt(0);
        return null;
    }
    private Icon castBtn;
    private final Cast.Changes castChanges = new Cast.Changes() {
        void changed(boolean on) { paintCast(); }
    };

    private void paintCast() {
        if (castBtn == null) return;
        boolean on = Cast.connected(a);
        castBtn.setKind(on ? Icon.Kind.CAST_ON : Icon.Kind.CAST);
        castBtn.setColor(on ? Ui.ACCENT : Ui.TEXT);
    }
    private LinearLayout localRows, remoteRows;
    private TextView empty;
    private List<Item> newMovies, newSeries, topMovies, topSeries;
    private List<Recs.Row> recRows;
    private String recSig = "";
    private String topSig = "";
    private static final long TOP10_EVERY = 60 * 60_000L;
    private final Runnable top10Tick = new Runnable() {
        public void run() {
            loadTop10();
            Async.MAIN.postDelayed(this, TOP10_EVERY);
        }
    };

    HomePage(MainActivity a) { super(a); }

    int ambient() { return Ambient.HOME; }

    View build() {
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(0, 0, 0, Ui.dp(a, 24));

        // Logo + name on the left, Settings on the right
        LinearLayout top = new LinearLayout(a);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(a, 16), Ui.statusBar(a) + Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 4));
        ImageView logo = new ImageView(a);
        try (InputStream in = a.getAssets().open("logo.png")) {
            logo.setImageBitmap(BitmapFactory.decodeStream(in));
        } catch (Exception ignored) {
        }
        top.addView(logo, new LinearLayout.LayoutParams(Ui.dp(a, 30), Ui.dp(a, 30)));
        TextView name = Ui.title(a, "YM Player", 20);
        name.setPadding(Ui.dp(a, 10), 0, 0, 0);
        top.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        castBtn = (Icon) iconButton(Icon.Kind.CAST, new View.OnClickListener() {
            public void onClick(View v) { Cast.pick(a); }
        });
        LinearLayout icons = new LinearLayout(a); // Cast + Settings together (highlighted together by the tutorial)
        icons.addView(castBtn);
        icons.addView(iconButton(Icon.Kind.SETTINGS, new View.OnClickListener() {
            public void onClick(View v) { a.startActivity(new Intent(a, SettingsActivity.class)); }
        }));
        top.addView(icons);
        iconsBox = icons;
        col.addView(top);

        localRows = new LinearLayout(a);
        localRows.setOrientation(LinearLayout.VERTICAL);
        localRows.setClipChildren(false);
        remoteRows = new LinearLayout(a);
        remoteRows.setOrientation(LinearLayout.VERTICAL);
        remoteRows.setClipChildren(false);
        col.addView(localRows);
        col.addView(remoteRows);

        empty = Ui.text(a, "", 15, Ui.TEXT2);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(Ui.dp(a, 32), Ui.dp(a, 90), Ui.dp(a, 32), 0);
        col.addView(empty, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(col);
        buildLocalRows();
        startLibrary();
        showCrashNotice();
        return scroll;
    }

    @Override
    void onReselect() { scroll.smoothScrollTo(0, 0); }

    @Override
    void onResume() {
        buildLocalRows();
        buildRemoteRows();
    }

    @Override
    void onStart() {
        Cast.listen(a, castChanges, true);
        paintCast();
        Async.MAIN.removeCallbacks(top10Tick);
        Async.MAIN.post(top10Tick); // check now (cheap if not due), then hourly while the app is open
        loadRecs(); // "Because you watched…" (cached for a few hours)
    }

    @Override
    void onStop() {
        Async.MAIN.removeCallbacks(top10Tick);
        Cast.listen(a, castChanges, false);
    }

    @Override
    void onDestroy() {
        Async.MAIN.removeCallbacks(top10Tick);
        Repo.onUpdated = null;
    }

    // ------------------------------------------------------------ rows

    private final Cards.OnPick pick = new Cards.OnPick() {
        public void pick(Item it, View card) {
            List<Item> siblings = null;
            if (Item.LIVE.equals(it.type)) {
                for (List<Item> l : new List[]{prefs.history(true), prefs.favs(Item.LIVE)}) {
                    for (Item x : l) if (x.key().equals(it.key())) { siblings = l; break; }
                    if (siblings != null) break;
                }
            }
            Open.item(a, it, siblings);
        }
    };

    private final Cards.OnMenu favMenu = new Cards.OnMenu() {
        public void menu(Item it, View card) { toggleFavMenu(it); }
    };

    /** Hold a Continue Watching or Queue card: Queue, Watched, Remove… */
    private final Cards.OnMenu continueMenu = new Cards.OnMenu() {
        public void menu(final Item it, View card) {
            ItemMenu.show(a, prefs, it, null, new Runnable() {
                public void run() { buildLocalRows(); }
            });
        }
    };

    private void buildLocalRows() {
        localRows.removeAllViews();
        List<Item> cw = prefs.homeShows("continue") ? prefs.continueWatching() : new ArrayList<Item>();
        if (!cw.isEmpty()) {
            localRows.addView(Cards.shelf(a, Cards.titleWithHint("Continue Watching", "Hold for options"), cw,
                    Cards.Shape.LANDSCAPE, prefs, pick, continueMenu));
        }
        List<Item> q = prefs.queue();
        if (!q.isEmpty()) {
            localRows.addView(Cards.shelf(a, Cards.titleWithHint("Your Queue", "Hold to remove"), q,
                    Cards.Shape.LANDSCAPE, prefs, pick, continueMenu));
        }
        if (prefs.homeShows("recent")) add(localRows, "Recent Channels", limit(prefs.history(true), 20), Cards.Shape.CHANNEL);
        if (prefs.homeShows("fav_live")) add(localRows, "Favourite Channels", prefs.favs(Item.LIVE), Cards.Shape.CHANNEL);
        if (prefs.homeShows("fav_movie")) add(localRows, "Favourite Movies", prefs.favs(Item.MOVIE), Cards.Shape.POSTER);
        if (prefs.homeShows("fav_series")) add(localRows, "Favourite Series", prefs.favs(Item.SERIES), Cards.Shape.POSTER);
        updateEmpty();
    }

    private void buildRemoteRows() {
        remoteRows.removeAllViews();
        if (prefs.homeShows("top_movies")) add(remoteRows, "Top 10 Movies in the UK", topMovies, Cards.Shape.RANKED);
        if (prefs.homeShows("top_series")) add(remoteRows, "Top 10 Series in the UK", topSeries, Cards.Shape.RANKED);
        if (recRows != null && prefs.homeShows("recommended")) for (Recs.Row r : recRows) add(remoteRows, r.title, r.items, Cards.Shape.POSTER);
        if (newMovies != null && prefs.homeShows("new_movies")) add(remoteRows, "Recently Added Movies", newMovies, Cards.Shape.POSTER);
        if (newSeries != null && prefs.homeShows("new_series")) add(remoteRows, "Recently Added Series", newSeries, Cards.Shape.POSTER);
        updateEmpty();
    }

    private void add(LinearLayout parent, String title, List<Item> items, Cards.Shape shape) {
        if (items == null || items.isEmpty()) return;
        parent.addView(Cards.shelf(a, title, items, shape, prefs, pick, favMenu));
    }

    private void updateEmpty() {
        boolean none = localRows.getChildCount() == 0 && remoteRows.getChildCount() == 0;
        if (none && empty.getText().length() == 0) {
            empty.setText("Welcome to YM Player\nPick Live TV, Movies or Series below to get started.");
        }
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
    }

    private static List<Item> limit(List<Item> l, int n) {
        return l.size() > n ? l.subList(0, n) : l;
    }

    // ------------------------------------------------------------ data

    /** Loads the saved library (instant) and refreshes it in the background when it's due. */
    private void startLibrary() {
        Repo.onUpdated = new Runnable() {
            public void run() {
                if (!a.isFinishing()) {
                    loadNewArrivals();
                    loadTop10();
                    loadRecs();
                }
            }
        };
        if (Repo.isRefreshing()) {
            loadNewArrivals();
            return;
        }
        if (Repo.isDue(prefs) && !Repo.hasLibrary(api)) {
            if (localRows.getChildCount() == 0) {
                empty.setText("Getting your channels, movies and series ready…\nThis only takes a while the first time.");
                empty.setVisibility(View.VISIBLE);
            }
            Async.run(new Async.Work<Boolean>() {
                public Boolean run() throws Exception {
                    Repo.refreshAll(api, prefs);
                    return true;
                }
            }, new Async.Done<Boolean>() {
                public void done(Boolean ok, Exception e) {
                    if (!a.isFinishing()) {
                        empty.setText("");
                        loadNewArrivals();
                    }
                }
            });
        } else {
            loadNewArrivals();
            Repo.preload(api);
            Repo.refreshIfDue(api, prefs, new Runnable() {
                public void run() { if (!a.isFinishing()) loadNewArrivals(); }
            });
        }
    }

    /** Newest 24, leaving out hidden categories and countries. */
    private List<Item> recent(String type) throws java.io.IOException {
        List<Item> l = Filters.items(api, prefs, type, Repo.recentlyAdded(api, type, 300));
        return l.size() > 24 ? new ArrayList<>(l.subList(0, 24)) : l;
    }

    private void loadNewArrivals() {
        Async.run(new Async.Work<List<Item>[]>() {
            @SuppressWarnings("unchecked")
            public List<Item>[] run() throws Exception {
                return new List[]{recent(Item.MOVIE), recent(Item.SERIES)};
            }
        }, new Async.Done<List<Item>[]>() {
            public void done(List<Item>[] r, Exception e) {
                if (a.isFinishing() || r == null) return;
                newMovies = r[0];
                newSeries = r[1];
                buildRemoteRows();
            }
        });
    }

    /** Netflix UK Top 10, matched to this provider's library. Checked hourly; only rebuilds if it changed. */
    private void loadTop10() {
        Async.runOn(Top10.WORKER, new Async.Work<List<Item>[]>() {
            @SuppressWarnings("unchecked")
            public List<Item>[] run() throws Exception {
                Top10.Data d = Top10.refresh(a.getApplicationContext());
                if (d == null) return null;
                List<Item> movies = Repo.cached(Item.MOVIE, null), series = Repo.cached(Item.SERIES, null);
                if (movies == null && Repo.hasLibrary(api)) movies = Repo.streams(api, Item.MOVIE, null);
                if (series == null && Repo.hasLibrary(api)) series = Repo.streams(api, Item.SERIES, null);
                return new List[]{Top10.match(d.films, movies), Top10.match(d.tv, series)};
            }
        }, new Async.Done<List<Item>[]>() {
            public void done(List<Item>[] r, Exception e) {
                if (a.isFinishing() || r == null) return;
                String sig = sig(r[0]) + "|" + sig(r[1]);
                if (sig.equals(topSig)) return;
                topSig = sig;
                topMovies = r[0];
                topSeries = r[1];
                buildRemoteRows();
            }
        });
    }

    /** "Because you watched …" rows (TMDB recommendations that your provider has). */
    private void loadRecs() {
        if (!prefs.homeShows("recommended")) return;
        Async.runOn(Top10.WORKER, new Async.Work<List<Recs.Row>>() {
            public List<Recs.Row> run() throws Exception { return Recs.rows(a.getApplicationContext(), api, prefs); }
        }, new Async.Done<List<Recs.Row>>() {
            public void done(List<Recs.Row> r, Exception e) {
                if (a.isFinishing() || r == null) return;
                StringBuilder b = new StringBuilder();
                for (Recs.Row row : r) b.append(row.title).append(sig(row.items));
                if (b.toString().equals(recSig)) return;
                recSig = b.toString();
                recRows = r;
                buildRemoteRows();
            }
        });
    }

    private static String sig(List<Item> l) {
        StringBuilder b = new StringBuilder();
        if (l != null) for (Item it : l) b.append(it.key()).append(',');
        return b.toString();
    }

    /** After a crash: explain what happened and offer the report. */
    private void showCrashNotice() {
        boolean vlc = prefs.takeVlcBrokenNotice();
        boolean fresh = App.crashIsNew(a);
        final String crash = fresh || vlc ? App.lastCrash(a) : null;
        if (!vlc && crash == null) return;
        List<Sheet.Option> o = new ArrayList<>();
        if (crash != null) {
            o.add(new Sheet.Option("Show error report", false, new Runnable() {
                public void run() {
                    TextView t = Ui.text(a, crash, 11, Ui.TEXT);
                    t.setTypeface(android.graphics.Typeface.MONOSPACE);
                    t.setTextIsSelectable(true);
                    int p = Ui.dp(a, 16);
                    t.setPadding(p, p, p, p);
                    ScrollView sv = new ScrollView(a);
                    sv.addView(t);
                    new android.app.AlertDialog.Builder(a, Ui.DARK ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                            .setTitle("Error report").setView(sv).setPositiveButton("OK", null).show();
                }
            }));
        }
        o.add(new Sheet.Option("OK", false, null));
        final String title = vlc ? "Switched to the Android player" : "Sorry about that";
        final String msg = vlc
                ? "The VLC player stopped the app on this phone, so movies and series use the Android player for now. You can try VLC again in Settings."
                : "YM Player closed unexpectedly last time. The error report helps fix the problem.";
        final List<Sheet.Option> opts = o;
        Async.MAIN.postDelayed(new Runnable() {
            public void run() { if (!a.isFinishing()) Sheet.show(a, title, msg, opts, 0); }
        }, 600);
    }
}
