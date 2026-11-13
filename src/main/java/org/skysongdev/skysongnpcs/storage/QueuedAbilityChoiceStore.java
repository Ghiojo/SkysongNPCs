package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.abilities.AbilityChoiceGrant;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another ability choice store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedAbilityChoiceStore implements AbilityChoiceStore {
    private final AbilityChoiceStore store;
    private final BackgroundWriter writer;

    public QueuedAbilityChoiceStore(AbilityChoiceStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs ability choice writer", onFailure);
    }

    @Override
    public List<AbilityChoiceGrant> loadGrants() {
        return store.loadGrants();
    }

    @Override
    public List<StoredChoice> loadChoices() {
        return store.loadChoices();
    }

    @Override
    public void saveGrant(AbilityChoiceGrant grant) {
        writer.submit(() -> store.saveGrant(grant));
    }

    @Override
    public void deleteGrant(UUID grant) {
        writer.submit(() -> store.deleteGrant(grant));
    }

    @Override
    public void saveChoice(UUID grant, String ability) {
        writer.submit(() -> store.saveChoice(grant, ability));
    }

    @Override
    public void deleteChoice(UUID grant, String ability) {
        writer.submit(() -> store.deleteChoice(grant, ability));
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
