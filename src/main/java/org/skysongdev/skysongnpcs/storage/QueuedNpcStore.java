package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Runs another store's writes on one background thread, in the order they were made, so the
 * main thread never waits on MySQL. Loading stays on the caller's thread, since it only runs
 * on enable. A write that fails goes to {@code onFailure} and the next one still runs.
 * Closing waits for the queued writes, then closes the other store.
 */
public final class QueuedNpcStore implements NpcStore {
    static final long CLOSE_TIMEOUT_SECONDS = 30;

    private final NpcStore store;
    private final Consumer<RuntimeException> onFailure;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkysongNPCs database writer");
        thread.setDaemon(true);
        return thread;
    });

    public QueuedNpcStore(NpcStore store, Consumer<RuntimeException> onFailure) {
        this.store = Objects.requireNonNull(store, "store");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
    }

    @Override
    public List<NpcRecord> loadAll() {
        return store.loadAll();
    }

    @Override
    public void save(NpcRecord record) {
        submit(() -> store.save(record));
    }

    @Override
    public void delete(UUID npc) {
        submit(() -> store.delete(npc));
    }

    private void submit(Runnable write) {
        try {
            writer.execute(() -> {
                try {
                    write.run();
                } catch (RuntimeException failed) {
                    onFailure.accept(failed);
                }
            });
        } catch (RejectedExecutionException closed) {
            throw new IllegalStateException("The database writer is closed", closed);
        }
    }

    @Override
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                onFailure.accept(new IllegalStateException(
                        "Some NPC changes were still unsaved after " + CLOSE_TIMEOUT_SECONDS + " seconds"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            store.close();
        }
    }
}
