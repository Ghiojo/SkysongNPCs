package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcAppearance;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SkysongNpcsApi#appearance()}. */
class AppearanceApiTest {
    private OfflineSkysongNPCs plugin;
    private NpcAppearance appearance;
    private UUID guard;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        appearance = SkysongNpcsApi.get().appearance();
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void changesEverythingWithoutThePlayerRules() {
        appearance.rename(guard, "<gold>A very long name that players could not type");
        appearance.setSkin(guard, "Notch");
        appearance.setNameplate(guard, Nameplate.HIDDEN);
        appearance.setPose(guard, NpcPose.LYING);
        appearance.setGlow(guard, NamedTextColor.GOLD);

        assertEquals("<gold>A very long name that players could not type", appearance.name(guard));
        assertEquals(Optional.of("Notch"), appearance.skin(guard));
        assertEquals(Nameplate.HIDDEN, appearance.nameplate(guard));
        assertEquals(NpcPose.LYING, appearance.pose(guard));
        assertEquals(Optional.of(NamedTextColor.GOLD), appearance.glow(guard));
    }

    @Test
    void skinsFromLinksComplete() {
        assertTrue(appearance.setSkinFromUrl(guard, "https://example.com/knight.png", false).isDone());
        assertThrows(IllegalArgumentException.class, () -> appearance.setSkinFromUrl(guard, "file:///etc/passwd", false));
    }

    @Test
    void onlyManagedNpcs() {
        UUID villager = plugin.fakeCitizens().add("Villager");

        assertThrows(IllegalArgumentException.class, () -> appearance.setSitting(villager, true));
        assertThrows(IllegalArgumentException.class, () -> appearance.rename(guard, " "));
    }
}
