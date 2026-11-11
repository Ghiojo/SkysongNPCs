package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.movement.MovementReplies;
import org.skysongdev.skysongnpcs.movement.MovementService;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The movement subcommands, for players: {@code /snpc select}, {@code /snpc tphere},
 * {@code /snpc follow <on|off> [player] [distance]} and {@code /snpc look <on|off> [range]}.
 * Each needs the matching action on the NPC (teleport, follow, look), which its owner and
 * staff always have.
 */
public final class MovementCommands {
    private static final String PLAYER = "player";
    private static final String DISTANCE = "distance";
    private static final String RANGE = "range";

    private final NpcTargeting targeting;
    private final NpcLookup citizens;
    private final AccessService access;
    private final MovementService movement;
    private final PluginConfiguration configuration;

    public MovementCommands(NpcTargeting targeting, NpcLookup citizens, AccessService access,
                            MovementService movement, PluginConfiguration configuration) {
        this.targeting = targeting;
        this.citizens = citizens;
        this.access = access;
        this.movement = movement;
        this.configuration = configuration;
    }

    public List<SubCommand> subCommands() {
        return List.of(this::select, this::tphere, this::follow, this::look);
    }

    /** {@code /snpc select}: the NPC looked at becomes the one commands act on from afar. */
    private LiteralArgumentBuilder<CommandSourceStack> select() {
        return player("select").executes(context -> {
            Player player = (Player) context.getSource().getSender();
            targeting.managedTarget(player).ifPresent(npc -> {
                String name = targeting.npcName(npc.id());
                if (access.allowed(player, npc).isEmpty()) {
                    targeting.send(player, "movement.select-not-allowed", Map.of("npc", name));
                    return;
                }
                citizens.select(player, npc.id());
                targeting.send(player, "movement.selected", Map.of("npc", name));
            });
            return Command.SINGLE_SUCCESS;
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> tphere() {
        return player("tphere").executes(context -> {
            Player player = (Player) context.getSource().getSender();
            allowedTarget(player, NpcAction.TELEPORT).ifPresent(npc -> reply(player, npc,
                    movement.teleportHere(player, npc, access.isStaff(player)), "movement.teleported", Map.of()));
            return Command.SINGLE_SUCCESS;
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> follow() {
        return player("follow")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                        .executes(context -> follow(context.getSource(), null, null))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PLAYER, StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    Bukkit.getOnlinePlayers().forEach(online -> builder.suggest(online.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(context -> follow(context.getSource(),
                                        StringArgumentType.getString(context, PLAYER), null))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument(DISTANCE,
                                                DoubleArgumentType.doubleArg(1))
                                        .executes(context -> follow(context.getSource(),
                                                StringArgumentType.getString(context, PLAYER),
                                                DoubleArgumentType.getDouble(context, DISTANCE))))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                        .executes(context -> {
                            Player player = (Player) context.getSource().getSender();
                            allowedTarget(player, NpcAction.FOLLOW).ifPresent(npc -> {
                                movement.stopFollowing(npc.id());
                                targeting.send(player, "movement.follow-off", Map.of("npc", targeting.npcName(npc.id())));
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private int follow(CommandSourceStack source, String targetName, Double distance) {
        Player player = (Player) source.getSender();
        Optional<NpcRecord> npc = allowedTarget(player, NpcAction.FOLLOW);
        if (npc.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Player target = targetName == null ? player : Bukkit.getPlayerExact(targetName);
        if (target == null) {
            targeting.send(player, "movement.player-offline", Map.of("player", targetName));
            return Command.SINGLE_SUCCESS;
        }
        double margin = distance != null ? distance : movement.settings().followDistance();
        reply(player, npc.get(), movement.follow(player, npc.get(), target, margin, access.isStaff(player)),
                "movement.follow-on", Map.of("target", target.getName(), "distance", MovementReplies.number(margin)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> look() {
        return player("look")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                        .executes(context -> look(context.getSource(), true, null))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument(RANGE, DoubleArgumentType.doubleArg(1))
                                .executes(context -> look(context.getSource(), true,
                                        DoubleArgumentType.getDouble(context, RANGE)))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                        .executes(context -> look(context.getSource(), false, null)));
    }

    private int look(CommandSourceStack source, boolean on, Double range) {
        Player player = (Player) source.getSender();
        allowedTarget(player, NpcAction.LOOK).ifPresent(npc -> {
            double distance = range != null ? range : movement.settings().lookRange();
            reply(player, npc, movement.look(npc, on, distance, access.isStaff(player)),
                    on ? "movement.look-on" : "movement.look-off", Map.of("range", MovementReplies.number(distance)));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** The targeted managed NPC, when the player may do {@code action} with it. */
    private Optional<NpcRecord> allowedTarget(Player player, NpcAction action) {
        Optional<NpcRecord> npc = targeting.managedTarget(player);
        if (npc.isPresent() && !access.may(player, npc.get(), action)) {
            targeting.send(player, "movement.not-allowed", Map.of("npc", targeting.npcName(npc.get().id())));
            return Optional.empty();
        }
        return npc;
    }

    private void reply(Player player, NpcRecord npc, MovementService.Result result, String doneKey,
                       Map<String, String> placeholders) {
        Map<String, String> values = new HashMap<>(placeholders);
        values.put("npc", targeting.npcName(npc.id()));
        MovementReplies.send(configuration.messages(), player, result, doneKey, values, movement.settings());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> player(String name) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                .requires(source -> source.getSender() instanceof Player);
    }
}
