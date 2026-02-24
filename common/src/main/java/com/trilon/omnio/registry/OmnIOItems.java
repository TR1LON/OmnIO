package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.ConduitItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Item definitions for OmnIO.
 * Contains conduit items (one per type+tier combination).
 * Actual registration is performed by platform-specific modules.
 */
public final class OmnIOItems {

    /**
     * Map of conduit ID → ConduitItem instance. Populated by platform registration.
     */
    public static final Map<String, ConduitItem> CONDUIT_ITEMS = new LinkedHashMap<>();

    // Conduit item IDs: "{type}_{tier}"
    public static final String ENERGY_CONDUIT_BASIC = "energy_conduit_basic";
    public static final String ENERGY_CONDUIT_ADVANCED = "energy_conduit_advanced";
    public static final String ENERGY_CONDUIT_ELITE = "energy_conduit_elite";
    public static final String ENERGY_CONDUIT_ULTIMATE = "energy_conduit_ultimate";

    public static final String FLUID_CONDUIT_BASIC = "fluid_conduit_basic";
    public static final String FLUID_CONDUIT_ADVANCED = "fluid_conduit_advanced";
    public static final String FLUID_CONDUIT_ELITE = "fluid_conduit_elite";
    public static final String FLUID_CONDUIT_ULTIMATE = "fluid_conduit_ultimate";

    public static final String ITEM_CONDUIT_BASIC = "item_conduit_basic";
    public static final String ITEM_CONDUIT_ADVANCED = "item_conduit_advanced";
    public static final String ITEM_CONDUIT_ELITE = "item_conduit_elite";
    public static final String ITEM_CONDUIT_ULTIMATE = "item_conduit_ultimate";

    public static final String REDSTONE_CONDUIT = "redstone_conduit";

    /** All conduit item IDs in registration order. */
    public static final String[] ALL_CONDUIT_IDS = {
            ENERGY_CONDUIT_BASIC, ENERGY_CONDUIT_ADVANCED, ENERGY_CONDUIT_ELITE, ENERGY_CONDUIT_ULTIMATE,
            FLUID_CONDUIT_BASIC, FLUID_CONDUIT_ADVANCED, FLUID_CONDUIT_ELITE, FLUID_CONDUIT_ULTIMATE,
            ITEM_CONDUIT_BASIC, ITEM_CONDUIT_ADVANCED, ITEM_CONDUIT_ELITE, ITEM_CONDUIT_ULTIMATE,
            REDSTONE_CONDUIT,
    };

    private OmnIOItems() {
    }
}
