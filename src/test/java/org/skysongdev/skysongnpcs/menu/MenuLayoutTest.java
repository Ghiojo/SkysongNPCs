package org.skysongdev.skysongnpcs.menu;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuLayoutTest {

    private record Button(String id, int slot) {
    }

    private static MenuLayout.Arrangement<Button> arrange(int size, Button... buttons) {
        return MenuLayout.arrange(List.of(buttons), Button::slot, size);
    }

    @Test
    void buttonsGetTheSlotsTheyAskFor() {
        Button info = new Button("info", 13);
        Button wardrobe = new Button("wardrobe", 11);

        assertEquals(Map.of(11, wardrobe, 13, info), arrange(27, info, wardrobe).slots());
    }

    @Test
    void theFirstButtonKeepsATakenSlotAndTheOtherMovesToTheFirstFreeOne() {
        Button info = new Button("info", 0);
        Button other = new Button("other", 0);
        Button third = new Button("third", 1);

        MenuLayout.Arrangement<Button> arrangement = arrange(9, info, other, third);

        assertEquals(Map.of(0, info, 1, third, 2, other), arrangement.slots());
    }

    @Test
    void aSlotOutsideTheMenuMovesTheButtonInside() {
        Button far = new Button("far", 40);
        Button negative = new Button("negative", -1);

        assertEquals(Map.of(0, far, 1, negative), arrange(9, far, negative).slots());
    }

    @Test
    void buttonsThatDoNotFitAreDropped() {
        Button a = new Button("a", 0);
        Button b = new Button("b", 0);
        Button c = new Button("c", 0);

        MenuLayout.Arrangement<Button> arrangement = arrange(2, a, b, c);

        assertEquals(Map.of(0, a, 1, b), arrangement.slots());
        assertEquals(List.of(c), arrangement.dropped());
    }

    @Test
    void slotsComeInOrder() {
        Button late = new Button("late", 8);
        Button early = new Button("early", 2);

        assertEquals(List.of(2, 8), List.copyOf(arrange(9, late, early).slots().keySet()));
    }
}
