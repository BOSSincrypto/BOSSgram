package com.bossgram.core;

import com.bossgram.api.BossModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleRegistry {
    private static final List<BossModule> MODULES = new ArrayList<>();

    static {
        // Register features here. One line per module, nothing in upstream changes.
        MODULES.add(new com.bossgram.modules.antidelete.AntiDeleteModule());
        MODULES.add(new com.bossgram.modules.themes.ThemesModule());
        MODULES.add(new com.bossgram.modules.accounthider.AccountHiderModule());
        MODULES.add(new com.bossgram.modules.addtofolder.AddToFolderModule());
        MODULES.add(new com.bossgram.modules.antispoiler.AntiSpoilerModule());
    }

    private ModuleRegistry() {}

    public static List<BossModule> all() {
        return Collections.unmodifiableList(MODULES);
    }
}
