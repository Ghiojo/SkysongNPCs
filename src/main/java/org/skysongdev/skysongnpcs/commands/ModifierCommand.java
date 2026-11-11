package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.StatSheets.ModifierLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /snpc modifier [list]}, {@code /snpc modifier add <target> <amount>} and
 * {@code /snpc modifier del <id>} (staff): the targeted NPC's SkysongStats modifiers, like
 * SkysongStats' {@code /modifier}. A target is a stat's id or alias, or {@code max_<bar>}.
 */
public final class ModifierCommand implements SubCommand {
    private static final String TARGET = "target";
    private static final String AMOUNT = "amount";
    private static final String ID = "id";

    private final SheetTargeting targeting;

    public ModifierCommand(NpcTargeting targeting, NpcSheetService sheets) {
        this.targeting = new SheetTargeting(targeting, sheets);
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("modifier")
                .requires(source -> targeting.npcs().isStaff(source.getSender()))
                .executes(context -> list(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> list(context.getSource().getSender())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(TARGET, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(this::targets))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument(AMOUNT, IntegerArgumentType.integer())
                                        .executes(context -> {
                                            add(context.getSource().getSender(), StringArgumentType.getString(context, TARGET),
                                                    IntegerArgumentType.getInteger(context, AMOUNT));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("del")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ID, StringArgumentType.word())
                                .executes(context -> {
                                    delete(context.getSource().getSender(), StringArgumentType.getString(context, ID));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    /** Stat ids, then {@code max_} and the id for every stat; SkysongStats refuses the ones that are no bar. */
    private List<String> targets() {
        List<String> ids = targeting.sheets().stats().statIds();
        List<String> targets = new ArrayList<>(ids);
        ids.forEach(id -> targets.add("max_" + id));
        return targets;
    }

    private int list(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        UUID id = npc.get().id();
        String name = targeting.npcs().npcName(id);
        List<ModifierLine> modifiers = targeting.sheets().stats().modifiers(id);
        if (modifiers.isEmpty()) {
            targeting.npcs().send(sender, "modifier.empty", Map.of("npc", name));
            return Command.SINGLE_SUCCESS;
        }
        targeting.npcs().send(sender, "modifier.header", Map.of("npc", name));
        modifiers.forEach(modifier -> targeting.npcs().send(sender, "modifier.entry", placeholders(name, modifier)));
        return Command.SINGLE_SUCCESS;
    }

    private void add(CommandSender sender, String target, int amount) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        Optional<ModifierLine> added = targeting.sheets().stats().addModifier(npc.get().id(), target, amount);
        if (added.isEmpty()) {
            targeting.npcs().send(sender, "modifier.unknown-target", Map.of("target", target));
            return;
        }
        targeting.npcs().send(sender, "modifier.added",
                placeholders(targeting.npcs().npcName(npc.get().id()), added.get()));
    }

    private void delete(CommandSender sender, String modifierId) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcs().npcName(npc.get().id());
        String key = targeting.sheets().stats().removeModifier(npc.get().id(), modifierId)
                ? "modifier.removed" : "modifier.not-found";
        targeting.npcs().send(sender, key, Map.of("npc", name, "id", modifierId));
    }

    private static Map<String, String> placeholders(String npc, ModifierLine modifier) {
        String amount = modifier.amount() >= 0 ? "+" + modifier.amount() : String.valueOf(modifier.amount());
        return Map.of("npc", npc, "id", modifier.id(), "target", modifier.target(), "amount", amount);
    }
}
