package com.trilon.omnio.fabric;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.content.conduit.network.ConduitNetworkManager;
import com.trilon.omnio.fabric.registry.FabricRegistration;
import com.trilon.omnio.fabric.transfer.FabricEnergyTransferHelper;
import com.trilon.omnio.fabric.transfer.FabricFluidTransferHelper;
import com.trilon.omnio.fabric.transfer.FabricItemTransferHelper;
import com.trilon.omnio.registry.ConduitTypes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Fabric entry point for OmnIO.
 */
public class OmnIOFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register all blocks, items, block entities
        FabricRegistration.init();

        // Register conduit types with Fabric platform-specific transfer helpers
        ConduitTypes.register(
                FabricEnergyTransferHelper.INSTANCE,
                FabricFluidTransferHelper.INSTANCE,
                FabricItemTransferHelper.INSTANCE
        );

        // Tick all conduit networks each server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                ConduitNetworkManager.get(level).tickAllNetworks();
            }
        });

        // Clear cached network managers on server stop.
        // Note: ConduitTypeRegistry is NOT cleared — types are registered once
        // during onInitialize which only fires once per game session.
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ConduitNetworkManager.clearAll();
        });

        // Mark conduit nodes as ticking/non-ticking when chunks load/unload
        ServerChunkEvents.CHUNK_LOAD.register((serverLevel, chunk) -> {
            var chunkPos = chunk.getPos();
            ConduitNetworkManager.get(serverLevel).onChunkLoaded(chunkPos.x, chunkPos.z);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((serverLevel, chunk) -> {
            var chunkPos = chunk.getPos();
            ConduitNetworkManager.get(serverLevel).onChunkUnloaded(chunkPos.x, chunkPos.z);
        });

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on Fabric", Constants.MOD_NAME);
    }
}
