package com.sirak.liftlog.wear.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

private val Bg = Color(0xFF0F1115)
private val Card = Color(0xFF1A1D23)
private val CardAlt = Color(0xFF22262E)
private val Border = Color(0xFF2C313A)
private val Success = Color(0xFF22C55E)
private val Danger = Color(0xFFEF4444)
private val DangerBg = Color(0xFF3F1D1D)
val Warning = Color(0xFFF59E0B)
val RecordGold = Color(0xFFFACC15)

/** The three colours chosen on the phone: text, header, and accent. */
object AppPalette {
    var text by mutableStateOf(Color(0xFFF3F4F6))
    var header by mutableStateOf(Bg)
    var accent by mutableStateOf(Color(0xFF3B82F6))

    fun apply(textHex: String?, headerHex: String?, accentHex: String?) {
        parseHex(textHex)?.let { text = it }
        parseHex(headerHex)?.let { header = it }
        parseHex(accentHex)?.let { accent = it }
    }
}

fun loadPalette(context: Context) {
    val file = File(context.filesDir, "theme.json")
    if (!file.exists()) return
    runCatching {
        val obj = JSONObject(file.readText())
        AppPalette.apply(obj.optString("text"), obj.optString("header"), obj.optString("accent"))
    }
}

fun savePalette(context: Context) {
    val json = JSONObject()
        .put("text", AppPalette.text.toHex())
        .put("header", AppPalette.header.toHex())
        .put("accent", AppPalette.accent.toHex())
        .toString()
    File(context.filesDir, "theme.json").writeText(json)
}

@Composable
fun LiftLogTheme(content: @Composable () -> Unit) {
    val text = AppPalette.text
    val accent = AppPalette.accent
    val header = AppPalette.header
    MaterialTheme(
        colors = Colors(
            primary = accent,
            primaryVariant = mix(accent, header, 0.72f),
            secondary = mix(CardAlt, header, 0.55f),
            secondaryVariant = mix(Border, header, 0.4f),
            background = header,
            surface = mix(Card, header, 0.65f),
            error = Danger,
            onPrimary = onColor(accent),
            onSecondary = text,
            onBackground = text,
            onSurface = text,
            onSurfaceVariant = mix(text, header, 0.38f),
            onError = Color.White,
        ),
        content = content,
    )
}

@Composable
fun successChipColors() = ChipDefaults.primaryChipColors(
    backgroundColor = Success,
    contentColor = Color.White,
)

@Composable
fun dangerChipColors() = ChipDefaults.primaryChipColors(
    backgroundColor = DangerBg,
    contentColor = Danger,
)

private fun onColor(color: Color): Color {
    val lin = listOf(color.red, color.green, color.blue).map { channel ->
        if (channel <= 0.03928f) channel / 12.92f else Math.pow(((channel + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    val luminance = 0.2126f * lin[0] + 0.7152f * lin[1] + 0.0722f * lin[2]
    return if (luminance > 0.45f) Color(0xFF111827) else Color.White
}

private fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

private fun parseHex(input: String?): Color? {
    val raw = input?.trim()?.removePrefix("#") ?: return null
    val hex = when (raw.length) {
        3 -> raw.map { "$it$it" }.joinToString("")
        6 -> raw
        else -> return null
    }
    val rgb = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or rgb)
}

private fun Color.toHex(): String {
    val part = { channel: Float -> (channel * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0') }
    return "#${part(red)}${part(green)}${part(blue)}"
}
