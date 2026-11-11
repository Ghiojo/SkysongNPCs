package org.skysongdev.skysongnpcs.access;

import org.junit.jupiter.api.Test;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The API's access types: actions, groups, sources and grants. */
class AccessModelTest {

    @Test
    void actionsHaveThePlansIds() {
        assertEquals("stats-view", NpcAction.STATS_VIEW.key());
        assertEquals(Optional.of(NpcAction.WARDROBE_EDIT), NpcAction.fromKey(" Wardrobe-Edit "));
        assertEquals(Optional.empty(), NpcAction.fromKey("fly"));
        for (NpcAction action : NpcAction.values()) {
            assertEquals(Optional.of(action), NpcAction.fromKey(action.key()));
        }
    }

    @Test
    void anEditCoversItsViewOnly() {
        assertTrue(NpcAction.INVENTORY_EDIT.covers(NpcAction.INVENTORY_VIEW));
        assertTrue(NpcAction.INVENTORY_EDIT.covers(NpcAction.INVENTORY_EDIT));
        assertFalse(NpcAction.INVENTORY_VIEW.covers(NpcAction.INVENTORY_EDIT));
        assertFalse(NpcAction.INVENTORY_EDIT.covers(NpcAction.EQUIPMENT_VIEW));
    }

    @Test
    void groupsReadWhatTheyWrite() {
        UUID alice = UUID.randomUUID();
        AccessGroup character = AccessGroup.player(alice, "Pirate Queen");

        assertEquals("player:" + alice + "/Pirate Queen", character.toString());
        assertEquals(character, AccessGroup.parse(character.toString()));
        assertEquals(new AccessGroup("economy", "smithy/workers"), AccessGroup.parse("Economy:smithy/workers"));
        assertThrows(IllegalArgumentException.class, () -> AccessGroup.parse("nocolon"));
        assertThrows(IllegalArgumentException.class, () -> AccessGroup.parse("perm:"));
        assertThrows(IllegalArgumentException.class, () -> AccessGroup.parse(":id"));
    }

    @Test
    void pluginSourcesIgnoreCaseAndCannotBeTheOwner() {
        assertEquals(GrantSource.plugin("skysongeconomy"), GrantSource.plugin("SkysongEconomy"));
        assertEquals("owner", GrantSource.owner().key());
        assertThrows(IllegalArgumentException.class, () -> GrantSource.plugin("Owner"));
        assertThrows(IllegalArgumentException.class, () -> GrantSource.plugin("two words"));
    }

    @Test
    void aGrantAllowsItsActionsAndTheViewsTheyCover() {
        AccessGrant grant = new AccessGrant(AccessGroup.permission("x"), GrantSource.owner(),
                Set.of(NpcAction.INFO, NpcAction.WARDROBE_EDIT));

        assertTrue(grant.allows(NpcAction.INFO));
        assertTrue(grant.allows(NpcAction.WARDROBE_VIEW));
        assertFalse(grant.allows(NpcAction.PICKUP));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessGrant(AccessGroup.permission("x"), GrantSource.owner(), Set.of()));
        assertThrows(UnsupportedOperationException.class, () -> grant.actions().add(NpcAction.MANAGE));
    }
}
