package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.AncestryPicksParser;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongstats.api.Ancestry;
import org.skysongdev.skysongstats.api.AncestryChoice;
import org.skysongdev.skysongstats.api.AncestryPicks;
import org.skysongdev.skysongstats.api.CharacterAncestry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * {@code /snpc ancestry} (staff): the targeted NPC's SkysongStats ancestry, as
 * {@code /ancestry} does for players.
 * <ul>
 *     <li>{@code /snpc ancestry}: what it is, and what it gave;</li>
 *     <li>{@code /snpc ancestry info <ancestry>}: its numbered choices, to type picks for;</li>
 *     <li>{@code /snpc ancestry set <ancestry> [picks]}: without picks the choices stay pending;</li>
 *     <li>{@code /snpc ancestry choose <picks>}, {@code rechoose}, {@code remove}.</li>
 * </ul>
 * Picks are typed as {@link AncestryPicksParser} reads them: {@code 1=dex,con 2=1}. Staff are
 * not held to a template's ancestry list; that list is for the owner's first setup.
 */
public final class AncestryCommand implements SubCommand {
    private static final String ANCESTRY = "ancestry";
    private static final String PICKS = "picks";

    private final SheetTargeting targeting;
    private final SheetEffects effects;
    private final PluginConfiguration configuration;

