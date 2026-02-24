package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.api.transfer.IFluidTransferHelper;
import com.trilon.omnio.api.transfer.IItemTransferHelper;
import com.trilon.omnio.api.transfer.ITransferHelper;
import com.trilon.omnio.content.conduit.network.ConduitTypeRegistry;
import com.trilon.omnio.content.conduit.type.energy.EnergyConduitTier;
import com.trilon.omnio.content.conduit.type.energy.EnergyConduitType;
import com.trilon.omnio.content.conduit.type.energy.NoOpEnergyTransferHelper;
import com.trilon.omnio.content.conduit.type.fluid.FluidConduitTier;
import com.trilon.omnio.content.conduit.type.fluid.FluidConduitType;
import com.trilon.omnio.content.conduit.type.fluid.NoOpFluidTransferHelper;
import com.trilon.omnio.content.conduit.type.item.ItemConduitTier;
import com.trilon.omnio.content.conduit.type.item.ItemConduitType;
import com.trilon.omnio.content.conduit.type.item.NoOpItemTransferHelper;
import com.trilon.omnio.content.conduit.type.redstone.RedstoneConduitType;
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
     * Register built-in conduit types using the given platform-specific transfer helpers.
     *
     * @param energyHelper the platform-specific energy transfer implementation
     * @param fluidHelper  the platform-specific fluid transfer implementation
     * @param itemHelper   the platform-specific item transfer implementation
     */
    public static void register(ITransferHelper<Long> energyHelper,
                                 IFluidTransferHelper fluidHelper,
                                 IItemTransferHelper itemHelper) {
        Constants.LOG.debug("Registering built-in conduit types");

        // Register energy conduit types (one per tier, excluding CREATIVE)
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_BASIC, EnergyConduitTier.BASIC, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ADVANCED, EnergyConduitTier.ADVANCED, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ELITE, EnergyConduitTier.ELITE, energyHelper);
        registerEnergyConduit(OmnIOItems.ENERGY_CONDUIT_ULTIMATE, EnergyConduitTier.ULTIMATE, energyHelper);

        // Register fluid conduit types (one per tier, excluding CREATIVE)
        registerFluidConduit(OmnIOItems.FLUID_CONDUIT_BASIC, FluidConduitTier.BASIC, fluidHelper);
        registerFluidConduit(OmnIOItems.FLUID_CONDUIT_ADVANCED, FluidConduitTier.ADVANCED, fluidHelper);
        registerFluidConduit(OmnIOItems.FLUID_CONDUIT_ELITE, FluidConduitTier.ELITE, fluidHelper);
        registerFluidConduit(OmnIOItems.FLUID_CONDUIT_ULTIMATE, FluidConduitTier.ULTIMATE, fluidHelper);

        // Register item conduit types (one per tier, excluding CREATIVE)
        registerItemConduit(OmnIOItems.ITEM_CONDUIT_BASIC, ItemConduitTier.BASIC, itemHelper);
        registerItemConduit(OmnIOItems.ITEM_CONDUIT_ADVANCED, ItemConduitTier.ADVANCED, itemHelper);
        registerItemConduit(OmnIOItems.ITEM_CONDUIT_ELITE, ItemConduitTier.ELITE, itemHelper);
        registerItemConduit(OmnIOItems.ITEM_CONDUIT_ULTIMATE, ItemConduitTier.ULTIMATE, itemHelper);

        // Register redstone conduit type (single tier)
        registerRedstoneConduit(OmnIOItems.REDSTONE_CONDUIT);

        Constants.LOG.info("Registered {} conduit types", ConduitTypeRegistry.getAll().size());
    }

    /**
     * Register with the no-op helpers (used when no platform helpers are available yet).
     */
    public static void register() {
        register(NoOpEnergyTransferHelper.INSTANCE,
                NoOpFluidTransferHelper.INSTANCE,
                NoOpItemTransferHelper.INSTANCE);
    }

    /**
     * Legacy overload for backward compatibility — registers energy only with real helper,
     * other types get no-op helpers.
     */
    public static void register(ITransferHelper<Long> energyHelper) {
        register(energyHelper, NoOpFluidTransferHelper.INSTANCE, NoOpItemTransferHelper.INSTANCE);
    }

    private static void registerEnergyConduit(String itemId, EnergyConduitTier tier, ITransferHelper<Long> helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, itemId);
        EnergyConduitType type = new EnergyConduitType(id, tier, helper);
        ConduitTypeRegistry.register(id, type);
    }

    private static void registerFluidConduit(String itemId, FluidConduitTier tier, IFluidTransferHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, itemId);
        FluidConduitType type = new FluidConduitType(id, tier, helper);
        ConduitTypeRegistry.register(id, type);
    }

    private static void registerItemConduit(String itemId, ItemConduitTier tier, IItemTransferHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, itemId);
        ItemConduitType type = new ItemConduitType(id, tier, helper);
        ConduitTypeRegistry.register(id, type);
    }

    private static void registerRedstoneConduit(String itemId) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, itemId);
        RedstoneConduitType type = new RedstoneConduitType(id);
        ConduitTypeRegistry.register(id, type);
    }
}
