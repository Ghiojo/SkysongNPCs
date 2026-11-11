package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.appearance.AppearanceMenu;
import org.skysongdev.skysongnpcs.appearance.AppearanceService;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * The appearance subcommands, each acting on the targeted NPC: {@code /snpc name <name>},
 * {@code skin <player | link> [slim]}, {@code nameplate <show|hide|hover>},
 * {@code pose <stand|crouch|lie>}, {@code sit <on|off>}, {@code glow <colour|off>}, and
 * {@code /snpc appearance} for the menu. Name needs the {@code rename} action, skin the
 * {@code skin} action, and the rest {@code appearance}. Colours and styles in a name need
 * {@code skysongnpcs.name.format}.
 */
public final class AppearanceCommands {
    /** Lets a player use colours and styles in NPC names. */
    public static final String FORMAT_NAMES = "skysongnpcs.name.format";
    private static final String SLIM = " slim";

    private final NpcTargeting targeting;
    private final AccessService access;
    private final AppearanceService appearance;
    private final AppearanceMenu menu;
    private final PluginConfiguration configuration;

    public AppearanceCommands(NpcTargeting targeting, AccessService access, AppearanceService appearance,
                              AppearanceMenu menu, PluginConfiguration configuration) {
        this.targeting = targeting;
        this.access = access;
        this.appearance = appearance;
        this.menu = menu;
        this.configuration = configuration;
    }

    /** Every appearance subcommand, to add to {@code /snpc}. */
    public List<SubCommand> subCommands() {
        return List.of(this::menu, this::name, this::skin, this::nameplate, this::pose, this::sit, this::glow);
    }