    public AncestryCommand(NpcTargeting targeting, NpcSheetService sheets, SheetEffects effects,
                           PluginConfiguration configuration) {
        this.targeting = new SheetTargeting(targeting, sheets);
        this.effects = effects;
        this.configuration = configuration;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("ancestry")
                .requires(source -> targeting.npcs().isStaff(source.getSender()))
                .executes(context -> show(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                        .then(ancestryArgument()
                                .executes(context -> info(context.getSource().getSender(),
                                        StringArgumentType.getString(context, ANCESTRY)))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("set")
                        .then(ancestryArgument()
                                .executes(context -> set(context.getSource().getSender(),
                                        StringArgumentType.getString(context, ANCESTRY), null))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PICKS,
                                                StringArgumentType.greedyString())
                                        .executes(context -> set(context.getSource().getSender(),
                                                StringArgumentType.getString(context, ANCESTRY),
                                                StringArgumentType.getString(context, PICKS))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("choose")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PICKS,
                                        StringArgumentType.greedyString())
                                .executes(context -> choose(context.getSource().getSender(),
                                        StringArgumentType.getString(context, PICKS)))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("rechoose")
                        .executes(context -> simple(context.getSource().getSender(), "ancestry.rechosen",
                                "ancestry.no-choices", effects::rechooseAncestry)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                        .executes(context -> simple(context.getSource().getSender(), "ancestry.removed",
                                "ancestry.none", effects::removeAncestry)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> ancestryArgument() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(ANCESTRY, StringArgumentType.word())
                .suggests(EffectText.suggest(() -> effects.ancestries().stream().map(Ancestry::id).toList()));
    }

    private int show(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String name = targeting.npcs().npcName(npc.get().id());
        Optional<CharacterAncestry> ancestry = effects.ancestryOf(npc.get().id());
        if (ancestry.isEmpty()) {
            targeting.npcs().send(sender, "ancestry.none", Map.of("npc", name));
            return Command.SINGLE_SUCCESS;
        }
        Map<String, Integer> given = new LinkedHashMap<>();
        ancestry.get().adjustments().forEach(adjustment -> given.merge(adjustment.stat(), adjustment.amount(), Integer::sum));
        targeting.npcs().send(sender, "ancestry.show", Map.of("npc", name,
                "ancestry", EffectText.plain(ancestry.get().name()),
                "adjustments", adjustments(given)));
        if (ancestry.get().choicesPending()) {
            targeting.npcs().send(sender, "ancestry.pending", Map.of("npc", name, "ancestry", ancestry.get().id()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandSender sender, String typed) {
        Optional<Ancestry> ancestry = find(sender, typed);
        if (ancestry.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Map<String, Integer> fixed = new LinkedHashMap<>();
        ancestry.get().adjustments().forEach(adjustment -> fixed.merge(adjustment.stat(), adjustment.amount(), Integer::sum));
        targeting.npcs().send(sender, "ancestry.info.header", Map.of(
                "ancestry", EffectText.plain(ancestry.get().name()), "adjustments", adjustments(fixed)));
        List<AncestryChoice> choices = ancestry.get().choices();
        if (choices.isEmpty()) {
            targeting.npcs().send(sender, "ancestry.info.no-choices", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        for (int i = 0; i < choices.size(); i++) {
            String number = String.valueOf(i + 1);
            if (choices.get(i) instanceof AncestryChoice.Stats stats) {
                targeting.npcs().send(sender, "ancestry.info.stats", Map.of(
                        "number", number, "name", EffectText.plain(stats.name()),
                        "amount", signed(stats.amount()), "picks", String.valueOf(stats.picks()),
                        "from", stats.anyStat() ? configuration.messages().plain("ancestry.info.any-stat", Map.of()) : String.join(", ", stats.from())));
            } else if (choices.get(i) instanceof AncestryChoice.Packages packages) {
                List<String> options = new ArrayList<>();
                for (int option = 0; option < packages.options().size(); option++) {
                    Map<String, Integer> gives = new LinkedHashMap<>();
                    packages.options().get(option).adjustments()
                            .forEach(adjustment -> gives.merge(adjustment.stat(), adjustment.amount(), Integer::sum));
                    options.add((option + 1) + ") " + EffectText.plain(packages.options().get(option).name())
                            + " (" + adjustments(gives) + ")");
                }
                targeting.npcs().send(sender, "ancestry.info.packages", Map.of(
                        "number", number, "name", EffectText.plain(packages.name()),
                        "options", String.join(", ", options)));
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandSender sender, String typed, String typedPicks) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        Optional<Ancestry> ancestry = npc.isEmpty() ? Optional.empty() : find(sender, typed);
        if (ancestry.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        UUID id = npc.get().id();
        Map<String, String> placeholders = Map.of("npc", targeting.npcs().npcName(id),
                "ancestry", EffectText.plain(ancestry.get().name()));
        if (typedPicks == null && ancestry.get().hasChoices()) {
            effects.setAncestry(id, ancestry.get());
            targeting.npcs().send(sender, "ancestry.set-pending", Map.of("npc", placeholders.get("npc"),
                    "ancestry", placeholders.get("ancestry"), "id", ancestry.get().id()));
            return Command.SINGLE_SUCCESS;
        }
        Ancestry chosen = ancestry.get();
        return withPicks(sender, chosen, typedPicks == null ? "" : typedPicks, picks -> effects.setAncestry(id, chosen, picks),
                "ancestry.set", "ancestry.unknown", placeholders);
    }

    private int choose(CommandSender sender, String typedPicks) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        UUID id = npc.get().id();
        String name = targeting.npcs().npcName(id);
        Optional<CharacterAncestry> current = effects.ancestryOf(id);
        Optional<Ancestry> ancestry = current.filter(CharacterAncestry::choicesPending)
                .flatMap(found -> effects.ancestry(found.id()));
        if (ancestry.isEmpty()) {
            targeting.npcs().send(sender, "ancestry.nothing-pending", Map.of("npc", name));
            return Command.SINGLE_SUCCESS;
        }
        return withPicks(sender, ancestry.get(), typedPicks, picks -> effects.chooseAncestry(id, picks),
                "ancestry.chosen", "ancestry.nothing-pending",
                Map.of("npc", name, "ancestry", EffectText.plain(ancestry.get().name())));
    }

    /** Reads the picks, checks them, then applies them, telling the sender what went wrong. */
    private int withPicks(CommandSender sender, Ancestry ancestry, String typed,
                          Predicate<AncestryPicks> apply, String done, String refused,
                          Map<String, String> placeholders) {
        AncestryPicks picks;
        try {
            picks = AncestryPicksParser.parse(ancestry, typed);
        } catch (IllegalArgumentException malformed) {
            targeting.npcs().send(sender, "ancestry.bad-picks", Map.of("reason", malformed.getMessage(),
                    "id", ancestry.id()));
            return Command.SINGLE_SUCCESS;
        }
        List<String> problems = effects.ancestryProblems(ancestry, picks);
        if (!problems.isEmpty()) {
            targeting.npcs().send(sender, "ancestry.bad-picks", Map.of("reason", String.join("; ", problems),
                    "id", ancestry.id()));
            return Command.SINGLE_SUCCESS;
        }
        try {
            targeting.npcs().send(sender, apply.test(picks) ? done : refused, placeholders);
        } catch (IllegalArgumentException refusedPicks) {
            targeting.npcs().send(sender, "ancestry.bad-picks", Map.of("reason", refusedPicks.getMessage(),
                    "id", ancestry.id()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int simple(CommandSender sender, String done, String refused, Predicate<UUID> action) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isPresent()) {
            boolean changed = action.test(npc.get().id());
            targeting.npcs().send(sender, changed ? done : refused,
                    Map.of("npc", targeting.npcs().npcName(npc.get().id())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private Optional<Ancestry> find(CommandSender sender, String typed) {
        Optional<Ancestry> ancestry = effects.ancestry(typed);
        if (ancestry.isEmpty()) {
            targeting.npcs().send(sender, "ancestry.unknown", Map.of("ancestry", typed));
        }
        return ancestry;
    }

    /** {@code dex +2, con -1}; {@code -} when nothing. */
    private static String adjustments(Map<String, Integer> given) {
        if (given.isEmpty()) {
            return "-";
        }
        return given.entrySet().stream().map(entry -> entry.getKey() + " " + signed(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private static String signed(int amount) {
        return amount >= 0 ? "+" + amount : String.valueOf(amount);
    }
}
