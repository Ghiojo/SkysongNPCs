package org.skysongdev.skysongnpcs.commands;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Collection;
import java.util.function.Supplier;

/** What the injury, condition and ancestry commands share. */
final class EffectText {
    private EffectText() {
    }

    /**
     * A SkysongStats name (MiniMessage) as plain text, since messages.yml placeholders are
     * inserted as they are.
     */
    static String plain(String miniMessage) {
        return MiniMessage.miniMessage().stripTags(miniMessage);
    }

    /** Staff, or a DM with {@link Permissions#INJURIES}. */
    static boolean mayAfflict(CommandSender sender) {
        return sender.hasPermission(Permissions.ADMIN) || sender.hasPermission(Permissions.INJURIES);
    }

    static SuggestionProvider<CommandSourceStack> suggest(Supplier<Collection<String>> names) {
        return SheetTargeting.suggest(names);
    }
}
