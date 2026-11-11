package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.sheets.SheetEffects;
import org.skysongdev.skysongstats.api.Injury;
import org.skysongdev.skysongstats.api.InjuryType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * {@code /snpc injury [list]}, {@code /snpc injury <award|remove|discover|forget> <injury>}
 * (staff and DMs with {@code skysongnpcs.injuries}): the targeted NPC's injuries, as
 * SkysongStats' {@code /injury} does for players.
 */
public final class InjuryCommand implements SubCommand {
    private static final String INJURY = "injury";

    private final SheetTargeting targeting;
    private final SheetEffects effects;

    public InjuryCommand(NpcTargeting targeting, NpcSheetService sheets, SheetEffects effects) {
        this.targeting = new SheetTargeting(targeting, sheets);
        this.effects = effects;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("injury")
                .requires(source -> EffectText.mayAfflict(source.getSender()))
                .executes(context -> list(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> list(context.getSource().getSender())))
                .then(change("award", effects::awardInjury, "injury.awarded", "injury.unknown"))
                .then(change("remove", effects::removeInjury, "injury.removed", "injury.not-had"))
                .then(change("discover", effects::discoverInjury, "injury.discovered", "injury.already-known"))
                .then(change("forget", effects::forgetInjury, "injury.forgotten", "injury.not-forgotten"));
    }

    private LiteralArgumentBuilder<CommandSourceStack> change(String literal, BiPredicate<UUID, String> action,
                                                               String done, String refused) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(literal)
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(INJURY, StringArgumentType.word())
                        .suggests(EffectText.suggest(() -> effects.injuryTypes().stream().map(InjuryType::id).toList()))
                        .executes(context -> {
                            change(context.getSource().getSender(), StringArgumentType.getString(context, INJURY),
                                    action, done, refused);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private void change(CommandSender sender, String injury, BiPredicate<UUID, String> action, String done,
                        String refused) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcs().npcName(npc.get().id());
        Optional<InjuryType> type = effects.injuryType(injury);
        if (type.isEmpty()) {
            targeting.npcs().send(sender, "injury.unknown", Map.of("injury", injury));
            return;
        }
        boolean changed = action.test(npc.get().id(), type.get().id());
        targeting.npcs().send(sender, changed ? done : refused,
                Map.of("npc", name, "injury", EffectText.plain(type.get().name())));
    }

    private int list(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        UUID id = npc.get().id();
        String name = targeting.npcs().npcName(id);
        List<Injury> current = effects.injuries(id);
        if (current.isEmpty()) {
            targeting.npcs().send(sender, "injury.none", Map.of("npc", name));
        } else {
            targeting.npcs().send(sender, "injury.header", Map.of("npc", name));
            current.forEach(injury -> targeting.npcs().send(sender, "injury.entry", Map.of(
                    "injury", nameOf(injury.typeId()),
                    "severity", effects.injuryType(injury.typeId()).map(InjuryType::severity).orElse("?"))));
        }
        Set<String> known = effects.knownInjuries(id);
        if (!known.isEmpty()) {
            targeting.npcs().send(sender, "injury.known", Map.of("npc", name,
                    "injuries", known.stream().map(this::nameOf).collect(Collectors.joining(", "))));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** The injury's plain name; its id when it left injuries.yml. */
    private String nameOf(String id) {
        return effects.injuryType(id).map(type -> EffectText.plain(type.name())).orElse(id);
    }
}
