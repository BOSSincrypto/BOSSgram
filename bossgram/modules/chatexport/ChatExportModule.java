package com.bossgram.modules.chatexport;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Native port of chatexport.py WITHOUT its DEX core (динамическая загрузка
 * DEX отклонена политикой, см. docs/PLUGINS.md). v1 читает историю с сервера
 * TL_messages_getHistory (paginated, API mode) and writes HTML / JSON / TXT
 * locally. Media files themselves are not re-downloaded in v1 — messages
 * keep "[media]" markers and file names when present.
 */
public class ChatExportModule implements BossModule {

    public static final int FORMAT_HTML = 0;
    public static final int FORMAT_JSON = 1;
    public static final int FORMAT_TXT = 2;

    private static final int PAGE = 100;

    public interface Listener {
        void onDone(File file, int count);
        void onError(String message);
    }

    public static class Item {
        public int id;
        public long date;
        public String sender = "";
        public String text = "";
        public boolean out;
        public String media = "";
    }

    private BossContext ctx;

    @Override public String id() { return "chatexport"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_chatexport").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_chatexport").edit().putBoolean("enabled", v).apply();
    }

    public int getFormat() {
        return ctx != null ? ctx.prefs("boss_chatexport").getInt("format", FORMAT_HTML) : FORMAT_HTML;
    }

    public void setFormat(int v) {
        if (ctx != null) ctx.prefs("boss_chatexport").edit().putInt("format", v).apply();
    }

    public boolean getTimestamps() {
        return ctx == null || ctx.prefs("boss_chatexport").getBoolean("timestamps", true);
    }

    public void setTimestamps(boolean v) {
        if (ctx != null) ctx.prefs("boss_chatexport").edit().putBoolean("timestamps", v).apply();
    }

    public int getMaxMessages() {
        return ctx != null ? ctx.prefs("boss_chatexport").getInt("max_messages", 0) : 0;
    }

    public void setMaxMessages(int v) {
        if (ctx != null) ctx.prefs("boss_chatexport").edit().putInt("max_messages", Math.max(0, v)).apply();
    }

    public void exportChat(long dialogId, Listener listener) {
        if (!isEnabled()) {
            postError(listener, "Модуль выключен");
            return;
        }
        final int max = getMaxMessages();
        final int cap = max <= 0 ? 5000 : Math.min(20000, max);
        final int account = UserConfig.selectedAccount;
        new Thread(() -> fetchPages(account, dialogId, cap, listener)).start();
    }

    private void fetchPages(int account, long dialogId, int cap, Listener listener) {
        try {
            ArrayList<Item> out = new ArrayList<>();
            java.util.HashMap<Long, TLRPC.User> users = new java.util.HashMap<>();
            java.util.HashMap<Long, TLRPC.Chat> chats = new java.util.HashMap<>();
            String title = "chat_" + dialogId;
            int offsetId = 0;
            while (out.size() < cap) {
                TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                try {
                    req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
                } catch (Throwable t) {
                    postError(listener, "Нет доступа к чату");
                    return;
                }
                req.offset_id = offsetId;
                req.offset_date = 0;
                req.add_offset = 0;
                req.limit = Math.min(PAGE, cap - out.size());
                req.max_id = 0;
                req.min_id = 0;
                req.hash = 0;
                final Object lock = new Object();
                final TLRPC.messages_Messages[] box = new TLRPC.messages_Messages[1];
                final TLRPC.TL_error[] errBox = new TLRPC.TL_error[1];
                final boolean[] done = new boolean[1];
                ConnectionsManager.getInstance(account).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                    synchronized (lock) {
                        if (response instanceof TLRPC.messages_Messages) {
                            box[0] = (TLRPC.messages_Messages) response;
                        } else {
                            errBox[0] = error;
                        }
                        done[0] = true;
                        lock.notifyAll();
                    }
                });
                synchronized (lock) {
                    long deadline = System.currentTimeMillis() + 30000;
                    while (!done[0] && System.currentTimeMillis() < deadline) {
                        try { lock.wait(1000); } catch (InterruptedException ignore) { break; }
                    }
                }
                if (box[0] == null) {
                    if (!out.isEmpty()) break;
                    postError(listener, "История недоступна");
                    return;
                }
                TLRPC.messages_Messages res = box[0];
                if (res.users != null) {
                    for (TLRPC.User u : res.users) {
                        if (u != null) users.put(u.id, u);
                    }
                }
                if (res.chats != null) {
                    for (TLRPC.Chat c : res.chats) {
                        if (c != null) {
                            chats.put(c.id, c);
                            if (c.title != null && !c.title.isEmpty() && title.startsWith("chat_")) {
                                title = c.title;
                            }
                        }
                    }
                }
                if (res.messages == null || res.messages.isEmpty()) break;
                for (TLRPC.Message m : res.messages) {
                    if (m == null) continue;
                    if (m instanceof TLRPC.TL_messageEmpty) continue;
                    Item it = new Item();
                    it.id = m.id;
                    it.date = m.date;
                    it.out = m.out;
                    it.text = m.message != null ? m.message : "";
                    it.sender = senderName(m, users, chats, account);
                    if (m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty)) {
                        it.media = mediaLabel(m.media);
                    }
                    out.add(it);
                    offsetId = Math.min(offsetId == 0 ? m.id : offsetId, m.id);
                    if (out.size() >= cap) break;
                }
                if (res.messages.size() < PAGE) break;
                if (res instanceof TLRPC.TL_messages_messagesSlice) {
                    int total = ((TLRPC.TL_messages_messagesSlice) res).count;
                    if (out.size() >= total) break;
                }
            }
            if (out.isEmpty()) {
                postError(listener, "Сообщений не найдено");
                return;
            }
            // Oldest-first for file output.
            ArrayList<Item> ordered = new ArrayList<>(out.size());
            for (int i = out.size() - 1; i >= 0; i--) ordered.add(out.get(i));
            File f = writeFile(title, dialogId, ordered);
            if (f == null) {
                postError(listener, "Не удалось записать файл");
                return;
            }
            final File outFile = f;
            final int count = ordered.size();
            AndroidUtilities.runOnUIThread(() -> {
                try { listener.onDone(outFile, count); } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            postError(listener, "Ошибка экспорта");
        }
    }

    private String senderName(TLRPC.Message m, java.util.HashMap<Long, TLRPC.User> users,
                              java.util.HashMap<Long, TLRPC.Chat> chats, int account) {
        try {
            if (m.from_id instanceof TLRPC.TL_peerUser) {
                long uid = ((TLRPC.TL_peerUser) m.from_id).user_id;
                TLRPC.User u = users.get(uid);
                if (u == null) u = MessagesController.getInstance(account).getUser(uid);
                if (u != null) {
                    String n = UserObject.getUserName(u);
                    return n != null && !n.isEmpty() ? n : "User" + uid;
                }
                return "User" + uid;
            }
            if (m.from_id instanceof TLRPC.TL_peerChannel) {
                long cid = ((TLRPC.TL_peerChannel) m.from_id).channel_id;
                TLRPC.Chat c = chats.get(cid);
                if (c != null && c.title != null) return c.title;
                return "Channel" + cid;
            }
            if (m.from_id instanceof TLRPC.TL_peerChat) {
                long cid = ((TLRPC.TL_peerChat) m.from_id).chat_id;
                TLRPC.Chat c = chats.get(cid);
                if (c != null && c.title != null) return c.title;
                return "Chat" + cid;
            }
            if (m.out) {
                try {
                    TLRPC.User self = UserConfig.getInstance(account).getCurrentUser();
                    if (self != null) {
                        String n = UserObject.getUserName(self);
                        if (n != null && !n.isEmpty()) return n;
                    }
                } catch (Throwable ignore) {}
                return "You";
            }
        } catch (Throwable ignore) {}
        return "Unknown";
    }

    private String mediaLabel(TLRPC.MessageMedia media) {
        try {
            String cls = media.getClass().getSimpleName();
            if (cls.contains("Photo")) return "[photo]";
            if (cls.contains("Document")) return "[document]";
            if (cls.contains("Geo")) return "[location]";
            if (cls.contains("Contact")) return "[contact]";
            if (cls.contains("Poll")) return "[poll]";
            if (cls.contains("Sticker")) return "[sticker]";
            return "[media]";
        } catch (Throwable ignore) {
            return "[media]";
        }
    }

    private File writeFile(String title, long dialogId, ArrayList<Item> items) {
        try {
            File dir = ctx != null ? new File(ctx.appContext().getExternalCacheDir(), "BossExport") : null;
            if (dir == null) return null;
            if (!dir.exists()) dir.mkdirs();
            String safe = title.replaceAll("[^a-zA-Z0-9а-яА-Я._-]+", "_");
            if (safe.length() > 40) safe = safe.substring(0, 40);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            int format = getFormat();
            String ext = format == FORMAT_JSON ? "json" : format == FORMAT_TXT ? "txt" : "html";
            File f = new File(dir, "export_" + safe + "_" + stamp + "." + ext);
            boolean ts = getTimestamps();
            if (format == FORMAT_JSON) {
                writeJson(f, title, dialogId, items, ts);
            } else if (format == FORMAT_TXT) {
                writeTxt(f, title, dialogId, items, ts);
            } else {
                writeHtml(f, title, dialogId, items, ts);
            }
            return f;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private void writeJson(File f, String title, long dialogId, ArrayList<Item> items, boolean ts) throws Exception {
        JSONObject root = new JSONObject();
        root.put("title", title);
        root.put("dialog_id", dialogId);
        root.put("count", items.size());
        JSONArray arr = new JSONArray();
        for (Item it : items) {
            JSONObject o = new JSONObject();
            o.put("id", it.id);
            if (ts) o.put("date", it.date);
            o.put("from", it.sender);
            o.put("text", it.text);
            if (!it.media.isEmpty()) o.put("media", it.media);
            arr.put(o);
        }
        root.put("messages", arr);
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(root.toString(2));
        }
    }

    private void writeTxt(File f, String title, long dialogId, ArrayList<Item> items, boolean ts) throws Exception {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(title + " (id " + dialogId + ")\n");
            w.write("Messages: " + items.size() + "\n\n");
            for (Item it : items) {
                String when = ts ? " [" + fmt.format(new Date(it.date * 1000)) + "]" : "";
                w.write(it.sender + when + ": " + it.text);
                if (!it.media.isEmpty()) w.write(" " + it.media);
                w.write("\n");
            }
        }
    }

    private void writeHtml(File f, String title, long dialogId, ArrayList<Item> items, boolean ts) throws Exception {
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US);
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write("<!DOCTYPE html><html><head><meta charset=\"utf-8\">");
            w.write("<title>" + esc(title) + "</title>");
            w.write("<style>body{font-family:sans-serif;background:#0e1621;color:#fff;max-width:760px;margin:0 auto;padding:16px}"
                    + ".msg{background:#17212b;border-radius:10px;padding:8px 12px;margin:8px 0}"
                    + ".from{color:#5da8e8;font-weight:bold;font-size:13px}"
                    + ".time{color:#6d7f8f;font-size:11px;margin-left:8px}"
                    + ".text{margin-top:4px;white-space:pre-wrap}</style></head><body>");
            w.write("<h2>" + esc(title) + " (id " + dialogId + ")</h2>");
            w.write("<p>Messages: " + items.size() + "</p>");
            for (Item it : items) {
                w.write("<div class=\"msg\"><span class=\"from\">" + esc(it.sender) + "</span>");
                if (ts) w.write("<span class=\"time\">" + esc(fmt.format(new Date(it.date * 1000))) + "</span>");
                w.write("<div class=\"text\">" + esc(it.text));
                if (!it.media.isEmpty()) w.write(" " + esc(it.media));
                w.write("</div></div>");
            }
            w.write("</body></html>");
        }
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void postError(Listener listener, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            try { listener.onError(message); } catch (Throwable t) { t.printStackTrace(); }
        });
    }
}
