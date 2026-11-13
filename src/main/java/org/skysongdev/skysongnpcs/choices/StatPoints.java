package org.skysongdev.skysongnpcs.choices;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.choices.PointGrant;
import org.skysongdev.skysongnpcs.api.choices.SpendResult;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;
import org.skysongdev.skysongnpcs.sheets.StatSheets;
import org.skysongdev.skysongnpcs.sheets.StatSheets.StatEdit;
import org.skysongdev.skysongnpcs.storage.ChoiceStore;
import org.skysongdev.skysongnpcs.storage.ChoiceStore.StoredPointGrant;
import org.skysongdev.skysongnpcs.storage.ChoiceStore.StoredSpend;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * NPCs' stat points: grants with a source, spending them 1 point = +1 base on an eligible
 * stat up to its cap, taking unspent ones back, and respec. Kept in memory and written
 * through to the {@link ChoiceStore}.
 *
 * <p>A spend draws from the grants that allow the stat, those with the fewest eligible stats
 * first, so points that could go anywhere are kept for stats only they can reach. Each spend
 * remembers its grant, so respec and {@link #take} know which points are whose.
 */
public final class StatPoints {
    /** Longest source the table holds. */
    static final int MAX_SOURCE = 64;

    private final ChoiceStore store;
    private final NpcRegistry registry;
    private final StatSheets sheets;
    private final StatBases bases;
    private final Function<NpcRecord, PointRules> rules;
    private final Resolved resolved;
    private final LongSupplier clock;
    private final Map<UUID, List<Grant>> grants = new HashMap<>();
    /** NPC, then grant, then stat: points spent. */
    private final Map<UUID, Map<UUID, Map<String, Integer>>> spends = new HashMap<>();

    /**
     * @param rules    the eligible stats and caps of an NPC (config.yml with its template's overrides)
     * @param resolved hears about every spend, after it is saved
     */
    public StatPoints(ChoiceStore store, NpcRegistry registry, StatSheets sheets, StatBases bases,
                      Function<NpcRecord, PointRules> rules, Resolved resolved, LongSupplier clock) {
        this.store = store;
        this.registry = registry;
        this.sheets = sheets;
        this.bases = bases;
        this.rules = rules;
        this.resolved = resolved;
        this.clock = clock;
    }

    /** Reads every grant and spend from the store. Called once on enable. */
    public void load() {
        grants.clear();
        spends.clear();
        for (StoredPointGrant stored : store.loadGrants()) {
            grants.computeIfAbsent(stored.npc(), npc -> new ArrayList<>()).add(new Grant(stored));
        }
        for (StoredSpend spend : store.loadSpends()) {
            spendsOf(spend.npc()).computeIfAbsent(spend.grant(), grant -> new LinkedHashMap<>())
                    .merge(spend.stat(), spend.amount(), Integer::sum);
        }
    }

    /**
     * Gives an NPC points.
     *
     * @param eligible stat ids or aliases; empty for the NPC's defaults
     * @throws IllegalArgumentException when the NPC is not managed, the amount is below 1, the
     *                                  source is blank or too long, or a stat does not exist
     */
    public PointGrant grant(UUID npc, String source, int amount, Collection<String> eligible) {
        Objects.requireNonNull(source, "source");
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException("NPC " + npc + " is not managed");
        }
        if (amount < 1) {
            throw new IllegalArgumentException("A grant gives 1 point or more, not " + amount);
        }
        if (source.isBlank() || source.length() > MAX_SOURCE) {
            throw new IllegalArgumentException("A source is 1 to " + MAX_SOURCE + " characters: '" + source + "'");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String stat : eligible) {
            ids.add(sheets.statId(stat).orElseThrow(() -> new IllegalArgumentException("SkysongStats has no stat " + stat)));
        }
        Grant grant = new Grant(new StoredPointGrant(UUID.randomUUID(), npc, source, amount, ids, clock.getAsLong()));
        grants.computeIfAbsent(npc, id -> new ArrayList<>()).add(grant);
        store.saveGrant(grant.stored());
        return view(npc, grant);
    }

    /**
     * Takes back unspent points of a source, newest grant first. A grant left with nothing
     * spent is deleted.
     *
     * @return how many were taken
     */
    public int take(UUID npc, String source, int max) {
        List<Grant> owned = new ArrayList<>(grants.getOrDefault(npc, List.of()));
        owned.removeIf(grant -> !grant.source.equals(source));
        int taken = 0;
        for (int i = owned.size() - 1; i >= 0 && taken < max; i--) {
            Grant grant = owned.get(i);
            int spent = spent(npc, grant);
            int take = Math.min(grant.amount - spent, max - taken);
            if (take <= 0) {
                continue;
            }
            taken += take;
            grant.amount -= take;
            if (grant.amount == 0) {
                grants.get(npc).remove(grant);
                store.deleteGrant(grant.id);
            } else {
                store.saveGrant(grant.stored());
            }
        }
        return taken;
    }

    /** The NPC's grants, oldest first. */
    public List<PointGrant> grants(UUID npc) {
        return grants.getOrDefault(npc, List.of()).stream().map(grant -> view(npc, grant)).toList();
    }

    public int unspent(UUID npc) {
        return grants.getOrDefault(npc, List.of()).stream().mapToInt(grant -> grant.amount - spent(npc, grant)).sum();
    }

    public int unspent(UUID npc, String source) {
        return grants.getOrDefault(npc, List.of()).stream().filter(grant -> grant.source.equals(source))
                .mapToInt(grant -> grant.amount - spent(npc, grant)).sum();
    }

    /** The unspent points that may go on the stat. */
    public int available(UUID npc, String stat) {
        return eligibleGrants(npc, stat).stream().mapToInt(grant -> grant.amount - spent(npc, grant)).sum();
    }

    /**
     * The stats unspent points may go on: the NPC's default eligible stats first, in their
     * order, then the others grants name, in stats.yml order.
     */
    public List<String> spendable(UUID npc) {
        Optional<NpcRecord> record = registry.get(npc);
        if (record.isEmpty()) {
            return List.of();
        }
        Set<String> stats = new LinkedHashSet<>(rules.apply(record.get()).eligible());
        stats.addAll(sheets.statIds());
        return stats.stream().filter(stat -> available(npc, stat) > 0).toList();
    }

    /** The highest base points may give the stat on this NPC; empty for no cap. */
    public OptionalInt cap(UUID npc, String stat) {
        return registry.get(npc).map(record -> rules.apply(record).cap(stat)).orElse(OptionalInt.empty());
    }

    /** The stat's base value now; empty when the NPC has no sheet. */
    public OptionalInt base(UUID npc, String stat) {
        return bases.base(npc, stat);
    }

    /** The points spent on a stat, from every grant. */
    public int spentOn(UUID npc, String stat) {
        return spendsOf(npc).values().stream().mapToInt(byStat -> byStat.getOrDefault(stat, 0)).sum();
    }

    /** Whether {@link #spend} would work, without changing anything. */
    public SpendResult check(UUID npc, String stat, int amount) {
        if (amount < 1) {
            throw new IllegalArgumentException("Spend 1 point or more, not " + amount);
        }
        if (!registry.isManaged(npc) || !sheets.exists(npc)) {
            return SpendResult.NO_SHEET;
        }
        Optional<String> id = sheets.statId(stat);
        if (id.isEmpty()) {
            return SpendResult.UNKNOWN_STAT;
        }
        int available = available(npc, id.get());
        if (available == 0) {
            return SpendResult.NOT_ELIGIBLE;
        }
        if (available < amount) {
            return SpendResult.NOT_ENOUGH_POINTS;
        }
        OptionalInt cap = cap(npc, id.get());
        OptionalInt base = bases.base(npc, id.get());
        if (cap.isPresent() && base.isPresent() && base.getAsInt() + amount > cap.getAsInt()) {
            return SpendResult.ABOVE_CAP;
        }
        return SpendResult.DONE;
    }

    /**
     * Spends points on a stat: its base goes up by {@code amount}.
     *
     * @param player who chose it, or null for a plugin
     */
    public SpendResult spend(UUID npc, String stat, int amount, @Nullable Player player) {
        SpendResult check = check(npc, stat, amount);
        if (check != SpendResult.DONE) {
            return check;
        }
        String id = sheets.statId(stat).orElseThrow();
        if (sheets.editStat(npc, id, StatEdit.ADD, amount).isEmpty()) {
            return SpendResult.NO_SHEET;
        }
        int left = amount;
        for (Grant grant : eligibleGrants(npc, id)) {
            int draw = Math.min(grant.amount - spent(npc, grant), left);
            if (draw <= 0) {
                continue;
            }
            Map<String, Integer> byStat = spendsOf(npc).computeIfAbsent(grant.id, g -> new LinkedHashMap<>());
            int now = byStat.merge(id, draw, Integer::sum);
            store.saveSpend(new StoredSpend(npc, grant.id, id, now));
            left -= draw;
            if (left == 0) {
                break;
            }
        }
        resolved.spent(registry.get(npc).orElseThrow(), player, id, amount);
        return SpendResult.DONE;
    }

    /**
     * Gives back every spent point: each stat loses what points added to it.
     *
     * @return how many points were given back
     */
    public int respec(UUID npc) {
        return respec(npc, null);
    }

    /**
     * Gives back the spent points of one source's grants, or of every grant when
     * {@code source} is null: each stat loses what those points added to it.
     *
     * @return how many points were given back
     */
    public int respec(UUID npc, @Nullable String source) {
        Map<UUID, Map<String, Integer>> spent = spends.get(npc);
        if (spent == null || spent.isEmpty()) {
            return 0;
        }
        Set<UUID> given = source == null ? Set.copyOf(spent.keySet())
                : grants.getOrDefault(npc, List.of()).stream()
                        .filter(grant -> grant.source.equals(source)).map(grant -> grant.id)
                        .collect(Collectors.toSet());
        Map<String, Integer> byStat = new LinkedHashMap<>();
        for (UUID grant : given) {
            Map<String, Integer> stats = spent.remove(grant);
            if (stats != null) {
                stats.forEach((stat, points) -> byStat.merge(stat, points, Integer::sum));
            }
        }
        if (byStat.isEmpty()) {
            return 0;
        }
        byStat.forEach((stat, points) -> sheets.editStat(npc, stat, StatEdit.REDUCE, points));
        // The store deletes spends per NPC only: the ones that stay are saved again.
        store.deleteSpends(npc);
        if (spent.isEmpty()) {
            spends.remove(npc);
        } else {
            spent.forEach((grant, stats) -> stats.forEach((stat, points) ->
                    store.saveSpend(new StoredSpend(npc, grant, stat, points))));
        }
        return byStat.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** Drops everything of an NPC whose record was deleted. */
    public void forget(UUID npc) {
        grants.remove(npc);
        spends.remove(npc);
        store.deleteAll(npc);
    }

    /** Gives a copied NPC the same grants and spends as its original, under new ids. */
    public void copy(UUID original, UUID copy) {
        Map<UUID, Map<String, Integer>> spent = spendsOf(original);
        for (Grant grant : grants.getOrDefault(original, List.of())) {
            Grant copied = new Grant(new StoredPointGrant(UUID.randomUUID(), copy, grant.source, grant.amount,
                    grant.eligible, grant.created));
            grants.computeIfAbsent(copy, id -> new ArrayList<>()).add(copied);
            store.saveGrant(copied.stored());
            spent.getOrDefault(grant.id, Map.of()).forEach((stat, points) -> {
                spendsOf(copy).computeIfAbsent(copied.id, id -> new LinkedHashMap<>()).put(stat, points);
                store.saveSpend(new StoredSpend(copy, copied.id, stat, points));
            });
        }
    }

    private List<Grant> eligibleGrants(UUID npc, String stat) {
        Optional<NpcRecord> record = registry.get(npc);
        if (record.isEmpty()) {
            return List.of();
        }
        PointRules npcRules = rules.apply(record.get());
        return grants.getOrDefault(npc, List.of()).stream()
                .filter(grant -> eligible(grant, npcRules).contains(stat))
                .sorted(Comparator.comparingInt((Grant grant) -> eligible(grant, npcRules).size())
                        .thenComparingLong(grant -> grant.created))
                .toList();
    }

    private static Collection<String> eligible(Grant grant, PointRules rules) {
        return grant.eligible.isEmpty() ? rules.eligible() : grant.eligible;
    }

    private int spent(UUID npc, Grant grant) {
        return spendsOf(npc).getOrDefault(grant.id, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    private Map<UUID, Map<String, Integer>> spendsOf(UUID npc) {
        return spends.computeIfAbsent(npc, id -> new LinkedHashMap<>());
    }

    private PointGrant view(UUID npc, Grant grant) {
        return new PointGrant(grant.id, grant.source, grant.amount, spent(npc, grant), grant.eligible);
    }

    /** Hears about every spend. */
    @FunctionalInterface
    public interface Resolved {
        void spent(NpcRecord npc, @Nullable Player player, String stat, int amount);
    }

    private static final class Grant {
        private final UUID id;
        private final UUID npc;
        private final String source;
        private final Set<String> eligible;
        private final long created;
        private int amount;

        private Grant(StoredPointGrant stored) {
            id = stored.id();
            npc = stored.npc();
            source = stored.source();
            eligible = stored.eligible();
            created = stored.created();
            amount = stored.amount();
        }

        private StoredPointGrant stored() {
            return new StoredPointGrant(id, npc, source, amount, eligible, created);
        }
    }
}
