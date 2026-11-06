package com.ymplayer.mobile;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * Full-screen landscape player with touch controls, on either engine (VLC for Live TV,
 * the Android player for movies and series, same as the TV app).
 * Tap to show or hide the controls, double-tap the left or right side to skip back 5 s or
 * forward 10 s, drag the bar to scrub. On Live TV swipe up or down to change channel.
 */
public class PlayerActivity extends Activity implements Engine.Listener {
    static final long BACK_MS = 5000, FORWARD_MS = 10000;
    static final float[] SUB_SIZES = {0.045f, 0.056f, 0.068f, 0.085f};
    static final String[] SUB_COLOR_NAMES = {"White", "Yellow", "Cyan", "Green", "Pink", "Orange"};
    static final int[] SUB_COLORS = {0xFFFFFFFF, 0xFFFFE14D, 0xFF5AE0FF, 0xFF6BEA7A, 0xFFFF8AD8, 0xFFFFA94D};
    static final String[] SUB_OPACITY_NAMES = {"25%", "50%", "75%", "100%"};
    static final int[] SUB_OPACITY_ALPHA = {0x40, 0x80, 0xBF, 0xFF};
    private static final DecelerateInterpolator EASE = new DecelerateInterpolator(2f);

    private Prefs prefs;
    private XtreamApi api;
    private List<Item> queue;
    private int index;

    private Engine engine;
    private boolean usingVlc;
    private FrameLayout root, videoHost;
    private ProgressBar spinner;

    // Controls
    private FrameLayout controls;
    private TextView title, subtitle, timeLeft, timeRight, seekBubbleL, seekBubbleR, dragTime;
    private Icon playPause, leftBtn, rightBtn;
    private SeekBarView seek;
    private LinearLayout vodBottom, liveBottom, actions;
    private ImageView liveLogo;
    private TextView liveNow, liveTime, liveNext;
    private SeekBarView liveBar;
    private View actFav, actNext;

    // Panels
    private FrameLayout channelPanel, trackPanel;
    private ListView channelList;
    private ChannelAdapter channelAdapter;
    private LinearLayout trackList;
    private LinearLayout errorBox;
    private TextView errorText, statusPill;

    // Casting: the video is playing on a TV, this screen is the remote control
    private boolean casting;
    private long lastCastPos;
    private Icon castBtn;
    private LinearLayout castInfo;
    private TextView castText;
    private final Cast.Changes castChanges = new Cast.Changes() {
        void changed(boolean on) {
            paintCast();
            if (on && !casting) startCasting();
            else if (!on && casting) stopCasting();
        }
    };

    // State
    private String liveFormat;
    private int retries;
    private boolean triedAltFormat, triedOtherEngine;
    private boolean firstStart = true, dragging;
    private long seekTarget = -1, burst;
    private int burstSide;
    private int lastRendered = -1, stallTicks, recoveries;

