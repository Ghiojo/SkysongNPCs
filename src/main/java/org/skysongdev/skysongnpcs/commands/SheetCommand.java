package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.sheets.NpcSheetService;
import org.skysongdev.skysongnpcs.templates.NpcTemplate;
import org.skysongdev.skysongnpcs.templates.TemplateRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * {@code /snpc sheet}: shows the targeted NPC's stat sheet in chat to whoever has its
 * stats-view action (its owner and staff always do). {@code /snpc sheet create [template]}
 * (staff) gives a managed NPC a sheet from a template, by default the one it came from, or
 * with every stat at its stats.yml start value when it has none.
 */
public final class SheetCommand implements SubCommand {
    private static final String TEMPLATE = "template";

    private final SheetTargeting targeting;
    private final AccessService access;
    private final TemplateRegistry templates;
    private final PluginConfiguration configuration;
    // M16: abilities
    private final Function<UUID, Optional<Component>> footer;

    public SheetCommand(NpcTargeting targeting, AccessService access, NpcSheetService sheets,
                        TemplateRegistry templates, PluginConfiguration configuration) {
        this(targeting, access, sheets, templates, configuration, npc -> Optional.empty());
    }

    /**
     * @param footer a line shown under an NPC's sheet, such as its abilities (M16); empty for none
     */
    public SheetCommand(NpcTargeting targeting, AccessService access, NpcSheetService sheets,
                        TemplateRegistry templates, PluginConfiguration configuration,
                        Function<UUID, Optional<Component>> footer) {
        this.targeting = new SheetTargeting(targeting, sheets);
        this.access = access;
        this.templates = templates;
        this.configuration = configuration;
        this.footer = footer;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("sheet")
                .requires(source -> source.getSender() instanceof Player || targeting.npcs().isStaff(source.getSender()))
                .executes(context -> {
                    show(context.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                        .requires(source -> targeting.npcs().isStaff(source.getSender()))
                        .executes(context -> {
                            create(context.getSource().getSender(), null);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(TEMPLATE, StringArgumentType.word())
                                .suggests(SheetTargeting.suggest(() -> templates.all().stream()
                                        .filter(NpcTemplate::sheet).map(NpcTemplate::id).toList()))
                                .executes(context -> {
                                    create(context.getSource().getSender(), StringArgumentType.getString(context, TEMPLATE));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private void show(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.withSheet(sender);
        if (npc.isEmpty()) {
            return;
        }
        NpcTargeting npcs = targeting.npcs();
        String name = npcs.npcName(npc.get().id());
        boolean allowed = sender instanceof Player player
                ? access.may(player, npc.get(), NpcAction.STATS_VIEW) : npcs.isStaff(sender);
        if (!allowed) {
            npcs.send(sender, "sheet.not-allowed", Map.of("npc", name));
            return;
        }
        List<String> lines = configuration.messages().lines("sheet.lines");
        targeting.sheets().stats().render(npc.get().id(), lines, name).ifPresent(rendered -> {
            rendered.forEach(sender::sendMessage);
            // M16: the NPC's abilities, each a word with its description on hover.
            footer.apply(npc.get().id()).ifPresent(sender::sendMessage);
        });
    }

    private void create(CommandSender sender, String templateId) {
        NpcTargeting npcs = targeting.npcs();
        Optional<NpcRecord> npc = npcs.managedTarget(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = npcs.npcName(npc.get().id());
        if (targeting.sheets().hasSheet(npc.get().id())) {
            npcs.send(sender, "sheet.already", Map.of("npc", name));
            return;
        }
        String id = templateId != null ? templateId : npc.get().template().orElse(null);
        try {
            if (id == null) {
                targeting.sheets().create(npc.get().id(), Map.of());
                npcs.send(sender, "sheet.created", Map.of("npc", name));
                return;
            }
            Optional<NpcTemplate> template = templates.get(id);
            if (template.isEmpty()) {
                npcs.send(sender, "sheet.unknown-template", Map.of("template", id));
            } else if (!template.get().sheet()) {
                npcs.send(sender, "sheet.template-without-sheet", Map.of("template", id));
            } else {
                targeting.sheets().create(npc.get().id(), template.get());
                npcs.send(sender, "sheet.created-from", Map.of("npc", name, "template", id));
            }
        } catch (IllegalArgumentException failed) {
            // A stat the template names left stats.yml after the template was read.
            npcs.send(sender, "sheet.failed", Map.of("npc", name, "reason", String.valueOf(failed.getMessage())));
        }
    }
}
