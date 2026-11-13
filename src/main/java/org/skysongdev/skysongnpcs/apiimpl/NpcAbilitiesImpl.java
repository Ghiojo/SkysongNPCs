package org.skysongdev.skysongnpcs.apiimpl;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.abilities.AbilitiesMenu;
import org.skysongdev.skysongnpcs.abilities.AbilityChoiceService;
import org.skysongdev.skysongnpcs.abilities.AbilityDefinition;
import org.skysongdev.skysongnpcs.abilities.AbilityService;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilities;
import org.skysongdev.skysongnpcs.api.ability.NpcAbility;
import org.skysongdev.skysongnpcs.api.ability.AbilityChoiceResult;
import org.skysongdev.skysongnpcs.api.ability.NpcAbilityChoice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** {@link NpcAbilities} over the plugin's {@link AbilityService}. */
final class NpcAbilitiesImpl implements NpcAbilities {
    private final AbilityService abilities;
    private final AbilitiesMenu menu;
    private final AbilityChoiceService choices;

    NpcAbilitiesImpl(AbilityService abilities, AbilitiesMenu menu, AbilityChoiceService choices) {
        this.abilities = abilities;
        this.menu = menu;
        this.choices = choices;
    }

    @Override
    public @NotNull List<NpcAbility> defined() {
        mainThread();
        return abilities.definitions().all().stream().map(AbilityDefinition::view).toList();
    }

    @Override
    public @NotNull Optional<NpcAbility> find(@NotNull String ability) {
        mainThread();
        return abilities.definitions().get(Objects.requireNonNull(ability, "ability")).map(AbilityDefinition::view);
    }

    @Override
    public @NotNull List<NpcAbility> of(@NotNull UUID npc) {
        mainThread();
        return abilities.of(Objects.requireNonNull(npc, "npc")).stream().map(AbilityDefinition::view).toList();
    }

    @Override
    public boolean has(@NotNull UUID npc, @NotNull String ability) {
        mainThread();
        return abilities.has(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(ability, "ability"));
    }

    @Override
    public boolean grant(@NotNull UUID npc, @NotNull String ability) {
        mainThread();
        return grant(npc, ability, STAFF);
    }

    @Override
    public boolean grant(@NotNull UUID npc, @NotNull String ability, @NotNull String source) {
        mainThread();
        return abilities.grant(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(ability, "ability"),
                Objects.requireNonNull(source, "source"));
    }

    @Override
    public boolean revoke(@NotNull UUID npc, @NotNull String ability, @NotNull String source) {
        mainThread();
        return abilities.revoke(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(ability, "ability"),
                Objects.requireNonNull(source, "source"));
    }

    @Override
    public @NotNull Set<String> sources(@NotNull UUID npc, @NotNull String ability) {
        mainThread();
        return abilities.sources(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(ability, "ability"));
    }

    @Override
    public boolean revoke(@NotNull UUID npc, @NotNull String ability) {
        mainThread();
        return abilities.revoke(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(ability, "ability"));
    }

    @Override
    public @NotNull NpcAbilityChoice grantChoice(@NotNull UUID npc, @NotNull String source,
                                                 @NotNull List<String> options, int count) {
        mainThread();
        return choices.toApi(choices.grant(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"),
                List.copyOf(options), count));
    }

    @Override
    public @NotNull List<NpcAbilityChoice> choices(@NotNull UUID npc) {
        mainThread();
        return choices.grants(Objects.requireNonNull(npc, "npc")).stream().map(choices::toApi).toList();
    }

    @Override
    public @NotNull AbilityChoiceResult choose(@NotNull UUID choice, @NotNull List<String> picked) {
        mainThread();
        return choices.choose(null, Objects.requireNonNull(choice, "choice"), List.copyOf(picked));
    }

    @Override
    public int takeUnspentChoices(@NotNull UUID npc, @NotNull String source) {
        mainThread();
        return choices.removeUnspent(Objects.requireNonNull(npc, "npc"), Objects.requireNonNull(source, "source"),
                Integer.MAX_VALUE);
    }

    @Override
    public boolean open(@NotNull Player viewer, @NotNull UUID npc) {
        mainThread();
        return menu.open(Objects.requireNonNull(viewer, "viewer"), Objects.requireNonNull(npc, "npc"));
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }
}
