package org.skysongdev.skysongnpcs;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.apiimpl.SkysongNpcsApiImpl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkysongNPCsTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registersTheApiWhileEnabled() {
        SkysongNPCs plugin = MockBukkit.load(SkysongNPCs.class);

        assertEquals(SkysongNpcsApiImpl.API_VERSION, SkysongNpcsApi.get().apiVersion());

        MockBukkit.getMock().getPluginManager().disablePlugin(plugin);
        assertThrows(IllegalStateException.class, SkysongNpcsApi::get);
    }

    @Test
    void dependsOnCitizensStatsAndItems() {
        SkysongNPCs plugin = MockBukkit.load(SkysongNPCs.class);

        assertEquals(List.of("Citizens", "SkysongStats", "Skysong_Items"), plugin.getPluginMeta().getPluginDependencies());
        assertEquals(List.of("SkysongWardrobe", "LuckPerms", "WorldGuard"), plugin.getPluginMeta().getPluginSoftDependencies());
    }
}
