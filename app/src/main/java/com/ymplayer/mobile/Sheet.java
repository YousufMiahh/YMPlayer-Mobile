package com.ymplayer.mobile;

import android.app.Activity;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A bottom sheet that slides up over the screen (categories, "hold" menus, confirmations).
 * Tap outside, swipe it down or press Back to close.
 */
public final class Sheet {
    public static class Option {
        final String label;
        final boolean checked;
        final Runnable action;
        final boolean destructive;
        /** Optional heart at the end of the row (favourite categories). */
        boolean hasHeart, hearted;
        Runnable heartToggle;

        public Option(String label, boolean checked, Runnable action) { this(label, checked, action, false); }

        boolean inert;
        Runnable longPress;
        boolean longPressRemoves;

        /** Something to do when the row is pressed and held. */
        public Option longPress(Runnable r) {
            longPress = r;
            return this;
        }

        /** Pressing and holding does this and takes the row away, leaving the sheet open (e.g. hide a category). */
        public Option longPressRemoves(Runnable r) {
            longPress = r;
            longPressRemoves = true;
            return this;
        }

        Runnable swipe;
        String swipeLabel;

        /** Swipe the row left to do this (e.g. hide a category): it slides away and leaves the sheet open. */
        public Option swipeLeft(String label, Runnable r) {
            swipeLabel = label;
            swipe = r;
            return this;
        }

        /** A row that's just information (tapping it does nothing). */
        public Option inert() {
            inert = true;
            return this;
        }

        public Option heart(boolean on, Runnable toggle) {
            hasHeart = true;
            hearted = on;
            heartToggle = toggle;
            return this;
        }

