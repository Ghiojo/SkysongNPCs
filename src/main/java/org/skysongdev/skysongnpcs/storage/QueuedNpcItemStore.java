package org.skysongdev.skysongnpcs.storage;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/** Runs another item store's writes in the background, the way {@link QueuedNpcStore} does. */
public final class QueuedNpcItemStore implements NpcItemStore {
    private final NpcItemStore store;
    private final BackgroundWriter writer;

    public QueuedNpcItemStore(NpcItemStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs item writer", onFailure);
    }

    @Override
    public Map<UUID, StoredItem> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(UUID npc, StoredItem item) {
        writer.submit(() -> store.save(npc, item));
    }

    @Override
    public void delete(UUID npc) {
        writer.submit(() -> store.delete(npc));
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
