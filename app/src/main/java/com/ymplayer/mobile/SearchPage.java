package com.ymplayer.mobile;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search channels, movies and series at once. Ignores punctuation, spaces and accents
 * ("spiderman" finds "Spider-Man"), and each section shows as soon as it's ready.
 */
class SearchPage extends Page {
    private static final String[] TYPES = {Item.LIVE, Item.MOVIE, Item.SERIES};
    private static final String[] TITLES = {"Channels", "Movies", "Series"};
    private static final int MAX = 40;

    private EditText box;
    private Icon clear;
    private ScrollView scroll;
    private final LinearLayout[] slots = new LinearLayout[3];
    private TextView status;
    private final boolean[] ready = new boolean[3];
    private final int[] found = new int[3];
    private volatile int token;
    private boolean shownOnce;
    private final Runnable pending = new Runnable() { public void run() { search(); } };

    SearchPage(MainActivity a) { super(a); }

    int ambient() { return Ambient.SEARCH; }

    View build() {
        LinearLayout page = new LinearLayout(a);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(header("Search", null));

        FrameLayout field = new FrameLayout(a);
        field.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 8));
        box = Ui.input(a, "Channels, movies and series", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        box.setPadding(Ui.dp(a, 44), Ui.dp(a, 12), Ui.dp(a, 44), Ui.dp(a, 12));
        box.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        field.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Icon glass = new Icon(a, Icon.Kind.SEARCH, Ui.TEXT2);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20), Gravity.START | Gravity.CENTER_VERTICAL);
        gl.leftMargin = Ui.dp(a, 14);
        field.addView(glass, gl);
        clear = new Icon(a, Icon.Kind.CLOSE, Ui.TEXT2);
        int cp = Ui.dp(a, 12);
        clear.setPadding(cp, cp, cp, cp);
        Ui.ripple(clear, null, 20);
        clear.setVisibility(View.GONE);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                box.setText("");
                box.requestFocus();
                showKeyboard();
            }
        });
        field.addView(clear, new FrameLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44), Gravity.END | Gravity.CENTER_VERTICAL));
        page.addView(field);

        // All | Live TV | Movies | Series
        LinearLayout chipRow = new LinearLayout(a);
        chipRow.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 6));
        for (int i = 0; i < 4; i++) {
            final int f = i;
            chips[i] = Ui.oneLine(Ui.medium(a, CHIP_NAMES[i], 13.5f, Ui.TEXT));
            int px = Ui.dp(a, 14), py = Ui.dp(a, 7);
            chips[i].setPadding(px, py, px, py);
            chips[i].setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { setFilter(f); }
            });
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cl.rightMargin = Ui.dp(a, 8);
            chipRow.addView(chips[i], cl);
        }
        page.addView(chipRow);
        paintChips();

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setPadding(0, 0, 0, Ui.dp(a, 24));
        status = Ui.text(a, "", 14, Ui.TEXT2);
        status.setGravity(Gravity.CENTER);
        status.setPadding(Ui.dp(a, 24), Ui.dp(a, 24), Ui.dp(a, 24), 0);
        col.addView(status, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 3; i++) {
            slots[i] = new LinearLayout(a);
            slots[i].setOrientation(LinearLayout.VERTICAL);
            slots[i].setClipChildren(false);
            col.addView(slots[i]);
        }
        scroll.addView(col);
        // Scrolling the results tucks the keyboard away, like most phone apps
        scroll.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) hideKeyboard();
                return false;
            }
        });
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        box.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) {}
            public void onTextChanged(CharSequence s, int x, int y, int z) {}
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                Async.MAIN.removeCallbacks(pending);
                Async.MAIN.postDelayed(pending, 300);
            }
        });
        box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int action, KeyEvent e) {
                if (e != null && e.getAction() != KeyEvent.ACTION_DOWN) return true;
                Async.MAIN.removeCallbacks(pending);
                search();
                hideKeyboard();
                return true;
            }
        });
        loadLibrary();
        return page;
    }

    @Override
    void onShown() {
        if (!shownOnce && box.getText().length() == 0) {
            shownOnce = true;
            box.requestFocus();
            box.postDelayed(new Runnable() { public void run() { showKeyboard(); } }, 250);
        }
    }

    @Override
    void onReselect() {
        box.requestFocus();
        box.selectAll();
        showKeyboard();
    }

    @Override
    void onDestroy() { Async.MAIN.removeCallbacks(pending); }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(box, InputMethodManager.SHOW_IMPLICIT);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
        box.clearFocus();
    }

    // ------------------------------------------------------------ searching (same as the TV app)

    private static final java.util.concurrent.ExecutorService LOADER = lowPriority("search-load");
    private static final java.util.concurrent.ExecutorService MATCHER = lowPriority("search-match");

    private static java.util.concurrent.ExecutorService lowPriority(final String name) {
        return java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                        r.run();
                    }
                }, name);
                t.setDaemon(true);
                return t;
            }
        });
    }

    private void loadLibrary() {
        boolean all = true;
        for (int i = 0; i < 3; i++) {
            ready[i] = Repo.cached(TYPES[i], null) != null;
            all &= ready[i];
        }
        status.setText(all ? "Type to search everything" : "Getting your library ready…");
        for (int i = 0; i < 3; i++) {
            if (ready[i]) continue;
            final int slot = i;
            Async.runOn(LOADER, new Async.Work<List<Item>>() {
                public List<Item> run() throws Exception { return Repo.streams(api, TYPES[slot], null); }
            }, new Async.Done<List<Item>>() {
                public void done(List<Item> r, Exception e) {
                    if (a.isFinishing()) return;
                    ready[slot] = true;
                    searchSlot(slot, query(), token);
                    updateStatus();
                }
            });
        }
    }

    private String query() { return box.getText().toString().trim().toLowerCase(Locale.UK); }

    /** 0 = All, 1 = Live TV, 2 = Movies, 3 = Series. */
    private int filter;
    private final TextView[] chips = new TextView[4];
    private static final String[] CHIP_NAMES = {"All", "Live TV", "Movies", "Series"};
    private static final int GRID_MAX = 120;

    private void paintChips() {
        for (int i = 0; i < 4; i++) {
            boolean on = i == filter;
            android.graphics.drawable.GradientDrawable g = Ui.rounded(a, on ? Ui.FOCUS_BG : 0, 0, 16);
            if (!on) g.setStroke(Ui.dp(a, 1), Ui.DARK ? 0x66EBEBF5 : 0x553C3C43);
            Ui.ripple(chips[i], g, 16);
            chips[i].setTextColor(on ? Ui.ON_FOCUS : Ui.TEXT);
            chips[i].setTypeface(on ? Ui.semibold() : Ui.medium());
        }
    }

    /** Show only one type; it then fills the screen as a grid. */
    private void setFilter(int f) {
        if (f == filter) return;
        filter = f;
        paintChips();
        search();
    }

    private boolean shows(int slot) { return filter == 0 || filter == slot + 1; }

    private void search() {
        token++;
        for (int i = 0; i < 3; i++) {
            found[i] = 0;
            if (query().length() < 2 || !shows(i)) slots[i].removeAllViews();
            else if (ready[i]) searchSlot(i, query(), token);
        }
        updateStatus();
    }

    private void updateStatus() {
        boolean all = ready[0] && ready[1] && ready[2];
        String q = query();
        if (q.length() < 2) {
            status.setText(all ? "Type to search everything" : "Getting your library ready…");
            status.setVisibility(View.VISIBLE);
            return;
        }
        int total = found[0] + found[1] + found[2];
        if (!all) status.setText(total == 0 ? "Searching…" : "");
        else status.setText(total == 0 ? "No results for \"" + q + "\"" : "");
        status.setVisibility(status.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void searchSlot(final int slot, final String q, final int t) {
        if (q.length() < 2 || !shows(slot)) return;
        final boolean asGrid = filter != 0;
        final int max = asGrid ? GRID_MAX : MAX;
        final List<Item> all = Repo.cached(TYPES[slot], null);
        if (all == null) return;
        Async.runOn(MATCHER, new Async.Work<List<Item>>() {
            public List<Item> run() {
                if (t != token) return null;
                return Match.rank(Filters.items(api, prefs, TYPES[slot], all), q, max, null); // best matches first, hidden categories left out
            }
        }, new Async.Done<List<Item>>() {
            public void done(final List<Item> hits, Exception e) {
                if (a.isFinishing() || t != token || hits == null) return;
                slots[slot].removeAllViews();
                found[slot] = hits.size();
                if (!hits.isEmpty()) {
                    Cards.Shape shape = slot == 0 ? Cards.Shape.CHANNEL : Cards.Shape.POSTER;
                    String heading = TITLES[slot] + "  ·  " + hits.size() + (hits.size() >= max ? "+" : "");
                    Cards.OnPick onPick = new Cards.OnPick() {
                        public void pick(Item it, View card) {
                            hideKeyboard();
                            Open.item(a, it, hits);
                        }
                    };
                    Cards.OnMenu onMenu = new Cards.OnMenu() {
                        public void menu(Item it, View card) { toggleFavMenu(it); }
                    };
                    if (asGrid) slots[slot].addView(Cards.grid(a, heading, hits, shape, prefs, onPick, onMenu));
                    else slots[slot].addView(Cards.shelf(a, heading, hits, shape, prefs, onPick, onMenu));
                }
                updateStatus();
            }
        });
    }
}
