package com.trilon.omnio.registry;

import com.trilon.omnio.Constants;
import com.trilon.omnio.content.conduit.ConduitMenu;

import net.minecraft.world.inventory.MenuType;

/**
 * Common static references for menu types.
 * Populated by platform-specific registration (NeoForge / Fabric).
 */
public final class OmnIOMenuTypes {

    /** The conduit configuration menu type. Set by platform registration. */
    public static MenuType<ConduitMenu> CONDUIT_MENU;

    private OmnIOMenuTypes() {
    }

    /**
     * Set the conduit menu type. Called from platform registration code.
     */
    public static void setConduitMenu(MenuType<ConduitMenu> type) {
        CONDUIT_MENU = type;
    }

    /**
     * Called during mod init to register all menu types.
     */
    public static void register() {
        Constants.LOG.debug("Registering menu types");
    }
}
