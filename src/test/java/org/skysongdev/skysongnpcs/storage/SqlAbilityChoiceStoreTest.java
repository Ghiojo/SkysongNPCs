package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.abilities.AbilityChoiceGrant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlAbilityChoiceStoreTest {
    private static final UUID GUARD = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SMITH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlAbilityChoiceStore store() {
        return new SqlAbilityChoiceStore(dataSource, log::add);
    }

    @Test
    void savesGrantsAndChoices() {
        AbilityChoiceGrant grant = new AbilityChoiceGrant(UUID.randomUUID(), GUARD, "economy:module:trade",
                List.of("tough", "swift"), 1);
        store().saveGrant(grant);
        store().saveGrant(grant.withCount(2));
        store().saveChoice(grant.id(), "swift");
        store().saveChoice(grant.id(), "swift");

        assertEquals(List.of(grant.withCount(2)), store().loadGrants());
        assertEquals(List.of(new AbilityChoiceStore.StoredChoice(grant.id(), "swift")), store().loadChoices());

        store().deleteChoice(grant.id(), "swift");
        assertEquals(List.of(), store().loadChoices());
        assertEquals(List.of(), log);
    }

    @Test
    void deletesOneGrantOrEveryGrantOfAnNpc() {
        AbilityChoiceGrant first = new AbilityChoiceGrant(UUID.randomUUID(), GUARD, "a", List.of("tough"), 1);
        AbilityChoiceGrant second = new AbilityChoiceGrant(UUID.randomUUID(), GUARD, "b", List.of("swift"), 1);
        AbilityChoiceGrant other = new AbilityChoiceGrant(UUID.randomUUID(), SMITH, "a", List.of("tough"), 1);
        for (AbilityChoiceGrant grant : List.of(first, second, other)) {
            store().saveGrant(grant);
            store().saveChoice(grant.id(), grant.options().getFirst());
        }

        store().deleteGrant(first.id());
        assertEquals(List.of(second, other), store().loadGrants());

        store().deleteAll(GUARD);
        assertEquals(List.of(other), store().loadGrants());
        assertEquals(List.of(new AbilityChoiceStore.StoredChoice(other.id(), "tough")), store().loadChoices());
    }
}
