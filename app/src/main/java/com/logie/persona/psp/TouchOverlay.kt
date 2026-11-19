package com.logie.persona.psp

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.logie.persona.backend.psp.PspControls
import com.logie.persona.backend.touch.GestureConfig
import com.logie.persona.backend.touch.Gestures
import com.logie.persona.backend.touch.Pad
import com.logie.persona.backend.touch.PadOutput
import org.ppsspp.ppsspp.NativeApp

/**
 * The whole screen as the game's controls: touches go to [Gestures] (held swipe = d-pad, quick swipe left/right = L1/R1, held tap = Circle, tap = X,
 * double tap = Triangle, swipe in from the right edge = Start, tap and held tap = Square) and a two-finger tap calls [onMenu]. Nothing is drawn.
 * The emulator never sees these touches: its own on-screen buttons are off.
 */
class TouchOverlay(context: Context, private val vibrate: Boolean, private val onMenu: () -> Unit) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var gestures: Gestures? = null
    private val tick = Runnable { gestures?.tick(SystemClock.uptimeMillis()); schedule() }

    private val output = object : PadOutput {
        override fun button(button: Pad, down: Boolean) {
            if (down) { NativeApp.keyDown(PspControls.DEVICE, PspControls.key(button), false); if (vibrate && button in FACE) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
            else NativeApp.keyUp(PspControls.DEVICE, PspControls.key(button))
        }
        override fun stick(x: Float, y: Float) { NativeApp.joystickAxis(PspControls.DEVICE, AXES, floatArrayOf(x, y), 2) }
        override fun menu() { onMenu() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val dp = resources.displayMetrics.density
        gestures?.reset(SystemClock.uptimeMillis())
        gestures = Gestures(GestureConfig(dp = dp, screenWidth = w.toFloat()), output)
        // Android's own back gesture lives at the screen's edges; give the right edge back so a swipe in from it can mean Start (Android allows about 200 dp of it).
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(w - (48 * dp).toInt(), (h / 2 - 100 * dp).toInt().coerceAtLeast(0), w, (h / 2 + 100 * dp).toInt().coerceAtMost(h)))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val g = gestures ?: return true
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> { val i = e.actionIndex; g.down(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime) }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) g.move(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> { val i = e.actionIndex; g.up(e.getPointerId(i), e.getX(i), e.getY(i), e.eventTime) }
            MotionEvent.ACTION_CANCEL -> g.reset(e.eventTime)
        }
        schedule()
        return true
    }

    /** Lets go of every button (the menu opened, or the game left the screen). */
    fun releaseAll() { gestures?.reset(SystemClock.uptimeMillis()); handler.removeCallbacks(tick) }

    private fun schedule() {
        handler.removeCallbacks(tick)
        gestures?.nextDeadline()?.let { handler.postAtTime(tick, it) }          // the gestures' clock is uptimeMillis, as MotionEvent.eventTime is
    }

    override fun onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow() }

    private companion object {
        val FACE = setOf(Pad.CROSS, Pad.CIRCLE, Pad.SQUARE, Pad.TRIANGLE)
        val AXES = intArrayOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
    }
}
