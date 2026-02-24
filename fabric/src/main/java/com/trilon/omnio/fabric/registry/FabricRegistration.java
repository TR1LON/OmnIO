package com.trilon.omnio.fabric.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.ConduitItem;
import com.trilon.omnio.content.conduit.OmniConduitBlock;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import com.trilon.omnio.registry.OmnIOBlockEntities;
import com.trilon.omnio.registry.OmnIOBlocks;
import com.trilon.omnio.registry.OmnIOItems;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Fabric-specific registration using vanilla {@link Registry#register}.
 * Registers all blocks, items, and block entities for OmnIO.
 */
public final class FabricRegistration {

    private FabricRegistration() {
    }

    /**
     * Register all OmnIO content into Fabric registries.
     */
    public static void init() {
        registerBlocks();
        registerBlockEntities();
        registerItems();
        registerCreativeTabs();

        Constants.LOG.debug("Fabric registries initialized");
    }

    private static void registerBlocks() {
        OmniConduitBlock block = new OmniConduitBlock(OmnIOBlocks.CONDUIT_BUNDLE_PROPS);
        Registry.register(BuiltInRegistries.BLOCK, id("conduit_bundle"), block);
        OmnIOBlocks.setConduitBundle(block);
    }

    private static void registerBlockEntities() {
        BlockEntityType<OmniConduitBlockEntity> beType = BlockEntityType.Builder
                .of(OmniConduitBlockEntity::new, OmnIOBlocks.CONDUIT_BUNDLE)
                .build(null);
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("conduit_bundle"), beType);
        OmnIOBlockEntities.setConduitBundle(beType);
    }

    private static void registerItems() {
        for (String conduitId : OmnIOItems.ALL_CONDUIT_IDS) {
            String fullId = Constants.MOD_ID + ":" + conduitId;
            ConduitItem item = new ConduitItem(fullId, OmnIOBlocks.CONDUIT_BUNDLE, new Item.Properties());
            Registry.register(BuiltInRegistries.ITEM, id(conduitId), item);
            OmnIOItems.CONDUIT_ITEMS.put(conduitId, item);
        }
    }

    private static void registerCreativeTabs() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            for (ConduitItem item : OmnIOItems.CONDUIT_ITEMS.values()) {
                entries.accept(item);
            }
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, path);
    }
}
