package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConduitSlot;
import com.trilon.omnio.api.conduit.ConnectionStatus;
import com.trilon.omnio.api.conduit.IConnectionConfig.RedstoneMode;
import com.trilon.omnio.api.conduit.IConnectionConfig.TransferMode;
import com.trilon.omnio.registry.OmnIOMenuTypes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side container for the conduit configuration GUI.
 * Manages conduit/face selection and syncs configuration values to the client
 * via {@link ContainerData}.
 *
 * <p>No real inventory slots — all interaction is via {@link #clickMenuButton}.</p>
 */
public class ConduitMenu extends AbstractContainerMenu {

    /** Number of ContainerData integer slots synced automatically. */
    public static final int DATA_COUNT = 10;

    // ContainerData indices
    public static final int DATA_TRANSFER_MODE = 0;
    public static final int DATA_PRIORITY = 1;
    public static final int DATA_REDSTONE_MODE = 2;
    public static final int DATA_CHANNEL = 3;
    /** Indices 4–9: connection status ordinal per face (DOWN=4 … EAST=9). */
    public static final int DATA_FACE_STATUS_BASE = 4;

    // Button ID ranges for clickMenuButton
    public static final int BTN_SELECT_SLOT_BASE = 0;   // 0–8
    public static final int BTN_SELECT_FACE_BASE = 10;   // 10–15
    public static final int BTN_CYCLE_TRANSFER = 20;
    public static final int BTN_CYCLE_REDSTONE = 21;
    public static final int BTN_PRIORITY_UP = 22;
    public static final int BTN_PRIORITY_DOWN = 23;
    public static final int BTN_SET_CHANNEL_BASE = 30;   // 30–45

    private final BlockPos pos;
    @Nullable
    private final OmniConduitBlockEntity blockEntity;
    private final ContainerData data;

    /** Currently selected conduit slot index (0-based into the ordered slot list). */
    int selectedSlotIndex = 0;

    /** Currently selected face for configuration. */
    public Direction selectedFace = Direction.NORTH;

    // ---- Constructors ----

    /**
     * Server-side constructor — called when a player opens the GUI.
     */
    public ConduitMenu(int containerId, Inventory playerInv, OmniConduitBlockEntity be) {
        super(OmnIOMenuTypes.CONDUIT_MENU, containerId);
        this.pos = be.getBlockPos();
        this.blockEntity = be;
        this.data = new ServerContainerData();
        addDataSlots(data);
    }

    /**
     * Client-side constructor — called from the platform network factory.
     * Resolves the block entity from the client level.
     */
    public ConduitMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, pos, Direction.NORTH);
    }

    /**
     * Client-side constructor with initial face selection.
     */
    public ConduitMenu(int containerId, Inventory playerInv, BlockPos pos, Direction initialFace) {
        super(OmnIOMenuTypes.CONDUIT_MENU, containerId);
        this.pos = pos;
        if (playerInv.player.level().getBlockEntity(pos) instanceof OmniConduitBlockEntity be) {
            this.blockEntity = be;
        } else {
            this.blockEntity = null;
        }
        this.selectedFace = initialFace;
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    // ---- Accessors ----

    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public OmniConduitBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public int getSelectedSlotIndex() {
        return selectedSlotIndex;
    }

    public Direction getSelectedFace() {
        return selectedFace;
    }

    /**
     * @return ordered list of conduit slots in the bundle (sorted deterministically), or empty if BE is unavailable
     */
    public List<ConduitSlot> getConduitSlots() {
        if (blockEntity != null) {
            return blockEntity.getSortedSlots();
        }
        return List.of();
    }

    /**
     * @return the currently selected conduit slot, or null if index is out of range
     */
    @Nullable
    public ConduitSlot getSelectedSlot() {
        List<ConduitSlot> slots = getConduitSlots();
        if (selectedSlotIndex >= 0 && selectedSlotIndex < slots.size()) {
            return slots.get(selectedSlotIndex);
        }
        return null;
    }

    /**
     * @return the {@link ConnectionConfig} for the selected conduit + face, or null
     */
    @Nullable
    ConnectionConfig getSelectedConfig() {
        if (blockEntity == null) return null;
        ConduitSlot slot = getSelectedSlot();
        if (slot == null) return null;
        ConnectionContainer container = blockEntity.getConnectionContainer(slot);
        if (container == null) return null;
        return container.getConfig(selectedFace);
    }

    // ---- Synced Data Getters (read by ConduitScreen) ----

    public TransferMode getTransferMode() {
        int ordinal = data.get(DATA_TRANSFER_MODE);
        TransferMode[] values = TransferMode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : TransferMode.DISABLED;
    }

    /**
     * @return the transfer mode for a specific face of the selected conduit,
     *         reading directly from the block entity (works for non-selected faces too).
     */
    public TransferMode getFaceTransferMode(Direction face) {
        if (blockEntity == null) return TransferMode.DISABLED;
        ConduitSlot slot = getSelectedSlot();
        if (slot == null) return TransferMode.DISABLED;
        ConnectionContainer container = blockEntity.getConnectionContainer(slot);
        if (container == null) return TransferMode.DISABLED;
        ConnectionConfig cfg = container.getConfig(face);
        return cfg != null ? cfg.getTransferMode() : TransferMode.DISABLED;
    }

    public int getPriority() {
        return data.get(DATA_PRIORITY);
    }

    public RedstoneMode getRedstoneMode() {
        int ordinal = data.get(DATA_REDSTONE_MODE);
        RedstoneMode[] values = RedstoneMode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : RedstoneMode.ALWAYS_ACTIVE;
    }

    public int getChannel() {
        return data.get(DATA_CHANNEL);
    }

    /**
     * @return the connection status for a given face of the selected conduit
     */
    public ConnectionStatus getFaceStatus(Direction face) {
        int ordinal = data.get(DATA_FACE_STATUS_BASE + face.get3DDataValue());
        ConnectionStatus[] values = ConnectionStatus.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ConnectionStatus.DISCONNECTED;
    }

    // ---- Button Handling ----

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        // Select conduit slot (0–8)
        if (buttonId >= BTN_SELECT_SLOT_BASE && buttonId <= BTN_SELECT_SLOT_BASE + 8) {
            int index = buttonId - BTN_SELECT_SLOT_BASE;
            if (index < getConduitSlots().size()) {
                selectedSlotIndex = index;
            }
            return true;
        }

        // Select face (10–15 → Direction ordinals)
        if (buttonId >= BTN_SELECT_FACE_BASE && buttonId <= BTN_SELECT_FACE_BASE + 5) {
            selectedFace = Direction.from3DDataValue(buttonId - BTN_SELECT_FACE_BASE);
            return true;
        }

        // Cycle transfer mode
        if (buttonId == BTN_CYCLE_TRANSFER) {
            ConnectionConfig cfg = getSelectedConfig();
            ConduitSlot slot = getSelectedSlot();
            if (cfg != null && blockEntity != null && slot != null) {
                cfg.cycleTransferMode();
                blockEntity.notifyConfigChanged(slot);
            }
            return true;
        }

        // Cycle redstone mode
        if (buttonId == BTN_CYCLE_REDSTONE) {
            ConnectionConfig cfg = getSelectedConfig();
            ConduitSlot slot = getSelectedSlot();
            if (cfg != null && blockEntity != null && slot != null) {
                cfg.cycleRedstoneMode();
                blockEntity.notifyConfigChanged(slot);
            }
            return true;
        }

        // Priority up
        if (buttonId == BTN_PRIORITY_UP) {
            ConnectionConfig cfg = getSelectedConfig();
            ConduitSlot slot = getSelectedSlot();
            if (cfg != null && blockEntity != null && slot != null) {
                cfg.setPriority(cfg.getPriority() + 1);
                blockEntity.notifyConfigChanged(slot);
            }
            return true;
        }

        // Priority down
        if (buttonId == BTN_PRIORITY_DOWN) {
            ConnectionConfig cfg = getSelectedConfig();
            ConduitSlot slot = getSelectedSlot();
            if (cfg != null && blockEntity != null && slot != null) {
                cfg.setPriority(cfg.getPriority() - 1);
                blockEntity.notifyConfigChanged(slot);
            }
            return true;
        }

        // Set channel color (30–45)
        if (buttonId >= BTN_SET_CHANNEL_BASE && buttonId <= BTN_SET_CHANNEL_BASE + 15) {
            int channel = buttonId - BTN_SET_CHANNEL_BASE;
            ConnectionConfig cfg = getSelectedConfig();
            ConduitSlot slot = getSelectedSlot();
            if (cfg != null && blockEntity != null && slot != null) {
                cfg.setChannel(channel);
                blockEntity.notifyConfigChanged(slot);
            }
            return true;
        }

        return false;
    }

    // ---- Required Overrides ----

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY; // No inventory slots in this menu
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    // ---- Server-side ContainerData Implementation ----

    /**
     * Dynamically reads config values from the block entity based on current selection.
     * Auto-synced to the client every tick by AbstractContainerMenu.broadcastChanges().
     */
    private class ServerContainerData implements ContainerData {

        @Override
        public int get(int index) {
            ConnectionConfig cfg = getSelectedConfig();
            return switch (index) {
                case DATA_TRANSFER_MODE -> cfg != null ? cfg.getTransferMode().ordinal() : 0;
                case DATA_PRIORITY -> cfg != null ? cfg.getPriority() : 0;
                case DATA_REDSTONE_MODE -> cfg != null ? cfg.getRedstoneMode().ordinal() : 0;
                case DATA_CHANNEL -> cfg != null ? cfg.getChannel() : 0;
                default -> {
                    // Face status indices 4–9
                    if (index >= DATA_FACE_STATUS_BASE && index < DATA_FACE_STATUS_BASE + 6) {
                        Direction face = Direction.from3DDataValue(index - DATA_FACE_STATUS_BASE);
                        ConduitSlot slot = getSelectedSlot();
                        if (slot != null && blockEntity != null) {
                            ConnectionContainer container = blockEntity.getConnectionContainer(slot);
                            if (container != null) {
                                yield container.getStatus(face).ordinal();
                            }
                        }
                        yield ConnectionStatus.DISCONNECTED.ordinal();
                    }
                    yield 0;
                }
            };
        }

        @Override
        public void set(int index, int value) {
            // Read-only on server — clients modify via clickMenuButton
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    }
}
