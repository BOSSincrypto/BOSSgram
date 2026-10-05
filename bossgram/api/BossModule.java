package com.bossgram.api;

/** Every BOSSgram feature implements this. Upstream knows nothing about modules. */
public interface BossModule {
    String id();
    void onInit(BossContext ctx);
    default void onDestroy() {}
}
