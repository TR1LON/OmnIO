package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;

/**
 * Stub: Item registrations for OmnIO.
 * Will contain conduit items (one per type per tier), facade items, wrench, etc.
 */
public final class OmnIOItems {

    private OmnIOItems() {
    }

    /**
     * Called during mod init to register all items.
     */
    public static void register() {
        Constants.LOG.debug("Registering items");
        // TODO: Register conduit items (energy basic/adv/elite/ult, fluid, item, redstone)
        // TODO: Register facade item
        // TODO: Register wrench / yeta wrench equivalent
    }
}
