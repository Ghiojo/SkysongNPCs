package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.FakeNpcSpawner;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What {@link NpcFactory} undoes when making an NPC fails half-way. */
class NpcFactoryTest {
    private OfflineSkysongNPCs plugin;
    private Location location;

    @BeforeEach
    void setUp() {
        ServerMock server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        location = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aSheetThatFailsTakesTheNpcAway() {
        FakeNpcSpawner spawner = plugin.fakeSpawner();
        NpcFactory factory = new NpcFactory(plugin.templates(), plugin.registry(), spawner, (npc, template) -> {
            throw new IllegalArgumentException("SkysongStats has no stat str");
        });
        NpcTemplate example = plugin.templates().get("example").orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(example, null, NpcOwner.server(), location));

        assertTrue(spawner.spawned().isEmpty());
        assertTrue(plugin.registry().all().isEmpty());
    }
}
