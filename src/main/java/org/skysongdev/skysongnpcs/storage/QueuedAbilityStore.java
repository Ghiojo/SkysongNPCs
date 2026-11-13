package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AppliedAbility;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another ability store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedAbilityStore implements AbilityStore {
    private final AbilityStore store;
    private final BackgroundWriter writer;

    public QueuedAbilityStore(AbilityStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs ability writer", onFailure);
    }

    @Override
    public List<StoredAbility> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(UUID npc, String ability, AppliedAbility applied, List<String> sources) {
        List<String> saved = List.copyOf(sources);
        writer.submit(() -> store.save(npc, ability, applied, saved));
    }

    @Override
    public void delete(UUID npc, String ability) {
        writer.submit(() -> store.delete(npc, ability));
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
