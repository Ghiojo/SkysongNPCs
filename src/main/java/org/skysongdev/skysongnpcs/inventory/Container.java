package org.skysongdev.skysongnpcs.inventory;

import org.skysongdev.skysongnpcs.api.access.NpcAction;

import java.util.Locale;

/** The two item holders an NPC has: what it carries and what it wears. */
public enum Container {
    INVENTORY(NpcAction.INVENTORY_VIEW, NpcAction.INVENTORY_EDIT),
    EQUIPMENT(NpcAction.EQUIPMENT_VIEW, NpcAction.EQUIPMENT_EDIT);

    private final NpcAction view;
    private final NpcAction edit;

    Container(NpcAction view, NpcAction edit) {
        this.view = view;
        this.edit = edit;
    }

    public NpcAction view() {
        return view;
    }

    public NpcAction edit() {
        return edit;
    }

    /** Its section in messages.yml, under {@code containers:}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
