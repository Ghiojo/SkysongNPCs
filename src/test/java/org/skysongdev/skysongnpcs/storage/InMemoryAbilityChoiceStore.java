package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AbilityChoiceGrant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps ability choices in memory, for tests that have no database. */
public final class InMemoryAbilityChoiceStore implements AbilityChoiceStore {
    private final Map<UUID, AbilityChoiceGrant> grants = new LinkedHashMap<>();
    private final List<StoredChoice> choices = new ArrayList<>();

    @Override
    public synchronized List<AbilityChoiceGrant> loadGrants() {
        return new ArrayList<>(grants.values());
    }

    @Override
    public synchronized List<StoredChoice> loadChoices() {
        return new ArrayList<>(choices);
    }

    @Override
    public synchronized void saveGrant(AbilityChoiceGrant grant) {
        grants.put(grant.id(), grant);
    }

    @Override
    public synchronized void deleteGrant(UUID grant) {
        grants.remove(grant);
        choices.removeIf(choice -> choice.grant().equals(grant));
    }

    @Override
    public synchronized void saveChoice(UUID grant, String ability) {
        deleteChoice(grant, ability);
        choices.add(new StoredChoice(grant, ability));
    }

    @Override
    public synchronized void deleteChoice(UUID grant, String ability) {
        choices.removeIf(choice -> choice.grant().equals(grant) && choice.ability().equals(ability));
    }

    @Override
    public synchronized void deleteAll(UUID npc) {
        grants.values().removeIf(grant -> {
            if (grant.npc().equals(npc)) {
                choices.removeIf(choice -> choice.grant().equals(grant.id()));
                return true;
            }
            return false;
        });
    }

    @Override
    public void close() {
    }
}
