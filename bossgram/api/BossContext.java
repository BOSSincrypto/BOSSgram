package com.bossgram.api;

import android.content.Context;
import android.content.SharedPreferences;

/** Minimal surface modules may use. Expand carefully — each method is a contract you support across Telegram updates. */
public interface BossContext {
    Context appContext();
    SharedPreferences prefs(String name);
    void openSavedMessages(long dialogId);
}
