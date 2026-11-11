package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Changes how managed NPCs look, for the commands, the appearance menu and the API. It keeps
 * the rules that hold for everyone: only managed NPCs, a skin only on player-shaped ones, and
 * never sitting and lying at once. Who may change what is checked by the callers.
 */
public final class AppearanceService {
    /** Legacy colour codes, which Citizens also turns into colours. */
    private static final Pattern LEGACY_CODE = Pattern.compile("[&§][0-9a-fk-orx#]", Pattern.CASE_INSENSITIVE);
    /** What Minecraft allows in a player name. */
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int DEFAULT_MAX_NAME_LENGTH = 32;

    /** Why a name a player typed is refused. */
    public enum NameProblem { BLANK, TOO_LONG, FORMATTING }

    private final NpcRegistry registry;
    private final AppearanceDriver citizens;
    private final Supplier<ConfigurationSection> config;

    /**
     * @param config config.yml as it is now, read again on every check so /snpc reload applies
     */
    public AppearanceService(NpcRegistry registry, AppearanceDriver citizens, Supplier<ConfigurationSection> config) {
        this.registry = registry;
        this.citizens = citizens;
        this.config = config;
    }

    /** The longest name, in visible characters, players may give an NPC (config.yml). */
    public int maxNameLength() {
        return Math.max(1, config.get().getInt("appearance.name.max-length", DEFAULT_MAX_NAME_LENGTH));
    }

    /**
     * Whether a player may give an NPC this name: not blank, not longer than config.yml allows
     * (counting what shows, not the tags), and without colours or styles unless they may format.
     */
    public Optional<NameProblem> checkName(String name, boolean mayFormat) {
        String visible = MiniMessage.miniMessage().stripTags(name);
        if (visible.isBlank()) {
            return Optional.of(NameProblem.BLANK);
        }
        if (!mayFormat && (!visible.equals(name) || LEGACY_CODE.matcher(name).find())) {
            return Optional.of(NameProblem.FORMATTING);
        }
        if (LEGACY_CODE.matcher(visible).replaceAll("").length() > maxNameLength()) {
            return Optional.of(NameProblem.TOO_LONG);
        }
        return Optional.empty();
    }

    /** Whether {@code text} could be a Minecraft player's name. */
    public static boolean isPlayerName(String text) {
        return PLAYER_NAME.matcher(text).matches();
    }

    public String name(UUID npc) {
        return citizens.rawName(managed(npc));
    }

    public void rename(UUID npc, String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("An NPC's name cannot be blank");
        }
        citizens.rename(managed(npc), name);
    }

    public boolean hasSkin(UUID npc) {
        return citizens.hasSkin(managed(npc));
    }

    public Optional<String> skin(UUID npc) {
        return citizens.skin(managed(npc));
    }

    public void setSkin(UUID npc, String player) {
        citizens.skinFromPlayer(playerShaped(npc), player);
    }

    public CompletableFuture<Void> setSkinFromUrl(UUID npc, String url, boolean slim) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("A skin link must start with http:// or https://");
        }
        return citizens.skinFromUrl(playerShaped(npc), url, slim);
    }

    public Nameplate nameplate(UUID npc) {
        return citizens.nameplate(managed(npc));
    }

    public void setNameplate(UUID npc, Nameplate nameplate) {
        citizens.nameplate(managed(npc), nameplate);
    }

    public NpcPose pose(UUID npc) {
        return citizens.pose(managed(npc));
    }

    public void setPose(UUID npc, NpcPose pose) {
        managed(npc);
        if (pose == NpcPose.LYING && citizens.sitting(npc)) {
            citizens.sitting(npc, false);
        }
        citizens.pose(npc, pose);
    }

    public boolean sitting(UUID npc) {
        return citizens.sitting(managed(npc));
    }

    public void setSitting(UUID npc, boolean sitting) {
        managed(npc);
        if (sitting && citizens.pose(npc) == NpcPose.LYING) {
            citizens.pose(npc, NpcPose.STANDING);
        }
        citizens.sitting(npc, sitting);
    }

    public Optional<NamedTextColor> glow(UUID npc) {
        return citizens.glow(managed(npc));
    }

    public void setGlow(UUID npc, NamedTextColor colour) {
        citizens.glow(managed(npc), colour);
    }

    private UUID managed(UUID npc) {
        if (!registry.isManaged(npc)) {
            throw new IllegalArgumentException(npc + " is not a managed NPC");
        }
        return npc;
    }

    private UUID playerShaped(UUID npc) {
        if (!citizens.hasSkin(managed(npc))) {
            throw new IllegalArgumentException(npc + " is not player-shaped, so it has no skin");
        }
        return npc;
    }
}
