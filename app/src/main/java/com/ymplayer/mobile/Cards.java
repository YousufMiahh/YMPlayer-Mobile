package com.ymplayer.mobile;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Artwork cards and horizontal shelves: tap to open, press and hold for more. */
public final class Cards {
    private Cards() {}

    public enum Shape { POSTER, LANDSCAPE, CHANNEL, RANKED }

    public interface OnPick { void pick(Item item, View card); }

    public interface OnMenu { void menu(Item item, View card); }

    static int width(Context c, Shape s) {
        switch (s) {
            case POSTER: return Ui.dp(c, 112);
            case RANKED: return Ui.dp(c, 150);
            case CHANNEL: return Ui.dp(c, 136);
            default: return Ui.dp(c, 220);
        }
    }

    /** One card: rounded artwork + title (+ optional subtitle and progress). */
    public static LinearLayout card(Context c, Shape shape, String image, String title, String subtitle, float progress, int rank) {
        return card(c, shape, image, title, subtitle, progress, rank, 0, false);
    }

    /**
     * @param fitW   card width to use instead of the usual one (height follows), 0 for normal
     * @param named  show the title under posters too (they normally rely on the artwork)
     */
    public static LinearLayout card(Context c, Shape shape, String image, String title, String subtitle, float progress, int rank,
                                    int fitW, boolean named) {
        int w, h;
        switch (shape) {
            case POSTER: w = Ui.dp(c, 112); h = Ui.dp(c, 168); break;
            case RANKED: w = Ui.dp(c, 104); h = Ui.dp(c, 156); break;
            case CHANNEL: w = Ui.dp(c, 136); h = Ui.dp(c, 78); break;
            default: w = Ui.dp(c, 220); h = Ui.dp(c, 124); break;
        }
        if (fitW > 0 && shape != Shape.RANKED) {
            h = Math.round(h * (fitW / (float) w));
            w = fitW;
        }
        final LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClipChildren(false);

        FrameLayout art = new FrameLayout(c);
        art.setBackground(Ui.rounded(c, Ui.SURFACE, 0, 8));
        art.setClipToOutline(true);
        ImageView img = new ImageView(c);
        if (shape == Shape.CHANNEL) {
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int p = Ui.dp(c, 14);
            img.setPadding(p, p, p, p);
        } else {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        // Name / initials in place of the picture: shown while it loads, and kept if it can't be loaded
        TextView ph = Ui.lines(Ui.title(c, shape == Shape.CHANNEL || shape == Shape.LANDSCAPE ? initials(title) : title, shape == Shape.CHANNEL ? 20 : 15), 4);
        ph.setTextColor(Ui.TEXT3);
        ph.setGravity(Gravity.CENTER);
        int pp = Ui.dp(c, 8);
        ph.setPadding(pp, pp, pp, pp);
        art.addView(ph, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        art.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (progress > 0) {
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 3), Gravity.BOTTOM);
            int m = Ui.dp(c, 8);
            bp.setMargins(m, 0, m, m);
            art.addView(progressBar(c, progress), bp);
        }
        Ui.rippleOver(art, 8);
        art.setClickable(false);
        ImageLoader.load(img, image, shape == Shape.CHANNEL || shape == Shape.LANDSCAPE ? 440 : 240,
                shape == Shape.CHANNEL || shape == Shape.LANDSCAPE ? 248 : 360, ph);

        if (shape == Shape.RANKED) {
            FrameLayout ranked = new FrameLayout(c);
            ranked.setClipChildren(false);
            RankNumber n = new RankNumber(c, rank);
            ranked.addView(n, new FrameLayout.LayoutParams(Ui.dp(c, 70), h, Gravity.START | Gravity.BOTTOM));
            FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(w, h, Gravity.END);
            ranked.addView(art, alp);
            card.addView(ranked, new LinearLayout.LayoutParams(width(c, shape), h));
        } else {
            card.addView(art, new LinearLayout.LayoutParams(w, h));
        }
        card.setTag(art);

        if (named || (shape != Shape.POSTER && shape != Shape.RANKED)) {
            TextView t = Ui.oneLine(Ui.medium(c, title, 13, Ui.TEXT));
            t.setPadding(Ui.dp(c, 1), Ui.dp(c, 7), 0, 0);
            card.addView(t, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (subtitle != null && !subtitle.isEmpty()) {
                TextView s = Ui.oneLine(Ui.text(c, subtitle, 12, Ui.TEXT2));
                s.setPadding(Ui.dp(c, 1), Ui.dp(c, 3), 0, 0);
                card.addView(s, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        // Presses show on the artwork; the whole card is the touch target.
        card.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                View a = (View) v.getTag();
                if (a.getForeground() != null) {
                    a.getForeground().setHotspot(e.getX(), e.getY());
                    int act = e.getActionMasked();
                    if (act == android.view.MotionEvent.ACTION_DOWN) a.setPressed(true);
                    else if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) a.setPressed(false);
                }
                return false;
            }
        });
        return card;
    }

