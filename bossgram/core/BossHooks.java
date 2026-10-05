package com.bossgram.core;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.SparseArray;

import androidx.collection.LongSparseArray;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;
import com.bossgram.modules.antidelete.AntiDeleteModule;
import com.bossgram.modules.antidelete.BossSettingsActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single touch point called from 5 one-liners in upstream (see patches/0001-boss-hooks.patch).
 * If a Telegram update renames a method signature, fix ONLY here — modules stay untouched.
 *
 * Threading: hooks run on UI thread. Snapshots are reads-only (cheap), writes go to
 * MessagesStorage queue. Never throws into upstream: every entry wrapped in try/catch.
 */
public final class BossHooks {

    private static volatile boolean inited;
    private static BossContext ctx;

    private BossHooks() {}

    public static void init(Application app) {
        if (inited) return;
        inited = true;
        ctx = new BossContext() {
            @Override public Context appContext() { return app; }
            @Override public SharedPreferences prefs(String name) {
                return app.getSharedPreferences(name, Context.MODE_PRIVATE);
            }
            @Override public void openSavedMessages(long dialogId) {
                openSettings();
            }
        };
        for (BossModule m : ModuleRegistry.all()) {
            try { m.onInit(ctx); } catch (Throwable t) { t.printStackTrace(); }
        }
        try { applyCustomTheme(); } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Hook 2/5. Own-side deletes: all MessagesController.deleteMessages overloads funnel into the
     * full 12-arg version, snapshot here BEFORE any marking. */
    public static void onOwnMessagesDeleted(int account, long dialogId, ArrayList<Integer> ids) {
        if (!inited || ids == null || ids.isEmpty()) return;
        try {
            List<String> texts = snapshotTexts(account, dialogId, ids);
            routeToAntiDelete(account, dialogId, ids, texts);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Hook 3/5. Remote deletes (other side / sync) via processUpdateArray, BEFORE storage marks.
     * Runs on UI thread here, in-memory MessageObjects still readable.
     * Key semantics: channel dialogId (-channelId) directly, 0 for the rest (resolve per message). */
    public static void onRemoteMessagesDeleted(int account, LongSparseArray<ArrayList<Integer>> deleted) {
        if (!inited || deleted == null) return;
        try {
            for (int i = 0, n = deleted.size(); i < n; i++) {
                long key = deleted.keyAt(i);
                ArrayList<Integer> ids = deleted.valueAt(i);
                if (ids == null || ids.isEmpty()) continue;
                if (key != 0) {
                    List<String> texts = snapshotTexts(account, key, ids);
                    routeToAntiDelete(account, key, ids, texts);
                } else {
                    // Private chats: update carries no dialogId, resolve each message from memory.
                    Map<Long, ArrayList<Integer>> byDialog = new HashMap<>();
                    Map<Long, ArrayList<String>> textsByDialog = new HashMap<>();
                    MessagesController mc = MessagesController.getInstance(account);
                    for (Integer id : ids) {
                        MessageObject obj = mc.dialogMessagesByIds.get(id);
                        if (obj == null) continue; // never opened / already flushed: can't attribute, skip (v1)
                        long d = obj.getDialogId();
                        ArrayList<Integer> l = byDialog.get(d);
                        if (l == null) { l = new ArrayList<>(); byDialog.put(d, l); }
                        l.add(id);
                        ArrayList<String> tl = textsByDialog.get(d);
                        if (tl == null) { tl = new ArrayList<>(); textsByDialog.put(d, tl); }
                        tl.add(textOf(obj));
                    }
                    for (Map.Entry<Long, ArrayList<Integer>> e : byDialog.entrySet()) {
                        routeToAntiDelete(account, e.getKey(), e.getValue(), textsByDialog.get(e.getKey()));
                    }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Hook 4/5. Per-chat badge / "saved" indicator entry point. v1: no-op, keeps hook stable. */
    public static void onChatOpened(Object chatActivity) {
        if (!inited) return;
        // Reserved: read ((ChatActivity) chatActivity).getDialogId() for per-chat UI.
    }

    /** Hook 5/5. Deep-link entry: bossgram://settings opens BossGram settings. Returns true if consumed. */
    public static boolean onNewIntent(LaunchActivity activity, Intent intent) {
        try {
            if (intent == null || intent.getData() == null) return false;
            Uri data = intent.getData();
            if (!"bossgram".equals(data.getScheme())) return false;
            if ("settings".equals(data.getHost()) || "/settings".equals(data.getPath())) {
                openSettings();
                return true;
            }
            return false;
        } catch (Throwable t) { t.printStackTrace(); return false; }
    }

    public static void openSettings() {
        try {
            BaseFragment last = LaunchActivity.getLastFragment();
            if (last == null) return;
            last.presentFragment(new BossSettingsActivity(), false, true);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    public static void applyCustomTheme() {
        if (!inited || ctx == null) return;
        for (BossModule m : ModuleRegistry.all()) {
            if (m instanceof com.bossgram.modules.themes.ThemesModule) {
                ((com.bossgram.modules.themes.ThemesModule) m).apply(ctx);
            }
        }
    }

    public static List<BossModule> modules() {
        return ModuleRegistry.all();
    }

    // ---- internals ----

    private static void routeToAntiDelete(int account, long dialogId, List<Integer> ids, List<String> texts) {
        if (ctx == null) return;
        for (BossModule m : ModuleRegistry.all()) {
            if (m instanceof AntiDeleteModule) {
                final AntiDeleteModule mod = (AntiDeleteModule) m;
                // Off UI thread: file IO must not jank the chat.
                try {
                    MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                        try { mod.handleDeleted(dialogId, ids, texts); }
                        catch (Throwable t) { t.printStackTrace(); }
                    });
                } catch (Throwable t) {
                    // Storage not ready (early boot): fallback to direct call, store is synchronized.
                    try { mod.handleDeleted(dialogId, ids, texts); }
                    catch (Throwable t2) { t2.printStackTrace(); }
                }
                return;
            }
        }
    }

    private static List<String> snapshotTexts(int account, long dialogId, List<Integer> ids) {
        List<String> out = new ArrayList<>(ids.size());
        try {
            MessagesController mc = MessagesController.getInstance(account);
            ArrayList<MessageObject> top = mc.dialogMessage.get(dialogId);
            for (Integer id : ids) {
                MessageObject obj = mc.dialogMessagesByIds.get(id);
                if (obj == null && top != null) {
                    for (int i = 0, n = top.size(); i < n; i++) {
                        MessageObject o = top.get(i);
                        if (o != null && o.getId() == id) { obj = o; break; }
                    }
                }
                out.add(obj != null ? textOf(obj) : "");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            while (out.size() < ids.size()) out.add("");
        }
        return out;
    }

    private static String textOf(MessageObject obj) {
        try {
            if (!TextUtils.isEmpty(obj.messageText)) return obj.messageText.toString();
            if (obj.messageOwner != null && !TextUtils.isEmpty(obj.messageOwner.message)) {
                return obj.messageOwner.message;
            }
        } catch (Throwable t) { t.printStackTrace(); }
        return "";
    }
}
