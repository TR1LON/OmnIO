package com.trilon.omnio.platform;

import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;

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

    /**
     * Open the conduit configuration GUI for a player.
     * Platform-specific because NeoForge and Fabric use different APIs to send
     * extra data (BlockPos + initial face) with the menu-open packet.
     *
     * @param player the server player to open the screen for
     * @param be     the conduit block entity to configure
     * @param clickedFace the face that was clicked (pre-selects that face in the GUI)
     */
    void openConduitScreen(ServerPlayer player, OmniConduitBlockEntity be, Direction clickedFace);
}
