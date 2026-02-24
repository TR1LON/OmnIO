package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import net.minecraft.resources.ResourceLocation;

/**
 * Stub: Built-in conduit type registrations.
 * Will be populated when the conduit type registry and implementations are built.
 */
public final class ConduitTypes {

    public static final ResourceLocation ENERGY = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "energy");
    public static final ResourceLocation FLUID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "fluid");
    public static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "item");
    public static final ResourceLocation REDSTONE = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "redstone");

    private ConduitTypes() {
    }

    /**
     * Called during mod init to register built-in conduit types.
     * Implementation will be added in Phase 3 when the registry is fully wired.
     */
    public static void register() {
        Constants.LOG.debug("Registering built-in conduit types");
        // TODO: Register ENERGY, FLUID, ITEM, REDSTONE conduit type instances
    }
}
