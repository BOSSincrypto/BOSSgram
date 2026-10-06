package com.bossgram.modules.accountlimit;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

/**
 * Native port of the accounts_limit_16 plugin.
 * Pretends premium is present ONLY for account-limit UI call sites (stack check),
 * so the client offers up to 16 accounts. Everywhere else premium logic is untouched.
 */
public class AccountLimitModule implements BossModule {

    private static final String[] LIMIT_CALLERS = {
        "org.telegram.ui.MainTabsActivity",
        "org.telegram.ui.UserInfoActivity",
        "org.telegram.ui.LogoutActivity",
    };

    private BossContext ctx;

    @Override public String id() { return "accountlimit"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_accountlimit").getBoolean("enabled", false);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_accountlimit").edit().putBoolean("enabled", v).apply();
    }

    /** Called at the start of UserConfig.hasPremiumOnAccounts. Never throws. */
    public boolean unlockForLimitUi() {
        if (!isEnabled()) return false;
        try {
            for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
                String cn = el.getClassName();
                for (String caller : LIMIT_CALLERS) {
                    if (cn.equals(caller) || cn.startsWith(caller + "$")) return true;
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
        return false;
    }
}
