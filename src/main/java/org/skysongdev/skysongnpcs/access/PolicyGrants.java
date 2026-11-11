package org.skysongdev.skysongnpcs.access;

import org.skysongdev.skysongnpcs.api.access.AccessGrant;

import java.util.List;
import java.util.UUID;

/** The grants an NPC gets from the access policy it follows, on top of its own. */
@FunctionalInterface
public interface PolicyGrants {
    /** No policies: NPCs have only their own grants. */
    PolicyGrants NONE = npc -> List.of();

    /** The grants of the policy the NPC follows, empty when it follows none. */
    List<AccessGrant> grants(UUID npc);
}
