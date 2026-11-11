package org.skysongdev.skysongnpcs.abilities;

import org.skysongdev.skysongnpcs.api.event.NpcAbilityChangeEvent;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.StatSheets;
import org.skysongdev.skysongnpcs.sheets.StatSheets.ModifierLine;
import org.skysongdev.skysongnpcs.storage.AbilityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Managed NPCs' abilities, and keeping what each one does to its NPC in line with
 * abilities.yml. Everything is cached in memory and written through to the store.
 *
 * <p>What an ability does is remembered per NPC ({@link AppliedAbility}), so that bringing it
 * in line touches only what it added:
 * <ul>
 *   <li>Modifiers: re-added when the ability's list differs from what the NPC has from it.</li>
 *   <li>Proficiency tiers: raised when below the ability's tier, and put back on removal
 *       only while still at the tier the ability set.</li>
 *   <li>Stat points and picks: the difference is given, or taken back while unspent.</li>
 * </ul>
 * An NPC is always brought in line as a whole, so two abilities that raise the same
 * proficiency agree.
 */
public final class AbilityService {
    /** The source of the choices an ability gives: {@code ability:<id>}, and {@code ability:<id>:<skill>/<tier>} for picks. */
    static final String SOURCE = "ability:";

    private final NpcRegistry registry;
    private final AbilityRegistry definitions;
    private final AbilityStore store;
    private final StatSheets sheets;
    private final AbilityChoices choices;
    private final Consumer<NpcAbilityChangeEvent> events;
    /** NPC to its abilities, in the order they were granted. */
    private final Map<UUID, Map<String, AppliedAbility>> abilities = new HashMap<>();

    public AbilityService(NpcRegistry registry, AbilityRegistry definitions, AbilityStore store, StatSheets sheets,
                          AbilityChoices choices, Consumer<NpcAbilityChangeEvent> events) {
        this.registry = registry;
        this.definitions = definitions;
        this.store = store;
        this.sheets = sheets;
        this.choices = choices;
        this.events = events;
    }

    /** Reads the saved abilities. Rows of NPCs that are no longer managed are dropped. */
    public void load() {
        abilities.clear();
        for (AbilityStore.StoredAbility row : store.loadAll()) {
            if (registry.isManaged(row.npc())) {
                abilities.computeIfAbsent(row.npc(), ignored -> new LinkedHashMap<>()).put(row.ability(), row.applied());
            } else {
                store.delete(row.npc(), row.ability());
            }
        }
    }

    public AbilityRegistry definitions() {
        return definitions;
    }

    /** The NPC's abilities that abilities.yml still defines, in the file's order. */
    public List<AbilityDefinition> of(UUID npc) {
        Set<String> owned = abilities.getOrDefault(npc, Map.of()).keySet();
        List<String> order = definitions.ids();
        return owned.stream()
                .map(definitions::get).flatMap(Optional::stream)
                .sorted(Comparator.comparingInt(definition -> order.indexOf(definition.id())))
                .toList();
    }

    public boolean has(UUID npc, String ability) {
        return abilities.getOrDefault(npc, Map.of()).containsKey(ability);
    }

    /** What the ability did to the NPC, for staff and tests. */
    public Optional<AppliedAbility> applied(UUID npc, String ability) {
        return Optional.ofNullable(abilities.getOrDefault(npc, Map.of()).get(ability));
    }

    /**
     * Gives a managed NPC an ability and applies it.
     *
     * @return false when it had it already
     * @throws IllegalArgumentException when the NPC is not managed or there is no such ability
     */
    public boolean grant(UUID npc, String ability) {
        Objects.requireNonNull(npc, "npc");
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        if (definitions.get(ability).isEmpty()) {
            throw new IllegalArgumentException("abilities.yml has no ability " + ability);
        }
        Map<String, AppliedAbility> owned = abilities.computeIfAbsent(npc, ignored -> new LinkedHashMap<>());
        if (owned.containsKey(ability)) {
            return false;
        }
        owned.put(ability, AppliedAbility.NOTHING);
        sync(npc);
        events.accept(new NpcAbilityChangeEvent(npc, ability, NpcAbilityChangeEvent.Change.GRANTED));
        return true;
    }

    /**
     * Takes an ability and its effects away from an NPC; spent points and picks stay.
     *
     * @return false when the NPC did not have it
     */
    public boolean revoke(UUID npc, String ability) {
        if (!remove(npc, ability)) {
            return false;
        }
        sync(npc);
        events.accept(new NpcAbilityChangeEvent(npc, ability, NpcAbilityChangeEvent.Change.REVOKED));
        return true;
    }

