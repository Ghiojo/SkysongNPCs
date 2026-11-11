package org.skysongdev.skysongnpcs.choices;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.menu.NpcMenuService;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Map;

/**
 * The Assign points button, {@code skysongnpcs:choices}, for anyone with the {@code choose}
 * action: opens the setup while the NPC is in setup, otherwise the choice menu, and shows how
 * many choices are pending.
 */
public final class AssignPointsButton {
    static final String NAME = "choices";
    static final int SLOT = 10;

    private final Guis guis;
    private final ChoiceMenu choices;

    public AssignPointsButton(Guis guis, ChoiceMenu choices) {
        this.guis = guis;
        this.choices = choices;
    }

    public void register(Plugin plugin, NpcMenuService menu, AccessService access) {
        menu.registerButton(plugin, NAME, SLOT, this::icon,
                context -> access.may(context.viewer(), (NpcRecord) context.npc(), NpcAction.CHOOSE),
                click -> {
                    click.close();
                    choices.open(click.viewer(), click.npc().id());
                });
    }

    ItemStack icon(MenuContext context) {
        NpcRecord npc = (NpcRecord) context.npc();
        String key = npc.state() == NpcState.SETUP ? "choices.setup-button" : "choices.button";
        return guis.icon(Material.EXPERIENCE_BOTTLE, key, Map.of("pending", Integer.toString(choices.pending(npc.id()))));
    }
}
