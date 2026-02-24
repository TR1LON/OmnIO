package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.transfer.ITransferHelper;
import com.trilon.omnio.content.conduit.network.ConduitTypeRegistry;
import com.trilon.omnio.content.conduit.type.energy.EnergyConduitTier;
import com.trilon.omnio.content.conduit.type.energy.EnergyConduitType;
import com.trilon.omnio.content.conduit.type.energy.NoOpEnergyTransferHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Built-in conduit type registrations.
 * Registers all OmnIO conduit types into the {@link ConduitTypeRegistry}.
 */
public final class ConduitTypes {

    public static final ResourceLocation ENERGY = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "energy");
    public static final ResourceLocation FLUID = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "fluid");
    public static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "item");
    public static final ResourceLocation REDSTONE = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "redstone");

    private ConduitTypes() {
    }

    /**
     * Register built-in conduit types using the given energy transfer helper.
     * The helper is platform-specific (NeoForge IEnergyStorage, Fabric Energy API).
     *
     * @param energyHelper the platform-specific energy transfer implementation
     */
    public static void register(ITransferHelper<Long> energyHelper) {
        Constants.LOG.debug("Registering built-in conduit types");

        // Register energy conduit types (one per tier)
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_BASIC, EnergyConduitTier.BASIC, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ADVANCED, EnergyConduitTier.ADVANCED, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ELITE, EnergyConduitTier.ELITE, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ULTIMATE, EnergyConduitTier.ULTIMATE, energyHelper);

        // TODO: Register fluid conduit types (Phase 6)
        // TODO: Register item conduit types (Phase 7)
        // TODO: Register redstone conduit types (Phase 8)

        Constants.LOG.info("Registered {} conduit types", ConduitTypeRegistry.getAll().size());
    }

    /**
     * Register with the no-op helper (used when no platform helper is available yet).
     */
    public static void register() {
        register(NoOpEnergyTransferHelper.INSTANCE);
    }

    private static void registerEnergyConduit(String itemId, EnergyConduitTier tier, ITransferHelper<Long> helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, itemId);
        EnergyConduitType type = new EnergyConduitType(id, tier, helper);
        ConduitTypeRegistry.register(id, type);
    }
}
