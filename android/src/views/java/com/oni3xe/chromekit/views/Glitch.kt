package com.oni3xe.chromekit.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/*
 * chromekit glitch overlay (cybr-launcher only): short HUD glitch bursts drawn over the home screen
 * on a timer. Framework-only; the launcher compiles this folder in. Nothing is drawn or invalidated
 * between bursts, and it stops while not visible. Not part of theme sync.
 */

/** The glitch look. Unknown or missing values parse to null. */
enum class GlitchType(val value: String) {
    /** A band of horizontal scanlines. */
    SCANLINES("scanlines"),
    /** Slices with a primary/cyan offset fringe (chromatic shift). */
    RGB_SPLIT("rgb"),
    /** A brief dim flicker with one torn, offset band. */
    TEAR("tear"),
    /** Small noise blocks. */
    BLOCKS("blocks");

    companion object {
        val DEFAULT = SCANLINES
        fun parse(value: String?): GlitchType? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Type, strength and frequency together. [strength] (1..3) sets how strong a burst looks;
 * [frequency] (1..3) sets how often bursts come, independently. Frequency 1 is the original cadence.
 */
data class GlitchSpec(
    val type: GlitchType,
    val strength: Int,
    val frequency: Int = GlitchMath.MIN_FREQUENCY,
    /** Theme tint: the whole glitch in the main colour only, at a higher alpha (see [GlitchMath.ink]). */
    val tinted: Boolean = false,
) {
    init {
        require(strength in GlitchMath.MIN_STRENGTH..GlitchMath.MAX_STRENGTH)
        require(frequency in GlitchMath.MIN_FREQUENCY..GlitchMath.MAX_FREQUENCY)
    }
}

/** Timing and intensity, pure (unit tested). */
object GlitchMath {
    const val MIN_STRENGTH = 1
    const val MAX_STRENGTH = 3
    const val MIN_FREQUENCY = 1
    const val MAX_FREQUENCY = 3
    /** Frame step inside a burst (about 20fps: a glitch should stutter, not animate smoothly). */
    const val FRAME_MS = 50L
    /** The first burst after it's turned on or the screen comes back: soon enough to be noticed. */
    const val FIRST_MIN_MS = 1_000L
    const val FIRST_MAX_MS = 2_000L

    private val GAP_MIN_MS = longArrayOf(7_000, 5_000, 3_000)
    private val GAP_MAX_MS = longArrayOf(12_000, 9_000, 6_000)
    private val BURST_MIN_MS = longArrayOf(160, 200, 260)
    private val BURST_MAX_MS = longArrayOf(260, 320, 420)
    private val ALPHA = floatArrayOf(0.14f, 0.20f, 0.28f)
    /** Tinted: alpha x1.8, capped so strength 3 stays tasteful. */
    const val TINT_ALPHA_FACTOR = 1.8f
    const val TINT_ALPHA_MAX = 0.45f
    /** Neutral mode's flicker dim (TEAR). */
    const val FLICKER_NEUTRAL: Int = -0x1000000 // opaque black
    /** Gap multiplier per frequency: 1 = the original gaps exactly, 2 = ~1.7x as often, 3 = ~3x. */
    private val FREQUENCY_GAP_FACTOR = floatArrayOf(1f, 0.6f, 0.35f)
    /** No gap is ever shorter than this (frequency 2-3 only; frequency 1 gaps are 3s and up). */
    const val GAP_FLOOR_MS = 1_500L
    /** Battery bound: the most of the time spent drawing, per frequency (worst case, strength 3). */
    private val DUTY_CAP = floatArrayOf(0.15f, 0.25f, 0.30f)

    private fun i(strength: Int) = strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) - 1
    private fun f(frequency: Int) = frequency.coerceIn(MIN_FREQUENCY, MAX_FREQUENCY) - 1
    private fun scaled(ms: Long, frequency: Int): Long =
        if (f(frequency) == 0) ms else (ms * FREQUENCY_GAP_FACTOR[f(frequency)]).toLong().coerceAtLeast(GAP_FLOOR_MS)

    /** Shortest and longest quiet time for this strength and frequency. */
    fun gapRangeMs(strength: Int, frequency: Int = MIN_FREQUENCY): LongRange =
        scaled(GAP_MIN_MS[i(strength)], frequency)..scaled(GAP_MAX_MS[i(strength)], frequency)

    /** The drawing-time cap for a frequency level. */
    fun dutyCap(frequency: Int): Float = DUTY_CAP[f(frequency)]

    /** Delay before the first burst once running. */
    fun firstMs(rnd: Random): Long = rnd.nextLong(FIRST_MIN_MS, FIRST_MAX_MS + 1)

    /** Quiet time before the next burst. */
    fun gapMs(strength: Int, rnd: Random): Long = gapMs(strength, MIN_FREQUENCY, rnd)

    fun gapMs(strength: Int, frequency: Int, rnd: Random): Long =
        gapRangeMs(strength, frequency).let { rnd.nextLong(it.first, it.last + 1) }

    /** Length of one burst. */
    fun burstMs(strength: Int, rnd: Random): Long = rnd.nextLong(BURST_MIN_MS[i(strength)], BURST_MAX_MS[i(strength)] + 1)

    /** Peak alpha of the overlay marks (neutral mode). */
    fun alpha(strength: Int): Float = ALPHA[i(strength)]

    /** Peak alpha for neutral or tinted mode. */
    fun alpha(strength: Int, tinted: Boolean): Float =
        if (tinted) (alpha(strength) * TINT_ALPHA_FACTOR).coerceAtMost(TINT_ALPHA_MAX) else alpha(strength)

    /**
     * The colours and alpha a burst draws with. Neutral: [main] + [accent] marks, a black flicker.
     * Tinted: every mark and the flicker in [main] only, at [alpha] (strength, tinted).
     */
    fun ink(main: Int, accent: Int, strength: Int, tinted: Boolean): GlitchInk =
        if (tinted) GlitchInk(main, main, main, alpha(strength, true))
        else GlitchInk(main, accent, FLICKER_NEUTRAL, alpha(strength, false))

    /** How many marks (lines, slices, blocks) per frame. */
    fun marks(type: GlitchType, strength: Int): Int = when (type) {
        GlitchType.SCANLINES -> 6 + 3 * i(strength)
        GlitchType.RGB_SPLIT -> 2 + i(strength)
        GlitchType.TEAR -> 1
        GlitchType.BLOCKS -> 5 + 3 * i(strength)
    }

    /** Share of the time spent bursting (battery bound): worst case burst max / gap min. */
    fun maxDutyCycle(strength: Int, frequency: Int = MIN_FREQUENCY): Float =
        BURST_MAX_MS[i(strength)].toFloat() / gapRangeMs(strength, frequency).first

    /**
     * Reduced motion, from the system's animator duration scale: 0 means "animator duration scale:
     * off" (developer options) or "remove animations" (accessibility, which sets it to 0). Window
     * and transition scales are NOT used (people turn those off for speed alone), and neither is
     * battery saver.
     */
    fun reducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    fun reduceMotion(context: Context): Boolean = reducedMotion(
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    )
}

/** What one burst draws with: mark colours, the TEAR flicker colour and the peak alpha (0..1). */
data class GlitchInk(val primary: Int, val secondary: Int, val flicker: Int, val alpha: Float) {
    val colors: Set<Int> get() = setOf(primary, secondary, flicker)
}

/**
 * Full-screen, non-interactive overlay. Set [spec] (null = off) and [primary]/[secondary] (theme
 * colours: main, accent). With [GlitchSpec.tinted] it draws in [primary] only, at a higher alpha. It idles with no drawing and no frame callbacks; a timer wakes it for a short
 * burst of a few frames, then sleeps again. The first burst comes 1-2s after it starts running
 * (turned on, shown again, back from another screen). It only runs while attached, shown and its
 * window visible (screen off or another app on top stops it), and never with reduced motion.
 */
class GlitchOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var spec: GlitchSpec? = null
        set(value) { if (field != value) { field = value; restart() } }
    var primary: Int = Color.RED
    var secondary: Int = Color.CYAN

