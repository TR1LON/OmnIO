package com.trilon.omnio.content.conduit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data sent from server to client when opening the conduit GUI.
 * Contains the block position and the initially selected face.
 */
public record ConduitMenuData(BlockPos pos, int faceIndex) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ConduitMenuData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, data) -> {
                        buf.writeBlockPos(data.pos);
                        buf.writeByte(data.faceIndex);
                    },
                    buf -> new ConduitMenuData(buf.readBlockPos(), buf.readByte())
            );

    public Direction face() {
        return Direction.from3DDataValue(Math.clamp(faceIndex, 0, 5));
    }

    public ConduitMenuData(BlockPos pos, Direction face) {
        this(pos, face.get3DDataValue());
    }
}
