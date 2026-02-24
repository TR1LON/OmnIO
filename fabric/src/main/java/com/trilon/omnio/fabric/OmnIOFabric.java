package com.trilon.omnio.fabric;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.content.conduit.network.ConduitNetworkManager;
import com.trilon.omnio.fabric.registry.FabricRegistration;
import net.fabricmc.api.ModInitializer;
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

        // Tick all conduit networks each server tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var level : server.getAllLevels()) {
                ConduitNetworkManager.get(level).tickAllNetworks();
            }
        });

        // Clear cached network managers on server stop
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ConduitNetworkManager.clearAll();
        });

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on Fabric", Constants.MOD_NAME);
    }
}