    private val rnd = Random(SystemClock.uptimeMillis())
    private val paint = Paint()
    private var burstEnd = 0L
    /** True while the timer is scheduled. */
    var running = false
        private set

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setWillNotDraw(false)
    }

    /** One timer: starts a burst when idle, steps frames during it, then schedules the next burst. */
    private val tick: Runnable = object : Runnable {
        override fun run() {
            val s = spec ?: return
            val now = SystemClock.uptimeMillis()
            if (burstEnd == 0L) burstEnd = now + GlitchMath.burstMs(s.strength, rnd)
            if (now < burstEnd) {
                invalidate()
                postDelayed(this, GlitchMath.FRAME_MS)
            } else {
                burstEnd = 0L
                invalidate() // one clean frame
                postDelayed(this, GlitchMath.gapMs(s.strength, s.frequency, rnd))
            }
        }
    }

    private fun canRun(): Boolean = spec != null && isAttachedToWindow && windowVisibility == VISIBLE &&
        isShown && !GlitchMath.reduceMotion(context)

    private fun stop() {
        removeCallbacks(tick)
        if (burstEnd != 0L) { burstEnd = 0L; invalidate() }
        running = false
    }

    private fun restart() {
        stop()
        if (!canRun()) return
        running = true
        postDelayed(tick, GlitchMath.firstMs(rnd))
    }

    /** A burst right now (settings preview after a change), then the normal rhythm. */
    fun burstNow() {
        stop()
        if (!canRun()) return
        running = true
        post(tick)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); restart() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) { if (!running) restart() } else stop()
    }
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) { if (!running) restart() } else stop()
    }

    override fun onDraw(canvas: Canvas) {
        val s = spec ?: return
        if (burstEnd == 0L) return
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val ink = GlitchMath.ink(primary, secondary, s.strength, s.tinted)
        val primary = ink.primary; val secondary = ink.secondary
        val a = (ink.alpha * 255).toInt()
        val n = GlitchMath.marks(s.type, s.strength)
        val px = resources.displayMetrics.density
        when (s.type) {
            GlitchType.SCANLINES -> {
                val band = h * 0.2f
                val bandTop = rnd.nextFloat() * (h - band)
                paint.color = primary; paint.alpha = a
                val step = band / n
                repeat(n) { k ->
                    val y = bandTop + k * step + rnd.nextFloat() * step * 0.3f
                    canvas.drawRect(0f, y, w, y + 2f * px, paint)
                }
            }
            GlitchType.RGB_SPLIT -> repeat(n) {
                val y = rnd.nextFloat() * h
                val sh = (3f + rnd.nextFloat() * 10f) * px
                val off = (3f + rnd.nextFloat() * 6f) * px
                paint.color = primary; paint.alpha = a
                canvas.drawRect(-off, y, w - off, y + sh, paint)
                paint.color = secondary; paint.alpha = a
                canvas.drawRect(off, y + px, w + off, y + sh + px, paint)
            }
            GlitchType.TEAR -> {
                paint.color = ink.flicker; paint.alpha = a / 2
                canvas.drawRect(0f, 0f, w, h, paint) // flicker
                val y = rnd.nextFloat() * h
                val th = (10f + rnd.nextFloat() * 24f) * px
                paint.color = secondary; paint.alpha = a
                canvas.drawRect((6f + rnd.nextFloat() * 14f) * px, y, w, y + th, paint)
                paint.color = primary; paint.alpha = (a * 1.5f).toInt().coerceAtMost(255)
                canvas.drawRect(0f, y, w, y + 2f * px, paint)
            }
            GlitchType.BLOCKS -> repeat(n) {
                val bw = (12f + rnd.nextFloat() * 48f) * px
                val bh = (6f + rnd.nextFloat() * 16f) * px
                val x = rnd.nextFloat() * (w - bw).coerceAtLeast(1f)
                val y = rnd.nextFloat() * (h - bh).coerceAtLeast(1f)
                paint.color = if (rnd.nextBoolean()) primary else secondary; paint.alpha = a
                canvas.drawRect(x, y, x + bw, y + bh, paint)
            }
        }
    }
}
