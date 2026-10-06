package com.bossgram.modules.globalsearch;

import androidx.collection.LongSparseArray;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Native port of the advanced_chat_search plugin.
 * Extends @-mention search with Telegram global user search (TL_contacts_search):
 * results are merged into the adapter lists inside showUsersResult (params in scope,
 * no reflection). Debounced 250 ms, per-adapter generations, stale responses dropped.
 */
public class GlobalSearchModule implements BossModule {

    private static final int DEBOUNCE_MS = 250;
    private static final int LIMIT = 20;

    private static class State {
        int generation;
        String query = "";
        final List<TLRPC.User> users = new ArrayList<>();
        int requestId;
    }

    private BossContext ctx;
    private final Map<Object, State> states = new WeakHashMap<>();
    private final Map<Object, Runnable> pending = new WeakHashMap<>();

    @Override public String id() { return "globalsearch"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_globalsearch").getBoolean("enabled", false);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_globalsearch").edit().putBoolean("enabled", v).apply();
    }

    private State stateOf(Object adapter) {
        State s = states.get(adapter);
        if (s == null) {
            s = new State();
            states.put(adapter, s);
        }
        return s;
    }

    /** Hook: end of MentionsAdapter.setSearchingMentions. */
    public void onSearchModeChanged(Object adapter, boolean enabled) {
        if (!isEnabled() || adapter == null) return;
        try {
            if (!enabled) {
                State s = states.remove(adapter);
                if (s != null && s.requestId != 0) {
                    ConnectionsManager.getInstance(UserConfig.selectedAccount)
                            .cancelRequest(s.requestId, true);
                }
                Runnable r = pending.remove(adapter);
                if (r != null) AndroidUtilities.cancelRunOnUIThread(r);
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }

    /** Hook: start of MentionsAdapter.searchUsernameOrHashtag. */
    public void onSearchQuery(Object adapter, CharSequence text, boolean usernameOnly, boolean forSearch) {
        if (!isEnabled() || adapter == null) return;
        try {
            if (!usernameOnly || !forSearch) return;
            String raw = text == null ? "" : text.toString();
            String q = raw.startsWith("@") ? raw.substring(1) : raw;
            q = q.trim();
            final State s = stateOf(adapter);
            if (s.requestId != 0) {
                ConnectionsManager.getInstance(UserConfig.selectedAccount).cancelRequest(s.requestId, true);
                s.requestId = 0;
            }
            s.generation++;
            s.query = q;
            s.users.clear();
            if (q.isEmpty()) return;
            final int gen = s.generation;
            final String query = q;
            Runnable old = pending.remove(adapter);
            if (old != null) AndroidUtilities.cancelRunOnUIThread(old);
            Runnable r = () -> startGlobalSearch(adapter, gen, query);
            pending.put(adapter, r);
            AndroidUtilities.runOnUIThread(r, DEBOUNCE_MS);
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void startGlobalSearch(Object adapter, int generation, String query) {
        try {
            State s = states.get(adapter);
            if (s == null || s.generation != generation || !query.equals(s.query)) return;
            TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
            req.q = query;
            req.limit = LIMIT;
            int account = UserConfig.selectedAccount;
            s.requestId = ConnectionsManager.getInstance(account).sendRequest(req,
                    (TLObject response, TLRPC.TL_error error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            applyResponse(adapter, generation, account, response, error);
                        } catch (Throwable t) { t.printStackTrace(); }
                    }));
        } catch (Throwable t) { t.printStackTrace(); }
    }

    private void applyResponse(Object adapter, int generation, int account,
                               TLObject response, TLRPC.TL_error error) {
        State s = states.get(adapter);
        if (s == null || s.generation != generation) return;
        s.requestId = 0;
        if (error != null || !(response instanceof TLRPC.TL_contacts_found)) return;
        TLRPC.TL_contacts_found found = (TLRPC.TL_contacts_found) response;
        try {
            MessagesController.getInstance(account).putUsers(found.users, false);
        } catch (Throwable t) { t.printStackTrace(); }
        Map<Long, TLRPC.User> byId = new java.util.HashMap<>();
        for (TLRPC.User u : found.users) {
            if (u != null && !u.deleted) byId.put(u.id, u);
        }
        s.users.clear();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (ArrayList<TLRPC.Peer> peers : new ArrayList[]{found.my_results, found.results}) {
            if (peers == null) continue;
            for (TLRPC.Peer p : peers) {
                long uid = peerUserId(p);
                if (uid == 0 || !seen.add(uid)) continue;
                TLRPC.User u = byId.get(uid);
                if (u != null) s.users.add(u);
            }
        }
    }

    private long peerUserId(TLRPC.Peer p) {
        try {
            if (p instanceof TLRPC.TL_peerUser) return ((TLRPC.TL_peerUser) p).user_id;
        } catch (Throwable ignore) {}
        return 0;
    }

    /** Hook: start of MentionsAdapter.showUsersResult — newResult/newMap in scope, mutated in place. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void mergeSearchUsers(Object adapter, ArrayList newResult, LongSparseArray newMap) {
        if (!isEnabled() || adapter == null || newResult == null) return;
        try {
            State s = states.get(adapter);
            if (s == null || s.users.isEmpty()) return;
            java.util.Set<Long> present = new java.util.HashSet<>();
            for (Object o : newResult) {
                if (o instanceof TLRPC.User) present.add(((TLRPC.User) o).id);
            }
            for (TLRPC.User u : s.users) {
                if (present.add(u.id)) {
                    newResult.add(u);
                    if (newMap != null) {
                        try { newMap.put(u.id, u); } catch (Throwable ignore) {}
                    }
                }
            }
        } catch (Throwable t) { t.printStackTrace(); }
    }
}
