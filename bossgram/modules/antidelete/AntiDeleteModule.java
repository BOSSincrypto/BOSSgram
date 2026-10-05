package com.bossgram.modules.antidelete;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;
import java.util.List;

/** Saves deleted messages per ChatFilter, with cleanup. Sidecar storage — no MessagesStorage edits. */
public class AntiDeleteModule implements BossModule {

    private BossContext ctx;
    private ChatFilter filter;
    private AntiDeleteStore store;

    @Override public String id() { return "antidelete"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
        this.filter = new ChatFilter(ctx.prefs("boss_antidelete"));
        this.store = new AntiDeleteStore(ctx.appContext());
    }

    public void handleDeleted(long dialogId, List<Integer> ids, List<String> textsOrNull) {
        if (filter == null || store == null) return;
        if (!filter.shouldSave(dialogId)) return;
        if (ids == null) return;
        for (int i = 0; i < ids.size(); i++) {
            String text = (textsOrNull != null && i < textsOrNull.size()) ? textsOrNull.get(i) : "";
            store.save(dialogId, ids.get(i), text, System.currentTimeMillis());
        }
        store.trimIfNeeded(filter.maxPerChat(), filter.maxAgeDays());
    }

    public ChatFilter filter() { return filter; }
    public AntiDeleteStore store() { return store; }
}
