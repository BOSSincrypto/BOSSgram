package com.bossgram.modules.chatsummary;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Native port of chat_summary.py core (no host SDK, no requests lib).
 * Fetches last N messages via TL_messages_getHistory, builds a plain-text
 * transcript (sender: text, service/empty skipped, 48k chars cap),
 * then calls the configured AI provider (OpenAI / Anthropic / Gemini /
 * Ollama / OpenAI-compatible custom endpoint) via HttpURLConnection.
 * API key lives in local prefs only, never logged.
 */
public class ChatSummaryModule implements BossModule {

    public static final int PROVIDER_OPENAI = 0;
    public static final int PROVIDER_ANTHROPIC = 1;
    public static final int PROVIDER_GEMINI = 2;
    public static final int PROVIDER_OLLAMA = 3;
    public static final int PROVIDER_CUSTOM = 4;

    public static final int STYLE_BRIEF = 0;
    public static final int STYLE_DETAILED = 1;
    public static final int STYLE_BULLETS = 2;

    public static final int LANG_AUTO = 0;
    public static final int LANG_RU = 1;
    public static final int LANG_EN = 2;

    private static final int MAX_CONTENT_CHARS = 48000;

    public interface Listener {
        void onResult(String summary, int used);
        void onError(String message);
    }

    private BossContext ctx;

    @Override public String id() { return "chatsummary"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_chatsummary").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putBoolean("enabled", v).apply();
    }

