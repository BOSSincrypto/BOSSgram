package com.bossgram.core;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;
import java.util.List;

/**
 * Single touch point called from 3 one-liners in upstream (see patches/0001-boss-hooks.patch).
 * If a Telegram update renames a method signature, fix ONLY here — modules stay untouched.
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
                // TODO: present com.bossgram fragment via LaunchActivity. Kept out of v1 skeleton.
            }
        };
        for (BossModule m : ModuleRegistry.all()) {
            try { m.onInit(ctx); } catch (Throwable t) { t.printStackTrace(); }
        }
        try { applyCustomTheme(); } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Called at start of MessagesController.deleteMessages. Pass ids + optional snapshots. */
    public static void onMessagesDeleted(long dialogId, List<Integer> messageIds, List<String> textSnapshotsOrNull) {
        if (!inited) return;
        for (BossModule m : ModuleRegistry.all()) {
            if (m instanceof com.bossgram.modules.antidelete.AntiDeleteModule) {
                ((com.bossgram.modules.antidelete.AntiDeleteModule) m)
                        .handleDeleted(dialogId, messageIds, textSnapshotsOrNull);
            }
        }
    }

    /** Called from ChatActivity when a chat opens. For badge / "saved" button. */
    public static void onChatOpened(Object chatActivity) {
        if (!inited) return;
        // v1: no-op, keeps hook stable. AntiDelete UI reads store lazily.
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
}
