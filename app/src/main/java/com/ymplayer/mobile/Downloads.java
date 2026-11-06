package com.ymplayer.mobile;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Movies and episodes saved to the phone for watching offline. Uses Android's own download
 * service, so downloads carry on with the app closed, show in your notifications and resume
 * after the connection drops. Files live in the app's own storage (no permissions needed) and
 * are removed if the app is uninstalled.
 */
public final class Downloads {
    private Downloads() {}

    public static final int QUEUED = 0, RUNNING = 1, PAUSED = 2, DONE = 3, FAILED = 4;

    public static class Entry {
        public Item item;
        public long id, added;
        public String file;
    }

    public static class Status {
        public int state = QUEUED;
        public long done, total;
        public String reason;

        public float fraction() { return total > 0 ? Math.min(1f, done / (float) total) : 0f; }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("downloads", Context.MODE_PRIVATE);
    }

    private static String key(Context c) {
        return "list_" + new Prefs(c).ns(); // separate list for each account
    }

    public static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp(c).getString(key(c), "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.item = Item.fromJson(o.getJSONObject("item"));
                e.id = o.optLong("id", -1);
                e.added = o.optLong("added", 0);
                e.file = o.optString("file", null);
                out.add(e);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, List<Entry> l) {
        JSONArray a = new JSONArray();
        for (Entry e : l) {
            try {
                a.put(new JSONObject().put("item", e.item.toJson()).put("id", e.id).put("added", e.added).put("file", e.file));
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString(key(c), a.toString()).apply();
    }

    public static Entry find(Context c, Item it) {
        for (Entry e : list(c)) if (e.item.key().equals(it.key())) return e;
        return null;
    }

    public static boolean wifiOnly(Context c) { return sp(c).getBoolean("wifi_only", true); }

    public static void setWifiOnly(Context c, boolean b) { sp(c).edit().putBoolean("wifi_only", b).apply(); }

    private static DownloadManager dm(Context c) {
        return (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Starts downloading a movie or episode. */
    public static void start(Context c, XtreamApi api, Item it) {
        if (find(c, it) != null) return;
        String ext = it.ext == null || it.ext.isEmpty() ? "mp4" : it.ext;
        String name = (it.type + "_" + it.id).replaceAll("[^A-Za-z0-9_]", "_") + "." + ext;
        String url = api.playUrl(it, "ts");
        String title = Item.EPISODE.equals(it.type) && it.parentName != null
                ? it.parentName + " · E" + it.num + " " + it.name : it.name;
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .setDescription("YM Player")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(c, Environment.DIRECTORY_MOVIES, name)
                .setAllowedOverMetered(!wifiOnly(c))
                .setAllowedOverRoaming(false);
        r.addRequestHeader("User-Agent", new Prefs(c).userAgent());
        Entry e = new Entry();
        e.item = it;
        e.id = dm(c).enqueue(r);
        e.added = System.currentTimeMillis();
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        e.file = dir == null ? null : new File(dir, name).getAbsolutePath();
        List<Entry> l = list(c);
        l.add(0, e);
        save(c, l);
    }

    /** Cancels a download or deletes a downloaded file. */
    public static void remove(Context c, Item it) {
        List<Entry> l = list(c);
        for (int i = l.size() - 1; i >= 0; i--) {
            Entry e = l.get(i);
            if (!e.item.key().equals(it.key())) continue;
            try {
                if (e.id >= 0) dm(c).remove(e.id);
            } catch (Exception ignored) {
            }
            if (e.file != null) new File(e.file).delete();
            l.remove(i);
        }
        save(c, l);
    }

    public static void retry(Context c, XtreamApi api, Item it) {
        remove(c, it);
        start(c, api, it);
    }

    /** Progress for several downloads in one go. */
    public static Map<Long, Status> statuses(Context c, List<Entry> entries) {
        Map<Long, Status> out = new HashMap<>();
        if (entries.isEmpty()) return out;
        long[] ids = new long[entries.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = entries.get(i).id;
        Cursor cur = null;
        try {
            cur = dm(c).query(new DownloadManager.Query().setFilterById(ids));
            while (cur != null && cur.moveToNext()) {
                Status s = new Status();
                long id = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                int st = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                int reason = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                s.done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                s.total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                switch (st) {
                    case DownloadManager.STATUS_SUCCESSFUL: s.state = DONE; break;
                    case DownloadManager.STATUS_RUNNING: s.state = RUNNING; break;
                    case DownloadManager.STATUS_PAUSED:
                        s.state = PAUSED;
                        s.reason = reason == DownloadManager.PAUSED_QUEUED_FOR_WIFI ? "Waiting for Wi-Fi"
                                : reason == DownloadManager.PAUSED_WAITING_FOR_NETWORK ? "Waiting for a connection"
                                : reason == DownloadManager.PAUSED_WAITING_TO_RETRY ? "Retrying soon" : "Paused";
                        break;
                    case DownloadManager.STATUS_FAILED:
                        s.state = FAILED;
                        s.reason = reason == DownloadManager.ERROR_INSUFFICIENT_SPACE ? "Not enough space on your phone"
                                : reason >= 400 && reason < 600 ? "Your provider refused it (HTTP " + reason + ")"
                                : "Download failed";
                        break;
                    default: s.state = QUEUED;
                }
                out.put(id, s);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        // Finished files whose record Android has since forgotten still count as downloaded
        for (Entry e : entries) {
            if (out.containsKey(e.id)) continue;
            Status s = new Status();
            File f = e.file == null ? null : new File(e.file);
            if (f != null && f.exists() && f.length() > 0) {
                s.state = DONE;
                s.done = s.total = f.length();
            } else {
                s.state = FAILED;
                s.reason = "Download failed";
            }
            out.put(e.id, s);
        }
        return out;
    }

    public static Status status(Context c, Entry e) {
        List<Entry> one = new ArrayList<>();
        one.add(e);
        return statuses(c, one).get(e.id);
    }

    /** A file:// address to play from if this movie or episode is fully downloaded, else null. */
    public static String localUrl(Context c, Item it) {
        Entry e = find(c, it);
        if (e == null || e.file == null) return null;
        File f = new File(e.file);
        if (!f.exists() || f.length() == 0) return null;
        Status s = status(c, e);
        return s != null && s.state == DONE ? Uri.fromFile(f).toString() : null;
    }

    public static long freeSpace(Context c) {
        try {
            File dir = c.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            return new StatFs(dir == null ? c.getFilesDir().getPath() : dir.getPath()).getAvailableBytes();
        } catch (Exception e) {
            return -1;
        }
    }

    public static String size(long bytes) {
        if (bytes <= 0) return "0 MB";
        double gb = bytes / 1073741824.0;
        if (gb >= 1) return String.format(java.util.Locale.UK, "%.1f GB", gb);
        return Math.max(1, Math.round(bytes / 1048576.0)) + " MB";
    }
}
