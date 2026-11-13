package org.skysongdev.skysongnpcs.abilities;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.ability.AbilityChoiceResult;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilityChoice;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.AbilityChoiceStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Every NPC's ability choices: the grants it was given and what it chose with them. Kept in
 * memory and written through to the store.
 *
 * <p>A chosen ability is granted through {@link AbilityService} under {@link #abilitySource}
 * of the grant's source. A choice counts only while that source still holds the ability, so
 * an ability that is revoked, or dropped from abilities.yml, gives its choice back without
 * any bookkeeping here. Options abilities.yml no longer defines, and abilities the NPC has
 * already, are not offered.
 */
public final class AbilityChoiceService {
    /** What a chosen ability's source starts with. */
    public static final String SOURCE_PREFIX = "choice:";

    private final NpcRegistry registry;
    private final AbilityService abilities;
    private final AbilityChoiceStore store;
    private final Changes changes;
    private final Logger logger;
    /** Grants by id, in the order they were given. */
    private final Map<UUID, AbilityChoiceGrant> grants = new LinkedHashMap<>();
    /** Chosen abilities by grant id, oldest first. */
    private final Map<UUID, List<String>> chosen = new LinkedHashMap<>();

    public AbilityChoiceService(NpcRegistry registry, AbilityService abilities, AbilityChoiceStore store,
                                Changes changes, Logger logger) {
        this.registry = registry;
        this.abilities = abilities;
        this.store = store;
        this.changes = changes;
        this.logger = logger;
    }

    /** The ability source a grant's chosen abilities are held by. */
    public static String abilitySource(String grantSource) {
        return SOURCE_PREFIX + grantSource;
    }

    /** Reads every grant and choice from the store. Rows of NPCs no longer managed are dropped. */
    public void load() {
        grants.clear();
        chosen.clear();
        Set<UUID> stale = new HashSet<>();
        for (AbilityChoiceGrant grant : store.loadGrants()) {
            if (registry.isManaged(grant.npc())) {
                grants.put(grant.id(), grant);
            } else {
                stale.add(grant.npc());
            }
        }
        for (AbilityChoiceStore.StoredChoice choice : store.loadChoices()) {
            if (grants.containsKey(choice.grant())) {
                chosen.computeIfAbsent(choice.grant(), ignored -> new ArrayList<>()).add(choice.ability());
            }
        }
        stale.forEach(store::deleteAll);
        if (!stale.isEmpty()) {
            logger.info("Dropped the ability choices of " + stale.size() + " NPC(s) that are no longer managed.");
        }
    }

    /**
     * Gives an NPC a choice of abilities. A grant from the same source with the same options
     * grows by {@code count} instead.
     *
     * @param options abilities.yml ids, each defined now
     * @return the grant, new or grown
     * @throws IllegalArgumentException when the NPC is not managed, an option is not defined,
     *                                  there is no option, the count is under 1 or the source
     *                                  is not 1 to 64 characters without spaces
     */
    public AbilityChoiceGrant grant(UUID npc, String source, List<String> options, int count) {
        Objects.requireNonNull(npc, "npc");
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        for (String option : options) {
            if (abilities.definitions().get(option).isEmpty()) {
                throw new IllegalArgumentException("abilities.yml has no ability " + option);
            }
        }
        AbilityChoiceGrant wanted = new AbilityChoiceGrant(UUID.randomUUID(), npc, source, options, count);
        AbilityChoiceGrant saved = grants(npc).stream()
                .filter(grant -> grant.source().equals(wanted.source()) && grant.options().equals(wanted.options()))
                .findFirst()
                .map(grant -> grant.withCount(grant.count() + count))
                .orElse(wanted);
        grants.put(saved.id(), saved);
        store.saveGrant(saved);
        return saved;
    }

    /** An NPC's grants, oldest first, chosen or not. */
    public List<AbilityChoiceGrant> grants(UUID npc) {
        return grants.values().stream().filter(grant -> grant.npc().equals(npc)).toList();
    }

    public Optional<AbilityChoiceGrant> grant(UUID id) {
        return Optional.ofNullable(grants.get(id));
    }

    /** The abilities chosen with a grant that the NPC still holds for it, oldest first. */
    public List<String> chosen(AbilityChoiceGrant grant) {
        String source = abilitySource(grant.source());
        return chosen.getOrDefault(grant.id(), List.of()).stream()
                .filter(ability -> abilities.sources(grant.npc(), ability).contains(source))
                .toList();
    }

    /** How many abilities a grant can still choose. */
    public int remaining(AbilityChoiceGrant grant) {
        return Math.max(0, grant.count() - chosen(grant).size());
    }

    /**
     * What a grant can choose now: its options abilities.yml defines that the NPC does not
     * have, in the grant's order.
     */
    public List<String> options(AbilityChoiceGrant grant) {
        return grant.options().stream()
                .filter(ability -> abilities.definitions().get(ability).isPresent())
                .filter(ability -> !abilities.has(grant.npc(), ability))
                .toList();
    }

    /** Whether a grant has a choice left and something to choose. */
    public boolean choosable(AbilityChoiceGrant grant) {
        return remaining(grant) > 0 && !options(grant).isEmpty();
    }

    /** Every choice an NPC has left, counting only grants with something to choose. */
    public int pending(UUID npc) {
        return grants(npc).stream().filter(this::choosable)
                .mapToInt(grant -> Math.min(remaining(grant), options(grant).size())).sum();
    }

    /** The choices one source gave an NPC that are not made yet. */
    public int unspent(UUID npc, String source) {
        return grants(npc).stream().filter(grant -> grant.source().equals(source)).mapToInt(this::remaining).sum();
    }

    /**
     * Chooses abilities with a grant: each is granted to the NPC. Every one is checked before
     * any is granted, so either all are or none.
     *
     * @param player who chose, or null for the API and staff
     */
    public AbilityChoiceResult choose(@Nullable Player player, UUID grantId, List<String> picked) {
        AbilityChoiceGrant grant = grants.get(grantId);
        Optional<NpcRecord> npc = grant == null ? Optional.empty() : registry.get(grant.npc());
        if (npc.isEmpty()) {
            return AbilityChoiceResult.NO_SUCH_CHOICE;
        }
        if (picked.isEmpty()) {
            return AbilityChoiceResult.NOTHING_CHOSEN;
        }
        if (picked.size() > remaining(grant)) {
            return AbilityChoiceResult.TOO_MANY;
        }
        List<String> options = options(grant);
        if (picked.stream().distinct().count() != picked.size() || !options.containsAll(picked)) {
            return AbilityChoiceResult.NOT_AN_OPTION;
        }
        String source = abilitySource(grant.source());
        List<String> made = chosen.computeIfAbsent(grant.id(), ignored -> new ArrayList<>());
        for (String ability : picked) {
            abilities.grant(grant.npc(), ability, source);
            made.remove(ability);
            made.add(ability);
            store.saveChoice(grant.id(), ability);
            changes.chose(npc.get(), player, grant, ability);
        }
        return AbilityChoiceResult.DONE;
    }

    /**
     * Gives back the abilities an NPC chose: each is revoked for its choice, and the choice can
     * be made again. Only {@code source}'s grants, or every grant when it is null.
     *
     * @return how many choices were given back
     */
    public int respec(UUID npc, @Nullable String source) {
        int refunded = 0;
        for (AbilityChoiceGrant grant : grants(npc)) {
            if (source != null && !grant.source().equals(source)) {
                continue;
            }
            List<String> live = chosen(grant);
            List<String> made = chosen.remove(grant.id());
            if (made == null) {
                continue;
            }
            for (String ability : made) {
                store.deleteChoice(grant.id(), ability);
            }
            for (String ability : live) {
                abilities.revoke(npc, ability, abilitySource(grant.source()));
                refunded++;
            }
        }
        return refunded;
    }

    /**
     * Takes back up to {@code max} choices one source gave an NPC that are not made yet,
     * newest grants first. A grant keeps at least as many as were made with it.
     *
     * @return how many were taken back
     */
    public int removeUnspent(UUID npc, String source, int max) {
        int left = max;
        List<AbilityChoiceGrant> fromSource = new ArrayList<>(grants(npc).stream()
                .filter(grant -> grant.source().equals(source)).toList());
        Collections.reverse(fromSource);
        for (AbilityChoiceGrant grant : fromSource) {
            if (left <= 0) {
                break;
            }
            int taken = Math.min(left, remaining(grant));
            if (taken == 0) {
                continue;
            }
            left -= taken;
            int count = grant.count() - taken;
            if (count == 0) {
                grants.remove(grant.id());
                chosen.remove(grant.id());
                store.deleteGrant(grant.id());
            } else {
                AbilityChoiceGrant smaller = grant.withCount(count);
                grants.put(smaller.id(), smaller);
                store.saveGrant(smaller);
            }
        }
        return max - left;
    }

    /** Forgets an NPC's choices, as when it is deleted. Its abilities go with it elsewhere. */
    public void deleteAll(UUID npc) {
        List<UUID> ids = grants(npc).stream().map(AbilityChoiceGrant::id).toList();
        if (ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> {
            grants.remove(id);
            chosen.remove(id);
        });
        store.deleteAll(npc);
    }

    /**
     * Gives a copied NPC the original's grants and choices, under new ids. The abilities
     * themselves, with their sources, are copied by {@link AbilityService#copied}.
     */
    public void copy(UUID original, UUID copy) {
        if (!registry.isManaged(copy)) {
            return;
        }
        for (AbilityChoiceGrant grant : grants(original)) {
            AbilityChoiceGrant copied = grant.copiedTo(copy);
            grants.put(copied.id(), copied);
            store.saveGrant(copied);
            List<String> made = new ArrayList<>(chosen(grant));
            if (!made.isEmpty()) {
                chosen.put(copied.id(), made);
                made.forEach(ability -> store.saveChoice(copied.id(), ability));
            }
        }
    }

    /** A grant as the API shows it. */
    public NpcAbilityChoice toApi(AbilityChoiceGrant grant) {
        return new NpcAbilityChoice(grant.id(), grant.source(), grant.options(), grant.count(), chosen(grant));
    }

    /** Hears about every ability chosen. */
    @FunctionalInterface
    public interface Changes {
        void chose(NpcRecord npc, @Nullable Player player, AbilityChoiceGrant grant, String ability);
    }
}
