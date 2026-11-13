package com.ymplayer.mobile;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The main screen: five tabs along the bottom (Home, Live TV, Movies, Series, Search), like Netflix.
 * Switching tabs glides the highlight across and slides the new page in from that side.
 */
public class MainActivity extends Activity {
    static final int HOME = 0, LIVE = 1, MOVIES = 2, SERIES = 3, DOWNLOADS = 4, SEARCH = 5;
    private static final String[] LABELS = {"Home", "Live TV", "Movies", "Series", "Downloads", "Search"};
    private static final Icon.Kind[] ICONS = {Icon.Kind.HOME, Icon.Kind.LIVE, Icon.Kind.MOVIES, Icon.Kind.SERIES, Icon.Kind.DOWNLOAD, Icon.Kind.SEARCH};
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    Prefs prefs;
    XtreamApi api;
    private FrameLayout pages;
    private final Page[] page = new Page[6];
    private int current = -1;
    private LinearLayout nav;
    private final Icon[] navIcons = new Icon[6];
    private final TextView[] navLabels = new TextView[6];
    private View navPill;
    private ValueAnimator pillAnim;
    private boolean started;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Ui.edgeToEdge(this);
        prefs = new Prefs(this);
        api = new XtreamApi(prefs);
        ImageLoader.setUserAgent(prefs.userAgent());
        if (!prefs.hasAccount()) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }
        Ambient.install(this, Ambient.HOME);

        FrameLayout root = new FrameLayout(this);
        pages = new FrameLayout(this);
        int navH = Ui.dp(this, 62);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        plp.bottomMargin = navH;
        root.addView(pages, plp);
        navBar = buildNav();
        root.addView(navBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, navH, Gravity.BOTTOM));
        setContentView(root);

        page[HOME] = new HomePage(this);
        page[LIVE] = new BrowsePage(this, Item.LIVE);
        page[MOVIES] = new BrowsePage(this, Item.MOVIE);
        page[SERIES] = new BrowsePage(this, Item.SERIES);
        page[DOWNLOADS] = new DownloadsPage(this);
        page[SEARCH] = new SearchPage(this);
        int start = b != null ? b.getInt("tab", HOME) : HOME;
        select(start, false);
        Async.MAIN.postDelayed(new Runnable() { // once Home has filled in
            public void run() {
                if (isFinishing()) return;
                Coach.offer(MainActivity.this, false, new Runnable() { public void run() { homeTour(); } });
            }
        }, 1500);
        Async.MAIN.postDelayed(new Runnable() { // new version on GitHub? (not over the tutorial)
            public void run() {
                if (!isFinishing() && !Coach.showing(MainActivity.this) && !WhatsNew.maybeShow(MainActivity.this)) Updater.check(MainActivity.this, false);
            }
        }, 3000);
    }

    private View navBar;

    /** Quick-start tips (Settings → Show Tutorial runs them again). */
    private void homeTour() {
        if (current != HOME) return;
        HomePage h = (HomePage) page[HOME];
        Coach.show(this, "m_home", false,
                new Coach.Step(navBar, "Getting around",
                        "Home, Live TV, Movies, Series, Downloads and Search are along the bottom. Tap a tab again to jump back to the top."),
                new Coach.Step(h.topIcons(), "Cast & Settings",
                        "Cast sends what you're watching to a Chromecast or Google TV. Settings lets you choose what Home shows, "
                                + "hide countries and categories you don't watch, and switch accounts."),
                new Coach.Step(h.firstRow(), "Your Home screen",
                        "Continue Watching, recent channels, favourites and the UK Top 10. Press and hold any card for more options, "
                                + "like removing it from Continue Watching."),
                new Coach.Step(null, "Watching",
                        "Videos turn sideways to fill the screen. Tap for controls, double-tap the left or right side to skip, "
                                + "and press Home while watching to keep it playing in a small window."));
    }

    private View buildNav() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundColor(Ui.DARK ? 0xF20B0B10 : 0xF5F7F7FA);
        View line = new View(this);
        line.setBackgroundColor(Ui.DARK ? 0x1FFFFFFF : 0x1F000000);
        bar.addView(line, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(this, 0.5f)), Gravity.TOP));

        navPill = new View(this);
        navPill.setBackground(Ui.rounded(this, Ui.SURFACE_HI, 0, 14));
        navPill.setAlpha(0f);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30), Gravity.TOP | Gravity.START);
        pl.topMargin = Ui.dp(this, 7);
        bar.addView(navPill, pl);

        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        bar.addView(nav, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        for (int i = 0; i < LABELS.length; i++) {
            final int idx = i;
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            t.setPadding(0, Ui.dp(this, 10), 0, 0);
            navIcons[i] = new Icon(this, ICONS[i], Ui.TEXT2);
            t.addView(navIcons[i], new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            // Full-width and centred (a single-line TextView left at its natural width drifted off centre)
            navLabels[i] = Ui.medium(this, LABELS[i], 10.5f, Ui.TEXT2);
            navLabels[i].setMaxLines(1);
            navLabels[i].setGravity(Gravity.CENTER_HORIZONTAL);
            navLabels[i].setPadding(0, Ui.dp(this, 7), 0, 0);
            t.addView(navLabels[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { select(idx, true); }
            });
            nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        return bar;
    }

    void select(int idx, boolean animate) {
        if (idx == current) {
            page[idx].onReselect();
            return;
        }
        final int from = current;
        current = idx;
        Page p = page[idx];
        View in = p.view();
        if (in.getParent() == null) pages.addView(in, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        in.bringToFront();
        in.setVisibility(View.VISIBLE);
        if (from >= 0 && animate) {
            final View out = page[from].view();
            int dir = idx > from ? 1 : -1;
            in.setAlpha(0f);
            in.setTranslationX(dir * Ui.dp(this, 36));
            in.animate().alpha(1f).translationX(0).setDuration(280).setInterpolator(EASE).start();
            out.animate().alpha(0f).translationX(-dir * Ui.dp(this, 24)).setDuration(200).withEndAction(new Runnable() {
                public void run() {
                    if (current != from) {
                        out.setVisibility(View.GONE);
                        out.setTranslationX(0);
                        out.setAlpha(1f);
                    }
                }
            }).start();
        } else if (from >= 0) {
            page[from].view().setVisibility(View.GONE);
        }
        for (int i = 0; i < LABELS.length; i++) {
            int col = i == idx ? Ui.TEXT : Ui.TEXT2; // unselected: dimmer, but still crisp and readable
            navIcons[i].setColor(col);
            navLabels[i].setTextColor(col);
            navLabels[i].setTypeface(i == idx ? Ui.semibold() : Ui.medium());
        }
        movePill(idx, animate && from >= 0);
        Ambient.tintColor(this, p.ambient());
        p.onShown();
    }

    /** The highlight behind the selected tab glides across, like the top bar on the TV app. */
    private void movePill(final int idx, final boolean animate) {
        nav.post(new Runnable() {
            public void run() {
                View tab = nav.getChildAt(idx);
                if (tab == null || tab.getWidth() == 0) return;
                final float target = tab.getLeft() + (tab.getWidth() - navPill.getWidth()) / 2f;
                if (pillAnim != null) pillAnim.cancel();
                navPill.animate().alpha(1f).setDuration(150).start();
                if (!animate || navPill.getAlpha() == 0f) {
                    navPill.setTranslationX(target);
                    return;
                }
                final float from = navPill.getTranslationX();
                pillAnim = ValueAnimator.ofFloat(0f, 1f);
                pillAnim.setDuration(300);
                pillAnim.setInterpolator(EASE);
                pillAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator v) {
                        navPill.setTranslationX(from + (target - from) * (Float) v.getAnimatedValue());
                    }
                });
                pillAnim.start();
            }
        });
    }

    /** A favourite was added or removed somewhere: rows that show favourites refresh. */
    void favouritesChanged() {
        for (Page p : page) if (p != null && p.built()) p.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (pages == null) return;
        for (Page p : page) p.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pages == null) return;
        if (started) for (Page p : page) if (p.built()) p.onResume();
        started = true;
    }

    @Override
    protected void onStop() {
        if (pages != null) for (Page p : page) p.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (pages != null) for (Page p : page) p.onDestroy();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", Math.max(0, current));
    }

    @Override
    public void onBackPressed() {
        if (Coach.back(this)) return;
        if (Sheet.dismiss(this)) return;
        if (current >= 0 && page[current].onBack()) return;
        if (current != HOME) {
            select(HOME, true);
            return;
        }
        super.onBackPressed();
    }
}
