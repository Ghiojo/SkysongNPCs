package org.skysongdev.skysongnpcs.items;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API's {@code items()} and {@code npcs().create}, and the placing texts. */
class BlueprintApiTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private SkysongNpcsApi api;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        api = SkysongNpcsApi.get();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void makesAndReadsBlueprintsWithoutTouchingTheOriginal() {
        ItemStack paper = new ItemStack(Material.PAPER);

        ItemStack blueprint = api.items().makeBlueprint(paper, "EXAMPLE", "Fido");

        assertTrue(api.items().isBlueprint(blueprint));
        assertEquals(Optional.of("example"), api.items().blueprintTemplate(blueprint));
        assertEquals(Optional.of("Fido"), plugin.blueprints().name(blueprint));
        assertFalse(api.items().isBlueprint(paper));
    }

    @Test
    void blueprintsNeedATemplateThatExists() {
        assertThrows(IllegalArgumentException.class,
                () -> api.items().makeBlueprint(new ItemStack(Material.PAPER), "gone", null));
    }

    @Test
    void createMakesAnNpcWaitingForSetup() {
        Location at = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);

        ManagedNpc npc = api.npcs().create("example", null, NpcOwner.server(), at);

        assertEquals(NpcState.SETUP, npc.state());
        assertEquals(Optional.of("example"), npc.template());
        assertTrue(plugin.fakeSpawner().spawned().containsKey(npc.id()));
        assertThrows(IllegalArgumentException.class, () -> api.npcs().create("gone", null, NpcOwner.server(), at));
    }

    @Test
    void createSaysWhenTheSpawnIsRefused() {
        plugin.fakeSpawner().refuseSpawns(true);
        Location at = new Location(server.addSimpleWorld("world"), 0.5, 64, 0.5);

        assertThrows(IllegalStateException.class, () -> api.npcs().create("example", null, NpcOwner.server(), at));
        assertTrue(plugin.registry().all().isEmpty());
    }

    @Test
    void everyPlacingResultHasItsText() {
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8));
        for (NpcPlacer.Result result : NpcPlacer.Result.values()) {
            if (result != NpcPlacer.Result.NOT_A_BLUEPRINT && result != NpcPlacer.Result.CANCELLED) {
                assertTrue(bundled.isString("place." + NpcPlaceListener.key(result)), result.name());
            }
        }
    }
}
