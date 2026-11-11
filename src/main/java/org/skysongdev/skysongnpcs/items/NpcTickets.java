package org.skysongdev.skysongnpcs.items;

import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.storage.NpcItemStore;
import org.skysongdev.skysongnpcs.storage.NpcItemStore.StoredItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Each NPC's item data, kept in memory and written through to the {@link NpcItemStore}: the
 * blueprint it was placed from, which its NPC item is made from, and while it is picked up the
 * ticket of the one item that may place it again, with the state it goes back to.
 */
public final class NpcTickets {
    private static final StoredItem NONE = new StoredItem(null, null, null);

    private final NpcItemStore store;
    private final Logger logger;
    private final Map<UUID, StoredItem> rows = new HashMap<>();

    public NpcTickets(NpcItemStore store, Logger logger) {
        this.store = Objects.requireNonNull(store, "store");
        this.logger = logger;
    }

    /** Reads every row from the store, replacing what is in memory. Called once on enable. */
    public void load() {
        rows.clear();
        rows.putAll(store.loadAll());
    }

    /** Remembers the blueprint a new NPC was placed from, one item of it. */
    public void rememberBlueprint(UUID npc, ItemStack blueprint) {
        StoredItem row = row(npc);
        save(npc, new StoredItem(blueprint.asOne().serializeAsBytes(), row.ticket(), row.resume()));
    }

    /** The blueprint the NPC was placed from; empty when it was not placed from one. */
    public Optional<ItemStack> blueprint(UUID npc) {
        byte[] bytes = row(npc).blueprint();
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ItemStack.deserializeBytes(bytes));
        } catch (RuntimeException unreadable) {
            logger.log(Level.WARNING, "The blueprint NPC " + npc + " was placed from could not be read,"
                    + " so its item is made from config.yml's pickup.item instead.", unreadable);
            return Optional.empty();
        }
    }

    /**
     * Gives the NPC a new ticket, so every item made before stops working.
     *
     * @param resume the state it goes back to when placed again
     * @return the new ticket
     */
    public UUID issue(UUID npc, NpcState resume) {
        UUID ticket = UUID.randomUUID();
        save(npc, new StoredItem(row(npc).blueprint(), ticket, resume));
        return ticket;
    }

    /** Whether {@code ticket} is the NPC's current one. */
    public boolean isCurrent(UUID npc, UUID ticket) {
        return ticket.equals(row(npc).ticket());
    }

    /** The state the NPC goes back to when placed again: the one it had when picked up. */
    public NpcState resume(UUID npc) {
        NpcState resume = row(npc).resume();
        return resume == null ? NpcState.PLACED : resume;
    }

    /** Forgets everything about an NPC that was deleted; its items stop working. */
    public void forget(UUID npc) {
        if (rows.remove(npc) != null) {
            store.delete(npc);
        }
    }

    private StoredItem row(UUID npc) {
        return rows.getOrDefault(npc, NONE);
    }

    private void save(UUID npc, StoredItem row) {
        rows.put(npc, row);
        store.save(npc, row);
    }
}
