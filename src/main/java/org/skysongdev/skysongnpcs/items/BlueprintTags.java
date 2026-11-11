package org.skysongdev.skysongnpcs.items;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongitems.api.TagType;
import org.skysongdev.skysongitems.api.event.SkysongItemsRegisterEvent;

import java.util.List;

/**
 * The SkysongItems tags of blueprint items, registered on every
 * {@link SkysongItemsRegisterEvent}: SkysongItems forgets registered tags on {@code /si reload}
 * and asks again.
 */
public final class BlueprintTags implements Listener {
    /** The template a blueprint places: {@code /si tag set npc_blueprint guard_dog}. */
    public static final String BLUEPRINT = "npc_blueprint";
    /** Optional: the placed NPC's name, instead of the template's. */
    public static final String NAME = "npc_name";

    static final List<String> ALL = List.of(BLUEPRINT, NAME);

    private final Plugin plugin;

    public BlueprintTags(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRegister(SkysongItemsRegisterEvent event) {
        for (String tag : ALL) {
            if (!event.registerTag(plugin, tag, TagType.STRING)) {
                plugin.getLogger().warning("SkysongItems refused the " + tag
                        + " tag, probably because another tag has that id. Blueprint items cannot use it.");
            }
        }
    }
}
