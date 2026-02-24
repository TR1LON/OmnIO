package com.trilon.omnio;

/**
 * Common initialization logic shared between all loader platforms.
 * Each platform entry point (NeoForge, Fabric) calls {@link #init()} during mod loading.
 */
public final class OmnIOCommon {

    private OmnIOCommon() {
    }

    /**
     * Called by each platform's mod entry point to initialize shared content.
     */
    public static void init() {
        Constants.LOG.info("Initializing {} v{}", Constants.MOD_NAME, "0.1.0");
    }
}
