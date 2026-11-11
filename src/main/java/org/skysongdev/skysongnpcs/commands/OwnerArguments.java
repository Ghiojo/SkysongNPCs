package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The {@code <player> [profile]} arguments that name a character, for {@code /snpc adopt}
 * and {@code /snpc owner}. The player is suggested from who is online, the profile from that
 * player's profiles; a profile name may have spaces.
 */
final class OwnerArguments {
    private static final String PLAYER = "player";
    private static final String PROFILE = "profile";

    private OwnerArguments() {
    }

    /**
     * {@code <player> [profile]}, running {@code action} with the sender and the character once
     * it is found. A name that matches no character gets a message and runs nothing.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> character(
            NpcTargeting targeting, BiConsumer<CommandContext<CommandSourceStack>, NpcOwner> action) {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(PLAYER, StringArgumentType.word())
                .suggests((context, builder) -> {
                    String typed = builder.getRemainingLowerCase();
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().toLowerCase().startsWith(typed)) {
                            builder.suggest(online.getName());
                        }
                    }
                    return builder.buildFuture();
                })
                .executes(context -> run(context, targeting, null, action))
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PROFILE, StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(StringArgumentType.getString(context, PLAYER));
                            if (player != null) {
                                String typed = builder.getRemainingLowerCase();
                                for (String profile : targeting.characters().profiles(player.getUniqueId())) {
                                    if (profile.toLowerCase().startsWith(typed)) {
                                        builder.suggest(profile);
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> run(context, targeting, StringArgumentType.getString(context, PROFILE), action)));
    }

    private static int run(CommandContext<CommandSourceStack> context, NpcTargeting targeting, String profile,
                           BiConsumer<CommandContext<CommandSourceStack>, NpcOwner> action) {
        Optional<NpcOwner> owner = targeting.character(context.getSource().getSender(),
                StringArgumentType.getString(context, PLAYER), profile);
        owner.ifPresent(found -> action.accept(context, found));
        return Command.SINGLE_SUCCESS;
    }
}
