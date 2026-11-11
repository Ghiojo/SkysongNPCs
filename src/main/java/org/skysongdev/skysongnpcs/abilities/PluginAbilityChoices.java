package org.skysongdev.skysongnpcs.abilities;

import org.skysongdev.skysongnpcs.choices.StatPoints;
import org.skysongdev.skysongnpcs.picks.ProficiencyPicks;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@link AbilityChoices} through M14's {@link StatPoints} and M15's {@link ProficiencyPicks}.
 * They are looked up on every call, since the plugin makes them after the abilities.
 */
public final class PluginAbilityChoices implements AbilityChoices {
    private final Supplier<StatPoints> points;
    private final Supplier<ProficiencyPicks> picks;

    public PluginAbilityChoices(Supplier<StatPoints> points, Supplier<ProficiencyPicks> picks) {
        this.points = points;
        this.picks = picks;
    }

    @Override
    public void grantPoints(UUID npc, String source, int amount, List<String> eligible) {
        points.get().grant(npc, source, amount, eligible);
    }

    @Override
    public int takeUnspentPoints(UUID npc, String source, int max) {
        return points.get().take(npc, source, max);
    }

    @Override
    public void grantPicks(UUID npc, String source, String skill, String tier, int count) {
        picks.get().grant(npc, source, skill, tier, count);
    }

    @Override
    public int takeUnspentPicks(UUID npc, String source, int max) {
        return picks.get().removeUnspent(npc, source, max);
    }
}
