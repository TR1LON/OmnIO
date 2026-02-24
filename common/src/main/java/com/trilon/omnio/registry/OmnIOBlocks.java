package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.OmniConduitBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Block definitions for OmnIO.
 * Actual registration is performed by platform-specific modules (NeoForge/Fabric)
 * using these definitions.
 */
public final class OmnIOBlocks {

    /**
     * The single conduit bundle block. All conduit types share this one block.
     */
    public static OmniConduitBlock CONDUIT_BUNDLE;

    /**
     * Block properties for the conduit bundle.
     */
    public static final BlockBehaviour.Properties CONDUIT_BUNDLE_PROPS = BlockBehaviour.Properties.of()
            .strength(1.5F, 10.0F)
            .sound(SoundType.METAL)
            .noOcclusion()
            .dynamicShape();

    private OmnIOBlocks() {
    }

    /**
     * Called by platform modules after registration to set the static reference.
     */
    public static void setConduitBundle(OmniConduitBlock block) {
        CONDUIT_BUNDLE = block;
    }
}
