package org.skysongdev.skysongnpcs.storage;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * One background thread that runs database writes in the order they were made, so the main
 * thread never waits on MySQL. A write that fails goes to {@code onFailure} and the next one
 * still runs. Closing waits for the queued writes. The queued stores share this.
 */
final class BackgroundWriter {
    static final long CLOSE_TIMEOUT_SECONDS = 30;

    private final Consumer<RuntimeException> onFailure;
    private final ExecutorService writer;

    BackgroundWriter(String threadName, Consumer<RuntimeException> onFailure) {
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        this.writer = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Queues a write. @throws IllegalStateException once closed */
    void submit(Runnable write) {
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

    /** Refuses new writes and waits for the queued ones. */
    void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                onFailure.accept(new IllegalStateException(
                        "Some NPC changes were still unsaved after " + CLOSE_TIMEOUT_SECONDS + " seconds"));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
