package org.skysongdev.skysongnpcs.movement;

import org.bukkit.command.CommandSender;
import org.skysongdev.skysongnpcs.config.Messages;

import java.util.HashMap;
import java.util.Map;

/** Tells a player what their move came to, with messages.yml's {@code movement:} texts. */
public final class MovementReplies {
    private MovementReplies() {
    }

    /**
     * Sends {@code doneKey} when it worked, or the text for why not.
     *
     * @param placeholders at least {@code npc}; {@code max} is added for the limits
     */
    public static void send(Messages messages, CommandSender sender, MovementService.Result result,
                            String doneKey, Map<String, String> placeholders, MovementSettings settings) {
        Map<String, String> values = new HashMap<>(placeholders);
        String key = switch (result) {
            case DONE -> doneKey;
            case NOT_PLACED -> "movement.not-placed";
            case OTHER_WORLD -> "movement.other-world";
            case TOO_FAR -> {
                values.put("max", number(settings.teleportMaxDistance()));
                yield "movement.too-far";
            }
            case ABOVE_MAX -> {
                boolean look = doneKey.startsWith("movement.look");
                values.put("max", number(look ? settings.lookMaxRange() : settings.followMaxDistance()));
                yield look ? "movement.look-above-max" : "movement.follow-above-max";
            }
            case NOT_OTHERS -> "movement.follow-not-others";
        };
        messages.send(sender, key, values);
    }

    /** A distance as players read it: {@code 3} rather than {@code 3.0}. */
    public static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
