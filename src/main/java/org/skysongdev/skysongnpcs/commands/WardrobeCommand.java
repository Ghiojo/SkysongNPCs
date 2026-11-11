package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.wardrobe.NpcWardrobes;

/**
 * {@code /snpc wardrobe}: opens the targeted NPC's wardrobe, to dress it ({@code wardrobe-edit})
 * or to look at it ({@code wardrobe-view}). The same as the menu's Wardrobe button.
 */
public final class WardrobeCommand implements SubCommand {
    private final NpcTargeting targeting;
    private final NpcWardrobes wardrobes;

    public WardrobeCommand(NpcTargeting targeting, NpcWardrobes wardrobes) {
        this.targeting = targeting;
        this.wardrobes = wardrobes;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("wardrobe")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getSender();
                    targeting.managedTarget(player).ifPresent(npc -> wardrobes.open(player, npc, false));
                    return Command.SINGLE_SUCCESS;
                });
    }
}
