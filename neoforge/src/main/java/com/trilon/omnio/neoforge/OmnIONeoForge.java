package com.trilon.omnio.neoforge;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge entry point for OmnIO.
 */
@Mod(Constants.MOD_ID)
public class OmnIONeoForge {

    public OmnIONeoForge(IEventBus modEventBus) {
        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on NeoForge", Constants.MOD_NAME);
    }
}
