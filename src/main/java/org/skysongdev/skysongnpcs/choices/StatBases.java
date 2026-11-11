package org.skysongdev.skysongnpcs.choices;

import java.util.OptionalInt;
import java.util.UUID;

/**
 * Reads a stat's base value on an NPC's sheet, which caps are checked against.
 * {@link SkysongStatsBases} is the real one; tests use their own.
 */
@FunctionalInterface
public interface StatBases {
    /** The stat's base value; empty when the NPC has no sheet or there is no such stat. */
    OptionalInt base(UUID npc, String stat);
}
