package org.skysongdev.skysongnpcs.storage;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another choice store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedChoiceStore implements ChoiceStore {
    private final ChoiceStore store;
    private final BackgroundWriter writer;

    public QueuedChoiceStore(ChoiceStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs choice writer", onFailure);
    }

    @Override
    public List<StoredPointGrant> loadGrants() {
        return store.loadGrants();
    }

    @Override
    public List<StoredSpend> loadSpends() {
        return store.loadSpends();
    }

    @Override
    public List<StoredStep> loadSteps() {
        return store.loadSteps();
    }

    @Override
    public void saveGrant(StoredPointGrant grant) {
        writer.submit(() -> store.saveGrant(grant));
    }

    @Override
    public void deleteGrant(UUID grant) {
        writer.submit(() -> store.deleteGrant(grant));
    }

    @Override
    public void saveSpend(StoredSpend spend) {
        writer.submit(() -> store.saveSpend(spend));
    }

    @Override
    public void deleteSpends(UUID npc) {
        writer.submit(() -> store.deleteSpends(npc));
    }

    @Override
    public void saveStep(UUID npc, String step) {
        writer.submit(() -> store.saveStep(npc, step));
    }

    @Override
    public void deleteSteps(UUID npc) {
        writer.submit(() -> store.deleteSteps(npc));
    }

    @Override
    public void deleteAll(UUID npc) {
        writer.submit(() -> store.deleteAll(npc));
    }

    @Override
    public void close() {
        try {
            writer.close();
        } finally {
            store.close();
        }
    }
}
