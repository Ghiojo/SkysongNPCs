package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another grant store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedGrantStore implements GrantStore {
    private final GrantStore store;
    private final BackgroundWriter writer;

    public QueuedGrantStore(GrantStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs grant writer", onFailure);
    }

    @Override
    public List<StoredGrant> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(UUID npc, AccessGrant grant) {
        writer.submit(() -> store.save(npc, grant));
    }

    @Override
    public void delete(UUID npc, GrantSource source, AccessGroup group) {
        writer.submit(() -> store.delete(npc, source, group));
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
