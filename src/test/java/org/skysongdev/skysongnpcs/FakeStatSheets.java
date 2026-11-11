package org.skysongdev.skysongnpcs;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.sheets.StatSheets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * SkysongStats for tests: two stats ({@code strength}, alias {@code str}, and {@code hp}, a
 * bar), each starting at 0, and the tiers {@code untrained} and {@code expert} of one
 * proficiency, {@code swords}. It records what the plugin sends.
 */
public final class FakeStatSheets implements StatSheets {
    private final Map<UUID, Map<String, Integer>> sheets = new HashMap<>();
    private final Map<UUID, List<ModifierLine>> modifiers = new HashMap<>();
    private final Map<UUID, Map<String, String>> tiers = new HashMap<>();
    /** Every setItems call, in order. */
    public final List<List<ItemStack>> itemCalls = new ArrayList<>();
    private int nextModifier = 1;

    /** A stat's base value on an NPC's sheet. */
    public int value(UUID npc, String stat) {
        return sheets.get(npc).get(stat);
    }

    /** M14: a stat's base value, empty when the NPC has no sheet or the stat does not exist. */
    public java.util.OptionalInt base(UUID npc, String stat) {
        Map<String, Integer> values = sheets.get(npc);
        Optional<String> id = statId(stat);
        return values == null || id.isEmpty() ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(values.get(id.get()));
    }

    /** A proficiency's tier on an NPC's sheet, null when never set. */
    public String tier(UUID npc, String proficiency) {
        return tiers.getOrDefault(npc, Map.of()).get(proficiency);
    }

    @Override
    public boolean exists(UUID npc) {
        return sheets.containsKey(npc);
    }

    @Override
    public boolean create(UUID npc, Map<String, Integer> stats) {
        if (sheets.containsKey(npc)) {
            return false;
        }
        Map<String, Integer> values = new LinkedHashMap<>();
        statIds().forEach(id -> values.put(id, 0));
        values.putAll(stats);
        sheets.put(npc, values);
        return true;
    }

    @Override
    public Optional<String> statId(String idOrAlias) {
        return switch (idOrAlias.toLowerCase()) {
            case "strength", "str" -> Optional.of("strength");
            case "hp" -> Optional.of("hp");
            default -> Optional.empty();
        };
    }

    @Override
    public List<String> statIds() {
        return List.of("strength", "hp");
    }

    @Override
    public boolean setItems(UUID npc, List<ItemStack> items) {
        itemCalls.add(List.copyOf(items));
        return exists(npc);
    }

    /** One line per stat, {@code <name>: <stat> <value>}, whatever lines are asked for. */
    @Override
    public Optional<List<Component>> render(UUID npc, List<String> lines, String name) {
        if (!exists(npc)) {
            return Optional.empty();
        }
        List<Component> rendered = new ArrayList<>();
        sheets.get(npc).forEach((stat, value) -> rendered.add(Component.text(name + ": " + stat + " " + value)));
        return Optional.of(rendered);
    }

    @Override
    public OptionalInt editStat(UUID npc, String stat, StatEdit edit, int amount) {
        Optional<String> id = statId(stat);
        if (!exists(npc) || id.isEmpty()) {
            return OptionalInt.empty();
        }
        Map<String, Integer> values = sheets.get(npc);
        int value = switch (edit) {
            case SET -> amount;
            case ADD -> values.get(id.get()) + amount;
            case REDUCE -> values.get(id.get()) - amount;
        };
        values.put(id.get(), value);
        return OptionalInt.of(value);
    }

    @Override
    public List<ModifierLine> modifiers(UUID npc) {
        return List.copyOf(modifiers.getOrDefault(npc, List.of()));
    }

    @Override
    public Optional<ModifierLine> addModifier(UUID npc, String target, int amount) {
        String stat = target.startsWith("max_") ? target.substring(4) : target;
        if (!exists(npc) || statId(stat).isEmpty()) {
            return Optional.empty();
        }
        ModifierLine line = new ModifierLine(String.valueOf(nextModifier++), target, amount);
        modifiers.computeIfAbsent(npc, ignored -> new ArrayList<>()).add(line);
        return Optional.of(line);
    }

    @Override
    public boolean removeModifier(UUID npc, String modifierId) {
        return modifiers.getOrDefault(npc, new ArrayList<>()).removeIf(line -> line.id().equals(modifierId));
    }

    @Override
    public List<String> proficiencyIds() {
        return List.of("swords");
    }

    @Override
    public List<String> tierIds() {
        return List.of("untrained", "expert");
    }

    @Override
    public ProficiencyResult setProficiency(UUID npc, String proficiency, String tier) {
        if (!exists(npc)) {
            return ProficiencyResult.NO_SHEET;
        }
        if (!proficiencyIds().contains(proficiency)) {
            return ProficiencyResult.UNKNOWN_PROFICIENCY;
        }
        if (!tierIds().contains(tier)) {
            return ProficiencyResult.UNKNOWN_TIER;
        }
        tiers.computeIfAbsent(npc, ignored -> new HashMap<>()).put(proficiency, tier);
        return ProficiencyResult.DONE;
    }
}
