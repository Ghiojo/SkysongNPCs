package org.skysongdev.skysongnpcs.apiimpl;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcAppearance;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.appearance.AppearanceService;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** {@link NpcAppearance} over the plugin's {@link AppearanceService}. */
final class NpcAppearanceImpl implements NpcAppearance {
    private final AppearanceService appearance;

    NpcAppearanceImpl(AppearanceService appearance) {
        this.appearance = appearance;
    }

    @Override
    public @NotNull String name(@NotNull UUID npc) {
        return appearance.name(checked(npc));
    }

    @Override
    public void rename(@NotNull UUID npc, @NotNull String name) {
        appearance.rename(checked(npc), Objects.requireNonNull(name, "name"));
    }

    @Override
    public boolean hasSkin(@NotNull UUID npc) {
        return appearance.hasSkin(checked(npc));
    }

    @Override
    public @NotNull Optional<String> skin(@NotNull UUID npc) {
        return appearance.skin(checked(npc));
    }

    @Override
    public void setSkin(@NotNull UUID npc, @NotNull String player) {
        appearance.setSkin(checked(npc), Objects.requireNonNull(player, "player"));
    }

    @Override
    public @NotNull CompletableFuture<Void> setSkinFromUrl(@NotNull UUID npc, @NotNull String url, boolean slim) {
        return appearance.setSkinFromUrl(checked(npc), Objects.requireNonNull(url, "url"), slim);
    }

    @Override
    public @NotNull Nameplate nameplate(@NotNull UUID npc) {
        return appearance.nameplate(checked(npc));
    }

    @Override
    public void setNameplate(@NotNull UUID npc, @NotNull Nameplate nameplate) {
        appearance.setNameplate(checked(npc), Objects.requireNonNull(nameplate, "nameplate"));
    }

    @Override
    public @NotNull NpcPose pose(@NotNull UUID npc) {
        return appearance.pose(checked(npc));
    }

    @Override
    public void setPose(@NotNull UUID npc, @NotNull NpcPose pose) {
        appearance.setPose(checked(npc), Objects.requireNonNull(pose, "pose"));
    }

    @Override
    public boolean sitting(@NotNull UUID npc) {
        return appearance.sitting(checked(npc));
    }

    @Override
    public void setSitting(@NotNull UUID npc, boolean sitting) {
        appearance.setSitting(checked(npc), sitting);
    }

    @Override
    public @NotNull Optional<NamedTextColor> glow(@NotNull UUID npc) {
        return appearance.glow(checked(npc));
    }

    @Override
    public void setGlow(@NotNull UUID npc, @Nullable NamedTextColor colour) {
        appearance.setGlow(checked(npc), colour);
    }

    private static UUID checked(UUID npc) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
        return Objects.requireNonNull(npc, "npc");
    }
}
