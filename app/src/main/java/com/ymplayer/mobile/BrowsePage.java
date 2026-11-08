package com.ymplayer.mobile;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Live TV, Movies or Series: a category picker at the top and the list below
 * (channels as rows, movies and series as a poster grid). Changing category slides the new list in.
 */
class BrowsePage extends Page {
    static final String FAV = "__fav", RECENT = "__recent", CONTINUE = "__continue", NEW = "__new", ALL = "__all";
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    private final String type;
    private final boolean live;
    private TextView picker, count, emptyText;
    private LinearLayout bar, searchBar;
    private android.widget.EditText searchBox;
    private boolean searching;
    private PhoneGuide guideView;
    private Icon guideToggle;
    private int searchToken;
    private ProgressBar spinner;
    private AbsListView content;
    private ContentAdapter adapter;
    private final List<Item> categories = new ArrayList<>();
    private Item currentCat;
    private int currentIndex = -1, loadToken;
    private List<Item> shown = new ArrayList<>();

    BrowsePage(MainActivity a, String type) {
        super(a);
        this.type = type;
        this.live = Item.LIVE.equals(type);
    }

    int ambient() { return live ? Ambient.LIVE : Item.MOVIE.equals(type) ? Ambient.MOVIES : Ambient.SERIES; }

    private String title() { return live ? "Live TV" : Item.MOVIE.equals(type) ? "Movies" : "Series"; }

