package com.trilon.omnio.neoforge;

import com.trilon.omnio.Constants;
import com.trilon.omnio.OmnIOCommon;
import com.trilon.omnio.content.conduit.network.ConduitNetworkManager;
import com.trilon.omnio.neoforge.registry.NeoForgeRegistration;
import com.trilon.omnio.neoforge.transfer.NeoForgeEnergyTransferHelper;
import com.trilon.omnio.neoforge.transfer.NeoForgeFluidTransferHelper;
import com.trilon.omnio.neoforge.transfer.NeoForgeItemTransferHelper;
import com.trilon.omnio.registry.ConduitTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

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

        // Game event listeners (tick, server stop, chunk load/unload)
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);
        NeoForge.EVENT_BUS.addListener(this::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(this::onChunkUnload);

        OmnIOCommon.init();
        Constants.LOG.info("{} initialized on NeoForge", Constants.MOD_NAME);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            NeoForgeRegistration.populateCommonReferences();
            // Register conduit types with NeoForge platform-specific transfer helpers
            ConduitTypes.register(
                    NeoForgeEnergyTransferHelper.INSTANCE,
                    NeoForgeFluidTransferHelper.INSTANCE,
                    NeoForgeItemTransferHelper.INSTANCE
            );
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        // Tick all conduit networks in every loaded dimension
        for (var level : event.getServer().getAllLevels()) {
            ConduitNetworkManager.get(level).tickAllNetworks();
        }
    }

    @SuppressWarnings("unused") // parameter required by NeoForge event bus
    private void onServerStopped(ServerStoppedEvent event) {
        // Clear all cached network managers to prevent memory leaks.
        // Note: ConduitTypeRegistry is NOT cleared here — types are registered once
        // during FMLCommonSetupEvent which only fires once per game session.
        // Clearing them would leave the registry empty if a new world is started
        // without restarting the game.
        ConduitNetworkManager.clearAll();
    }

    private void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            var chunkPos = event.getChunk().getPos();
            ConduitNetworkManager.get(serverLevel).onChunkLoaded(chunkPos.x, chunkPos.z);
        }
    }

    private void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            var chunkPos = event.getChunk().getPos();
            ConduitNetworkManager.get(serverLevel).onChunkUnloaded(chunkPos.x, chunkPos.z);
        }
    }
}
