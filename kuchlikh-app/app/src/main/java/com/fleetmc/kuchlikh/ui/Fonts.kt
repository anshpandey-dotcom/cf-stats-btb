package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import android.graphics.fonts.FontFamily as PlatformFamily

object Fonts {

    val keys = listOf("serif", "sans", "hand", "mono")

    fun label(key: String): String = when (key) {
        "serif" -> "Lora"
        "sans" -> "Sans"
        "hand" -> "Kalam"
        else -> "Typewriter"
    }

    private val typefaces = HashMap<String, Typeface>()
    private val families = HashMap<String, FontFamily>()

    @Synchronized
    fun typeface(context: Context, key: String, bold: Boolean): Typeface =
        typefaces.getOrPut("$key/$bold") {
            runCatching { build(context.applicationContext, key, bold) }
                .getOrElse { Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL) }
        }

    @Synchronized
    fun family(context: Context, key: String, bold: Boolean = false): FontFamily =
        families.getOrPut("$key/$bold") { FontFamily(typeface(context, key, bold)) }

    private fun build(context: Context, key: String, bold: Boolean): Typeface {
        val style = if (bold) Typeface.BOLD else Typeface.NORMAL
        return when (key) {
            "serif" -> serif(context, if (bold) 650 else 400, style)
            "hand" -> Typeface.createFromAsset(context.assets, if (bold) "fonts/kalam_bold.ttf" else "fonts/kalam.ttf")
            "mono" -> Typeface.create(Typeface.MONOSPACE, style)
            else -> Typeface.create(Typeface.SANS_SERIF, style)
        }
    }

    private fun serif(context: Context, weight: Int, style: Int): Typeface {
        val assets = context.assets
        val axis = "'wght' $weight"
        if (Build.VERSION.SDK_INT >= 29) {
            val latin = Font.Builder(assets, "fonts/lora.ttf").setFontVariationSettings(axis).build()
            val deva = Font.Builder(assets, "fonts/noto_serif_devanagari.ttf").setFontVariationSettings(axis).build()
            return Typeface.CustomFallbackBuilder(PlatformFamily.Builder(latin).build())
                .addCustomFallback(PlatformFamily.Builder(deva).build())
                .build()
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return Typeface.Builder(assets, "fonts/lora.ttf").setFontVariationSettings(axis).build()
        }
        return Typeface.create(Typeface.createFromAsset(assets, "fonts/lora.ttf"), style)
    }
}
