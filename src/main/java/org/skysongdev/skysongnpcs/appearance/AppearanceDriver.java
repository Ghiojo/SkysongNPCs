package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * What the appearance controls ask Citizens to do, with no rules of their own.
 * {@link org.skysongdev.skysongnpcs.citizens.CitizensAppearance} is the real one; tests use
 * their own, since Citizens is not on the test classpath. Every NPC passed in exists in Citizens.
 */
public interface AppearanceDriver {

    /** The name as stored, with its formatting. */
    String rawName(UUID npc);

    void rename(UUID npc, String name);

    /** Whether the NPC is player-shaped, so it has a skin. */
    boolean hasSkin(UUID npc);

    /** The player whose skin it wears, if any. */
    Optional<String> skin(UUID npc);

    void skinFromPlayer(UUID npc, String player);

    /** Uploads the image at the link and dresses the NPC in it; completes on the main thread. */
    CompletableFuture<Void> skinFromUrl(UUID npc, String url, boolean slim);

    Nameplate nameplate(UUID npc);

    void nameplate(UUID npc, Nameplate nameplate);

    /** Standing, crouching or lying; sitting is apart. */
    NpcPose pose(UUID npc);

    void pose(UUID npc, NpcPose pose);

    boolean sitting(UUID npc);

    void sitting(UUID npc, boolean sitting);

    Optional<NamedTextColor> glow(UUID npc);

    /** Glows in the colour, or stops glowing with null. */
    void glow(UUID npc, NamedTextColor colour);

    /**
     * The NPC one of Citizens' own commands would act on: the one its {@code --id} flag names,
     * else the one the sender selected.
     *
     * @param args the words after the command's label
     */
    Optional<UUID> commandTarget(CommandSender sender, String[] args);
}
