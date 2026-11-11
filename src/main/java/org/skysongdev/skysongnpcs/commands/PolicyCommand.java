package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.access.ActionPresets;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.policy.AccessPolicy;
import org.skysongdev.skysongnpcs.api.policy.PolicyOwner;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.policy.PolicyRegistry;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * {@code /snpc policy}: access policies, named sets of grants many NPCs follow at once.
 * <ul>
 *   <li>{@code /snpc policy} says which policy the targeted NPC follows.</li>
 *   <li>{@code /snpc policy list} lists your character's policies (staff: every policy).</li>
 *   <li>{@code /snpc policy create|delete|info <policy>} makes, deletes or shows one.</li>
 *   <li>{@code /snpc policy grant <policy> <group> <actions>} and
 *       {@code revoke <policy> <group> [actions]} change its grants, as {@code /snpc access} does.</li>
 *   <li>{@code /snpc policy assign <policy>} and {@code unassign} put the targeted NPC in or out.</li>
 * </ul>
 * A policy belongs to the character that made it; only they (while playing it) and staff
 * change it. Putting an NPC in or out also needs {@code manage} on that NPC, and the policy
 * must be yours.
 */
public final class PolicyCommand implements SubCommand {
    private static final String POLICY = "policy";
    private static final String ARGUMENTS = "arguments";

    private final NpcTargeting targeting;
    private final AccessService access;
    private final AccessCommand groups;
    private final PolicyRegistry policies;
    private final PluginConfiguration configuration;

