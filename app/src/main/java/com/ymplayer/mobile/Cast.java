package com.ymplayer.mobile;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.HlsSegmentFormat;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.images.WebImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Cast (Chromecast, Google TV and TVs with Chromecast built in), using Google's own
 * Default Media Receiver. The TV fetches the stream straight from your provider, so the phone
 * is free once it's playing. Our own pop-up lists the devices instead of Google's dialog.
 */
public final class Cast {
    private Cast() {}

    static final String RECEIVER = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

    /** Tells the Cast framework which receiver app to use (named in the manifest). */
    public static class Options implements OptionsProvider {
        @Override
        public CastOptions getCastOptions(Context c) {
            return new CastOptions.Builder().setReceiverApplicationId(RECEIVER).build();
        }

        @Override
        public List<SessionProvider> getAdditionalSessionProviders(Context c) { return null; }
    }

    private static Boolean ok;

    /** Null if this phone has no Google Play services (Cast can't work there). */
    static CastContext context(Context c) {
        if (Boolean.FALSE.equals(ok)) return null;
        try {
            CastContext cc = CastContext.getSharedInstance(c.getApplicationContext());
            ok = true;
            return cc;
        } catch (Throwable t) {
            ok = false;
            return null;
        }
    }

    static CastSession session(Context c) {
        CastContext cc = context(c);
        if (cc == null) return null;
        try {
            CastSession s = cc.getSessionManager().getCurrentCastSession();
            return s != null && s.isConnected() ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean connected(Context c) { return session(c) != null; }

    static String deviceName(Context c) {
        CastSession s = session(c);
        try {
            return s == null || s.getCastDevice() == null ? "your TV" : s.getCastDevice().getFriendlyName();
        } catch (Throwable t) {
            return "your TV";
        }
    }

    static RemoteMediaClient remote(Context c) {
        CastSession s = session(c);
        return s == null ? null : s.getRemoteMediaClient();
    }

    static void listen(Context c, SessionManagerListener<CastSession> l, boolean on) {
        CastContext cc = context(c);
        if (cc == null) return;
        try {
            SessionManager m = cc.getSessionManager();
            if (on) m.addSessionManagerListener(l, CastSession.class);
            else m.removeSessionManagerListener(l, CastSession.class);
        } catch (Throwable ignored) {
        }
    }

    /** A listener where you only care that casting started or stopped. */
    abstract static class Changes implements SessionManagerListener<CastSession> {
        abstract void changed(boolean connected);

        public void onSessionStarting(CastSession s) {}
        public void onSessionStarted(CastSession s, String id) { changed(true); }
        public void onSessionStartFailed(CastSession s, int e) { changed(false); }
        public void onSessionEnding(CastSession s) {}
        public void onSessionEnded(CastSession s, int e) { changed(false); }
        public void onSessionResuming(CastSession s, String id) {}
        public void onSessionResumed(CastSession s, boolean was) { changed(true); }
        public void onSessionResumeFailed(CastSession s, int e) { changed(false); }
        public void onSessionSuspended(CastSession s, int r) {}
    }

    // ------------------------------------------------------------ picking a TV

    /** The Cast button: choose a TV, or stop casting if already connected. */
    static void pick(final Activity a) {
        final CastContext cc = context(a);
        if (cc == null) {
            Toast.makeText(a, "Casting needs Google Play services, which this phone doesn't have", Toast.LENGTH_LONG).show();
            return;
        }
        if (connected(a)) {
            List<Sheet.Option> o = new ArrayList<>();
            o.add(new Sheet.Option("Stop casting", false, new Runnable() {
                public void run() {
                    try {
                        cc.getSessionManager().endCurrentSession(true);
                    } catch (Throwable ignored) {
                    }
                }
            }, true));
            o.add(new Sheet.Option("Keep casting", false, null));
            Sheet.show(a, "Casting to " + deviceName(a), null, o, 0);
            return;
        }
        final MediaRouter router = MediaRouter.getInstance(a);
        final MediaRouteSelector selector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(RECEIVER)).build();
        final Sheet[] sheet = new Sheet[1];
        final MediaRouter.Callback cb = new MediaRouter.Callback() {
            @Override public void onRouteAdded(MediaRouter r, MediaRouter.RouteInfo route) { refresh(); }
            @Override public void onRouteRemoved(MediaRouter r, MediaRouter.RouteInfo route) { refresh(); }
            @Override public void onRouteChanged(MediaRouter r, MediaRouter.RouteInfo route) { refresh(); }

            void refresh() {
                if (sheet[0] == null) return;
                if (!sheet[0].isOpen()) {
                    router.removeCallback(this);
                    return;
                }
                sheet[0].setOptions(devices(a, router, selector, this));
            }
        };
        router.addCallback(selector, cb, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN | MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
        sheet[0] = Sheet.show(a, "Cast to", "Your phone and TV need to be on the same Wi-Fi.", devices(a, router, selector, cb), 0);
        // Stop scanning after a while so the battery isn't drained
        Async.MAIN.postDelayed(new Runnable() {
            public void run() { router.removeCallback(cb); }
        }, 30000);
    }

    private static List<Sheet.Option> devices(final Activity a, final MediaRouter router, MediaRouteSelector selector,
                                              final MediaRouter.Callback cb) {
        List<Sheet.Option> o = new ArrayList<>();
        for (final MediaRouter.RouteInfo r : router.getRoutes()) {
            if (r.isDefault() || !r.isEnabled() || !r.matchesSelector(selector)) continue;
            o.add(new Sheet.Option(r.getName(), false, new Runnable() {
                public void run() {
                    router.removeCallback(cb);
                    router.selectRoute(r); // the Cast framework connects and tells our listeners
                    Toast.makeText(a, "Connecting to " + r.getName() + "…", Toast.LENGTH_SHORT).show();
                }
            }));
        }
        if (o.isEmpty()) o.add(new Sheet.Option("Looking for TVs and Chromecasts…", false, null).inert());
        return o;
    }

    // ------------------------------------------------------------ playing on the TV

    /**
     * Sends a stream to the TV. Live TV goes as HLS (.m3u8), the format Chromecast understands;
     * movies and episodes go as they are (.mp4 works best).
     */
    static boolean load(Context c, XtreamApi api, Item it, long startMs) {
        RemoteMediaClient rm = remote(c);
        if (rm == null) return false;
        boolean live = Item.LIVE.equals(it.type);
        String url = api.playUrl(it, "m3u8");
        MediaMetadata md = new MediaMetadata(live ? MediaMetadata.MEDIA_TYPE_GENERIC
                : Item.EPISODE.equals(it.type) ? MediaMetadata.MEDIA_TYPE_TV_SHOW : MediaMetadata.MEDIA_TYPE_MOVIE);
        md.putString(MediaMetadata.KEY_TITLE, it.name);
        if (Item.EPISODE.equals(it.type) && it.parentName != null) md.putString(MediaMetadata.KEY_SUBTITLE, it.parentName);
        String img = it.icon != null ? it.icon : it.parentIcon;
        if (img != null && img.startsWith("http")) md.addImage(new WebImage(Uri.parse(img)));
        MediaInfo.Builder mi = new MediaInfo.Builder(url)
                .setStreamType(live ? MediaInfo.STREAM_TYPE_LIVE : MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType(url, live))
                .setMetadata(md);
        if (live || url.endsWith(".m3u8")) mi.setHlsSegmentFormat(HlsSegmentFormat.TS);
        MediaLoadRequestData req = new MediaLoadRequestData.Builder()
                .setMediaInfo(mi.build())
                .setAutoplay(true)
                .setCurrentTime(live ? 0 : Math.max(0, startMs))
                .build();
        rm.load(req);
        return true;
    }

    private static String contentType(String url, boolean live) {
        String u = url.toLowerCase(java.util.Locale.UK);
        if (live || u.endsWith(".m3u8")) return "application/x-mpegurl";
        if (u.endsWith(".mkv")) return "video/x-matroska";
        if (u.endsWith(".webm")) return "video/webm";
        if (u.endsWith(".ts")) return "video/mp2t";
        return "video/mp4";
    }
}
