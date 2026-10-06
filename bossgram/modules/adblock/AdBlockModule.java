package com.bossgram.modules.adblock;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

/**
 * Native port of the AdBlock plugin (method-hook parts only, no host SDK needed).
 * Suppresses promo checks/dialogs and video ads via BossHooks early-returns.
 * No network, no prefs writes beyond the enable flag.
 */
public class AdBlockModule implements BossModule {

    private BossContext ctx;

    @Override public String id() { return "adblock"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_adblock").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_adblock").edit().putBoolean("enabled", v).apply();
    }
}
