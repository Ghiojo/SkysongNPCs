package org.skysongdev.skysongnpcs.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.storage.InMemoryNpcItemStore;

import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NPC tickets and blueprints, kept through the store across restarts. */
class NpcTicketsTest {
    private static final UUID NPC = UUID.randomUUID();
    private final InMemoryNpcItemStore store = new InMemoryNpcItemStore();

    @BeforeEach
    void setUp() {
        // Items are serialised through the server.
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void ticketsBlueprintsAndResumeStatesSurviveARestart() {
        NpcTickets tickets = tickets();
        ItemStack blueprint = new ItemStack(Material.PAPER, 5);
        tickets.rememberBlueprint(NPC, blueprint);
        UUID ticket = tickets.issue(NPC, NpcState.SETUP);

        NpcTickets restarted = tickets();
        restarted.load();

        assertTrue(restarted.isCurrent(NPC, ticket));
        assertEquals(NpcState.SETUP, restarted.resume(NPC));
        assertEquals(new ItemStack(Material.PAPER, 1), restarted.blueprint(NPC).orElseThrow());
    }

    @Test
    void aNewTicketVoidsTheOldOneAndKeepsTheBlueprint() {
        NpcTickets tickets = tickets();
        tickets.rememberBlueprint(NPC, new ItemStack(Material.PAPER));
        UUID first = tickets.issue(NPC, NpcState.PLACED);

        UUID second = tickets.issue(NPC, NpcState.PLACED);

        assertFalse(tickets.isCurrent(NPC, first));
        assertTrue(tickets.isCurrent(NPC, second));
        assertTrue(tickets.blueprint(NPC).isPresent());
    }

    @Test
    void anNpcWithNothingStoredHasNoTicketAndResumesPlaced() {
        NpcTickets tickets = tickets();

        assertFalse(tickets.isCurrent(NPC, UUID.randomUUID()));
        assertEquals(NpcState.PLACED, tickets.resume(NPC));
        assertTrue(tickets.blueprint(NPC).isEmpty());
    }

    @Test
    void forgettingDeletesTheRow() {
        NpcTickets tickets = tickets();
        UUID ticket = tickets.issue(NPC, NpcState.PLACED);

        tickets.forget(NPC);

        assertFalse(tickets.isCurrent(NPC, ticket));
        assertTrue(store.loadAll().isEmpty());
    }

    private NpcTickets tickets() {
        return new NpcTickets(store, Logger.getLogger("test"));
    }
}
