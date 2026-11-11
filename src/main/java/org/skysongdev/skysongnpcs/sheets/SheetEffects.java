package org.skysongdev.skysongnpcs.sheets;

import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.ConditionType;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.InjuryType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What the plugin asks SkysongStats about an NPC sheet's injuries, conditions and ancestry.
 * {@link SkysongStatsEffects} is the real one; tests use their own. It uses SkysongStats'
 * own records, which are plain data and on the test classpath, but never calls
 * {@code SkysongStatsApi.get()} outside the real implementation.
 *
 * <p>Every write expects the NPC to have a sheet ({@link StatSheets#exists(UUID)}); callers
 * check that first. Writes behave like SkysongStats' own commands: saved straight away, the
 * stats follow, and SkysongStats' events fire.
 */
public interface SheetEffects {

    // Injuries

    /** Every injury of injuries.yml, in file order. */
    List<InjuryType> injuryTypes();

    /** The injury with this id, ignoring case. */
    Optional<InjuryType> injuryType(String id);

    /** The NPC's current injuries, oldest first. */
    List<Injury> injuries(UUID npc);

    /** The ids of the injuries the NPC knows, in the order it learnt them. */
    Set<String> knownInjuries(UUID npc);

    /** Gives the NPC an injury, like {@code /injury award}; false when there is no such injury. */
    boolean awardInjury(UUID npc, String injury);

    /** Takes an injury away; false when the NPC did not have it. */
    boolean removeInjury(UUID npc, String injury);

    /** Teaches the NPC an injury without giving it; false when known already or no such injury. */
    boolean discoverInjury(UUID npc, String injury);

    /** Undoes a discover; false when the NPC did not know it or has it now. */
    boolean forgetInjury(UUID npc, String injury);

    // Conditions

    /** Every condition of conditions.yml, in file order. */
    List<ConditionType> conditionTypes();

    /** The condition with this id, ignoring case. */
    Optional<ConditionType> conditionType(String id);

    /** The NPC's current conditions: direct ones and those its injuries and conditions give. */
    List<ActiveCondition> conditions(UUID npc);

    /**
     * Gives the NPC a condition directly; giving it again changes the level.
     *
     * @param level 1 or more
     * @return the condition as the NPC has it now; empty when there is no such condition
     */
    Optional<ActiveCondition> awardCondition(UUID npc, String condition, int level);

    /** Takes back a direct award; false when the NPC had no direct award of it. */
    boolean removeCondition(UUID npc, String condition);

    // Ancestry

    /** Every ancestry of ancestries.yml, in file order, with its choices. */
    List<Ancestry> ancestries();

    /** The ancestry with this id or name, ignoring case and formatting. */
    Optional<Ancestry> ancestry(String idOrName);

    /** Why the picks don't fit the ancestry's choices, one entry per problem; empty when they fit. */
    List<String> ancestryProblems(Ancestry ancestry, AncestryPicks picks);

    /** The NPC's ancestry; empty when it has none. */
    Optional<CharacterAncestry> ancestryOf(UUID npc);

    /**
     * Gives the NPC the ancestry with its choices made, taking off what the old one gave.
     *
     * @return false when the ancestry has left ancestries.yml
     * @throws IllegalArgumentException when the picks don't fit, naming every problem
     */
    boolean setAncestry(UUID npc, Ancestry ancestry, AncestryPicks picks);

    /**
     * Gives the NPC the ancestry with its fixed adjustments only, its choices left pending
     * for {@link #chooseAncestry}.
     *
     * @return false when the ancestry has left ancestries.yml
     */
    boolean setAncestry(UUID npc, Ancestry ancestry);

    /**
     * Makes the NPC's pending ancestry choices.
     *
     * @return false when no choices are pending
     * @throws IllegalArgumentException when the picks don't fit, naming every problem
     */
    boolean chooseAncestry(UUID npc, AncestryPicks picks);

    /**
     * Takes off what the choices gave and makes them pending again; fixed adjustments stay.
     *
     * @return false when the NPC has no ancestry or it has no choices
     */
    boolean rechooseAncestry(UUID npc);

    /** Takes off everything the ancestry gave; false when the NPC had none. */
    boolean removeAncestry(UUID npc);
}
