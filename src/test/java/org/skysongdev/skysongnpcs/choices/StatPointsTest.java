package org.skysongdev.skysongnpcs.choices;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.choices.PointGrant;
import org.skysongdev.skysongnpcs.api.choices.SpendResult;
import org.skysongdev.skysongnpcs.api.event.NpcChoiceResolvedEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.InMemoryChoiceStore;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Granting, spending, capping, taking back and respec of stat points. */
class StatPointsTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private FakeStatSheets sheets;
    private InMemoryChoiceStore store;
    private PointRules rules = new PointRules(List.of("strength"), Map.of("strength", 6));
    private final AtomicLong clock = new AtomicLong();
    private StatPoints points;
    private UUID npc;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        sheets = plugin.fakeSheets();
        store = new InMemoryChoiceStore();
        NpcRegistry registry = plugin.registry();
        npc = plugin.fakeCitizens().add("Guard");
        registry.adopt(npc, NpcOwner.server());
        sheets.create(npc, Map.of("strength", 2, "hp", 10));
        points = newPoints();
    }

    private StatPoints newPoints() {
        return new StatPoints(store, plugin.registry(), sheets, sheets::base, record -> rules,
                (record, player, stat, amount) -> server.getPluginManager().callEvent(
                        new NpcChoiceResolvedEvent(record, player, NpcChoiceResolvedEvent.STAT_POINTS, stat, amount)),
                clock::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void spendingRaisesTheBaseAndUsesThePoints() {
        points.grant(npc, "staff", 3, List.of());

        assertEquals(SpendResult.DONE, points.spend(npc, "str", 2, null));

        assertEquals(4, sheets.value(npc, "strength"));
        assertEquals(1, points.unspent(npc));
        assertEquals(2, points.spentOn(npc, "strength"));
        server.getPluginManager().assertEventFired(NpcChoiceResolvedEvent.class,
                event -> event.getChoice().equals("strength") && event.getAmount() == 2);
    }

    @Test
    void pointsOnlyGoOnEligibleStatsAndNeverAboveTheCap() {
        points.grant(npc, "staff", 10, List.of());

        assertEquals(SpendResult.NOT_ELIGIBLE, points.spend(npc, "hp", 1, null));
        assertEquals(SpendResult.UNKNOWN_STAT, points.spend(npc, "luck", 1, null));
        assertEquals(SpendResult.ABOVE_CAP, points.spend(npc, "str", 5, null));
        assertEquals(SpendResult.DONE, points.spend(npc, "str", 4, null));
        assertEquals(SpendResult.ABOVE_CAP, points.spend(npc, "str", 1, null));
        assertEquals(SpendResult.NOT_ENOUGH_POINTS, points.check(npc, "str", 7));
        assertEquals(6, sheets.value(npc, "strength"));
    }

    @Test
    void aGrantMayNameItsOwnStats() {
        points.grant(npc, "ability:tough", 2, List.of("hp"));

        assertEquals(List.of("hp"), points.spendable(npc));
        assertEquals(SpendResult.NOT_ELIGIBLE, points.spend(npc, "str", 1, null));
        assertEquals(SpendResult.DONE, points.spend(npc, "hp", 2, null));
        assertEquals(12, sheets.value(npc, "hp"));
        assertThrows(IllegalArgumentException.class, () -> points.grant(npc, "staff", 1, List.of("luck")));
    }

    @Test
    void narrowGrantsAreSpentFirst() {
        points.grant(npc, "anywhere", 1, List.of("strength", "hp"));
        points.grant(npc, "strength-only", 1, List.of("strength"));

        points.spend(npc, "str", 1, null);

        // The strength-only point went, so the other can still reach hp.
        assertEquals(0, points.unspent(npc, "strength-only"));
        assertEquals(SpendResult.DONE, points.spend(npc, "hp", 1, null));
    }

    @Test
    void takingBackOnlyTakesUnspentPointsOfThatSource() {
        points.grant(npc, "ability:tough", 3, List.of());
        points.grant(npc, "staff", 2, List.of());
        // Both grants allow strength; the oldest is drawn first.
        points.spend(npc, "str", 2, null);

        assertEquals(1, points.take(npc, "ability:tough", 5));
        assertEquals(0, points.unspent(npc, "ability:tough"));
        assertEquals(2, points.unspent(npc, "staff"));
        assertEquals(List.of(new PointGrant(points.grants(npc).getFirst().id(), "ability:tough", 2, 2, Set.of()),
                        points.grants(npc).get(1)), points.grants(npc));

        // A grant with nothing spent goes when emptied.
        assertEquals(2, points.take(npc, "staff", 2));
        assertEquals(1, points.grants(npc).size());
    }

    @Test
    void respecGivesThePointsBack() {
        points.grant(npc, "staff", 3, List.of());
        points.spend(npc, "str", 3, null);

        assertEquals(3, points.respec(npc));

        assertEquals(2, sheets.value(npc, "strength"));
        assertEquals(3, points.unspent(npc));
        assertEquals(0, points.respec(npc));
    }

    @Test
    void everythingSurvivesARestart() {
        points.grant(npc, "staff", 3, List.of("strength"));
        points.spend(npc, "str", 1, null);

        StatPoints reloaded = newPoints();
        reloaded.load();

        assertEquals(2, reloaded.unspent(npc));
        assertEquals(1, reloaded.spentOn(npc, "strength"));
        assertEquals(points.grants(npc), reloaded.grants(npc));
    }

    @Test
    void aCopyGetsItsOwnPointsAndADeletedNpcLosesThem() {
        points.grant(npc, "staff", 3, List.of());
        points.spend(npc, "str", 1, null);
        UUID copy = plugin.fakeCitizens().add("Guard");
        plugin.registry().copied(npc, copy);
        sheets.create(copy, Map.of("strength", 3));

        points.copy(npc, copy);
        points.spend(copy, "str", 2, null);

        assertEquals(2, points.unspent(npc));
        assertEquals(0, points.unspent(copy));
        assertEquals(3, points.respec(copy));

        points.forget(npc);
        assertEquals(0, points.unspent(npc));
        assertTrue(store.loadGrants().stream().noneMatch(grant -> grant.npc().equals(npc)));
    }

    @Test
    void capsComeFromTheRules() {
        assertEquals(OptionalInt.of(6), points.cap(npc, "strength"));
        assertEquals(OptionalInt.empty(), points.cap(npc, "hp"));
        rules = rules.with(null, Map.of());
        assertEquals(OptionalInt.empty(), points.cap(npc, "strength"));
    }

    @Test
    void badGrantsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> points.grant(npc, "staff", 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> points.grant(npc, " ", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> points.grant(UUID.randomUUID(), "staff", 1, List.of()));
    }
}
