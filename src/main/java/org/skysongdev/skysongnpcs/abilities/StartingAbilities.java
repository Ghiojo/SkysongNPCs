package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.items.Blueprints;
import org.skysongdev.skysongnpcs.items.NpcFactory;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Gives a new NPC from a template its starting abilities: the blueprint's
 * {@code npc_abilities} tag when it has one, otherwise the template's {@code abilities}. Ids
 * abilities.yml does not have are logged and skipped, so a stale blueprint still places.
 */
public final class StartingAbilities implements NpcFactory.Created {
    /** The source of abilities a blueprint item's own list gave. */
    public static final String BLUEPRINT = "blueprint";
    /** The source of abilities the template gave. */
    public static final String TEMPLATE = "template";

    private final AbilityService abilities;
    private final TemplateAbilities templates;
    private final Blueprints blueprints;
    private final Logger logger;

    public StartingAbilities(AbilityService abilities, TemplateAbilities templates, Blueprints blueprints,
                             Logger logger) {
        this.abilities = abilities;
        this.templates = templates;
        this.blueprints = blueprints;
        this.logger = logger;
    }

    @Override
    public void created(NpcRecord npc, NpcTemplate template, @Nullable ItemStack blueprint) {
        Optional<List<String>> fromItem = blueprint == null ? Optional.empty() : blueprints.abilities(blueprint);
        List<String> ids = fromItem.orElseGet(() -> templates.of(template.id()));
        String source = fromItem.isPresent() ? BLUEPRINT : TEMPLATE;
        for (String id : ids) {
            if (abilities.definitions().get(id).isEmpty()) {
                logger.warning("NPC " + npc.id() + " from template " + template.id() + " should get the ability " + id
                        + ", which abilities.yml does not have. It was skipped.");
                continue;
            }
            abilities.grant(npc.id(), id, source);
        }
    }
}
