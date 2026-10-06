package com.bossgram.modules.antidelete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Vendored Python plugins (plugins/*.py) + audit status.
 * Shown in BossGram settings. Truthful statuses only: nothing claims to run what can't run yet.
 * Python host runtime (Chaquopy + SDK adapters) is a separate project, see docs/PLUGINS.md.
 */
public final class PluginCatalog {

    public static class Entry {
        public final String id;
        public final String name;
        public final String version;
        public final String author;
        public final String status;
        public Entry(String id, String name, String version, String author, String status) {
            this.id = id; this.name = name; this.version = version;
            this.author = author; this.status = status;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES.add(new Entry("AdBlock", "AdBlock", "1.1", "@kvucoPlugins",
                "Аудит чист. Порт в натив следующим шагом"));
        ENTRIES.add(new Entry("accounts_limit_16", "16 Accounts", "1.0", "@kvucoplugins",
                "Аудит чист. Ждет хост-рантайм"));
        ENTRIES.add(new Entry("advanced_chat_search", "Advanced Chat Search", "1.0.0", "@anivplugins",
                "Аудит чист. Ждет хост-рантайм"));
        ENTRIES.add(new Entry("always-tabs-forums", "Always tabs forums", "1.0.3", "@shikaatux",
                "Аудит чист. Ждет хост-рантайм"));
        ENTRIES.add(new Entry("account_age_checker", "Account Age", "5.2", "@mihailkotovski",
                "Аудит чист, но нужен ExteraGram SDK + ключ datereg.pro"));
    }

    private PluginCatalog() {}

    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }
}
