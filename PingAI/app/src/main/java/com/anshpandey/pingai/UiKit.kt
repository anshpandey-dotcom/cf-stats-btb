package com.anshpandey.pingai

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class UiKit(private val ctx: Context) {

    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density).toInt()

    fun color(id: Int): Int = ContextCompat.getColor(ctx, id)

    fun icon(res: Int, sizeDp: Int, tint: Int): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun card(title: String?, iconRes: Int? = null, build: (LinearLayout) -> Unit): View {
        val card = MaterialCardView(ctx).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(2).toFloat()
            setCardBackgroundColor(color(R.color.card_bg))
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
        }
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (title != null) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            if (iconRes != null) {
                row.addView(icon(iconRes, 22, color(R.color.green_mid)).apply {
                    (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(10)
                })
            }
            row.addView(TextView(ctx).apply {
                text = title
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.green_mid))
            })
            box.addView(row)
        }
        build(box)
        card.addView(box)
        return card
    }

    fun label(text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.text_soft))
        setPadding(0, dp(12), 0, dp(4))
    }

    fun hint(text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 13f
        setTextColor(color(R.color.text_soft))
        setPadding(0, dp(4), 0, dp(4))
    }

    fun body(text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 14f
        setTextColor(color(R.color.text_main))
        setPadding(0, dp(4), 0, dp(4))
    }

    fun edit(value: String, hintText: String, lines: Int = 1) = EditText(ctx).apply {
        setText(value)
        hint = hintText
        textSize = 14f
        setTextColor(color(R.color.text_main))
        setHintTextColor(color(R.color.text_soft))
        setBackgroundResource(R.drawable.bg_input)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (lines > 1) {
            minLines = lines
            gravity = Gravity.TOP
        } else {
            setSingleLine(true)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun button(text: String, icon: Int? = null, onClick: () -> Unit) = MaterialButton(ctx).apply {
        this.text = text
        isAllCaps = false
        if (icon != null) {
            setIconResource(icon)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = dp(8)
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    fun outlined(text: String, icon: Int? = null, onClick: () -> Unit) =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            isAllCaps = false
            if (icon != null) {
                setIconResource(icon)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconPadding = dp(8)
            }
            setTextColor(color(R.color.green_mid))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }

    fun switchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) = SwitchCompat(ctx).apply {
        this.text = text
        isChecked = checked
        setTextColor(color(R.color.text_main))
        setPadding(0, dp(6), 0, dp(6))
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    fun row(text: String, onDelete: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(ctx).apply {
            this.text = text
            textSize = 14f
            setTextColor(color(R.color.text_main))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(TextView(ctx).apply {
            this.text = "✕"
            textSize = 18f
            setPadding(dp(14), dp(4), dp(4), dp(4))
            setTextColor(0xFFC62828.toInt())
            setOnClickListener { onDelete() }
        })
        return row
    }

    fun radios(options: List<String>, selected: Int, onSelect: (Int) -> Unit): View {
        val group = RadioGroup(ctx)
        options.forEachIndexed { i, text ->
            group.addView(RadioButton(ctx).apply {
                id = View.generateViewId()
                this.text = text
                textSize = 14f
                setTextColor(color(R.color.text_main))
                isChecked = i == selected
                setOnClickListener { onSelect(i) }
            })
        }
        return group
    }
}
