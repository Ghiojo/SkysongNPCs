package org.skysongdev.skysongnpcs.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps NPC item rows in a map, for tests that have no database. */
public class InMemoryNpcItemStore implements NpcItemStore {
    private final Map<UUID, StoredItem> rows = new HashMap<>();

    @Override
    public synchronized Map<UUID, StoredItem> loadAll() {
        return new HashMap<>(rows);
    }

    @Override
    public synchronized void save(UUID npc, StoredItem item) {
        rows.put(npc, item);
    }

    @Override
    public synchronized void delete(UUID npc) {
        rows.remove(npc);
    }

    @Override
    public void close() {
    }
}
