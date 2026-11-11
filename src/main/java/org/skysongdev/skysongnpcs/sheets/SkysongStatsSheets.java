package org.skysongdev.skysongnpcs.sheets;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongstats.api.CharacterId;
import org.skysongdev.skysongstats.api.Modifier;
import org.skysongdev.skysongstats.api.Proficiency;
import org.skysongdev.skysongstats.api.SheetLayout;
import org.skysongdev.skysongstats.api.SkysongStatsApi;
import org.skysongdev.skysongstats.api.Stat;
import org.skysongdev.skysongstats.api.StatAction;
import org.skysongdev.skysongstats.api.Tier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

/** {@link StatSheets} through the SkysongStats API (1.4.0): NPC sheets are plugin characters. */
public final class SkysongStatsSheets implements StatSheets {
    private final Plugin plugin;

    public SkysongStatsSheets(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The NPC's character id in SkysongStats. */
    public static CharacterId character(UUID npc) {
        return new CharacterId(npc, PROFILE);
    }

    @Override
    public boolean exists(UUID npc) {
        return stats().characters().owner(character(npc)).map(plugin.getName()::equals).orElse(false);
    }

    @Override
    public boolean create(UUID npc, Map<String, Integer> values) {
        Map<Stat, Integer> starting = new LinkedHashMap<>();
        values.forEach((id, value) -> starting.put(stats().stats().find(id)
                .orElseThrow(() -> new IllegalArgumentException("SkysongStats has no stat " + id)), value));
        return stats().characters().create(plugin, character(npc), starting);
    }

    @Override
    public Optional<String> statId(String idOrAlias) {
        return stats().stats().find(idOrAlias).map(Stat::id);
    }

    @Override
    public List<String> statIds() {
        return stats().stats().all().stream().map(Stat::id).toList();
    }

    @Override
    public boolean setItems(UUID npc, List<ItemStack> items) {
        return stats().characters().setItems(character(npc), items);
    }

    @Override
    public Optional<List<Component>> render(UUID npc, List<String> lines, String name) {
        return lines.isEmpty()
                ? stats().sheets().render(character(npc), SheetLayout.STATS, name)
                : stats().sheets().render(character(npc), lines, name);
    }

    @Override
    public OptionalInt editStat(UUID npc, String stat, StatEdit edit, int amount) {
        StatAction action = switch (edit) {
            case SET -> StatAction.SET;
            case ADD -> StatAction.ADD;
            case REDUCE -> StatAction.REDUCE;
        };
        return stats().stats().apply(character(npc), stat, action, amount)
                .map(change -> OptionalInt.of(change.after()))
                .orElse(OptionalInt.empty());
    }

    @Override
    public List<ModifierLine> modifiers(UUID npc) {
        return stats().modifiers().list(character(npc)).stream().map(SkysongStatsSheets::line).toList();
    }

    @Override
    public Optional<ModifierLine> addModifier(UUID npc, String target, int amount) {
        return stats().modifiers().add(character(npc), target, amount).map(SkysongStatsSheets::line);
    }

    @Override
    public boolean removeModifier(UUID npc, String modifierId) {
        return stats().modifiers().remove(character(npc), modifierId);
    }

    @Override
    public List<String> proficiencyIds() {
        return stats().proficiencies().all().stream().map(Proficiency::id).toList();
    }

    @Override
    public List<String> tierIds() {
        return stats().proficiencies().tiers().stream().map(Tier::id).toList();
    }

    @Override
    public ProficiencyResult setProficiency(UUID npc, String proficiency, String tier) {
        if (!exists(npc)) {
            return ProficiencyResult.NO_SHEET;
        }
        Optional<Proficiency> found = stats().proficiencies().find(proficiency);
        if (found.isEmpty()) {
            return ProficiencyResult.UNKNOWN_PROFICIENCY;
        }
        Optional<Tier> foundTier = stats().proficiencies().findTier(tier);
        if (foundTier.isEmpty()) {
            return ProficiencyResult.UNKNOWN_TIER;
        }
        stats().proficiencies().setTier(character(npc), found.get(), foundTier.get());
        return ProficiencyResult.DONE;
    }

    // M16: abilities

    @Override
    public Optional<String> proficiencyTier(UUID npc, String proficiency) {
        if (!exists(npc)) {
            return Optional.empty();
        }
        return stats().proficiencies().find(proficiency)
                .flatMap(found -> stats().proficiencies().tier(character(npc), found))
                .map(Tier::id);
    }

    private static ModifierLine line(Modifier modifier) {
        return new ModifierLine(modifier.id(), modifier.target(), modifier.amount());
    }

    private static SkysongStatsApi stats() {
        return SkysongStatsApi.get();
    }
}
