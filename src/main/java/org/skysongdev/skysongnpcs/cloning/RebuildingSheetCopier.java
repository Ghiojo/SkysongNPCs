package org.skysongdev.skysongnpcs.cloning;

import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.sheets.SkysongStatsSheets;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.CharacterId;
import org.skysongdev.skysongstats.api.CharacterSheet;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.Modifier;
import org.skysongdev.skysongstats.api.Proficiency;
import org.skysongdev.skysongstats.api.SheetField;
import org.skysongdev.skysongstats.api.Skill;
import org.skysongdev.skysongstats.api.SkysongStatsApi;
import org.skysongdev.skysongstats.api.Stat;
import org.skysongdev.skysongstats.api.StatAction;
import org.skysongdev.skysongstats.api.StatValues;
import org.skysongdev.skysongstats.api.Tier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link SheetCopier} for SkysongStats 1.4.0, which cannot copy a character: the copy is
 * rebuilt through the API, piece by piece. It gets the original's base values, ancestry,
 * modifiers, skill XP, proficiency tiers and focus, sheet fields, injuries (and which ones it
 * knows) and direct conditions.
 *
 * <p>Three things can't be copied exactly this way, and come back as gaps: injuries are
 * awarded again, so their award time is now; ancestry picks are read back from what they
 * gave, and when that is not possible the copy's choices are left pending; and an ancestry
 * that left ancestries.yml can't be given again, so only what it gave the base values is
 * copied. Rebuilding also fires SkysongStats' change events for the copy. Replace this class
 * with a call to {@code characters().copy} once SkysongStats has it.
 */
public final class RebuildingSheetCopier implements SheetCopier {
    /** What SkysongStats keeps in a sheet field nobody set. */
    static final String NOT_SET = "[Not Set]";

    private final Plugin plugin;
    private final Supplier<SkysongStatsApi> api;

    public RebuildingSheetCopier(Plugin plugin) {
        this(plugin, SkysongStatsApi::get);
    }

    RebuildingSheetCopier(Plugin plugin, Supplier<SkysongStatsApi> api) {
        this.plugin = plugin;
        this.api = api;
    }

    @Override
    public CopyResult copy(UUID from, UUID to) {
        SkysongStatsApi stats = api.get();
        CharacterId original = SkysongStatsSheets.character(from);
        CharacterId copy = SkysongStatsSheets.character(to);
        if (!ours(stats, original)) {
            return CopyResult.of(Outcome.NO_SHEET);
        }

        Map<Stat, Integer> values = new LinkedHashMap<>();
        Map<Stat, Integer> bars = new LinkedHashMap<>();
        for (Stat stat : stats.stats().all()) {
            stats.stats().get(original, stat).map(StatValues::base)
                    .ifPresent(base -> (stat.isBar() ? bars : values).put(stat, base));
        }
        if (!stats.characters().create(plugin, copy, values)) {
            return CopyResult.of(Outcome.TAKEN);
        }

        List<String> gaps = new ArrayList<>();
        boolean hasAncestry = copyAncestry(stats, original, copy, gaps);
        // The original's base values already hold what its ancestry gave, so they are set
        // again over whatever giving the ancestry did.
        setBases(stats, copy, values);
        for (Modifier modifier : stats.modifiers().list(original)) {
            stats.modifiers().add(copy, modifier.target(), modifier.amount());
        }
        copySkillsAndProficiencies(stats, original, copy);
        copySheetFields(stats, original, copy, hasAncestry);
        copyInjuriesAndConditions(stats, original, copy, gaps);
        // Last: a bar's max comes from the other stats and the modifiers, and caps its value.
        setBases(stats, copy, bars);
        return new CopyResult(Outcome.COPIED, gaps);
    }

    @Override
    public boolean delete(UUID npc) {
        return api.get().characters().delete(plugin, SkysongStatsSheets.character(npc));
    }

    private boolean ours(SkysongStatsApi stats, CharacterId character) {
        return stats.characters().owner(character).map(plugin.getName()::equals).orElse(false);
    }

