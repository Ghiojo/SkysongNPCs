package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.Material;
import org.skysongdev.skysongnpcs.api.ability.NpcAbility;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One ability as abilities.yml defines it: how it looks and what it does to an NPC.
 *
 * @param id          its key in abilities.yml
 * @param name        its one-word name, MiniMessage
 * @param description its hover text, MiniMessage; lines are separated by {@code \n}
 * @param icon        the item the abilities menu draws it with
 * @param modifiers   saved SkysongStats modifiers it adds, in the file's order
 * @param points      extra stat points it gives, or {@link Points#NONE}
 * @param picks       proficiency picks it gives
 * @param tiers       proficiency id to the tier the NPC has at least while it has the ability
 */
public record AbilityDefinition(String id, String name, String description, Material icon,
                                List<Modifier> modifiers, Points points, List<Picks> picks,
                                Map<String, String> tiers) {

    public AbilityDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        modifiers = List.copyOf(modifiers);
        Objects.requireNonNull(points, "points");
        picks = List.copyOf(picks);
        tiers = Map.copyOf(tiers);
    }

    /** What the API shows of it. */
    public NpcAbility view() {
        return new NpcAbility(id, name, description, icon);
    }

    /**
     * A modifier the ability adds.
     *
     * @param target a stat id, or {@code max_<stat>} for a bar's max
     */
    public record Modifier(String target, int amount) {
    }

    /**
     * Stat points the ability gives.
     *
     * @param eligible the stat ids they may go to; empty for the NPC's usual ones
     */
    public record Points(int amount, List<String> eligible) {
        public static final Points NONE = new Points(0, List.of());

        public Points {
            eligible = List.copyOf(eligible);
        }
    }

    /** Proficiency picks the ability gives: {@code count} proficiencies of a skill, each set to {@code tier}. */
    public record Picks(String skill, String tier, int count) {

        /** Tells this pick grant apart from the ability's others. */
        public String key() {
            return skill + "/" + tier;
        }
    }
}