    /**
     * Brings every NPC in line with abilities.yml: on enable and after a reload. Abilities the
     * file no longer has are taken away from every NPC with their effects.
     */
    public void syncAll() {
        for (UUID npc : List.copyOf(abilities.keySet())) {
            List<String> gone = abilities.get(npc).keySet().stream()
                    .filter(ability -> definitions.get(ability).isEmpty()).toList();
            gone.forEach(ability -> remove(npc, ability));
            sync(npc);
            gone.forEach(ability -> events.accept(
                    new NpcAbilityChangeEvent(npc, ability, NpcAbilityChangeEvent.Change.REMOVED_FROM_FILE)));
        }
    }

    /** Brings one NPC's abilities in line with abilities.yml, saving what changed. */
    public void sync(UUID npc) {
        Map<String, AppliedAbility> owned = abilities.get(npc);
        if (owned == null) {
            return;
        }
        if (owned.isEmpty()) {
            abilities.remove(npc);
            return;
        }
        boolean sheet = sheets.exists(npc);
        List<String> tierOrder = sheet ? sheets.tierIds() : List.of();

        // Tiers first put back where the ability no longer wants them, then raised, so two
        // abilities on one proficiency end up at the higher tier.
        Map<String, Map<String, AppliedAbility.RaisedTier>> keptTiers = new HashMap<>();
        for (Map.Entry<String, AppliedAbility> entry : owned.entrySet()) {
            AbilityDefinition definition = definitions.get(entry.getKey()).orElseThrow();
            Map<String, AppliedAbility.RaisedTier> kept = new LinkedHashMap<>();
            entry.getValue().tiers().forEach((proficiency, raised) -> {
                if (sheet && raised.tier().equals(definition.tiers().get(proficiency))) {
                    kept.put(proficiency, raised);
                } else {
                    restore(npc, proficiency, raised);
                }
            });
            keptTiers.put(entry.getKey(), kept);
        }

        for (Map.Entry<String, AppliedAbility> entry : owned.entrySet()) {
            String ability = entry.getKey();
            AbilityDefinition definition = definitions.get(ability).orElseThrow();
            AppliedAbility before = entry.getValue();
            AppliedAbility after = new AppliedAbility(
                    sheet ? modifiers(npc, definition, before.modifiers()) : List.of(),
                    sheet ? raise(npc, definition, keptTiers.get(ability), tierOrder) : Map.of(),
                    0, List.of(), Map.of());
            after = points(npc, definition, before, after);
            after = picks(npc, definition, before, after);
            if (!after.equals(before)) {
                entry.setValue(after);
                store.save(npc, ability, after);
            }
        }
    }

    /** Forgets a deleted NPC's abilities. Its sheet and choices go with it elsewhere. */
    public void deleted(UUID npc) {
        if (abilities.remove(npc) != null) {
            store.deleteAll(npc);
        }
    }

    /**
     * Gives a copied NPC the original's abilities. Its sheet was copied before (M13), with every
     * modifier added again in the same order under new ids; the ability's modifiers are found
     * among them by target and amount, so nothing is added twice.
     */
    public void copied(UUID original, UUID copy) {
        Map<String, AppliedAbility> owned = abilities.get(original);
        if (owned == null || owned.isEmpty()) {
            return;
        }
        Map<String, ModifierLine> originalLines = new HashMap<>();
        sheets.modifiers(original).forEach(line -> originalLines.put(line.id(), line));
        List<ModifierLine> unclaimed = new ArrayList<>(sheets.modifiers(copy));

        Map<String, AppliedAbility> copied = new LinkedHashMap<>();
        owned.forEach((ability, applied) -> {
            List<String> ids = new ArrayList<>();
            for (String id : applied.modifiers()) {
                ModifierLine line = originalLines.get(id);
                if (line == null) {
                    continue;
                }
                for (int i = 0; i < unclaimed.size(); i++) {
                    ModifierLine candidate = unclaimed.get(i);
                    if (candidate.target().equals(line.target()) && candidate.amount() == line.amount()) {
                        ids.add(candidate.id());
                        unclaimed.remove(i);
                        break;
                    }
                }
            }
            AppliedAbility forCopy = new AppliedAbility(ids, applied.tiers(), applied.points(), applied.eligible(),
                    applied.picks());
            copied.put(ability, forCopy);
            store.save(copy, ability, forCopy);
        });
        abilities.put(copy, copied);
    }

    // What one ability does.

    private boolean remove(UUID npc, String ability) {
        Map<String, AppliedAbility> owned = abilities.get(npc);
        AppliedAbility applied = owned == null ? null : owned.remove(ability);
        if (applied == null) {
            return false;
        }
        if (sheets.exists(npc)) {
            applied.modifiers().forEach(id -> sheets.removeModifier(npc, id));
            applied.tiers().forEach((proficiency, raised) -> restore(npc, proficiency, raised));
        }
        if (applied.points() > 0) {
            choices.takeUnspentPoints(npc, source(ability), applied.points());
        }
        applied.picks().forEach((key, given) -> choices.takeUnspentPicks(npc, pickSource(ability, key), given));
        store.delete(npc, ability);
        return true;
    }

