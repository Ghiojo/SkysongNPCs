package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The snpc_npcs SQL, run on H2 in MySQL mode. */
class SqlNpcStoreTest {
    private JdbcDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        // A fresh database per test; DB_CLOSE_DELAY keeps it while no connection is open.
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlNpcStore store() {
        return new SqlNpcStore(dataSource, () -> { });
    }

    @Test
    void savesAndLoadsCharacterAndServerOwners() {
        NpcRecord owned = new NpcRecord(UUID.randomUUID(), NpcOwner.character(UUID.randomUUID(), "Red Hood"),
                NpcState.SETUP, "guard_dog");
        NpcRecord server = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());
        SqlNpcStore store = store();

        store.save(owned);
        store.save(server);

        assertEquals(Set.of(owned, server), new HashSet<>(store().loadAll()));
    }

    @Test
    void savingAgainReplacesTheRecord() {
        UUID npc = UUID.randomUUID();
        SqlNpcStore store = store();
        store.save(NpcRecord.adopted(npc, NpcOwner.server()));

        NpcRecord changed = NpcRecord.adopted(npc, NpcOwner.character(UUID.randomUUID(), "Default"));
        store.save(changed);

        assertEquals(List.of(changed), store.loadAll());
    }

    @Test
    void deletesOneRecord() {
        NpcRecord kept = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());
        NpcRecord gone = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());
        SqlNpcStore store = store();
        store.save(kept);
        store.save(gone);

        store.delete(gone.id());
        store.delete(UUID.randomUUID());

        assertEquals(List.of(kept), store.loadAll());
    }

    @Test
    void openingTwiceKeepsTheTable() {
        NpcRecord record = NpcRecord.adopted(UUID.randomUUID(), NpcOwner.server());
        store().save(record);

        assertEquals(List.of(record), store().loadAll());
    }
}
