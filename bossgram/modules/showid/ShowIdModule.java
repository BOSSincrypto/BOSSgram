package com.bossgram.modules.showid;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

/**
 * Show IDs of users and groups.
 * - Toggle (default ON).
 * - Helpers for profile/subtitle rows: userIdText / chatIdText / dialogIdText.
 * - Lookup by @username (user, group, channel) for BossSettingsActivity.
 * No hooks rewrite IDs anywhere; display only.
 */
public class ShowIdModule implements BossModule {

    public interface Listener {
        void onResult(String title, String lines);
        void onError(String message);
    }

    private BossContext ctx;

    @Override public String id() { return "showid"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_showid").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_showid").edit().putBoolean("enabled", v).apply();
    }

    public boolean getCopyOnTap() {
        return ctx == null || ctx.prefs("boss_showid").getBoolean("copy_on_tap", true);
    }

    public void setCopyOnTap(boolean v) {
        if (ctx != null) ctx.prefs("boss_showid").edit().putBoolean("copy_on_tap", v).apply();
    }

    /** "ID: 12345" for a user, "" when disabled/unknown. */
    public String userIdText(TLRPC.User user) {
        if (!isEnabled() || user == null) return "";
        return "ID: " + user.id;
    }

    /** "ID группы: ..." / "ID канала: ..." for a chat, "" when disabled/unknown. */
    public String chatIdText(TLRPC.Chat chat) {
        if (!isEnabled() || chat == null) return "";
        try {
            if (chat instanceof TLRPC.TL_channel) {
                boolean mega = ((TLRPC.TL_channel) chat).megagroup;
                return (mega ? "ID группы: " : "ID канала: ") + chat.id;
            }
        } catch (Throwable ignore) {}
        return "ID группы: " + chat.id;
    }

    /** Generic dialog id line: user / group / channel distinguished by sign. */
    public String dialogIdText(long dialogId, int account) {
        if (!isEnabled() || dialogId == 0) return "";
        try {
            if (dialogId > 0) {
                TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                String name = u != null ? UserObject.getUserName(u) : "";
                return "ID пользователя: " + dialogId + (name.isEmpty() ? "" : " (" + name + ")");
            }
            long cid = -dialogId;
            TLRPC.Chat c = MessagesController.getInstance(account).getChat(cid);
            if (c != null) {
                return chatIdText(c) + (c.title != null ? " (" + c.title + ")" : "");
            }
            return "ID: " + dialogId;
        } catch (Throwable t) {
            return "ID: " + dialogId;
        }
    }

    /** Message-level helper: author id + chat id for the open chat. */
    public String messageIdsText(MessageObject obj, long dialogId) {
        if (!isEnabled() || obj == null) return "";
        try {
            long author = 0;
            if (obj.messageOwner != null && obj.messageOwner.from_id instanceof TLRPC.TL_peerUser) {
                author = ((TLRPC.TL_peerUser) obj.messageOwner.from_id).user_id;
            }
            if (author != 0 && dialogId != 0 && dialogId != author) {
                return "ID: " + author + " · Чат: " + dialogId;
            }
            if (author != 0) return "ID: " + author;
            if (dialogId != 0) return "ID чата: " + dialogId;
        } catch (Throwable ignore) {}
        return "";
    }

    /** Resolve @username and report user/group/channel IDs. */
    public void lookup(String username, Listener listener) {
        if (!isEnabled()) {
            postError(listener, "Модуль выключен");
            return;
        }
        String q = username == null ? "" : username.trim().replaceFirst("^@", "");
        if (q.isEmpty()) {
            postError(listener, "Пустой username");
            return;
        }
        try {
            int account = UserConfig.selectedAccount;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = q;
            ConnectionsManager.getInstance(account).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    postError(listener, "Не найдено");
                    return;
                }
                TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                try {
                    MessagesController.getInstance(account).putUsers(resolved.users, false);
                    MessagesController.getInstance(account).putChats(resolved.chats, false);
                } catch (Throwable ignore) {}
                TLRPC.User user = findUser(resolved);
                if (user != null) {
                    String name = "";
                    try { name = UserObject.getUserName(user); } catch (Throwable ignore) {}
                    String pub = "";
                    try { pub = UserObject.getPublicUsername(user); } catch (Throwable ignore) {}
                    String lines = "ID пользователя: " + user.id
                            + (name.isEmpty() ? "" : "\n" + name)
                            + (pub == null || pub.isEmpty() ? "" : "\n@" + pub);
                    postResult(listener, name.isEmpty() ? ("User" + user.id) : name, lines);
                    return;
                }
                TLRPC.Chat chat = findChat(resolved);
                if (chat != null) {
                    String kind = "группа";
                    try {
                        if (chat instanceof TLRPC.TL_channel) {
                            kind = ((TLRPC.TL_channel) chat).megagroup ? "супергруппа" : "канал";
                        }
                    } catch (Throwable ignore) {}
                    String pub = "";
                    try { pub = ChatObject.getPublicUsername(chat); } catch (Throwable ignore) {}
                    String t = chat.title != null ? chat.title : kind;
                    String lines = chatIdText(chat)
                            + "\n" + kind
                            + (pub == null || pub.isEmpty() ? "" : "\n@" + pub);
                    postResult(listener, t, lines);
                    return;
                }
                postError(listener, "Ни юзер, ни чат не найдены");
            });
        } catch (Throwable t) {
            t.printStackTrace();
            postError(listener, "Ошибка запроса");
        }
    }

    private TLRPC.User findUser(TLRPC.TL_contacts_resolvedPeer resolved) {
        try {
            long uid = resolved.peer instanceof TLRPC.TL_peerUser
                    ? ((TLRPC.TL_peerUser) resolved.peer).user_id : 0;
            if (uid == 0 || resolved.users == null) return null;
            for (TLRPC.User u : resolved.users) {
                if (u != null && u.id == uid) return u;
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private TLRPC.Chat findChat(TLRPC.TL_contacts_resolvedPeer resolved) {
        try {
            long cid = 0;
            if (resolved.peer instanceof TLRPC.TL_peerChannel) {
                cid = ((TLRPC.TL_peerChannel) resolved.peer).channel_id;
            } else if (resolved.peer instanceof TLRPC.TL_peerChat) {
                cid = ((TLRPC.TL_peerChat) resolved.peer).chat_id;
            }
            if (cid == 0 || resolved.chats == null) return null;
            for (TLRPC.Chat c : resolved.chats) {
                if (c != null && c.id == cid) return c;
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private void postResult(Listener listener, String title, String lines) {
        AndroidUtilities.runOnUIThread(() -> {
            try { listener.onResult(title, lines); } catch (Throwable t) { t.printStackTrace(); }
        });
    }

    private void postError(Listener listener, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            try { listener.onError(message); } catch (Throwable t) { t.printStackTrace(); }
        });
    }
}
