package com.bossgram.modules.addtofolder;

import android.view.View;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.DialogsActivity;

/**
 * Native port of the add_to_folder plugin (without its embedded DEX blob).
 * Shows the "Add to folder" action-mode button when selecting chats inside a folder,
 * not only on the main screen. Called at the end of DialogsActivity.updateCounters.
 *
 * Uses reflection for 3 private DialogsActivity members (addToFolderItem/filterTabsView/folderId).
 * If upstream renames them, this module silently no-ops — build never breaks, behavior just stops.
 */
public class AddToFolderModule implements BossModule {

    private BossContext ctx;

    @Override public String id() { return "addtofolder"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_addtofolder").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_addtofolder").edit().putBoolean("enabled", v).apply();
    }

    /** Called from BossHooks at the end of DialogsActivity.updateCounters. Never throws. */
    public void onCountersUpdated(Object fragment, Object addToFolderItem, Object filterTabsView, int folderId) {
        if (!isEnabled() || !(fragment instanceof DialogsActivity)) return;
        try {
            if (!(addToFolderItem instanceof ActionBarMenuSubItem)) return;
            boolean inFolderTab = false;
            if (filterTabsView instanceof View) {
                try {
                    Object def = filterTabsView.getClass().getMethod("currentTabIsDefault").invoke(filterTabsView);
                    inFolderTab = ((View) filterTabsView).getVisibility() == View.VISIBLE
                            && !Boolean.TRUE.equals(def);
                } catch (Throwable ignore) {}
            }
            if (inFolderTab || folderId != 0) {
                ((ActionBarMenuSubItem) addToFolderItem).setVisibility(View.VISIBLE);
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