    /** @return whether the copy has the ancestry now */
    private static boolean copyAncestry(SkysongStatsApi stats, CharacterId original, CharacterId copy,
                                        List<String> gaps) {
        Optional<CharacterAncestry> had = stats.ancestries().of(original);
        if (had.isEmpty()) {
            return false;
        }
        Optional<Ancestry> ancestry = stats.ancestries().ancestry(had.get().id());
        if (ancestry.isEmpty()) {
            gaps.add("its ancestry " + had.get().id() + " is no longer in ancestries.yml, so the copy has none"
                    + " (its base values still include what the ancestry gave)");
            return false;
        }
        if (!ancestry.get().hasChoices()) {
            stats.ancestries().set(copy, ancestry.get(), AncestryPicks.none());
            return true;
        }
        if (had.get().choicesPending()) {
            // Same as the original: fixed adjustments on, choices still to make.
            stats.ancestries().set(copy, ancestry.get());
            return true;
        }
        Optional<AncestryPicks> picks = AncestryPicksReader.read(ancestry.get(), had.get())
                .filter(read -> stats.ancestries().problems(ancestry.get(), read).isEmpty());
        if (picks.isPresent()) {
            stats.ancestries().set(copy, ancestry.get(), picks.get());
        } else {
            stats.ancestries().set(copy, ancestry.get());
            gaps.add("its " + had.get().id() + " ancestry choices could not be read back, so the copy's are"
                    + " pending for staff to make again (its base values are copied exactly)");
        }
        return true;
    }

    private static void setBases(SkysongStatsApi stats, CharacterId copy, Map<Stat, Integer> bases) {
        bases.forEach((stat, base) -> {
            boolean differs = stats.stats().get(copy, stat).map(values -> values.base() != base).orElse(true);
            if (differs) {
                stats.stats().apply(copy, stat, StatAction.SET, base);
            }
        });
    }

    private static void copySkillsAndProficiencies(SkysongStatsApi stats, CharacterId original, CharacterId copy) {
        for (Skill skill : stats.skills().all()) {
            int xp = stats.skills().xp(original, skill).orElse(0);
            if (xp != stats.skills().xp(copy, skill).orElse(0)) {
                stats.skills().setXp(copy, skill, xp);
            }
        }
        for (Proficiency proficiency : stats.proficiencies().all()) {
            Optional<Tier> tier = stats.proficiencies().tier(original, proficiency);
            if (tier.isPresent() && !tier.equals(stats.proficiencies().tier(copy, proficiency))) {
                stats.proficiencies().setTier(copy, proficiency, tier.get());
            }
        }
        stats.proficiencies().focus(original).ifPresent(focus -> {
            stats.proficiencies().setFocus(copy, focus.proficiency(), true);
            if (focus.points() > 0) {
                stats.proficiencies().setPoints(copy, focus.points());
            }
        });
    }

    private static void copySheetFields(SkysongStatsApi stats, CharacterId original, CharacterId copy,
                                        boolean hasAncestry) {
        Optional<CharacterSheet> sheet = stats.sheets().get(original);
        if (sheet.isEmpty()) {
            return;
        }
        Map<SheetField, String> fields = new LinkedHashMap<>();
        fields.put(SheetField.NAME, sheet.get().name());
        fields.put(SheetField.AGE, sheet.get().age());
        fields.put(SheetField.GENDER, sheet.get().gender());
        fields.put(SheetField.PRONOUNS, sheet.get().pronouns());
        fields.put(SheetField.DESCRIPTION, sheet.get().description());
        // With an ancestry, SkysongStats fills the field itself.
        if (!hasAncestry) {
            fields.put(SheetField.ANCESTRY, sheet.get().ancestry());
        }
        fields.forEach((field, value) -> {
            if (!NOT_SET.equals(value)) {
                stats.sheets().set(copy, field, value);
            }
        });
    }

    private static void copyInjuriesAndConditions(SkysongStatsApi stats, CharacterId original, CharacterId copy,
                                                  List<String> gaps) {
        List<Injury> injuries = stats.injuries().current(original);
        for (Injury injury : injuries) {
            stats.injuries().award(copy, injury.typeId());
        }
        if (!injuries.isEmpty()) {
            gaps.add("its " + injuries.size() + " injur" + (injuries.size() == 1 ? "y was" : "ies were")
                    + " awarded to the copy again, so the copy's date from now");
        }
        Set<String> known = stats.injuries().known(original);
        for (String injury : known) {
            stats.injuries().discover(copy, injury);
        }
        for (String injury : Set.copyOf(stats.injuries().known(copy))) {
            if (!known.contains(injury)) {
                stats.injuries().forget(copy, injury);
            }
        }
        for (ActiveCondition condition : stats.conditions().current(original)) {
            if (condition.direct()) {
                stats.conditions().award(copy, condition.typeId(), condition.level());
            }
        }
    }
}