        public Option(String label, boolean checked, Runnable action, boolean destructive) {
            this.label = label;
            this.checked = checked;
            this.action = action;
            this.destructive = destructive;
        }
    }

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2.2f);

    private final Activity a;
    private final FrameLayout layer;
    private final View scrim;
    private final LinearLayout panel;
    private boolean closing;

    private static final java.util.WeakHashMap<Activity, Sheet> OPEN = new java.util.WeakHashMap<>();

    /** Closes the sheet on top, if any (call from onBackPressed). */
    public static boolean dismiss(Activity a) {
        Sheet s = OPEN.get(a);
        if (s == null) return false;
        s.close();
        return true;
    }

    public static Sheet show(Activity a, String title, String subtitle, List<Option> options, int scrollTo) {
        return show(a, title, subtitle, options, scrollTo, null);
    }

    /** @param searchHint non-null adds a search box that filters the rows as you type. */
    public static Sheet show(Activity a, String title, String subtitle, List<Option> options, int scrollTo, String searchHint) {
        Sheet s = new Sheet(a, title, subtitle, options, scrollTo, searchHint);
        OPEN.put(a, s);
        return s;
    }

    private final List<Option> visible = new ArrayList<>();
    private BaseAdapter adapter;
    private ListView listView;
    private int rowH, maxH;
    private boolean fixedHeight;

    private Sheet(final Activity a, String title, String subtitle, final List<Option> options, int scrollTo, String searchHint) {
        this.a = a;
        ViewGroup content = a.findViewById(android.R.id.content);
        layer = new FrameLayout(a);
        scrim = new View(a);
        scrim.setBackgroundColor(0x99000000);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { close(); } });
        layer.addView(scrim, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(Ui.DARK ? 0xFF1C1C22 : 0xFFFFFFFF);
        float r = Ui.dp(a, 18);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        panel.setClickable(true);
        panel.setPadding(0, Ui.dp(a, 8), 0, Ui.dp(a, 12));

        View grip = new View(a);
        grip.setBackground(Ui.rounded(a, Ui.TEXT3, 0, 3));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(Ui.dp(a, 36), Ui.dp(a, 4));
        glp.gravity = Gravity.CENTER_HORIZONTAL;
        glp.bottomMargin = Ui.dp(a, 10);
        panel.addView(grip, glp);

        if (title != null) {
            TextView t = Ui.oneLine(Ui.title(a, title, 18));
            t.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), subtitle == null ? Ui.dp(a, 8) : 0);
            panel.addView(t);
        }
        if (subtitle != null) {
            TextView st = Ui.lines(Ui.text(a, subtitle, 13, Ui.TEXT2), 3);
            st.setPadding(Ui.dp(a, 20), Ui.dp(a, 4), Ui.dp(a, 20), Ui.dp(a, 10));
            panel.addView(st);
        }

        visible.addAll(options);
        final ListView list = new ListView(a);
        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            public int getCount() { return visible.size(); }
            public Object getItem(int i) { return visible.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup parent) {
                final Option o = visible.get(i);
                final LinearLayout row = new LinearLayout(a);
                row.setBackgroundColor(Ui.DARK ? 0xFF1C1C22 : 0xFFFFFFFF); // covers what's behind until it's swiped
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, o.hasHeart ? 6 : 20), 0);
                row.setMinimumHeight(Ui.dp(a, 50));
                Ui.ripple(row, null, 0);
                row.setClickable(false);
                TextView l = Ui.oneLine(Ui.medium(a, o.label, 16, o.destructive ? Ui.RED : o.inert ? Ui.TEXT2 : Ui.TEXT));
                if (o.checked) l.setTypeface(Ui.bold());
                row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                if (o.checked) {
                    Icon ic = new Icon(a, Icon.Kind.CHECK, Ui.TEXT);
                    LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(Ui.dp(a, 20), Ui.dp(a, 20));
                    cl.leftMargin = Ui.dp(a, 8);
                    row.addView(ic, cl);
                }
                if (o.hasHeart) {
                    final Icon h = new Icon(a, o.hearted ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART, o.hearted ? Ui.RED : Ui.TEXT3);
                    int p = Ui.dp(a, 13);
                    h.setPadding(p, p, p, p);
                    Ui.ripple(h, null, 22);
                    h.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View x) {
                            o.hearted = !o.hearted;
                            if (o.heartToggle != null) o.heartToggle.run();
                            h.setKind(o.hearted ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART);
                            h.setColor(o.hearted ? Ui.RED : Ui.TEXT3);
                            x.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                            h.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction(new Runnable() {
                                public void run() { h.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); }
                            }).start();
                        }
                    });
                    row.addView(h, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
                }
                if (o.swipe == null) return row;
                // Behind the row: what a swipe to the left does, in red
                FrameLayout frame = new FrameLayout(a);
                TextView under = Ui.medium(a, o.swipeLabel, 15, 0xFFFFFFFF);
                under.setTypeface(Ui.semibold());
                under.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
                under.setPadding(0, 0, Ui.dp(a, 22), 0);
                under.setBackgroundColor(Ui.RED);
                frame.addView(under, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                frame.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                frame.setTag(row);
                return frame;
            }
        };
        list.setAdapter(adapter);
        // Swipe a row left (where the option allows it): the row follows the finger, the red action
        // shows underneath, and letting go far enough (or flicking) does it; otherwise it springs back
        list.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            int pos = -1;
            View cell;
            boolean swiping;
            android.view.VelocityTracker vt;
            final int slop = android.view.ViewConfiguration.get(a).getScaledTouchSlop();

            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        x0 = e.getX();
                        y0 = e.getY();
                        swiping = false;
                        pos = list.pointToPosition((int) x0, (int) y0);
                        cell = pos < 0 ? null : list.getChildAt(pos - list.getFirstVisiblePosition());
                        boolean can = cell != null && cell.getTag() instanceof View && pos < visible.size() && visible.get(pos).swipe != null;
                        if (!can) cell = null;
                        if (vt != null) vt.recycle();
                        vt = android.view.VelocityTracker.obtain();
                        vt.addMovement(e);
                        return false;
                    }
                    case MotionEvent.ACTION_MOVE: {
                        if (vt != null) vt.addMovement(e);
                        if (cell == null) return false;
                        float dx = e.getX() - x0, dy = e.getY() - y0;
                        if (!swiping) {
                            if (dx < -slop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                                swiping = true;
                                list.requestDisallowInterceptTouchEvent(true);
                                MotionEvent cancel = MotionEvent.obtain(e); // no tap or press-and-hold now
                                cancel.setAction(MotionEvent.ACTION_CANCEL);
                                list.onTouchEvent(cancel);
                                cancel.recycle();
                            } else {
                                return false;
                            }
                        }
                        ((View) cell.getTag()).setTranslationX(Math.min(0, dx));
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        if (!swiping || cell == null) { swiping = false; return false; }
                        swiping = false;
                        final View row = (View) cell.getTag();
                        float vx = 0;
                        if (vt != null) {
                            vt.addMovement(e);
                            vt.computeCurrentVelocity(1000);
                            vx = vt.getXVelocity();
                        }
                        float w = cell.getWidth(), at = row.getTranslationX();
                        final int p = pos;
                        if (at < -w * 0.4f || (vx < -1200 && at < -slop * 2)) {
                            row.animate().translationX(-w).setDuration(160).withEndAction(new Runnable() {
                                public void run() {
                                    if (p >= visible.size()) return;
                                    Option o = visible.get(p);
                                    o.swipe.run();
                                    visible.remove(o);
                                    try { options.remove(o); } catch (UnsupportedOperationException ignored) { }
                                    adapter.notifyDataSetChanged();
                                    row.setTranslationX(0);
                                }
                            }).start();
                        } else {
                            row.animate().translationX(0).setDuration(220).setInterpolator(EASE).start();
                        }
                        cell = null;
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
        list.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                final Option o = visible.get(i);
                final Runnable lp = o.longPress;
                if (lp == null) return false;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                if (o.longPressRemoves) {
                    lp.run();
                    visible.remove(o);
                    try {
                        options.remove(o);
                    } catch (UnsupportedOperationException ignored) {
                    }
                    adapter.notifyDataSetChanged();
                    return true;
                }
                close();
                Async.MAIN.postDelayed(lp, 230); // after this sheet has slid away
                return true;
            }
        });
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int i, long id) {
                if (visible.get(i).inert) return;
                final Runnable act = visible.get(i).action;
                close();
                if (act != null) act.run();
            }
        });
        listView = list;
        rowH = Ui.dp(a, 50);
        int screenH = a.getResources().getDisplayMetrics().heightPixels;
        maxH = (int) (screenH * (searchHint != null ? 0.55f : 0.62f));
        fixedHeight = searchHint != null;
        int h = searchHint != null ? maxH : Math.min(maxH, rowH * Math.max(1, options.size()));

        if (searchHint != null) {
            // Search box: narrows the rows as you type, ignoring case, spaces, symbols and accents
            android.widget.FrameLayout field = new android.widget.FrameLayout(a);
            field.setPadding(Ui.dp(a, 16), Ui.dp(a, 2), Ui.dp(a, 16), Ui.dp(a, 8));
            final android.widget.EditText box = Ui.input(a, searchHint, android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            box.setPadding(Ui.dp(a, 40), Ui.dp(a, 10), Ui.dp(a, 14), Ui.dp(a, 10));
            box.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.sp(a, 15));
            box.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            field.addView(box, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Icon glass = new Icon(a, Icon.Kind.SEARCH, Ui.TEXT2);
            android.widget.FrameLayout.LayoutParams gl = new android.widget.FrameLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18), Gravity.START | Gravity.CENTER_VERTICAL);
            gl.leftMargin = Ui.dp(a, 13);
            field.addView(glass, gl);
            panel.addView(field);
            final TextView none = Ui.text(a, "No matching categories", 14, Ui.TEXT2);
            none.setGravity(Gravity.CENTER);
            none.setPadding(0, Ui.dp(a, 24), 0, 0);
            none.setVisibility(View.GONE);
            panel.addView(none, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            box.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence x, int b, int c, int d) {}
                public void onTextChanged(CharSequence x, int b, int c, int d) {}
                public void afterTextChanged(android.text.Editable e) {
                    String q = Item.squash(e.toString());
                    visible.clear();
                    for (Option o : options) if (q.isEmpty() || Item.squash(o.label).contains(q)) visible.add(o);
                    adapter.notifyDataSetChanged();
                    list.setSelection(0);
                    none.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
            box.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                public boolean onEditorAction(TextView v, int act, android.view.KeyEvent e) {
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                            a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
                    return true;
                }
            });
            // Scrolling the list tucks the keyboard away
            list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                public void onScrollStateChanged(android.widget.AbsListView v, int st) {
                    if (st == SCROLL_STATE_TOUCH_SCROLL) {
                        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                                a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(box.getWindowToken(), 0);
                    }
                }
                public void onScroll(android.widget.AbsListView v, int f, int n, int t) {}
            });
        }
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        if (scrollTo > 2) list.setSelection(scrollTo - 2);

        // Drag the sheet: it follows your finger 1:1 (from where you took hold, even mid-animation),
        // resists if you pull it up past the top, and the dimming follows it. Let go and it goes
        // where your flick was heading, at your finger's speed: closed, or back up with a spring.
        panel.setOnTouchListener(new View.OnTouchListener() {
            float grab;
            android.view.VelocityTracker vt;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (closing) return false;
                        panel.animate().cancel();
                        drag.stop();
                        grab = e.getRawY() - panel.getTranslationY();
                        vt = android.view.VelocityTracker.obtain();
                        vt.addMovement(e);
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        if (vt != null) vt.addMovement(e);
                        float y = e.getRawY() - grab;
                        if (y < 0) y = -rubberBand(-y, panel.getHeight()); // soft edge, not a wall
                        setDrag(y);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        float vel = 0;
                        if (vt != null) {
                            vt.addMovement(e);
                            vt.computeCurrentVelocity(1000);
                            vel = vt.getYVelocity();
                            vt.recycle();
                            vt = null;
                        }
                        float at = panel.getTranslationY();
                        float projected = at + Spring.project(vel);
                        if (projected > panel.getHeight() * 0.5f) dismiss(vel);
                        else drag.to(0, vel, vel < -50 ? 0.8f : 1f, 0.3f, null); // back up (a touch of bounce if flicked)
                        return true;
                    }
                }
                return false;
            }
        });

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        int maxW = Ui.dp(a, 560);
        if (a.getResources().getDisplayMetrics().widthPixels > maxW) {
            plp.width = maxW;
            plp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        layer.addView(panel, plp);
        layer.setElevation(Ui.dp(a, 30));
        content.addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel.setVisibility(View.INVISIBLE);
        panel.post(new Runnable() {
            public void run() {
                panel.setTranslationY(panel.getHeight());
                panel.setVisibility(View.VISIBLE);
                panel.animate().translationY(0).setDuration(300).setInterpolator(EASE).start();
                scrim.animate().alpha(1f).setDuration(250).start();
            }
        });
    }

    private final Spring drag = new Spring(new Spring.Value() {
        public float get() { return panel.getTranslationY(); }
        public void set(float v) { setDrag(v); }
    });

    /** The sheet at `y` below its open position, with the dimming fading as it goes. */
    private void setDrag(float y) {
        panel.setTranslationY(y);
        float h = Math.max(1, panel.getHeight());
        scrim.setAlpha(Math.max(0f, Math.min(1f, 1f - y / h)));
    }

    /** Past the edge it follows less and less: real things slow before they stop. */
    private static float rubberBand(float over, float dim) {
        float k = 0.55f;
        return (over * dim * k) / (dim + k * Math.abs(over));
    }

    /** Thrown down: it carries on at the finger's speed until it's gone. */
    private void dismiss(float velocity) {
        if (closing) return;
        closing = true;
        if (OPEN.get(a) == this) OPEN.remove(a);
        drag.to(panel.getHeight(), Math.max(velocity, 0), 1f, 0.3f, new Runnable() {
            public void run() {
                ViewGroup p = (ViewGroup) layer.getParent();
                if (p != null) p.removeView(layer);
            }
        });
    }

    /** Replace the rows while the sheet is open (e.g. Cast devices appearing). */
    public void setOptions(List<Option> options) {
        visible.clear();
        visible.addAll(options);
        adapter.notifyDataSetChanged();
        if (!fixedHeight) {
            ViewGroup.LayoutParams lp = listView.getLayoutParams();
            lp.height = Math.min(maxH, rowH * Math.max(1, options.size()));
            listView.setLayoutParams(lp);
        }
    }

    public boolean isOpen() { return !closing; }

    public void close() {
        if (closing) return;
        closing = true;
        drag.stop();
        if (OPEN.get(a) == this) OPEN.remove(a);
        scrim.animate().alpha(0f).setDuration(200).start();
        panel.animate().translationY(panel.getHeight()).setDuration(220).setInterpolator(EASE).withEndAction(new Runnable() {
            public void run() {
                ViewGroup p = (ViewGroup) layer.getParent();
                if (p != null) p.removeView(layer);
            }
        }).start();
    }

    static int navBar(Activity a) {
        if (a.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) return 0;
        int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? a.getResources().getDimensionPixelSize(id) : 0;
    }

    /** Small helper for "Are you sure?" sheets. */
    public static void confirm(Activity a, String title, String subtitle, String yes, Runnable action) {
        List<Option> o = new ArrayList<>();
        o.add(new Option(yes, false, action, true));
        o.add(new Option("Cancel", false, null));
        show(a, title, subtitle, o, 0);
    }
}
