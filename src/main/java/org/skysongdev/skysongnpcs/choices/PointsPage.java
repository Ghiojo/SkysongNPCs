package org.skysongdev.skysongnpcs.choices;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.api.choices.SpendResult;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.StatSheets;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Spending stat points: one icon per stat the NPC's points may go on. Left-click adds a point,
 * right-click takes it off again, and nothing is saved until Confirm. It is both the "points"
 * step of the setup and the stat points page of the choice menu.
 */
public final class PointsPage implements ChoicePage, SetupStep {
    public static final String ID = "points";
    private static final int MAX_STATS = 45;

    private final Guis guis;
    private final StatPoints points;
    private final StatSheets sheets;
    private final NpcLookup citizens;

    public PointsPage(Guis guis, StatPoints points, StatSheets sheets, NpcLookup citizens) {
        this.guis = guis;
        this.points = points;
        this.sheets = sheets;
        this.citizens = citizens;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int pending(UUID npc) {
        return points.unspent(npc);
    }

    @Override
    public boolean applies(NpcRecord npc) {
        return sheets.exists(npc.id()) && points.unspent(npc.id()) > 0;
    }

    @Override
    public ItemStack icon(NpcRecord npc) {
        return guis.icon(Material.EXPERIENCE_BOTTLE, "choices.points.icon",
                Map.of("points", Integer.toString(points.unspent(npc.id()))));
    }

    @Override
    public void open(Player player, NpcRecord npc, SetupFlow flow) {
        show(player, npc, () -> flow.stepDone(player, npc, ID), null);
    }

    @Override
    public void open(Player player, NpcRecord npc, Runnable back) {
        show(player, npc, back, back);
    }

    @Override
    public int respec(UUID npc) {
        return points.respec(npc);
    }

    /**
     * @param after what Confirm does once the points are spent
     * @param back  what the Back button does; null for no Back button (in the setup)
     */
    private void show(Player player, NpcRecord npc, Runnable after, Runnable back) {
        List<String> stats = points.spendable(npc.id()).stream().limit(MAX_STATS).toList();
        int rows = (stats.size() + 8) / 9 + 1;
        ChoiceGui gui = guis.page(player, rows, "choices.points.title", Map.of("npc", citizens.name(npc.id())));
        Map<String, Integer> planned = new LinkedHashMap<>();
        draw(gui, npc, stats, planned, after, back);
        guis.show(gui);
    }

    private void draw(ChoiceGui gui, NpcRecord npc, List<String> stats, Map<String, Integer> planned,
                      Runnable after, Runnable back) {
        gui.clear();
        UUID id = npc.id();
        int left = points.unspent(id) - planned.values().stream().mapToInt(Integer::intValue).sum();
        for (int i = 0; i < stats.size(); i++) {
            String stat = stats.get(i);
            int adding = planned.getOrDefault(stat, 0);
            OptionalInt base = points.base(id, stat);
            OptionalInt cap = points.cap(id, stat);
            ItemStack icon = guis.icon(Material.PAPER, "choices.points.stat", Map.of(
                    "stat", stat.toUpperCase(Locale.ROOT),
                    "base", base.isPresent() ? Integer.toString(base.getAsInt()) : "?",
                    "cap", cap.isPresent() ? Integer.toString(cap.getAsInt())
                            : guis.messages().plain("choices.points.no-cap", Map.of()),
                    "adding", Integer.toString(adding)));
            icon.setAmount(Math.max(1, Math.min(64, adding)));
            gui.button(i, icon, click -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    planned.computeIfPresent(stat, (s, n) -> n > 1 ? n - 1 : null);
                } else if (canAdd(id, stat, planned)) {
                    planned.merge(stat, 1, Integer::sum);
                }
                draw(gui, npc, stats, planned, after, back);
            });
        }
        int bottom = gui.size() - 9;
        if (back != null) {
            gui.button(bottom, guis.icon(Material.ARROW, "choices.back", Map.of()), click -> back.run());
        }
        gui.label(bottom + 4, guis.icon(Material.EXPERIENCE_BOTTLE, "choices.points.left",
                Map.of("points", Integer.toString(left))));
        gui.button(bottom + 8, guis.icon(Material.LIME_DYE, "choices.points.confirm",
                Map.of("points", Integer.toString(left))), click -> confirm(gui, npc, planned, after));
    }

    /** Whether one more point fits: points left for that stat, and room under its cap. */
    private boolean canAdd(UUID npc, String stat, Map<String, Integer> planned) {
        int total = planned.values().stream().mapToInt(Integer::intValue).sum();
        int adding = planned.getOrDefault(stat, 0);
        if (total >= points.unspent(npc) || adding >= points.available(npc, stat)) {
            return false;
        }
        OptionalInt cap = points.cap(npc, stat);
        OptionalInt base = points.base(npc, stat);
        return cap.isEmpty() || base.isEmpty() || base.getAsInt() + adding + 1 <= cap.getAsInt();
    }

    private void confirm(ChoiceGui gui, NpcRecord npc, Map<String, Integer> planned, Runnable after) {
        Player player = gui.viewer();
        planned.forEach((stat, amount) -> {
            SpendResult result = points.spend(npc.id(), stat, amount, player);
            String key = result == SpendResult.DONE ? "choices.points.spent" : "choices.points.refused";
            guis.send(player, key, Map.of("npc", citizens.name(npc.id()), "stat", stat.toUpperCase(Locale.ROOT),
                    "points", Integer.toString(amount), "reason", result.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        });
        planned.clear();
        guis.close(gui);
        after.run();
    }
}
