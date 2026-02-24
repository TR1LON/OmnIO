package com.trilon.omnio.content.conduit;

import com.trilon.omnio.api.conduit.ConnectionStatus;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds per-direction connection data for a single conduit type within a bundle.
 * Each of the 6 directions has a {@link ConnectionConfig} and optional filter item slots.
 */
public class ConnectionContainer {

    /** Number of filter slots per connection side (for item conduits, etc.) */
    public static final int FILTER_SLOTS = 5;

    private final Map<Direction, ConnectionConfig> configs = new EnumMap<>(Direction.class);
    private final Map<Direction, ItemStack[]> filterSlots = new EnumMap<>(Direction.class);

    public ConnectionContainer() {
        for (Direction dir : Direction.values()) {
            configs.put(dir, new ConnectionConfig());
            filterSlots.put(dir, createEmptyFilters());
        }
    }

    /**
     * @param direction the side to query
     * @return the connection config for the given direction
     */
    public ConnectionConfig getConfig(Direction direction) {
        return configs.get(direction);
    }

    /**
     * @param direction the side to query
     * @return the connection status for the given direction
     */
    public ConnectionStatus getStatus(Direction direction) {
        return configs.get(direction).getStatus();
    }

    /**
     * @param direction the side to query
     * @return true if there is any active connection (conduit or block) on this side
     */
    public boolean isConnected(Direction direction) {
        ConnectionStatus s = getStatus(direction);
        return s == ConnectionStatus.CONNECTED_CONDUIT || s == ConnectionStatus.CONNECTED_BLOCK;
    }

    /**
     * Set the connection config for a specific direction.
     */
    public void setConfig(Direction direction, ConnectionConfig config) {
        configs.put(direction, config);
    }

    /**
     * Disconnect a specific side (set to DISCONNECTED, reset transfer mode).
     */
    public void disconnect(Direction direction) {
        configs.put(direction, new ConnectionConfig());
    }

    /**
     * @param direction the side to query
     * @return the filter item slots for the given direction
     */
    public ItemStack[] getFilterSlots(Direction direction) {
        return filterSlots.get(direction);
    }

    /**
     * Set a filter item in a specific slot on a specific side.
     */
    public void setFilterSlot(Direction direction, int slot, ItemStack stack) {
        if (slot >= 0 && slot < FILTER_SLOTS) {
            filterSlots.get(direction)[slot] = stack;
        }
    }

    // ---- NBT Serialization ----

    private static final String TAG_DIRECTION = "Dir";
    private static final String TAG_CONFIG = "Config";
    private static final String TAG_FILTERS = "Filters";

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        ListTag dirList = new ListTag();
        for (Direction dir : Direction.values()) {
            CompoundTag dirTag = new CompoundTag();
            dirTag.putInt(TAG_DIRECTION, dir.get3DDataValue());
            dirTag.put(TAG_CONFIG, configs.get(dir).save());

            // Save filter slots
            ListTag filtersTag = new ListTag();
            for (ItemStack stack : filterSlots.get(dir)) {
                if (stack.isEmpty()) {
                    filtersTag.add(new CompoundTag());
                } else {
                    filtersTag.add((Tag) stack.save(registries));
                }
            }
            dirTag.put(TAG_FILTERS, filtersTag);

            dirList.add(dirTag);
        }
        tag.put("Directions", dirList);
        return tag;
    }

    public static ConnectionContainer load(CompoundTag tag, HolderLookup.Provider registries) {
        ConnectionContainer container = new ConnectionContainer();
        ListTag dirList = tag.getList("Directions", Tag.TAG_COMPOUND);
        for (int i = 0; i < dirList.size(); i++) {
            CompoundTag dirTag = dirList.getCompound(i);
            Direction dir = Direction.from3DDataValue(dirTag.getInt(TAG_DIRECTION));
            container.configs.put(dir, ConnectionConfig.load(dirTag.getCompound(TAG_CONFIG)));

            // Load filter slots
            if (dirTag.contains(TAG_FILTERS)) {
                ListTag filtersTag = dirTag.getList(TAG_FILTERS, Tag.TAG_COMPOUND);
                ItemStack[] slots = container.filterSlots.get(dir);
                for (int j = 0; j < Math.min(filtersTag.size(), FILTER_SLOTS); j++) {
                    CompoundTag itemTag = filtersTag.getCompound(j);
                    if (!itemTag.isEmpty()) {
                        final int slot = j;
                        ItemStack.parse(registries, itemTag).ifPresent(parsed -> slots[slot] = parsed);
                    }
                }
            }
        }
        return container;
    }

    private static ItemStack[] createEmptyFilters() {
        ItemStack[] slots = new ItemStack[FILTER_SLOTS];
        for (int i = 0; i < FILTER_SLOTS; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        return slots;
    }
}
