package com.bossgram.modules.accounthider;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Native port of the account_hider plugin (without its opaque DEX blob).
 * Removes selected accounts from account-picker lists (tabs switcher, dialogs switcher, settings).
 * Hidden accounts keep working (messages/notifications untouched) — they just don't show in pickers.
 */
public class AccountHiderModule implements BossModule {

    private BossContext ctx;

    @Override public String id() { return "accounthider"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_accounthider").getBoolean("enabled", false);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_accounthider").edit().putBoolean("enabled", v).apply();
    }

    public Set<Integer> getHidden() {
        Set<Integer> out = new HashSet<>();
        if (ctx == null) return out;
        for (String s : ctx.prefs("boss_accounthider").getStringSet("hidden", new HashSet<>())) {
            try { out.add(Integer.parseInt(s)); } catch (NumberFormatException ignore) {}
        }
        return out;
    }

    public void setHidden(int account, boolean hide) {
        if (ctx == null) return;
        Set<String> cur = new HashSet<>(ctx.prefs("boss_accounthider").getStringSet("hidden", new HashSet<>()));
        if (hide) cur.add(String.valueOf(account));
        else cur.remove(String.valueOf(account));
        ctx.prefs("boss_accounthider").edit().putStringSet("hidden", cur).apply();
    }

    /** Called from BossHooks at every account-picker list build. Mutates in place, never throws. */
    public void filterAccounts(List<Integer> accounts) {
        if (!isEnabled() || accounts == null || accounts.isEmpty()) return;
        Set<Integer> hidden = getHidden();
        if (hidden.isEmpty()) return;
        try {
            accounts.removeIf(hidden::contains);
        } catch (Throwable t) {
            // removeIf missing on old runtimes: manual loop
            try {
                for (int i = accounts.size() - 1; i >= 0; i--) {
                    if (hidden.contains(accounts.get(i))) accounts.remove(i);
                }
            } catch (Throwable t2) { t2.printStackTrace(); }
        }
    }
}
