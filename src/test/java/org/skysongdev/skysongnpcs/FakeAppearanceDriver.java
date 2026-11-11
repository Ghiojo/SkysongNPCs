package org.skysongdev.skysongnpcs;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.appearance.AppearanceDriver;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Citizens' looks for tests: every NPC's name, skin, nameplate, pose, sitting and glow, kept in maps. */
public final class FakeAppearanceDriver implements AppearanceDriver {
    /** A skin link the fake refuses, as MineSkin would a broken one. */
    public static final String BROKEN_URL = "https://example.com/broken.png";

    private final Map<UUID, String> names = new HashMap<>();
    private final Set<UUID> notPlayerShaped = new HashSet<>();
    private final Map<UUID, String> skins = new HashMap<>();
    private final Map<UUID, Nameplate> nameplates = new HashMap<>();
    private final Map<UUID, NpcPose> poses = new HashMap<>();
    private final Set<UUID> sitting = new HashSet<>();
    private final Map<UUID, NamedTextColor> glows = new HashMap<>();
    private final Map<UUID, Boolean> slim = new HashMap<>();
    private UUID commandTarget;

    /** The NPC is not player-shaped (a wolf, say), so it has no skin. */
    public void notPlayerShaped(UUID npc) {
        notPlayerShaped.add(npc);
    }

    /** Citizens' commands act on this NPC from now on; null for none. */
    public void commandTarget(UUID npc) {
        commandTarget = npc;
    }

    /** Whether the last skin from a link was slim. */
    public boolean slim(UUID npc) {
        return slim.getOrDefault(npc, false);
    }

    @Override
    public String rawName(UUID npc) {
        return names.getOrDefault(npc, "NPC");
    }

    @Override
    public void rename(UUID npc, String name) {
        names.put(npc, name);
    }

    @Override
    public boolean hasSkin(UUID npc) {
        return !notPlayerShaped.contains(npc);
    }

    @Override
    public Optional<String> skin(UUID npc) {
        return Optional.ofNullable(skins.get(npc));
    }

    @Override
    public void skinFromPlayer(UUID npc, String player) {
        skins.put(npc, player);
    }

    @Override
    public CompletableFuture<Void> skinFromUrl(UUID npc, String url, boolean slimSkin) {
        if (url.equals(BROKEN_URL)) {
            return CompletableFuture.failedFuture(new IllegalStateException("MineSkin gave no skin for that link"));
        }
        skins.remove(npc);
        slim.put(npc, slimSkin);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Nameplate nameplate(UUID npc) {
        return nameplates.getOrDefault(npc, Nameplate.SHOWN);
    }

    @Override
    public void nameplate(UUID npc, Nameplate nameplate) {
        nameplates.put(npc, nameplate);
    }

    @Override
    public NpcPose pose(UUID npc) {
        return poses.getOrDefault(npc, NpcPose.STANDING);
    }

    @Override
    public void pose(UUID npc, NpcPose pose) {
        poses.put(npc, pose);
    }

    @Override
    public boolean sitting(UUID npc) {
        return sitting.contains(npc);
    }

    @Override
    public void sitting(UUID npc, boolean sits) {
        if (sits) {
            sitting.add(npc);
        } else {
            sitting.remove(npc);
        }
    }

    @Override
    public Optional<NamedTextColor> glow(UUID npc) {
        return Optional.ofNullable(glows.get(npc));
    }

    @Override
    public void glow(UUID npc, NamedTextColor colour) {
        if (colour == null) {
            glows.remove(npc);
        } else {
            glows.put(npc, colour);
        }
    }

    @Override
    public Optional<UUID> commandTarget(CommandSender sender, String[] args) {
        return Optional.ofNullable(commandTarget);
    }
}
