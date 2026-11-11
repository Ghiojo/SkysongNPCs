package org.skysongdev.skysongnpcs.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A {@link ChoiceStore} in memory, for tests. */
public class InMemoryChoiceStore implements ChoiceStore {
    private final List<StoredPointGrant> grants = new ArrayList<>();
    private final List<StoredSpend> spends = new ArrayList<>();
    private final List<StoredStep> steps = new ArrayList<>();

    @Override
    public synchronized List<StoredPointGrant> loadGrants() {
        return new ArrayList<>(grants);
    }

    @Override
    public synchronized List<StoredSpend> loadSpends() {
        return new ArrayList<>(spends);
    }

    @Override
    public synchronized List<StoredStep> loadSteps() {
        return new ArrayList<>(steps);
    }

    @Override
    public synchronized void saveGrant(StoredPointGrant grant) {
        grants.removeIf(stored -> stored.id().equals(grant.id()));
        grants.add(grant);
    }

    @Override
    public synchronized void deleteGrant(UUID grant) {
        grants.removeIf(stored -> stored.id().equals(grant));
    }

    @Override
    public synchronized void saveSpend(StoredSpend spend) {
        spends.removeIf(stored -> stored.npc().equals(spend.npc()) && stored.grant().equals(spend.grant())
                && stored.stat().equals(spend.stat()));
        if (spend.amount() != 0) {
            spends.add(spend);
        }
    }

    @Override
    public synchronized void deleteSpends(UUID npc) {
        spends.removeIf(stored -> stored.npc().equals(npc));
    }

    @Override
    public synchronized void saveStep(UUID npc, String step) {
        StoredStep stored = new StoredStep(npc, step);
        if (!steps.contains(stored)) {
            steps.add(stored);
        }
    }

    @Override
    public synchronized void deleteSteps(UUID npc) {
        steps.removeIf(stored -> stored.npc().equals(npc));
    }

    @Override
    public synchronized void deleteAll(UUID npc) {
        deleteSteps(npc);
        deleteSpends(npc);
        grants.removeIf(stored -> stored.npc().equals(npc));
    }

    @Override
    public void close() {
    }
}
