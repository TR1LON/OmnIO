package com.trilon.omnio.platform;

import java.util.ServiceLoader;

/**
 * Platform abstraction layer using Java's {@link ServiceLoader} SPI.
 * Each loader platform (NeoForge, Fabric) provides an implementation
 * registered via {@code META-INF/services/}.
 */
public interface IPlatformHelper {

    /**
     * The singleton instance, loaded via ServiceLoader at class-init time.
     */
    IPlatformHelper INSTANCE = ServiceLoader.load(IPlatformHelper.class)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                    "No IPlatformHelper implementation found. OmnIO requires a loader-specific platform module."));

    /**
     * @return the display name of the current mod loader (e.g. "NeoForge", "Fabric")
     */
    String getPlatformName();

    /**
     * @param modId the mod ID to check
     * @return true if the given mod is loaded on the current platform
     */
    boolean isModLoaded(String modId);

    /**
     * @return true if the current environment is a development (non-production) environment
     */
    boolean isDevelopmentEnvironment();
}
