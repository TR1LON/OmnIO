package com.trilon.omnio.neoforge.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.ConduitItem;
import com.trilon.omnio.content.conduit.ConduitMenu;
import com.trilon.omnio.content.conduit.OmniConduitBlock;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import com.trilon.omnio.registry.OmnIOBlockEntities;
import com.trilon.omnio.registry.OmnIOBlocks;
import com.trilon.omnio.registry.OmnIOItems;
import com.trilon.omnio.registry.OmnIOMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge-specific registration using {@link DeferredRegister}.
 * Registers all blocks, items, and block entities for OmnIO.
 */
public final class NeoForgeRegistration {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Constants.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Constants.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, Constants.MOD_ID);

    // ---- Blocks ----

    public static final DeferredBlock<OmniConduitBlock> CONDUIT_BUNDLE_BLOCK =
            BLOCKS.register("conduit_bundle", () -> new OmniConduitBlock(OmnIOBlocks.CONDUIT_BUNDLE_PROPS));

    // ---- Block Entities ----

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OmniConduitBlockEntity>> CONDUIT_BUNDLE_BE =
            BLOCK_ENTITIES.register("conduit_bundle", () ->
                    BlockEntityType.Builder.of(OmniConduitBlockEntity::new, CONDUIT_BUNDLE_BLOCK.get()).build(null));

    // ---- Menus ----

    public static final DeferredHolder<MenuType<?>, MenuType<ConduitMenu>> CONDUIT_MENU =
            MENUS.register("conduit", () ->
                    IMenuTypeExtension.create((containerId, inv, buf) -> {
                        BlockPos pos = buf.readBlockPos();
                        int faceIdx = buf.readByte();
                        Direction face = Direction.from3DDataValue(Math.max(0, Math.min(5, faceIdx)));
                        return new ConduitMenu(containerId, inv, pos, face);
                    }));

    // ---- Items (Conduit Items) ----

    private static final Map<String, DeferredItem<ConduitItem>> CONDUIT_ITEM_HOLDERS = new LinkedHashMap<>();

    static {
        for (String conduitId : OmnIOItems.ALL_CONDUIT_IDS) {
            String fullId = Constants.MOD_ID + ":" + conduitId;
            CONDUIT_ITEM_HOLDERS.put(conduitId,
                    ITEMS.register(conduitId, () -> new ConduitItem(
                            fullId,
                            CONDUIT_BUNDLE_BLOCK.get(),
                            new Item.Properties()
                    ))
            );
        }
    }

    private NeoForgeRegistration() {
    }

    /**
     * Returns the deferred item holders for client-side registration (item colors etc.).
     */
    public static Map<String, DeferredItem<ConduitItem>> getConduitItemHolders() {
        return Collections.unmodifiableMap(CONDUIT_ITEM_HOLDERS);
    }

    /**
     * Register all deferred registers on the mod event bus and set up common references.
     */
    public static void init(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);

        // Listen for registry completion to populate common static references
        modEventBus.addListener(NeoForgeRegistration::onBuildCreativeTabs);

        Constants.LOG.debug("NeoForge registries initialized");
    }

    /**
     * Called after registries are frozen to populate common static references.
     * Should be invoked from the FMLCommonSetupEvent or similar.
     */
    public static void populateCommonReferences() {
        OmnIOBlocks.setConduitBundle(CONDUIT_BUNDLE_BLOCK.get());
        OmnIOBlockEntities.setConduitBundle(CONDUIT_BUNDLE_BE.get());
        OmnIOMenuTypes.setConduitMenu(CONDUIT_MENU.get());

        for (Map.Entry<String, DeferredItem<ConduitItem>> entry : CONDUIT_ITEM_HOLDERS.entrySet()) {
            OmnIOItems.CONDUIT_ITEMS.put(entry.getKey(), entry.getValue().get());
        }

        Constants.LOG.debug("Common references populated from NeoForge registry");
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            for (DeferredItem<ConduitItem> item : CONDUIT_ITEM_HOLDERS.values()) {
                event.accept(item);
            }
        }
    }
}
