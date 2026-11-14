package net.bigyous.gptgodmc.god.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * take_item counts slots by kind. It once asked the inventory to remove an exact pristine stack, so a sword that had
 * been swung even once "was not there" while its owner stood holding it.
 */
class TakeItemTest {
    @Test
    void theOneInHandGoesFirst() {
        // Slots 0..4: a sword in slot 1 (held) and another in slot 3.
        assertArrayEquals(new int[] { 0, 1, 0, 0, 0 }, BlessingTools.takeFrom(new int[] { 0, 1, 0, 1, 0 }, 1, 1));
        assertArrayEquals(new int[] { 0, 1, 0, 1, 0 }, BlessingTools.takeFrom(new int[] { 0, 1, 0, 1, 0 }, 2, 1));
    }

    @Test
    void stacksAreTakenInOrderUntilEnough() {
        assertArrayEquals(new int[] { 64, 0, 6, 0 }, BlessingTools.takeFrom(new int[] { 64, 0, 32, 10 }, 70, 1));
        assertArrayEquals(new int[] { 5, 0, 0 }, BlessingTools.takeFrom(new int[] { 9, 0, 3 }, 5, -1));
    }

    @Test
    void itTakesOnlyWhatIsThere() {
        assertArrayEquals(new int[] { 3, 0, 2 }, BlessingTools.takeFrom(new int[] { 3, 0, 2 }, 40, 0));
        assertArrayEquals(new int[] { 0, 0, 0 }, BlessingTools.takeFrom(new int[] { 0, 0, 0 }, 1, 2));
    }
}
