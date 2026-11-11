package org.skysongdev.skysongnpcs.cloning;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.FakeSheetCopier;
import org.skysongdev.skysongnpcs.FakeStatSheets;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.event.NpcCloneEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.sheets.StatSheets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCloningTest {
    private OfflineSkysongNPCs plugin;
    private FakeStatSheets stats;
    private FakeSheetCopier copier;
    private UUID guard;
    private UUID copy;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        stats = plugin.fakeSheets();
        copier = plugin.fakeCopier();
        guard = plugin.fakeCitizens().add("Guard");
        copy = plugin.fakeCitizens().add("Guard");
        plugin.registry().adopt(guard, NpcOwner.server());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aCopyGetsItsOwnSheet() {
        plugin.sheets().create(guard, Map.of("strength", 4));

        plugin.registry().copied(guard, copy);

        assertTrue(stats.exists(copy));
        assertEquals(4, stats.value(copy, "strength"));
    }

    @Test
    void changingTheCopyLeavesTheOriginalAlone() {
        plugin.sheets().create(guard, Map.of("strength", 4));
        plugin.registry().copied(guard, copy);

        stats.editStat(copy, "strength", StatSheets.StatEdit.SET, 9);

        assertEquals(4, stats.value(guard, "strength"));
        assertEquals(9, stats.value(copy, "strength"));
    }

    @Test
    void theCopysItemsAreCountedOnItsNewSheet() {
        plugin.sheets().create(guard, Map.of());
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        plugin.fakeItems().give(copy, sword);

        plugin.registry().copied(guard, copy);

        assertEquals(List.of(sword), stats.itemCalls.getLast());
    }

    @Test
    void theSheetIsCompleteForOtherListenersOfTheCloneEvent() {
        plugin.sheets().create(guard, Map.of("strength", 2));
        List<Boolean> seen = new ArrayList<>();
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.LOW)
            public void onClone(NpcCloneEvent event) {
                seen.add(stats.exists(event.getCopy().id()));
            }
        }, plugin);

        plugin.registry().copied(guard, copy);

        assertEquals(List.of(true), seen);
    }

    @Test
    void anNpcWithoutASheetIsCopiedWithoutOne() {
        plugin.registry().copied(guard, copy);

        assertTrue(plugin.registry().isManaged(copy));
        assertFalse(stats.exists(copy));
    }

    @Test
    void copiesOfUnmanagedNpcsAreLeftAlone() {
        UUID stranger = plugin.fakeCitizens().add("Stranger");
        stats.create(stranger, Map.of());

        plugin.registry().copied(stranger, copy);

        assertFalse(stats.exists(copy));
    }

    @Test
    void aCopyThatAlreadyHasASheetKeepsIt() {
        plugin.sheets().create(guard, Map.of("strength", 4));
        stats.create(copy, Map.of("strength", 1));

        plugin.registry().copied(guard, copy);

        assertEquals(1, stats.value(copy, "strength"));
    }

    @Test
    void aFailedCopyStillLeavesTheCopyManaged() {
        plugin.sheets().create(guard, Map.of());
        copier.failure = new IllegalStateException("SkysongStats is down");

        assertDoesNotThrow(() -> plugin.registry().copied(guard, copy));
        assertTrue(plugin.registry().isManaged(copy));
    }

    @Test
    void gapsDoNotStopTheCopy() {
        plugin.sheets().create(guard, Map.of("strength", 3));
        copier.gaps = List.of("its injuries date from now");

        plugin.registry().copied(guard, copy);

        assertEquals(3, stats.value(copy, "strength"));
    }

    @Test
    void aRemovedNpcsSheetIsDeleted() {
        plugin.cloning().removed(guard);

        assertEquals(List.of(guard), copier.deleted);
    }

    @Test
    void aFailedDeleteIsOnlyLogged() {
        copier.failure = new IllegalStateException("SkysongStats is down");

        assertDoesNotThrow(() -> plugin.cloning().removed(guard));
    }
}
