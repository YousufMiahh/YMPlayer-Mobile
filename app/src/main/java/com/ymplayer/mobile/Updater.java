package com.ymplayer.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Updates straight from the GitHub releases: on launch (at most every few hours) the app asks
 * GitHub for the latest release, and if it's newer than this one offers it: the browser
 * downloads it and Android installs it as usual.
 * Only proper releases are offered, never test builds.
 */
public final class Updater {
    private Updater() {}

    static final String REPO = "YousufMiahh/YMPlayer-Mobile";
    private static final String APP = "YM Player";
    private static final long EVERY_MS = 3 * 3600_000L;
    private static boolean busy;

    static class Release {
        int number;
        String name, notes, apkUrl;
        long size;
    }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("updater", Context.MODE_PRIVATE); }

    static int installedNumber(Context c) {
        try {
            return (int) c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * @param manual from Settings: always check, and say so if there's nothing new.
     */
    public static void check(final Activity a, final boolean manual) {
        if (busy) return;
        final SharedPreferences sp = sp(a);
        long now = System.currentTimeMillis();
        if (!manual && now - sp.getLong("checked", 0) < EVERY_MS) return;
        busy = true;
        if (manual) Toast.makeText(a, "Checking for updates…", Toast.LENGTH_SHORT).show();
        Async.run(new Async.Work<Release>() {
            public Release run() throws Exception { return latest(); }
        }, new Async.Done<Release>() {
            public void done(Release r, Exception e) {
                busy = false;
                if (a.isFinishing()) return;
                if (e != null || r == null) {
                    if (manual) Toast.makeText(a, "Couldn't check for updates — try again later", Toast.LENGTH_LONG).show();
                    return;
                }
                sp.edit().putLong("checked", System.currentTimeMillis()).apply();
                int mine = installedNumber(a);
                if (r.number <= mine || r.apkUrl == null) {
                    if (manual) Toast.makeText(a, "You're up to date (" + APP + " 1.0." + mine + ")", Toast.LENGTH_LONG).show();
                    return;
                }
                if (!manual && r.number == sp.getInt("later", 0)
                        && System.currentTimeMillis() - sp.getLong("later_at", 0) < 24 * 3600_000L) return; // "Later" pressed today
                offer(a, r);
            }
        });
    }

    /** Asks GitHub for the newest release. */
    static Release latest() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/" + REPO + "/releases/latest").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "YMPlayer-Updater");
        try {
            if (c.getResponseCode() != 200) return null;
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            JSONObject o = new JSONObject(out.toString("UTF-8"));
            Release r = new Release();
            String tag = o.optString("tag_name", "");
            r.number = Integer.parseInt(tag.substring(tag.lastIndexOf('.') + 1)); // "v1.0.51" → 51
            r.name = APP + " " + tag.replaceFirst("^v", "");
            r.notes = o.optString("body", "").trim().replaceAll("(?m)^- ", "•  ");
            JSONArray assets = o.optJSONArray("assets");
            for (int i = 0; assets != null && i < assets.length(); i++) {
                JSONObject as = assets.getJSONObject(i);
                if (as.optString("name", "").endsWith(".apk")) {
                    r.apkUrl = as.optString("browser_download_url", null);
                    r.size = as.optLong("size", 0);
                    break;
                }
            }
            return r;
        } finally {
            c.disconnect();
        }
    }

    private static AlertDialog.Builder dialog(Activity a) {
        return new AlertDialog.Builder(a, Ui.DARK ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert);
    }

    private static void offer(final Activity a, final Release r) {
        String msg = r.name + " is out. Update now downloads it in your browser; tap the file when it's done to install. Your accounts, favourites and history stay as they are.";
        if (r.notes != null && !r.notes.isEmpty() && !r.notes.startsWith("Latest test build")) {
            msg += "\n\nWhat's new:\n" + (r.notes.length() > 700 ? r.notes.substring(0, 700) + "…" : r.notes);
        }
        dialog(a).setTitle("Update available")
                .setMessage(msg)
                .setPositiveButton("Update now", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) { start(a, r); }
                })
                .setNegativeButton("Later", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        sp(a).edit().putInt("later", r.number).putLong("later_at", System.currentTimeMillis()).apply();
                    }
                })
                .show();
    }

    /**
     * The browser downloads the new version and Android installs it as usual. (The app doesn't
     * install it itself: that needs the "install other apps" permission, which makes Google Play
     * Protect treat the app as harmful.)
     */
    private static void start(final Activity a, final Release r) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Toast.makeText(a, "Downloading " + r.name + " — when it's done, tap the file to install it", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText("YM Player", r.apkUrl));
            Toast.makeText(a, "No browser found — the download link is copied, paste it into a browser", Toast.LENGTH_LONG).show();
        }
    }
}