    /** Small "✓" in the corner of something you've already watched. */
    static void markWatched(LinearLayout card) {
        if (!(card.getTag() instanceof FrameLayout)) return;
        Context c = card.getContext();
        TextView b = Ui.medium(c, "✓", 12, 0xFF000000);
        b.setTypeface(Ui.semibold());
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.rounded(c, 0xF2FFFFFF, 0, 11));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(c, 22), Ui.dp(c, 22), Gravity.TOP | Gravity.END);
        lp.topMargin = lp.rightMargin = Ui.dp(c, 6);
        ((FrameLayout) card.getTag()).addView(b, lp);
    }

    public static View progressBar(Context c, float progress) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(Ui.rounded(c, 0x59FFFFFF, 0, 2));
        bar.setClipToOutline(true);
        View fill = new View(c);
        fill.setBackground(Ui.rounded(c, Color.WHITE, 0, 2));
        float p = Math.max(0.02f, Math.min(1f, progress));
        bar.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, p));
        View rest = new View(c);
        bar.addView(rest, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f - p + 0.0001f));
        return bar;
    }

    /** A titled horizontal shelf of cards. */
    public static LinearLayout shelf(final Context c, CharSequence title, final List<Item> items, final Shape shape,
                                     final Prefs prefs, final OnPick pick, final OnMenu menu) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        TextView h = Ui.text(c, "", 17, Ui.TEXT);
        h.setText(title);
        h.setTypeface(Ui.bold());
        h.setPadding(Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 8));
        box.addView(h);

        HorizontalScrollView sv = new HorizontalScrollView(c);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(Ui.dp(c, 16), 0, Ui.dp(c, 8), 0);
        int rank = 0;
        for (final Item it : items) {
            rank++;
            String sub = null;
            String img = it.icon;
            float progress = 0;
            if (shape == Shape.LANDSCAPE) {
                long pos = prefs.position(it), dur = prefs.duration(it);
                if (dur > 0) progress = pos / (float) dur;
                if (Item.EPISODE.equals(it.type)) {
                    String left = dur > 0 ? SeriesActivity.fmt(Math.max(0, dur - pos)) + " left" : null;
                    sub = XtreamApi.joinNonEmpty(it.parentName, left);
                    if (img == null) img = it.parentIcon;
                } else {
                    sub = dur > 0 ? SeriesActivity.fmt(Math.max(0, dur - pos)) + " left" : null;
                }
            }
            String name = it.name;
            if (Item.EPISODE.equals(it.type) && it.num != null) name = "E" + it.num + " · " + it.name;
            LinearLayout card = card(c, shape, img, name, sub, progress, it.rank > 0 ? it.rank : rank);
            if (prefs != null && prefs.isWatched(it)) markWatched(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(c, shape == Shape.RANKED ? 4 : 8);
            row.addView(card, lp);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pick.pick(it, v); }
            });
            if (menu != null) {
                card.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        menu.menu(it, v);
                        return true;
                    }
                });
            }
        }
        sv.addView(row);
        box.addView(sv);
        return box;
    }

    /** A titled grid of cards wrapping across the screen (search narrowed to one type). */
    public static LinearLayout grid(final Context c, CharSequence title, final List<Item> items, final Shape shape,
                                    final Prefs prefs, final OnPick pick, final OnMenu menu) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        TextView h = Ui.text(c, "", 17, Ui.TEXT);
        h.setText(title);
        h.setTypeface(Ui.bold());
        h.setPadding(Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 4));
        box.addView(h);
        int gap = Ui.dp(c, 8), side = Ui.dp(c, 16);
        int usable = c.getResources().getDisplayMetrics().widthPixels - 2 * side;
        int cols = Math.max(2, Math.round((usable + gap) / (float) (width(c, shape) + gap)));
        int cardW = (usable - (cols - 1) * gap) / cols; // stretch the cards so the grid fills the width evenly
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(side, Ui.dp(c, 10), side, 0);
                box.addView(row);
            }
            final Item it = items.get(i);
            String name = Item.EPISODE.equals(it.type) && it.num != null ? "E" + it.num + " · " + it.name : it.name;
            LinearLayout card = card(c, shape, it.icon, name, null, 0, 0, cardW, true);
            if (prefs != null && prefs.isWatched(it)) markWatched(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % cols != cols - 1) lp.rightMargin = gap;
            row.addView(card, lp);
            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { pick.pick(it, v); }
            });
            if (menu != null) {
                card.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        menu.menu(it, v);
                        return true;
                    }
                });
            }
        }
        return box;
    }

    /** "Title    hint" with the hint small and grey. */
    public static CharSequence titleWithHint(String title, String hint) {
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(title).append("    ");
        int start = b.length();
        b.append(hint);
        b.setSpan(new android.text.style.RelativeSizeSpan(0.72f), start, b.length(), 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(Ui.TEXT3), start, b.length(), 0);
        b.setSpan(new android.text.style.TypefaceSpan("sans-serif"), start, b.length(), 0);
        return b;
    }

    static String initials(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String w : s.replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            b.append(Character.toUpperCase(w.charAt(0)));
            if (b.length() == 2) break;
        }
        return b.toString();
    }

    /** Netflix-style big outlined rank number beside a Top 10 poster. */
    static final class RankNumber extends View {
        private final String n;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG);

        RankNumber(Context c, int rank) {
            super(c);
            n = String.valueOf(rank);
            fill.setTypeface(Ui.bold());
            fill.setColor(Ui.BG);
            fill.setTextAlign(Paint.Align.LEFT);
            edge.set(fill);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Ui.dp(c, 2.5f));
            edge.setColor(Ui.DARK ? 0xCCEBEBF5 : 0xB33C3C43);
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            float size = h * (n.length() > 1 ? 0.66f : 0.78f);
            fill.setTextSize(size);
            edge.setTextSize(size);
            float x = n.length() > 1 ? -size * 0.14f : Ui.dp(getContext(), 2);
            float y = h - Ui.dp(getContext(), 2);
            c.drawText(n, x, y, edge);
            c.drawText(n, x, y, fill);
        }
    }
}
