package org.skysongdev.skysongnpcs;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.event.NpcDeleteEvent;
import org.skysongdev.skysongnpcs.api.event.NpcOwnerChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.ManagedNpc;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkysongNPCsTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersTheApiWhileEnabled() {
        OfflineSkysongNPCs plugin = OfflineSkysongNPCs.load();

        assertEquals(SkysongNpcsApiImpl.API_VERSION, SkysongNpcsApi.get().apiVersion());

        server.getPluginManager().disablePlugin(plugin);
        assertThrows(IllegalStateException.class, SkysongNpcsApi::get);
    }

    @Test
    void dependsOnCitizensStatsAndItems() {
        OfflineSkysongNPCs plugin = OfflineSkysongNPCs.load();

        assertEquals(List.of("Citizens", "SkysongStats", "Skysong_Items"), plugin.getPluginMeta().getPluginDependencies());
        assertEquals(List.of("SkysongWardrobe", "LuckPerms", "WorldGuard"), plugin.getPluginMeta().getPluginSoftDependencies());
    }

    @Test
    void disablesItselfWhenMySqlCannotBeReached() throws Exception {
        SkysongNPCs plugin = MockBukkit.loadWithConfig(SkysongNPCs.class, configWithoutMySql());

        assertFalse(plugin.isEnabled(), "SkysongNPCs must not run without its database");
    }

    @Test
    void apiAdoptsOnlyNpcsCitizensHas() {
        OfflineSkysongNPCs plugin = OfflineSkysongNPCs.load();
        Npcs npcs = SkysongNpcsApi.get().npcs();
        UUID guard = plugin.fakeCitizens().add("Guard");

        ManagedNpc adopted = npcs.adopt(guard, NpcOwner.server());

        assertEquals(guard, adopted.id());
        assertTrue(npcs.isManaged(guard));
        assertThrows(IllegalStateException.class, () -> npcs.adopt(guard, NpcOwner.server()));
        assertThrows(IllegalArgumentException.class, () -> npcs.adopt(UUID.randomUUID(), NpcOwner.server()));
    }

    @Test
    void apiOwnerChangesAndReleasesFireEvents() {
        OfflineSkysongNPCs plugin = OfflineSkysongNPCs.load();
        Npcs npcs = SkysongNpcsApi.get().npcs();
        UUID guard = plugin.fakeCitizens().add("Guard");
        NpcOwner alice = NpcOwner.character(UUID.randomUUID(), "Default");
        npcs.adopt(guard, NpcOwner.server());

        assertEquals(alice, npcs.setOwner(guard, alice).owner());
        assertEquals(List.of(guard), npcs.ownedBy(alice).stream().map(ManagedNpc::id).toList());
        server.getPluginManager().assertEventFired(NpcOwnerChangeEvent.class,
                event -> event.getPreviousOwner().equals(NpcOwner.server()));

        assertTrue(npcs.release(guard));
        assertFalse(npcs.release(guard));
        server.getPluginManager().assertEventFired(NpcDeleteEvent.class,
                event -> event.getCause() == NpcDeleteEvent.Cause.RELEASED);
    }

    // Port 1 refuses every connection, so this never reaches a MySQL that runs nearby.
    private static YamlConfiguration configWithoutMySql() throws Exception {
        try (InputStream in = Objects.requireNonNull(
                SkysongNPCs.class.getClassLoader().getResourceAsStream("config.yml"), "config.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            config.set("database.host", "127.0.0.1");
            config.set("database.port", "1");
            return config;
        }
    }
}
