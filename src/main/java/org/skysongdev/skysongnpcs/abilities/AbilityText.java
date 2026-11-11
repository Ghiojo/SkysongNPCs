package org.skysongdev.skysongnpcs.abilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** How abilities are written: one word each, its description shown on hover or as lore. */
public final class AbilityText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final PluginConfiguration configuration;

    public AbilityText(PluginConfiguration configuration) {
        this.configuration = configuration;
    }

    /** The ability's name; hovering it shows its description. */
    public Component word(AbilityDefinition ability) {
        Component name = MINI_MESSAGE.deserialize(ability.name());
        if (ability.description().isBlank()) {
            return name;
        }
        return name.hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize(ability.description())));
    }

    /**
     * The line under the stat sheet: messages.yml's {@code abilities.sheet.label}, then every
     * ability as a hover word.
     *
     * @return empty when the NPC has no abilities
     */
    public Optional<Component> sheetLine(List<AbilityDefinition> abilities) {
        if (abilities.isEmpty()) {
            return Optional.empty();
        }
        Component separator = configuration.messages().get("abilities.sheet.separator");
        Component words = Component.join(JoinConfiguration.separator(separator),
                abilities.stream().map(this::word).toList());
        return Optional.of(configuration.messages().get("abilities.sheet.label").append(words));
    }

    /** The ability's icon in the abilities menu: its name, and its description as lore. */
    public ItemStack icon(AbilityDefinition ability) {
        ItemStack icon = new ItemStack(ability.icon());
        icon.editMeta(meta -> {
            meta.displayName(itemText(configuration.messages().get("abilities.menu.ability-name")
                    .append(MINI_MESSAGE.deserialize(ability.name()))));
            if (!ability.description().isBlank()) {
                meta.lore(Arrays.stream(ability.description().split("\n"))
                        .map(line -> itemText(MINI_MESSAGE.deserialize(line))).toList());
            }
        });
        return icon;
    }

    /** The item an NPC without abilities shows in the menu. */
    public ItemStack none(String npc) {
        ItemStack icon = new ItemStack(org.bukkit.Material.BARRIER);
        icon.editMeta(meta -> meta.displayName(itemText(
                configuration.messages().get("abilities.menu.none", Map.of("npc", npc)))));
        return icon;
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
