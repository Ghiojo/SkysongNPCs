package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code npcs().findSpot}: where a new NPC can stand, with the checks blueprint items make. */
class FindSpotApiTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private SkysongNpcsApi api;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        api = SkysongNpcsApi.get();
        // Stone up to y 63, air from y 64.
        world = new WorldMock(Material.STONE, 63);
        server.addWorld(world);
        // The search skips chunks that are not loaded, and a new WorldMock has none loaded.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.loadChunk(x, z);
            }
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFreeSpotIsItsOwnAnswerFacingTheSameWay() {
        Location spot = api.npcs().findSpot(new Location(world, 3.2, 64, 7.9, 90, 10), 4).orElseThrow();

        assertEquals(new Location(world, 3.5, 64, 7.5, 90, 10), spot);
    }

    @Test
    void aBlockedSpotGivesTheClosestFreeOne() {
        world.getBlockAt(0, 65, 0).setType(Material.STONE);

        Location spot = api.npcs().findSpot(new Location(world, 0.5, 64, 0.5), 4).orElseThrow();

        assertEquals(64, spot.getBlockY());
        assertEquals(1, Math.abs(spot.getBlockX()) + Math.abs(spot.getBlockZ()));
    }

    @Test
    void theNpcNeedsSolidGround() {
        assertEquals(Optional.empty(), api.npcs().findSpot(new Location(world, 0.5, 70, 0.5), 0));

        // From the air, the search comes down to the ground.
        Location spot = api.npcs().findSpot(new Location(world, 0.5, 66, 0.5), 3).orElseThrow();
        assertEquals(64, spot.getBlockY());
    }

    @Test
    void nothingFreeInTheRadiusIsNoSpot() {
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        world.getBlockAt(0, 65, 0).setType(Material.STONE);

        assertEquals(Optional.empty(), api.npcs().findSpot(new Location(world, 0.5, 64, 0.5), 0));
    }

    @Test
    void configCapsTheRadius() {
        plugin.configuration().config().set("placing.find-spot.max-radius", 0);
        world.getBlockAt(0, 65, 0).setType(Material.STONE);

        assertEquals(Optional.empty(), api.npcs().findSpot(new Location(world, 0.5, 64, 0.5), 8));
    }

    @Test
    void fullChunksAreSkippedExceptForStaff() {
        plugin.configuration().config().set("placing.limits.per-chunk", 1);
        api.npcs().create("example", null, NpcOwner.server(), new Location(world, 5.5, 64, 5.5));
        Location near = new Location(world, 0.5, 64, 0.5);

        Location spot = api.npcs().findSpot(near, 4).orElseThrow();
        assertNotEquals(0, spot.getBlockX() >> 4 | spot.getBlockZ() >> 4);

        PlayerMock staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        assertEquals(new Location(world, 0.5, 64, 0.5), api.npcs().findSpot(near, 4, staff).orElseThrow());
    }

    @Test
    void regionsDecideForAPlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        SpotFinder finder = new SpotFinder(plugin.registry(), plugin.fakeSpawner(),
                (player, location) -> location.getBlockX() >= 2, () -> PlacingSettings.read(plugin.configuration().config()));
        Location near = new Location(world, 0.5, 64, 0.5);

        assertEquals(2, finder.find(near, 4, alice).orElseThrow().getBlockX());
        assertEquals(0, finder.find(near, 4, null).orElseThrow().getBlockX());
    }

    @Test
    void unloadedChunksAreNotSearched() {
        world.unloadChunk(0, 0);

        Location spot = api.npcs().findSpot(new Location(world, 0.5, 64, 0.5), 2).orElseThrow();
        assertNotEquals(0, spot.getBlockX() >> 4 | spot.getBlockZ() >> 4);
    }

    @Test
    void badArgumentsAreRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> api.npcs().findSpot(new Location(world, 0, 64, 0), -1));
        assertThrows(IllegalArgumentException.class,
                () -> api.npcs().findSpot(new Location(null, 0, 64, 0), 2));
    }

    @Test
    void theApiVersionMovedOn() {
        assertTrue(SkysongNpcsApi.get().apiVersion().compareTo("0.19.0") >= 0);
    }
}
