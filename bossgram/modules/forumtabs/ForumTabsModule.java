package com.bossgram.modules.forumtabs;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.tgnet.TLRPC;

/**
 * Native port of the always-tabs-forums plugin.
 * Forces tab view in all forums: ChatObject.areTabsEnabled returns chat.forum.
 */
public class ForumTabsModule implements BossModule {

    private BossContext ctx;

    @Override public String id() { return "forumtabs"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_forumtabs").getBoolean("enabled", false);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_forumtabs").edit().putBoolean("enabled", v).apply();
    }

    /** Called at the start of ChatObject.areTabsEnabled. Null = fall through to stock logic. */
    public Boolean forumTabs(TLRPC.Chat chat) {
        if (!isEnabled()) return null;
        try {
            if (chat == null) return false;
            return chat.forum;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }
}
