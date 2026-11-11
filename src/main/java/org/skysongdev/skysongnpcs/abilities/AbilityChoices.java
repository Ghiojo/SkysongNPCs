package org.skysongdev.skysongnpcs.abilities;

import java.util.List;
import java.util.UUID;

/**
 * Where abilities put the stat points and proficiency picks they give: the NPC's pending
 * choices. Each grant carries a source, so what an ability gave can be counted and taken
 * back without touching what staff, templates or other plugins gave.
 */
public interface AbilityChoices {

    /**
     * Gives stat points.
     *
     * @param eligible the stats they may go to; empty for the NPC's usual ones
     */
    void grantPoints(UUID npc, String source, int amount, List<String> eligible);

    /** Takes back up to {@code max} of the source's unspent stat points; answers how many it took. */
    int takeUnspentPoints(UUID npc, String source, int max);

    /** Gives {@code count} picks of a skill's proficiencies, each to be set to {@code tier}. */
    void grantPicks(UUID npc, String source, String skill, String tier, int count);

    /** Takes back up to {@code max} of the source's unspent picks; answers how many it took. */
    int takeUnspentPicks(UUID npc, String source, int max);
}
