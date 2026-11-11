package org.skysongdev.skysongnpcs.picks;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

/** Gives a new NPC from a template the template's proficiency picks. Only NPCs with a stat sheet get picks. */
public final class StartingPicks implements NpcFactory.Created {
    private final ProficiencyPicks picks;
    private final TemplatePicks templates;

    public StartingPicks(ProficiencyPicks picks, TemplatePicks templates) {
        this.picks = picks;
        this.templates = templates;
    }

    @Override
    public void created(NpcRecord npc, NpcTemplate template, @Nullable ItemStack blueprint) {
        if (template.sheet()) {
            templates.grant(picks, npc.id(), template.id());
        }
    }
}
