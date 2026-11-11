package org.skysongdev.skysongnpcs;

import org.skysongdev.skysongnpcs.abilities.AbilityChoices;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pending choices for tests: per NPC and source, how many points and picks are unspent and spent. */
public final class FakeAbilityChoices implements AbilityChoices {
    private final Map<String, Integer> unspentPoints = new HashMap<>();
    private final Map<String, Integer> spentPoints = new HashMap<>();
    private final Map<String, List<String>> eligible = new HashMap<>();
    private final Map<String, Integer> unspentPicks = new HashMap<>();
    private final Map<String, String> pickKinds = new HashMap<>();

    @Override
    public void grantPoints(UUID npc, String source, int amount, List<String> stats) {
        unspentPoints.merge(key(npc, source), amount, Integer::sum);
        eligible.put(key(npc, source), List.copyOf(stats));
    }

    @Override
    public int takeUnspentPoints(UUID npc, String source, int max) {
        return take(unspentPoints, key(npc, source), max);
    }

    @Override
    public void grantPicks(UUID npc, String source, String skill, String tier, int count) {
        unspentPicks.merge(key(npc, source), count, Integer::sum);
        pickKinds.put(key(npc, source), skill + "/" + tier);
    }

    @Override
    public int takeUnspentPicks(UUID npc, String source, int max) {
        return take(unspentPicks, key(npc, source), max);
    }

    /** Spends points of a source, as a player would. */
    public void spendPoints(UUID npc, String source, int amount) {
        int taken = take(unspentPoints, key(npc, source), amount);
        spentPoints.merge(key(npc, source), taken, Integer::sum);
    }

    public int unspentPoints(UUID npc, String source) {
        return unspentPoints.getOrDefault(key(npc, source), 0);
    }

    public int spentPoints(UUID npc, String source) {
        return spentPoints.getOrDefault(key(npc, source), 0);
    }

    public List<String> eligible(UUID npc, String source) {
        return eligible.getOrDefault(key(npc, source), List.of());
    }

    public int unspentPicks(UUID npc, String source) {
        return unspentPicks.getOrDefault(key(npc, source), 0);
    }

    public String pickKind(UUID npc, String source) {
        return pickKinds.get(key(npc, source));
    }

    private static int take(Map<String, Integer> counts, String key, int max) {
        int have = counts.getOrDefault(key, 0);
        int taken = Math.min(have, max);
        counts.put(key, have - taken);
        return taken;
    }

    private static String key(UUID npc, String source) {
        return npc + "|" + source;
    }
}
