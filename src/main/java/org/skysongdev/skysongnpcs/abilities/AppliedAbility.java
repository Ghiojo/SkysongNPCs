package org.skysongdev.skysongnpcs.abilities;

import java.util.List;
import java.util.Map;

/**
 * What an ability did to one NPC, kept so it can be brought in line with abilities.yml or
 * undone: the ids of the modifiers it added, the proficiency tiers it raised, and how many
 * stat points and picks it gave.
 *
 * @param modifiers the SkysongStats ids of the modifiers it added, in the ability's order
 * @param tiers     proficiency id to the tier it raised it to and the tier it had before
 * @param points    stat points given and not taken back (spent ones stay given)
 * @param eligible  the stats those points were given for
 * @param picks     {@link AbilityDefinition.Picks#key()} to the picks given and not taken back
 */
public record AppliedAbility(List<String> modifiers, Map<String, RaisedTier> tiers, int points,
                             List<String> eligible, Map<String, Integer> picks) {
    public static final AppliedAbility NOTHING = new AppliedAbility(List.of(), Map.of(), 0, List.of(), Map.of());

    public AppliedAbility {
        modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        tiers = tiers == null ? Map.of() : Map.copyOf(tiers);
        eligible = eligible == null ? List.of() : List.copyOf(eligible);
        picks = picks == null ? Map.of() : Map.copyOf(picks);
    }

    /** A proficiency the ability raised: to {@code tier}, from {@code previous}. */
    public record RaisedTier(String tier, String previous) {
    }
}
