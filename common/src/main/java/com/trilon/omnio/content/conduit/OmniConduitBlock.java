package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.platform.IPlatformHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

/**
 * The single block type used for ALL conduit bundles.
 * Multiple conduit types (energy, fluid, item, redstone) can coexist within
 * a single instance of this block. The actual conduit data is stored in the
 * {@link OmniConduitBlockEntity}.
 *
 * <p>Supports waterlogging and dynamic shapes based on conduit connections.</p>
 */
public class OmniConduitBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** Base center core shape (6x6x6 centered) */
    private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

    /** Connector arm shapes per direction (from core to block face) */
    private static final VoxelShape[] CONNECTOR_SHAPES = new VoxelShape[6];

    static {
        // DOWN (Y-)
        CONNECTOR_SHAPES[Direction.DOWN.get3DDataValue()] = Block.box(5.0, 0.0, 5.0, 11.0, 5.0, 11.0);
        // UP (Y+)
        CONNECTOR_SHAPES[Direction.UP.get3DDataValue()] = Block.box(5.0, 11.0, 5.0, 11.0, 16.0, 11.0);
        // NORTH (Z-)
        CONNECTOR_SHAPES[Direction.NORTH.get3DDataValue()] = Block.box(5.0, 5.0, 0.0, 11.0, 11.0, 5.0);
        // SOUTH (Z+)
        CONNECTOR_SHAPES[Direction.SOUTH.get3DDataValue()] = Block.box(5.0, 5.0, 11.0, 11.0, 11.0, 16.0);
        // WEST (X-)
        CONNECTOR_SHAPES[Direction.WEST.get3DDataValue()] = Block.box(0.0, 5.0, 5.0, 5.0, 11.0, 11.0);
        // EAST (X+)
        CONNECTOR_SHAPES[Direction.EAST.get3DDataValue()] = Block.box(11.0, 5.0, 5.0, 16.0, 11.0, 11.0);
    }

    public OmniConduitBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    // ---- Shape ----

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OmniConduitBlockEntity conduitBE) {
            return conduitBE.getDynamicShape();
        }
        return CORE_SHAPE;
    }

    /**
     * Build a composite VoxelShape from the core plus connector arms for each connected direction.
     *
     * @param connectedDirections a set of directions that have active connections
     * @return the combined VoxelShape
     */
    public static VoxelShape buildShape(java.util.Set<Direction> connectedDirections) {
        VoxelShape shape = CORE_SHAPE;
        for (Direction dir : connectedDirections) {
            shape = Shapes.or(shape, CONNECTOR_SHAPES[dir.get3DDataValue()]);
        }
        return shape;
    }

    // ---- Interaction ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        // Allow ConduitItem placement to pass through to Item.useOn
        if (stack.getItem() instanceof ConduitItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // Only open the GUI when clicking a pad (a face with a block connection).
        // Clicking the cable body itself does nothing useful.
        // Determine which pad was hit using position, not face normal — the adjacent
        // block normally prevents clicking the outward face, so the player clicks
        // from an angle and getDirection() returns the wrong face.
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof OmniConduitBlockEntity conduitBE) {
            double lx = hitResult.getLocation().x - pos.getX();
            double ly = hitResult.getLocation().y - pos.getY();
            double lz = hitResult.getLocation().z - pos.getZ();
            Direction padDir = ConduitShape.hitTestPad(lx, ly, lz);

            if (padDir != null) {
                // Verify that this direction actually has a CONNECTED_BLOCK pad
                boolean hasPad = false;
                for (var slot : conduitBE.getSortedSlots()) {
                    ConnectionContainer container = conduitBE.getConnectionContainer(slot);
                    if (container != null && container.getStatus(padDir) == ConnectionStatus.CONNECTED_BLOCK) {
                        hasPad = true;
                        break;
                    }
                }
                if (hasPad) {
                    if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
                        IPlatformHelper.INSTANCE.openConduitScreen(serverPlayer, conduitBE, padDir);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ---- Block Entity ----

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OmniConduitBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // No per-BE ticker — network ticking is driven by ConduitNetworkManager from the server tick event
        return null;
    }

    // ---- Waterlogging ----

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        // Notify the block entity to re-evaluate connections when a neighbor changes
        // LevelAccessor does not have isClientSide(); use instanceof ServerLevel instead
        if (level instanceof ServerLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OmniConduitBlockEntity conduitBE) {
                conduitBE.onNeighborChanged(direction, neighborState, neighborPos);
            }
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    // ---- Destruction ----

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OmniConduitBlockEntity conduitBE) {
                conduitBE.onBlockRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ---- Rendering ----

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Custom rendering will be handled by BER in Phase 7; suppress default block model
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(WATERLOGGED);
    }
}
