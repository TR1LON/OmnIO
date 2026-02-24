package com.trilon.omnio.fabric.client;

import com.trilon.omnio.Constants;
import com.trilon.omnio.client.ConduitBundleRenderer;
import com.trilon.omnio.client.ConduitRenderHelper;
import com.trilon.omnio.client.ConduitScreen;
import com.trilon.omnio.content.conduit.ConduitItem;
import com.trilon.omnio.registry.OmnIOBlockEntities;
import com.trilon.omnio.registry.OmnIOItems;
import com.trilon.omnio.registry.OmnIOMenuTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/**
 * Client-side initialization for Fabric.
 * Registers block entity renderers, item colors, and menu screens.
 */
public class OmnIOFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register the BER for conduit bundles
        BlockEntityRenderers.register(
                OmnIOBlockEntities.CONDUIT_BUNDLE,
                ConduitBundleRenderer::new
        );

        // Register item color tinting for conduit items
        for (ConduitItem item : OmnIOItems.CONDUIT_ITEMS.values()) {
            ColorProviderRegistry.ITEM.register(
                    (stack, tintIndex) -> ConduitRenderHelper.getItemTintColor(
                            item.getConduitId(),
                            tintIndex,
                            ConduitItem.getChannel(stack)
                    ),
                    item
            );
        }

        // Register conduit menu screen
        MenuScreens.register(OmnIOMenuTypes.CONDUIT_MENU, ConduitScreen::new);

        Constants.LOG.debug("Fabric client initialized");
    }
}
