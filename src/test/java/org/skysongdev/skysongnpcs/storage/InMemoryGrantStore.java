package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Keeps grants in a list, for tests that have no database. */
public class InMemoryGrantStore implements GrantStore {
    private final List<StoredGrant> grants = new ArrayList<>();

    @Override
    public synchronized List<StoredGrant> loadAll() {
        return new ArrayList<>(grants);
    }

    @Override
    public synchronized void save(UUID npc, AccessGrant grant) {
        for (int i = 0; i < grants.size(); i++) {
            StoredGrant stored = grants.get(i);
            if (stored.npc().equals(npc) && stored.grant().source().equals(grant.source())
                    && stored.grant().group().equals(grant.group())) {
                grants.set(i, new StoredGrant(npc, grant));
                return;
            }
        }
        grants.add(new StoredGrant(npc, grant));
    }

    @Override
    public synchronized void delete(UUID npc, GrantSource source, AccessGroup group) {
        grants.removeIf(stored -> stored.npc().equals(npc) && stored.grant().source().equals(source)
                && stored.grant().group().equals(group));
    }

    @Override
    public synchronized void deleteAll(UUID npc) {
        grants.removeIf(stored -> stored.npc().equals(npc));
    }

    @Override
    public void close() {
    }
}
