package com.bossgram.modules.antidelete;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * v1 sidecar store: one JSON file per chat, no Telegram DB changes.
 * Survives Telegram updates. v2 can move to Room without touching hooks.
 */
public class AntiDeleteStore {

    private final File dir;

    public AntiDeleteStore(Context app) {
        dir = new File(app.getFilesDir(), "boss_antidelete");
        if (!dir.exists()) dir.mkdirs();
    }

    public synchronized void save(long dialogId, int msgId, String text, long ts) {
        try {
            JSONArray arr = loadArr(dialogId);
            JSONObject o = new JSONObject();
            o.put("id", msgId);
            o.put("text", text == null ? "" : text);
            o.put("ts", ts);
            arr.put(o);
            writeArr(dialogId, arr);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized List<String> listTexts(long dialogId, int limit) {
        List<String> out = new ArrayList<>();
        for (Entry e : listEntries(dialogId, limit)) out.add(e.text);
        return out;
    }

    public static class Entry {
        public final int id;
        public final String text;
        public final long ts;
        public Entry(int id, String text, long ts) { this.id = id; this.text = text; this.ts = ts; }
    }

    public synchronized List<Entry> listEntries(long dialogId, int limit) {
        List<Entry> out = new ArrayList<>();
        JSONArray arr = loadArr(dialogId);
        for (int i = Math.max(0, arr.length() - limit); i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            out.add(new Entry(o.optInt("id"), o.optString("text", ""), o.optLong("ts", 0)));
        }
        return out;
    }

    public synchronized List<Long> listDialogs() {
        List<Long> out = new ArrayList<>();
        File[] fs = dir.listFiles();
        if (fs == null) return out;
        for (File f : fs) {
            String n = f.getName();
            if (!n.endsWith(".json")) continue;
            try { out.add(Long.parseLong(n.substring(0, n.length() - 5))); }
            catch (NumberFormatException ignore) {}
        }
        Collections.sort(out);
        return out;
    }

    public synchronized int countFor(long dialogId) {
        return loadArr(dialogId).length();
    }

    public synchronized void clearChat(long dialogId) {
        File f = file(dialogId);
        if (f.exists()) f.delete();
    }

    public synchronized void clearAll() {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) if (f.getName().endsWith(".json")) f.delete();
    }

    /** Trim by count and age. Called after each save, cheap for v1 sizes. */
    public synchronized void trimIfNeeded(int maxPerChat, int maxAgeDays) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        long cutoff = System.currentTimeMillis() - (long) maxAgeDays * 24 * 3600 * 1000;
        for (File f : fs) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                String s = read(f);
                JSONArray arr = s.isEmpty() ? new JSONArray() : new JSONArray(s);
                JSONArray kept = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null && o.optLong("ts", 0) >= cutoff) kept.put(o);
                }
                while (kept.length() > maxPerChat) kept.remove(0);
                write(f, kept.toString());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private File file(long dialogId) { return new File(dir, dialogId + ".json"); }

    private JSONArray loadArr(long dialogId) {
        try {
            String s = read(file(dialogId));
            if (s.isEmpty()) return new JSONArray();
            return new JSONArray(s);
        } catch (Exception e) { return new JSONArray(); }
    }

    private void writeArr(long dialogId, JSONArray arr) { write(file(dialogId), arr.toString()); }

    private String read(File f) {
        if (!f.exists()) return "";
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            return n <= 0 ? "" : new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }

    private void write(File f, String s) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { e.printStackTrace(); }
    }
}
