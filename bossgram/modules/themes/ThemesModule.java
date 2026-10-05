package com.bossgram.modules.themes;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

/**
 * Stub: no Theme.java edits. apply() reads palette from prefs and calls public Theme.setColor().
 * Real keys to map in next iteration (after Telegram sources beside this repo).
 */
public class ThemesModule implements BossModule {

    @Override public String id() { return "themes"; }

    @Override public void onInit(BossContext ctx) {
        // register settings entry here once LaunchActivity hook exists
    }

    public void apply(BossContext ctx) {
        String palette = ctx.prefs("boss_themes").getString("palette", "default");
        if ("default".equals(palette)) return;
        // TODO: map palette -> Theme.setColor(Theme.key_chat_inBubble, ...) etc.
        // Kept empty on purpose: applying half a theme worse than none.
    }
}
