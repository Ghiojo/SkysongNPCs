package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.access.ActionPresets;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * {@code /snpc access}: who may do what with the targeted NPC.
 * <ul>
 *   <li>{@code /snpc access} lists its grants, from every source.</li>
 *   <li>{@code /snpc access grant <group> <actions>} adds actions to the group's owner-side grant.</li>
 *   <li>{@code /snpc access revoke <group> [actions]} takes those actions away, or the whole grant.</li>
 *   <li>{@code /snpc access as <source> grant|revoke ...} (staff) does the same to another
 *       source's grants, such as a plugin's.</li>
 * </ul>
 * The owner, staff and anyone the NPC lets {@code manage} may use it. Actions are action ids
 * and presets, split by spaces or commas. A group with spaces goes in quotes.
 */
public final class AccessCommand implements SubCommand {
    private static final String ARGUMENTS = "arguments";
    private static final String SOURCE = "source";

    private final NpcTargeting targeting;
    private final AccessService access;
    private final PluginConfiguration configuration;

    public AccessCommand(NpcTargeting targeting, AccessService access, PluginConfiguration configuration) {
        this.targeting = targeting;
        this.access = access;
        this.configuration = configuration;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        Function<CommandContext<CommandSourceStack>, Optional<GrantSource>> ownerSide =
                context -> Optional.of(GrantSource.owner());
        return LiteralArgumentBuilder.<CommandSourceStack>literal("access")
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()))
                .executes(context -> {
                    CommandSender sender = context.getSource().getSender();
                    manageable(sender).ifPresent(npc -> list(sender, npc));
                    return Command.SINGLE_SUCCESS;
                })
                .then(change("grant", ownerSide, true))
                .then(change("revoke", ownerSide, false))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("as")
                        .requires(source -> targeting.isStaff(source.getSender()))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(SOURCE, StringArgumentType.word())
                                .then(change("grant", this::sourceArgument, true))
                                .then(change("revoke", this::sourceArgument, false))));
    }

    /** {@code grant <group> <actions>} or {@code revoke <group> [actions]}, on the grants of the source given. */
    private LiteralArgumentBuilder<CommandSourceStack> change(
            String literal, Function<CommandContext<CommandSourceStack>, Optional<GrantSource>> source, boolean granting) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(literal)
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ARGUMENTS, StringArgumentType.greedyString())
                        .suggests(suggestions())
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            source.apply(context).ifPresent(from ->
                                    change(sender, from, StringArgumentType.getString(context, ARGUMENTS), granting));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private Optional<GrantSource> sourceArgument(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, SOURCE);
        if (name.equalsIgnoreCase(GrantSource.owner().key())) {
            return Optional.of(GrantSource.owner());
        }
        try {
            return Optional.of(GrantSource.plugin(name));
        } catch (IllegalArgumentException notAName) {
            targeting.send(context.getSource().getSender(), "access.bad-source", Map.of("source", name));
            return Optional.empty();
        }
    }

    private void list(CommandSender sender, NpcRecord npc) {
        Map<String, String> names = Map.of("npc", targeting.npcName(npc.id()), "owner", targeting.ownerName(npc.owner()));
        List<AccessGrant> grants = access.grants().grants(npc.id());
        if (grants.isEmpty()) {
            targeting.send(sender, "access.list-empty", names);
            return;
        }
        targeting.send(sender, "access.list-header", names);
        for (AccessGrant grant : grants) {
            targeting.send(sender, "access.list-entry", Map.of(
                    "group", groupName(grant.group()),
                    "source", sourceName(grant.source()),
                    "actions", keys(grant.actions())));
        }
    }

    private void change(CommandSender sender, GrantSource source, String arguments, boolean granting) {
        Optional<NpcRecord> npc = manageable(sender);
        if (npc.isEmpty()) {
            return;
        }
        String[] split = splitGroup(arguments.trim());
        Optional<AccessGroup> group = group(sender, split[0]);
        if (group.isEmpty()) {
            return;
        }
        ActionPresets.Parsed parsed = access.presets().parse(split[1]);
        if (parsed.unknown() != null) {
            targeting.send(sender, "access.unknown-action", Map.of(
                    "word", parsed.unknown(),
                    "actions", Arrays.stream(NpcAction.values()).map(NpcAction::key).collect(Collectors.joining(", ")),
                    "presets", String.join(", ", access.presets().names())));
            return;
        }
        UUID id = npc.get().id();
        Map<String, String> names = Map.of(
                "npc", targeting.npcName(id),
                "group", groupName(group.get()),
                "source", sourceName(source));
        Set<NpcAction> current = access.grants().find(id, source, group.get())
                .map(AccessGrant::actions).orElse(Set.of());
        if (granting) {
            if (parsed.actions().isEmpty()) {
                targeting.send(sender, "access.no-actions", Map.of("presets", String.join(", ", access.presets().names())));
                return;
            }
            Set<NpcAction> merged = EnumSet.copyOf(parsed.actions());
            merged.addAll(current);
            access.grants().set(id, source, group.get(), merged);
            targeting.send(sender, "access.granted", with(names, "actions", keys(merged)));
            return;
        }
        if (current.isEmpty()) {
            targeting.send(sender, "access.not-granted", names);
            return;
        }
        Set<NpcAction> left = EnumSet.noneOf(NpcAction.class);
        if (!parsed.actions().isEmpty()) {
            left.addAll(current);
            left.removeAll(parsed.actions());
        }
        access.grants().set(id, source, group.get(), left);
        if (left.isEmpty()) {
            targeting.send(sender, "access.revoked-all", names);
        } else {
            targeting.send(sender, "access.revoked", with(names, "actions", keys(left)));
        }
    }

    /** The targeted managed NPC, when the sender is staff, its owner, or may manage it. */
    private Optional<NpcRecord> manageable(CommandSender sender) {
        Optional<NpcRecord> npc = targeting.managedTarget(sender);
        if (npc.isEmpty() || targeting.isStaff(sender)) {
            return npc;
        }
        if (sender instanceof Player player && access.may(player, npc.get(), NpcAction.MANAGE)) {
            return npc;
        }
        targeting.send(sender, "access.not-allowed", Map.of("npc", targeting.npcName(npc.get().id())));
        return Optional.empty();
    }

    /**
     * Reads a group as players type it: {@code player:NAME}, {@code player:NAME/PROFILE},
     * {@code perm:NODE}, or {@code TYPE:ID} for a type another plugin registered. Player names
     * become UUIDs.
     */
    Optional<AccessGroup> group(CommandSender sender, String text) {
        AccessGroup typed;
        try {
            typed = AccessGroup.parse(text);
        } catch (IllegalArgumentException notAGroup) {
            return badGroup(sender, text);
        }
        switch (typed.type()) {
            case AccessGroup.PLAYER -> {
                return playerGroup(sender, typed.id());
            }
            case AccessGroup.PERMISSION -> {
                return Optional.of(typed);
            }
            default -> {
                return access.provider(typed.type()).isPresent() ? Optional.of(typed) : badGroup(sender, text);
            }
        }
    }

    private Optional<AccessGroup> playerGroup(CommandSender sender, String id) {
        int slash = id.indexOf('/');
        String name = slash < 0 ? id : id.substring(0, slash);
        String profile = slash < 0 ? null : id.substring(slash + 1);
        OfflinePlayer player = Bukkit.getPlayerExact(name);
        if (player == null) {
            player = Bukkit.getOfflinePlayerIfCached(name);
        }
        if (player == null) {
            targeting.send(sender, "npc.unknown-player", Map.of("player", name));
            return Optional.empty();
        }
        if (profile == null) {
            return Optional.of(AccessGroup.player(player.getUniqueId()));
        }
        if (!targeting.characters().profiles(player.getUniqueId()).contains(profile)) {
            targeting.send(sender, "npc.unknown-profile", Map.of("player", name, "profile", profile));
            return Optional.empty();
        }
        return Optional.of(AccessGroup.player(player.getUniqueId(), profile));
    }

    private Optional<AccessGroup> badGroup(CommandSender sender, String text) {
        String types = access.providerTypes().isEmpty()
                ? configuration.messages().plain("access.no-types", Map.of())
                : String.join(", ", access.providerTypes());
        targeting.send(sender, "access.bad-group", Map.of("group", text, "types", types));
        return Optional.empty();
    }

    /** A group as players read it: player names instead of UUIDs. */
    String groupName(AccessGroup group) {
        if (!group.type().equals(AccessGroup.PLAYER)) {
            return group.toString();
        }
        int slash = group.id().indexOf('/');
        String uuid = slash < 0 ? group.id() : group.id().substring(0, slash);
        String name;
        try {
            name = Bukkit.getOfflinePlayer(UUID.fromString(uuid)).getName();
        } catch (IllegalArgumentException notAUuid) {
            name = null;
        }
        return AccessGroup.PLAYER + ":" + (name == null ? uuid : name) + (slash < 0 ? "" : group.id().substring(slash));
    }

    private String sourceName(GrantSource source) {
        return source instanceof GrantSource.PluginSide plugin
                ? plugin.plugin()
                : configuration.messages().plain("access.owner-side", Map.of());
    }

    /**
     * Splits {@code <group> <actions>}: the group is the first word, or everything up to the
     * closing quote when it starts with one.
     *
     * @return the group and the rest, which may be empty
     */
    static String[] splitGroup(String arguments) {
        if (arguments.startsWith("\"")) {
            int close = arguments.indexOf('"', 1);
            if (close > 0) {
                return new String[]{arguments.substring(1, close), arguments.substring(close + 1).trim()};
            }
            return new String[]{arguments.substring(1), ""};
        }
        int space = arguments.indexOf(' ');
        return space < 0
                ? new String[]{arguments, ""}
                : new String[]{arguments.substring(0, space), arguments.substring(space + 1).trim()};
    }

    /** Groups while the first word is typed, then action ids and presets for the word being typed. */
    SuggestionProvider<CommandSourceStack> suggestions() {
        return (context, builder) -> {
            String typed = builder.getRemaining();
            if (!typed.startsWith("\"") && !typed.contains(" ")) {
                suggestGroups(context.getSource().getSender(), builder, typed.toLowerCase(Locale.ROOT));
                return builder.buildFuture();
            }
            int wordStart = Math.max(typed.lastIndexOf(' '), typed.lastIndexOf(',')) + 1;
            if (typed.startsWith("\"") && typed.indexOf('"', 1) < 0) {
                return builder.buildFuture();
            }
            SuggestionsBuilder word = builder.createOffset(builder.getStart() + wordStart);
            String start = typed.substring(wordStart).toLowerCase(Locale.ROOT);
            for (NpcAction action : NpcAction.values()) {
                if (action.key().startsWith(start)) {
                    word.suggest(action.key());
                }
            }
            for (String preset : access.presets().names()) {
                if (preset.startsWith(start)) {
                    word.suggest(preset);
                }
            }
            return word.buildFuture();
        };
    }

    private void suggestGroups(CommandSender sender, SuggestionsBuilder builder, String typed) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            String group = AccessGroup.PLAYER + ":" + online.getName();
            if (group.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(group);
            }
        }
        if ((AccessGroup.PERMISSION + ":").startsWith(typed)) {
            builder.suggest(AccessGroup.PERMISSION + ":");
        }
        for (String type : access.providerTypes()) {
            List<String> ids = sender instanceof Player player
                    ? access.provider(type).map(provider -> List.copyOf(provider.suggestions(player))).orElse(List.of())
                    : List.of();
            if (ids.isEmpty() && (type + ":").startsWith(typed)) {
                builder.suggest(type + ":");
            }
            for (String id : ids) {
                if ((type + ":" + id).toLowerCase(Locale.ROOT).startsWith(typed) && !id.contains(" ")) {
                    builder.suggest(type + ":" + id);
                }
            }
        }
    }

    private static String keys(Set<NpcAction> actions) {
        return actions.stream().map(NpcAction::key).collect(Collectors.joining(", "));
    }

    private static Map<String, String> with(Map<String, String> placeholders, String key, String value) {
        Map<String, String> copy = new HashMap<>(placeholders);
        copy.put(key, value);
        return copy;
    }
}
