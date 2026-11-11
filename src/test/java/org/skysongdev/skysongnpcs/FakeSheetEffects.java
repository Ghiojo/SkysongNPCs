package org.skysongdev.skysongnpcs;

import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryAdjustment;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPackage;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;
import org.skysongdev.skysongstats.api.ConditionType;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.InjuryType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * SkysongStats' injuries, conditions and ancestries for tests:
 * <ul>
 *     <li>injuries {@code broken_arm} ("Broken Arm", major, gives {@code bleeding} 2) and
 *     {@code bruise} ("<red>Bruise</red>", minor);</li>
 *     <li>conditions {@code bleeding} ("Bleeding") and {@code tired} ("Tired");</li>
 *     <li>ancestries {@code elf} ("Elf", dex +2, then choice 1: +1 to one of str/con, and
 *     choice 2: one of two packages) and {@code human} ("Human", no choices, str +1).</li>
 * </ul>
 * Picks are checked loosely: every choice needs a pick of the right kind and count.
 */
public final class FakeSheetEffects implements SheetEffects {
    private static final List<InjuryType> INJURIES = List.of(
            new InjuryType("broken_arm", "Broken Arm", "", "BONE", "major", "", List.of(), Map.of("bleeding", 2)),
            new InjuryType("bruise", "<red>Bruise</red>", "", "PAPER", "minor", "", List.of(), Map.of()));
    private static final List<ConditionType> CONDITIONS = List.of(
            new ConditionType("bleeding", "Bleeding", "", "PAPER", List.of(), Map.of()),
            new ConditionType("tired", "Tired", "", "PAPER", List.of(), Map.of()));
    private static final List<Ancestry> ANCESTRIES = List.of(
            new Ancestry("elf", "Elf", "STONE", List.of(), "skysongstats.ancestry.elf",
                    List.of(new AncestryAdjustment("dex", 2)),
                    List.of(new AncestryChoice.Stats("Boost", 1, 1, List.of("str", "con"), false, false),
                            new AncestryChoice.Packages("Heritage", List.of(
                                    new AncestryPackage("Woodland", "OAK_LOG", List.of(new AncestryAdjustment("wis", 1))),
                                    new AncestryPackage("Cave", "STONE", List.of(new AncestryAdjustment("con", 1)))),
                                    false))),
            new Ancestry("human", "Human", "STONE", List.of(), "", List.of(new AncestryAdjustment("str", 1)), List.of()));

    private final Map<UUID, List<Injury>> injuries = new HashMap<>();
    private final Map<UUID, Set<String>> known = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> direct = new HashMap<>();
    private final Map<UUID, CharacterAncestry> ancestries = new HashMap<>();

    @Override
    public List<InjuryType> injuryTypes() {
        return INJURIES;
    }

    @Override
    public Optional<InjuryType> injuryType(String id) {
        return INJURIES.stream().filter(type -> type.id().equalsIgnoreCase(id)).findFirst();
    }

    @Override
    public List<Injury> injuries(UUID npc) {
        return List.copyOf(injuries.getOrDefault(npc, List.of()));
    }

    @Override
    public Set<String> knownInjuries(UUID npc) {
        return Set.copyOf(known.getOrDefault(npc, Set.of()));
    }

    @Override
    public boolean awardInjury(UUID npc, String injury) {
        Optional<InjuryType> type = injuryType(injury);
        if (type.isEmpty()) {
            return false;
        }
        List<Injury> current = injuries.computeIfAbsent(npc, id -> new ArrayList<>());
        current.removeIf(had -> had.typeId().equals(type.get().id()));
        current.add(new Injury(type.get().id(), Instant.now()));
        known.computeIfAbsent(npc, id -> new LinkedHashSet<>()).add(type.get().id());
        return true;
    }

    @Override
    public boolean removeInjury(UUID npc, String injury) {
        return injuries.getOrDefault(npc, new ArrayList<>()).removeIf(had -> had.typeId().equalsIgnoreCase(injury));
    }

    @Override
    public boolean discoverInjury(UUID npc, String injury) {
        return injuryType(injury).map(type -> known.computeIfAbsent(npc, id -> new LinkedHashSet<>()).add(type.id()))
                .orElse(false);
    }

    @Override
    public boolean forgetInjury(UUID npc, String injury) {
        String id = injury.toLowerCase(Locale.ROOT);
        if (injuries.getOrDefault(npc, List.of()).stream().anyMatch(had -> had.typeId().equals(id))) {
            return false;
        }
        return known.getOrDefault(npc, new LinkedHashSet<>()).remove(id);
    }

    @Override
    public List<ConditionType> conditionTypes() {
        return CONDITIONS;
    }

    @Override
    public Optional<ConditionType> conditionType(String id) {
        return CONDITIONS.stream().filter(type -> type.id().equalsIgnoreCase(id)).findFirst();
    }

