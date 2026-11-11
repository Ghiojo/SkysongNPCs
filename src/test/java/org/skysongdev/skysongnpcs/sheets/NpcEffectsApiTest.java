package org.skysongdev.skysongnpcs.sheets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.SkysongNpcsApi;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.sheet.NpcEffects;
import org.skysongdev.skysongstats.api.AncestryPicks;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link NpcEffects}, the API part for injuries, conditions and ancestry. */
class NpcEffectsApiTest {
    private OfflineSkysongNPCs plugin;
    private NpcEffects effects;
    private UUID guard;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        effects = SkysongNpcsApi.get().effects();
        guard = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void awardsAndRemovesInjuries() {
        assertTrue(effects.awardInjury(guard, "BRUISE"));
        assertFalse(effects.awardInjury(guard, "plague"));
        assertEquals("bruise", effects.injuries(guard).getFirst().typeId());
        assertTrue(effects.removeInjury(guard, "bruise"));
        assertFalse(effects.removeInjury(guard, "bruise"));
        assertTrue(effects.discoverInjury(guard, "broken_arm"));
        assertTrue(effects.injuries(guard).isEmpty());
    }

    @Test
    void awardsAndRemovesConditions() {
        assertEquals(3, effects.awardCondition(guard, "tired", 3).orElseThrow().level());
        assertTrue(effects.awardCondition(guard, "fever", 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> effects.awardCondition(guard, "tired", 0));
        assertEquals(List.of("tired"), effects.conditions(guard).stream().map(condition -> condition.typeId()).toList());
        assertTrue(effects.removeCondition(guard, "tired"));
        assertTrue(effects.conditions(guard).isEmpty());
    }

    @Test
    void setsAndRemovesAncestries() {
        assertTrue(effects.setAncestry(guard, "elf", AncestryPicks.builder().stats(0, "str").option(1, 0).build()));
        assertEquals("elf", effects.ancestry(guard).orElseThrow().id());
        assertFalse(effects.setAncestry(guard, "dwarf", AncestryPicks.none()));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> effects.setAncestry(guard, "elf", AncestryPicks.none()));
        assertTrue(refused.getMessage().startsWith("The picks don't fit elf: choice 1 needs"), refused.getMessage());
        assertEquals(1, effects.ancestry(guard).orElseThrow().total("str"), "a refused set changes nothing");

        assertTrue(effects.removeAncestry(guard));
        assertTrue(effects.ancestry(guard).isEmpty());
        assertFalse(effects.removeAncestry(guard));
    }

    @Test
    void refusesNpcsWithoutASheetOrUnmanaged() {
        UUID dog = plugin.fakeCitizens().add("Dog");
        assertThrows(IllegalArgumentException.class, () -> effects.injuries(dog));
        plugin.registry().adopt(dog, NpcOwner.server());
        // Its SkysongStats character was deleted by hand.
        plugin.fakeSheets().delete(dog);
        assertThrows(IllegalArgumentException.class, () -> effects.awardInjury(dog, "bruise"));
        assertThrows(IllegalArgumentException.class, () -> effects.ancestry(dog));
    }
}
