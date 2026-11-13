package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AppliedAbility;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** An {@link AbilityStore} in memory, for tests. */
public final class InMemoryAbilityStore implements AbilityStore {
    private final Map<UUID, Map<String, AppliedAbility>> rows = new LinkedHashMap<>();
    private final Map<UUID, Map<String, List<String>>> sources = new LinkedHashMap<>();

    @Override
    public List<StoredAbility> loadAll() {
        List<StoredAbility> all = new ArrayList<>();
        rows.forEach((npc, abilities) -> abilities.forEach((ability, applied) ->
                all.add(new StoredAbility(npc, ability, applied,
                        sources.getOrDefault(npc, Map.of()).getOrDefault(ability, List.of())))));
        return all;
    }

    @Override
    public void save(UUID npc, String ability, AppliedAbility applied, List<String> holders) {
        rows.computeIfAbsent(npc, ignored -> new LinkedHashMap<>()).put(ability, applied);
        sources.computeIfAbsent(npc, ignored -> new LinkedHashMap<>()).put(ability, List.copyOf(holders));
    }

    @Override
    public void delete(UUID npc, String ability) {
        Map<String, AppliedAbility> abilities = rows.get(npc);
        if (abilities != null) {
            abilities.remove(ability);
        }
        sources.getOrDefault(npc, new LinkedHashMap<>()).remove(ability);
    }

    @Override
    public void deleteAll(UUID npc) {
        rows.remove(npc);
        sources.remove(npc);
    }

    @Override
    public void close() {
    }

    /** Who is saved as holding one of an NPC's abilities, or null. */
    public List<String> savedSources(UUID npc, String ability) {
        return sources.getOrDefault(npc, Map.of()).get(ability);
    }

    /** Saves a row the way versions before sources did, with none. */
    public void saveWithoutSources(UUID npc, String ability, AppliedAbility applied) {
        rows.computeIfAbsent(npc, ignored -> new LinkedHashMap<>()).put(ability, applied);
    }

    /** What is saved for one of an NPC's abilities, or null. */
    public AppliedAbility saved(UUID npc, String ability) {
        return rows.getOrDefault(npc, Map.of()).get(ability);
    }
}
