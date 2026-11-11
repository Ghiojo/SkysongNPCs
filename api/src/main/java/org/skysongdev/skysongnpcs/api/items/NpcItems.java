package org.skysongdev.skysongnpcs.api.items;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Blueprint items: SkysongItems items with the {@code npc_blueprint} tag set to a template id.
 * Right-clicking the top of a block with one places a new NPC from that template, owned by
 * the character the player is playing, and uses up one item unless the player is in creative.
 * Staff make them with {@code /si tag set npc_blueprint <template>}; plugins make them here.
 *
 * <p>The optional {@code npc_name} tag gives the placed NPC another name than the template's.
 *
 * <p>Must be called on the server's main thread.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 *
 * @since 0.6.0
 */
@ApiStatus.NonExtendable
public interface NpcItems {

    /**
     * @param item any item, may be null or air
     * @return true when the item is a blueprint, whether or not its template still exists
     */
    boolean isBlueprint(@Nullable ItemStack item);

    /**
     * The template a blueprint places.
     *
     * @param item any item, may be null or air
     * @return the template's id, lowercased; empty when the item is not a blueprint
     */
    @NotNull Optional<String> blueprintTemplate(@Nullable ItemStack item);

    /**
     * Makes a blueprint out of a copy of an item. The item itself is left as it was.
     *
     * @param base     the item to copy, normally a SkysongItems item made from a template
     * @param template the template the blueprint places, as in templates/&lt;id&gt;.yml
     * @param name     the placed NPC's name, or null for the template's
     * @return the blueprint
     * @throws IllegalArgumentException when the item is air or there is no such template
     */
    @NotNull ItemStack makeBlueprint(@NotNull ItemStack base, @NotNull String template, @Nullable String name);
}
