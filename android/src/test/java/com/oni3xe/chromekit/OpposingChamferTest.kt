package com.oni3xe.chromekit

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
}
