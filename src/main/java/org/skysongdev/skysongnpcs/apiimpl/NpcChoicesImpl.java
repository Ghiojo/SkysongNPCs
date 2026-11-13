package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.choices.NpcChoices;
import org.skysongdev.skysongnpcs.api.choices.PointGrant;
import org.skysongdev.skysongnpcs.api.choices.SpendResult;
import org.skysongdev.skysongnpcs.api.npc.NpcState;
import org.skysongdev.skysongnpcs.choices.ChoiceMenu;
import org.skysongdev.skysongnpcs.choices.StatPoints;
import org.skysongdev.skysongnpcs.picks.ProficiencyPicks;
import org.skysongdev.skysongnpcs.api.choices.PickResult;
import org.skysongdev.skysongnpcs.api.choices.ProficiencyPickGrant;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** {@link NpcChoices} over the plugin's {@link StatPoints}, {@link ProficiencyPicks} and {@link ChoiceMenu}. */
final class NpcChoicesImpl implements NpcChoices {
    private final NpcRegistry registry;
    private final StatPoints points;
    private final ChoiceMenu menu;
    private final ProficiencyPicks picks;

    NpcChoicesImpl(NpcRegistry registry, StatPoints points, ChoiceMenu menu, ProficiencyPicks picks) {
        this.registry = registry;
        this.points = points;
        this.menu = menu;
        this.picks = picks;
    }

    @Override
    public @NotNull PointGrant grantPoints(@NotNull UUID npc, @NotNull String source, int amount,
                                           @NotNull Collection<String> eligible) {
        mainThread();
        return points.grant(Objects.requireNonNull(npc, "npc"), source, amount, Objects.requireNonNull(eligible, "eligible"));
    }

    @Override
    public int takeUnspentPoints(@NotNull UUID npc, @NotNull String source, int max) {
        mainThread();
        return points.take(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"), max);
    }

    @Override
    public @NotNull List<PointGrant> pointGrants(@NotNull UUID npc) {
        mainThread();
        return points.grants(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public int unspentPoints(@NotNull UUID npc) {
        mainThread();
        return points.unspent(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public int unspentPoints(@NotNull UUID npc, @NotNull String source) {
        mainThread();
        return points.unspent(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"));
    }

    @Override
    public @NotNull SpendResult spendPoints(@NotNull UUID npc, @NotNull String stat, int amount) {
        mainThread();
        return points.spend(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(stat, "stat"), amount, null);
    }

    @Override
    public int respec(@NotNull UUID npc) {
        mainThread();
        return menu.respec(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public int respec(@NotNull UUID npc, @NotNull String source) {
        mainThread();
        return menu.respec(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"));
    }

    @Override
    public boolean inSetup(@NotNull UUID npc) {
        mainThread();
        return registry.get(Objects.requireNonNull(npc, "npc")).map(record -> record.state() == NpcState.SETUP).orElse(false);
    }

    @Override
    public boolean open(@NotNull Player player, @NotNull UUID npc) {
        mainThread();
        return menu.open(Objects.requireNonNull(player, "player"), Objects.requireNonNull(npc, "npc"));
    }

    // M15: proficiency picks

    @Override
    public @NotNull ProficiencyPickGrant grantPicks(@NotNull UUID npc, @NotNull String source, @NotNull String skill,
                                                   @NotNull String tier, int count) {
        mainThread();
        return picks.toApi(picks.grant(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(skill, "skill"), Objects.requireNonNull(tier, "tier"), count));
    }

    @Override
    public @NotNull ProficiencyPickGrant grantPicks(@NotNull UUID npc, @NotNull String source, @NotNull String skill,
                                                   @NotNull String tier, int count, @NotNull String promoteTo,
                                                   int promotionCost) {
        mainThread();
        return picks.toApi(picks.grant(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(skill, "skill"), Objects.requireNonNull(tier, "tier"), count,
                Objects.requireNonNull(promoteTo, "promoteTo"), promotionCost));
    }

    @Override
    public @NotNull PickResult promote(@NotNull UUID npc, @NotNull String proficiency) {
        mainThread();
        return picks.promoteAny(null, Objects.requireNonNull(npc, "npc"),
                Objects.requireNonNull(proficiency, "proficiency"));
    }

    @Override
    public int takeUnspentPicks(@NotNull UUID npc, @NotNull String source, int max) {
        mainThread();
        return picks.removeUnspent(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"), max);
    }

    @Override
    public @NotNull List<ProficiencyPickGrant> pickGrants(@NotNull UUID npc) {
        mainThread();
        return picks.grants(Objects.requireNonNull(npc, "npc")).stream().map(picks::toApi).toList();
    }

    @Override
    public int unspentPicks(@NotNull UUID npc) {
        mainThread();
        return picks.pending(Objects.requireNonNull(npc, "npc"));
    }

    @Override
    public int unspentPicks(@NotNull UUID npc, @NotNull String source) {
        mainThread();
        return picks.unspent(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"));
    }

    @Override
    public @NotNull PickResult spendPick(@NotNull UUID npc, @NotNull String proficiency) {
        mainThread();
        return picks.pickAny(null, Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(proficiency, "proficiency"));
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
