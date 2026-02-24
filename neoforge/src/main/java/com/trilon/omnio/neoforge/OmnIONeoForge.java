package com.trilon.omnio.neoforge;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.content.conduit.network.ConduitNetworkManager;
import com.trilon.omnio.neoforge.registry.NeoForgeRegistration;
import com.trilon.omnio.neoforge.transfer.NeoForgeEnergyTransferHelper;
import com.trilon.omnio.registry.ConduitTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point for OmnIO.
 */
@Mod(Constants.MOD_ID)
public class OmnIONeoForge {

    public OmnIONeoForge(IEventBus modEventBus) {
        // Register all blocks, items, block entities via DeferredRegister
        NeoForgeRegistration.init(modEventBus);

        // Populate common static references after registries freeze
        modEventBus.addListener(this::onCommonSetup);

        // Game event listeners (tick, server stop)
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on NeoForge", Constants.MOD_NAME);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForgeRegistration.populateCommonReferences();
            // Register conduit types with NeoForge energy transfer helper
            ConduitTypes.register(NeoForgeEnergyTransferHelper.INSTANCE);
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // Tick all conduit networks in every loaded dimension
        for (var level : event.getServer().getAllLevels()) {
            ConduitNetworkManager.get(level).tickAllNetworks();
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        // Clear all cached network managers to prevent memory leaks.
        // Note: ConduitTypeRegistry is NOT cleared here — types are registered once
        // during FMLCommonSetupEvent which only fires once per game session.
        // Clearing them would leave the registry empty if a new world is started
        // without restarting the game.
        ConduitNetworkManager.clearAll();
    }
}
