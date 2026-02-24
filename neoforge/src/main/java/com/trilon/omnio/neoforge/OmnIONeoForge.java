package com.trilon.omnio.neoforge;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.neoforge.registry.NeoForgeRegistration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

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

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on NeoForge", Constants.MOD_NAME);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NeoForgeRegistration::populateCommonReferences);
    }
}
