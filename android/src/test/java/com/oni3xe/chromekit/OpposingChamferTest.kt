package com.oni3xe.chromekit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class OpposingChamferTest {
    private val eps = 1e-4f

    @Test fun noCutIsASquareRect() {
        assertArrayEquals(floatArrayOf(-2f, -2f, 102f, -2f, 102f, 52f, -2f, 52f), opposingChamferOutline(100f, 50f, 0f, 2f, true), eps)
    }

    @Test fun ltrCutsTopLeftAndBottomRightOnly() {
        assertArrayEquals(floatArrayOf(10f, 0f, 100f, 0f, 100f, 40f, 90f, 50f, 0f, 50f, 0f, 10f), opposingChamferOutline(100f, 50f, 10f, 0f, true), eps)
    }

    @Test fun rtlMirrorsToTopRightAndBottomLeft() {
        assertArrayEquals(floatArrayOf(0f, 0f, 90f, 0f, 100f, 10f, 100f, 50f, 10f, 50f, 0f, 40f), opposingChamferOutline(100f, 50f, 10f, 0f, false), eps)
    }

    @Test fun cutsAreStraight45DegreeLinesAndStayParallelWhenInflated() {
        for (inflate in listOf(0f, 3f, 14f)) {
            val p = opposingChamferOutline(100f, 50f, 10f, inflate, true)
            // Top-left cut: (p[10], p[11]) -> (p[0], p[1]); bottom-right: (p[4], p[5]) -> (p[6], p[7]).
            assertEquals(abs(p[0] - p[10]), abs(p[11] - p[1]), eps)
            assertEquals(abs(p[4] - p[6]), abs(p[7] - p[5]), eps)
            // The cut line sits exactly `inflate` outside the original cut line x + y = 10.
            val dist = (10f - (p[0] + p[1])) / sqrt(2f)
            assertEquals(inflate, dist, eps)
        }
    }

    @Test fun cutIsClampedToHalfTheShortSide() {
        val p = opposingChamferOutline(40f, 20f, 50f, 0f, true)
        assertEquals(10f, p[0], eps)
    }

    @Test fun negativeInflateInsetsTheOutlineAndKeepsTheCutParallel() {
        // The screen frame strokes at half a border inside the edge
        val p = opposingChamferOutline(100f, 50f, 6f, -0.5f, true)
        assertEquals(0.5f, p[1], eps)
        assertEquals(99.5f, p[2], eps)
        val dist = ((p[0] + p[1]) - 6f) / sqrt(2f)
        assertEquals(0.5f, dist, eps)
        assertEquals(abs(p[4] - p[6]), abs(p[7] - p[5]), eps)
    }

    @Test fun cornerTrianglesAreTopStartAndBottomEnd() {
        val ltr = chamferCornerTriangles(100f, 50f, 6f, true)
        assertEquals(2, ltr.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 6f, 0f, 0f, 6f), ltr[0], eps)
        assertArrayEquals(floatArrayOf(100f, 50f, 100f, 44f, 94f, 50f), ltr[1], eps)
        val rtl = chamferCornerTriangles(100f, 50f, 6f, false)
        assertArrayEquals(floatArrayOf(100f, 0f, 94f, 0f, 100f, 6f), rtl[0], eps)
        assertArrayEquals(floatArrayOf(0f, 50f, 0f, 44f, 6f, 50f), rtl[1], eps)
    }

    @Test fun noCutHasNoCornerTriangles() {
        assertEquals(0, chamferCornerTriangles(100f, 50f, 0f, true).size)
    }

    @Test fun theSixDpChamferMatchesTheTokenOnBothCorners() {
        val c = Tokens.Shape.CHAMFER_DP
        val p = opposingChamferOutline(80f, 36f, c, 0f, true)
        assertArrayEquals(floatArrayOf(c, 0f, 80f, 0f, 80f, 36f - c, 80f - c, 36f, 0f, 36f, 0f, c), p, eps)
    }

    /**
     * The top-start / bottom-end triangles sit *outside* the chamfered outline. Filling them with
     * `bg` (the old hudScreenFrame) painted a solid ear past the frame line. The frame now clips
     * to [ChamferShape] instead and leaves these corners to the bezel behind the window.
     */
    @Test fun cutCornerInteriorIsOutsideTheFrame() {
        val cut = 6f
        val tri = chamferCornerTriangles(100f, 50f, cut, true)[0]
        val cx = (tri[0] + tri[2] + tri[4]) / 3f
        val cy = (tri[1] + tri[3] + tri[5]) / 3f
        assertTrue(cx + cy < cut)
        val p = opposingChamferOutline(100f, 50f, cut, 0f, true)
        // Cut line is (cut, 0) -> (0, cut); a point with x+y < cut is on the outside of that line.
        assertEquals(cut, p[0], eps)
        assertEquals(0f, p[1], eps)
        assertEquals(0f, p[10], eps)
        assertEquals(cut, p[11], eps)
    }
}
