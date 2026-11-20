package org.skysongdev.skysongnpcs.items;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skysongdev.skysongnpcs.npc.NpcRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Which item an NPC is picked up into, before SkysongNPCs adds the NPC, its ticket and lore.
 * The first of these that is set and can be made wins:
 * <ol>
 *     <li>the NPC's own SkysongItems saved item ({@code /snpc pickupitem set}, or the API);</li>
 *     <li>its template's {@code pickup-item:};</li>
 *     <li>the blueprint it was placed from;</li>
 *     <li>config.yml's {@code pickup.skysong-item};</li>
 *     <li>config.yml's {@code pickup.item} material, so a pickup never fails for want of an item.</li>
 * </ol>
 * Saved items are looked up each time an item is made, so re-saving one with {@code /si saveitem}
 * changes every item made after. A saved item that does not exist is skipped with a warning.
 */
public final class PickupItems {
    /** Where an NPC's item comes from. */
    public enum Source { NPC, TEMPLATE, BLUEPRINT, DEFAULT, MATERIAL }

    /**
     * Where an NPC's item comes from now.
     *
     * @param source    which step of the chain
     * @param savedItem the saved item's id for NPC, TEMPLATE and DEFAULT; null for BLUEPRINT and MATERIAL
     */
    public record Choice(Source source, @Nullable String savedItem) {
    }

    /** A made item and where it came from. */
    public record Made(Choice choice, ItemStack item) {
    }

    private final NpcTickets tickets;
    private final TemplatePickupItems templates;
    private final SavedItems saved;
    private final Supplier<FileConfiguration> config;
    private final Logger logger;

    public PickupItems(NpcTickets tickets, TemplatePickupItems templates, SavedItems saved,
                       Supplier<FileConfiguration> config, Logger logger) {
        this.tickets = tickets;
        this.templates = templates;
        this.saved = saved;
        this.config = config;
        this.logger = logger;
    }

    /** Each template's pickup-item:, which {@code /snpc reload} rereads. */
    public TemplatePickupItems templates() {
        return templates;
    }

    /** The saved item the NPC has of its own; empty when it uses its template's, blueprint's or the default. */
    public Optional<String> own(UUID npc) {
        return tickets.pickupItem(npc);
    }

    /**
     * Sets the saved item the NPC's item is made from, or with null goes back to its template's,
     * blueprint's or the default. Items made before keep working and keep their look.
     *
     * @return the id as SkysongItems has it, which may differ from {@code savedItem} in case; null after clearing
     * @throws IllegalArgumentException when SkysongItems has no saved item with this id
     */
    public @Nullable String setOwn(UUID npc, @Nullable String savedItem) {
        if (savedItem == null) {
            tickets.setPickupItem(npc, null);
            return null;
        }
        String id = find(savedItem)
                .orElseThrow(() -> new IllegalArgumentException("SkysongItems has no saved item " + savedItem));
        tickets.setPickupItem(npc, id);
        return id;
    }

    /** The NPC's pickup item is copied along with the NPC. */
    public void copied(UUID original, UUID copy) {
        tickets.pickupItem(original).ifPresent(id -> tickets.setPickupItem(copy, id));
    }

    /** config.yml's pickup.skysong-item; empty when it is blank. */
    public Optional<String> defaultItem() {
        String id = config.get().getString("pickup.skysong-item", "");
        return id == null || id.isBlank() ? Optional.empty() : Optional.of(id.trim());
    }

    /** The ids of every saved item, for tab completion. */
    public List<String> savedIds() {
        return saved.ids().stream().sorted().toList();
    }

    /** Where the NPC's item would come from if it were made now. */
    public Choice choice(NpcRecord npc) {
        for (Choice candidate : candidates(npc)) {
            if (candidate.savedItem() == null || saved.ids().contains(candidate.savedItem())) {
                return candidate;
            }
        }
        return new Choice(Source.MATERIAL, null);
    }

    /** A new item for the NPC, from the first step of the chain that can make one. */
    public Made make(NpcRecord npc) {
        for (Choice candidate : candidates(npc)) {
            Optional<ItemStack> item = candidate.source() == Source.BLUEPRINT
                    ? tickets.blueprint(npc.id())
                    : saved.create(candidate.savedItem());
            if (item.isPresent() && !item.get().isEmpty()) {
                return new Made(candidate, item.get().asOne());
            }
            if (candidate.source() != Source.BLUEPRINT) {
                logger.warning("NPC " + npc.id() + "'s item should be the SkysongItems saved item "
                        + candidate.savedItem() + " (" + candidate.source().name().toLowerCase(Locale.ROOT)
                        + "), but SkysongItems has none by that id, so the next choice is used.");
            }
        }
        return new Made(new Choice(Source.MATERIAL, null), new ItemStack(fallbackMaterial()));
    }

    /** Every step of the chain that is set for this NPC, in order, before checking that it can be made. */
    private List<Choice> candidates(NpcRecord npc) {
        List<Choice> candidates = new ArrayList<>();
        tickets.pickupItem(npc.id()).ifPresent(id -> candidates.add(new Choice(Source.NPC, id)));
        npc.template().flatMap(templates::of).ifPresent(id -> candidates.add(new Choice(Source.TEMPLATE, id)));
        if (tickets.hasBlueprint(npc.id())) {
            candidates.add(new Choice(Source.BLUEPRINT, null));
        }
        defaultItem().ifPresent(id -> candidates.add(new Choice(Source.DEFAULT, id)));
        return candidates;
    }

    /** The saved item's id as SkysongItems has it: an exact match, else the only one that differs just in case. */
    private Optional<String> find(String typed) {
        String id = typed.trim();
        if (saved.ids().contains(id)) {
            return Optional.of(id);
        }
        List<String> matches = saved.ids().stream().filter(candidate -> candidate.equalsIgnoreCase(id)).toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private Material fallbackMaterial() {
        Material material = Material.matchMaterial(Objects.requireNonNullElse(
                config.get().getString("pickup.item", "ARMOR_STAND"), "ARMOR_STAND"));
        return material == null || !material.isItem() || material.isAir() ? Material.ARMOR_STAND : material;
    }
}