    View build() {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        View guideBtn = null;
        if (live) {
            guideBtn = iconButton(Icon.Kind.GUIDE, new View.OnClickListener() {
                public void onClick(View v) { showGuide(guideView.getVisibility() != View.VISIBLE, true); }
            });
            guideToggle = (Icon) guideBtn;
        }
        col.addView(header(title(), guideBtn));

        // [Category ▾]  [♥ Favourites]  [🔍 Search]
        final FrameLayout barBox = new FrameLayout(a);
        bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), 0);
        picker = dropdown(a, "Categories");
        picker.setMaxWidth(a.getResources().getDisplayMetrics().widthPixels - Ui.dp(a, 262)); // room for the other two buttons
        picker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { openPicker(); }
        });
        bar.addView(picker);
        View favs = pill(Icon.Kind.HEART, "Favourites", new View.OnClickListener() {
            public void onClick(View v) { openFavouriteCategories(); }
        });
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        fl.leftMargin = Ui.dp(a, 8);
        bar.addView(favs, fl);
        View find = pill(Icon.Kind.SEARCH, "Search", new View.OnClickListener() {
            public void onClick(View v) { openSearch(); }
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.leftMargin = Ui.dp(a, 8);
        bar.addView(find, sl);
        barBox.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        barBox.addView(buildSearchBar(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(barBox);
        count = Ui.oneLine(Ui.text(a, "", 12, Ui.TEXT3));
        count.setPadding(Ui.dp(a, 18), 0, Ui.dp(a, 16), Ui.dp(a, 8));
        count.setVisibility(View.GONE); // only used for search results
        barBox.setPadding(0, 0, 0, Ui.dp(a, 12));
        col.addView(count);

        FrameLayout box = new FrameLayout(a);
        adapter = new ContentAdapter();
        if (live) {
            ListView l = new ListView(a);
            l.setDivider(null);
            l.setSelector(new android.graphics.drawable.ColorDrawable(0));
            l.setPadding(0, 0, 0, Ui.dp(a, 12));
            l.setClipToPadding(false);
            content = l;
        } else {
            GridView g = new GridView(a);
            g.setNumColumns(GridView.AUTO_FIT);
            g.setColumnWidth(Ui.dp(a, 106));
            g.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
            g.setHorizontalSpacing(Ui.dp(a, 8));
            g.setVerticalSpacing(Ui.dp(a, 14));
            g.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 16));
            g.setClipToPadding(false);
            g.setSelector(new android.graphics.drawable.ColorDrawable(0));
            content = g;
        }
        content.setVerticalScrollBarEnabled(false);
        content.setAdapter(adapter);
        content.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos < shown.size()) Open.item(a, shown.get(pos), shown);
            }
        });
        content.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (pos >= shown.size()) return false;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                toggleFavMenu(shown.get(pos));
                return true;
            }
        });
        box.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (live) {
            guideView = new PhoneGuide(a);
            guideView.setVisibility(View.GONE);
            guideView.setListener(new PhoneGuide.Listener() {
                public void onChannel(int row) {
                    if (row < shown.size()) Open.item(a, shown.get(row), shown);
                }

                public void onProgramme(final int row, XtreamApi.Programme p) {
                    final Item ch = shown.get(row);
                    long now = System.currentTimeMillis() / 1000L;
                    boolean onNow = p.start <= now && now < p.stop;
                    String sub = p.timeRange() + "  ·  " + ch.name + (onNow ? "  ·  On now" : "")
                            + (p.desc == null || p.desc.isEmpty() ? "" : "\n\n" + p.desc);
                    List<Sheet.Option> o = new ArrayList<>();
                    o.add(new Sheet.Option(onNow ? "Watch now" : "Watch " + ch.name, false, new Runnable() {
                        public void run() { Open.item(a, ch, shown); }
                    }));
                    o.add(new Sheet.Option("Close", false, null));
                    Sheet.show(a, p.title, sub, o, 0);
                }

                public void onNeedListings(List<Item> chs) {
                    Guide.load(api, chs, new Runnable() {
                        public void run() { if (!a.isFinishing()) guideView.listingsChanged(); }
                    });
                }
            });
            FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            glp.leftMargin = glp.rightMargin = Ui.dp(a, 8);
            box.addView(guideView, glp);
        }

        emptyText = Ui.text(a, "", 15, Ui.TEXT2);
        emptyText.setGravity(Gravity.CENTER);
        int p = Ui.dp(a, 32);
        emptyText.setPadding(p, 0, p, Ui.dp(a, 60));
        box.addView(emptyText, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        spinner = new ProgressBar(a);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.TEXT2));
        spinner.setVisibility(View.GONE);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 36), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        slp.topMargin = Ui.dp(a, 80);
        box.addView(spinner, slp);
        col.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        loadCategories();
        return col;
    }

    @Override
    void onReselect() { content.smoothScrollToPosition(0); }

    private int filterVersion;

    @Override
    void onResume() {
        if (filterVersion != 0 && filterVersion != Filters.version()) { // changed in Settings
            loadCategories();
            return;
        }
        // Favourites / recent / continue lists may have changed while you were away
        if (searching) {
            adapter.notifyDataSetChanged();
            return;
        }
        if (currentCat != null && isSpecial(currentCat.id)) show(special(currentCat.id), 0);
        else adapter.notifyDataSetChanged();
    }

    // ------------------------------------------------------------ categories

    private void loadCategories() {
        spinner.setVisibility(View.VISIBLE);
        Async.run(new Async.Work<List<Item>>() {
            public List<Item> run() throws Exception { return Filters.categories(prefs, type, Repo.categories(api, type)); } // hidden ones left out
        }, new Async.Done<List<Item>>() {
            public void done(List<Item> cats, Exception e) {
                if (a.isFinishing()) return;
                spinner.setVisibility(View.GONE);
                filterVersion = Filters.version();
                categories.clear();
                categories.add(new Item(Item.CATEGORY, FAV, live ? "Favourite Channels" : Item.MOVIE.equals(type) ? "Favourite Movies" : "Favourite Series"));
                if (live) categories.add(new Item(Item.CATEGORY, RECENT, "Recent Channels"));
                else {
                    categories.add(new Item(Item.CATEGORY, CONTINUE, "Continue Watching"));
                    categories.add(new Item(Item.CATEGORY, NEW, "Recently Added"));
                }
                categories.add(new Item(Item.CATEGORY, ALL, live ? "All Channels" : "All " + title()));
                int firstReal = categories.size();
                if (cats != null) categories.addAll(cats);
                if (e != null) emptyText.setText("Couldn't load categories\n" + Async.message(e));
                // Last category you used, else Favourites if you have any, else everything
                String saved = prefs.raw().getString(prefs.ns() + "m_cat_" + type, null);
                int start = -1;
                for (int i = 0; saved != null && i < categories.size(); i++) if (categories.get(i).id.equals(saved)) start = i;
                if (start < 0) start = !prefs.favs(type).isEmpty() ? 0 : firstReal - 1;
                openCategory(start);
                if (live && prefs.raw().getBoolean(prefs.ns() + "m_live_guide", false)) showGuide(true, false);
            }
        });
    }

    /** The full category list, with a search box and a heart to favourite each category. */
    private void openPicker() {
        if (categories.isEmpty()) return;
        List<Sheet.Option> o = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            final int idx = i;
            final Item c = categories.get(i);
            Sheet.Option opt = new Sheet.Option(c.name, i == currentIndex, new Runnable() {
                public void run() { openCategory(idx); }
            });
            if (!c.id.startsWith("__")) {
                opt.heart(prefs.isFavCategory(type, c.id), new Runnable() {
                    public void run() { prefs.toggleFavCategory(type, c.id); }
                });
                opt.longPress(new Runnable() { // press and hold: hide it
                    public void run() {
                        List<Sheet.Option> h = new ArrayList<>();
                        h.add(new Sheet.Option("Hide this category", false, new Runnable() {
                            public void run() {
                                Filters.setCategoryHidden(prefs, type, c.id, true);
                                android.widget.Toast.makeText(a, "Hidden — bring it back in Settings → Hidden Categories", android.widget.Toast.LENGTH_LONG).show();
                                loadCategories();
                            }
                        }, true));
                        h.add(new Sheet.Option("Cancel", false, null));
                        Sheet.show(a, c.name, null, h, 0);
                    }
                });
            }
            o.add(opt);
        }
        Sheet.show(a, title() + " categories", "Tap ♡ to favourite  ·  press and hold to hide", o, currentIndex, "Search categories");
    }

    /** Just the categories you've hearted. */
    private void openFavouriteCategories() {
        if (categories.isEmpty()) return;
        List<Sheet.Option> o = new ArrayList<>();
        int at = 0;
        for (String id : prefs.favCategories(type)) {
            for (int i = 0; i < categories.size(); i++) {
                final Item c = categories.get(i);
                if (!c.id.equals(id)) continue;
                final int idx = i;
                if (i == currentIndex) at = o.size();
                o.add(new Sheet.Option(c.name, i == currentIndex, new Runnable() {
                    public void run() { openCategory(idx); }
                }).heart(true, new Runnable() {
                    public void run() { prefs.toggleFavCategory(type, c.id); }
                }));
            }
        }
        if (o.isEmpty()) {
            o.add(new Sheet.Option("Choose categories", false, new Runnable() {
                public void run() { openPicker(); }
            }));
            Sheet.show(a, "Favourite categories", "Tap the ♡ next to any category to add it here.", o, 0);
            return;
        }
        Sheet.show(a, "Favourite categories", null, o, at);
    }

    private View pill(Icon.Kind k, String label, View.OnClickListener l) {
        LinearLayout p = new LinearLayout(a);
        p.setGravity(Gravity.CENTER_VERTICAL);
        int px = Ui.dp(a, 12), py = Ui.dp(a, 8);
        p.setPadding(px, py, px + Ui.dp(a, 2), py);
        android.graphics.drawable.GradientDrawable g = Ui.rounded(a, 0, 0, 18);
        g.setStroke(Ui.dp(a, 1), Ui.DARK ? 0x66EBEBF5 : 0x553C3C43);
        Ui.ripple(p, g, 18);
        p.addView(new Icon(a, k, Ui.TEXT), new LinearLayout.LayoutParams(Ui.dp(a, 15), Ui.dp(a, 15)));
        TextView t = Ui.oneLine(Ui.medium(a, label, 14, Ui.TEXT));
        t.setTypeface(Ui.semibold());
        t.setPadding(Ui.dp(a, 6), 0, 0, 0);
        p.addView(t);
        p.setOnClickListener(l);
        return p;
    }

    // ------------------------------------------------------------ search within this section

    private View buildSearchBar() {
        searchBar = new LinearLayout(a);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 6), 0);
        searchBar.setVisibility(View.GONE);
        FrameLayout field = new FrameLayout(a);
        String what = live ? "channels" : Item.MOVIE.equals(type) ? "movies" : "series";
        searchBox = Ui.input(a, "Search " + what, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchBox.setPadding(Ui.dp(a, 38), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
        searchBox.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.sp(a, 15));
        searchBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.addView(searchBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Icon glass = new Icon(a, Icon.Kind.SEARCH, Ui.TEXT2);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(Ui.dp(a, 17), Ui.dp(a, 17), Gravity.START | Gravity.CENTER_VERTICAL);
        gl.leftMargin = Ui.dp(a, 12);
        field.addView(glass, gl);
        searchBar.addView(field, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cancel = Ui.medium(a, "Cancel", 14, Ui.TEXT);
        cancel.setPadding(Ui.dp(a, 12), Ui.dp(a, 10), Ui.dp(a, 12), Ui.dp(a, 10));
        Ui.ripple(cancel, null, 16);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeSearch(); }
        });
        searchBar.addView(cancel);
        final Runnable run = new Runnable() { public void run() { runSearch(); } };
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
            public void onTextChanged(CharSequence x, int b, int c, int d) {}
            public void afterTextChanged(android.text.Editable e) {
                Async.MAIN.removeCallbacks(run);
                Async.MAIN.postDelayed(run, 250);
            }
        });
        searchBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int act, android.view.KeyEvent e) {
                Async.MAIN.removeCallbacks(run);
                runSearch();
                hideKeyboard();
                return true;
            }
        });
        return searchBar;
    }

    private void openSearch() {
        searching = true;
        bar.animate().alpha(0f).setDuration(120).withEndAction(new Runnable() {
            public void run() { bar.setVisibility(View.INVISIBLE); }
        }).start();
        searchBar.setAlpha(0f);
        searchBar.setTranslationX(Ui.dp(a, 24));
        searchBar.setVisibility(View.VISIBLE);
        searchBar.animate().alpha(1f).translationX(0).setDuration(220).setInterpolator(EASE).start();
        searchBox.setText("");
        searchBox.requestFocus();
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchBox, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        show(new ArrayList<Item>(), 0);
        String what = live ? "channel" : Item.MOVIE.equals(type) ? "film" : "series";
        emptyText.setText("Type the name of a " + what);
        count.setText("Searching all of " + title());
    }

    private void closeSearch() {
        if (!searching) return;
        searching = false;
        searchToken++;
        hideKeyboard();
        searchBar.animate().alpha(0f).setDuration(120).withEndAction(new Runnable() {
            public void run() { searchBar.setVisibility(View.GONE); }
        }).start();
        bar.setVisibility(View.VISIBLE);
        bar.animate().alpha(1f).setDuration(200).start();
        int idx = currentIndex;
        currentIndex = -1;
        openCategory(idx);
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
    }

    /** Same matching as the Search tab: ignores punctuation, spaces and accents; names starting with it come first. */
    private void runSearch() {
        final String q = searchBox.getText().toString().trim();
        final int t = ++searchToken;
        if (Item.squash(q).length() < 2) {
            show(new ArrayList<Item>(), 0);
            emptyText.setText(q.isEmpty() ? "Type the name of a " + (live ? "channel" : Item.MOVIE.equals(type) ? "film" : "series") : "Keep typing…");
            count.setText("Searching all of " + title());
            return;
        }
        spinner.setVisibility(View.VISIBLE);
        Async.run(new Async.Work<List<Item>>() {
            public List<Item> run() throws Exception {
                List<Item> all = Filters.items(api, prefs, type, Repo.streams(api, type, null));
                if (t != searchToken) return null;
                return Match.rank(all, q, 300, null); // best matches first (see Match)
            }
        }, new Async.Done<List<Item>>() {
            public void done(List<Item> r, Exception e) {
                if (a.isFinishing() || t != searchToken || !searching || r == null) return;
                spinner.setVisibility(View.GONE);
                show(r, 0);
                if (r.isEmpty()) emptyText.setText("No results for \"" + q + "\"");
                count.setText(r.isEmpty() ? "" : (r.size() >= 300 ? "300+" : String.valueOf(r.size())) + (r.size() == 1 ? " result" : " results"));
            }
        });
    }

    @Override
    boolean onBack() {
        if (searching) {
            closeSearch();
            return true;
        }
        return false;
    }

    private boolean isSpecial(String id) { return id.startsWith("__") && !ALL.equals(id) && !NEW.equals(id); }

    private List<Item> special(String id) {
        if (FAV.equals(id)) return prefs.favs(type);
        if (RECENT.equals(id)) return prefs.history(true);
        if (CONTINUE.equals(id)) return Item.MOVIE.equals(type) ? prefs.continueMovies() : prefs.continueSeries();
        return new ArrayList<>();
    }

    private void openCategory(int idx) {
        if (idx < 0 || idx >= categories.size()) return;
        final int dir = currentIndex < 0 || idx == currentIndex ? 0 : (idx > currentIndex ? 1 : -1);
        currentIndex = idx;
        final Item cat = categories.get(idx);
        currentCat = cat;
        prefs.raw().edit().putString(prefs.ns() + "m_cat_" + type, cat.id).apply();
        picker.setText(cat.name + "  ▾");
        count.setText("");
        final int token = ++loadToken;
        if (isSpecial(cat.id)) {
            show(special(cat.id), dir);
            return;
        }
        emptyText.setText("");
        spinner.setVisibility(View.VISIBLE);
        Async.run(new Async.Work<List<Item>>() {
            public List<Item> run() throws Exception {
                if (NEW.equals(cat.id)) {
                    List<Item> l = Filters.items(api, prefs, type, Repo.recentlyAdded(api, type, 300));
                    return l.size() > 60 ? new ArrayList<>(l.subList(0, 60)) : l;
                }
                if (ALL.equals(cat.id)) return Filters.items(api, prefs, type, Repo.streams(api, type, null));
                return Repo.streams(api, type, cat.id);
            }
        }, new Async.Done<List<Item>>() {
            public void done(List<Item> list, Exception e) {
                if (a.isFinishing() || token != loadToken) return;
                spinner.setVisibility(View.GONE);
                if (e != null) {
                    show(new ArrayList<Item>(), dir);
                    emptyText.setText("Couldn't load this list\n" + Async.message(e));
                    return;
                }
                show(list, dir);
            }
        });
    }

    /** Swap the list in, sliding from the direction you moved in the category list (like the TV app). */
    private void show(List<Item> list, int dir) {
        spinner.setVisibility(View.GONE);
        shown = list == null ? new ArrayList<Item>() : list;
        adapter.notifyDataSetChanged();
        if (guideView != null && guideView.getVisibility() == View.VISIBLE) guideView.setChannels(shown);
        content.setSelection(0);
        int n = shown.size();
        count.setVisibility(searching ? View.VISIBLE : View.GONE);
        if (n == 0) {
            String what = live ? "channel" : Item.MOVIE.equals(type) ? "movie" : "series";
            if (searching || currentCat == null) emptyText.setText("");
            else if (FAV.equals(currentCat.id)) emptyText.setText("No favourites yet\nPress and hold any " + what + " to add it.");
            else if (RECENT.equals(currentCat.id)) emptyText.setText("Channels you watch will show here.");
            else if (CONTINUE.equals(currentCat.id)) emptyText.setText("Things you start watching will show here.");
            else emptyText.setText("Nothing in this category");
        } else {
            emptyText.setText("");
        }
        if (dir != 0) {
            content.setAlpha(0f);
            content.setTranslationX(dir * Ui.dp(a, 40));
            content.animate().alpha(1f).translationX(0).setDuration(260).setInterpolator(EASE).start();
        }
    }

    // ------------------------------------------------------------ TV guide (Live TV)

    /** Switch between the channel list and the TV guide (remembered for next time). */
    private void showGuide(boolean on, boolean animate) {
        if (guideView == null) return;
        prefs.raw().edit().putBoolean(prefs.ns() + "m_live_guide", on).apply();
        guideToggle.setKind(on ? Icon.Kind.LIST : Icon.Kind.GUIDE);
        final View in = on ? guideView : content, out = on ? content : guideView;
        if (on) guideView.setChannels(shown);
        in.setVisibility(View.VISIBLE);
        if (animate) {
            in.setAlpha(0f);
            in.setTranslationY(Ui.dp(a, 24));
            in.animate().alpha(1f).translationY(0).setDuration(260).setInterpolator(EASE).start();
            out.animate().alpha(0f).setDuration(160).withEndAction(new Runnable() {
                public void run() {
                    out.setVisibility(View.GONE);
                    out.setAlpha(1f);
                }
            }).start();
        } else {
            out.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ rows and posters

    private class ContentAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup parent) {
            return live ? channelRow(i, v) : posterCell(i, v);
        }
    }

    private static class RowHolder {
        ImageView logo;
        TextView initials, name, sub;
        Icon heart;
        Item item;
    }

    private View channelRow(int i, View v) {
        final RowHolder h;
        if (v == null) {
            LinearLayout r = new LinearLayout(a);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(Ui.dp(a, 16), Ui.dp(a, 6), Ui.dp(a, 6), Ui.dp(a, 6));
            Ui.ripple(r, null, 0);
            r.setClickable(false);
            h = new RowHolder();
            FrameLayout box = new FrameLayout(a);
            box.setBackground(Ui.rounded(a, Ui.SURFACE, 0, 8));
            box.setClipToOutline(true);
            h.logo = new ImageView(a);
            h.logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int lp = Ui.dp(a, 6);
            h.logo.setPadding(lp, lp, lp, lp);
            box.addView(h.logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.initials = Ui.title(a, "", 15);
            h.initials.setTextColor(Ui.TEXT3);
            h.initials.setGravity(Gravity.CENTER);
            box.addView(h.initials, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            r.addView(box, new LinearLayout.LayoutParams(Ui.dp(a, 80), Ui.dp(a, 48)));
            LinearLayout texts = new LinearLayout(a);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 8), 0);
            h.name = Ui.oneLine(Ui.medium(a, "", 15, Ui.TEXT));
            h.sub = Ui.oneLine(Ui.text(a, "", 12, Ui.TEXT3));
            h.sub.setPadding(0, Ui.dp(a, 3), 0, 0);
            texts.addView(h.name);
            texts.addView(h.sub);
            r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            h.heart = new Icon(a, Icon.Kind.HEART, Ui.TEXT3);
            int hp = Ui.dp(a, 12);
            h.heart.setPadding(hp, hp, hp, hp);
            Ui.ripple(h.heart, null, 22);
            h.heart.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) {
                    if (h.item == null) return;
                    prefs.toggleFav(h.item);
                    x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    paintHeart(h);
                    h.heart.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction(new Runnable() {
                        public void run() { h.heart.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); }
                    }).start();
                    if (FAV.equals(currentCat.id)) show(special(FAV), 0);
                }
            });
            r.addView(h.heart, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
            r.setTag(h);
            v = r;
        } else {
            h = (RowHolder) v.getTag();
        }
        Item it = shown.get(i);
        h.item = it;
        h.name.setText(it.name);
        h.sub.setText(it.num != null && !it.num.isEmpty() ? "Channel " + it.num : "");
        h.sub.setVisibility(h.sub.getText().length() == 0 ? View.GONE : View.VISIBLE);
        h.initials.setText(it.icon == null || it.icon.isEmpty() ? Cards.initials(it.name) : "");
        ImageLoader.load(h.logo, it.icon, 200, 120);
        paintHeart(h);
        return v;
    }

    private void paintHeart(RowHolder h) {
        boolean fav = h.item != null && prefs.isFav(h.item);
        h.heart.setKind(fav ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART);
        h.heart.setColor(fav ? Ui.RED : Ui.TEXT3);
    }

    private static class PosterHolder {
        ImageView img;
        TextView placeholder, title;
        View heart;
    }

    private View posterCell(int i, View v) {
        PosterHolder h;
        if (v == null) {
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            h = new PosterHolder();
            FrameLayout art = new AspectFrame(a, 1.5f);
            art.setBackground(Ui.rounded(a, Ui.SURFACE, 0, 8));
            art.setClipToOutline(true);
            h.placeholder = Ui.lines(Ui.title(a, "", 14), 4);
            h.placeholder.setTextColor(Ui.TEXT3);
            h.placeholder.setGravity(Gravity.CENTER);
            int p = Ui.dp(a, 8);
            h.placeholder.setPadding(p, p, p, p);
            art.addView(h.placeholder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.img = new ImageView(a);
            h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.addView(h.img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            Icon heart = new Icon(a, Icon.Kind.HEART_FILLED, Ui.W1);
            heart.setElevation(Ui.dp(a, 2));
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(Ui.dp(a, 16), Ui.dp(a, 16), Gravity.TOP | Gravity.END);
            hl.topMargin = hl.rightMargin = Ui.dp(a, 6);
            art.addView(heart, hl);
            h.heart = heart;
            Ui.rippleOver(art, 8);
            art.setClickable(false);
            art.setDuplicateParentStateEnabled(true);
            cell.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            h.title = Ui.oneLine(Ui.text(a, "", 12, Ui.TEXT2));
            h.title.setPadding(Ui.dp(a, 1), Ui.dp(a, 5), 0, 0);
            cell.addView(h.title);
            cell.setTag(h);
            v = cell;
        } else {
            h = (PosterHolder) v.getTag();
        }
        Item it = shown.get(i);
        h.title.setText(it.name);
        h.placeholder.setText(it.name); // shows until (or if) the poster loads
        h.heart.setVisibility(prefs.isFav(it) ? View.VISIBLE : View.GONE);
        ImageLoader.load(h.img, it.icon, 240, 360);
        return v;
    }

    /** A frame whose height follows its width (posters are 2:3). */
    static class AspectFrame extends FrameLayout {
        private final float ratio;

        AspectFrame(android.content.Context c, float ratio) {
            super(c);
            this.ratio = ratio;
        }

        @Override
        protected void onMeasure(int w, int h) {
            int width = MeasureSpec.getSize(w);
            super.onMeasure(w, MeasureSpec.makeMeasureSpec((int) (width * ratio), MeasureSpec.EXACTLY));
        }
    }
}
