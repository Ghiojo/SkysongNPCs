package org.skysongdev.skysongnpcs.choices;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.skysongdev.skysongnpcs.choices.menu.ChoiceGui;
import org.skysongdev.skysongnpcs.choices.menu.Guis;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongnpcs.sheets.StatSheets;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPackage;
import org.skysongdev.skysongstats.api.AncestryPicks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The "ancestry" step of the setup: pick one of the template's ancestries, or Other (no
 * ancestry, for animals and creatures) when the template allows it. An ancestry with choices
 * asks for them next, one page per choice, and is saved with them through SkysongStats once
 * they fit. Nothing is saved before the last Confirm.
 */
public final class AncestryStep implements SetupStep {
    public static final String ID = "ancestry";
    private static final int ROWS = 6;
    private static final int OPTION_SLOTS = 45;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Guis guis;
    private final SheetEffects effects;
    private final StatSheets sheets;
    private final NpcLookup citizens;
    private final Function<NpcRecord, TemplateChoices> templates;

    public AncestryStep(Guis guis, SheetEffects effects, StatSheets sheets, NpcLookup citizens,
                        Function<NpcRecord, TemplateChoices> templates) {
        this.guis = guis;
        this.effects = effects;
        this.sheets = sheets;
        this.citizens = citizens;
        this.templates = templates;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean applies(NpcRecord npc) {
        return sheets.exists(npc.id()) && !templates.apply(npc).ancestries().isEmpty();
    }

    /** The ancestries the NPC's template offers that SkysongStats still has, in the template's order. */
    List<Ancestry> offered(NpcRecord npc) {
        return templates.apply(npc).ancestries().stream().map(effects::ancestry)
                .flatMap(Optional::stream).toList();
    }

    @Override
    public void open(Player player, NpcRecord npc, SetupFlow flow) {
        ChoiceGui gui = guis.page(player, ROWS, "choices.ancestry.title", Map.of("npc", citizens.name(npc.id())));
        drawList(gui, npc, flow, null);
        guis.show(gui);
    }

    /** @param selected the picked ancestry, empty for Other, null for nothing picked yet */
    private void drawList(ChoiceGui gui, NpcRecord npc, SetupFlow flow, Optional<Ancestry> selected) {
        gui.clear();
        List<Ancestry> offered = offered(npc);
        int slot = 0;
        for (Ancestry ancestry : offered) {
            if (slot >= OPTION_SLOTS - 1) {
                break;
            }
            boolean picked = selected != null && selected.isPresent() && selected.get().id().equals(ancestry.id());
            ItemStack icon = Guis.icon(material(ancestry.icon()), MINI.deserialize(ancestry.name()),
                    ancestry.lore().stream().map(MINI::deserialize).toList());
            mark(icon, picked);
            gui.button(slot++, icon, click -> drawList(gui, npc, flow, Optional.of(ancestry)));
        }
        if (templates.apply(npc).allowOther()) {
            ItemStack other = guis.icon(Material.BONE, "choices.ancestry.other", Map.of());
            mark(other, selected != null && selected.isEmpty());
            gui.button(slot, other, click -> drawList(gui, npc, flow, Optional.empty()));
        }
        if (selected != null) {
            String name = selected.map(ancestry -> plain(ancestry.name()))
                    .orElseGet(() -> guis.messages().plain("choices.ancestry.other-name", Map.of()));
            gui.button(gui.size() - 1, guis.icon(Material.LIME_DYE, "choices.ancestry.confirm", Map.of("ancestry", name)),
                    click -> {
                        if (selected.isEmpty()) {
                            finish(gui, npc, flow);
                        } else if (!selected.get().hasChoices()) {
                            save(gui, npc, flow, selected.get(), AncestryPicks.none());
                        } else {
                            drawChoice(gui, npc, flow, selected.get(), 0, new ArrayList<>());
                        }
                    });
        }
    }

    /**
     * One page per choice of the ancestry.
     *
     * @param made what earlier choices picked: stat ids, or the option's position as text
     */
    private void drawChoice(ChoiceGui gui, NpcRecord npc, SetupFlow flow, Ancestry ancestry, int index,
                            List<List<String>> made) {
        gui.clear();
        AncestryChoice choice = ancestry.choices().get(index);
        Set<String> picked = new LinkedHashSet<>();
        int needed;
        List<String> options = new ArrayList<>();
        List<ItemStack> icons = new ArrayList<>();
        if (choice instanceof AncestryChoice.Stats stats) {
            needed = stats.picks();
            List<String> from = stats.anyStat() || stats.from().isEmpty() ? sheets.statIds() : stats.from();
            for (String stat : from) {
                options.add(stat);
                icons.add(guis.icon(Material.PAPER, "choices.ancestry.stat-option", Map.of(
                        "stat", stat.toUpperCase(Locale.ROOT), "amount", signed(stats.amount()))));
            }
        } else {
            AncestryChoice.Packages packages = (AncestryChoice.Packages) choice;
            needed = 1;
            for (int i = 0; i < packages.options().size(); i++) {
                AncestryPackage option = packages.options().get(i);
                options.add(Integer.toString(i));
                icons.add(Guis.icon(material(option.icon()), MINI.deserialize(option.name()), option.adjustments().stream()
                        .map(adjustment -> guis.messages().get("choices.ancestry.package-line", Map.of(
                                "stat", adjustment.stat().toUpperCase(Locale.ROOT), "amount", signed(adjustment.amount()))))
                        .toList()));
            }
        }
        drawOptions(gui, npc, flow, ancestry, index, made, choice.name(), options, icons, picked, needed);
    }

    private void drawOptions(ChoiceGui gui, NpcRecord npc, SetupFlow flow, Ancestry ancestry, int index,
                             List<List<String>> made, String choiceName, List<String> options, List<ItemStack> icons,
                             Set<String> picked, int needed) {
        gui.clear();
        for (int i = 0; i < options.size() && i < OPTION_SLOTS; i++) {
            String option = options.get(i);
            ItemStack icon = icons.get(i).clone();
            mark(icon, picked.contains(option));
            gui.button(i, icon, click -> {
                if (!picked.remove(option)) {
                    if (needed == 1) {
                        picked.clear();
                    }
                    if (picked.size() < needed) {
                        picked.add(option);
                    }
                }
                drawOptions(gui, npc, flow, ancestry, index, made, choiceName, options, icons, picked, needed);
            });
        }
        gui.label(gui.size() - 5, guis.icon(Material.BOOK, "choices.ancestry.choice", Map.of(
                "choice", plain(choiceName), "picks", Integer.toString(needed),
                "picked", Integer.toString(picked.size()))));
        if (picked.size() == needed) {
            gui.button(gui.size() - 1, guis.icon(Material.LIME_DYE, "choices.ancestry.next", Map.of()), click -> {
                List<List<String>> soFar = new ArrayList<>(made);
                soFar.add(List.copyOf(picked));
                if (index + 1 < ancestry.choices().size()) {
                    drawChoice(gui, npc, flow, ancestry, index + 1, soFar);
                } else {
                    save(gui, npc, flow, ancestry, picks(ancestry, soFar));
                }
            });
        }
    }

    static AncestryPicks picks(Ancestry ancestry, List<List<String>> made) {
        AncestryPicks.Builder builder = AncestryPicks.builder();
        for (int i = 0; i < made.size(); i++) {
            if (ancestry.choices().get(i) instanceof AncestryChoice.Stats) {
                builder.stats(i, made.get(i).toArray(String[]::new));
            } else {
                builder.option(i, Integer.parseInt(made.get(i).getFirst()));
            }
        }
        return builder.build();
    }

    private void save(ChoiceGui gui, NpcRecord npc, SetupFlow flow, Ancestry ancestry, AncestryPicks picks) {
        Player player = gui.viewer();
        List<String> problems = effects.ancestryProblems(ancestry, picks);
        if (!problems.isEmpty()) {
            guis.send(player, "choices.ancestry.problems", Map.of("problems", String.join("; ", problems)));
            drawList(gui, npc, flow, Optional.of(ancestry));
            return;
        }
        if (!effects.setAncestry(npc.id(), ancestry, picks)) {
            guis.send(player, "choices.ancestry.failed", Map.of("ancestry", plain(ancestry.name())));
            guis.close(gui);
            return;
        }
        guis.send(player, "choices.ancestry.saved", Map.of("npc", citizens.name(npc.id()), "ancestry", plain(ancestry.name())));
        finish(gui, npc, flow);
    }

    private void finish(ChoiceGui gui, NpcRecord npc, SetupFlow flow) {
        guis.close(gui);
        flow.stepDone(gui.viewer(), npc, ID);
    }

    private static void mark(ItemStack icon, boolean picked) {
        if (picked) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
    }

    /** SkysongStats icons are a Material name or {@code model:<id>}; anything else draws as a name tag. */
    static Material material(String icon) {
        Material material = Material.matchMaterial(icon);
        return material == null || !material.isItem() || material.isAir() ? Material.NAME_TAG : material;
    }

    private static String plain(String miniMessage) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(MINI.deserialize(miniMessage));
    }

    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : Integer.toString(amount);
    }

}
