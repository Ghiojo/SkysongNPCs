package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Runs another group store's writes in the background, the way {@link QueuedGrantStore} does. */
public final class QueuedGroupStore implements GroupStore {
    private final GroupStore store;
    private final BackgroundWriter writer;

    public QueuedGroupStore(GroupStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs group writer", onFailure);
    }

    @Override
    public List<PlayerGroup> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(PlayerGroup group) {
        writer.submit(() -> store.save(group));
    }

    @Override
    public void delete(String id) {
        writer.submit(() -> store.delete(id));
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
