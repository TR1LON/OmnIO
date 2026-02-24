package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.OmniConduitBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Block entity definitions for OmnIO.
 * Actual registration is performed by platform-specific modules.
 */
public final class OmnIOBlockEntities {

    /**
     * The block entity type for conduit bundles.
     */
    public static BlockEntityType<OmniConduitBlockEntity> CONDUIT_BUNDLE;

    private OmnIOBlockEntities() {
    }

    /**
     * Called by platform modules after registration to set the static reference.
     */
    public static void setConduitBundle(BlockEntityType<OmniConduitBlockEntity> type) {
        CONDUIT_BUNDLE = type;
        OmniConduitBlockEntity.setType(type);
    }

    /**
     * @return the registered block entity type for conduit bundles
     */
    public static BlockEntityType<OmniConduitBlockEntity> getConduitBundle() {
        return CONDUIT_BUNDLE;
    }
}
