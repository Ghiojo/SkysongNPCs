package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.api.groups.GroupMember;
import org.skysongdev.skysongnpcs.api.groups.PlayerGroup;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.groups.PlayerGroupRegistry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /snpc group}: named lists of characters, which {@code /snpc access grant group:<name> ...}
 * lets in all at once.
 * <ul>
 *   <li>{@code /snpc group} or {@code list}: the groups the sender may change.</li>
 *   <li>{@code create <name>}: a group owned by the character the sender plays
 *       ({@code create <name> server} for staff: owned by the server).</li>
 *   <li>{@code delete <name>}: deletes it, and every grant that names it.</li>
 *   <li>{@code add|remove <name> <player> [profile]}: one character, or every character of
 *       the player when no profile is given.</li>
 *   <li>{@code info <name>}: its owner and members.</li>
 * </ul>
 * A group's owner, while playing the owning character, and staff may change and see it.
 */
public final class GroupCommand implements SubCommand {
    private static final String NAME = "name";
    private static final String PLAYER = "player";
    private static final String PROFILE = "profile";

    private final NpcTargeting targeting;
    private final PlayerGroupRegistry groups;
    private final PluginConfiguration configuration;

    public GroupCommand(NpcTargeting targeting, PlayerGroupRegistry groups, PluginConfiguration configuration) {
        this.targeting = targeting;
        this.groups = groups;
        this.configuration = configuration;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal("group")
                .requires(source -> targeting.isStaff(source.getSender())
                        || source.getSender() instanceof Player player && player.hasPermission(Permissions.GROUPS))
                .executes(context -> list(context.getSource().getSender()))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                        .executes(context -> list(context.getSource().getSender())))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(NAME, StringArgumentType.word())
                                .executes(context -> create(context, false))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("server")
                                        .requires(source -> targeting.isStaff(source.getSender()))
                                        .executes(context -> create(context, true)))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("delete")
                        .then(name().executes(this::delete)))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                        .then(name().executes(this::info)))
                .then(member("add", true))
                .then(member("remove", false));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> name() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(NAME, StringArgumentType.word())
                .suggests(editableGroups());
    }

    /** {@code add|remove <name> <player> [profile]}. */
    private LiteralArgumentBuilder<CommandSourceStack> member(String literal, boolean adding) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(literal)
                .then(name().then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PLAYER, StringArgumentType.word())
                        .suggests((context, builder) -> {
                            String typed = builder.getRemainingLowerCase();
                            Bukkit.getOnlinePlayers().stream().map(Player::getName)
                                    .filter(name -> name.toLowerCase().startsWith(typed)).forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(context -> changeMember(context, null, adding))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(PROFILE, StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(StringArgumentType.getString(context, PLAYER));
                                    if (player != null) {
                                        String typed = builder.getRemainingLowerCase();
                                        targeting.characters().profiles(player.getUniqueId()).stream()
                                                .filter(profile -> profile.toLowerCase().startsWith(typed)).forEach(builder::suggest);
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> changeMember(context, StringArgumentType.getString(context, PROFILE), adding)))));
    }

    private int list(CommandSender sender) {
        List<PlayerGroup> editable = editable(sender);
        if (editable.isEmpty()) {
            targeting.send(sender, "group.list-empty", Map.of());
            return Command.SINGLE_SUCCESS;
        }
        targeting.send(sender, "group.list-header", Map.of());
        for (PlayerGroup group : editable) {
            targeting.send(sender, "group.list-entry", Map.of("group", group.id(),
                    "owner", targeting.ownerName(group.owner()), "count", String.valueOf(group.members().size())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> context, boolean serverOwned) {
        CommandSender sender = context.getSource().getSender();
        String name = PlayerGroupRegistry.normalize(StringArgumentType.getString(context, NAME));
        Optional<PlayerGroupRegistry.Refusal> refusal = groups.refusal(name);
        if (refusal.isPresent()) {
            targeting.send(sender, refusal.get() == PlayerGroupRegistry.Refusal.BAD_NAME ? "group.bad-name" : "group.taken",
                    Map.of("group", name));
            return Command.SINGLE_SUCCESS;
        }
        NpcOwner owner;
        if (serverOwned || !(sender instanceof Player player)) {
            // Only staff reach here without a player: the requires() of the node lets nobody else in.
            owner = NpcOwner.server();
        } else {
            Optional<String> profile = targeting.characters().activeProfile(player.getUniqueId());
            if (profile.isEmpty()) {
                targeting.send(sender, "group.no-character", Map.of());
                return Command.SINGLE_SUCCESS;
            }
            owner = NpcOwner.character(player.getUniqueId(), profile.get());
            int limit = configuration.config().getInt("groups.per-character", 0);
            if (!targeting.isStaff(sender) && limit > 0 && groups.ownedBy(owner).size() >= limit) {
                targeting.send(sender, "group.group-limit", Map.of("limit", String.valueOf(limit)));
                return Command.SINGLE_SUCCESS;
            }
        }
        groups.create(name, owner);
        targeting.send(sender, "group.created", Map.of("group", name));
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        editableGroup(sender, StringArgumentType.getString(context, NAME)).ifPresent(group -> {
            int revoked = groups.delete(group.id()).orElse(0);
            targeting.send(sender, "group.deleted", Map.of("group", group.id(), "grants", String.valueOf(revoked)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        editableGroup(sender, StringArgumentType.getString(context, NAME)).ifPresent(group -> {
            Map<String, String> names = Map.of("group", group.id(), "owner", targeting.ownerName(group.owner()));
            if (group.members().isEmpty()) {
                targeting.send(sender, "group.info-empty", names);
                return;
            }
            targeting.send(sender, "group.info-header", names);
            group.members().forEach(member -> targeting.send(sender, "group.info-entry", Map.of("member", memberName(member))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int changeMember(CommandContext<CommandSourceStack> context, String profile, boolean adding) {
        CommandSender sender = context.getSource().getSender();
        Optional<PlayerGroup> group = editableGroup(sender, StringArgumentType.getString(context, NAME));
        if (group.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<GroupMember> member = member(sender, StringArgumentType.getString(context, PLAYER), profile);
        if (member.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Map<String, String> names = Map.of("group", group.get().id(), "member", memberName(member.get()));
        boolean isIn = group.get().members().contains(member.get());
        if (adding) {
            if (isIn) {
                targeting.send(sender, "group.already-member", names);
                return Command.SINGLE_SUCCESS;
            }
            int limit = configuration.config().getInt("groups.max-members", 0);
            if (!targeting.isStaff(sender) && limit > 0 && group.get().members().size() >= limit) {
                targeting.send(sender, "group.member-limit", Map.of("group", group.get().id(), "limit", String.valueOf(limit)));
                return Command.SINGLE_SUCCESS;
            }
            groups.addMember(group.get().id(), member.get());
            targeting.send(sender, "group.added", names);
        } else {
            if (!isIn) {
                targeting.send(sender, "group.not-member", names);
                return Command.SINGLE_SUCCESS;
            }
            groups.removeMember(group.get().id(), member.get());
            targeting.send(sender, "group.removed", names);
        }
        return Command.SINGLE_SUCCESS;
    }

    /** A player name and, optionally, one of their profiles; without one, every character of the player. */
    private Optional<GroupMember> member(CommandSender sender, String playerName, String profile) {
        OfflinePlayer player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            player = Bukkit.getOfflinePlayerIfCached(playerName);
        }
        if (player == null) {
            targeting.send(sender, "npc.unknown-player", Map.of("player", playerName));
            return Optional.empty();
        }
        if (profile == null) {
            return Optional.of(GroupMember.everyCharacter(player.getUniqueId()));
        }
        if (!targeting.characters().profiles(player.getUniqueId()).contains(profile)) {
            String name = player.getName() == null ? playerName : player.getName();
            targeting.send(sender, "npc.unknown-profile", Map.of("player", name, "profile", profile));
            return Optional.empty();
        }
        return Optional.of(GroupMember.character(player.getUniqueId(), profile));
    }

    /** The group, when it exists and the sender may change it. */
    private Optional<PlayerGroup> editableGroup(CommandSender sender, String name) {
        Optional<PlayerGroup> group = groups.get(name);
        if (group.isEmpty()) {
            targeting.send(sender, "group.unknown", Map.of("group", PlayerGroupRegistry.normalize(name)));
            return Optional.empty();
        }
        if (!mayEdit(sender, group.get())) {
            targeting.send(sender, "group.not-yours", Map.of("group", group.get().id(),
                    "owner", targeting.ownerName(group.get().owner())));
            return Optional.empty();
        }
        return group;
    }

    /** Staff, or a player playing the owning character. */
    private boolean mayEdit(CommandSender sender, PlayerGroup group) {
        if (targeting.isStaff(sender)) {
            return true;
        }
        return sender instanceof Player player && group.owner() instanceof NpcOwner.CharacterOwner owner
                && owner.player().equals(player.getUniqueId())
                && targeting.characters().activeProfile(player.getUniqueId()).map(owner.profile()::equals).orElse(false);
    }

    /** Every group for staff; the groups of the character a player plays for anyone else. */
    private List<PlayerGroup> editable(CommandSender sender) {
        if (targeting.isStaff(sender)) {
            return groups.all();
        }
        return groups.all().stream().filter(group -> mayEdit(sender, group)).toList();
    }

    private SuggestionProvider<CommandSourceStack> editableGroups() {
        return (context, builder) -> {
            String typed = builder.getRemainingLowerCase();
            editable(context.getSource().getSender()).stream().map(PlayerGroup::id)
                    .filter(id -> id.startsWith(typed)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** A member as messages.yml writes it. */
    private String memberName(GroupMember member) {
        String player = Bukkit.getOfflinePlayer(member.player()).getName();
        String name = player == null ? member.player().toString() : player;
        return member.profile() == null
                ? configuration.messages().plain("group.member-every", Map.of("player", name))
                : configuration.messages().plain("group.member-character", Map.of("player", name, "profile", member.profile()));
    }
}
