package org.skysongdev.skysongnpcs.appearance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.access.AccessService;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.appearance.Nameplate;
import org.skysongdev.skysongnpcs.api.appearance.NpcPose;
import org.skysongdev.skysongnpcs.api.menu.NpcMenu;
import org.skysongdev.skysongnpcs.config.Messages;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The one-row Appearance menu, opened from the NPC menu's Appearance button or with
 * {@code /snpc appearance}. Each button shows only to players with its action: name
 * ({@code rename}), skin ({@code skin}, player-shaped NPCs only), and nameplate, pose,
 * sitting and glowing ({@code appearance}). Name and skin need typing, so they close the menu
 * and offer the command in chat; the others change with a click. Slots and items can be moved
 * in menus.yml ({@code appearance-menu}).
 */
public final class AppearanceMenu implements Listener {
    /** The buttons, in their default slots. */
    enum Button {
        BACK(0, Material.ARROW, null),
        NAME(2, Material.NAME_TAG, NpcAction.RENAME),
        SKIN(3, Material.PLAYER_HEAD, NpcAction.SKIN),
        NAMEPLATE(4, Material.OAK_SIGN, NpcAction.APPEARANCE),
        POSE(5, Material.ARMOR_STAND, NpcAction.APPEARANCE),
        SITTING(6, Material.OAK_STAIRS, NpcAction.APPEARANCE),
        GLOW(7, Material.GLOWSTONE_DUST, NpcAction.APPEARANCE);

        final int slot;
        final Material material;
        final NpcAction action;