    public int getProvider() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getInt("provider", PROVIDER_OPENAI) : PROVIDER_OPENAI;
    }

    public void setProvider(int v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putInt("provider", v).apply();
    }

    public String getKey() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getString("key", "") : "";
    }

    public void setKey(String v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putString("key", v != null ? v : "").apply();
    }

    public String getModel() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getString("model", "") : "";
    }

    public void setModel(String v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putString("model", v != null ? v : "").apply();
    }

    public int getStyle() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getInt("style", STYLE_BRIEF) : STYLE_BRIEF;
    }

    public void setStyle(int v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putInt("style", v).apply();
    }

    public int getLang() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getInt("lang", LANG_AUTO) : LANG_AUTO;
    }

    public void setLang(int v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putInt("lang", v).apply();
    }

    public String getCustomUrl() {
        return ctx != null ? ctx.prefs("boss_chatsummary").getString("custom_url", "") : "";
    }

    public void setCustomUrl(String v) {
        if (ctx != null) ctx.prefs("boss_chatsummary").edit().putString("custom_url", v != null ? v : "").apply();
    }

    public void summarize(long dialogId, int count, Listener listener) {
        if (!isEnabled()) {
            postError(listener, "Модуль выключен");
            return;
        }
        final int limit = Math.max(10, Math.min(500, count));
        try {
            int account = UserConfig.selectedAccount;
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
            req.offset_id = 0;
            req.offset_date = 0;
            req.add_offset = 0;
            req.limit = limit;
            req.max_id = 0;
            req.min_id = 0;
            req.hash = 0;
            ConnectionsManager.getInstance(account).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                    postError(listener, "История недоступна");
                    return;
                }
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                String transcript = buildTranscript(res);
                if (transcript == null || transcript.trim().isEmpty()) {
                    postError(listener, "Текстовых сообщений не найдено");
                    return;
                }
                int used = countLines(transcript);
                runAi(transcript, used, listener);
            });
        } catch (Throwable t) {
            t.printStackTrace();
            postError(listener, "Ошибка запроса");
        }
    }

    private String buildTranscript(TLRPC.messages_Messages res) {
        try {
            ArrayList<TLRPC.Message> msgs = res.messages;
            if (msgs == null || msgs.isEmpty()) return "";
            java.util.HashMap<Long, TLRPC.User> users = new java.util.HashMap<>();
            if (res.users != null) {
                for (TLRPC.User u : res.users) {
                    if (u != null) users.put(u.id, u);
                }
            }
            java.util.HashMap<Long, TLRPC.Chat> chats = new java.util.HashMap<>();
            if (res.chats != null) {
                for (TLRPC.Chat c : res.chats) {
                    if (c != null) chats.put(c.id, c);
                }
            }
            StringBuilder sb = new StringBuilder();
            // API returns newest-first; walk oldest-first for readable transcript.
            for (int i = msgs.size() - 1; i >= 0; i--) {
                TLRPC.Message m = msgs.get(i);
                if (m == null || m instanceof TLRPC.TL_messageEmpty || m instanceof TLRPC.TL_messageService) continue;
                String text = m.message;
                if (text == null || text.trim().isEmpty()) continue;
                String sender = senderName(m, users, chats);
                sb.append(sender).append(": ").append(singleLine(text)).append('\n');
                if (sb.length() > MAX_CONTENT_CHARS + 1024) break;
            }
            String full = sb.toString();
            if (full.length() > MAX_CONTENT_CHARS) {
                full = "...[обрезано]\n" + full.substring(full.length() - MAX_CONTENT_CHARS);
            }
            return full;
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    private String senderName(TLRPC.Message m, java.util.HashMap<Long, TLRPC.User> users,
                              java.util.HashMap<Long, TLRPC.Chat> chats) {
        try {
            if (m.from_id instanceof TLRPC.TL_peerUser) {
                long uid = ((TLRPC.TL_peerUser) m.from_id).user_id;
                TLRPC.User u = users.get(uid);
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
            if (m.peer_id instanceof TLRPC.TL_peerChannel) {
                long cid = ((TLRPC.TL_peerChannel) m.peer_id).channel_id;
                TLRPC.Chat c = chats.get(cid);
                if (c != null && c.title != null) return c.title;
            }
        } catch (Throwable ignore) {}
        return "Unknown";
    }

    private String singleLine(String s) {
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private int countLines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') n++;
        }
        return Math.max(1, n);
    }

    private String systemPrompt(int style, int lang) {
        String stylePart;
        if (style == STYLE_DETAILED) {
            stylePart = "Write a detailed summary covering all important topics discussed.";
        } else if (style == STYLE_BULLETS) {
            stylePart = "Write a bullet-point list of the main topics and decisions.";
        } else {
            stylePart = "Write a concise 2-3 sentence summary of the key points.";
        }
        String langPart;
        if (lang == LANG_RU) {
            langPart = "Respond in Russian.";
        } else if (lang == LANG_EN) {
            langPart = "Respond in English.";
        } else {
            langPart = "Respond in the same language as the conversation.";
        }
        return "You are a helpful assistant that summarizes Telegram chat conversations. "
                + stylePart + " " + langPart + " Focus only on content. Do not include meta-commentary.";
    }

    private void runAi(String transcript, int used, Listener listener) {
        final int provider = getProvider();
        final String key = getKey();
        final String model = getModel();
        final int style = getStyle();
        final int lang = getLang();
        final String customUrl = getCustomUrl();
        final String system = systemPrompt(style, lang);
        new Thread(() -> {
            try {
                String out = callProvider(provider, key, model, system, transcript, customUrl);
                if (out == null || out.trim().isEmpty()) {
                    postError(listener, "Пустой ответ AI");
                    return;
                }
                final String res = out.trim();
                AndroidUtilities.runOnUIThread(() -> {
                    try { listener.onResult(res, used); } catch (Throwable t) { t.printStackTrace(); }
                });
            } catch (Exception e) {
                postError(listener, "Ошибка AI: " + shortMsg(e));
            }
        }).start();
    }

    private String callProvider(int provider, String key, String model,
                                String system, String content, String customUrl) throws Exception {
        if (provider == PROVIDER_ANTHROPIC) {
            String m = model == null || model.isEmpty() ? "claude-sonnet-4-5" : model;
            JSONObject body = new JSONObject();
            body.put("model", m);
            body.put("max_tokens", 1024);
            body.put("system", system);
            JSONArray msgs = new JSONArray();
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", content);
            msgs.put(u);
            body.put("messages", msgs);
            java.util.HashMap<String, String> headers = new java.util.HashMap<>();
            headers.put("x-api-key", key != null ? key : "");
            headers.put("anthropic-version", "2023-06-01");
            String resp = postJson("https://api.anthropic.com/v1/messages", body.toString(), headers);
            JSONObject json = new JSONObject(resp);
            return json.getJSONArray("content").getJSONObject(0).getString("text");
        }
        if (provider == PROVIDER_GEMINI) {
            String m = model == null || model.isEmpty() ? "gemini-2.0-flash" : model;
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + m
                    + ":generateContent?key=" + (key != null ? key : "");
            JSONObject part = new JSONObject();
            part.put("text", system + "\n\n" + content);
            JSONObject c = new JSONObject();
            c.put("parts", new JSONArray().put(part));
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(c));
            String resp = postJson(url, body.toString(), null);
            JSONObject json = new JSONObject(resp);
            return json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        }
        if (provider == PROVIDER_OLLAMA) {
            String m = model == null || model.isEmpty() ? "llama3" : model;
            JSONObject body = new JSONObject();
            body.put("model", m);
            body.put("stream", false);
            JSONArray msgs = new JSONArray();
            JSONObject s = new JSONObject();
            s.put("role", "system");
            s.put("content", system);
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", content);
            msgs.put(s);
            msgs.put(u);
            body.put("messages", msgs);
            String resp = postJson("http://localhost:11434/api/chat", body.toString(), null);
            return new JSONObject(resp).getJSONObject("message").getString("content");
        }
        // OpenAI + custom (OpenAI-compatible).
        String m = model == null || model.isEmpty() ? "gpt-4o" : model;
        String url = "https://api.openai.com/v1/chat/completions";
        if (provider == PROVIDER_CUSTOM && customUrl != null && !customUrl.trim().isEmpty()) {
            url = customUrl.trim();
        }
        JSONObject body = new JSONObject();
        body.put("model", m);
        JSONArray msgs = new JSONArray();
        JSONObject s = new JSONObject();
        s.put("role", "system");
        s.put("content", system);
        JSONObject u = new JSONObject();
        u.put("role", "user");
        u.put("content", "Chat conversation to summarize:\n\n" + content);
        msgs.put(s);
        msgs.put(u);
        body.put("messages", msgs);
        java.util.HashMap<String, String> headers = new java.util.HashMap<>();
        headers.put("Authorization", "Bearer " + (key != null ? key : ""));
        String resp = postJson(url, body.toString(), headers);
        return new JSONObject(resp).getJSONArray("choices")
                .getJSONObject(0).getJSONObject("message").getString("content");
    }

    private String postJson(String urlStr, String json, java.util.HashMap<String, String> headers) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            if (headers != null) {
                for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                    conn.setRequestProperty(e.getKey(), e.getValue());
                }
            }
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(in);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code);
            }
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = br.read(buf)) != -1) {
            sb.append(buf, 0, n);
            if (sb.length() > 200000) break;
        }
        return sb.toString();
    }

    private String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return "unknown";
        return m.length() > 120 ? m.substring(0, 120) : m;
    }

    private void postError(Listener listener, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            try { listener.onError(message); } catch (Throwable t) { t.printStackTrace(); }
        });
    }

    // Used by MessageObject-free paths; kept for parity with the .py transcript builder.
    @SuppressWarnings("unused")
    private String textOf(MessageObject obj) {
        try {
            if (obj != null && obj.messageText != null) return obj.messageText.toString();
            if (obj != null && obj.messageOwner != null && obj.messageOwner.message != null) {
                return obj.messageOwner.message;
            }
        } catch (Throwable ignore) {}
        return "";
    }
}
