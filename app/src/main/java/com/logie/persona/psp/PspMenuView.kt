package com.logie.persona.psp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** A row of the menu: its words, what it is set to (if anything) and what tapping it does. */
class MenuRow(val label: String, val value: String? = null, val action: () -> Unit)

/** The menu a two-finger tap opens over the game: a dark panel in the launcher's colours with one row per choice. It takes every touch while it is up. */
class PspMenuView(context: Context) : FrameLayout(context) {
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init {
        setBackgroundColor(Color.argb(150, 6, 4, 24))
        isClickable = true; isFocusable = true
        val panel = ScrollView(context).apply {
            background = GradientDrawable().apply { setColor(Color.argb(238, 18, 12, 64)); cornerRadius = dp(14f); setStroke(dp(2f).toInt(), Color.argb(255, 201, 184, 255)) }
            setPadding(dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt(), dp(8f).toInt())
            addView(list)
        }
        addView(panel, LayoutParams(dp(440f).toInt().coerceAtMost(resources.displayMetrics.widthPixels - dp(32f).toInt()), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    fun show(rows: List<MenuRow>) {
        list.removeAllViews()
        for (r in rows) list.addView(row(r), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f).toInt()))
        visibility = View.VISIBLE
    }

    fun hide() { visibility = View.GONE }

    private fun row(r: MenuRow): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14f).toInt(), 0, dp(14f).toInt(), 0)
        isClickable = true
        background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(8f) }
        setOnTouchListener { v, e ->
            val d = v.background as GradientDrawable
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> d.setColor(Color.argb(90, 201, 184, 255))
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> d.setColor(Color.TRANSPARENT)
            }
            false
        }
        setOnClickListener { r.action() }
        addView(TextView(context).apply {
            text = r.label; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.12f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.value?.let { v -> addView(TextView(context).apply { text = v; setTextColor(Color.rgb(201, 184, 255)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f) }) }
    }
}