    /** The ability's modifiers on the sheet: kept when they still match, otherwise added again. */
    private List<String> modifiers(UUID npc, AbilityDefinition definition, List<String> before) {
        Map<String, ModifierLine> current = new HashMap<>();
        sheets.modifiers(npc).forEach(line -> current.put(line.id(), line));
        List<AbilityDefinition.Modifier> have = before.stream().map(current::get)
                .map(line -> line == null ? null : new AbilityDefinition.Modifier(line.target(), line.amount()))
                .toList();
        if (have.equals(definition.modifiers())) {
            return before;
        }
        before.forEach(id -> sheets.removeModifier(npc, id));
        List<String> added = new ArrayList<>();
        for (AbilityDefinition.Modifier modifier : definition.modifiers()) {
            sheets.addModifier(npc, modifier.target(), modifier.amount()).ifPresent(line -> added.add(line.id()));
        }
        return added;
    }

    private Map<String, AppliedAbility.RaisedTier> raise(UUID npc, AbilityDefinition definition,
                                                         Map<String, AppliedAbility.RaisedTier> kept,
                                                         List<String> tierOrder) {
        Map<String, AppliedAbility.RaisedTier> raised = new LinkedHashMap<>(kept);
        definition.tiers().forEach((proficiency, tier) -> {
            if (raised.containsKey(proficiency)) {
                return;
            }
            Optional<String> current = sheets.proficiencyTier(npc, proficiency);
            if (current.isEmpty() || tierOrder.indexOf(current.get()) >= tierOrder.indexOf(tier)) {
                return;
            }
            if (sheets.setProficiency(npc, proficiency, tier) == StatSheets.ProficiencyResult.DONE) {
                raised.put(proficiency, new AppliedAbility.RaisedTier(tier, current.get()));
            }
        });
        return raised;
    }

    /** Puts a proficiency back, unless something else changed it since the ability raised it. */
    private void restore(UUID npc, String proficiency, AppliedAbility.RaisedTier raised) {
        if (sheets.proficiencyTier(npc, proficiency).map(raised.tier()::equals).orElse(false)) {
            sheets.setProficiency(npc, proficiency, raised.previous());
        }
    }

    private AppliedAbility points(UUID npc, AbilityDefinition definition, AppliedAbility before, AppliedAbility after) {
        String source = source(definition.id());
        int given = before.points();
        List<String> eligible = definition.points().eligible();
        if (given > 0 && !before.eligible().equals(eligible)) {
            // Unspent points were given for other stats: give them again for the new ones.
            given -= choices.takeUnspentPoints(npc, source, given);
        }
        int wanted = definition.points().amount();
        if (wanted > given) {
            choices.grantPoints(npc, source, wanted - given, eligible);
            given = wanted;
        } else if (wanted < given) {
            given -= choices.takeUnspentPoints(npc, source, given - wanted);
        }
        return new AppliedAbility(after.modifiers(), after.tiers(), given, given > 0 ? eligible : List.of(),
                after.picks());
    }

    private AppliedAbility picks(UUID npc, AbilityDefinition definition, AppliedAbility before, AppliedAbility after) {
        Map<String, Integer> given = new LinkedHashMap<>();
        Map<String, AbilityDefinition.Picks> wanted = new LinkedHashMap<>();
        definition.picks().forEach(pick -> wanted.put(pick.key(), pick));
        before.picks().forEach((key, count) -> {
            if (!wanted.containsKey(key)) {
                int left = count - choices.takeUnspentPicks(npc, pickSource(definition.id(), key), count);
                if (left > 0) {
                    given.put(key, left);
                }
            }
        });
        wanted.forEach((key, pick) -> {
            String source = pickSource(definition.id(), key);
            int had = before.picks().getOrDefault(key, 0);
            if (pick.count() > had) {
                choices.grantPicks(npc, source, pick.skill(), pick.tier(), pick.count() - had);
                had = pick.count();
            } else if (pick.count() < had) {
                had -= choices.takeUnspentPicks(npc, source, had - pick.count());
            }
            given.put(key, had);
        });
        return new AppliedAbility(after.modifiers(), after.tiers(), after.points(), after.eligible(), given);
    }

    static String source(String ability) {
        return SOURCE + ability;
    }

    static String pickSource(String ability, String key) {
        return SOURCE + ability + ":" + key;
    }
}
