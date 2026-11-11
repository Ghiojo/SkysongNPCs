package org.skysongdev.skysongnpcs.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.storage.GrantStore;
import org.skysongdev.skysongnpcs.storage.InMemoryGrantStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessRegistryTest {
    private static final AccessGroup GUARDS = AccessGroup.permission("server.guards");
    private static final AccessGroup WORKERS = new AccessGroup("economy", "smithy/workers");
    private static final GrantSource ECONOMY = GrantSource.plugin("SkysongEconomy");

    private InMemoryGrantStore store;
    private List<String> changes;
    private AccessRegistry registry;
    private UUID npc;

    @BeforeEach
    void setUp() {
        store = new InMemoryGrantStore();
        changes = new ArrayList<>();
        registry = new AccessRegistry(store, (id, group, source, previous, current) ->
                changes.add(group + " " + source.key() + " " + previous + " -> " + current));
        npc = UUID.randomUUID();
    }

    @Test
    void setMakesChangesAndDeletesAGrant() {
        AccessGrant made = registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO)).orElseThrow();
        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO, NpcAction.FOLLOW));
        Optional<AccessGrant> deleted = registry.set(npc, GrantSource.owner(), GUARDS, Set.of());

        assertEquals(Set.of(NpcAction.INFO), made.actions());
        assertEquals(Optional.empty(), deleted);
        assertEquals(List.of(), registry.grants(npc));
        assertEquals(List.of(), store.loadAll());
        assertEquals(List.of(
                "perm:server.guards owner [] -> [INFO]",
                "perm:server.guards owner [INFO] -> [INFO, FOLLOW]",
                "perm:server.guards owner [INFO, FOLLOW] -> []"), changes);
    }

    @Test
    void settingTheSameActionsAgainChangesNothing() {
        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO));
        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO));
        registry.set(npc, GrantSource.owner(), WORKERS, Set.of());

        assertEquals(1, changes.size());
    }

    @Test
    void sourcesKeepSeparateGrantsForTheSameGroup() {
        registry.set(npc, GrantSource.owner(), WORKERS, Set.of(NpcAction.INFO));
        registry.set(npc, ECONOMY, WORKERS, Set.of(NpcAction.INVENTORY_EDIT));

        registry.set(npc, GrantSource.owner(), WORKERS, Set.of());

        assertEquals(List.of(new AccessGrant(WORKERS, ECONOMY, Set.of(NpcAction.INVENTORY_EDIT))), registry.grants(npc));
    }

    @Test
    void aChangedGrantKeepsItsPlace() {
        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO));
        registry.set(npc, ECONOMY, WORKERS, Set.of(NpcAction.INFO));

        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.LOOK));

        assertEquals(List.of(GUARDS, WORKERS), registry.grants(npc).stream().map(AccessGrant::group).toList());
    }

    @Test
    void loadReadsWhatWasSaved() {
        registry.set(npc, ECONOMY, WORKERS, Set.of(NpcAction.TELEPORT));

        AccessRegistry restarted = new AccessRegistry(store, (id, group, source, previous, current) -> { });
        restarted.load();

        assertEquals(registry.grants(npc), restarted.grants(npc));
    }

    @Test
    void deleteAllAndCopyAreSavedButNotAnnounced() {
        UUID copy = UUID.randomUUID();
        registry.set(npc, GrantSource.owner(), GUARDS, Set.of(NpcAction.INFO));
        changes.clear();

        registry.copy(npc, copy);
        registry.deleteAll(npc);

        assertEquals(List.of(), registry.grants(npc));
        assertEquals(List.of(new AccessGrant(GUARDS, GrantSource.owner(), Set.of(NpcAction.INFO))), registry.grants(copy));
        assertEquals(List.of(copy), store.loadAll().stream().map(GrantStore.StoredGrant::npc).toList());
        assertTrue(changes.isEmpty());
    }
}
