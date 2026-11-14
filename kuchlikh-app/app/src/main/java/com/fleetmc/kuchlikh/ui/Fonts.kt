package com.fleetmc.kuchlikh.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.net.Uri
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import android.graphics.fonts.FontFamily as PlatformFamily

object Fonts {

    private const val CUSTOM = "custom:"

    val builtIn = listOf("serif", "sans", "hand", "playfair", "crimson", "caveat", "mono")

    private val typefaces = HashMap<String, Typeface>()
    private val families = HashMap<String, FontFamily>()

    fun label(key: String): String = when (key) {
        "serif" -> "Lora"
        "sans" -> "Sans"
        "hand" -> "Kalam"
        "playfair" -> "Playfair"
        "crimson" -> "Crimson"
        "caveat" -> "Caveat"
        "mono" -> "Typewriter"
        else -> key.removePrefix(CUSTOM).substringBeforeLast('.')
    }

    private fun dir(context: Context): File = File(context.filesDir, "fonts").apply { mkdirs() }

    fun custom(context: Context): List<String> =
        dir(context).listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map { CUSTOM + it.name }

    fun all(context: Context): List<String> = builtIn + custom(context)

    private fun looksLikeFont(file: File): Boolean {
        val head = ByteArray(4)
        val read = file.inputStream().use { it.read(head) }
        if (read < 4) return false
        val tag = String(head, Charsets.ISO_8859_1)
        return tag == "OTTO" || tag == "true" || tag == "ttcf" || (head[0].toInt() == 0 && head[1].toInt() == 1 && head[2].toInt() == 0 && head[3].toInt() == 0)
    }

    @Synchronized
    fun import(context: Context, uri: Uri, displayName: String?): String? {
        val raw = (displayName ?: uri.lastPathSegment ?: "font").substringAfterLast('/')
        val base = raw.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Font" }
        val target = File(dir(context), "$base.ttf")
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } != null
        }.getOrDefault(false)
        if (!ok || !looksLikeFont(target) || runCatching { Typeface.createFromFile(target) }.isFailure) {
            target.delete()
            return null
        }
        forget(CUSTOM + target.name)
        return CUSTOM + target.name
    }

    @Synchronized
    fun remove(context: Context, key: String) {
        if (!key.startsWith(CUSTOM)) return
        File(dir(context), key.removePrefix(CUSTOM)).delete()
        forget(key)
    }

    private fun forget(key: String) {
        typefaces.keys.removeAll { it.startsWith("$key/") }
        families.keys.removeAll { it.startsWith("$key/") }
    }

    @Synchronized
    fun typeface(context: Context, key: String, bold: Boolean, italic: Boolean = false): Typeface =
        typefaces.getOrPut("$key/$bold/$italic") {
            runCatching { build(context.applicationContext, key, bold, italic) }
                .getOrElse { Typeface.create(Typeface.SERIF, style(bold, italic)) }
        }

    @Synchronized
    fun family(context: Context, key: String, bold: Boolean = false, italic: Boolean = false): FontFamily =
        families.getOrPut("$key/$bold/$italic") { FontFamily(typeface(context, key, bold, italic)) }

    private fun style(bold: Boolean, italic: Boolean): Int = when {
        bold && italic -> Typeface.BOLD_ITALIC
        bold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }

    private fun slanted(base: Typeface, bold: Boolean, italic: Boolean): Typeface =
        if (italic) Typeface.create(base, style(bold, true)) else base

    private fun build(context: Context, key: String, bold: Boolean, italic: Boolean): Typeface {
        val assets = context.assets
        val weight = if (bold) 700 else 400
        return when {
            key == "serif" -> serif(context, if (bold) 650 else 400, italic)
            key == "sans" -> Typeface.create(Typeface.SANS_SERIF, style(bold, italic))
            key == "hand" -> slanted(
                Typeface.createFromAsset(assets, if (bold) "fonts/kalam_bold.ttf" else "fonts/kalam.ttf"), false, italic,
            )
            key == "playfair" -> slanted(variable(context, "fonts/playfair.ttf", weight), bold, italic)
            key == "crimson" -> slanted(variable(context, "fonts/crimson.ttf", weight), bold, italic)
            key == "caveat" -> slanted(variable(context, "fonts/caveat.ttf", weight), bold, italic)
            key == "mono" -> slanted(
                Typeface.createFromAsset(assets, if (bold) "fonts/courier_prime_bold.ttf" else "fonts/courier_prime.ttf"), false, italic,
            )
            key.startsWith(CUSTOM) -> {
                val file = File(dir(context), key.removePrefix(CUSTOM))
                val base = Typeface.createFromFile(file)
                if (bold || italic) Typeface.create(base, style(bold, italic)) else base
            }
            else -> serif(context, weight, italic)
        }
    }

    private fun variable(context: Context, asset: String, weight: Int): Typeface {
        if (Build.VERSION.SDK_INT >= 26) {
            return Typeface.Builder(context.assets, asset).setFontVariationSettings("'wght' $weight").build()
        }
        return Typeface.createFromAsset(context.assets, asset)
    }

    private fun serif(context: Context, weight: Int, italic: Boolean): Typeface {
        val assets = context.assets
        val axis = "'wght' $weight"
        val latinAsset = if (italic) "fonts/lora_italic.ttf" else "fonts/lora.ttf"
        if (Build.VERSION.SDK_INT >= 29) {
            val latin = Font.Builder(assets, latinAsset).setFontVariationSettings(axis).build()
            val deva = Font.Builder(assets, "fonts/noto_serif_devanagari.ttf").setFontVariationSettings(axis).build()
            return Typeface.CustomFallbackBuilder(PlatformFamily.Builder(latin).build())
                .addCustomFallback(PlatformFamily.Builder(deva).build())
                .build()
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return Typeface.Builder(assets, latinAsset).setFontVariationSettings(axis).build()
        }
        return Typeface.createFromAsset(assets, latinAsset)
    }
}
