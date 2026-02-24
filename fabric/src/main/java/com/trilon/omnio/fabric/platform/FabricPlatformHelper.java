package com.trilon.omnio.fabric.platform;

import com.trilon.omnio.content.conduit.ConduitMenu;
import com.trilon.omnio.content.conduit.ConduitMenuData;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import com.trilon.omnio.platform.IPlatformHelper;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import org.jetbrains.annotations.Nullable;

/**
 * Fabric implementation of {@link IPlatformHelper}.
 */
public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public void openConduitScreen(ServerPlayer player, OmniConduitBlockEntity be, Direction clickedFace) {
        player.openMenu(new ExtendedScreenHandlerFactory<ConduitMenuData>() {
            @Override
            public ConduitMenuData getScreenOpeningData(ServerPlayer serverPlayer) {
                return new ConduitMenuData(be.getBlockPos(), clickedFace);
            }

            @Override
            public Component getDisplayName() {
                return be.getDisplayName();
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inv, Player p) {
                return be.createMenu(containerId, inv, p);
            }
        });
        // Pre-select the face on the server-side menu as well
        if (player.containerMenu instanceof ConduitMenu cm) {
            cm.selectedFace = clickedFace;
        }
    }
}
