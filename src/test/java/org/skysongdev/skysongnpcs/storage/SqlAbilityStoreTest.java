package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.abilities.AppliedAbility;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlAbilityStoreTest {
    private static final UUID GUARD = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlAbilityStore store() {
        return new SqlAbilityStore(dataSource, log::add);
    }

    @Test
    void savesAndLoadsWhatTheAbilityDidAndWhoHoldsIt() {
        AppliedAbility applied = new AppliedAbility(List.of("m1"), Map.of(), 2, List.of("strength"), Map.of());
        store().save(GUARD, "tough", applied, List.of("economy:module:trade", "staff"));

        assertEquals(List.of(new AbilityStore.StoredAbility(GUARD, "tough", applied,
                List.of("economy:module:trade", "staff"))), store().loadAll());
    }

    @Test
    void rowsFromBeforeSourcesLoadWithNone() throws SQLException {
        store();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO snpc_abilities (npc, ability, applied) VALUES ('" + GUARD
                    + "', 'tough', '{\"modifiers\":[\"m1\"],\"tiers\":{},\"points\":0,\"eligible\":[],\"picks\":{}}')");
        }

        List<AbilityStore.StoredAbility> loaded = store().loadAll();

        assertEquals(List.of("m1"), loaded.get(0).applied().modifiers());
        assertEquals(List.of(), loaded.get(0).sources());
        assertEquals(List.of(), log);
    }
}