    @Override
    public List<ActiveCondition> conditions(UUID npc) {
        List<ActiveCondition> current = new ArrayList<>();
        for (ConditionType type : CONDITIONS) {
            Integer level = direct.getOrDefault(npc, Map.of()).get(type.id());
            List<String> from = new ArrayList<>();
            int highest = level == null ? 0 : level;
            for (Injury injury : injuries.getOrDefault(npc, List.of())) {
                Integer given = injuryType(injury.typeId()).orElseThrow().conditions().get(type.id());
                if (given != null) {
                    from.add(injury.typeId());
                    highest = Math.max(highest, given);
                }
            }
            if (highest > 0) {
                current.add(new ActiveCondition(type.id(), highest, level != null, from, List.of()));
            }
        }
        return current;
    }

    @Override
    public Optional<ActiveCondition> awardCondition(UUID npc, String condition, int level) {
        Optional<ConditionType> type = conditionType(condition);
        if (type.isEmpty()) {
            return Optional.empty();
        }
        direct.computeIfAbsent(npc, id -> new LinkedHashMap<>()).put(type.get().id(), level);
        return conditions(npc).stream().filter(active -> active.typeId().equals(type.get().id())).findFirst();
    }

    @Override
    public boolean removeCondition(UUID npc, String condition) {
        return direct.getOrDefault(npc, new HashMap<>()).remove(condition.toLowerCase(Locale.ROOT)) != null;
    }

    @Override
    public List<Ancestry> ancestries() {
        return ANCESTRIES;
    }

    @Override
    public Optional<Ancestry> ancestry(String idOrName) {
        return ANCESTRIES.stream().filter(ancestry -> ancestry.id().equalsIgnoreCase(idOrName)
                || ancestry.name().equalsIgnoreCase(idOrName)).findFirst();
    }

    @Override
    public List<String> ancestryProblems(Ancestry ancestry, AncestryPicks picks) {
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < ancestry.choices().size(); i++) {
            AncestryChoice choice = ancestry.choices().get(i);
            if (choice instanceof AncestryChoice.Stats stats) {
                Optional<List<String>> picked = picks.stats(i);
                if (picked.isEmpty() || picked.get().size() != stats.picks()
                        || !stats.from().containsAll(picked.get())) {
                    problems.add("choice " + (i + 1) + " needs " + stats.picks() + " of " + stats.from());
                }
            } else if (picks.option(i).isEmpty()) {
                problems.add("choice " + (i + 1) + " needs a package");
            }
        }
        return problems;
    }

    @Override
    public Optional<CharacterAncestry> ancestryOf(UUID npc) {
        return Optional.ofNullable(ancestries.get(npc));
    }

    @Override
    public boolean setAncestry(UUID npc, Ancestry ancestry, AncestryPicks picks) {
        List<String> problems = ancestryProblems(ancestry, picks);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        ancestries.put(npc, new CharacterAncestry(ancestry.id(), ancestry.name(), false, given(ancestry, picks)));
        return true;
    }

    @Override
    public boolean setAncestry(UUID npc, Ancestry ancestry) {
        ancestries.put(npc, new CharacterAncestry(ancestry.id(), ancestry.name(), ancestry.hasChoices(),
                given(ancestry, null)));
        return true;
    }

    @Override
    public boolean chooseAncestry(UUID npc, AncestryPicks picks) {
        CharacterAncestry current = ancestries.get(npc);
        if (current == null || !current.choicesPending()) {
            return false;
        }
        return setAncestry(npc, ancestry(current.id()).orElseThrow(), picks);
    }

    @Override
    public boolean rechooseAncestry(UUID npc) {
        CharacterAncestry current = ancestries.get(npc);
        if (current == null || !ancestry(current.id()).orElseThrow().hasChoices()) {
            return false;
        }
        return setAncestry(npc, ancestry(current.id()).orElseThrow());
    }

    @Override
    public boolean removeAncestry(UUID npc) {
        return ancestries.remove(npc) != null;
    }

    /** What the ancestry gives: its fixed adjustments, then the picks' when there are picks. */
    private static List<CharacterAncestry.Given> given(Ancestry ancestry, AncestryPicks picks) {
        List<CharacterAncestry.Given> given = new ArrayList<>();
        ancestry.adjustments().forEach(adjustment -> given.add(new CharacterAncestry.Given(adjustment.stat(),
                adjustment.amount(), -1)));
        if (picks == null) {
            return given;
        }
        for (int i = 0; i < ancestry.choices().size(); i++) {
            int choice = i;
            if (ancestry.choices().get(i) instanceof AncestryChoice.Stats stats) {
                picks.stats(i).orElseThrow().forEach(stat -> given.add(new CharacterAncestry.Given(stat,
                        stats.amount(), choice)));
            } else if (ancestry.choices().get(i) instanceof AncestryChoice.Packages packages) {
                packages.options().get(picks.option(i).orElseThrow()).adjustments().forEach(adjustment ->
                        given.add(new CharacterAncestry.Given(adjustment.stat(), adjustment.amount(), choice)));
            }
        }
        return given;
    }
}
