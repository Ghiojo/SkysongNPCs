package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongstats.api.ActiveCondition;
import org.skysongdev.skysongstats.api.ConditionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /snpc condition [list]}, {@code /snpc condition award <condition> [level]} and
 * {@code /snpc condition remove <condition>} (staff and DMs with
 * {@code skysongnpcs.injuries}): the targeted NPC's conditions, as SkysongStats'
 * {@code /condition} does for players. Only direct awards can be removed; a condition an
 * injury gives goes with the injury.
 */
public final class ConditionCommand implements SubCommand {
    private static final String CONDITION = "condition";
    private static final String LEVEL = "level";

    private final SheetTargeting targeting;
    private final SheetEffects effects;
    private final PluginConfiguration configuration;

    public ConditionCommand(NpcTargeting targeting, NpcSheetService sheets, SheetEffects effects,
                            PluginConfiguration configuration) {
        this.targeting = new SheetTargeting(targeting, sheets);
        this.effects = effects;
        this.configuration = configuration;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("condition")
                .requires(source -> EffectText.mayAfflict(source.getSender()))
                .executes(context -> list(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> list(context.getSource().getSender())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("award")
                        .then(conditionArgument()
                                .executes(context -> award(context.getSource().getSender(),
                                        StringArgumentType.getString(context, CONDITION), 1))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(LEVEL,
                                                IntegerArgumentType.integer(1))
                                        .executes(context -> award(context.getSource().getSender(),
                                                StringArgumentType.getString(context, CONDITION),
                                                IntegerArgumentType.getInteger(context, LEVEL))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                        .then(conditionArgument()
                                .executes(context -> remove(context.getSource().getSender(),
                                        StringArgumentType.getString(context, CONDITION)))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> conditionArgument() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(CONDITION, StringArgumentType.word())
                .suggests(EffectText.suggest(() -> effects.conditionTypes().stream().map(ConditionType::id).toList()));
    }

    private int award(CommandSender sender, String condition, int level) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        Optional<ConditionType> type = npc.isEmpty() ? Optional.empty() : known(sender, condition);
        if (type.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<ActiveCondition> now = effects.awardCondition(npc.get().id(), type.get().id(), level);
        if (now.isEmpty()) {
            targeting.npcs().send(sender, "condition.unknown", Map.of("condition", condition));
            return Command.SINGLE_SUCCESS;
        }
        targeting.npcs().send(sender, "condition.awarded", Map.of(
                "npc", targeting.npcs().npcName(npc.get().id()),
                "condition", EffectText.plain(type.get().name()),
                "level", String.valueOf(now.get().level())));
        return Command.SINGLE_SUCCESS;
    }

    private int remove(CommandSender sender, String condition) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        Optional<ConditionType> type = npc.isEmpty() ? Optional.empty() : known(sender, condition);
        if (type.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String key = effects.removeCondition(npc.get().id(), type.get().id()) ? "condition.removed" : "condition.not-direct";
        targeting.npcs().send(sender, key, Map.of(
                "npc", targeting.npcs().npcName(npc.get().id()),
                "condition", EffectText.plain(type.get().name())));
        return Command.SINGLE_SUCCESS;
    }

    private Optional<ConditionType> known(CommandSender sender, String condition) {
        Optional<ConditionType> type = effects.conditionType(condition);
        if (type.isEmpty()) {
            targeting.npcs().send(sender, "condition.unknown", Map.of("condition", condition));
        }
        return type;
    }

    private int list(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        UUID id = npc.get().id();
        String name = targeting.npcs().npcName(id);
        List<ActiveCondition> current = effects.conditions(id);
        if (current.isEmpty()) {
            targeting.npcs().send(sender, "condition.none", Map.of("npc", name));
            return Command.SINGLE_SUCCESS;
        }
        targeting.npcs().send(sender, "condition.header", Map.of("npc", name));
        current.forEach(condition -> targeting.npcs().send(sender, "condition.entry", Map.of(
                "condition", nameOf(condition.typeId()),
                "level", String.valueOf(condition.level()),
                "sources", sources(condition))));
        return Command.SINGLE_SUCCESS;
    }

    /** Why the NPC has it: given directly, and the injuries and conditions that give it. */
    private String sources(ActiveCondition condition) {
        List<String> sources = new ArrayList<>();
        if (condition.direct()) {
            sources.add(configuration.messages().plain("condition.source-direct", Map.of()));
        }
        condition.fromInjuries().forEach(injury -> sources.add(
                effects.injuryType(injury).map(type -> EffectText.plain(type.name())).orElse(injury)));
        condition.fromConditions().forEach(other -> sources.add(nameOf(other)));
        return String.join(", ", sources);
    }

    private String nameOf(String id) {
        return effects.conditionType(id).map(type -> EffectText.plain(type.name())).orElse(id);
    }
}
