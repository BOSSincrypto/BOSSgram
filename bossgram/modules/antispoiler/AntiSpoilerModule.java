package com.bossgram.modules.antispoiler;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;

import java.util.List;

/**
 * Native port of the anti_spoiler plugin (without its embedded DEX blob).
 * Auto-reveals text and media spoilers. Called at the END of
 * ChatMessageCell.setMessageContent, mirroring the tap-to-reveal sequence:
 * set flags + clear SpoilerEffects from all layout blocks + invalidate.
 * Flags alone are not enough — effects are baked at layout time.
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

    /** Called from BossHooks at the end of ChatMessageCell.setMessageContent. Never throws. */
    public void onMessageShown(Object cell) {
        if (!isEnabled() || !(cell instanceof ChatMessageCell)) return;
        try {
            ChatMessageCell c = (ChatMessageCell) cell;
            MessageObject o = c.getMessageObject();
            if (o == null) return;
            o.isSpoilersRevealed = true;
            o.isMediaSpoilersRevealed = true;
            if (o.textLayoutBlocks != null) {
                for (MessageObject.TextLayoutBlock block : o.textLayoutBlocks) {
                    clear(block);
                }
            }
            clearLayouts(c.captionLayout);
            clearLayouts(c.explanationLayout);
            c.invalidate();
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void clearLayouts(MessageObject.TextLayoutBlocks layouts) {
        try {
            if (layouts != null && layouts.textLayoutBlocks != null) {
                for (MessageObject.TextLayoutBlock block : layouts.textLayoutBlocks) {
                    clear(block);
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void clear(MessageObject.TextLayoutBlock block) {
        try {
            if (block != null) {
                List spoilers = block.spoilers;
                if (spoilers != null) spoilers.clear();
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
