package com.extreme.dvplayer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class LibraryStore {
    private static final String PREFS = "extreme_dv_library";
    private static final String KEY_RECENTS = "recents";
    private static final String KEY_PLAYLISTS = "playlists";
    private static final String KEY_FOLDERS = "folders";
    private static final String KEY_NETWORKS = "networks";

    private LibraryStore() {}

    public static class Recent {
        public String uri;
        public String title;
        public long durationMs;
        public long positionMs;
        public long sizeBytes;
        public int width;
        public int height;
        public String badge;
        public long lastPlayed;
    }

    public static class NetworkProfile {
        public String name;
        public String type;
        public String address;
        public String username;
        public String password;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized void saveRecent(Context c, Recent r) {
        try {
            JSONArray old = new JSONArray(prefs(c).getString(KEY_RECENTS, "[]"));
            JSONArray out = new JSONArray();
            out.put(toJson(r));
            for (int i = 0; i < old.length() && out.length() < 30; i++) {
                JSONObject o = old.optJSONObject(i);
                if (o == null) continue;
                if (r.uri.equals(o.optString("uri"))) continue;
                out.put(o);
            }
            prefs(c).edit().putString(KEY_RECENTS, out.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<Recent> getRecents(Context c) {
        ArrayList<Recent> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_RECENTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                Recent r = new Recent();
                r.uri = o.optString("uri");
                r.title = o.optString("title", "Video");
                r.durationMs = o.optLong("durationMs");
                r.positionMs = o.optLong("positionMs");
                r.sizeBytes = o.optLong("sizeBytes");
                r.width = o.optInt("width");
                r.height = o.optInt("height");
                r.badge = o.optString("badge", "VIDEO");
                r.lastPlayed = o.optLong("lastPlayed");
                list.add(r);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void clearRecents(Context c) {
        prefs(c).edit().putString(KEY_RECENTS, "[]").apply();
    }

    private static JSONObject toJson(Recent r) throws Exception {
        JSONObject o = new JSONObject();
        o.put("uri", r.uri);
        o.put("title", r.title);
        o.put("durationMs", r.durationMs);
        o.put("positionMs", r.positionMs);
        o.put("sizeBytes", r.sizeBytes);
        o.put("width", r.width);
        o.put("height", r.height);
        o.put("badge", r.badge);
        o.put("lastPlayed", r.lastPlayed);
        return o;
    }

    public static List<String> getPlaylists(Context c) {
        return getStringArray(c, KEY_PLAYLISTS);
    }

    public static void addPlaylist(Context c, String name) {
        ArrayList<String> l = new ArrayList<>(getPlaylists(c));
        if (!name.trim().isEmpty() && !l.contains(name.trim())) l.add(name.trim());
        saveStringArray(c, KEY_PLAYLISTS, l);
    }

    public static List<String> getFolders(Context c) {
        return getStringArray(c, KEY_FOLDERS);
    }

    public static void addFolder(Context c, String uri) {
        ArrayList<String> l = new ArrayList<>(getFolders(c));
        if (!l.contains(uri)) l.add(uri);
        saveStringArray(c, KEY_FOLDERS, l);
    }

    public static List<NetworkProfile> getNetworks(Context c) {
        ArrayList<NetworkProfile> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_NETWORKS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                NetworkProfile p = new NetworkProfile();
                p.name = o.optString("name"); p.type = o.optString("type"); p.address = o.optString("address");
                p.username = o.optString("username"); p.password = o.optString("password");
                list.add(p);
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void addNetwork(Context c, NetworkProfile p) {
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_NETWORKS, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", p.name); o.put("type", p.type); o.put("address", p.address);
            o.put("username", p.username); o.put("password", p.password);
            a.put(o);
            prefs(c).edit().putString(KEY_NETWORKS, a.toString()).apply();
        } catch (Exception ignored) {}
    }

    private static List<String> getStringArray(Context c, String key) {
        ArrayList<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } catch (Exception ignored) {}
        return out;
    }

    private static void saveStringArray(Context c, String key, List<String> values) {
        JSONArray a = new JSONArray();
        for (String s : values) a.put(s);
        prefs(c).edit().putString(key, a.toString()).apply();
    }
}
