package com.ymplayer.mobile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Design tokens and helpers — a dark, quiet, tvOS-inspired look:
 * near-black backgrounds, translucent surfaces, white focus states, gentle lift on focus.
 */
public final class Ui {
    private Ui() {}

    // Colours — set by applyTheme(); dark is the default.
    public static boolean DARK = true;
    public static int BG, BG_TOP, SURFACE, SURFACE_HI, SURFACE_LO, SHEET, TEXT, TEXT2, TEXT3, FOCUS_BG, ON_FOCUS, ON_FOCUS2;
    public static final int ACCENT = 0xFF0A84FF;        // system blue
    public static final int RED = 0xFFFF453A;
    // Always-light text for overlays drawn over video.
    public static final int W1 = 0xFFFFFFFF, W2 = 0x99EBEBF5, W3 = 0x66EBEBF5;

    // Legacy aliases
    public static int MUTED, PANEL, CARD;

    static { applyTheme(true); }

    public static void applyTheme(boolean dark) {
        DARK = dark;
        if (dark) {
            BG = 0xFF0A0B12; BG_TOP = 0xFF1C2040;
            SURFACE = 0x1AFFFFFF; SURFACE_HI = 0x2EFFFFFF; SURFACE_LO = 0x12FFFFFF;
            SHEET = 0xF21C1C22;
            TEXT = 0xFFFFFFFF; TEXT2 = 0x99EBEBF5; TEXT3 = 0x4DEBEBF5;
            FOCUS_BG = 0xFFFFFFFF; ON_FOCUS = 0xFF0B0B0F; ON_FOCUS2 = 0xB30B0B0F;
        } else {
            BG = 0xFFF2F2F7; BG_TOP = 0xFFFFFFFF;
            SURFACE = 0x12000000; SURFACE_HI = 0x21000000; SURFACE_LO = 0x0A000000;
            SHEET = 0xF5F7F7FA;
            TEXT = 0xFF000000; TEXT2 = 0x993C3C43; TEXT3 = 0x5C3C3C43;
            FOCUS_BG = 0xFF1D1D1F; ON_FOCUS = 0xFFFFFFFF; ON_FOCUS2 = 0xB3FFFFFF;
        }
        MUTED = TEXT2; PANEL = SURFACE; CARD = SURFACE;
    }

    private static Typeface regular, medium, semibold, bold;

    public static void init(Context c) {
        applyTheme(new Prefs(c).darkTheme());
        Repo.init(c);
        if (regular != null) return;
        try {
            regular = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Regular.ttf");
            medium = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Medium.ttf");
            semibold = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-SemiBold.ttf");
            bold = Typeface.createFromAsset(c.getAssets(), "fonts/Inter-Bold.ttf");
        } catch (Exception e) {
            regular = Typeface.DEFAULT;
            medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            semibold = medium;
            bold = Typeface.DEFAULT_BOLD;
        }
    }

    public static Typeface regular() { return regular == null ? Typeface.DEFAULT : regular; }
    public static Typeface medium() { return medium == null ? Typeface.DEFAULT : medium; }
    public static Typeface semibold() { return semibold == null ? Typeface.DEFAULT_BOLD : semibold; }
    public static Typeface bold() { return bold == null ? Typeface.DEFAULT_BOLD : bold; }

    /** Layout unit: one Android dp, so sizes in this app read like normal phone design sizes. */
    public static float unit(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    public static int dp(Context c, float v) {
        int px = Math.round(v * unit(c));
        return px == 0 && v > 0 ? 1 : px;
    }

    /** Text size in pixels: follows the phone's font-size setting, but not so far that layouts break. */
    public static float sp(Context c, float v) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        float scale = Math.min(m.scaledDensity / m.density, 1.25f);
        return v * m.density * scale;
    }