    private LiteralArgumentBuilder<CommandSourceStack> menu() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("appearance")
                .requires(source -> source.getSender() instanceof Player)
                .executes(context -> {
                    Player player = (Player) context.getSource().getSender();
                    targeting.managedTarget(player).ifPresent(npc -> {
                        if (!menu.open(player, npc.id())) {
                            targeting.send(player, "appearance.not-allowed", Map.of("npc", targeting.npcName(npc.id())));
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> name() {
        return literal("name").then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("name",
                        StringArgumentType.greedyString())
                .executes(context -> run(context.getSource().getSender(), NpcAction.RENAME, (sender, npc) -> {
                    String name = StringArgumentType.getString(context, "name").trim();
                    Optional<AppearanceService.NameProblem> problem =
                            appearance.checkName(name, sender.hasPermission(FORMAT_NAMES));
                    if (problem.isPresent()) {
                        targeting.send(sender, "appearance.name." + problem.get().name().toLowerCase(Locale.ROOT)
                                .replace('_', '-'), Map.of("max", String.valueOf(appearance.maxNameLength())));
                        return;
                    }
                    String old = targeting.npcName(npc.id());
                    appearance.rename(npc.id(), name);
                    targeting.send(sender, "appearance.name.done",
                            Map.of("old", old, "npc", MiniMessage.miniMessage().stripTags(name)));
                })));
    }

    private LiteralArgumentBuilder<CommandSourceStack> skin() {
        return literal("skin").then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("source",
                        StringArgumentType.greedyString())
                .executes(context -> run(context.getSource().getSender(), NpcAction.SKIN, (sender, npc) -> {
                    String name = targeting.npcName(npc.id());
                    if (!appearance.hasSkin(npc.id())) {
                        targeting.send(sender, "appearance.skin.not-player-shaped", Map.of("npc", name));
                        return;
                    }
                    String source = StringArgumentType.getString(context, "source").trim();
                    if (source.startsWith("http://") || source.startsWith("https://")) {
                        boolean slim = source.toLowerCase(Locale.ROOT).endsWith(SLIM);
                        String url = slim ? source.substring(0, source.length() - SLIM.length()).trim() : source;
                        skinFromUrl(sender, npc, url, slim);
                        return;
                    }
                    if (!AppearanceService.isPlayerName(source)) {
                        targeting.send(sender, "appearance.skin.bad-source", Map.of("source", source));
                        return;
                    }
                    appearance.setSkin(npc.id(), source);
                    targeting.send(sender, "appearance.skin.done", Map.of("npc", name, "skin", source));
                    wardrobeHint(sender, name);
                })));
    }

    private void skinFromUrl(CommandSender sender, NpcRecord npc, String url, boolean slim) {
        String name = targeting.npcName(npc.id());
        targeting.send(sender, "appearance.skin.url-working", Map.of("npc", name));
        appearance.setSkinFromUrl(npc.id(), url, slim).whenComplete((done, failed) -> {
            if (failed == null) {
                targeting.send(sender, "appearance.skin.url-done", Map.of("npc", name));
                wardrobeHint(sender, name);
                return;
            }
            Throwable cause = failed instanceof CompletionException && failed.getCause() != null ? failed.getCause() : failed;
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            targeting.send(sender, "appearance.skin.url-failed", Map.of("npc", name, "reason", reason));
        });
    }

    /** SkysongWardrobe draws an outfit over the base skin again only when the wardrobe closes. */
    private void wardrobeHint(CommandSender sender, String name) {
        if (Bukkit.getPluginManager().isPluginEnabled("SkysongWardrobe")) {
            targeting.send(sender, "appearance.skin.wardrobe-hint", Map.of("npc", name));
        }
    }

    private LiteralArgumentBuilder<CommandSourceStack> nameplate() {
        LiteralArgumentBuilder<CommandSourceStack> node = literal("nameplate");
        for (Nameplate nameplate : Nameplate.values()) {
            node.then(LiteralArgumentBuilder.<CommandSourceStack>literal(nameplate.key())
                    .executes(context -> run(context.getSource().getSender(), NpcAction.APPEARANCE, (sender, npc) -> {
                        appearance.setNameplate(npc.id(), nameplate);
                        targeting.send(sender, "appearance.nameplate.done", Map.of("npc", targeting.npcName(npc.id()),
                                "nameplate", word("appearance.nameplates." + nameplate.key())));
                    })));
        }
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> pose() {
        LiteralArgumentBuilder<CommandSourceStack> node = literal("pose");
        for (NpcPose pose : NpcPose.values()) {
            node.then(LiteralArgumentBuilder.<CommandSourceStack>literal(pose.key())
                    .executes(context -> run(context.getSource().getSender(), NpcAction.APPEARANCE, (sender, npc) -> {
                        appearance.setPose(npc.id(), pose);
                        targeting.send(sender, "appearance.pose.done", Map.of("npc", targeting.npcName(npc.id()),
                                "pose", word("appearance.poses." + pose.key())));
                    })));
        }
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> sit() {
        LiteralArgumentBuilder<CommandSourceStack> node = literal("sit");
        for (boolean sitting : new boolean[]{true, false}) {
            node.then(LiteralArgumentBuilder.<CommandSourceStack>literal(sitting ? "on" : "off")
                    .executes(context -> run(context.getSource().getSender(), NpcAction.APPEARANCE, (sender, npc) -> {
                        appearance.setSitting(npc.id(), sitting);
                        targeting.send(sender, "appearance.sit." + (sitting ? "on" : "off"),
                                Map.of("npc", targeting.npcName(npc.id())));
                    })));
        }
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> glow() {
        return literal("glow").then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("colour",
                        StringArgumentType.word())
                .suggests((context, builder) -> {
                    Stream.concat(Stream.of("off"), NamedTextColor.NAMES.keys().stream())
                            .filter(word -> word.startsWith(builder.getRemainingLowerCase()))
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> run(context.getSource().getSender(), NpcAction.APPEARANCE, (sender, npc) -> {
                    String typed = StringArgumentType.getString(context, "colour").toLowerCase(Locale.ROOT);
                    String name = targeting.npcName(npc.id());
                    if (typed.equals("off")) {
                        appearance.setGlow(npc.id(), null);
                        targeting.send(sender, "appearance.glow.off", Map.of("npc", name));
                        return;
                    }
                    NamedTextColor colour = NamedTextColor.NAMES.value(typed);
                    if (colour == null) {
                        targeting.send(sender, "appearance.glow.unknown", Map.of("colour", typed,
                                "colours", String.join(", ", NamedTextColor.NAMES.keys())));
                        return;
                    }
                    appearance.setGlow(npc.id(), colour);
                    targeting.send(sender, "appearance.glow.on", Map.of("npc", name, "colour", typed.replace('_', ' ')));
                })));
    }

    /** A subcommand players and staff (also from the console, on the NPC they selected) may use. */
    private LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()));
    }

    /** Runs {@code change} on the targeted NPC when the sender may do the action with it. */
    private int run(CommandSender sender, NpcAction action, BiConsumer<CommandSender, NpcRecord> change) {
        targeting.managedTarget(sender).ifPresent(npc -> {
            boolean allowed = sender instanceof Player player ? access.may(player, npc, action) : targeting.isStaff(sender);
            if (!allowed) {
                targeting.send(sender, "appearance.not-allowed", Map.of("npc", targeting.npcName(npc.id())));
                return;
            }
            change.accept(sender, npc);
        });
        return Command.SINGLE_SUCCESS;
    }

    private String word(String key) {
        return configuration.messages().plain(key, Map.of());
    }
}
