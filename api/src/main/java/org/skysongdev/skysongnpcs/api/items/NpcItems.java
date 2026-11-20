package org.skysongdev.skysongnpcs.api.items;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Blueprint items: SkysongItems items with the {@code npc_blueprint} tag set to a template id.
 * Right-clicking the top of a block with one places a new NPC from that template, owned by
 * the character the player is playing, and uses up one item unless the player is in creative.
 * Staff make them with {@code /si tag set npc_blueprint <template>}; plugins make them here.
 *
 * <p>The optional {@code npc_name} tag gives the placed NPC another name than the template's.
 *
 * <p>NPC items (since 0.13.0): picking an NPC up despawns it, keeping everything it has, and
 * gives back the blueprint it was placed from with the {@code npc_id} and {@code npc_ticket}
 * tags added. Only the item with the NPC's current ticket places it again; any other copy is
 * refused. Placing it again keeps the ticket, so a copy finds the NPC already placed.
 *
 * <p>Pickup items (since 0.25.0): the item an NPC is picked up into is the first of these that
 * is set and exists: the NPC's own SkysongItems saved item ({@link #setPickupItem}), its
 * template's {@code pickup-item:}, the blueprint it was placed from, config.yml's
 * {@code pickup.skysong-item}, and last config.yml's {@code pickup.item} material. Saved items
 * are ids as {@code /si loaditem} takes them, looked up each time an item is made.
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

    // M18: NPC items

    /**
     * The NPC an NPC item holds, whether or not its ticket is still the current one.
     *
     * @param item any item, may be null or air
     * @return the NPC's Citizens UUID; empty when the item is not an NPC item
     * @since 0.13.0
     */
    @NotNull Optional<UUID> storedNpc(@Nullable ItemStack item);

    /**
     * @param item any item, may be null or air
     * @return true when the item is an NPC item whose NPC is managed and whose ticket is the
     *         NPC's current one: the one item that may place the NPC again
     * @since 0.13.0
     */
    boolean isCurrent(@Nullable ItemStack item);

    /**
     * Picks an NPC up: despawns it, keeping everything it has, and makes its item with a new
     * ticket. Checks no access and fires no {@link org.skysongdev.skysongnpcs.api.event.NpcPickupEvent};
     * the caller decides who may and hands the item over.
     *
     * @param npc a placed managed NPC's Citizens UUID
     * @return its NPC item
     * @throws IllegalArgumentException when the NPC is not managed
     * @throws IllegalStateException    when it is picked up already
     * @since 0.13.0
     */
    @NotNull ItemStack pickUp(@NotNull UUID npc);

    /**
     * Makes a new item for a picked-up NPC, with a new ticket, so every older item for it
     * stops working. What {@code /snpc reissue} does, for an item that was lost.
     *
     * @param npc a picked-up managed NPC's Citizens UUID
     * @return its new NPC item
     * @throws IllegalArgumentException when the NPC is not managed
     * @throws IllegalStateException    when it is placed, not picked up
     * @since 0.13.0
     */
    @NotNull ItemStack reissue(@NotNull UUID npc);

    // Pickup items: SkysongItems saved items

    /**
     * The SkysongItems saved item the NPC has of its own, set with {@link #setPickupItem} or
     * {@code /snpc pickupitem set}.
     *
     * @param npc a managed NPC's Citizens UUID
     * @return the saved item's id; empty when it uses its template's, blueprint's or the default
     * @throws IllegalArgumentException when the NPC is not managed
     * @since 0.25.0
     */
    @NotNull Optional<String> pickupItem(@NotNull UUID npc);

    /**
     * Sets the SkysongItems saved item the NPC is picked up into from now on. Items made before
     * keep working and keep their look; {@link #reissue} remakes a picked-up NPC's item.
     *
     * @param npc       a managed NPC's Citizens UUID
     * @param savedItem a saved item's id as {@code /si loaditem} takes it (case does not matter
     *                  when only one id matches), or null to go back to its template's,
     *                  blueprint's or the default
     * @return the id as SkysongItems has it; null after clearing
     * @throws IllegalArgumentException when the NPC is not managed or SkysongItems has no such saved item
     * @since 0.25.0
     */
    @Nullable String setPickupItem(@NotNull UUID npc, @Nullable String savedItem);

    /**
     * The SkysongItems saved item the NPC's item would be made from now, after its own, its
     * template's, its blueprint and the default are tried in that order, skipping saved items
     * that do not exist.
     *
     * @param npc a managed NPC's Citizens UUID
     * @return the saved item's id; empty when the item would be its blueprint or the fallback material
     * @throws IllegalArgumentException when the NPC is not managed
     * @since 0.25.0
     */
    @NotNull Optional<String> effectivePickupItem(@NotNull UUID npc);

    /**
     * @return config.yml's {@code pickup.skysong-item}; empty when it is blank
     * @since 0.25.0
     */
    @NotNull Optional<String> defaultPickupItem();
}
