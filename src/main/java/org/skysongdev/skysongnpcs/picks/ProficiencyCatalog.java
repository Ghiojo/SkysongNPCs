package org.skysongdev.skysongnpcs.picks;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What proficiency picks ask SkysongStats: its skills, proficiencies and tiers, and an NPC's
 * tiers. {@link SkysongStatsProficiencyCatalog} is the real one; tests use their own, since
 * SkysongStats is not on the test classpath. It speaks in ids, so nothing outside it needs
 * SkysongStats' classes. Names are plain text, for message placeholders.
 */
public interface ProficiencyCatalog {

    /** A skill's id from its id or alias. */
    Optional<String> skillId(String idOrAlias);

    /** Every skill's id, in skills.yml order. */
    List<String> skillIds();

    /** A tier's id from its id. */
    Optional<String> tierId(String id);

    /** Every tier's id, lowest first. */
    List<String> tierIds();

    /** A proficiency's id from its id or alias. */
    Optional<String> proficiencyId(String idOrAlias);

    /** The skill a proficiency belongs to. */
    Optional<String> skillOf(String proficiency);

    /** The proficiencies of a skill, in proficiencies.yml order; empty for an unknown skill. */
    List<String> proficienciesOf(String skill);

    String skillName(String skill);

    String proficiencyName(String proficiency);

    String tierName(String tier);

    /** The NPC's tier in a proficiency; empty when it has no sheet or the proficiency is unknown. */
    Optional<String> tier(UUID npc, String proficiency);

    /** Sets the NPC's tier in a proficiency; false when it has no sheet or an id is unknown. */
    boolean setTier(UUID npc, String proficiency, String tier);

    /** Where a tier sits in {@link #tierIds()}, -1 when it is unknown. */
    default int rank(String tier) {
        return tierIds().indexOf(tier);
    }
}
