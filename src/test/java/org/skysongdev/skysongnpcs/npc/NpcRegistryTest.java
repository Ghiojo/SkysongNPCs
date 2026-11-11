package org.skysongdev.skysongnpcs.npc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcRegistryTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final NpcOwner ALICE_DEFAULT = NpcOwner.character(ALICE, "Default");
    private static final NpcOwner ALICE_ROGUE = NpcOwner.character(ALICE, "Rogue");

    private InMemoryNpcStore store;
    private RecordedChanges changes;
    private NpcRegistry registry;

    @BeforeEach
    void setUp() {
        store = new InMemoryNpcStore();
        changes = new RecordedChanges();
        registry = new NpcRegistry(store, changes);
    }

    @Test
    void adoptedNpcsArePlacedWithoutTemplateAndSaved() {
        UUID npc = UUID.randomUUID();

        NpcRecord record = registry.adopt(npc, ALICE_DEFAULT);

        assertEquals(new NpcRecord(npc, ALICE_DEFAULT, NpcState.PLACED, null), record);
        assertTrue(registry.isManaged(npc));
        assertEquals(List.of(record), store.loadAll());
    }

    @Test
    void theAdoptHookSeesTheRecordAndAFailureUndoesTheAdoption() {
        UUID npc = UUID.randomUUID();
        List<UUID> seen = new ArrayList<>();
        registry.onAdopt(record -> {
            seen.add(record.id());
            assertTrue(registry.isManaged(record.id()));
            throw new IllegalStateException("no sheet");
        });

        assertThrows(IllegalStateException.class, () -> registry.adopt(npc, NpcOwner.server()));

        assertEquals(List.of(npc), seen);
        assertFalse(registry.isManaged(npc));
        assertTrue(store.loadAll().isEmpty());
        assertTrue(changes.seen.isEmpty());
    }

    @Test
    void adoptingTwiceIsRefused() {
        UUID npc = UUID.randomUUID();
        registry.adopt(npc, NpcOwner.server());

        assertThrows(IllegalStateException.class, () -> registry.adopt(npc, ALICE_DEFAULT));
    }

    @Test
    void loadReadsWhatWasSaved() {
        NpcRecord saved = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);

        NpcRegistry restarted = new NpcRegistry(store, changes);
        restarted.load();

        assertEquals(Optional.of(saved), restarted.get(saved.id()));
    }

    @Test
    void listsByCharacterAndByPlayer() {
        NpcRecord defaultNpc = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);
        NpcRecord rogueNpc = registry.adopt(UUID.randomUUID(), ALICE_ROGUE);
        NpcRecord serverNpc = registry.adopt(UUID.randomUUID(), NpcOwner.server());

        assertEquals(List.of(defaultNpc), registry.ownedBy(ALICE_DEFAULT));
        assertEquals(List.of(serverNpc), registry.ownedBy(NpcOwner.server()));
        assertEquals(List.of(defaultNpc, rogueNpc), registry.ownedByPlayer(ALICE));
        assertEquals(List.of(defaultNpc, rogueNpc, serverNpc), registry.all());
    }

    @Test
    void ownerChangesAreSavedAndAnnounced() {
        NpcRecord npc = registry.adopt(UUID.randomUUID(), NpcOwner.server());

        NpcRecord changed = registry.setOwner(npc.id(), ALICE_ROGUE);

        assertEquals(ALICE_ROGUE, changed.owner());
        assertEquals(List.of(changed), store.loadAll());
        assertEquals(List.of("owner " + npc.id() + " was " + NpcOwner.server()), changes.seen);
    }

    @Test
    void theSameOwnerIsNotAnnouncedAgain() {
        NpcRecord npc = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);

        registry.setOwner(npc.id(), ALICE_DEFAULT);

        assertTrue(changes.seen.isEmpty());
    }

    @Test
    void settingTheOwnerOfAnUnmanagedNpcIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> registry.setOwner(UUID.randomUUID(), ALICE_DEFAULT));
    }

    @Test
    void releaseAndRemovalDeleteTheRecordWithTheirCause() {
        NpcRecord released = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);
        NpcRecord removed = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);

        assertEquals(Optional.of(released), registry.release(released.id()));
        assertEquals(Optional.of(removed), registry.removed(removed.id()));

        assertFalse(registry.isManaged(released.id()));
        assertTrue(store.loadAll().isEmpty());
        assertEquals(List.of("deleted " + released.id() + " RELEASED", "deleted " + removed.id() + " REMOVED"),
                changes.seen);
    }

    @Test
    void removingAPlainCitizensNpcDoesNothing() {
        assertEquals(Optional.empty(), registry.removed(UUID.randomUUID()));
        assertTrue(changes.seen.isEmpty());
    }

    @Test
    void aCopyGetsItsOwnRecord() {
        NpcRecord original = registry.adopt(UUID.randomUUID(), ALICE_DEFAULT);
        UUID copy = UUID.randomUUID();

        Optional<NpcRecord> cloned = registry.copied(original.id(), copy);

        assertEquals(Optional.of(new NpcRecord(copy, ALICE_DEFAULT, NpcState.PLACED, null)), cloned);
        assertEquals(List.of("cloned " + original.id() + " to " + copy), changes.seen);

        // Changing the copy leaves the original as it was.
        registry.setOwner(copy, NpcOwner.server());
        assertEquals(ALICE_DEFAULT, registry.get(original.id()).orElseThrow().owner());
    }

    @Test
    void copyingAPlainCitizensNpcDoesNothing() {
        UUID copy = UUID.randomUUID();

        assertEquals(Optional.empty(), registry.copied(UUID.randomUUID(), copy));
        assertFalse(registry.isManaged(copy));
    }

    private static final class RecordedChanges implements NpcRegistry.Changes {
        private final List<String> seen = new ArrayList<>();

        @Override
        public void ownerChanged(NpcRecord npc, NpcOwner previous) {
            seen.add("owner " + npc.id() + " was " + previous);
        }

        @Override
        public void cloned(NpcRecord original, NpcRecord copy) {
            seen.add("cloned " + original.id() + " to " + copy.id());
        }

        @Override
        public void deleted(NpcRecord npc, Deletion why) {
            seen.add("deleted " + npc.id() + " " + why);
        }
    }

    // M17

    @Test
    void createdNpcsWaitForSetupWithTheirTemplate() {
        UUID npc = UUID.randomUUID();

        NpcRecord record = registry.create(npc, ALICE_DEFAULT, "guard_dog");

        assertEquals(new NpcRecord(npc, ALICE_DEFAULT, NpcState.SETUP, "guard_dog"), record);
        assertEquals(List.of(record), store.loadAll());
        assertThrows(IllegalStateException.class, () -> registry.create(npc, ALICE_DEFAULT, "guard_dog"));
    }
}