    /** @param groups reads groups and suggests them the way {@code /snpc access} does */
    public PolicyCommand(NpcTargeting targeting, AccessService access, AccessCommand groups, PolicyRegistry policies,
                         PluginConfiguration configuration) {
        this.targeting = targeting;
        this.access = access;
        this.groups = groups;
        this.policies = policies;
        this.configuration = configuration;
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(POLICY)
                .requires(source -> source.getSender() instanceof Player || targeting.isStaff(source.getSender()))
                .executes(context -> run(context, this::show))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list").executes(context -> run(context, this::list)))
                .then(withPolicy("create", this::create))
                .then(withPolicy("delete", (sender, id) -> editable(sender, id).ifPresent(policy -> delete(sender, policy))))
                .then(withPolicy("info", (sender, id) -> editable(sender, id).ifPresent(policy -> info(sender, policy))))
                .then(change("grant", true))
                .then(change("revoke", false))
                .then(withPolicy("assign", this::assign))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("unassign").executes(context -> run(context, this::unassign)));
    }

    private static int run(CommandContext<CommandSourceStack> context, Consumer<CommandSender> action) {
        action.accept(context.getSource().getSender());
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> withPolicy(String literal, BiConsumer<CommandSender, String> action) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(literal)
                .then(policyArgument().executes(context -> {
                    action.accept(context.getSource().getSender(), StringArgumentType.getString(context, POLICY));
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** {@code grant <policy> <group> <actions>} or {@code revoke <policy> <group> [actions]}. */
    private LiteralArgumentBuilder<CommandSourceStack> change(String literal, boolean granting) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(literal)
                .then(policyArgument()
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument(ARGUMENTS, StringArgumentType.greedyString())
                                .suggests(groups.suggestions())
                                .executes(context -> {
                                    CommandSender sender = context.getSource().getSender();
                                    editable(sender, StringArgumentType.getString(context, POLICY)).ifPresent(policy ->
                                            change(sender, policy, StringArgumentType.getString(context, ARGUMENTS), granting));
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> policyArgument() {
        return RequiredArgumentBuilder.<CommandSourceStack, String>argument(POLICY, StringArgumentType.word())
                .suggests(policySuggestions());
    }

    private void show(CommandSender sender) {
        Optional<NpcRecord> npc = manageable(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcName(npc.get().id());
        policies.policyOf(npc.get().id()).ifPresentOrElse(
                policy -> send(sender, "policy.show", names(policy, "npc", name)),
                () -> send(sender, "policy.show-none", Map.of("npc", name)));
    }

    private void list(CommandSender sender) {
        Collection<AccessPolicy> shown;
        if (targeting.isStaff(sender)) {
            shown = policies.all();
        } else {
            Optional<PolicyOwner> self = self(sender);
            if (self.isEmpty()) {
                send(sender, "policy.no-character", Map.of());
                return;
            }
            shown = policies.ownedBy(self.get());
        }
        if (shown.isEmpty()) {
            send(sender, "policy.list-empty", Map.of());
            return;
        }
        send(sender, "policy.list-header", Map.of());
        shown.forEach(policy -> send(sender, "policy.list-entry", names(policy)));
    }

    private void create(CommandSender sender, String id) {
        Optional<PolicyOwner> self = self(sender);
        if (self.isEmpty()) {
            send(sender, "policy.no-character", Map.of());
            return;
        }
        String key;
        try {
            key = AccessPolicy.normalizeId(id);
        } catch (IllegalArgumentException notAnId) {
            send(sender, "policy.bad-id", Map.of(POLICY, id));
            return;
        }
        if (policies.get(key).isPresent()) {
            send(sender, "policy.taken", Map.of(POLICY, key));
            return;
        }
        int limit = configuration.config().getInt("policies.per-character", 0);
        if (limit > 0 && !targeting.isStaff(sender) && policies.ownedBy(self.get()).size() >= limit) {
            send(sender, "policy.limit", Map.of("limit", Integer.toString(limit)));
            return;
        }
        send(sender, "policy.created", names(policies.create(key, self.get())));
    }

    private void delete(CommandSender sender, AccessPolicy policy) {
        Map<String, String> names = names(policy);
        policies.delete(policy.id());
        send(sender, "policy.deleted", names);
    }

    private void info(CommandSender sender, AccessPolicy policy) {
        if (policy.grants().isEmpty()) {
            send(sender, "policy.info-empty", names(policy));
            return;
        }
        send(sender, "policy.info-header", names(policy));
        for (AccessGrant grant : policy.grants()) {
            send(sender, "policy.info-entry", Map.of("group", groups.groupName(grant.group()), "actions", keys(grant.actions())));
        }
    }

    private void change(CommandSender sender, AccessPolicy policy, String arguments, boolean granting) {
        String[] split = AccessCommand.splitGroup(arguments.trim());
        Optional<AccessGroup> group = groups.group(sender, split[0]);
        if (group.isEmpty()) {
            return;
        }
        ActionPresets.Parsed parsed = access.presets().parse(split[1]);
        if (parsed.unknown() != null) {
            send(sender, "access.unknown-action", Map.of(
                    "word", parsed.unknown(),
                    "actions", Arrays.stream(NpcAction.values()).map(NpcAction::key).collect(Collectors.joining(", ")),
                    "presets", String.join(", ", access.presets().names())));
            return;
        }
        Map<String, String> names = names(policy, "group", groups.groupName(group.get()));
        Set<NpcAction> current = policies.find(policy, group.get()).map(AccessGrant::actions).orElse(Set.of());
        if (granting) {
            if (parsed.actions().isEmpty()) {
                send(sender, "access.no-actions", Map.of("presets", String.join(", ", access.presets().names())));
                return;
            }
            Set<NpcAction> merged = EnumSet.copyOf(parsed.actions());
            merged.addAll(current);
            policies.setGrant(policy.id(), group.get(), merged);
            send(sender, "policy.granted", with(names, "actions", keys(merged)));
            return;
        }
        if (current.isEmpty()) {
            send(sender, "policy.not-granted", names);
            return;
        }
        Set<NpcAction> left = EnumSet.noneOf(NpcAction.class);
        if (!parsed.actions().isEmpty()) {
            left.addAll(current);
            left.removeAll(parsed.actions());
        }
        policies.setGrant(policy.id(), group.get(), left);
        send(sender, left.isEmpty() ? "policy.revoked-all" : "policy.revoked", with(names, "actions", keys(left)));
    }

    private void assign(CommandSender sender, String id) {
        Optional<NpcRecord> npc = manageable(sender);
        if (npc.isEmpty()) {
            return;
        }
        Optional<AccessPolicy> policy = editable(sender, id);
        if (policy.isEmpty()) {
            return;
        }
        Map<String, String> names = names(policy.get(), "npc", targeting.npcName(npc.get().id()));
        if (policies.policyOf(npc.get().id()).map(AccessPolicy::id).filter(policy.get().id()::equals).isPresent()) {
            send(sender, "policy.already", names);
            return;
        }
        policies.assign(npc.get().id(), policy.get().id());
        send(sender, "policy.assigned", names);
    }

    private void unassign(CommandSender sender) {
        Optional<NpcRecord> npc = manageable(sender);
        if (npc.isEmpty()) {
            return;
        }
        String name = targeting.npcName(npc.get().id());
        Optional<AccessPolicy> policy = policies.policyOf(npc.get().id());
        if (policy.isEmpty()) {
            send(sender, "policy.show-none", Map.of("npc", name));
            return;
        }
        policies.unassign(npc.get().id());
        send(sender, "policy.unassigned", names(policy.get(), "npc", name));
    }

    /** The policy, when it exists and the sender is staff or plays the character it belongs to. */
    private Optional<AccessPolicy> editable(CommandSender sender, String id) {
        Optional<AccessPolicy> policy;
        try {
            policy = policies.get(id);
        } catch (IllegalArgumentException notAnId) {
            policy = Optional.empty();
        }
        if (policy.isEmpty()) {
            send(sender, "policy.unknown", Map.of(POLICY, id));
            return Optional.empty();
        }
        if (targeting.isStaff(sender) || self(sender).filter(policy.get().owner()::equals).isPresent()) {
            return policy;
        }
        send(sender, "policy.not-yours", Map.of(POLICY, policy.get().id()));
        return Optional.empty();
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
        send(sender, "access.not-allowed", Map.of("npc", targeting.npcName(npc.get().id())));
        return Optional.empty();
    }

    /** The character the sender plays now, as a policy owner. */
    private Optional<PolicyOwner> self(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return Optional.empty();
        }
        return targeting.characters().activeProfile(player.getUniqueId())
                .map(profile -> PolicyOwner.character(player.getUniqueId(), profile));
    }

    /** The policies the sender may change: their character's, or every one for staff. */
    private SuggestionProvider<CommandSourceStack> policySuggestions() {
        return (context, builder) -> {
            CommandSender sender = context.getSource().getSender();
            Collection<AccessPolicy> shown = targeting.isStaff(sender)
                    ? policies.all()
                    : self(sender).map(policies::ownedBy).orElse(List.of());
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            shown.stream().map(AccessPolicy::id).filter(id -> id.startsWith(typed)).forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** {@code <policy>}, {@code <owner>} and {@code <members>} of a policy, plus one more pair. */
    private Map<String, String> names(AccessPolicy policy, String key, String value) {
        return with(names(policy), key, value);
    }

    private Map<String, String> names(AccessPolicy policy) {
        return Map.of(
                POLICY, policy.id(),
                "owner", ownerName(policy.owner()),
                "members", Integer.toString(policies.members(policy.id()).size()));
    }

    private String ownerName(PolicyOwner owner) {
        return switch (owner) {
            case PolicyOwner.CharacterOwner character -> {
                String player = Bukkit.getOfflinePlayer(character.player()).getName();
                yield configuration.messages().plain("owner-name.character", Map.of(
                        "player", player == null ? character.player().toString() : player,
                        "profile", character.profile()));
            }
            case PolicyOwner.PluginOwner plugin ->
                    configuration.messages().plain("policy.owner-plugin", Map.of("plugin", plugin.plugin()));
        };
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        targeting.send(sender, key, placeholders);
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
