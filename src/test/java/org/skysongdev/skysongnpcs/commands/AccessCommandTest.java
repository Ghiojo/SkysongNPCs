package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.OfflineSkysongNPCs;
import org.skysongdev.skysongnpcs.api.access.AccessGrant;
import org.skysongdev.skysongnpcs.api.access.AccessGroup;
import org.skysongdev.skysongnpcs.api.access.GrantSource;
import org.skysongdev.skysongnpcs.api.access.NpcAction;
import org.skysongdev.skysongnpcs.api.event.NpcAccessChangeEvent;
import org.skysongdev.skysongnpcs.api.npc.NpcOwner;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@code /snpc access}. */
class AccessCommandTest {
    private ServerMock server;
    private OfflineSkysongNPCs plugin;
    private CommandRunner commands;
    private PlayerMock staff;
    private PlayerMock alice;
    private PlayerMock bob;
    private UUID guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = OfflineSkysongNPCs.load();
        NpcTargeting targeting = new NpcTargeting(plugin.registry(), plugin.citizens(), plugin.characters(),
                plugin.configuration());
        commands = new CommandRunner(plugin.configuration(), "1.0.0",
                new AccessCommand(targeting, plugin.access(), plugin.configuration()));

        staff = server.addPlayer("Staff");
        staff.addAttachment(plugin, Permissions.ADMIN, true);
        alice = server.addPlayer("Alice");
        bob = server.addPlayer("Bob");
        plugin.fakeCharacters().play(alice.getUniqueId(), "Default");
        plugin.fakeCharacters().play(bob.getUniqueId(), "Smith");

        guard = plugin.fakeCitizens().add("Guard");
        plugin.fakeCitizens().target(guard);
        plugin.registry().adopt(guard, NpcOwner.character(alice.getUniqueId(), "Default"));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theOwnerGrantsActionsAndPresetsToAPlayer() throws CommandSyntaxException {
        commands.run(alice, "snpc access grant player:Bob customer, inventory-edit");

        assertEquals("[NPCs] player:Bob now has info, stats-view, teleport, follow, look, inventory-edit on Guard.",
                plain(alice));
        assertEquals(List.of(new AccessGrant(AccessGroup.player(bob.getUniqueId()), GrantSource.owner(),
                Set.of(NpcAction.INFO, NpcAction.STATS_VIEW, NpcAction.TELEPORT, NpcAction.FOLLOW, NpcAction.LOOK,
                        NpcAction.INVENTORY_EDIT))), grants());
        server.getPluginManager().assertEventFired(NpcAccessChangeEvent.class);
    }

    @Test
    void grantingAddsToWhatTheGroupHad() throws CommandSyntaxException {
        commands.run(alice, "snpc access grant perm:server.guards info");
        commands.run(alice, "snpc access grant perm:server.guards look");

        assertEquals(Set.of(NpcAction.INFO, NpcAction.LOOK), grants().get(0).actions());
    }

    @Test
    void aCharacterWithSpacesGoesInQuotes() throws CommandSyntaxException {
        plugin.fakeCharacters().play(bob.getUniqueId(), "Pirate Queen");

        commands.run(alice, "snpc access grant \"player:Bob/Pirate Queen\" info");

        assertEquals(AccessGroup.player(bob.getUniqueId(), "Pirate Queen"), grants().get(0).group());
        assertEquals("[NPCs] player:Bob/Pirate Queen now has info on Guard.", plain(alice));
    }

    @Test
    void revokeTakesActionsAwayOrTheWholeGrant() throws CommandSyntaxException {
        commands.run(alice, "snpc access grant player:Bob info follow");
        plain(alice);

        commands.run(alice, "snpc access revoke player:Bob follow");
        assertEquals("[NPCs] player:Bob now has only info on Guard.", plain(alice));

        commands.run(alice, "snpc access revoke player:Bob");
        assertEquals("[NPCs] player:Bob no longer has any access to Guard from owner.", plain(alice));
        assertEquals(List.of(), grants());
    }

    @Test
    void listsGrantsFromEverySource() throws CommandSyntaxException {
        commands.run(alice, "snpc access");
        assertEquals("[NPCs] Only Alice (Default) and staff may use Guard.", plain(alice));
        plugin.access().grants().set(guard, GrantSource.plugin("SkysongEconomy"),
                AccessGroup.permission("shop.staff"), Set.of(NpcAction.INFO));

        commands.run(alice, "snpc access");

        assertEquals("[NPCs] Besides Alice (Default) and staff, these may use Guard:", plain(alice));
        assertEquals(" - perm:shop.staff (skysongeconomy): info", plain(alice));
    }

    @Test
    void ownerSideCommandsLeavePluginGrantsAlone() throws CommandSyntaxException {
        GrantSource economy = GrantSource.plugin("SkysongEconomy");
        plugin.access().grants().set(guard, economy, AccessGroup.permission("shop.staff"), Set.of(NpcAction.INFO));

        commands.run(alice, "snpc access revoke perm:shop.staff");

        assertEquals("[NPCs] perm:shop.staff has no access to Guard from owner.", plain(alice));
        assertEquals(1, grants().size());
    }

    @Test
    void staffChangeAPluginsGrantsWithAs() throws CommandSyntaxException {
        plugin.access().grants().set(guard, GrantSource.plugin("SkysongEconomy"),
                AccessGroup.permission("shop.staff"), Set.of(NpcAction.INFO));

        commands.run(staff, "snpc access as SkysongEconomy revoke perm:shop.staff");

        assertEquals(List.of(), grants());
        assertThrows(CommandSyntaxException.class,
                () -> commands.run(alice, "snpc access as SkysongEconomy revoke perm:shop.staff"));
    }

    @Test
    void aManagerMayChangeGrantsButOthersMayNot() throws CommandSyntaxException {
        commands.run(bob, "snpc access grant player:Bob all");
        assertEquals("[NPCs] You may not change who can use Guard.", plain(bob));

        commands.run(alice, "snpc access grant player:Bob manage");
        plain(alice);
        commands.run(bob, "snpc access grant perm:x info");

        assertEquals("[NPCs] perm:x now has info on Guard.", plain(bob));
    }

    @Test
    void explainsBadGroupsAndActions() throws CommandSyntaxException {
        commands.run(alice, "snpc access grant economy:smithy info");
        assertEquals("[NPCs] economy:smithy is not a group. Use player:NAME, \"player:NAME/PROFILE\", perm:NODE,"
                + " or a type from another plugin: none.", plain(alice));

        commands.run(alice, "snpc access grant player:Nobody info");
        assertEquals("[NPCs] No player called Nobody has played here.", plain(alice));

        commands.run(alice, "snpc access grant player:Bob fly");
        assertEquals("[NPCs] fly is not an action or a preset.", plain(alice).lines().findFirst().orElseThrow());

        commands.run(alice, "snpc access grant player:Bob");
        assertEquals("[NPCs] Name the actions after the group, or a preset: all, customer, viewer, caretaker.",
                plain(alice));
    }

    @Test
    void splitsAQuotedGroupFromItsActions() {
        assertEquals(List.of("player:Bob/Pirate Queen", "info, look"),
                List.of(AccessCommand.splitGroup("\"player:Bob/Pirate Queen\" info, look")));
        assertEquals(List.of("perm:x", ""), List.of(AccessCommand.splitGroup("perm:x")));
    }

    private List<AccessGrant> grants() {
        return plugin.access().grants().grants(guard);
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
