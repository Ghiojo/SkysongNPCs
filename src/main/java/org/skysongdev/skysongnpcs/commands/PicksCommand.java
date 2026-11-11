package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.picks.PickGrant;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.picks.PicksPage;
import org.skysongdev.skysongnpcs.picks.ProficiencyCatalog;
import org.skysongdev.skysongnpcs.picks.ProficiencyPicks;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Proficiency picks on the targeted NPC.
 * <ul>
 *   <li>{@code /snpc picks}: its grants and how many picks each has left ({@code choose}).</li>
 *   <li>{@code /snpc picks pick <proficiency>}: spends one pick of that proficiency's skill
 *       ({@code choose}); the picks page of {@code /snpc choose} does the same with clicks.</li>
 *   <li>{@code /snpc picks grant <skill> <tier> [count]} (staff): gives picks, source {@code staff}.</li>
 *   <li>{@code /snpc picks take <source> [count]} (staff): takes back unspent picks of a source.</li>
 * </ul>
 * Spent picks are given back by {@code /snpc respec}, with the other choices.
 */
public final class PicksCommand implements SubCommand {
    /** The source of picks staff give with this command. */
    public static final String STAFF_SOURCE = "staff";
    private static final String SKILL = "skill";
    private static final String TIER = "tier";
    private static final String COUNT = "count";
    private static final String SOURCE = "source";
    private static final String PROFICIENCY = "proficiency";

    private final NpcTargeting targeting;
    private final AccessService access;
    private final ProficiencyPicks picks;

    public PicksCommand(NpcTargeting targeting, AccessService access, ProficiencyPicks picks) {
        this.targeting = targeting;
        this.access = access;
        this.picks = picks;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        ProficiencyCatalog catalog = picks.catalog();
        return LiteralArgumentBuilder.<CommandSourceStack>literal("picks")
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()))
                .executes(context -> {
                    list(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("pick")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PROFICIENCY, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(() -> catalog.skillIds().stream()
                                        .flatMap(skill -> catalog.proficienciesOf(skill).stream()).toList()))
                                .executes(context -> {
                                    pick(context.getSource().getSender(), StringArgumentType.getString(context, PROFICIENCY));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("grant")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(SKILL, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(catalog::skillIds))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(TIER, StringArgumentType.word())
                                        .suggests(SheetTargeting.suggest(catalog::tierIds))
                                        .executes(context -> grant(context, 1))
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(COUNT,
                                                        IntegerArgumentType.integer(1))
                                                .executes(context -> grant(context,
                                                        IntegerArgumentType.getInteger(context, COUNT)))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("take")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(SOURCE, StringArgumentType.string())
                                .executes(context -> take(context, Integer.MAX_VALUE))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(COUNT,
                                                IntegerArgumentType.integer(1))
                                        .executes(context -> take(context,
                                                IntegerArgumentType.getInteger(context, COUNT))))));
    }

    private void list(CommandSender sender) {
        Optional<NpcRecord> npc = mayChoose(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcName(npc.get().id());
        List<PickGrant> grants = picks.grants(npc.get().id());
        if (grants.isEmpty()) {
            targeting.send(sender, "picks.list.none", Map.of("npc", name));
            return;
        }
        targeting.send(sender, "picks.list.header", Map.of("npc", name,
                "left", String.valueOf(picks.pending(npc.get().id()))));
        ProficiencyCatalog catalog = picks.catalog();
        for (PickGrant grant : grants) {
            targeting.send(sender, "picks.list.line", Map.of(
                    "skill", catalog.skillName(grant.skill()),
                    "tier", catalog.tierName(grant.tier()),
                    "left", String.valueOf(picks.remaining(grant)),
                    "count", String.valueOf(grant.count()),
                    "source", grant.source()));
        }
    }

    private void pick(CommandSender sender, String proficiency) {
        Optional<NpcRecord> npc = mayChoose(sender);
        if (npc.isEmpty()) {
            return;
        }
        ProficiencyCatalog catalog = picks.catalog();
        String name = targeting.npcName(npc.get().id());
        PickResult result = picks.pickAny(sender instanceof Player player ? player : null, npc.get().id(), proficiency);
        Optional<String> id = catalog.proficiencyId(proficiency);
        Map<String, String> placeholders = Map.of("npc", name,
                "proficiency", id.map(catalog::proficiencyName).orElse(proficiency),
                "skill", id.flatMap(catalog::skillOf).map(catalog::skillName).orElse("?"),
                "tier", id.flatMap(found -> catalog.tier(npc.get().id(), found)).map(catalog::tierName).orElse("?"));
        String key = switch (result) {
            case DONE -> "picks.picked";
            case UNKNOWN_PROFICIENCY -> "proficiency.unknown";
            case WRONG_SKILL -> "picks.none-for-skill";
            default -> PicksPage.resultKey(result);
        };
        targeting.send(sender, key, placeholders);
    }

    private int grant(CommandContext<CommandSourceStack> context, int count) {
        CommandSender sender = context.getSource().getSender();
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String skill = StringArgumentType.getString(context, SKILL);
        String tier = StringArgumentType.getString(context, TIER);
        ProficiencyCatalog catalog = picks.catalog();
        String name = targeting.npcName(npc.get().id());
        if (catalog.skillId(skill).isEmpty()) {
            targeting.send(sender, "picks.unknown-skill", Map.of("skill", skill));
        } else if (catalog.tierId(tier).isEmpty()) {
            targeting.send(sender, "proficiency.unknown-tier", Map.of("tier", tier));
        } else {
            PickGrant grant = picks.grant(npc.get().id(), STAFF_SOURCE, skill, tier, count);
            targeting.send(sender, "picks.granted", Map.of("npc", name, "count", String.valueOf(count),
                    "skill", catalog.skillName(grant.skill()), "tier", catalog.tierName(grant.tier())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int take(CommandContext<CommandSourceStack> context, int count) {
        CommandSender sender = context.getSource().getSender();
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isPresent()) {
            String source = StringArgumentType.getString(context, SOURCE);
            int taken = picks.removeUnspent(npc.get().id(), source, count);
            targeting.send(sender, "picks.taken", Map.of("npc", targeting.npcName(npc.get().id()),
                    "count", String.valueOf(taken), "source", source));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** The targeted managed NPC, when the sender may make its choices; otherwise the sender is told why. */
    private Optional<NpcRecord> mayChoose(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return npc;
        }
        boolean allowed = sender instanceof Player player
                ? access.may(player, npc.get(), NpcAction.CHOOSE) : targeting.isStaff(sender);
        if (!allowed) {
            targeting.send(sender, "picks.not-allowed", Map.of("npc", targeting.npcName(npc.get().id())));
            return Optional.empty();
        }
        return npc;
    }
}
