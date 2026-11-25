package com.oni3xe.chromekit.views

import android.animation.ValueAnimator
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
 * chromekit glitch overlay: subtle, intermittent HUD glitches drawn over an app's UI in short bursts
 * on a timer. Framework-only (Views apps compile this folder in; Compose apps host the View with
 * AndroidView). Nothing is drawn or invalidated between bursts; it stops while not visible.
 */

/** The glitch look. Unknown or missing values parse to null (= off, fail closed). */
enum class GlitchType(val value: String) {
    /** A few faint horizontal scanlines sweeping through a band. */
    SCANLINES("scanlines"),
    /** Thin slices with a primary/cyan offset fringe (chromatic shift). */
    RGB_SPLIT("rgb"),
    /** A brief dim flicker with one torn, offset band. */
    TEAR("tear"),
    /** A few small noise blocks. */
    BLOCKS("blocks");

    companion object {
        val DEFAULT = SCANLINES
        fun parse(value: String?): GlitchType? = entries.firstOrNull { it.value == value }
    }
}

/** On/type/strength together. [strength] is 1..3. */
data class GlitchSpec(val type: GlitchType, val strength: Int) {
    init { require(strength in GlitchMath.MIN_STRENGTH..GlitchMath.MAX_STRENGTH) }

    companion object {
        /** From synced values: null (off) unless [enabled] is exactly 1 and the type is known; strength clamped. */
        fun parse(enabled: Long?, type: String?, strength: Long?): GlitchSpec? {
            if (enabled != 1L) return null
            val t = GlitchType.parse(type) ?: return null
            val s = (strength ?: GlitchMath.MIN_STRENGTH.toLong()).coerceIn(GlitchMath.MIN_STRENGTH.toLong(), GlitchMath.MAX_STRENGTH.toLong())
            return GlitchSpec(t, s.toInt())
        }
    }
}

/** Timing and intensity, pure (unit tested). */
object GlitchMath {
    const val MIN_STRENGTH = 1
    const val MAX_STRENGTH = 3
    /** Frame step inside a burst (about 20fps: a glitch should stutter, not animate smoothly). */
    const val FRAME_MS = 50L

    private val GAP_MIN_MS = longArrayOf(14_000, 9_000, 6_000)
    private val GAP_MAX_MS = longArrayOf(26_000, 17_000, 11_000)
    private val BURST_MIN_MS = longArrayOf(90, 120, 150)
    private val BURST_MAX_MS = longArrayOf(160, 220, 300)
    private val ALPHA = floatArrayOf(0.06f, 0.10f, 0.15f)

    private fun i(strength: Int) = strength.coerceIn(MIN_STRENGTH, MAX_STRENGTH) - 1

    /** Quiet time before the next burst. */
    fun gapMs(strength: Int, rnd: Random): Long = rnd.nextLong(GAP_MIN_MS[i(strength)], GAP_MAX_MS[i(strength)] + 1)

    /** Length of one burst. */
    fun burstMs(strength: Int, rnd: Random): Long = rnd.nextLong(BURST_MIN_MS[i(strength)], BURST_MAX_MS[i(strength)] + 1)

    /** Peak alpha of the overlay marks. */
    fun alpha(strength: Int): Float = ALPHA[i(strength)]

    /** How many marks (lines, slices, blocks) per frame. */
    fun marks(type: GlitchType, strength: Int): Int = when (type) {
        GlitchType.SCANLINES -> 3 + 2 * i(strength)
        GlitchType.RGB_SPLIT -> 1 + i(strength)
        GlitchType.TEAR -> 1
        GlitchType.BLOCKS -> 3 + 2 * i(strength)
    }

    /** Share of the time spent bursting (battery bound): worst case burst max / gap min. */
    fun maxDutyCycle(strength: Int): Float = BURST_MAX_MS[i(strength)].toFloat() / GAP_MIN_MS[i(strength)]

    /**
     * Reduced motion: animator duration scale 0 (developer option, and what "remove animations"
     * sets), or animators disabled. Glitches are then off.
     */
    fun reduceMotion(context: Context): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
        val transition = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) }.getOrDefault(1f)
        return scale == 0f || transition == 0f
    }
}

/**
 * Full-screen, non-interactive overlay. Set [spec] (null = off) and [primary]/[secondary] (theme
 * colours: red, cyan). It idles with no drawing and no frame callbacks; a timer wakes it for a short
 * burst of a few frames, then sleeps again. It only runs while attached, shown and its window is
 * visible (so the screen being off or another app on top stops it), and never with reduced motion.
 */
class GlitchOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var spec: GlitchSpec? = null
        set(value) { if (field != value) { field = value; restart() } }
    var primary: Int = Color.RED
    var secondary: Int = Color.CYAN

    private val rnd = Random(SystemClock.uptimeMillis())
    private val paint = Paint()
    private var burstEnd = 0L
    private var running = false

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
                postDelayed(this, GlitchMath.gapMs(s.strength, rnd))
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
        val s = spec ?: return
        if (!canRun()) return
        running = true
        postDelayed(tick, GlitchMath.gapMs(s.strength, rnd) / 2)
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
        val a = (GlitchMath.alpha(s.strength) * 255).toInt()
        val n = GlitchMath.marks(s.type, s.strength)
        val px = resources.displayMetrics.density
        when (s.type) {
            GlitchType.SCANLINES -> {
                val bandTop = rnd.nextFloat() * h * 0.85f
                val band = h * 0.15f
                paint.color = primary; paint.alpha = a
                repeat(n * 3) {
                    val y = bandTop + rnd.nextFloat() * band
                    canvas.drawRect(0f, y, w, y + px, paint)
                }
            }
            GlitchType.RGB_SPLIT -> repeat(n) {
                val y = rnd.nextFloat() * h
                val sh = (2f + rnd.nextFloat() * 6f) * px
                val off = (2f + rnd.nextFloat() * 4f) * px
                paint.color = primary; paint.alpha = a
                canvas.drawRect(-off, y, w - off, y + sh, paint)
                paint.color = secondary; paint.alpha = a
                canvas.drawRect(off, y + px, w + off, y + sh + px, paint)
            }
            GlitchType.TEAR -> {
                paint.color = Color.BLACK; paint.alpha = a / 2
                canvas.drawRect(0f, 0f, w, h, paint) // flicker
                val y = rnd.nextFloat() * h
                val th = (6f + rnd.nextFloat() * 18f) * px
                paint.color = secondary; paint.alpha = a
                canvas.drawRect((4f + rnd.nextFloat() * 10f) * px, y, w, y + th, paint)
                paint.color = primary; paint.alpha = (a * 1.5f).toInt().coerceAtMost(255)
                canvas.drawRect(0f, y, w, y + px, paint)
            }
            GlitchType.BLOCKS -> repeat(n) {
                val bw = (8f + rnd.nextFloat() * 40f) * px
                val bh = (4f + rnd.nextFloat() * 14f) * px
                val x = rnd.nextFloat() * (w - bw)
                val y = rnd.nextFloat() * (h - bh)
                paint.color = if (rnd.nextBoolean()) primary else secondary; paint.alpha = a
                canvas.drawRect(x, y, x + bw, y + bh, paint)
            }
        }
    }
}