    private final Runnable hideControls = new Runnable() { public void run() { showControls(false); } };
    private final Runnable endBurst = new Runnable() {
        public void run() {
            burst = 0;
            seekTarget = -1;
            seekBubbleL.animate().alpha(0f).setDuration(200).start();
            seekBubbleR.animate().alpha(0f).setDuration(200).start();
        }
    };
    private final Runnable hideStatus = new Runnable() { public void run() { statusPill.setVisibility(View.GONE); } };
    private final Runnable zapPlay = new Runnable() { public void run() { play(false); } };
    private final Runnable ticker = new Runnable() {
        public void run() {
            updateProgress();
            Async.MAIN.postDelayed(this, 500);
        }
    };
    private final Runnable watchdog = new Runnable() {
        public void run() {
            checkStall();
            Async.MAIN.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        immersive();
        prefs = new Prefs(this);
        api = new XtreamApi(prefs);
        liveFormat = prefs.liveFormat();
        queue = Queue.items;
        index = getIntent().getIntExtra("index", 0);
        if (queue == null || queue.isEmpty() || index < 0 || index >= queue.size()) {
            finish();
            return;
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        videoHost = new FrameLayout(this);
        root.addView(videoHost, match());

        final GestureDetector gestures = new GestureDetector(this, new Gestures());
        View touch = new View(this);
        View.OnTouchListener tl = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) { return gestures.onTouchEvent(e) || true; }
        };
        touch.setOnTouchListener(tl);
        root.addView(touch, match());

        spinner = new ProgressBar(this);
        spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Ui.W2));
        spinner.setVisibility(View.GONE);
        root.addView(spinner, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.CENTER));

        castInfo = new LinearLayout(this);
        castInfo.setOrientation(LinearLayout.VERTICAL);
        castInfo.setGravity(Gravity.CENTER_HORIZONTAL);
        castInfo.setVisibility(View.GONE);
        castInfo.addView(new Icon(this, Icon.Kind.CAST_ON, Ui.W2), new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 56)));
        castText = Ui.medium(this, "", 15, Ui.W2);
        castText.setPadding(0, Ui.dp(this, 12), 0, 0);
        castInfo.addView(castText);
        FrameLayout.LayoutParams cil = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cil.topMargin = -Ui.dp(this, 120); // sits above the play controls
        root.addView(castInfo, cil);

        seekBubbleL = bubble();
        seekBubbleR = bubble();
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.START);
        bl.leftMargin = Ui.dp(this, 70);
        root.addView(seekBubbleL, bl);
        FrameLayout.LayoutParams br = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.END);
        br.rightMargin = Ui.dp(this, 70);
        root.addView(seekBubbleR, br);

        buildControls();
        controls.setOnTouchListener(tl); // taps on empty parts of the controls behave like taps on the video
        buildChannelPanel();
        buildTrackPanel();
        buildError();

        statusPill = Ui.medium(this, "", 13, Ui.W1);
        statusPill.setBackground(Ui.rounded(this, 0xD91C1C22, 0, 16));
        statusPill.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        statusPill.setVisibility(View.GONE);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        sp.topMargin = Ui.dp(this, 64);
        root.addView(statusPill, sp);

        setContentView(root);
        registerPipReceiver();
    }

    private TextView bubble() {
        TextView t = Ui.medium(this, "", 15, Ui.W1);
        t.setTypeface(Ui.semibold());
        t.setBackground(Ui.rounded(this, 0x59000000, 0, 26));
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        t.setAlpha(0f);
        return t;
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) immersive();
    }

    // ------------------------------------------------------------ controls

    private void buildControls() {
        controls = new FrameLayout(this);
        controls.setVisibility(View.GONE);
        View topShade = new View(this);
        topShade.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB3000000, 0x00000000}));
        controls.addView(topShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 110), Gravity.TOP));
        View center = new View(this);
        center.setBackgroundColor(0x33000000);
        controls.addView(center, match());
        View bottomShade = new View(this);
        bottomShade.setBackground(Ui.scrimBottom());
        controls.addView(bottomShade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 190), Gravity.BOTTOM));

        int side = Ui.dp(this, 24);
        // Top: back + title
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), side, 0);
        Icon back = icon(Icon.Kind.BACK, 44, 10);
        back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { onBackPressed(); } });
        top.addView(back);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(this, 8), 0, 0, 0);
        title = Ui.oneLine(Ui.title(this, "", 17));
        title.setTextColor(Ui.W1);
        subtitle = Ui.oneLine(Ui.medium(this, "", 12, Ui.W2));
        titles.addView(title);
        titles.addView(subtitle);
        top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        castBtn = icon(Icon.Kind.CAST, 44, 10);
        castBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { Cast.pick(PlayerActivity.this); }
        });
        top.addView(castBtn);
        controls.addView(top, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // Centre: back 5 s / play-pause / forward 10 s (channel down / up on Live TV)
        LinearLayout mid = new LinearLayout(this);
        mid.setGravity(Gravity.CENTER);
        leftBtn = icon(Icon.Kind.BACK10, 60, 12);
        playPause = icon(Icon.Kind.PAUSE, 72, 16);
        rightBtn = icon(Icon.Kind.FWD10, 60, 12);
        leftBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isLive()) zap(-1);
                else seekBy(-BACK_MS, -1);
            }
        });
        rightBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isLive()) zap(1);
                else seekBy(FORWARD_MS, 1);
            }
        });
        playPause.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { togglePause(); } });
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60));
        gap.leftMargin = gap.rightMargin = Ui.dp(this, 36);
        mid.addView(leftBtn);
        mid.addView(playPause, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        mid.addView(rightBtn);
        ((LinearLayout.LayoutParams) leftBtn.getLayoutParams()).rightMargin = Ui.dp(this, 48);
        ((LinearLayout.LayoutParams) rightBtn.getLayoutParams()).leftMargin = Ui.dp(this, 48);
        controls.addView(mid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Bottom
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(side, 0, side, Ui.dp(this, 10));

        vodBottom = new LinearLayout(this);
        vodBottom.setGravity(Gravity.CENTER_VERTICAL);
        timeLeft = Ui.medium(this, "0:00", 12, Ui.W1);
        timeRight = Ui.medium(this, "", 12, Ui.W2);
        seek = new SeekBarView(this);
        seek.setBarHeight(Ui.dp(this, 3.5f));
        seek.setThumb(true);
        seek.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) { return onSeekTouch(e); }
        });
        vodBottom.addView(timeLeft);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, Ui.dp(this, 32), 1);
        slp.leftMargin = slp.rightMargin = Ui.dp(this, 8);
        vodBottom.addView(seek, slp);
        vodBottom.addView(timeRight);
        bottom.addView(vodBottom);

        dragTime = Ui.title(this, "", 30);
        dragTime.setTextColor(Ui.W1);
        dragTime.setShadowLayer(Ui.dp(this, 8), 0, 0, 0xAA000000);
        dragTime.setVisibility(View.GONE);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.post(new Runnable() { public void run() {} });
        controls.addView(dragTime, dl);

        liveBottom = new LinearLayout(this);
        liveBottom.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout logoBox = new FrameLayout(this);
        logoBox.setBackground(Ui.rounded(this, 0x26FFFFFF, 0, 8));
        liveLogo = new ImageView(this);
        liveLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int lp = Ui.dp(this, 6);
        liveLogo.setPadding(lp, lp, lp, lp);
        logoBox.addView(liveLogo, match());
        liveBottom.addView(logoBox, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 44)));
        LinearLayout epg = new LinearLayout(this);
        epg.setOrientation(LinearLayout.VERTICAL);
        epg.setPadding(Ui.dp(this, 14), 0, 0, 0);
        LinearLayout nowRow = new LinearLayout(this);
        nowRow.setGravity(Gravity.CENTER_VERTICAL);
        liveNow = Ui.oneLine(Ui.medium(this, "", 14, Ui.W1));
        liveNow.setTypeface(Ui.semibold());
        liveTime = Ui.medium(this, "", 12, Ui.W2);
        liveTime.setPadding(Ui.dp(this, 10), 0, 0, 0);
        nowRow.addView(liveNow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nowRow.addView(liveTime);
        epg.addView(nowRow);
        liveBar = new SeekBarView(this);
        LinearLayout.LayoutParams lbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3));
        lbl.topMargin = lbl.bottomMargin = Ui.dp(this, 6);
        epg.addView(liveBar, lbl);
        liveNext = Ui.oneLine(Ui.text(this, "", 12, Ui.W2));
        epg.addView(liveNext);
        liveBottom.addView(epg, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        liveBottom.setPadding(0, 0, 0, Ui.dp(this, 4));
        bottom.addView(liveBottom);

        actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, Ui.dp(this, 2), 0, 0);
        bottom.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        controls.addView(bottom, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        root.addView(controls, match());
    }

    private Icon icon(Icon.Kind k, int sizeDp, int padDp) {
        Icon i = new Icon(this, k, Ui.W1);
        int p = Ui.dp(this, padDp);
        i.setPadding(p, p, p, p);
        i.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), null, Ui.rounded(this, Color.WHITE, 0, sizeDp / 2f)));
        i.setClickable(true);
        i.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, sizeDp), Ui.dp(this, sizeDp)));
        return i;
    }

    /** Small icon + label button along the bottom ("Subtitles & Audio", "Channels"…). */
    private View action(Icon.Kind k, String label, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF), null, Ui.rounded(this, Color.WHITE, 0, 18)));
        b.setClickable(true);
        Icon i = new Icon(this, k, Ui.W1);
        b.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = Ui.medium(this, label, 13, Ui.W1);
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        b.addView(t);
        b.setOnClickListener(l);
        b.setTag(i);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = Ui.dp(this, 6);
        actions.addView(b, lp);
        return b;
    }

    private void buildActions() {
        actions.removeAllViews();
        if (isLive()) {
            action(Icon.Kind.LIST, "Channels", new View.OnClickListener() { public void onClick(View v) { openChannels(); } });
        }
        action(Icon.Kind.SUBS, isLive() ? "Audio & Subtitles" : "Subtitles & Audio", new View.OnClickListener() {
            public void onClick(View v) { openTracks(); }
        });
        final Item fav = favTarget();
        if (fav != null) {
            actFav = action(prefs.isFav(fav) ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART, "Favourite", new View.OnClickListener() {
                public void onClick(View v) {
                    prefs.toggleFav(fav);
                    ((Icon) v.getTag()).setKind(prefs.isFav(fav) ? Icon.Kind.HEART_FILLED : Icon.Kind.HEART);
                    status(prefs.isFav(fav) ? "Added to Favourites" : "Removed from Favourites");
                    scheduleHide();
                }
            });
        }
        if (Item.EPISODE.equals(current().type) && index < queue.size() - 1) {
            actNext = action(Icon.Kind.NEXT, "Next Episode", new View.OnClickListener() {
                public void onClick(View v) {
                    savePosition();
                    index++;
                    retries = 0;
                    play(true);
                }
            });
        }
    }

    /** Episodes add their series to Favourites. */
    private Item favTarget() {
        Item cur = current();
        if (!Item.EPISODE.equals(cur.type)) return cur;
        if (cur.parentId == null) return null;
        Item s = new Item(Item.SERIES, cur.parentId, cur.parentName == null ? "" : cur.parentName);
        s.icon = cur.parentIcon;
        return s;
    }

    private void showControls(boolean show) {
        Async.MAIN.removeCallbacks(hideControls);
        if (show && inPip) return;
        if (show) {
            updateProgress();
            if (controls.getVisibility() != View.VISIBLE) {
                controls.setAlpha(0f);
                controls.setVisibility(View.VISIBLE);
            }
            controls.animate().alpha(1f).setDuration(180).withEndAction(null).start();
            scheduleHide();
        } else if (controls.getVisibility() == View.VISIBLE) {
            controls.animate().alpha(0f).setDuration(220).withEndAction(new Runnable() {
                public void run() { controls.setVisibility(View.GONE); }
            }).start();
        }
    }

    private void scheduleHide() {
        Async.MAIN.removeCallbacks(hideControls);
        if (!casting && curPlaying() && !dragging) Async.MAIN.postDelayed(hideControls, 4000); // casting: controls stay up
    }

    // ------------------------------------------------------------ one set of controls for the phone or the TV

    private com.google.android.gms.cast.framework.media.RemoteMediaClient rm() { return casting ? Cast.remote(this) : null; }

    private boolean hasPlayer() { return casting || engine != null; }

    private long curPos() {
        if (casting) {
            com.google.android.gms.cast.framework.media.RemoteMediaClient r = rm();
            if (r != null) lastCastPos = r.getApproximateStreamPosition();
            return lastCastPos;
        }
        return engine == null ? 0 : engine.position();
    }

    private long curDur() {
        if (casting) {
            com.google.android.gms.cast.framework.media.RemoteMediaClient r = rm();
            return r == null ? 0 : Math.max(0, r.getStreamDuration());
        }
        return engine == null ? 0 : engine.duration();
    }

    private long curBuffered() { return casting || engine == null ? 0 : engine.buffered(); }

    private boolean curPlaying() {
        if (casting) {
            com.google.android.gms.cast.framework.media.RemoteMediaClient r = rm();
            return r != null && r.isPlaying();
        }
        return engine != null && engine.isPlaying();
    }

    private void curSeek(long ms) {
        if (casting) {
            com.google.android.gms.cast.framework.media.RemoteMediaClient r = rm();
            if (r != null) r.seek(new com.google.android.gms.cast.MediaSeekOptions.Builder().setPosition(ms).build());
            lastCastPos = ms;
        } else if (engine != null) {
            engine.seekTo(ms);
        }
    }

    private void paintCast() {
        if (castBtn == null) return;
        boolean on = Cast.connected(this);
        castBtn.setKind(on ? Icon.Kind.CAST_ON : Icon.Kind.CAST);
        castBtn.setColor(on ? Ui.ACCENT : Ui.W1);
    }

    /** Casting just connected: hand the video over to the TV, carrying on from the same point. */
    private void startCasting() {
        long pos = isLive() || engine == null ? prefs.position(current()) : engine.position();
        savePosition();
        releaseEngine();
        casting = true;
        lastCastPos = pos;
        showCastInfo();
        Cast.load(this, api, current(), isLive() ? 0 : pos);
        showControls(true);
    }

    /** Casting ended: carry on here on the phone. */
    private void stopCasting() {
        long pos = lastCastPos;
        casting = false;
        castInfo.setVisibility(View.GONE);
        if (!isLive() && pos > 10000) prefs.setPosition(current(), pos);
        play(true);
    }

    private void showCastInfo() {
        castText.setText("Playing on " + Cast.deviceName(this));
        castInfo.setVisibility(View.VISIBLE);
        spinner.setVisibility(View.GONE);
        errorBox.setVisibility(View.GONE);
    }

    private boolean controlsShown() { return controls.getVisibility() == View.VISIBLE && controls.getAlpha() > 0.5f; }

    /** Tap: controls on/off. Double-tap a side: skip. Swipe up/down on Live TV: change channel. */
    private class Gestures extends GestureDetector.SimpleOnGestureListener {
        @Override public boolean onDown(MotionEvent e) { return true; }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (panelOpen()) {
                hidePanels();
                return true;
            }
            showControls(!controlsShown());
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (!hasPlayer() || panelOpen()) return false;
            float w = root.getWidth();
            if (isLive()) {
                togglePause();
                return true;
            }
            if (e.getX() < w * 0.38f) seekBy(-BACK_MS, -1);
            else if (e.getX() > w * 0.62f) seekBy(FORWARD_MS, 1);
            else togglePause();
            return true;
        }

        @Override
        public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
            if (!isLive() || a == null || panelOpen()) return false;
            float dy = b.getY() - a.getY(), dx = b.getX() - a.getX();
            if (Math.abs(dy) > Ui.dp(PlayerActivity.this, 60) && Math.abs(dy) > Math.abs(dx) * 1.5f) {
                zap(dy < 0 ? 1 : -1);
                return true;
            }
            return false;
        }
    }

    private boolean onSeekTouch(MotionEvent e) {
        if (!hasPlayer() || isLive()) return false;
        long d = curDur();
        if (d <= 0) return false;
        float f = seek.fractionAt(e.getX());
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                seek.getParent().requestDisallowInterceptTouchEvent(true);
                seek.setDragging(true);
                Async.MAIN.removeCallbacks(hideControls);
                dragTime.setVisibility(View.VISIBLE);
                // fall through
            case MotionEvent.ACTION_MOVE:
                seekTarget = (long) (f * d);
                seek.set(f, curBuffered() / (float) d);
                timeLeft.setText(SeriesActivity.fmt(seekTarget));
                timeRight.setText("−" + SeriesActivity.fmt(Math.max(0, d - seekTarget)));
                dragTime.setText(SeriesActivity.fmt(seekTarget));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                seek.setDragging(false);
                dragTime.setVisibility(View.GONE);
                long target = Math.min((long) (f * d), d - 1000);
                curSeek(Math.max(0, target));
                seekTarget = -1;
                scheduleHide();
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------ panels

    /** A panel sliding in from the right, over the video. */
    private FrameLayout sidePanel(String heading) {
        FrameLayout p = new FrameLayout(this);
        p.setBackgroundColor(0xF21C1C22);
        p.setVisibility(View.GONE);
        p.setClickable(true);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 4));
        TextView t = Ui.title(this, heading, 18);
        t.setTextColor(Ui.W1);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Icon close = icon(Icon.Kind.CLOSE, 40, 10);
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { hidePanels(); } });
        head.addView(close);
        p.addView(head, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60), Gravity.TOP));
        int w = Math.min(Ui.dp(this, 360), (int) (getResources().getDisplayMetrics().widthPixels * 0.5f));
        root.addView(p, new FrameLayout.LayoutParams(Math.max(w, Ui.dp(this, 300)), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        return p;
    }

    private void buildChannelPanel() {
        channelPanel = sidePanel("Channels");
        channelList = new ListView(this);
        channelList.setDivider(null);
        channelList.setSelector(new android.graphics.drawable.ColorDrawable(0));
        channelList.setVerticalScrollBarEnabled(false);
        channelAdapter = new ChannelAdapter();
        channelList.setAdapter(channelAdapter);
        channelList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> a, View v, int pos, long id) {
                hidePanels();
                if (pos != index) {
                    index = pos;
                    resetLiveState();
                    play(false);
                }
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = Ui.dp(this, 60);
        channelPanel.addView(channelList, lp);
    }

    private class ChannelAdapter extends BaseAdapter {
        public int getCount() { return queue == null ? 0 : queue.size(); }
        public Object getItem(int i) { return queue.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout r;
            ImageView logo;
            TextView name;
            if (v == null) {
                r = new LinearLayout(PlayerActivity.this);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(Ui.dp(PlayerActivity.this, 16), Ui.dp(PlayerActivity.this, 6), Ui.dp(PlayerActivity.this, 16), Ui.dp(PlayerActivity.this, 6));
                logo = new ImageView(PlayerActivity.this);
                logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
                r.addView(logo, new LinearLayout.LayoutParams(Ui.dp(PlayerActivity.this, 56), Ui.dp(PlayerActivity.this, 34)));
                name = Ui.oneLine(Ui.medium(PlayerActivity.this, "", 14, Ui.W1));
                name.setPadding(Ui.dp(PlayerActivity.this, 12), 0, 0, 0);
                r.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                r.setTag(new Object[]{logo, name});
                v = r;
            } else {
                r = (LinearLayout) v;
            }
            Object[] h = (Object[]) r.getTag();
            logo = (ImageView) h[0];
            name = (TextView) h[1];
            Item it = queue.get(i);
            boolean playing = i == index;
            name.setText((it.num != null && !it.num.isEmpty() ? it.num + "   " : "") + it.name + (prefs.isFav(it) ? "  ♥" : ""));
            name.setTypeface(playing ? Ui.bold() : Ui.medium());
            r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                    playing ? new android.graphics.drawable.ColorDrawable(0x26FFFFFF) : null, new android.graphics.drawable.ColorDrawable(Color.WHITE)));
            ImageLoader.load(logo, it.icon, 160, 96);
            return v;
        }
    }

    private void buildTrackPanel() {
        trackPanel = sidePanel("Subtitles & Audio");
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        trackList.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 16));
        sv.addView(trackList);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.topMargin = Ui.dp(this, 60);
        trackPanel.addView(sv, lp);
    }

    private void buildError() {
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER_HORIZONTAL);
        errorBox.setBackground(Ui.rounded(this, 0xF21C1C22, 0, 18));
        int p = Ui.dp(this, 22);
        errorBox.setPadding(p, p, p, p);
        errorBox.setVisibility(View.GONE);
        errorBox.setClickable(true);
        errorText = Ui.text(this, "", 14, Ui.W1);
        errorText.setGravity(Gravity.CENTER);
        errorText.setLineSpacing(0, 1.3f);
        errorBox.addView(errorText);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, Ui.dp(this, 16), 0, 0);
        Button again = Ui.primary(this, "Try Again");
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                resetLiveState();
                play(false);
            }
        });
        Button close = Ui.button(this, "Close");
        close.setTextColor(Ui.W1);
        close.setBackground(Ui.rounded(this, 0x33FFFFFF, 0, 10));
        close.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { finish(); } });
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.leftMargin = l.rightMargin = Ui.dp(this, 6);
        row.addView(again, l);
        row.addView(close, l);
        errorBox.addView(row);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.dp(this, 420), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        root.addView(errorBox, lp);
    }

    private boolean panelOpen() {
        return channelPanel.getVisibility() == View.VISIBLE || trackPanel.getVisibility() == View.VISIBLE;
    }

    private void slide(final View p, boolean show) {
        if (show) {
            p.setVisibility(View.VISIBLE);
            p.setTranslationX(p.getWidth() > 0 ? p.getWidth() : Ui.dp(this, 360));
            p.animate().translationX(0).setDuration(260).setInterpolator(EASE).withEndAction(null).start();
        } else if (p.getVisibility() == View.VISIBLE) {
            p.animate().translationX(p.getWidth()).setDuration(200).withEndAction(new Runnable() {
                public void run() { p.setVisibility(View.GONE); }
            }).start();
        }
    }

    private void hidePanels() {
        slide(channelPanel, false);
        slide(trackPanel, false);
    }

    private void openChannels() {
        showControls(false);
        channelAdapter.notifyDataSetChanged();
        slide(channelPanel, true);
        channelList.setSelection(Math.max(0, index - 3));
    }

    private void openTracks() {
        showControls(false);
        buildTrackRows();
        slide(trackPanel, true);
    }

    private void buildTrackRows() {
        trackList.removeAllViews();
        if (engine == null) return;
        header("Subtitles");
        boolean off = engine.subtitlesAreOff();
        option("Off", off, new Runnable() {
            public void run() {
                engine.subtitlesOff();
                prefs.setSubtitleLanguage("off");
            }
        });
        List<Engine.Track> text = engine.tracks(Engine.TEXT);
        for (final Engine.Track t : text) {
            option(t.label, t.selected, new Runnable() {
                public void run() {
                    engine.select(t);
                    prefs.setSubtitleLanguage(t.language == null ? "" : t.language);
                }
            });
        }
        if (text.isEmpty()) note("This stream has no subtitles");

        header("Audio");
        List<Engine.Track> audio = engine.tracks(Engine.AUDIO);
        for (final Engine.Track t : audio) {
            option(t.label, t.selected, new Runnable() {
                public void run() {
                    engine.select(t);
                    prefs.setAudioLanguage(t.language == null ? "" : t.language);
                }
            });
        }
        if (audio.isEmpty()) note("No audio tracks found");

        header("Subtitle Size");
        for (int s = 0; s < SettingsActivity.SUB_SIZES.length; s++) {
            final int size = s;
            option(SettingsActivity.SUB_SIZES[s], prefs.subtitleSize() == s, new Runnable() {
                public void run() {
                    prefs.setSubtitleSize(size);
                    engine.applySubtitleStyle();
                }
            });
        }
        if (usingVlc) note("With VLC, size changes apply from the next video");

        if (!isLive()) {
            header("Player");
            option("Android player", !usingVlc, new Runnable() {
                public void run() {
                    if (!usingVlc) return;
                    prefs.setPlayerChoice(current(), false);
                    hidePanels();
                    switchEngine(false, "Switched to Android player");
                }
            });
            option("VLC player", usingVlc, new Runnable() {
                public void run() {
                    if (usingVlc) return;
                    prefs.setPlayerChoice(current(), true);
                    hidePanels();
                    switchEngine(true, "Switched to VLC player");
                }
            });
            note("Picture freezing? Try the other player — it's remembered for this video.");
        }
    }

    private void header(String s) {
        TextView t = Ui.medium(this, s.toUpperCase(Locale.UK), 11, Ui.W3);
        t.setLetterSpacing(0.1f);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, trackList.getChildCount() == 0 ? 4 : 18), 0, Ui.dp(this, 6));
        trackList.addView(t);
    }

    private void note(String s) {
        TextView t = Ui.text(this, s, 12, Ui.W2);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        trackList.addView(t);
    }

    private void option(String label, boolean selected, final Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        r.setMinimumHeight(Ui.dp(this, 44));
        r.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33FFFFFF),
                selected ? Ui.rounded(this, 0x1FFFFFFF, 0, 10) : null, Ui.rounded(this, Color.WHITE, 0, 10)));
        r.setClickable(true);
        TextView l = Ui.oneLine(Ui.medium(this, label, 14, Ui.W1));
        if (selected) l.setTypeface(Ui.bold());
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (selected) r.addView(new Icon(this, Icon.Kind.CHECK, Ui.W1), new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        r.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                action.run();
                Async.MAIN.postDelayed(new Runnable() {
                    public void run() { if (trackPanel.getVisibility() == View.VISIBLE) buildTrackRows(); }
                }, 250);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 2);
        trackList.addView(r, lp);
    }

    // ------------------------------------------------------------ picture in picture

    private static final String PIP_ACTION = "com.ymplayer.mobile.PIP";
    private static final int PIP_BACK = 1, PIP_TOGGLE = 2, PIP_FWD = 3;
    private boolean inPip;
    private android.content.BroadcastReceiver pipReceiver;

    static boolean pipEnabled(android.content.Context c) {
        return new Prefs(c).raw().getBoolean("pip", true);
    }

    private boolean canPip() {
        return Build.VERSION.SDK_INT >= 26 && pipEnabled(this) && engine != null && !casting
                && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /** The buttons in the small window: back 5 s / play-pause / forward 10 s (or channel down / up on Live TV). */
    @android.annotation.TargetApi(26)
    private android.app.PictureInPictureParams pipParams() {
        android.app.PictureInPictureParams.Builder b = new android.app.PictureInPictureParams.Builder()
                .setAspectRatio(new android.util.Rational(16, 9));
        java.util.List<android.app.RemoteAction> acts = new java.util.ArrayList<>();
        boolean live = isLive();
        if (!live || queue.size() > 1) acts.add(pipAction(PIP_BACK, live ? Icon.Kind.PREV : Icon.Kind.BACK10, live ? "Previous channel" : "Back 5 seconds"));
        boolean playing = curPlaying();
        acts.add(pipAction(PIP_TOGGLE, playing ? Icon.Kind.PAUSE : Icon.Kind.PLAY, playing ? "Pause" : "Play"));
        if (!live || queue.size() > 1) acts.add(pipAction(PIP_FWD, live ? Icon.Kind.NEXT : Icon.Kind.FWD10, live ? "Next channel" : "Forward 10 seconds"));
        b.setActions(acts);
        if (Build.VERSION.SDK_INT >= 31) {
            // Swiping home goes straight into the small window (Android 12+)
            try {
                b.getClass().getMethod("setAutoEnterEnabled", boolean.class).invoke(b, canPip() && playing);
            } catch (Exception ignored) {
            }
        }
        return b.build();
    }

    @android.annotation.TargetApi(26)
    private android.app.RemoteAction pipAction(int cmd, Icon.Kind kind, String title) {
        int size = Ui.dp(this, 32);
        Icon v = new Icon(this, kind, Color.WHITE);
        v.measure(View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, size, size);
        android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        v.draw(new android.graphics.Canvas(bm));
        android.content.Intent i = new android.content.Intent(PIP_ACTION).setPackage(getPackageName()).putExtra("cmd", cmd);
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(this, cmd, i,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
        return new android.app.RemoteAction(android.graphics.drawable.Icon.createWithBitmap(bm), title, title, pi);
    }

    private void registerPipReceiver() {
        if (Build.VERSION.SDK_INT < 26) return;
        pipReceiver = new android.content.BroadcastReceiver() {
            public void onReceive(android.content.Context c, android.content.Intent i) {
                switch (i.getIntExtra("cmd", 0)) {
                    case PIP_BACK:
                        if (isLive()) zap(-1);
                        else seekBy(-BACK_MS, -1);
                        break;
                    case PIP_TOGGLE:
                        togglePause();
                        break;
                    case PIP_FWD:
                        if (isLive()) zap(1);
                        else seekBy(FORWARD_MS, 1);
                        break;
                }
                Async.MAIN.postDelayed(new Runnable() { public void run() { updatePip(); } }, 300);
            }
        };
        android.content.IntentFilter f = new android.content.IntentFilter(PIP_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pipReceiver, f, 4 /* RECEIVER_NOT_EXPORTED */);
        else registerReceiver(pipReceiver, f);
    }

    /** Keep the small window's buttons (and Android 12's auto-enter) in step with playback. */
    private void updatePip() {
        if (Build.VERSION.SDK_INT < 26 || engine == null) return;
        try {
            if (inPip || Build.VERSION.SDK_INT >= 31) setPictureInPictureParams(pipParams());
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Home button / swipe away while watching: keep playing in a small window
        if (canPip() && curPlaying() && !panelOpen()) {
            try {
                enterPictureInPictureMode(pipParams());
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean pip, android.content.res.Configuration cfg) {
        super.onPictureInPictureModeChanged(pip, cfg);
        inPip = pip;
        if (pip) {
            Async.MAIN.removeCallbacks(hideControls);
            controls.setVisibility(View.GONE);
            hidePanels();
            statusPill.setVisibility(View.GONE);
            seekBubbleL.setAlpha(0f);
            seekBubbleR.setAlpha(0f);
        } else {
            immersive();
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onStart() {
        super.onStart();
        if (videoHost == null) return;
        Cast.listen(this, castChanges, true);
        paintCast();
        play(true);
        firstStart = false;
        Async.MAIN.post(ticker);
        Async.MAIN.postDelayed(watchdog, 1000);
    }

    @Override
    protected void onStop() {
        Async.MAIN.removeCallbacks(ticker);
        Async.MAIN.removeCallbacks(watchdog);
        Async.MAIN.removeCallbacks(zapPlay);
        Cast.listen(this, castChanges, false);
        savePosition(); // if casting, the TV just carries on
        releaseEngine();
        boolean closedPip = inPip;
        super.onStop();
        if (closedPip) finish(); // the small window was closed: stop for good
    }

    @Override
    protected void onDestroy() {
        Runnable[] all = {hideControls, endBurst, hideStatus, zapPlay, ticker, watchdog};
        for (Runnable r : all) Async.MAIN.removeCallbacks(r);
        if (pipReceiver != null) {
            try {
                unregisterReceiver(pipReceiver);
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------ engines

    /** Live TV always uses VLC (the Android player doesn't cope with these live streams), unless Settings says otherwise. */
    private boolean wantsVlc(Item it) {
        if (Item.LIVE.equals(it.type)) return prefs.engineMode() != 1;
        if (prefs.vlcBroken()) return false;
        int mode = prefs.engineMode();
        if (mode == 2) return true;
        if (mode == 1) return false;
        Boolean choice = prefs.playerChoice(it);
        if (choice != null) return choice;
        return false;
    }

    private void startEngine(boolean vlc) {
        releaseEngine();
        try {
            engine = vlc ? new VlcEngine(this, prefs, this) : new ExoEngine(this, prefs, this);
            usingVlc = vlc;
        } catch (Throwable t) {
            engine = new ExoEngine(this, prefs, this);
            usingVlc = false;
        }
        videoHost.addView(engine.view(), 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
    }

    private void releaseEngine() {
        if (engine == null) return;
        videoHost.removeView(engine.view());
        engine.release();
        engine = null;
        spinner.setVisibility(View.GONE);
    }

    private void switchEngine(boolean vlc, String why) {
        long pos = isLive() || engine == null ? 0 : engine.position();
        startEngine(vlc);
        status(why);
        load(pos);
    }

    public void onReady() {
        retries = 0;
        errorBox.setVisibility(View.GONE);
    }

    public void onEnded() {
        if (isLive()) {
            play(false);
            return;
        }
        prefs.setPosition(current(), 0);
        if (Item.EPISODE.equals(current().type) && index < queue.size() - 1) {
            index++;
            retries = 0;
            play(false);
        } else {
            finish();
        }
    }

    public void onPlayingChanged(boolean playing) {
        playPause.setKind(playing ? Icon.Kind.PAUSE : Icon.Kind.PLAY);
        updatePip();
        if (inPip) return;
        if (!playing && engine != null && engine.isReady() && !isLive()) {
            showControls(true);
            Async.MAIN.removeCallbacks(hideControls);
        } else if (playing) {
            scheduleHide();
        }
    }

    public void onBuffering(boolean buffering) {
        spinner.setVisibility(buffering ? View.VISIBLE : View.GONE);
    }

    public void onTracksChanged() {
        if (trackPanel.getVisibility() == View.VISIBLE) buildTrackRows();
    }

    public void onError(String what, boolean behindLiveWindow) {
        if (engine == null) return;
        if (behindLiveWindow) {
            engine.retry(true);
            return;
        }
        if (retries < 2) {
            retries++;
            status("Reconnecting…");
            Async.MAIN.postDelayed(new Runnable() {
                public void run() { if (engine != null) engine.retry(false); }
            }, 1500);
            return;
        }
        if (isLive() && !triedAltFormat) {
            triedAltFormat = true;
            retries = 0;
            liveFormat = "ts".equals(liveFormat) ? "m3u8" : "ts";
            load(0);
            return;
        }
        if (!isLive() && !triedOtherEngine && prefs.engineMode() == 0) {
            triedOtherEngine = true;
            retries = 0;
            liveFormat = prefs.liveFormat();
            switchEngine(!usingVlc, usingVlc ? "Trying Android player…" : "Trying VLC player…");
            return;
        }
        errorText.setText("Can't play this " + (isLive() ? "channel" : "video") + "\n" + what
                + (isLive() && queue.size() > 1 ? "\n\nSwipe up or down for another channel." : ""));
        errorBox.setVisibility(View.VISIBLE);
        spinner.setVisibility(View.GONE);
    }

    private Item current() { return queue.get(index); }

    private boolean isLive() { return Item.LIVE.equals(current().type); }

    // ------------------------------------------------------------ playback

    private void play(boolean resume) {
        if (videoHost == null) return;
        Item it = current();
        long start = 0;
        if (!isLive() && resume) {
            long pos = prefs.position(it);
            if (pos > 10000) {
                start = pos;
                if (firstStart) Toast.makeText(this, "Resuming from " + SeriesActivity.fmt(pos), Toast.LENGTH_SHORT).show();
            }
        }
        if (Cast.connected(this)) { // already casting: send it straight to the TV
            releaseEngine();
            casting = true;
            lastCastPos = start;
            showCastInfo();
            Cast.load(this, api, it, start);
        } else {
            casting = false;
            castInfo.setVisibility(View.GONE);
            boolean vlc = wantsVlc(it);
            if (engine == null || vlc != usingVlc) startEngine(vlc);
            load(start);
        }
        prefs.addHistory(it);
        boolean live = isLive();
        vodBottom.setVisibility(live ? View.GONE : View.VISIBLE);
        liveBottom.setVisibility(live ? View.VISIBLE : View.GONE);
        leftBtn.setKind(live ? Icon.Kind.PREV : Icon.Kind.BACK10);
        rightBtn.setKind(live ? Icon.Kind.NEXT : Icon.Kind.FWD10);
        leftBtn.setVisibility(live && queue.size() < 2 ? View.INVISIBLE : View.VISIBLE);
        rightBtn.setVisibility(live && queue.size() < 2 ? View.INVISIBLE : View.VISIBLE);
        if (live) {
            title.setText((it.num != null && !it.num.isEmpty() ? it.num + "  ·  " : "") + it.name);
            subtitle.setText(queue.size() > 1 ? "Swipe up or down to change channel" : "Live");
            showEpg();
        } else {
            title.setText(it.name);
            subtitle.setText(Item.EPISODE.equals(it.type) ? XtreamApi.joinNonEmpty(it.parentName, "Episode " + it.num) : "");
        }
        subtitle.setVisibility(subtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);
        buildActions();
        showControls(true);
    }

    private void load(long startMs) {
        errorBox.setVisibility(View.GONE);
        lastRendered = -1;
        stallTicks = 0;
        try {
            engine.load(streamUrl(), startMs);
        } catch (Throwable t) {
            App.writeCrash(this, t);
            if (!usingVlc || isLive()) throw t instanceof RuntimeException ? (RuntimeException) t : new RuntimeException(t);
            prefs.setVlcBroken(true);
            startEngine(false);
            status("VLC couldn't start — using the Android player");
            engine.load(streamUrl(), startMs);
        }
    }

    /** Downloaded movies and episodes play from the phone; everything else streams. */
    private String streamUrl() {
        String local = isLive() ? null : Downloads.localUrl(this, current());
        return local != null ? local : api.playUrl(current(), liveFormat);
    }

    private void savePosition() {
        if (!hasPlayer() || queue == null || isLive()) return;
        long d = curDur(), p = curPos();
        if (d > 0) prefs.setDuration(current(), d);
        if (d > 0 && p > d - 60000) prefs.setPosition(current(), 0);
        else if (p > 10000) prefs.setPosition(current(), p);
    }

    private void resetLiveState() {
        retries = 0;
        triedAltFormat = false;
        triedOtherEngine = false;
        recoveries = 0;
        liveFormat = prefs.liveFormat();
    }

    /** Frozen-picture detector: playing, but no new frames for 3 s. */
    private void checkStall() {
        if (engine == null || !prefs.autoRecover() || !engine.isPlaying()) {
            stallTicks = 0;
            return;
        }
        float fps = engine.frameRate();
        int r = engine.framesRendered();
        if (r < 0 || fps < 0) return;
        if (fps > 0 && fps < 5) return;
        if (r != lastRendered) {
            lastRendered = r;
            stallTicks = 0;
            return;
        }
        if (++stallTicks < 3) return;
        stallTicks = 0;
        if (prefs.engineMode() == 0 && !usingVlc && prefs.playerChoice(current()) == null) {
            prefs.setPlayerChoice(current(), true);
            switchEngine(true, "Picture froze — switching to VLC player");
            return;
        }
        if (recoveries >= 4) return;
        recoveries++;
        status("Re-syncing video…");
        if (isLive()) load(0);
        else engine.seekTo(engine.position());
    }

    private void updateProgress() {
        if (casting) playPause.setKind(curPlaying() ? Icon.Kind.PAUSE : Icon.Kind.PLAY);
        if (!hasPlayer() || isLive() || dragging || controls.getVisibility() != View.VISIBLE) return;
        long d = curDur();
        long p = seekTarget >= 0 ? seekTarget : curPos();
        if (d <= 0) {
            seek.set(0, 0);
            timeLeft.setText(SeriesActivity.fmt(p));
            timeRight.setText("");
            return;
        }
        seek.set(p / (float) d, curBuffered() / (float) d);
        timeLeft.setText(SeriesActivity.fmt(p));
        timeRight.setText("−" + SeriesActivity.fmt(Math.max(0, d - p)));
    }

    private void showEpg() {
        final Item it = current();
        final int at = index;
        liveNow.setText("");
        liveTime.setText("");
        liveNext.setText("");
        liveBar.setVisibility(View.INVISIBLE);
        ImageLoader.load(liveLogo, it.icon, 200, 120);
        Async.run(new Async.Work<List<XtreamApi.Programme>>() {
            public List<XtreamApi.Programme> run() { return api.epg(it.id); }
        }, new Async.Done<List<XtreamApi.Programme>>() {
            public void done(List<XtreamApi.Programme> r, Exception e) {
                if (isFinishing() || at != index) return;
                if (r == null || r.isEmpty()) {
                    liveNow.setText("No guide information");
                    return;
                }
                XtreamApi.Programme now = r.get(0);
                liveNow.setText(now.title);
                liveTime.setText(now.timeRange());
                if (now.start > 0) {
                    liveBar.set(now.progress(), 0);
                    liveBar.setVisibility(View.VISIBLE);
                }
                if (r.size() > 1) {
                    XtreamApi.Programme n = r.get(1);
                    String t = n.timeRange();
                    liveNext.setText("Next   " + (t.length() >= 5 ? t.substring(0, 5) + "   " : "") + n.title);
                }
            }
        });
    }

    private void status(String s) {
        if (inPip) return;
        statusPill.setText(s);
        statusPill.setVisibility(View.VISIBLE);
        Async.MAIN.removeCallbacks(hideStatus);
        Async.MAIN.postDelayed(hideStatus, 2500);
    }

    private void togglePause() {
        if (casting) {
            com.google.android.gms.cast.framework.media.RemoteMediaClient r = rm();
            if (r != null) r.togglePlayback();
            playPause.setKind(curPlaying() ? Icon.Kind.PLAY : Icon.Kind.PAUSE);
        } else {
            if (engine == null) return;
            if (engine.isPlaying()) engine.pause();
            else engine.play();
        }
        pulse(playPause);
    }

    /** Double-tap skipping: repeated taps add up ("+30 sec"), shown on that side of the screen. */
    private void seekBy(long delta, int side) {
        if (!hasPlayer()) return;
        long d = curDur();
        long base = seekTarget >= 0 ? seekTarget : curPos();
        long target = Math.max(0, base + delta);
        if (d > 0) target = Math.min(target, d - 1000);
        seekTarget = target;
        if (side != burstSide) burst = 0;
        burstSide = side;
        burst += delta;
        curSeek(target);
        long s = Math.abs(burst) / 1000;
        TextView b = side < 0 ? seekBubbleL : seekBubbleR;
        (side < 0 ? seekBubbleR : seekBubbleL).setAlpha(0f);
        b.setText((burst < 0 ? "« " : "") + (burst < 0 ? "−" : "+") + (s >= 60 ? SeriesActivity.fmt(Math.abs(burst)) : s + " sec") + (burst > 0 ? " »" : ""));
        b.animate().cancel();
        b.setAlpha(1f);
        pulse(b);
        Async.MAIN.removeCallbacks(endBurst);
        Async.MAIN.postDelayed(endBurst, 900);
        updateProgress();
        if (controlsShown()) scheduleHide();
    }

    private void pulse(final View v) {
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).withEndAction(new Runnable() {
            public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(140).start(); }
        }).start();
    }

    private void zap(int dir) {
        if (queue.size() < 2) return;
        index = (index + dir + queue.size()) % queue.size();
        resetLiveState();
        Item it = current();
        title.setText((it.num != null && !it.num.isEmpty() ? it.num + "  ·  " : "") + it.name);
        status((dir > 0 ? "▲  " : "▼  ") + it.name);
        Async.MAIN.removeCallbacks(zapPlay);
        Async.MAIN.postDelayed(zapPlay, 350); // lets you flick through several channels quickly
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        // Bluetooth keyboards / media buttons
        if (engine != null && e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case android.view.KeyEvent.KEYCODE_SPACE:
                    togglePause();
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!isLive()) seekBy(FORWARD_MS, 1);
                    return true;
                case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!isLive()) seekBy(-BACK_MS, -1);
                    return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @Override
    public void onBackPressed() {
        if (videoHost != null && panelOpen()) {
            hidePanels();
            return;
        }
        // After picture in picture the player is in a window of its own: Back returns to the app, not the home screen
        if (isTaskRoot()) {
            startActivity(new android.content.Intent(this, MainActivity.class)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        }
        super.onBackPressed();
    }
}
