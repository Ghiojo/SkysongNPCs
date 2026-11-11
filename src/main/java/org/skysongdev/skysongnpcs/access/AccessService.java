package org.skysongdev.skysongnpcs.access;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GroupProvider;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;
import org.skysongdev.skysongnpcs.characters.CharacterLookup;
import org.skysongdev.skysongnpcs.commands.Permissions;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Answers "may this player do this with this NPC?". The owner, while playing the owning
 * character, and staff may do everything; everyone else needs a grant whose group they are in.
 * Group membership is checked live: {@code player:} against the character played now,
 * {@code perm:} against permissions, and other types against the plugin that provides them.
 */
public final class AccessService {
    /** Types a plugin cannot register: ours now, and the {@code lp:} and {@code group:} groups to come. */
    static final Set<String> RESERVED_TYPES = Set.of(AccessGroup.PLAYER, AccessGroup.PERMISSION, "lp", "group");

    private final AccessRegistry grants;
    private final CharacterLookup characters;
    private final ActionPresets presets;
    private final Logger logger;
    private final Map<String, Registration> providers = new LinkedHashMap<>();
    // M5: access policies
    private PolicyGrants policies = PolicyGrants.NONE;

    public AccessService(AccessRegistry grants, CharacterLookup characters, ActionPresets presets, Logger logger) {
        this.grants = grants;
        this.characters = characters;
        this.presets = presets;
        this.logger = logger;
    }

    public AccessRegistry grants() {
        return grants;
    }

    public ActionPresets presets() {
        return presets;
    }

    /** Whether the player may do the action with the NPC now. */
    public boolean may(Player player, NpcRecord npc, NpcAction action) {
        if (isStaff(player)) {
            return true;
        }
        String profile = characters.activeProfile(player.getUniqueId()).orElse(null);
        if (isOwner(player, profile, npc)) {
            return true;
        }
        for (AccessGrant grant : effectiveGrants(npc)) {
            if (grant.allows(action) && contains(grant.group(), player, profile)) {
                return true;
            }
        }
        return false;
    }

    /** Every action the player may do with the NPC now, {@code -view}s covered by an {@code -edit} included. */
    public Set<NpcAction> allowed(Player player, NpcRecord npc) {
        if (isStaff(player)) {
            return Collections.unmodifiableSet(EnumSet.allOf(NpcAction.class));
        }
        String profile = characters.activeProfile(player.getUniqueId()).orElse(null);
        if (isOwner(player, profile, npc)) {
            return Collections.unmodifiableSet(EnumSet.allOf(NpcAction.class));
        }
        Set<NpcAction> allowed = EnumSet.noneOf(NpcAction.class);
        for (AccessGrant grant : effectiveGrants(npc)) {
            if (contains(grant.group(), player, profile)) {
                for (NpcAction action : NpcAction.values()) {
                    if (grant.allows(action)) {
                        allowed.add(action);
                    }
                }
            }
        }
        return Collections.unmodifiableSet(allowed);
    }

    // M5: access policies

    /** Lets NPCs also allow what the policy they follow grants. */
    public void usePolicies(PolicyGrants policies) {
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    /** The NPC's own grants, then its policy's. */
    private List<AccessGrant> effectiveGrants(NpcRecord npc) {
        List<AccessGrant> policyGrants = policies.grants(npc.id());
        if (policyGrants.isEmpty()) {
            return grants.grants(npc.id());
        }
        List<AccessGrant> all = new ArrayList<>(grants.grants(npc.id()));
        all.addAll(policyGrants);
        return all;
    }

    /** Whether the player is in a group, playing {@code profile} (null for none). */
    public boolean contains(AccessGroup group, Player player, String profile) {
        switch (group.type()) {
            case AccessGroup.PLAYER -> {
                String self = player.getUniqueId().toString();
                return group.id().equals(self) || (profile != null && group.id().equals(self + "/" + profile));
            }
            case AccessGroup.PERMISSION -> {
                return player.hasPermission(group.id());
            }
            default -> {
                Registration registration = providers.get(group.type());
                if (registration == null) {
                    return false;
                }
                try {
                    return registration.provider().contains(group.id(), player, profile);
                } catch (RuntimeException failed) {
                    logger.log(Level.WARNING, registration.plugin().getName() + " failed to tell whether "
                            + player.getName() + " is in " + group + "; treated as not.", failed);
                    return false;
                }
            }
        }
    }

    /** @see org.skysongdev.skysongnpcs.api.access.Access#registerGroupProvider */
    public void registerProvider(Plugin plugin, String type, GroupProvider provider) {
        String key = type.toLowerCase(Locale.ROOT);
        if (RESERVED_TYPES.contains(key) || !key.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Group type " + type + " is reserved or not a lower-case word");
        }
        Registration existing = providers.get(key);
        if (existing != null && !existing.plugin().equals(plugin)) {
            throw new IllegalStateException("Group type " + key + " belongs to " + existing.plugin().getName());
        }
        providers.put(key, new Registration(plugin, provider));
    }

    public boolean unregisterProvider(String type) {
        return providers.remove(type.toLowerCase(Locale.ROOT)) != null;
    }

    /** Forgets every provider of a plugin that is disabling. */
    public void unregisterAll(Plugin plugin) {
        providers.values().removeIf(registration -> registration.plugin().equals(plugin));
    }

    /** The provider of a group type, empty for {@code player}, {@code perm} and unregistered types. */
    public Optional<GroupProvider> provider(String type) {
        return Optional.ofNullable(providers.get(type.toLowerCase(Locale.ROOT))).map(Registration::provider);
    }

    /** The registered group types, in the order they were registered. */
    public Set<String> providerTypes() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    public boolean isStaff(Player player) {
        return player.hasPermission(Permissions.ADMIN);
    }

    /** Whether the player is the NPC's owner: playing the owning character right now. */
    public boolean isOwner(Player player, NpcRecord npc) {
        return isOwner(player, characters.activeProfile(player.getUniqueId()).orElse(null), npc);
    }

    private static boolean isOwner(Player player, String profile, NpcRecord npc) {
        return npc.owner() instanceof NpcOwner.CharacterOwner owner
                && owner.player().equals(player.getUniqueId())
                && owner.profile().equals(profile);
    }

    private record Registration(Plugin plugin, GroupProvider provider) {
    }
}
