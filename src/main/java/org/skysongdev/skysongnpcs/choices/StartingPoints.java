package org.skysongdev.skysongnpcs.choices;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.items.Blueprints;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.List;
import java.util.OptionalInt;

/**
 * Gives a new NPC from a template its starting stat points: the blueprint's {@code npc_points}
 * tag when it has one, otherwise the template's {@code points}. Only NPCs with a stat sheet
 * get points.
 */
public final class StartingPoints implements NpcFactory.Created {
    /** The source of a template's starting points. */
    public static final String TEMPLATE = "template";
    /** The source of a blueprint's npc_points. */
    public static final String BLUEPRINT = "blueprint";

    private final StatPoints points;
    private final TemplateChoiceRegistry templates;
    private final Blueprints blueprints;

    public StartingPoints(StatPoints points, TemplateChoiceRegistry templates, Blueprints blueprints) {
        this.points = points;
        this.templates = templates;
        this.blueprints = blueprints;
    }

    @Override
    public void created(NpcRecord npc, NpcTemplate template, @Nullable ItemStack blueprint) {
        OptionalInt fromItem = blueprint == null ? OptionalInt.empty() : blueprints.points(blueprint);
        int amount = fromItem.orElse(templates.get(template.id()).points());
        if (amount > 0) {
            points.grant(npc.id(), fromItem.isPresent() ? BLUEPRINT : TEMPLATE, amount, List.of());
        }
    }
}
