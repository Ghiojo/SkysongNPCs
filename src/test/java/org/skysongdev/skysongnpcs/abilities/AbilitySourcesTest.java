package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilities;
import org.skysongdev.skysongnpcs.api.event.NpcAbilityChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.storage.InMemoryAbilityStore;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Abilities held by several sources: two givers never undo each other. */
class AbilitySourcesTest {
    private static final String MODULE = "economy:module:trade";

    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private NpcAbilities api;
    private FakeStatSheets sheets;
    private UUID guard;
    private final List<NpcAbilityChangeEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        api = SkysongNpcsApi.get().abilities();
        sheets = plugin.fakeSheets();
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"), AbilityServiceTest.TOUGH);
        assertTrue(plugin.abilities().definitions().reload());
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onChange(NpcAbilityChangeEvent event) {
                events.add(event);
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void twoSourcesApplyTheAbilityOnceAndOnlyTheLastRevokeTakesItAway() {
        assertTrue(api.grant(guard, "tough", MODULE));
        assertTrue(api.grant(guard, "tough", NpcAbilities.STAFF));

        assertEquals(Set.of(MODULE, NpcAbilities.STAFF), api.sources(guard, "tough"));
        assertEquals(2, sheets.modifiers(guard).size());
        assertEquals(1, events.size());
        assertEquals(MODULE, events.get(0).getSource());

        assertTrue(api.revoke(guard, "tough", MODULE));
        assertTrue(api.has(guard, "tough"));
        assertEquals(Set.of(NpcAbilities.STAFF), api.sources(guard, "tough"));
        assertEquals(2, sheets.modifiers(guard).size());
        assertEquals(1, events.size());

        assertTrue(api.revoke(guard, "tough", NpcAbilities.STAFF));
        assertFalse(api.has(guard, "tough"));
        assertEquals(Set.of(), api.sources(guard, "tough"));
        assertEquals(0, sheets.modifiers(guard).size());
        assertEquals(NpcAbilityChangeEvent.Change.REVOKED, events.get(1).getChange());
        assertEquals(NpcAbilities.STAFF, events.get(1).getSource());
    }

    @Test
    void grantingAndRevokingTwiceForOneSourceDoesNothing() {
        api.grant(guard, "tough", MODULE);

        assertFalse(api.grant(guard, "tough", MODULE));
        assertFalse(api.revoke(guard, "tough", NpcAbilities.STAFF));
        assertTrue(api.has(guard, "tough"));
    }

    @Test
    void theOldGrantIsStaffsAndTheOldRevokeTakesItFromEveryone() {
        api.grant(guard, "tough", MODULE);
        api.grant(guard, "tough");
        assertEquals(Set.of(MODULE, NpcAbilities.STAFF), api.sources(guard, "tough"));

        assertTrue(api.revoke(guard, "tough"));

        assertFalse(api.has(guard, "tough"));
        assertNull(events.get(events.size() - 1).getSource());
    }

    @Test
    void blankSourcesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> api.grant(guard, "tough", " "));
    }

    @Test
    void sourcesAreSavedAndOldRowsLoadAsStaffs() {
        InMemoryAbilityStore store = new InMemoryAbilityStore();
        AbilityService first = new AbilityService(plugin.registry(), plugin.abilities().definitions(), store, sheets,
                plugin.fakeChoices(), event -> { });
        first.grant(guard, "tough", MODULE);
        first.grant(guard, "tough", "template");
        assertEquals(List.of(MODULE, "template"), store.savedSources(guard, "tough"));
        UUID older = plugin.fakeCitizens().add("Older");
        plugin.registry().adopt(older, NpcOwner.server());
        store.saveWithoutSources(older, "tough", AppliedAbility.NOTHING);

        AbilityService second = new AbilityService(plugin.registry(), plugin.abilities().definitions(), store, sheets,
                plugin.fakeChoices(), event -> { });
        second.load();

        assertEquals(Set.of(MODULE, "template"), second.sources(guard, "tough"));
        assertEquals(Set.of(NpcAbilities.STAFF), second.sources(older, "tough"));
    }

    @Test
    void anAbilityWithoutEffectsIsSavedToo() throws IOException {
        Files.writeString(plugin.getDataFolder().toPath().resolve("abilities.yml"),
                "calm:\n  name: Calm\n  description: \"Never panics.\"\n");
        assertTrue(plugin.abilities().definitions().reload());
        InMemoryAbilityStore store = new InMemoryAbilityStore();
        AbilityService service = new AbilityService(plugin.registry(), plugin.abilities().definitions(), store, sheets,
                plugin.fakeChoices(), event -> { });

        service.grant(guard, "calm", MODULE);

        assertEquals(List.of(MODULE), store.savedSources(guard, "calm"));
    }

    @Test
    void aCopyKeepsTheSources() {
        api.grant(guard, "tough", MODULE);
        UUID copy = plugin.fakeCitizens().add("Copy");
        plugin.registry().adopt(copy, NpcOwner.server());

        plugin.abilities().copied(guard, copy);

        assertEquals(Set.of(MODULE), api.sources(copy, "tough"));
    }
}
