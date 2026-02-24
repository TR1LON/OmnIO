package com.trilon.omnio.content.conduit.network;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.conduit.IConduitType;
import com.trilon.omnio.api.tier.ITier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Runtime registry for conduit type instances.
 * Maps conduit item IDs (e.g., "omnio:energy_conduit_basic") to their
 * {@link IConduitType} implementation.
 *
 * <p>This is a simple in-memory lookup populated during mod initialization.
 * It replaces the {@link StubConduitType} that was used before real
 * conduit types were implemented.</p>
 *
 * <p>Third-party mods can register additional conduit types by calling
 * {@link #register(ResourceLocation, IConduitType)} during their init phase.</p>
 */
public final class ConduitTypeRegistry {

    private static final Map<ResourceLocation, IConduitType<?>> TYPES = new LinkedHashMap<>();

    private ConduitTypeRegistry() {
    }

    /**
     * Register a conduit type for a specific conduit item ID.
     *
     * @param conduitId the conduit item ID (e.g., "omnio:energy_conduit_basic")
     * @param type      the conduit type instance
     */
    public static void register(ResourceLocation conduitId, IConduitType<?> type) {
        IConduitType<?> existing = TYPES.put(conduitId, type);
        if (existing != null) {
            Constants.LOG.warn("Conduit type for {} was overwritten: {} → {}", conduitId, existing, type);
        }
        Constants.LOG.debug("Registered conduit type: {} → {}", conduitId, type);
    }

    /**
     * Look up the conduit type for a given conduit item ID.
     *
     * @param conduitId the conduit item ID
     * @return the conduit type, or null if not registered
     */
    @Nullable
    public static IConduitType<?> get(ResourceLocation conduitId) {
        return TYPES.get(conduitId);
    }

    /**
     * Look up the conduit type, falling back to a stub if not registered.
     * This ensures backward compatibility during development — conduit types
     * that haven't been implemented yet (fluid, item, redstone) still work
     * with the network manager.
     *
     * @param conduitId the conduit item ID
     * @return the registered conduit type, or a stub if not found
     */
    public static IConduitType<?> getOrStub(ResourceLocation conduitId) {
        IConduitType<?> type = TYPES.get(conduitId);
        if (type != null) return type;
        return new StubConduitType(conduitId);
    }

    /**
     * @return true if a conduit type is registered for the given ID
     */
    public static boolean isRegistered(ResourceLocation conduitId) {
        return TYPES.containsKey(conduitId);
    }

    /**
     * @return unmodifiable view of all registered conduit types
     */
    public static Map<ResourceLocation, IConduitType<?>> getAll() {
        return Collections.unmodifiableMap(TYPES);
    }

    /**
     * Clear all registered types. Called on server stop or for testing.
     */
    public static void clear() {
        TYPES.clear();
    }
}
