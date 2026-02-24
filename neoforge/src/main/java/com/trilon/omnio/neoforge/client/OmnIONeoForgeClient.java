package com.trilon.omnio.neoforge.client;

import com.trilon.omnio.Constants;
import com.trilon.omnio.client.ConduitBundleRenderer;
import com.trilon.omnio.client.ConduitRenderHelper;
import com.trilon.omnio.client.ConduitScreen;
import com.trilon.omnio.content.conduit.ConduitItem;
import com.trilon.omnio.neoforge.registry.NeoForgeRegistration;
import com.trilon.omnio.registry.OmnIOItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-side initialization for NeoForge.
 * Registers block entity renderers, item colors, model loaders, and menu screens.
 */
public final class OmnIONeoForgeClient {

    private OmnIONeoForgeClient() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(OmnIONeoForgeClient::onRegisterRenderers);
        modEventBus.addListener(OmnIONeoForgeClient::onRegisterItemColors);
        modEventBus.addListener(OmnIONeoForgeClient::onRegisterMenuScreens);
        Constants.LOG.debug("NeoForge client events registered");
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                NeoForgeRegistration.CONDUIT_BUNDLE_BE.get(),
                ConduitBundleRenderer::new
        );
        Constants.LOG.debug("Registered ConduitBundleRenderer");
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        NeoForgeRegistration.getConduitItemHolders().forEach((id, holder) -> {
            ConduitItem item = holder.get();
            event.register(
                    (stack, tintIndex) -> ConduitRenderHelper.getItemTintColor(
                            item.getConduitId(),
                            tintIndex,
                            ConduitItem.getChannel(stack)
                    ),
                    item
            );
        });
        Constants.LOG.debug("Registered conduit item colors");
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(NeoForgeRegistration.CONDUIT_MENU.get(), ConduitScreen::new);
        Constants.LOG.debug("Registered ConduitScreen");
    }
}
