package com.oni3xe.chromekit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** HudTextField's block cursor must never ask for an offset the previous layout did not contain. */
class HudTextFieldCursorTest {
    @Test fun appendIsNotDrawnOnThePreviousLayout() {
        // "14:00" plus one digit. getCursorRect(6) would throw "offset(6) is out of bounds [0, 5]".
        assertNull(cursorOffsetForLayout(selection = 6, text = "14:001", laidOut = "14:00"))
    }

    @Test fun firstCharacterInAnEmptyFieldWaitsForLayout() {
        assertNull(cursorOffsetForLayout(selection = 1, text = "S", laidOut = ""))
    }

    @Test fun deletionWaitsUntilTheShorterTextIsLaidOut() {
        assertNull(cursorOffsetForLayout(selection = 4, text = "14:0", laidOut = "14:00"))
    }

    @Test fun sameLengthReplacementWaitsUntilTheNewTextIsLaidOut() {
        assertNull(cursorOffsetForLayout(selection = 2, text = "15:00", laidOut = "14:00"))
    }

    @Test fun matchingLayoutUsesTheSelection() {
        assertEquals(5, cursorOffsetForLayout(selection = 5, text = "14:00", laidOut = "14:00"))
        assertEquals(2, cursorOffsetForLayout(selection = 2, text = "14:00", laidOut = "14:00"))
        assertEquals(0, cursorOffsetForLayout(selection = 0, text = "", laidOut = ""))
    }

    @Test fun matchingLayoutClampsASelectionOutsideTheText() {
        assertEquals(0, cursorOffsetForLayout(selection = -1, text = "14:00", laidOut = "14:00"))
        assertEquals(5, cursorOffsetForLayout(selection = 9, text = "14:00", laidOut = "14:00"))
    }
}
