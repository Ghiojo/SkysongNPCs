package org.skysongdev.skysongnpcs.picks;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.choices.ChoicePage;
import org.skysongdev.skysongnpcs.choices.SetupFlow;
import org.skysongdev.skysongnpcs.choices.SetupStep;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spending proficiency picks, one grant at a time: an icon per proficiency of the grant's skill
 * the NPC can still raise. Clicking selects or unselects it, up to the grant's picks left, and
 * nothing is saved until Confirm. After Confirm the next grant with something to pick opens,
 * and after the last one the page is done. It is both the "proficiency-picks" step of the
 * setup (after the stat points) and the picks page of the choice menu.
 */
public final class PicksPage implements ChoicePage, SetupStep {
    public static final String ID = "proficiency-picks";
    private static final int MAX_OPTIONS = 45;

    private final Guis guis;
    private final ProficiencyPicks picks;
    private final NpcLookup citizens;

    public PicksPage(Guis guis, ProficiencyPicks picks, NpcLookup citizens) {
        this.guis = guis;
        this.picks = picks;
        this.citizens = citizens;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int pending(UUID npc) {
        return picks.pending(npc);
    }

    /** Only when there is something to pick: a pick left on a grant whose skill has a proficiency below its tier. */
    @Override
    public boolean applies(NpcRecord npc) {
        return !spendable(npc.id()).isEmpty();
    }

    @Override
    public ItemStack icon(NpcRecord npc) {
        return guis.icon(Material.ENCHANTED_BOOK, "picks.icon", Map.of("left", Integer.toString(picks.pending(npc.id()))));
    }

    @Override
    public void open(Player player, NpcRecord npc, SetupFlow flow) {
        show(player, npc, spendable(npc.id()), () -> flow.stepDone(player, npc, ID), null);
    }

    @Override
    public void open(Player player, NpcRecord npc, Runnable back) {
        List<UUID> grants = spendable(npc.id());
        if (grants.isEmpty()) {
            guis.send(player, "picks.nothing", Map.of("npc", citizens.name(npc.id())));
            back.run();
            return;
        }
        show(player, npc, grants, back, back);
    }

    @Override
    public int respec(UUID npc, @Nullable String source) {
        return picks.respec(npc, source);
    }

    private List<UUID> spendable(UUID npc) {
        return picks.open(npc).stream().filter(grant -> !picks.options(grant).isEmpty()).map(PickGrant::id).toList();
    }

    /**
     * Shows the first of {@code grants} that still has something to pick.
     *
     * @param after what runs once every grant was confirmed
     * @param back  what the Back button does; null for no Back button (in the setup)
     */
    private void show(Player player, NpcRecord npc, List<UUID> grants, Runnable after, Runnable back) {
        List<UUID> left = new ArrayList<>(grants);
        while (!left.isEmpty()) {
            PickGrant grant = picks.grant(left.removeFirst()).orElse(null);
            if (grant == null || picks.remaining(grant) == 0) {
                continue;
            }
            List<String> options = picks.options(grant).stream().limit(MAX_OPTIONS).toList();
            if (options.isEmpty()) {
                continue;
            }
            int rows = (options.size() + 8) / 9 + 1;
            ChoiceGui gui = guis.page(player, rows, "picks.title", placeholders(grant, Map.of()));
            draw(gui, grant, options, new LinkedHashSet<>(), () -> show(player, npc, left, after, back), back);
            guis.show(gui);
            return;
        }
        after.run();
    }

    private void draw(ChoiceGui gui, PickGrant grant, List<String> options, Set<String> selected,
                      Runnable next, Runnable back) {
        gui.clear();
        ProficiencyCatalog catalog = picks.catalog();
        int left = picks.remaining(grant) - selected.size();
        for (int i = 0; i < options.size(); i++) {
            String proficiency = options.get(i);
            boolean chosen = selected.contains(proficiency);
            ItemStack icon = guis.icon(chosen ? Material.ENCHANTED_BOOK : Material.BOOK,
                    chosen ? "picks.selected" : "picks.option", placeholders(grant, Map.of(
                            "proficiency", catalog.proficiencyName(proficiency),
                            "current", catalog.tier(grant.npc(), proficiency).map(catalog::tierName).orElse("?"))));
            gui.button(i, icon, click -> {
                if (!selected.remove(proficiency)) {
                    if (selected.size() >= picks.remaining(grant)) {
                        return;
                    }
                    selected.add(proficiency);
                }
                draw(gui, grant, options, selected, next, back);
            });
        }
        int bottom = gui.size() - 9;
        if (back != null) {
            gui.button(bottom, guis.icon(Material.ARROW, "choices.back", Map.of()), click -> back.run());
        }
        gui.label(bottom + 4, guis.icon(Material.PAPER, "picks.left",
                placeholders(grant, Map.of("left", Integer.toString(left)))));
        gui.button(bottom + 8, guis.icon(Material.LIME_DYE, "picks.confirm",
                placeholders(grant, Map.of("count", Integer.toString(selected.size())))),
                click -> confirm(gui, grant, selected, next));
    }

    private void confirm(ChoiceGui gui, PickGrant grant, Set<String> selected, Runnable next) {
        Player player = gui.viewer();
        if (!selected.isEmpty()) {
            PickResult result = picks.pick(player, grant.id(), List.copyOf(selected));
            if (result == PickResult.DONE) {
                for (String proficiency : selected) {
                    guis.send(player, "picks.picked", placeholders(grant,
                            Map.of("proficiency", picks.catalog().proficiencyName(proficiency))));
                }
            } else {
                guis.send(player, resultKey(result), placeholders(grant, Map.of()));
            }
        }
        selected.clear();
        guis.close(gui);
        next.run();
    }

    private Map<String, String> placeholders(PickGrant grant, Map<String, String> more) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("npc", citizens.name(grant.npc()));
        placeholders.put("skill", picks.catalog().skillName(grant.skill()));
        placeholders.put("tier", picks.catalog().tierName(grant.tier()));
        placeholders.put("left", Integer.toString(picks.remaining(grant)));
        placeholders.putAll(more);
        return placeholders;
    }

    /** The messages.yml text that explains a pick that was not saved. */
    public static String resultKey(PickResult result) {
        return "picks.result." + result.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
