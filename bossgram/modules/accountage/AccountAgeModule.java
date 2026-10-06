package com.bossgram.modules.accountage;

import com.bossgram.api.BossContext;
import com.bossgram.api.BossModule;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * Native port of the account_age_checker FREE lookup core (no ExteraGram UI, no paid API).
 * Resolves a username, reads DC id + profile photos, estimates account age from the
 * earliest photo date (lower bound: account can't be younger than its oldest photo).
 * UI entry lives in BossSettingsActivity ("Проверить возраст").
 */
public class AccountAgeModule implements BossModule {

    public interface Listener {
        void onResult(Report r);
        void onError(String message);
    }

    public static class Report {
        public long userId;
        public String name = "";
        public String username = "";
        public int dcId;
        public long earliestPhotoDate; // seconds, 0 = unknown
        public int photosTotal;        // -1 = unknown
    }

    private BossContext ctx;

    @Override public String id() { return "accountage"; }

    @Override public void onInit(BossContext ctx) {
        this.ctx = ctx;
    }

    public boolean isEnabled() {
        return ctx != null && ctx.prefs("boss_accountage").getBoolean("enabled", true);
    }

    public void setEnabled(boolean v) {
        if (ctx != null) ctx.prefs("boss_accountage").edit().putBoolean("enabled", v).apply();
    }

    public void checkUsername(String username, Listener listener) {
        if (!isEnabled()) {
            listener.onError("Модуль выключен");
            return;
        }
        String q = username == null ? "" : username.trim().replaceFirst("^@", "");
        if (q.isEmpty()) {
            listener.onError("Пустой username");
            return;
        }
        try {
            int account = UserConfig.selectedAccount;
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = q;
            ConnectionsManager.getInstance(account).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                if (error != null || !(response instanceof TLRPC.TL_contacts_resolvedPeer)) {
                    postError(listener, "Юзер не найден");
                    return;
                }
                TLRPC.TL_contacts_resolvedPeer resolved = (TLRPC.TL_contacts_resolvedPeer) response;
                try {
                    MessagesController.getInstance(account).putUsers(resolved.users, false);
                } catch (Throwable ignore) {}
                TLRPC.User user = peerUser(resolved.peer, resolved.users);
                if (user == null) {
                    postError(listener, "Это не пользователь");
                    return;
                }
                fetchPhotos(account, user, listener);
            });
        } catch (Throwable t) {
            t.printStackTrace();
            listener.onError("Ошибка запроса");
        }
    }

    private TLRPC.User peerUser(TLRPC.Peer peer, ArrayList<TLRPC.User> users) {
        try {
            long uid = (peer instanceof TLRPC.TL_peerUser) ? ((TLRPC.TL_peerUser) peer).user_id : 0;
            if (uid == 0 || users == null) return null;
            for (TLRPC.User u : users) {
                if (u != null && u.id == uid) return u;
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private void fetchPhotos(int account, TLRPC.User user, Listener listener) {
        try {
            TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
            try {
                req.user_id = MessagesController.getInstance(account).getInputUser(user);
            } catch (Throwable t) {
                postError(listener, "Нет доступа к фото");
                return;
            }
            req.offset = 0;
            req.max_id = 0;
            req.limit = 1;
            ConnectionsManager.getInstance(account).sendRequest(req, (TLObject response, TLRPC.TL_error error) -> {
                if (error != null || !(response instanceof TLRPC.photos_Photos)) {
                    postError(listener, "Фото недоступны");
                    return;
                }
                TLRPC.photos_Photos photos = (TLRPC.photos_Photos) response;
                int total = photosTotal(photos);
                if (total <= 1) {
                    finish(account, user, photos, listener);
                    return;
                }
                TLRPC.TL_photos_getUserPhotos req2 = new TLRPC.TL_photos_getUserPhotos();
                try {
                    req2.user_id = MessagesController.getInstance(account).getInputUser(user);
                } catch (Throwable t) {
                    finish(account, user, photos, listener);
                    return;
                }
                req2.offset = total - 1;
                req2.max_id = 0;
                req2.limit = 1;
                ConnectionsManager.getInstance(account).sendRequest(req2, (TLObject r2, TLRPC.TL_error e2) -> {
                    if (e2 == null && r2 instanceof TLRPC.photos_Photos) {
                        finish(account, user, (TLRPC.photos_Photos) r2, listener);
                    } else {
                        finish(account, user, photos, listener);
                    }
                });
            });
        } catch (Throwable t) {
            t.printStackTrace();
            postError(listener, "Ошибка запроса");
        }
    }

    private int photosTotal(TLRPC.photos_Photos photos) {
        try {
            if (photos instanceof TLRPC.TL_photos_photosSlice) {
                return ((TLRPC.TL_photos_photosSlice) photos).count;
            }
            if (photos.photos != null) return photos.photos.size();
        } catch (Throwable ignore) {}
        return -1;
    }

    private void finish(int account, TLRPC.User user, TLRPC.photos_Photos photos, Listener listener) {
        try {
            Report r = new Report();
            r.userId = user.id;
            try { r.name = UserObject.getUserName(user); } catch (Throwable ignore) {}
            try { r.username = UserObject.getPublicUsername(user); } catch (Throwable ignore) {}
            try {
                if (user.photo instanceof TLRPC.TL_userProfilePhoto) {
                    r.dcId = ((TLRPC.TL_userProfilePhoto) user.photo).dc_id;
                }
            } catch (Throwable ignore) {}
            r.photosTotal = photosTotal(photos);
            long min = 0;
            try {
                if (photos.photos != null) {
                    for (TLRPC.Photo p : photos.photos) {
                        if (p != null && p.date > 0 && (min == 0 || p.date < min)) min = p.date;
                    }
                }
            } catch (Throwable ignore) {}
            r.earliestPhotoDate = min;
            final Report out = r;
            AndroidUtilities.runOnUIThread(() -> {
                try { listener.onResult(out); } catch (Throwable t) { t.printStackTrace(); }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            postError(listener, "Ошибка обработки");
        }
    }

    private void postError(Listener listener, String message) {
        AndroidUtilities.runOnUIThread(() -> {
            try { listener.onError(message); } catch (Throwable t) { t.printStackTrace(); }
        });
    }
}
