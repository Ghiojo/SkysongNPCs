package org.skysongdev.skysongnpcs.access;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.storage.GrantStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every NPC's access grants, kept in memory and written through to the {@link GrantStore},
 * like {@link org.skysongdev.skysongnpcs.npc.NpcRegistry} does for records. It does not check
 * that an NPC is managed; its callers do. {@link Changes} hears about every grant made,
 * changed or deleted through {@link #set}.
 */
public final class AccessRegistry {
    private final GrantStore store;
    private final Changes changes;
    private final Map<UUID, List<AccessGrant>> grants = new HashMap<>();

    public AccessRegistry(GrantStore store, Changes changes) {
        this.store = Objects.requireNonNull(store, "store");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    /** Reads every grant from the store, replacing what is in memory. Called once on enable. */
    public void load() {
        grants.clear();
        for (GrantStore.StoredGrant stored : store.loadAll()) {
            grants.computeIfAbsent(stored.npc(), ignored -> new ArrayList<>()).add(stored.grant());
        }
    }

    /** The NPC's grants, in the order they were first made. */
    public List<AccessGrant> grants(UUID npc) {
        return List.copyOf(grants.getOrDefault(npc, List.of()));
    }

    public Optional<AccessGrant> find(UUID npc, GrantSource source, AccessGroup group) {
        return grants.getOrDefault(npc, List.of()).stream()
                .filter(grant -> grant.source().equals(source) && grant.group().equals(group))
                .findFirst();
    }

    /**
     * Sets what one group's grant from one source allows: makes it, replaces its actions, or
     * deletes it when {@code actions} is empty. Nothing is saved or announced when it already
     * allows exactly that.
     *
     * @return the grant as it is now, empty when there is none
     */
    public Optional<AccessGrant> set(UUID npc, GrantSource source, AccessGroup group, Set<NpcAction> actions) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(group, "group");
        Optional<AccessGrant> current = find(npc, source, group);
        Set<NpcAction> previous = current.map(AccessGrant::actions).orElse(Set.of());
        if (previous.equals(actions)) {
            return current;
        }
        List<AccessGrant> list = grants.computeIfAbsent(npc, ignored -> new ArrayList<>());
        Optional<AccessGrant> result;
        if (actions.isEmpty()) {
            list.remove(current.orElseThrow());
            if (list.isEmpty()) {
                grants.remove(npc);
            }
            store.delete(npc, source, group);
            result = Optional.empty();
        } else {
            AccessGrant changed = new AccessGrant(group, source, actions);
            int at = current.map(list::indexOf).orElse(-1);
            if (at < 0) {
                list.add(changed);
            } else {
                // Keeps its place, so the list stays in the order grants were first made.
                list.set(at, changed);
            }
            store.save(npc, changed);
            result = Optional.of(changed);
        }
        changes.changed(npc, group, source, previous, result.map(AccessGrant::actions).orElse(Set.of()));
        return result;
    }

    /** Deletes every grant of an NPC whose record is gone. Not announced. */
    public void deleteAll(UUID npc) {
        if (grants.remove(npc) != null) {
            store.deleteAll(npc);
        }
    }

    /** Gives a copied NPC the same grants as its original. Not announced. */
    public void copy(UUID original, UUID copy) {
        List<AccessGrant> source = grants.get(original);
        if (source == null) {
            return;
        }
        grants.put(copy, new ArrayList<>(source));
        source.forEach(grant -> store.save(copy, grant));
    }

    /** Hears about every grant made, changed or deleted, after the change; the plugin fires its event here. */
    @FunctionalInterface
    public interface Changes {
        void changed(UUID npc, AccessGroup group, GrantSource source, Set<NpcAction> previous, Set<NpcAction> current);
    }
}
