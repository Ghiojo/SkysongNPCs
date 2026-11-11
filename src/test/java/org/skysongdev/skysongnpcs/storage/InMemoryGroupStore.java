package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps player groups in a map, for tests that have no database. */
public class InMemoryGroupStore implements GroupStore {
    private final Map<String, PlayerGroup> groups = new LinkedHashMap<>();

    @Override
    public synchronized List<PlayerGroup> loadAll() {
        return new ArrayList<>(groups.values());
    }

    @Override
    public synchronized void save(PlayerGroup group) {
        groups.put(group.id(), group);
    }

    @Override
    public synchronized void delete(String id) {
        groups.remove(id);
    }

    @Override
    public void close() {
    }
}
