package org.skysongdev.skysongnpcs.commands;

/** The permission nodes the commands check; plugin.yml declares them with their defaults. */
public final class Permissions {
    /** Staff: every NPC, and /snpc reload. */
    public static final String ADMIN = "skysongnpcs.admin";

    // M17
    /** Placing NPCs from blueprint items. */
    public static final String PLACE = "skysongnpcs.place";

    private Permissions() {
    }
}
