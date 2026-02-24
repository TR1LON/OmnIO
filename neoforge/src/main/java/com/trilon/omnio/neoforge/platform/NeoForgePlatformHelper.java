package com.trilon.omnio.neoforge.platform;

import com.trilon.omnio.content.conduit.ConduitMenu;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import com.trilon.omnio.platform.IPlatformHelper;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

/**
 * NeoForge implementation of {@link IPlatformHelper}.
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public void openConduitScreen(ServerPlayer player, OmniConduitBlockEntity be, Direction clickedFace) {
        player.openMenu(be, buf -> {
            buf.writeBlockPos(be.getBlockPos());
            buf.writeByte(clickedFace.get3DDataValue());
        });
        // Pre-select the face on the server-side menu as well
        if (player.containerMenu instanceof ConduitMenu cm) {
            cm.selectedFace = clickedFace;
        }
    }
}
