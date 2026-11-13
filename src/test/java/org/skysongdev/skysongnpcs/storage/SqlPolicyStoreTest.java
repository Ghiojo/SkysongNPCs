package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The snpc_policy tables, run on H2 in MySQL mode. */
class SqlPolicyStoreTest {
    private static final PolicyOwner ALICE = PolicyOwner.character(UUID.randomUUID(), "Pirate Queen");
    private static final PolicyOwner ECONOMY = new PolicyOwner.PluginOwner("SkysongEconomy");
    private static final AccessGroup WORKERS = new AccessGroup("economy", "smithy/workers");
    private static final UUID GUARD = UUID.randomUUID();

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlPolicyStore store() {
        return new SqlPolicyStore(dataSource, log::add);
    }

    @Test
    void savesAndLoadsPoliciesGrantsAndMembers() {
        store().savePolicy("shop", ALICE);
        store().savePolicy("economy.smithy", ECONOMY);
        store().saveGrant("economy.smithy", WORKERS, Set.of(NpcAction.INFO, NpcAction.INVENTORY_EDIT));
        store().saveGrant("economy.smithy", WORKERS, Set.of(NpcAction.INFO));
        store().setMember(GUARD, "shop");
        store().setMember(GUARD, "economy.smithy");

        PolicyStore.Snapshot loaded = store().loadAll();

        assertEquals(List.of(
                new PolicyStore.StoredPolicy("shop", ALICE, Map.of()),
                new PolicyStore.StoredPolicy("economy.smithy", ECONOMY, Map.of(WORKERS, Set.of(NpcAction.INFO)))),
                loaded.policies());
        assertEquals(Map.of(GUARD, "economy.smithy"), loaded.members());
    }

    @Test
    void policiesLoadInTheOrderTheyWereMadeEvenWithinOneMillisecond() {
        SqlPolicyStore store = store();
        List<String> made = new ArrayList<>();
        // Ids in reverse order, so falling back to sorting by id would show.
        for (int i = 30; i > 0; i--) {
            String id = "policy" + (char) ('a' + i % 26) + i;
            store.savePolicy(id, ALICE);
            made.add(id);
        }

        assertEquals(made, store().loadAll().policies().stream().map(PolicyStore.StoredPolicy::id).toList());
    }

    @Test
    void deletingAPolicyTakesItsGrantsAndMembersAlong() {
        store().savePolicy("shop", ALICE);
        store().saveGrant("shop", WORKERS, Set.of(NpcAction.INFO));
        store().setMember(GUARD, "shop");

        store().deletePolicy("shop");

        assertEquals(new PolicyStore.Snapshot(List.of(), Map.of()), store().loadAll());
    }

    @Test
    void deletesOneGrantOrMember() {
        store().savePolicy("shop", ALICE);
        store().saveGrant("shop", WORKERS, Set.of(NpcAction.INFO));
        store().setMember(GUARD, "shop");

        store().deleteGrant("shop", WORKERS);
        store().removeMember(GUARD);

        assertEquals(new PolicyStore.Snapshot(List.of(new PolicyStore.StoredPolicy("shop", ALICE, Map.of())), Map.of()),
                store().loadAll());
    }

    @Test
    void skipsRowsItCannotRead() throws SQLException {
        store().savePolicy("shop", ALICE);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO snpc_policies VALUES ('odd', 'martian:1', 0)");
            statement.execute("INSERT INTO snpc_policy_grants VALUES ('shop', 'perm:a', 'fly')");
            statement.execute("INSERT INTO snpc_policy_members VALUES ('" + GUARD + "', 'gone')");
        }

        PolicyStore.Snapshot loaded = store().loadAll();

        assertEquals(List.of(new PolicyStore.StoredPolicy("shop", ALICE, Map.of())), loaded.policies());
        assertEquals(Map.of(), loaded.members());
        assertEquals(2, log.size());
    }
}
