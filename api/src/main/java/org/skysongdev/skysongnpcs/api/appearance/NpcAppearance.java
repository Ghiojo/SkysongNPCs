package org.skysongdev.skysongnpcs.api.appearance;

import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * How managed NPCs look: name, skin, nameplate, pose, sitting and glowing. Players change
 * these with {@code /snpc name}, {@code skin}, {@code nameplate}, {@code pose}, {@code sit} and
 * {@code glow}, or from the menu, when they have the {@code rename}, {@code skin} or
 * {@code appearance} action. The API does not check access or the name rules in config.yml:
 * the calling plugin decides.
 *
 * <p>Every method takes the NPC's Citizens UUID and throws {@link IllegalArgumentException}
 * when it is not a managed NPC. Must be called on the server's main thread.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.12.0
 */
@ApiStatus.NonExtendable
public interface NpcAppearance {

    /**
     * @param npc the NPC
     * @return its name as stored in Citizens, with any MiniMessage or colour codes it holds
     */
    @NotNull String name(@NotNull UUID npc);

    /**
     * Renames the NPC. The name may hold MiniMessage tags, which Citizens shows.
     *
     * @param npc  the NPC
     * @param name the new name
     * @throws IllegalArgumentException also when the name is blank
     */
    void rename(@NotNull UUID npc, @NotNull String name);

    /**
     * @param npc the NPC
     * @return whether it is player-shaped, the only kind of NPC that has a skin
     */
    boolean hasSkin(@NotNull UUID npc);

    /**
     * @param npc the NPC
     * @return the player whose skin it wears, empty when it wears none or one from a link
     */
    @NotNull Optional<String> skin(@NotNull UUID npc);

    /**
     * Dresses the NPC in a player's skin, which Citizens fetches and keeps.
     *
     * @param npc    the NPC
     * @param player a Minecraft player name
     * @throws IllegalArgumentException also when the NPC is not player-shaped
     */
    void setSkin(@NotNull UUID npc, @NotNull String player);

    /**
     * Dresses the NPC in the skin image at a link, which Citizens uploads to MineSkin. This
     * takes a few seconds, off the main thread.
     *
     * @param npc  the NPC
     * @param url  an http or https link to a 64x64 skin PNG
     * @param slim whether it is a slim (Alex-shaped) skin
     * @return completes on the main thread once the NPC wears it, or exceptionally when the
     *         link gave no skin
     * @throws IllegalArgumentException also when the NPC is not player-shaped
     */
    @NotNull CompletableFuture<Void> setSkinFromUrl(@NotNull UUID npc, @NotNull String url, boolean slim);

    /**
     * @param npc the NPC
     * @return whether its name shows above its head
     */
    @NotNull Nameplate nameplate(@NotNull UUID npc);

    /**
     * @param npc       the NPC
     * @param nameplate whether its name shows above its head
     */
    void setNameplate(@NotNull UUID npc, @NotNull Nameplate nameplate);

    /**
     * @param npc the NPC
     * @return how it holds its body
     */
    @NotNull NpcPose pose(@NotNull UUID npc);

    /**
     * Changes the NPC's pose; {@link NpcPose#LYING} makes a sitting NPC get up first.
     *
     * @param npc  the NPC
     * @param pose the new pose
     */
    void setPose(@NotNull UUID npc, @NotNull NpcPose pose);

    /**
     * @param npc the NPC
     * @return whether it is sitting
     */
    boolean sitting(@NotNull UUID npc);

    /**
     * Makes the NPC sit where it is, or get up. Sitting down makes a lying NPC stand first.
     *
     * @param npc     the NPC
     * @param sitting true to sit
     */
    void setSitting(@NotNull UUID npc, boolean sitting);

    /**
     * @param npc the NPC
     * @return the colour it glows in, empty when it does not glow
     */
    @NotNull Optional<NamedTextColor> glow(@NotNull UUID npc);

    /**
     * Makes the NPC glow in a colour, or stop glowing.
     *
     * @param npc    the NPC
     * @param colour the colour, or null to stop glowing
     */
    void setGlow(@NotNull UUID npc, @Nullable NamedTextColor colour);
}
