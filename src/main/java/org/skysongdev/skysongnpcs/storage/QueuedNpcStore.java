package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Runs another store's writes on one background thread, in the order they were made, so the
 * main thread never waits on MySQL. Loading stays on the caller's thread, since it only runs
 * on enable. A write that fails goes to {@code onFailure} and the next one still runs.
 * Closing waits for the queued writes, then closes the other store.
 */
public final class QueuedNpcStore implements NpcStore {
    private final NpcStore store;
    private final BackgroundWriter writer;

    public QueuedNpcStore(NpcStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.writer = new BackgroundWriter("SkysongNPCs database writer", onFailure);
    }

    @Override
    public List<NpcRecord> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(NpcRecord record) {
        writer.submit(() -> store.save(record));
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
