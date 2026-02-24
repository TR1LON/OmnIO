package com.trilon.omnio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants for OmnIO across all loader platforms.
 */
public final class Constants {

    public static final String MOD_ID = "omnio";
    public static final String MOD_NAME = "OmnIO";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    /**
     * Maximum number of conduit types that can coexist in a single bundle block.
     */
    public static final int MAX_CONDUITS_PER_BUNDLE = 9;

    private Constants() {
    }
}
