package com.lagradost.cloudstream3.ui

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.lagradost.cloudstream3.R

object IntroOverlay {
    fun show(activity: Activity) {
        val root = activity.window?.decorView as? ViewGroup ?: return
        val density = activity.resources.displayMetrics.density
        val size = (200 * density).toInt()

        val curtain = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            elevation = 100f
        }
        val logo = ImageView(activity).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            alpha = 0f
            scaleX = 0.55f
            scaleY = 0.55f
        }
        curtain.addView(logo, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        root.addView(
            curtain,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                logo.animate()
                    .scaleX(7f).scaleY(7f).alpha(0f)
                    .setStartDelay(320)
                    .setDuration(520)
                    .setInterpolator(AccelerateInterpolator())
                    .start()
                curtain.animate()
                    .alpha(0f)
                    .setStartDelay(560)
                    .setDuration(320)
                    .withEndAction { root.removeView(curtain) }
                    .start()
            }
            .start()
    }
}
