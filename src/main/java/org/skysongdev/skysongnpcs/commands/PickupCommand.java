package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.items.NpcPickup;

import java.util.function.Supplier;

/**
 * {@code /snpc pickup}: picks the targeted NPC up into its item, for its owner, staff, or
 * anyone with the pickup action.
 */
public final class PickupCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final Supplier<NpcPickup> pickup;

    /** @param pickup the pickup, made after the commands are; asked for when the command runs */
    public PickupCommand(NpcTargeting targeting, Supplier<NpcPickup> pickup) {
        this.targeting = targeting;
        this.pickup = pickup;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("pickup")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getSender();
                    targeting.managedTarget(player).ifPresent(npc -> pickup.get().pickUpAndTell(player, npc.id()));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
