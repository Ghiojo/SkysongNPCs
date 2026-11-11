package org.skysongdev.skysongnpcs.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Puts buttons in slots. Each button asks for a slot; one asking for a slot outside the menu,
 * or one an earlier button already took, goes to the first free slot instead. Buttons that do
 * not fit at all are left out.
 */
final class MenuLayout {
    private MenuLayout() {
    }

    /**
     * @param buttons the buttons, earlier ones winning their slot
     * @param slot    the slot each one asks for
     * @param size    the number of slots in the menu
     */
    static <T> Arrangement<T> arrange(List<T> buttons, ToIntFunction<T> slot, int size) {
        Map<Integer, T> placed = new TreeMap<>();
        List<T> moved = new ArrayList<>();
        for (T button : buttons) {
            int wanted = slot.applyAsInt(button);
            if (wanted >= 0 && wanted < size && !placed.containsKey(wanted)) {
                placed.put(wanted, button);
            } else {
                moved.add(button);
            }
        }
        List<T> dropped = new ArrayList<>();
        int free = 0;
        for (T button : moved) {
            while (free < size && placed.containsKey(free)) {
                free++;
            }
            if (free < size) {
                placed.put(free, button);
            } else {
                dropped.add(button);
            }
        }
        return new Arrangement<>(Collections.unmodifiableMap(placed), List.copyOf(dropped));
    }

    /**
     * @param slots   slot to button, in slot order
     * @param dropped the buttons that found no free slot
     */
    record Arrangement<T>(Map<Integer, T> slots, List<T> dropped) {
    }
}
