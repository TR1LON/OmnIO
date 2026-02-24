package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;

/**
 * Stub: Block entity registrations for OmnIO.
 * Will contain the OmniConduitBlockEntity type.
 */
public final class OmnIOBlockEntities {

    private OmnIOBlockEntities() {
    }

    /**
     * Called during mod init to register all block entity types.
     */
    public static void register() {
        Constants.LOG.debug("Registering block entities");
        // TODO: Register CONDUIT_BUNDLE block entity type
    }
}
