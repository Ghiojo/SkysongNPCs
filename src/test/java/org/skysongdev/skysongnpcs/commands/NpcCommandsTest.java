package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.skysongdev.skysongnpcs.SkysongNPCs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NpcCommandsTest {
    private ServerMock server;
    private SkysongNPCs plugin;
    private CommandRunner commands;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SkysongNPCs.class);
        commands = new CommandRunner(plugin.configuration(), "1.2.3", new ReloadCommand(plugin.configuration()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rootShowsTheVersionToAnyone() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer();

        commands.run(player, "snpc");

        assertEquals("[NPCs] SkysongNPCs 1.2.3.", plain(player));
    }

    @Test
    void adminReloadsTheFiles() throws IOException, CommandSyntaxException {
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, Permissions.ADMIN, true);
        Files.writeString(dataFile("messages.yml"), "prefix: '[Edited] '\n");

        commands.run(admin, "snpc reload");

        assertEquals("[Edited] Reloaded config.yml and messages.yml.", plain(admin));
    }

    @Test
    void reloadNamesABrokenFile() throws IOException, CommandSyntaxException {
        PlayerMock admin = server.addPlayer();
        admin.setOp(true);
        Files.writeString(dataFile("config.yml"), "presets: [\n");

        commands.run(admin, "snpc reload");

        assertEquals("[NPCs] Not applied, because of a mistake in config.yml. The previous values stay;"
                + " the console says what is wrong.", plain(admin));
    }

    @Test
    void reloadIsRefusedWithoutAdmin() {
        PlayerMock player = server.addPlayer();

        // Brigadier treats a node the sender may not use as missing; Paper also leaves it out of tab completion.
        assertThrows(CommandSyntaxException.class, () -> commands.run(player, "snpc reload"));
    }

    private Path dataFile(String name) {
        return plugin.getDataFolder().toPath().resolve(name);
    }

    private static String plain(PlayerMock player) {
        return PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage());
    }
}
