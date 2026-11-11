package org.skysongdev.skysongnpcs.commands;

/** The permission nodes the commands check; plugin.yml declares them with their defaults. */
public final class Permissions {
    /** Staff: every NPC, and /snpc reload. */
    public static final String ADMIN = "skysongnpcs.admin";

    // M17
    /** Placing NPCs from blueprint items. */
    public static final String PLACE = "skysongnpcs.place";

    // M4
    /** Making player groups with /snpc group. */
    public static final String GROUPS = "skysongnpcs.groups";

    // M12
    /** DMs: award and remove injuries and conditions on NPC sheets. */
    public static final String INJURIES = "skysongnpcs.injuries";

    private Permissions() {
    }
}
