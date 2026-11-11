package org.skysongdev.skysongnpcs.storage;

import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Where access policies, their grants and which NPC follows which are kept between restarts. */
public interface PolicyStore extends AutoCloseable {

    /** Every saved policy and membership. Called once on enable, on the main thread. */
    Snapshot loadAll();

    /** Saves a new policy, with no grants. */
    void savePolicy(String id, PolicyOwner owner);

    /** Deletes a policy with its grants, and makes its NPCs follow none. */
    void deletePolicy(String id);

    /** Saves one group's grant in a policy, adding it or replacing its actions. */
    void saveGrant(String policy, AccessGroup group, Set<NpcAction> actions);

    /** Deletes one group's grant from a policy, if it is there. */
    void deleteGrant(String policy, AccessGroup group);

    /** Makes an NPC follow a policy, instead of the one it followed before. */
    void setMember(UUID npc, String policy);

    /** Makes an NPC follow no policy. */
    void removeMember(UUID npc);

    /** Finishes pending writes. */
    @Override
    void close();

    /**
     * A saved policy.
     *
     * @param grants each group's actions
     */
    record StoredPolicy(String id, PolicyOwner owner, Map<AccessGroup, Set<NpcAction>> grants) {
    }

    /**
     * Everything saved.
     *
     * @param members which policy each NPC follows
     */
    record Snapshot(List<StoredPolicy> policies, Map<UUID, String> members) {
    }
}
