package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.storage.NpcItemStore.StoredItem;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The snpc_items SQL, run on H2 in MySQL mode. */
class SqlNpcItemStoreTest {
    private static final UUID NPC = UUID.randomUUID();

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlNpcItemStore store() {
        return new SqlNpcItemStore(dataSource, log::add);
    }

    @Test
    void savesAndLoadsARow() {
        UUID ticket = UUID.randomUUID();
        store().save(NPC, new StoredItem(new byte[] {1, 2, 3}, ticket, NpcState.SETUP));

        StoredItem loaded = store().loadAll().get(NPC);

        assertArrayEquals(new byte[] {1, 2, 3}, loaded.blueprint());
        assertEquals(ticket, loaded.ticket());
        assertEquals(NpcState.SETUP, loaded.resume());
    }

    @Test
    void emptyColumnsLoadAsNull() {
        store().save(NPC, new StoredItem(null, null, null));

        StoredItem loaded = store().loadAll().get(NPC);

        assertNull(loaded.blueprint());
        assertNull(loaded.ticket());
        assertNull(loaded.resume());
    }

    @Test
    void savingAgainReplacesTheRow() {
        store().save(NPC, new StoredItem(new byte[] {1}, UUID.randomUUID(), NpcState.PLACED));
        UUID fresh = UUID.randomUUID();

        store().save(NPC, new StoredItem(new byte[] {1}, fresh, NpcState.PLACED));

        Map<UUID, StoredItem> rows = store().loadAll();
        assertEquals(1, rows.size());
        assertEquals(fresh, rows.get(NPC).ticket());
    }

    @Test
    void deletesARow() {
        store().save(NPC, new StoredItem(null, UUID.randomUUID(), NpcState.PLACED));

        store().delete(NPC);

        assertTrue(store().loadAll().isEmpty());
    }

    @Test
    void unreadableRowsAreSkippedAndLogged() throws SQLException {
        store();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO snpc_items (npc, ticket, resume_state) VALUES ('not-a-uuid', NULL, NULL)");
        }

        assertTrue(store().loadAll().isEmpty());
        assertEquals(1, log.size());
    }
}
