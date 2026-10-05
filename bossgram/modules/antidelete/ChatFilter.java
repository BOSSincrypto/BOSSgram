package com.bossgram.modules.antidelete;

import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/**
 * Which chats to save. v1: mode ALL_EXCEPT / ONLY_LIST + id sets.
 * dialogId here is Telegram dialog id (user/chat/channel). UI for picking chats — next step.
 */
public class ChatFilter {

    public enum Mode { ALL_EXCEPT, ONLY_LIST }

    private final SharedPreferences p;

    public ChatFilter(SharedPreferences p) { this.p = p; }

    public boolean shouldSave(long dialogId) {
        if (!p.getBoolean("enabled", true)) return false;
        Mode mode = Mode.valueOf(p.getString("mode", Mode.ALL_EXCEPT.name()));
        Set<String> list = p.getStringSet("list", new HashSet<>());
        boolean inList = list.contains(String.valueOf(dialogId));
        return mode == Mode.ALL_EXCEPT ? !inList : inList;
    }

    public boolean isEnabled() { return p.getBoolean("enabled", true); }

    public void setEnabled(boolean v) { p.edit().putBoolean("enabled", v).apply(); }

    public Mode getMode() {
        try { return Mode.valueOf(p.getString("mode", Mode.ALL_EXCEPT.name())); }
        catch (IllegalArgumentException e) { return Mode.ALL_EXCEPT; }
    }

    public void setMode(Mode m) { p.edit().putString("mode", m.name()).apply(); }

    public java.util.Set<Long> getListIds() {
        java.util.Set<Long> out = new java.util.HashSet<>();
        for (String s : p.getStringSet("list", new HashSet<>())) {
            try { out.add(Long.parseLong(s)); } catch (NumberFormatException ignore) {}
        }
        return out;
    }

    public void setList(Set<Long> ids) {
        Set<String> s = new HashSet<>();
        for (Long id : ids) s.add(String.valueOf(id));
        p.edit().putStringSet("list", s).apply();
    }

    public void clearChat(long dialogId) {
        Set<String> list = new HashSet<>(p.getStringSet("list", new HashSet<>()));
        list.remove(String.valueOf(dialogId));
        p.edit().putStringSet("list", list).apply();
    }

    public int maxPerChat() { return p.getInt("max_per_chat", 500); }
    public int maxAgeDays() { return p.getInt("max_age_days", 30); }

    public void setMaxPerChat(int v) { p.edit().putInt("max_per_chat", v).apply(); }
    public void setMaxAgeDays(int v) { p.edit().putInt("max_age_days", v).apply(); }
}
