package org.skysongdev.skysongnpcs.storage;

import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueuedNpcStoreTest {

    @Test
    void writesInOrderAndFinishesThemOnClose() {
        InMemoryNpcStore inner = new InMemoryNpcStore();
        QueuedNpcStore store = new QueuedNpcStore(inner, failed -> { });
        NpcRecord first = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());
        NpcRecord second = first.withOwner(NpcOwner.character(UUID.randomUUID(), "Default"));

        store.save(first);
        store.save(second);
        store.close();

        assertEquals(List.of(second), inner.loadAll());
        assertTrue(inner.closed());
    }

    @Test
    void aFailedWriteIsReportedAndTheNextStillRuns() {
        InMemoryNpcStore inner = new InMemoryNpcStore();
        List<RuntimeException> failures = new ArrayList<>();
        QueuedNpcStore store = new QueuedNpcStore(new InMemoryNpcStore() {
            @Override
            public void delete(UUID npc) {
                throw new StorageException("down", null);
            }

            @Override
            public void save(NpcRecord record) {
                inner.save(record);
            }
        }, failures::add);
        NpcRecord record = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());

        store.delete(record.id());
        store.save(record);
        store.close();

        assertEquals(1, failures.size());
        assertEquals(List.of(record), inner.loadAll());
    }

    @Test
    void refusesWritesOnceClosed() {
        QueuedNpcStore store = new QueuedNpcStore(new InMemoryNpcStore(), failed -> { });
        store.close();

        assertThrows(IllegalStateException.class, () -> store.delete(UUID.randomUUID()));
    }
}
