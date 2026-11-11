package org.skysongdev.skysongnpcs.picks;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.api.choices.ProficiencyPickGrant;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.storage.PickStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Every NPC's proficiency picks: the grants it was given, what it picked with them, and the
 * rules for picking. Kept in memory and written through to the store.
 *
 * <p>A pick raises one proficiency of the grant's skill to the grant's tier. A proficiency
 * already at that tier or above cannot be picked, and one grant cannot pick the same
 * proficiency twice. Several picks of one grant are checked together and saved together, so
 * a confirm screen either saves all of them or none.
 */
public final class ProficiencyPicks {
    private final NpcRegistry registry;
    private final ProficiencyCatalog catalog;
    private final PickStore store;
    private final Changes changes;
    private final Logger logger;
    /** Grants by id, in the order they were given. */
    private final Map<UUID, PickGrant> grants = new LinkedHashMap<>();
    /** Spends by grant id, oldest first. */
    private final Map<UUID, List<PickSpend>> spends = new LinkedHashMap<>();

    public ProficiencyPicks(NpcRegistry registry, ProficiencyCatalog catalog, PickStore store, Changes changes,
                            Logger logger) {
        this.registry = registry;
        this.catalog = catalog;
        this.store = store;
        this.changes = changes;
        this.logger = logger;
    }

    /** Reads every grant and spend from the store. Rows of NPCs no longer managed are dropped. */
    public void load() {
        grants.clear();
        spends.clear();
        Set<UUID> stale = new HashSet<>();
        for (PickGrant grant : store.loadGrants()) {
            if (registry.isManaged(grant.npc())) {
                grants.put(grant.id(), grant);
            } else {
                stale.add(grant.npc());
            }
        }
        for (PickSpend spend : store.loadSpends()) {
            if (grants.containsKey(spend.grant())) {
                spends.computeIfAbsent(spend.grant(), ignored -> new ArrayList<>()).add(spend);
            }
        }
        stale.forEach(store::deleteAll);
        if (!stale.isEmpty()) {
            logger.info("Dropped the proficiency picks of " + stale.size() + " NPC(s) that are no longer managed.");
        }
    }

    public ProficiencyCatalog catalog() {
        return catalog;
    }

    /**
     * Gives an NPC proficiency picks. Picks from the same source, skill and tier are added to
     * the grant it already has.
     *
     * @param skill a SkysongStats skill id or alias
     * @param tier  a SkysongStats tier id
     * @param count 1 or more
     * @return the grant, new or grown
     * @throws IllegalArgumentException when the NPC is not managed, the skill or tier is
     *                                  unknown, the count is under 1 or the source is not valid
     */
    public PickGrant grant(UUID npc, String source, String skill, String tier, int count) {
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        String skillId = catalog.skillId(skill)
                .orElseThrow(() -> new IllegalArgumentException("SkysongStats has no skill " + skill));
        String tierId = catalog.tierId(tier)
                .orElseThrow(() -> new IllegalArgumentException("SkysongStats has no tier " + tier));
        if (count < 1) {
            throw new IllegalArgumentException("A grant gives 1 pick or more, not " + count);
        }
        Optional<PickGrant> same = grants.values().stream()
                .filter(grant -> grant.npc().equals(npc) && grant.source().equals(source)
                        && grant.skill().equals(skillId) && grant.tier().equals(tierId))
                .findFirst();
        PickGrant saved = same.map(grant -> grant.withCount(grant.count() + count))
                .orElseGet(() -> new PickGrant(UUID.randomUUID(), npc, source, skillId, tierId, count));
        grants.put(saved.id(), saved);
        store.saveGrant(saved);
        return saved;
    }

    /** An NPC's grants, oldest first, spent or not. */
    public List<PickGrant> grants(UUID npc) {
        return grants.values().stream().filter(grant -> grant.npc().equals(npc)).toList();
    }

    public Optional<PickGrant> grant(UUID id) {
        return Optional.ofNullable(grants.get(id));
    }

    /** What was picked with a grant, oldest first. */
    public List<PickSpend> spends(UUID grant) {
        return List.copyOf(spends.getOrDefault(grant, List.of()));
    }

