package org.skysongdev.skysongnpcs.abilities;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.ability.AbilityChoiceResult;
import org.skysongdev.skysongnpcs.choices.ChoicePage;
import org.skysongdev.skysongnpcs.choices.SetupFlow;
import org.skysongdev.skysongnpcs.choices.SetupStep;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Making ability choices, one grant at a time: an icon per ability the grant offers that the
 * NPC does not have. Clicking selects or unselects it, up to the choices left, and nothing is
 * granted until Confirm. After Confirm the next grant with something to choose opens, and after
 * the last one the page is done. It is both the "ability-choices" step of the setup (after the
 * proficiency picks) and a page of the choice menu.
 */
public final class AbilityChoicePage implements ChoicePage, SetupStep {
    public static final String ID = "ability-choices";
    private static final int MAX_OPTIONS = 45;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final Guis guis;
    private final AbilityChoiceService choices;
    private final AbilityRegistry definitions;
    private final NpcLookup citizens;

    public AbilityChoicePage(Guis guis, AbilityChoiceService choices, AbilityRegistry definitions, NpcLookup citizens) {
        this.guis = guis;
        this.choices = choices;
        this.definitions = definitions;
        this.citizens = citizens;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int pending(UUID npc) {
        return choices.pending(npc);
    }

    @Override
    public boolean applies(NpcRecord npc) {
        return !choosable(npc.id()).isEmpty();
    }

    @Override
    public ItemStack icon(NpcRecord npc) {
        return guis.icon(Material.NETHER_STAR, "ability-choices.icon",
                Map.of("left", Integer.toString(choices.pending(npc.id()))));
    }

    @Override
    public void open(Player player, NpcRecord npc, SetupFlow flow) {
        show(player, npc, choosable(npc.id()), () -> flow.stepDone(player, npc, ID), null);
    }

    @Override
    public void open(Player player, NpcRecord npc, Runnable back) {
        List<UUID> grants = choosable(npc.id());
        if (grants.isEmpty()) {
            guis.send(player, "ability-choices.nothing", Map.of("npc", citizens.name(npc.id())));
            back.run();
            return;
        }
        show(player, npc, grants, back, back);
    }

    @Override
    public int respec(UUID npc, @Nullable String source) {
        return choices.respec(npc, source);
    }

    private List<UUID> choosable(UUID npc) {
        return choices.grants(npc).stream().filter(choices::choosable).map(AbilityChoiceGrant::id).toList();
    }

    /**
     * Shows the first of {@code grants} that still has something to choose.
     *
     * @param after what runs once every grant was confirmed
     * @param back  what the Back button does; null for no Back button (in the setup)
     */
    private void show(Player player, NpcRecord npc, List<UUID> grants, Runnable after, Runnable back) {
        List<UUID> left = new ArrayList<>(grants);
        while (!left.isEmpty()) {
            AbilityChoiceGrant grant = choices.grant(left.removeFirst()).orElse(null);
            if (grant == null || !choices.choosable(grant)) {
                continue;
            }
            List<String> options = choices.options(grant).stream().limit(MAX_OPTIONS).toList();
            int rows = (options.size() + 8) / 9 + 1;
            ChoiceGui gui = guis.page(player, rows, "ability-choices.title", placeholders(grant, Map.of()));
            draw(gui, grant, options, new LinkedHashSet<>(), () -> show(player, npc, left, after, back), back);
            guis.show(gui);
            return;
        }
        after.run();
    }

    private void draw(ChoiceGui gui, AbilityChoiceGrant grant, List<String> options, Set<String> selected,
                      Runnable next, Runnable back) {
        gui.clear();
        int left = choices.remaining(grant) - selected.size();
        for (int i = 0; i < options.size(); i++) {
            String ability = options.get(i);
            AbilityDefinition definition = definitions.get(ability).orElse(null);
            if (definition == null) {
                continue;
            }
            gui.button(i, icon(grant, definition, selected.contains(ability)), click -> {
                if (!selected.remove(ability)) {
                    if (left < 1) {
                        return;
                    }
                    selected.add(ability);
                }
                draw(gui, grant, options, selected, next, back);
            });
        }
        int bottom = gui.size() - 9;
        if (back != null) {
            gui.button(bottom, guis.icon(Material.ARROW, "choices.back", Map.of()), click -> back.run());
        }
        gui.label(bottom + 4, guis.icon(Material.PAPER, "ability-choices.left",
                placeholders(grant, Map.of("left", Integer.toString(left)))));
        gui.button(bottom + 8, guis.icon(Material.LIME_DYE, "ability-choices.confirm",
                        placeholders(grant, Map.of("count", Integer.toString(selected.size())))),
                click -> confirm(gui, grant, selected, next));
    }

    /** The ability's own icon, named by messages.yml, with its description and a click hint as lore. */
    private ItemStack icon(AbilityChoiceGrant grant, AbilityDefinition ability, boolean selected) {
        String key = selected ? "ability-choices.selected" : "ability-choices.option";
        Map<String, String> placeholders = placeholders(grant, Map.of("ability", plainName(ability)));
        List<Component> lore = new ArrayList<>();
        if (!ability.description().isBlank()) {
            Arrays.stream(ability.description().split("\n")).map(MINI_MESSAGE::deserialize).forEach(lore::add);
        }
        lore.addAll(guis.messages().lines(key + ".lore", placeholders));
        ItemStack icon = Guis.icon(ability.icon(), guis.messages().get(key + ".name", placeholders), lore);
        if (selected) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    private void confirm(ChoiceGui gui, AbilityChoiceGrant grant, Set<String> selected, Runnable next) {
        Player player = gui.viewer();
        if (!selected.isEmpty()) {
            AbilityChoiceResult result = choices.choose(player, grant.id(), List.copyOf(selected));
            if (result == AbilityChoiceResult.DONE) {
                for (String ability : selected) {
                    String name = definitions.get(ability).map(AbilityChoicePage::plainName).orElse(ability);
                    guis.send(player, "ability-choices.chosen", placeholders(grant, Map.of("ability", name)));
                }
            } else {
                guis.send(player, resultKey(result), placeholders(grant, Map.of()));
            }
        }
        selected.clear();
        guis.close(gui);
        next.run();
    }

    private Map<String, String> placeholders(AbilityChoiceGrant grant, Map<String, String> more) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("npc", citizens.name(grant.npc()));
        placeholders.put("left", Integer.toString(choices.remaining(grant)));
        placeholders.putAll(more);
        return placeholders;
    }

    /** An ability's name without its MiniMessage tags, for messages. */
    public static String plainName(AbilityDefinition ability) {
        return MINI_MESSAGE.stripTags(ability.name());
    }

    /** The messages.yml text that explains a choice that was not saved. */
    public static String resultKey(AbilityChoiceResult result) {
        return "ability-choices.result." + result.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
