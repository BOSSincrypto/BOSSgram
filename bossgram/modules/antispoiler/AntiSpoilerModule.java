package com.bossgram.modules.antispoiler;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.MessageObject;

/**
 * Native port of the anti_spoiler plugin (without its embedded DEX blob).
 * Auto-reveals text and media spoilers by pre-setting MessageObject flags at bind time —
 * the same flags the tap-to-reveal path sets (ChatMessageCell:4588), so rendering just works.
 */
public class AntiSpoilerModule implements BossModule {

    private BossContext ctx;

    @Override public String id() { return "antispoiler"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_antispoiler").getBoolean("enabled", false);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_antispoiler").edit().putBoolean("enabled", v).apply();
    }

    /** Called from BossHooks at the start of ChatMessageCell.setMessageObject. Never throws. */
    public void onMessageBound(Object messageObject) {
        if (!isEnabled() || !(messageObject instanceof MessageObject)) return;
        try {
            MessageObject o = (MessageObject) messageObject;
            o.isSpoilersRevealed = true;
            o.isMediaSpoilersRevealed = true;
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
