package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The snpc_grants SQL, run on H2 in MySQL mode. */
class SqlGrantStoreTest {
    private static final UUID NPC = UUID.randomUUID();
    private static final AccessGrant OWNER_GRANT = new AccessGrant(
            AccessGroup.player(UUID.randomUUID(), "Pirate Queen"), GrantSource.owner(),
            Set.of(NpcAction.INFO, NpcAction.STATS_VIEW));
    private static final AccessGrant PLUGIN_GRANT = new AccessGrant(
            new AccessGroup("economy", "smithy/workers"), GrantSource.plugin("SkysongEconomy"),
            Set.of(NpcAction.INVENTORY_EDIT));

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlGrantStore store() {
        return new SqlGrantStore(dataSource, log::add);
    }

    @Test
    void savesAndLoadsOwnerAndPluginGrants() {
        store().save(NPC, OWNER_GRANT);
        store().save(NPC, PLUGIN_GRANT);

        assertEquals(Set.of(new GrantStore.StoredGrant(NPC, OWNER_GRANT), new GrantStore.StoredGrant(NPC, PLUGIN_GRANT)),
                new HashSet<>(store().loadAll()));
    }

    @Test
    void savingAgainReplacesTheActions() {
        store().save(NPC, OWNER_GRANT);
        AccessGrant changed = new AccessGrant(OWNER_GRANT.group(), OWNER_GRANT.source(), Set.of(NpcAction.MANAGE));

        store().save(NPC, changed);

        assertEquals(List.of(new GrantStore.StoredGrant(NPC, changed)), store().loadAll());
    }

    @Test
    void deletesOneGrantOrAllOfAnNpc() {
        UUID other = UUID.randomUUID();
        store().save(NPC, OWNER_GRANT);
        store().save(NPC, PLUGIN_GRANT);
        store().save(other, OWNER_GRANT);

        store().delete(NPC, PLUGIN_GRANT.source(), PLUGIN_GRANT.group());
        assertEquals(3 - 1, store().loadAll().size());

        store().deleteAll(NPC);
        assertEquals(List.of(new GrantStore.StoredGrant(other, OWNER_GRANT)), store().loadAll());
    }

    @Test
    void skipsRowsItCannotReadAndUnknownActions() throws SQLException {
        store().save(NPC, OWNER_GRANT);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO snpc_grants VALUES ('" + NPC + "', 'owner', 'perm:a', 'fly')");
            statement.execute("INSERT INTO snpc_grants VALUES ('" + NPC + "', 'owner', 'perm:b', 'info,fly')");
        }

        List<GrantStore.StoredGrant> loaded = store().loadAll();

        assertEquals(2, loaded.size());
        assertEquals(1, log.size());
    }
}