        Button(int slot, Material material, NpcAction action) {
            this.slot = slot;
            this.material = material;
            this.action = action;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final List<NamedTextColor> COLOURS = List.copyOf(NamedTextColor.NAMES.values());

    private final Plugin plugin;
    private final NpcRegistry registry;
    private final NpcLookup names;
    private final AccessService access;
    private final AppearanceService appearance;
    private final PluginConfiguration configuration;
    private final NpcMenu npcMenu;

    public AppearanceMenu(Plugin plugin, NpcRegistry registry, NpcLookup names, AccessService access,
                          AppearanceService appearance, PluginConfiguration configuration, NpcMenu npcMenu) {
        this.plugin = plugin;
        this.registry = registry;
        this.names = names;
        this.access = access;
        this.appearance = appearance;
        this.configuration = configuration;
        this.npcMenu = npcMenu;
    }

    /** Whether the player may change anything about the NPC's looks, so the menu has something for them. */
    public boolean mayUse(Player player, NpcRecord npc) {
        return access.may(player, npc, NpcAction.RENAME) || access.may(player, npc, NpcAction.SKIN)
                || access.may(player, npc, NpcAction.APPEARANCE);
    }

    /**
     * Opens the menu for the player.
     *
     * @return false, opening nothing, when the NPC is not managed or the player may change nothing
     */
    public boolean open(Player player, UUID npc) {
        Optional<NpcRecord> record = registry.get(npc);
        if (record.isEmpty() || !mayUse(player, record.get())) {
            return false;
        }
        Holder holder = new Holder(player, npc);
        Component title = messages().get("appearance.menu.title", Map.of("npc", names.name(npc)));
        holder.inventory = Bukkit.createInventory(holder, 9, title);
        draw(holder, record.get());
        player.openInventory(holder.inventory);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getInventory()) {
            click(holder, holder.buttons.get(event.getSlot()), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void click(Holder holder, Button button, ClickType type) {
        Optional<NpcRecord> npc = registry.get(holder.npc);
        if (button == null || npc.isEmpty() || !visible(button, holder.viewer, npc.get())) {
            return;
        }
        UUID id = holder.npc;
        Map<String, String> placeholders = Map.of("npc", names.name(id));
        switch (button) {
            case BACK -> later(holder, () -> npcMenu.open(holder.viewer, id));
            case NAME -> later(holder, () -> messages().send(holder.viewer, "appearance.menu.name.prompt", placeholders));
            case SKIN -> later(holder, () -> messages().send(holder.viewer, "appearance.menu.skin.prompt", placeholders));
            case NAMEPLATE -> appearance.setNameplate(id, next(appearance.nameplate(id), Nameplate.values()));
            case POSE -> appearance.setPose(id, next(appearance.pose(id), NpcPose.values()));
            case SITTING -> appearance.setSitting(id, !appearance.sitting(id));
            case GLOW -> appearance.setGlow(id, nextGlow(appearance.glow(id), type));
        }
        if (holder.viewer.getOpenInventory().getTopInventory() == holder.inventory) {
            draw(holder, npc.get());
        }
    }

    /** Left-click turns glowing on (white) or off; right-click picks the next colour. */
    private static NamedTextColor nextGlow(Optional<NamedTextColor> now, ClickType type) {
        if (type.isRightClick()) {
            return now.map(colour -> COLOURS.get((COLOURS.indexOf(colour) + 1) % COLOURS.size()))
                    .orElse(NamedTextColor.WHITE);
        }
        return now.isPresent() ? null : NamedTextColor.WHITE;
    }

    private static <T> T next(T now, T[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == now) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private void draw(Holder holder, NpcRecord npc) {
        holder.inventory.clear();
        holder.buttons.clear();
        Map<String, String> placeholders = placeholders(npc.id());
        for (Button button : Button.values()) {
            if (!visible(button, holder.viewer, npc)) {
                continue;
            }
            int slot = settings().getInt(button.key() + ".slot", button.slot);
            if (slot < 0 || slot >= 9 || holder.buttons.containsKey(slot)) {
                slot = holder.inventory.firstEmpty();
                if (slot < 0) {
                    continue;
                }
            }
            holder.inventory.setItem(slot, icon(button, placeholders));
            holder.buttons.put(slot, button);
        }
    }

    private boolean visible(Button button, Player viewer, NpcRecord npc) {
        if (button.action == null) {
            return true;
        }
        if (button == Button.SKIN && !appearance.hasSkin(npc.id())) {
            return false;
        }
        return access.may(viewer, npc, button.action);
    }

    private ItemStack icon(Button button, Map<String, String> placeholders) {
        Material material = Optional.ofNullable(settings().getString(button.key() + ".material"))
                .map(name -> Material.matchMaterial(name))
                .filter(Material::isItem)
                .orElse(button.material);
        String key = "appearance.menu." + button.key();
        ItemStack icon = new ItemStack(material);
        icon.editMeta(meta -> {
            meta.displayName(itemText(messages().get(key + ".name", placeholders)));
            meta.lore(messages().lines(key + ".lore", placeholders).stream().map(AppearanceMenu::itemText).toList());
        });
        return icon;
    }

    /** The current looks, as the lore of the buttons writes them. */
    private Map<String, String> placeholders(UUID npc) {
        Messages messages = messages();
        Map<String, String> values = new HashMap<>();
        values.put("npc", names.name(npc));
        values.put("skin", appearance.hasSkin(npc)
                ? appearance.skin(npc).orElseGet(() -> messages.plain("appearance.no-skin", Map.of()))
                : messages.plain("appearance.no-skin", Map.of()));
        values.put("nameplate", words(messages, appearance.nameplate(npc)));
        values.put("pose", words(messages, appearance.pose(npc)));
        values.put("sitting", messages.plain("appearance.sitting." + (appearance.sitting(npc) ? "yes" : "no"), Map.of()));
        values.put("glow", glowWord(messages, appearance.glow(npc).orElse(null)));
        return values;
    }

    static String words(Messages messages, Nameplate nameplate) {
        return messages.plain("appearance.nameplates." + nameplate.key(), Map.of());
    }

    static String words(Messages messages, NpcPose pose) {
        return messages.plain("appearance.poses." + pose.key(), Map.of());
    }

    static String glowWord(Messages messages, NamedTextColor colour) {
        return colour == null ? messages.plain("appearance.glow.none", Map.of())
                : NamedTextColor.NAMES.key(colour).replace('_', ' ');
    }

    /** Runs after the menu closes: opening or sending inside a click handler confuses the client. */
    private void later(Holder holder, Runnable then) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (holder.viewer.getOpenInventory().getTopInventory() == holder.inventory) {
                holder.viewer.closeInventory();
            }
            then.run();
        });
    }

    private ConfigurationSection settings() {
        ConfigurationSection section = configuration.menus().getConfigurationSection("appearance-menu.buttons");
        return section != null ? section : configuration.menus().createSection("appearance-menu.buttons");
    }

    private Messages messages() {
        return configuration.messages();
    }

    private static Component itemText(Component text) {
        return text.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Marks an inventory as an open Appearance menu. */
    static final class Holder implements InventoryHolder {
        private final Player viewer;
        private final UUID npc;
        private final Map<Integer, Button> buttons = new HashMap<>();
        private Inventory inventory;

        private Holder(Player viewer, UUID npc) {
            this.viewer = viewer;
            this.npc = npc;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