    /** The picks of a grant not spent yet. */
    public int remaining(PickGrant grant) {
        return Math.max(0, grant.count() - spends.getOrDefault(grant.id(), List.of()).size());
    }

    /** An NPC's grants that still have picks, oldest first. */
    public List<PickGrant> open(UUID npc) {
        return grants(npc).stream().filter(grant -> remaining(grant) > 0).toList();
    }

    /** Every unspent pick an NPC has. */
    public int pending(UUID npc) {
        return grants(npc).stream().mapToInt(this::remaining).sum();
    }

    /** The unspent picks an NPC has from one source. */
    public int unspent(UUID npc, String source) {
        return grants(npc).stream().filter(grant -> grant.source().equals(source)).mapToInt(this::remaining).sum();
    }

    /**
     * Takes back up to {@code max} unspent picks one source gave an NPC, newest grants first.
     * Spent picks stay: a grant keeps at least as many picks as were spent with it.
     *
     * @return how many were taken back
     */
    public int removeUnspent(UUID npc, String source, int max) {
        int left = max;
        List<PickGrant> fromSource = new ArrayList<>(grants(npc).stream()
                .filter(grant -> grant.source().equals(source)).toList());
        Collections.reverse(fromSource);
        for (PickGrant grant : fromSource) {
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
                spends.remove(grant.id());
                store.deleteGrant(grant.id());
            } else {
                PickGrant smaller = grant.withCount(count);
                grants.put(smaller.id(), smaller);
                store.saveGrant(smaller);
            }
        }
        return max - left;
    }

    /** Takes back every unspent pick one source gave an NPC. */
    public int removeUnspent(UUID npc, String source) {
        return removeUnspent(npc, source, Integer.MAX_VALUE);
    }

    /**
     * The proficiencies a grant can pick now: those of its skill below its tier that it has
     * not picked already. Empty when the NPC has no sheet.
     */
    public List<String> options(PickGrant grant) {
        int target = catalog.rank(grant.tier());
        Set<String> picked = new HashSet<>();
        spends.getOrDefault(grant.id(), List.of()).forEach(spend -> picked.add(spend.proficiency()));
        List<String> options = new ArrayList<>();
        for (String proficiency : catalog.proficienciesOf(grant.skill())) {
            Optional<String> tier = catalog.tier(grant.npc(), proficiency);
            if (tier.isPresent() && catalog.rank(tier.get()) < target && !picked.contains(proficiency)) {
                options.add(proficiency);
            }
        }
        return options;
    }

    /**
     * Spends picks of a grant: each proficiency goes up to the grant's tier. Every one is
     * checked before any is saved, so either all are saved or none.
     *
     * @param player       who picked, or null for the API and staff commands
     * @param proficiencies proficiency ids or aliases, each once
     * @return what happened; {@link PickResult#DONE} when all were saved
     */
    public PickResult pick(@Nullable Player player, UUID grantId, List<String> proficiencies) {
        PickGrant grant = grants.get(grantId);
        if (grant == null) {
            return PickResult.NO_SUCH_GRANT;
        }
        if (proficiencies.isEmpty()) {
            return PickResult.NOTHING_PICKED;
        }
        if (proficiencies.size() > remaining(grant)) {
            return PickResult.TOO_MANY;
        }
        List<String> options = options(grant);
        List<String> ids = new ArrayList<>();
        for (String name : proficiencies) {
            Optional<String> id = catalog.proficiencyId(name);
            if (id.isEmpty()) {
                return PickResult.UNKNOWN_PROFICIENCY;
            }
            if (!catalog.skillOf(id.get()).map(grant.skill()::equals).orElse(false)) {
                return PickResult.WRONG_SKILL;
            }
            if (ids.contains(id.get()) || !options.contains(id.get())) {
                return catalog.tier(grant.npc(), id.get()).isEmpty() ? PickResult.NO_SHEET : PickResult.NOT_AN_OPTION;
            }
            ids.add(id.get());
        }
        Optional<NpcRecord> npc = registry.get(grant.npc());
        if (npc.isEmpty()) {
            return PickResult.NO_SUCH_GRANT;
        }
        for (String id : ids) {
            String previous = catalog.tier(grant.npc(), id).orElseThrow();
            if (!catalog.setTier(grant.npc(), id, grant.tier())) {
                return PickResult.NO_SHEET;
            }
            PickSpend spend = new PickSpend(grant.id(), id, previous);
            spends.computeIfAbsent(grant.id(), ignored -> new ArrayList<>()).add(spend);
            store.saveSpend(spend);
            changes.picked(npc.get(), player, grant, id);
        }
        return PickResult.DONE;
    }

    /**
     * Spends one pick on a proficiency from the NPC's oldest grant of its skill that can take
     * it. When none can, the answer of the first grant of that skill says why.
     *
     * @param player who picked, or null for the API and staff commands
     * @return {@link PickResult#WRONG_SKILL} when the NPC has no picks of that skill left
     */
    public PickResult pickAny(@Nullable Player player, UUID npc, String proficiency) {
        Optional<String> skill = catalog.proficiencyId(proficiency).flatMap(catalog::skillOf);
        if (skill.isEmpty()) {
            return PickResult.UNKNOWN_PROFICIENCY;
        }
        PickResult first = PickResult.WRONG_SKILL;
        boolean tried = false;
        for (PickGrant grant : open(npc)) {
            if (!grant.skill().equals(skill.get())) {
                continue;
            }
            PickResult result = pick(player, grant.id(), List.of(proficiency));
            if (result == PickResult.DONE) {
                return result;
            }
            if (!tried) {
                first = result;
                tried = true;
            }
        }
        return first;
    }

    /** A grant as the API shows it. */
    public ProficiencyPickGrant toApi(PickGrant grant) {
        return new ProficiencyPickGrant(grant.id(), grant.source(), grant.skill(), grant.tier(), grant.count(),
                spends(grant.id()).stream().map(PickSpend::proficiency).toList());
    }

    /**
     * Gives back every spent pick of an NPC: each picked proficiency goes back to the tier it
     * had, unless something else changed it since, and the picks can be spent again.
     *
     * @return how many picks were given back
     */
    public int respec(UUID npc) {
        int refunded = 0;
        for (PickGrant grant : grants(npc)) {
            List<PickSpend> spent = spends.remove(grant.id());
            if (spent == null) {
                continue;
            }
            for (int i = spent.size() - 1; i >= 0; i--) {
                PickSpend spend = spent.get(i);
                if (catalog.tier(npc, spend.proficiency()).map(grant.tier()::equals).orElse(false)) {
                    catalog.setTier(npc, spend.proficiency(), spend.previousTier());
                }
                store.deleteSpend(grant.id(), spend.proficiency());
                refunded++;
            }
        }
        return refunded;
    }

    /** Forgets an NPC's picks, as when it is deleted. Its proficiency tiers are left as they are. */
    public void deleteAll(UUID npc) {
        List<UUID> ids = grants(npc).stream().map(PickGrant::id).toList();
        if (ids.isEmpty()) {
            return;
        }
        ids.forEach(id -> {
            grants.remove(id);
            spends.remove(id);
        });
        store.deleteAll(npc);
    }

    /**
     * Gives a copied NPC the same grants and spends as its original, under new ids. Its
     * proficiency tiers are the sheet's business: they are copied with the sheet.
     */
    public void copy(UUID original, UUID copy) {
        if (!registry.isManaged(copy)) {
            return;
        }
        for (PickGrant grant : grants(original)) {
            PickGrant copied = grant.copiedTo(copy);
            grants.put(copied.id(), copied);
            store.saveGrant(copied);
            for (PickSpend spend : spends.getOrDefault(grant.id(), List.of())) {
                PickSpend copiedSpend = new PickSpend(copied.id(), spend.proficiency(), spend.previousTier());
                spends.computeIfAbsent(copied.id(), ignored -> new ArrayList<>()).add(copiedSpend);
                store.saveSpend(copiedSpend);
            }
        }
    }

    /** Hears about every pick spent. */
    @FunctionalInterface
    public interface Changes {
        void picked(NpcRecord npc, @Nullable Player player, PickGrant grant, String proficiency);
    }
}
