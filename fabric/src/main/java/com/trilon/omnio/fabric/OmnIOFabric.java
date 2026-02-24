package com.trilon.omnio.fabric;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.fabric.registry.FabricRegistration;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point for OmnIO.
 */
public class OmnIOFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // Register all blocks, items, block entities
        FabricRegistration.init();

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on Fabric", Constants.MOD_NAME);
    }
}