    /** Touch feedback: a soft ripple clipped to the view's rounded shape. */
    public static void ripple(View v, Drawable content, float radiusDp) {
        GradientDrawable mask = rounded(v.getContext(), Color.WHITE, 0, radiusDp);
        int rc = DARK ? 0x33FFFFFF : 0x22000000;
        v.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rc), content, mask));
        v.setClickable(true);
    }

    /** Ripple drawn over artwork (doesn't replace its background). */
    public static void rippleOver(View v, float radiusDp) {
        GradientDrawable mask = rounded(v.getContext(), Color.WHITE, 0, radiusDp);
        v.setForeground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(DARK ? 0x40FFFFFF : 0x26000000), null, mask));
        v.setClickable(true);
    }

    /** Makes room for the status bar at the top of a screen that draws behind it. */
    public static int statusBar(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? c.getResources().getDimensionPixelSize(id) : dp(c, 24);
    }

    /** Edge-to-edge window: draws behind transparent status and navigation bars, with icons that suit the theme. */
    public static void edgeToEdge(android.app.Activity a) {
        android.view.Window w = a.getWindow();
        w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(DARK ? 0xFF000000 : 0xFFF2F2F7);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!DARK) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        w.getDecorView().setSystemUiVisibility(flags);
    }

    public static GradientDrawable rounded(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) g.setStroke(dp(c, 2), stroke);
        return g;
    }

    /** Focus ring drawn over artwork. */
    public static Drawable outline(Context c, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.TRANSPARENT);
        g.setCornerRadius(dp(c, radiusDp));
        g.setStroke(dp(c, 4), DARK ? Color.WHITE : FOCUS_BG);
        return g;
    }

    public static Drawable screenBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG, BG});
    }

    /** Dark gradient for overlays on top of video (always dark). */
    public static Drawable scrimBottom() {
        return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF0000000, 0x99000000, 0x00000000});
    }

    /** Page-coloured gradients for backdrops behind text on detail pages. */
    public static Drawable pageScrimBottom() {
        int c = BG & 0x00FFFFFF;
        return new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xF5000000 | c, 0x99000000 | c, c});
    }

    public static Drawable scrimLeft() {
        int c = BG & 0x00FFFFFF;
        return new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFA000000 | c, 0xCC000000 | c, c});
    }

    /** White when focused (tvOS style), translucent otherwise. */
    public static Drawable pill(Context c, int normalFill, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        Drawable on = rounded(c, FOCUS_BG, 0, radiusDp);
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{android.R.attr.state_pressed}, on);
        s.addState(new int[]{}, normalFill == 0 ? new ColorDrawable(Color.TRANSPARENT) : rounded(c, normalFill, 0, radiusDp));
        return s;
    }

    /** Text colour that flips to dark when the view is focused. */
    public static android.content.res.ColorStateList focusText(int normal) {
        return new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{ON_FOCUS, ON_FOCUS, normal});
    }

    public static TextView text(Context c, String s, float sp, int color) {
        init(c);
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(c, sp));
        t.setTextColor(color);
        t.setTypeface(regular());
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView medium(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setTypeface(medium());
        return t;
    }

    public static TextView title(Context c, String s, float sp) {
        TextView t = text(c, s, sp, TEXT);
        t.setTypeface(bold());
        t.setLetterSpacing(-0.02f);
        return t;
    }

    public static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    public static TextView lines(TextView t, int n) {
        t.setMaxLines(n);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    /** tvOS button: translucent pill, white with dark text when focused, lifts slightly. */
    public static Button button(Context c, String s) {
        init(c);
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(focusText(TEXT));
        b.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(c, 15));
        b.setTypeface(semibold());
        b.setBackground(pill(c, SURFACE_HI, 10));
        b.setStateListAnimator(null);
        b.setFocusable(true);
        b.setGravity(Gravity.CENTER);
        int p = dp(c, 12);
        b.setPadding(p * 2, p, p * 2, p);
        b.setMinHeight(dp(c, 46));
        b.setMinimumHeight(dp(c, 46));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        return b;
    }

    /** The main action (Netflix's white Play button): solid white, dark text, full width. */
    public static Button primary(Context c, String s) {
        Button b = button(c, s);
        b.setTextColor(ON_FOCUS);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33000000), rounded(c, FOCUS_BG, 0, 10), null));
        return b;
    }

    public static EditText input(Context c, String hint, int inputType) {
        init(c);
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setTextColor(focusText(TEXT));
        e.setHintTextColor(TEXT3);
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, sp(c, 16));
        e.setTypeface(medium());
        e.setTextColor(TEXT);
        e.setBackground(rounded(c, SURFACE, 0, 12));
        int p = dp(c, 14);
        e.setPadding(p + dp(c, 4), p, p, p);
        return e;
    }

    // ------------------------------------------------------------ focus motion

    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(1.6f);

    /** Smoothly scale/raise a view when it gains focus, like tvOS cards. */
    public static void lift(final View v, final float scale) {
        final View.OnFocusChangeListener prev = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View view, boolean has) {
                animateFocus(view, has, scale);
                if (prev != null) prev.onFocusChange(view, has);
            }
        });
    }

    public static void animateFocus(View v, boolean on, float scale) {
        float s = on ? scale : 1f;
        v.animate().scaleX(s).scaleY(s).translationZ(on ? dp(v.getContext(), 14) : 0)
                .setDuration(180).setInterpolator(EASE).start();
    }
}
