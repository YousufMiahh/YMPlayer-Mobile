package com.ymplayer.mobile;

import android.app.Activity;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Press and hold a film, series or episode: Favourite, Add to Queue, Mark as watched (and for an
 * episode, the whole season), Remove from Continue Watching.
 */
final class ItemMenu {
    private ItemMenu() {}

    /**
     * @param season  the episode's season (for "Mark season as watched"), or null
     * @param changed run after anything changed, to refresh the screen
     */
    static void show(final Activity a, final Prefs prefs, final Item it, final List<Item> season, final Runnable changed) {
        final boolean episode = Item.EPISODE.equals(it.type), series = Item.SERIES.equals(it.type), live = Item.LIVE.equals(it.type);
        List<Sheet.Option> o = new ArrayList<>();
        if (!episode) {
            final boolean fav = prefs.isFav(it);
            o.add(new Sheet.Option(fav ? "♥  Remove from Favourites" : "♡  Add to Favourites", false, done(new Runnable() {
                public void run() { Open.toggleFav(a, prefs, it); }
            }, changed), fav));
        }
        if (!series && !live) {
            final boolean queued = prefs.inQueue(it);
            o.add(new Sheet.Option(queued ? "−  Remove from Queue" : "＋  Add to Queue", false, done(new Runnable() {
                public void run() {
                    if (queued) prefs.queueRemove(it); else prefs.queueAdd(it);
                    Toast.makeText(a, (queued ? "Removed from your Queue — " : "Added to your Queue — ") + it.name, Toast.LENGTH_SHORT).show();
                }
            }, changed)));
            final boolean watched = prefs.isWatched(it);
            o.add(new Sheet.Option(watched ? "○  Mark as Unwatched" : "✓  Mark as Watched", false, done(new Runnable() {
                public void run() { prefs.setWatched(it, !watched); }
            }, changed)));
        }
        if (episode && season != null && !season.isEmpty()) {
            final boolean all = prefs.watchedCount(season) == season.size();
            o.add(new Sheet.Option(all ? "○  Mark Season as Unwatched" : "✓  Mark Season as Watched", false, done(new Runnable() {
                public void run() { prefs.setWatched(season, !all); }
            }, changed)));
        }
        if (!series && !live && (prefs.position(it) > 0 || prefs.isUpNext(it))) {
            o.add(new Sheet.Option("✕  Remove from Continue Watching", false, done(new Runnable() {
                public void run() { prefs.setPosition(it, 0); }
            }, changed), true));
        }
        if (!episode) {
            o.add(new Sheet.Option(live ? "Play" : "More info", false, new Runnable() {
                public void run() { Open.item(a, it, null); }
            }));
        }
        String title = episode && it.parentName != null ? it.parentName + "  ·  " + it.name : it.name;
        Sheet.show(a, title, null, o, 0);
    }

    private static Runnable done(final Runnable action, final Runnable changed) {
        return new Runnable() {
            public void run() {
                action.run();
                if (changed != null) changed.run();
            }
        };
    }
}
