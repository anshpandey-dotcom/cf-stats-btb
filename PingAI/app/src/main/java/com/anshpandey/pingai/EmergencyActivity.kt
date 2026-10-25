package com.anshpandey.pingai

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class EmergencyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (!AlertCenter.active) {
            finish()
            return
        }
        AlertCenter.screen = this

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(dp(24), dp(80), dp(24), dp(40))
        }
        box.addView(TextView(this).apply {
            text = "Call request"
            textSize = 16f
            setTextColor(Color.parseColor("#CFEFE9"))
        })
        box.addView(TextView(this).apply {
            text = AlertCenter.sender
            textSize = 32f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(12))
        })
        box.addView(TextView(this).apply {
            text = AlertCenter.message
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-1, 0, 1f))

        box.addView(button("Receive (read it out)", "#25D366") { AlertCenter.receive(this) })
        val who = Store(this).userName.ifBlank { "him" }
        box.addView(button("Message: I have told $who", "#128C7E") { AlertCenter.quickMessage(this) })
        box.addView(button("Cut", "#C62828") { AlertCenter.cut(this) })
        setContentView(box)
    }

    override fun onDestroy() {
        if (AlertCenter.screen === this) AlertCenter.screen = null
        super.onDestroy()
    }

    private fun button(label: String, color: String, onClick: () -> Unit) = MaterialButton(this).apply {
        text = label
        isAllCaps = false
        setBackgroundColor(Color.parseColor(color))
        setTextColor(Color.WHITE)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(12) }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
