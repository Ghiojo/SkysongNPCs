package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.storage.ChoiceStore.StoredPointGrant;
import org.skysongdev.skysongnpcs.storage.ChoiceStore.StoredSpend;
import org.skysongdev.skysongnpcs.storage.ChoiceStore.StoredStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The choice tables' SQL, run on H2 in MySQL mode. */
class SqlChoiceStoreTest {
    private static final UUID NPC = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private SqlChoiceStore store;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        store = new SqlChoiceStore(dataSource, log::add);
    }

    @Test
    void grantsAreSavedUpdatedAndLoadedOldestFirst() {
        StoredPointGrant later = new StoredPointGrant(UUID.randomUUID(), NPC, "staff", 2, Set.of(), 20);
        StoredPointGrant first = new StoredPointGrant(UUID.randomUUID(), NPC, "template", 5, Set.of("str", "dex"), 10);
        store.saveGrant(later);
        store.saveGrant(first);
        store.saveGrant(new StoredPointGrant(later.id(), NPC, "staff", 1, Set.of(), 20));

        assertEquals(List.of(first, new StoredPointGrant(later.id(), NPC, "staff", 1, Set.of(), 20)), store.loadGrants());

        store.deleteGrant(first.id());
        assertEquals(1, store.loadGrants().size());
    }

    @Test
    void spendsAreReplacedAndAZeroDeletesThem() {
        UUID grant = UUID.randomUUID();
        store.saveSpend(new StoredSpend(NPC, grant, "str", 1));
        store.saveSpend(new StoredSpend(NPC, grant, "str", 3));
        store.saveSpend(new StoredSpend(NPC, grant, "dex", 1));
        store.saveSpend(new StoredSpend(NPC, grant, "dex", 0));

        assertEquals(List.of(new StoredSpend(NPC, grant, "str", 3)), store.loadSpends());

        store.deleteSpends(NPC);
        assertEquals(List.of(), store.loadSpends());
    }

    @Test
    void stepsAreSavedOnceAndDeletedWithEverythingElse() {
        store.saveStep(NPC, "ancestry");
        store.saveStep(NPC, "ancestry");
        store.saveStep(OTHER, "points");
        store.saveGrant(new StoredPointGrant(UUID.randomUUID(), NPC, "staff", 2, Set.of(), 1));
        store.saveSpend(new StoredSpend(NPC, UUID.randomUUID(), "str", 1));

        assertEquals(2, store.loadSteps().size());

        store.deleteAll(NPC);
        assertEquals(List.of(new StoredStep(OTHER, "points")), store.loadSteps());
        assertEquals(List.of(), store.loadGrants());
        assertEquals(List.of(), store.loadSpends());
        assertEquals(List.of(), log);
    }
}
