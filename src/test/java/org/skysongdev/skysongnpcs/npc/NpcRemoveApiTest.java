package org.skysongdev.skysongnpcs.npc;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code npcs().remove(npc)}: removing a managed NPC for good through the API. The fake sheet
 * copier records sheet deletions instead of making them, so the tests read its list.
 */
class NpcRemoveApiTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private SkysongNpcsApi api;
    private final List<NpcDeleteEvent> deleted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        api = SkysongNpcsApi.get();
        server.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDelete(NpcDeleteEvent event) {
                deleted.add(event);
            }
        }, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void removesAnAdoptedNpcWithItsRecordAndSheet() {
        UUID guard = plugin.fakeCitizens().add("Guard");
        api.npcs().adopt(guard, NpcOwner.server());
        assertTrue(plugin.fakeSheets().exists(guard));

        assertTrue(api.npcs().remove(guard));

        assertFalse(api.npcs().isManaged(guard));
        assertFalse(plugin.fakeCitizens().exists(guard));
        assertEquals(List.of(guard), plugin.fakeCopier().deleted);
        assertEquals(1, deleted.size());
        assertEquals(guard, deleted.get(0).getNpc().id());
        assertEquals(NpcDeleteEvent.Cause.REMOVED, deleted.get(0).getCause());
    }

    @Test
    void removesAnNpcMadeFromATemplate() {
        Location at = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);
        ManagedNpc npc = api.npcs().create("example", null, NpcOwner.server(), at);

        assertTrue(api.npcs().remove(npc.id()));

        assertFalse(api.npcs().isManaged(npc.id()));
        assertFalse(plugin.fakeSpawner().spawned().containsKey(npc.id()));
        assertEquals(List.of(npc.id()), plugin.fakeCopier().deleted);
        assertEquals(1, deleted.size());
    }

    @Test
    void removesAPickedUpNpcAndItsItemStopsWorking() {
        Location at = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);
        ManagedNpc npc = api.npcs().create("example", null, NpcOwner.server(), at);
        ItemStack item = plugin.pickup().pickUp(npc.id());
        assertTrue(plugin.pickup().isCurrent(item));

        assertTrue(api.npcs().remove(npc.id()));

        assertFalse(api.npcs().isManaged(npc.id()));
        assertFalse(plugin.fakeSpawner().despawned().containsKey(npc.id()));
        assertFalse(plugin.pickup().isCurrent(item));
    }

    @Test
    void leavesPlainCitizensNpcsAlone() {
        UUID plain = plugin.fakeCitizens().add("Villager");

        assertFalse(api.npcs().remove(plain));

        assertTrue(plugin.fakeCitizens().exists(plain));
        assertTrue(deleted.isEmpty());
    }

    @Test
    void cleansUpAnNpcCitizensHasLostAlready() {
        UUID guard = plugin.fakeCitizens().add("Guard");
        api.npcs().adopt(guard, NpcOwner.server());
        // Citizens dropped it without its remove event (its save file was edited by hand, say).
        plugin.fakeCitizens().remove(guard);

        assertTrue(api.npcs().remove(guard));

        assertFalse(api.npcs().isManaged(guard));
        assertEquals(List.of(guard), plugin.fakeCopier().deleted);
        assertEquals(1, deleted.size());
    }

    @Test
    void removingTwiceDoesNothingTheSecondTime() {
        UUID guard = plugin.fakeCitizens().add("Guard");
        api.npcs().adopt(guard, NpcOwner.server());
        api.npcs().remove(guard);

        assertFalse(api.npcs().remove(guard));
        assertEquals(1, deleted.size());
    }
}
