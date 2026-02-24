package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom registry keys for OmnIO.
 * These define the namespaced registries used for conduit types and related content.
 */
public final class OmnIORegistries {

    /**
     * Registry key for conduit types (energy, fluid, item, redstone, etc.).
     * Third-party mods can register additional conduit types into this registry.
     */
    public static final ResourceKey<Registry<Object>> CONDUIT_TYPES =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "conduit_type"));

    private OmnIORegistries() {
    }
}
