package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * What the plugin asks SkysongStats about NPC stat sheets. An NPC's sheet is the plugin
 * character {@code CharacterId(npc, "npc")} owned by SkysongNPCs. {@link SkysongStatsSheets}
 * is the real one; tests use their own, since SkysongStats is not on the test classpath. It
 * speaks in ids and plain values so that nothing outside it needs SkysongStats' classes.
 */
public interface StatSheets {
    /** The profile part of every NPC's character id. */
    String PROFILE = "npc";

    /** Whether the NPC has a sheet: a character of this plugin. */
    boolean exists(UUID npc);

    /**
     * Creates the NPC's sheet.
     *
     * @param stats starting base values by stat id; the others start at their stats.yml value
     * @return false when the id is already a character, and nothing changed
     */
    boolean create(UUID npc, Map<String, Integer> stats);

    /** A stat's id from its id or alias. */
    Optional<String> statId(String idOrAlias);

    /** Every stat's id, in stats.yml order. */
    List<String> statIds();

    /** Counts the items an NPC carries toward the item-tag stats (Arcrot...). */
    boolean setItems(UUID npc, List<ItemStack> items);

    /**
     * The sheet as chat lines.
     *
     * @param lines the lines with SkysongStats' sheet placeholders, or empty for SkysongStats'
     *              own stats sheet
     * @param name  what {@code {player}} shows
     * @return empty when the NPC has no sheet
     */
    Optional<List<Component>> render(UUID npc, List<String> lines, String name);

    /**
     * Changes a stat like SkysongStats' stat command; for a bar it changes the current value.
     *
     * @param amount 0 or more, except for {@link StatEdit#SET}
     * @return the stat's total after the change; empty when the NPC has no sheet or the
     *         stat does not exist
     */
    OptionalInt editStat(UUID npc, String stat, StatEdit edit, int amount);

    /** The sheet's modifiers, oldest first. */
    List<ModifierLine> modifiers(UUID npc);

    /** Adds a modifier; empty when the NPC has no sheet or the target is no stat. */
    Optional<ModifierLine> addModifier(UUID npc, String target, int amount);

    /** Removes a modifier; false when the sheet has no modifier with that id. */
    boolean removeModifier(UUID npc, String modifierId);

    /** Every proficiency's id. */
    List<String> proficiencyIds();

    /** Every tier's id, lowest first. */
    List<String> tierIds();

    /** Sets a proficiency's tier. */
    ProficiencyResult setProficiency(UUID npc, String proficiency, String tier);

    /** How {@link #editStat} changes a base value. */
    enum StatEdit { SET, ADD, REDUCE }

    /** One modifier on a sheet. */
    record ModifierLine(String id, String target, int amount) {
    }

    /** What {@link #setProficiency} did. */
    enum ProficiencyResult { DONE, NO_SHEET, UNKNOWN_PROFICIENCY, UNKNOWN_TIER }
}
