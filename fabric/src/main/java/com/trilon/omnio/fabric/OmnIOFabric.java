package com.trilon.omnio.fabric;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import net.fabricmc.api.ModInitializer;

/**
 * Fabric entry point for OmnIO.
 */
public class OmnIOFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on Fabric", Constants.MOD_NAME);
    }
}
