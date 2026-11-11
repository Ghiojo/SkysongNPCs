package org.skysongdev.skysongnpcs.api;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skysongdev.skysongnpcs.api.access.Access;
import org.skysongdev.skysongnpcs.api.inventory.NpcInventories;
import org.skysongdev.skysongnpcs.api.menu.NpcMenu;
import org.skysongdev.skysongnpcs.api.items.NpcItems;
import org.skysongdev.skysongnpcs.api.npc.Npcs;
import org.skysongdev.skysongnpcs.api.sheet.NpcSheets;
// M5: access policies
import org.skysongdev.skysongnpcs.api.policy.Policies;

/**
 * Entry point of the SkysongNPCs API, the supported way for other plugins (SkysongEconomy
 * first of all) to work with managed Citizens NPCs. Classes outside the
 * {@code org.skysongdev.skysongnpcs.api} package are internal and may change without notice.
 *
 * <p>SkysongNPCs registers this API with Bukkit's {@link org.bukkit.plugin.ServicesManager}
 * at the end of its {@code onEnable} and removes it in {@code onDisable}. Plugins that use it
 * add {@code depend: [SkysongNPCs]} (or {@code softdepend}) to their plugin.yml, so they
 * enable after SkysongNPCs, and get it with {@link #get()}:
 *
 * <pre>{@code
 * SkysongNpcsApi npcs = SkysongNpcsApi.get();
 * }</pre>
 *
 * <p>Every method must be called on the server's main thread. Methods return
 * {@link java.util.Optional} rather than {@code null} when something may be missing.
 *
 * <p>SkysongNPCs implements this interface; other plugins must not.
 */
@ApiStatus.NonExtendable
public interface SkysongNpcsApi {

    /**
     * The API registered by SkysongNPCs.
     *
     * @return the API
     * @throws IllegalStateException when SkysongNPCs is not installed or not enabled yet
     */
    static @NotNull SkysongNpcsApi get() {
        RegisteredServiceProvider<SkysongNpcsApi> registration =
                Bukkit.getServicesManager().getRegistration(SkysongNpcsApi.class);
        if (registration == null) {
            throw new IllegalStateException("The SkysongNPCs API is not available: SkysongNPCs is not enabled."
                    + " Add 'depend: [SkysongNPCs]' to your plugin.yml and check that SkysongNPCs is installed"
                    + " and started without errors.");
        }
        return registration.getProvider();
    }

    /**
     * The version of the API that the installed SkysongNPCs implements, as
     * {@code major.minor.patch}. A plugin can compare it with the version it was built
     * against and log a clear message instead of failing with {@link NoSuchMethodError}.
     * Adding methods raises the minor version; removing or changing one raises the major.
     *
     * @return the API version, for example {@code "0.1.0"}
     */
    @NotNull String apiVersion();

    /**
     * The managed NPCs: find, list, adopt, release, and change owners.
     *
     * @return the NPCs part of the API
     */
    @NotNull Npcs npcs();

    /**
     * Who may do what with the NPCs: grants, group providers, and the check itself.
     *
     * @return the access part of the API
     */
    @NotNull Access access();

    /**
     * The menu managed NPCs open on right-click: add your own buttons to it, or open it for a player.
     *
     * @return the menu part of the API
     */
    @NotNull NpcMenu menu();

    /**
     * Stat sheets: give an NPC one, and get its SkysongStats character id.
     *
     * @return the sheets part of the API
     * @since 0.5.0
     */
    @NotNull NpcSheets sheets();

    // M17

    /**
     * Blueprint items, which place new NPCs: make them and read them.
     *
     * @return the items part of the API
     * @since 0.6.0
     */
    @NotNull NpcItems items();

    // M5: access policies

    /**
     * Access policies: named sets of grants many NPCs follow at once.
     *
     * @return the policies part of the API
     * @since 0.7.0
     */
    @NotNull Policies policies();

    // M9

    /**
     * NPC inventories and equipment: open them for a player by their access, or read them.
     *
     * @return the inventories part of the API
     * @since 0.8.0
     */
    @NotNull NpcInventories inventories();
}
