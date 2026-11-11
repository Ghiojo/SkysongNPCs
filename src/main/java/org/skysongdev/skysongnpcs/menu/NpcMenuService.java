package org.skysongdev.skysongnpcs.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.menu.MenuClick;
import org.skysongdev.skysongnpcs.api.menu.MenuContext;
import org.skysongdev.skysongnpcs.api.menu.NpcMenu;
import org.skysongdev.skysongnpcs.config.PluginConfiguration;
import org.skysongdev.skysongnpcs.npc.NpcLookup;
import org.skysongdev.skysongnpcs.npc.NpcRecord;
import org.skysongdev.skysongnpcs.npc.NpcRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * The NPC menu: the registered buttons, and drawing, redrawing and clicking menus. Also the
 * API's {@link NpcMenu}. Layout comes from menus.yml, read again on every draw, so
 * {@code /snpc reload} applies to the next menu opened.
 *
 * <p>A button from another plugin that throws while drawing is left out of that menu and logged,
 * so one broken button never breaks the menu for everyone.
 */
public final class NpcMenuService implements NpcMenu {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]+");

    private final Plugin plugin;
    private final NpcRegistry registry;
    private final NpcLookup citizens;
    private final PluginConfiguration configuration;
    private final Map<String, RegisteredButton> buttons = new LinkedHashMap<>();

    public NpcMenuService(Plugin plugin, NpcRegistry registry, NpcLookup citizens, PluginConfiguration configuration) {
        this.plugin = plugin;
        this.registry = registry;
        this.citizens = citizens;
        this.configuration = configuration;
    }

    @Override
    public @NotNull String registerButton(@NotNull Plugin owner, @NotNull String name, int slot,
                                          @NotNull Function<MenuContext, ItemStack> icon,
                                          @NotNull Predicate<MenuContext> visibleTo,
                                          @NotNull Consumer<MenuClick> onClick) {
        mainThread();
        Objects.requireNonNull(owner, "plugin");
        Objects.requireNonNull(name, "name");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Button name '" + name
                    + "' may only hold lower-case letters, digits, _ and -");
        }
        String id = id(owner, name);
        if (buttons.containsKey(id)) {
            throw new IllegalArgumentException("A button " + id + " is registered already");
        }
        buttons.put(id, new RegisteredButton(id, owner, slot, Objects.requireNonNull(icon, "icon"),
                Objects.requireNonNull(visibleTo, "visibleTo"), Objects.requireNonNull(onClick, "onClick")));
        return id;
    }

    @Override
    public boolean unregisterButton(@NotNull Plugin owner, @NotNull String name) {
        mainThread();
        return buttons.remove(id(Objects.requireNonNull(owner, "plugin"), Objects.requireNonNull(name, "name"))) != null;
    }

    /** Drops every button of a plugin; called when it disables. */
    public void unregisterAll(Plugin owner) {
        buttons.values().removeIf(button -> button.plugin().equals(owner));
    }

    @Override
    public @NotNull List<String> buttons() {
        mainThread();
        // SkysongNPCs registers its own buttons on enable, before any other plugin can, so
        // registration order already puts them first.
        return List.copyOf(buttons.keySet());
    }

    @Override
    public boolean open(@NotNull Player viewer, @NotNull UUID npc) {
        mainThread();
        Optional<NpcRecord> record = registry.get(Objects.requireNonNull(npc, "npc"));
        if (record.isEmpty()) {
            return false;
        }
        NpcMenuHolder holder = new NpcMenuHolder(Objects.requireNonNull(viewer, "viewer"), record.get());
        MenuSettings settings = settings();
        Map<Integer, RegisteredButton> placed = arrange(holder, settings.rows() * 9);
        if (placed.isEmpty()) {
            return false;
        }
        Component title = configuration.messages().get("menu.title", Map.of("npc", citizens.name(npc)));
        Inventory inventory = Bukkit.createInventory(holder, settings.rows() * 9, title);
        holder.inventory(inventory);
        fill(holder, placed);
        viewer.openInventory(inventory);
        return true;
    }

    /** Whether right-clicking a managed NPC opens its menu (menus.yml). */
    public boolean opensOnRightClick() {
        return settings().openOnRightClick();
    }

    /** Draws an open menu again; closes it when the NPC is gone or the viewer sees no button any more. */
    void refresh(NpcMenuHolder holder) {
        Optional<NpcRecord> record = registry.get(holder.npc().id());
        Map<Integer, RegisteredButton> placed = Map.of();
        if (record.isPresent()) {
            holder.npc(record.get());
            placed = arrange(holder, holder.getInventory().getSize());
        }
        if (placed.isEmpty()) {
            closeLater(holder);
            return;
        }
        fill(holder, placed);
    }

    /** A click in the menu's own slots. The listener has cancelled it already. */
    void click(NpcMenuHolder holder, int slot, ClickType type) {
        RegisteredButton button = holder.button(slot);
        // The button may have been unregistered, or the viewer's access taken away, since the draw.
        if (button == null || buttons.get(button.id()) != button || !visible(button, holder)) {
            return;
        }
        try {
            button.onClick().accept(new Click(holder, type));
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.WARNING, "The NPC menu button " + button.id() + " failed on a click", failed);
        }
    }

    private Map<Integer, RegisteredButton> arrange(NpcMenuHolder holder, int size) {
        MenuSettings settings = settings();
        List<RegisteredButton> visible = new ArrayList<>();
        for (RegisteredButton button : buttons.values()) {
            if (!settings.hidden(button.id()) && visible(button, holder)) {
                visible.add(button);
            }
        }
        MenuLayout.Arrangement<RegisteredButton> arrangement =
                MenuLayout.arrange(visible, button -> settings.slot(button.id(), button.slot()), size);
        arrangement.dropped().forEach(button -> plugin.getLogger().warning("The NPC menu has no free slot for "
                + button.id() + ". Give it more rows or move buttons in menus.yml."));
        return arrangement.slots();
    }

    private void fill(NpcMenuHolder holder, Map<Integer, RegisteredButton> placed) {
        MenuSettings settings = settings();
        Inventory inventory = holder.getInventory();
        inventory.clear();
        Map<Integer, RegisteredButton> drawn = new LinkedHashMap<>();
        placed.forEach((slot, button) -> {
            ItemStack icon = icon(button, holder);
            if (icon == null) {
                return;
            }
            inventory.setItem(slot, settings.material(button.id()).map(icon::withType).orElse(icon));
            drawn.put(slot, button);
        });
        holder.place(drawn);
    }

    private boolean visible(RegisteredButton button, MenuContext context) {
        try {
            return button.visibleTo().test(context);
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.WARNING, "The NPC menu button " + button.id() + " failed to say who sees it", failed);
            return false;
        }
    }

    private ItemStack icon(RegisteredButton button, MenuContext context) {
        try {
            ItemStack icon = button.icon().apply(context);
            return icon == null || icon.isEmpty() ? null : icon.clone();
        } catch (RuntimeException failed) {
            plugin.getLogger().log(Level.WARNING, "The NPC menu button " + button.id() + " failed to draw", failed);
            return null;
        }
    }

    private void closeLater(NpcMenuHolder holder) {
        // Closing inside a click handler confuses the client; the next tick is safe.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (holder.viewer().getOpenInventory().getTopInventory().getHolder() == holder) {
                holder.viewer().closeInventory();
            }
        });
    }

    private MenuSettings settings() {
        return new MenuSettings(configuration.menus());
    }

    private static String id(Plugin owner, String name) {
        return owner.getName().toLowerCase(Locale.ROOT).replaceAll("[ .]", "_") + ":" + name;
    }

    private static void mainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("The SkysongNPCs API must be called on the main thread");
        }
    }

    /** What a button's click handler gets. */
    private final class Click implements MenuClick {
        private final NpcMenuHolder holder;
        private final ClickType type;

        private Click(NpcMenuHolder holder, ClickType type) {
            this.holder = holder;
            this.type = type;
        }

        @Override
        public @NotNull Player viewer() {
            return holder.viewer();
        }

        @Override
        public @NotNull NpcRecord npc() {
            return holder.npc();
        }

        @Override
        public @NotNull ClickType click() {
            return type;
        }

        @Override
        public void close() {
            closeLater(holder);
        }

        @Override
        public void refresh() {
            NpcMenuService.this.refresh(holder);
        }
    }
}
