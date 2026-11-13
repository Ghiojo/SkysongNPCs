package org.skysongdev.skysongnpcs.storage;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.picks.PickPromotion;
import org.skysongdev.skysongnpcs.picks.PickSpend;
import org.skysongdev.skysongnpcs.picks.PromotionSpend;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The proficiency pick tables, run on H2 in MySQL mode. */
class SqlPickStoreTest {
    private static final UUID NPC = UUID.randomUUID();
    private static final UUID OTHER_NPC = UUID.randomUUID();

    private JdbcDataSource dataSource;
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    }

    private SqlPickStore store() {
        return new SqlPickStore(dataSource, log::add);
    }

    private static PickGrant grant(UUID npc, int count) {
        return new PickGrant(UUID.randomUUID(), npc, "ability:smith", "metalworking", "proficient", count);
    }

    @Test
    void savesAndLoadsGrantsAndSpends() {
        PickGrant grant = grant(NPC, 2);
        store().saveGrant(grant);
        PickSpend spend = new PickSpend(grant.id(), "smithing", "untrained");
        store().saveSpend(spend);

        assertEquals(List.of(grant), store().loadGrants());
        assertEquals(List.of(spend), store().loadSpends());
        assertEquals(List.of(), log);
    }

    @Test
    void savesAndLoadsPromotionsAndTheGrantsThatAllowThem() {
        PickGrant grant = new PickGrant(UUID.randomUUID(), NPC, "economy:martial", "combat", "proficient", 3,
                new PickPromotion("expert", 1));
        store().saveGrant(grant);
        store().saveSpend(new PickSpend(grant.id(), "swords", "untrained"));
        PromotionSpend first = new PromotionSpend(grant.id(), 0, "swords", "proficient", "expert", 1);
        PromotionSpend second = new PromotionSpend(grant.id(), 1, "axes", "proficient", "expert", 1);
        store().savePromotion(second);
        store().savePromotion(first);

        assertEquals(List.of(grant), store().loadGrants());
        assertEquals(List.of(first, second), store().loadPromotions());

        store().deletePromotion(grant.id(), 1);
        assertEquals(List.of(first), store().loadPromotions());
        // A grant saved without its promotion loses it.
        store().saveGrant(new PickGrant(grant.id(), NPC, "economy:martial", "combat", "proficient", 3));
        assertEquals(null, store().loadGrants().getFirst().promotion());

        store().deleteGrant(grant.id());
        assertEquals(List.of(), store().loadPromotions());
    }

    @Test
    void deletingAnNpcDeletesItsPromotions() {
        PickGrant grant = new PickGrant(UUID.randomUUID(), NPC, "staff", "combat", "proficient", 2,
                new PickPromotion("expert", 2));
        store().saveGrant(grant);
        store().savePromotion(new PromotionSpend(grant.id(), 0, "swords", "proficient", "expert", 2));

        store().deleteAll(NPC);

        assertEquals(List.of(), store().loadGrants());
        assertEquals(List.of(), store().loadPromotions());
    }

    @Test
    void savingAGrantAgainChangesItsCount() {
        PickGrant grant = grant(NPC, 2);
        store().saveGrant(grant);
        store().saveGrant(grant.withCount(5));

        assertEquals(List.of(grant.withCount(5)), store().loadGrants());
    }

    @Test
    void deletingAGrantDeletesItsSpends() {
        PickGrant grant = grant(NPC, 2);
        store().saveGrant(grant);
        store().saveSpend(new PickSpend(grant.id(), "smithing", "untrained"));

        store().deleteGrant(grant.id());

        assertEquals(List.of(), store().loadGrants());
        assertEquals(List.of(), store().loadSpends());
    }

    @Test
    void deletesOneSpend() {
        PickGrant grant = grant(NPC, 2);
        store().saveGrant(grant);
        store().saveSpend(new PickSpend(grant.id(), "smithing", "untrained"));
        PickSpend kept = new PickSpend(grant.id(), "forging", "untrained");
        store().saveSpend(kept);

        store().deleteSpend(grant.id(), "smithing");

        assertEquals(List.of(kept), store().loadSpends());
    }

    @Test
    void deleteAllOnlyTouchesOneNpc() {
        PickGrant gone = grant(NPC, 1);
        PickGrant kept = grant(OTHER_NPC, 1);
        store().saveGrant(gone);
        store().saveGrant(kept);
        store().saveSpend(new PickSpend(gone.id(), "smithing", "untrained"));
        PickSpend keptSpend = new PickSpend(kept.id(), "smithing", "untrained");
        store().saveSpend(keptSpend);

        store().deleteAll(NPC);

        assertEquals(List.of(kept), store().loadGrants());
        assertEquals(List.of(keptSpend), store().loadSpends());
    }

    @Test
    void unreadableRowsAreSkippedAndLogged() throws Exception {
        store().saveGrant(grant(NPC, 1));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO snpc_pick_grants VALUES ('nope', '" + NPC + "', 'staff', 'combat', 'expert', 1)");
        }

        assertEquals(1, store().loadGrants().size());
        assertEquals(1, log.size());
    }
}
