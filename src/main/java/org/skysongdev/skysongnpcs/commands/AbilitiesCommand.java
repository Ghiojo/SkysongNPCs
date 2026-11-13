package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.abilities.AbilitiesMenu;
import org.skysongdev.skysongnpcs.abilities.AbilityDefinition;
import org.skysongdev.skysongnpcs.abilities.AbilityService;
import org.skysongdev.skysongnpcs.abilities.AbilityText;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /snpc abilities}: opens the targeted NPC's abilities menu (players), or lists them
 * in chat ({@code list}, and always from the console), for anyone with its abilities-view
 * action. {@code /snpc abilities grant|revoke <ability>} (staff) gives or takes one away.
 */
public final class AbilitiesCommand implements SubCommand {
    private static final String ABILITY = "ability";

    private final NpcTargeting targeting;
    private final AccessService access;
    private final AbilityService abilities;
    private final AbilitiesMenu menu;
    private final AbilityText text;

    public AbilitiesCommand(NpcTargeting targeting, AccessService access, AbilityService abilities, AbilitiesMenu menu,
                            AbilityText text) {
        this.targeting = targeting;
        this.access = access;
        this.abilities = abilities;
        this.menu = menu;
        this.text = text;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("abilities")
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    viewable(sender).ifPresent(npc -> {
                        if (sender instanceof Player player) {
                            menu.open(player, npc.id());
                        } else {
                            list(sender, npc);
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            viewable(sender).ifPresent(npc -> list(sender, npc));
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("grant")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ABILITY, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(() -> abilities.definitions().ids()))
                                .executes(context -> {
                                    grant(context.getSource().getSender(), StringArgumentType.getString(context, ABILITY));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("revoke")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ABILITY, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(() -> abilities.definitions().ids()))
                                .executes(context -> {
                                    revoke(context.getSource().getSender(), StringArgumentType.getString(context, ABILITY));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    /** The targeted NPC, when the sender may see its abilities. */
    private Optional<NpcRecord> viewable(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return npc;
        }
        boolean allowed = sender instanceof Player player
                ? access.may(player, npc.get(), NpcAction.ABILITIES_VIEW) : targeting.isStaff(sender);
        if (!allowed) {
            targeting.send(sender, "abilities.not-allowed", Map.of("npc", targeting.npcName(npc.get().id())));
            return Optional.empty();
        }
        return npc;
    }

    private void list(CommandSender sender, NpcRecord npc) {
        String name = targeting.npcName(npc.id());
        List<AbilityDefinition> owned = abilities.of(npc.id());
        if (owned.isEmpty()) {
            targeting.send(sender, "abilities.list.empty", Map.of("npc", name));
            return;
        }
        text.sheetLine(owned).ifPresent(line -> {
            targeting.send(sender, "abilities.list.header", Map.of("npc", name));
            sender.sendMessage(line);
        });
    }

    private void grant(CommandSender sender, String ability) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        Map<String, String> placeholders = Map.of("npc", targeting.npcName(npc.get().id()), "ability", ability);
        if (abilities.definitions().get(ability).isEmpty()) {
            targeting.send(sender, "abilities.unknown", placeholders);
        } else if (abilities.grant(npc.get().id(), ability, AbilityService.STAFF)) {
            targeting.send(sender, "abilities.granted", placeholders);
        } else {
            targeting.send(sender, "abilities.already", placeholders);
        }
    }

    private void revoke(CommandSender sender, String ability) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        UUID id = npc.get().id();
        Map<String, String> placeholders = Map.of("npc", targeting.npcName(id), "ability", ability);
        if (!abilities.has(id, ability)) {
            targeting.send(sender, "abilities.missing", placeholders);
            return;
        }
        // Staff take away only their own grant: a plugin's (a business module's) stays theirs to revoke.
        abilities.revoke(id, ability, AbilityService.STAFF);
        if (!abilities.has(id, ability)) {
            targeting.send(sender, "abilities.revoked", placeholders);
            return;
        }
        Map<String, String> held = new HashMap<>(placeholders);
        held.put("sources", String.join(", ", abilities.sourcesInOrder(id, ability)));
        targeting.send(sender, "abilities.held-by-others", held);
    }
}
