package org.skysongdev.skysongnpcs.npc;

import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.storage.NpcStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every managed NPC's record, kept in memory and written through to the {@link NpcStore}.
 * Reads never touch the database. It knows nothing of Citizens or Bukkit: the Citizens
 * listener tells it when an NPC is removed or copied, and {@link Changes} hears about every
 * change so the plugin can fire its events.
 */
public final class NpcRegistry {
    private final NpcStore store;
    private final Changes changes;
    private final Map<UUID, NpcRecord> records = new LinkedHashMap<>();

    public NpcRegistry(NpcStore store, Changes changes) {
        this.store = Objects.requireNonNull(store, "store");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    /** Reads every record from the store, replacing what is in memory. Called once on enable. */
    public void load() {
        records.clear();
        for (NpcRecord record : store.loadAll()) {
            records.put(record.id(), record);
        }
    }

    public boolean isManaged(UUID npc) {
        return records.containsKey(npc);
    }

    public Optional<NpcRecord> get(UUID npc) {
        return Optional.ofNullable(records.get(npc));
    }

    public List<NpcRecord> all() {
        return List.copyOf(records.values());
    }

    public List<NpcRecord> ownedBy(NpcOwner owner) {
        return records.values().stream().filter(record -> record.owner().equals(owner)).toList();
    }

    public List<NpcRecord> ownedByPlayer(UUID player) {
        return records.values().stream()
                .filter(record -> record.owner() instanceof NpcOwner.CharacterOwner character
                        && character.player().equals(player))
                .toList();
    }

    /**
     * Starts managing an NPC, then runs the {@link #onAdopt adopt hook}, which gives it its stat
     * sheet. The caller checks that Citizens has it.
     *
     * @throws IllegalStateException when it is managed already
     * @throws RuntimeException      what the adopt hook threw; the NPC is not managed then
     */
    public NpcRecord adopt(UUID npc, NpcOwner owner) {
        if (records.containsKey(npc)) {
            throw new IllegalStateException("NPC " + npc + " is managed already");
        }
        NpcRecord record = put(NpcRecord.adopted(npc, owner));
        try {
            adopted.accept(record);
        } catch (RuntimeException failed) {
            // Nothing was announced yet, so the record goes without an event.
            records.remove(npc);
            store.delete(npc);
            throw failed;
        }
        return record;
    }

    private Consumer<NpcRecord> adopted = record -> {
    };

    /**
     * Sets what every adopted NPC goes through right after its record exists: the plugin gives
     * it its stat sheet here. If it throws, the adoption is undone.
     */
    public void onAdopt(Consumer<NpcRecord> hook) {
        this.adopted = Objects.requireNonNull(hook, "hook");
    }

    /** Deletes an NPC's record because staff or a plugin let it go. */
    public Optional<NpcRecord> release(UUID npc) {
        return delete(npc, Changes.Deletion.RELEASED);
    }

    /** Deletes an NPC's record because Citizens removed the NPC. Nothing happens for an unmanaged NPC. */
    public Optional<NpcRecord> removed(UUID npc) {
        return delete(npc, Changes.Deletion.REMOVED);
    }

    /**
     * Gives an NPC a new owner. Nothing is saved or announced when it already has that owner.
     *
     * @throws IllegalArgumentException when the NPC is not managed
     */
    public NpcRecord setOwner(UUID npc, NpcOwner owner) {
        Objects.requireNonNull(owner, "owner");
        NpcRecord current = records.get(npc);
        if (current == null) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        if (current.owner().equals(owner)) {
            return current;
        }
        NpcRecord changed = put(current.withOwner(owner));
        changes.ownerChanged(changed, current.owner());
        return changed;
    }

    /**
     * Citizens copied an NPC: a managed original gives the copy a record of its own. Nothing
     * happens when the original is not managed.
     */
    public Optional<NpcRecord> copied(UUID original, UUID copy) {
        NpcRecord source = records.get(original);
        if (source == null) {
            return Optional.empty();
        }
        NpcRecord cloned = put(source.copiedTo(copy));
        changes.cloned(source, cloned);
        return Optional.of(cloned);
    }

    private NpcRecord put(NpcRecord record) {
        records.put(record.id(), record);
        store.save(record);
        return record;
    }

    private Optional<NpcRecord> delete(UUID npc, Changes.Deletion why) {
        NpcRecord gone = records.remove(npc);
        if (gone == null) {
            return Optional.empty();
        }
        store.delete(npc);
        changes.deleted(gone, why);
        return Optional.of(gone);
    }

    /** Hears about every change, after it is made; the plugin fires its API events from here. */
    public interface Changes {
        void ownerChanged(NpcRecord npc, NpcOwner previous);

        void cloned(NpcRecord original, NpcRecord copy);

        void deleted(NpcRecord npc, Deletion why);

        enum Deletion { REMOVED, RELEASED }
    }

    // M17: NPCs made from a template, by a blueprint item or the API.

    /**
     * Starts managing an NPC that was just made from a template, in state
     * {@link org.skysongdev.skysongnpcs.api.npc.NpcState#SETUP}. The caller checks that Citizens has it.
     *
     * @throws IllegalStateException when it is managed already
     */
    public NpcRecord create(UUID npc, NpcOwner owner, String template) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(template, "template");
        if (records.containsKey(npc)) {
            throw new IllegalStateException("NPC " + npc + " is managed already");
        }
        return put(new NpcRecord(npc, owner, org.skysongdev.skysongnpcs.api.npc.NpcState.SETUP, template));
    }

    // M18: pickup. Picking an NPC up stores it, placing it again brings back the state it had.

    /**
     * Moves an NPC to another state. Nothing is saved when it is in that state already.
     *
     * @throws IllegalArgumentException when the NPC is not managed
     */
    public NpcRecord setState(UUID npc, org.skysongdev.skysongnpcs.api.npc.NpcState state) {
        Objects.requireNonNull(state, "state");
        NpcRecord current = records.get(npc);
        if (current == null) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        if (current.state() == state) {
            return current;
        }
        return put(current.withState(state));
    }
}
