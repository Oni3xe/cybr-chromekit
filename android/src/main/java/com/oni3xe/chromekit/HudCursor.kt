package com.oni3xe.chromekit

/**
 * Offset for `TextLayoutResult.getCursorRect`, or null when [laidOut] is stale.
 *
 * HudTextField's block cursor reads the layout from the previous frame. Typing updates the
 * selection immediately, and `getCursorRect` throws if that offset is past the layout it is given
 * (`offset(6) is out of bounds [0, 5]` when a digit is appended to "14:00"). Ported from opsync #19.
 */
internal fun cursorOffsetForLayout(selection: Int, text: String, laidOut: String): Int? {
    if (text != laidOut) return null
    return selection.coerceIn(0, laidOut.length)
}
