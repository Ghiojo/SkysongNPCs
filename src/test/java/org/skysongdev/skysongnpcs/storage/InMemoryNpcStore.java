package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps records in a map, for tests that have no database. */
public class InMemoryNpcStore implements NpcStore {
    private final Map<UUID, NpcRecord> records = new LinkedHashMap<>();
    private boolean closed;

    @Override
    public synchronized List<NpcRecord> loadAll() {
        return new ArrayList<>(records.values());
    }

    @Override
    public synchronized void save(NpcRecord record) {
        records.put(record.id(), record);
    }

    @Override
    public synchronized void delete(UUID npc) {
        records.remove(npc);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public synchronized boolean closed() {
        return closed;
    }
}
