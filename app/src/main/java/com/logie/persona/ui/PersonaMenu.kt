package com.logie.persona.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logie.persona.R
import com.logie.persona.backend.catalog.Game
import com.logie.persona.backend.catalog.Version
import java.io.File
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Each game's menu, after the Persona games' own (the user's reference: Persona 5's pause menu over Persona 3, 20 November 2026): the game's
 * colours with a moving background of its own, a slanted panel on the right with its character in front of it, the menu's words stacked
 * and zig-zagging on the left in the game's lettering, the chosen one lit and moving, and a line of the game's kind at the bottom (the moon's
 * phase for Persona 1 and 2, the date and the time of day for 3, 4 and 5). Tap a word to choose it, tap it again to do it.
 *
 * - Persona 1: violet; the moon with its ring of runes and squares drifting up. Avant Garde lettering (the Final Fantasy XV page's main
 *   font: TeX Gyre Adventor, its free clone), the chosen word on a white band.
 * - Persona 2: crimson and black; a jagged sigil turning, ash falling, light slashing across. The same lettering, the chosen word on gold.
 * - Persona 3: deep blue water with light moving through it and bubbles; a white panel with a clock's rings turning and cracks across it.
 *   Rounded heavy lettering, slanted (SKIP Std B's stand-in: Rounded M+), the chosen word on a cyan band with a sweep of light; BM Space
 *   (the Persona 3 Reload page's) for the date.
 * - Persona 4: yellow; a black TV panel with static and scan lines, fog drifting, hazard stripes. Rounded black lettering, the chosen word
 *   in a black box that jitters like a bad signal.
 * - Persona 5: red; a sunburst turning, halftone dots, a black panel with stars. Every word a ransom note (each letter its own font, box and
 *   tilt), the chosen word in red and white, shaking.
 *
 * The games' own fonts (SKIP Std B, ITC Avant Garde Gothic, FOT-Rodin...) are commercial: a font file the person has can be set per game
 * (Settings, Games: Font) and is used in place of the stand-in. The character is the game's picture ([Portrait]): one the person set
 * (Settings, Games: Character), else the app's (`assets/characters/pN.png`), else a stand-in figure.
 */
@Composable
internal fun PersonaMenu(game: Game, version: Version, state: GameState, onVersion: (Version) -> Unit, modifier: Modifier = Modifier) {
    val style = MenuStyle.of(game.number)
    val fonts = rememberMenuFonts(game.number)
    val time by rememberSeconds()
    var chosen by rememberSaveable(game.id) { mutableStateOf<String?>(null) }
    val index = state.rows.indexOfFirst { it.label == chosen }.takeIf { it >= 0 } ?: state.rows.indexOfFirst { it.usable }.coerceAtLeast(0)
    // The entrance: the panel and the character come in from the right, the words from the left one after another.
    val enter = remember(game.id) { Animatable(0f) }
    LaunchedEffect(game.id) { enter.snapTo(0f); enter.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(style.base)) {
        val w = maxWidth
        val h = maxHeight
        val wide = w > h
        // The words are as large as the height lets them be: the title, the versions and the line at the bottom first.
        val budget = h - (if (game.versions.size > 1) 250.dp else 200.dp) - 70.dp - (if (state.gauge != null || state.notice != null) 50.dp else 0.dp)
        val scale = ((budget / state.rows.size.coerceAtLeast(1)) / (if (style.number == 5) 66.dp else 76.dp)).coerceIn(0.5f, 1f)        // The background, then the slanted panel with its own pattern.
        Canvas(Modifier.fillMaxSize()) { style.background(this, time) }
        Canvas(Modifier.fillMaxSize().graphicsLayer { translationX = (1f - enter.value) * size.width * 0.35f }) {
            val panel = panelPath(size, wide)
            clipPath(panel) { style.panel(this, time) }
            drawPath(panel, style.panelEdge, style = Stroke(width = 6.dp.toPx()))
        }
        // The character, in front of the panel, breathing.
        Portrait(
            game.number, style, time,
            Modifier.align(Alignment.BottomEnd)
                .fillMaxHeight(if (wide) 0.94f else 0.62f)
                .fillMaxWidth(if (wide) 0.5f else 0.62f)
                .padding(end = if (wide) w * 0.04f else 0.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationX = (1f - enter.value) * 120.dp.toPx()
                    translationY = sin(time * 1.3f) * 3.dp.toPx()
                    val s = 1f + 0.006f * sin(time * 1.3f + 0.6f)
                    scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0.5f, 1f)
                },
        )
        // The words.
        Column(
            Modifier.fillMaxHeight().fillMaxWidth(if (wide) 0.62f else 1f).statusBarsPadding().padding(start = 26.dp, top = 18.dp, end = 12.dp, bottom = 18.dp),
        ) {
            Box(Modifier.padding(end = if (wide) 0.dp else 104.dp)) { Title(game, version, style, fonts, time, enter.value) }
            if (game.versions.size > 1) Versions(game, version, style, fonts, onVersion)
            Spacer(Modifier.height(if (wide) 30.dp else 22.dp))
            state.rows.forEachIndexed { i, row ->
                val appear = ((enter.value - 0.12f * i / state.rows.size.coerceAtLeast(1)) / 0.7f).coerceIn(0f, 1f)
                // The zig-zag of the reference: each word a little to the right of the last, then back.
                val zig = listOf(0f, 0.6f, 1f, 0.45f, 0.85f, 0.2f)[i % 6]
                MenuWord(
                    row, i == index, style, fonts, time, i, scale,
                    Modifier.padding(start = (zig * 34f).dp, top = (if (style.number == 5) 4f else 9f).dp * scale, bottom = (if (style.number == 5) 4f else 9f).dp * scale)
                        .graphicsLayer { alpha = appear; translationX = (1f - appear) * -220.dp.toPx() },
                ) { if (i == index) { if (row.usable) state.perform(row.id) } else chosen = row.label }
            }
            Spacer(Modifier.weight(1f))
            state.gauge?.let { g ->
                Text(g.text.uppercase(), style = fonts.small.copy(color = style.ink, fontSize = 15.sp, letterSpacing = 2.sp))
                LinearProgressIndicator(progress = { g.fraction }, color = style.gaugeColor, trackColor = style.ink.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth(0.7f).padding(top = 6.dp, bottom = 10.dp))
            }
            state.notice?.let { Text(it, style = fonts.small.copy(color = style.ink, fontSize = 14.sp), modifier = Modifier.padding(bottom = 10.dp)) }
            Hud(game.number, style, fonts, time)
        }
    }
}

// The clock

/** Seconds since the screen came up, a frame at a time. */
@Composable
private fun rememberSeconds(): State<Float> = produceState(0f) {
    var start = -1L
    while (true) withFrameNanos { now -> if (start < 0) start = now; value = (now - start) / 1_000_000_000f }
}

// The lettering

/** A game's lettering: the words' face, the title's, the small text's, and Persona 5's mix of faces for its ransom notes. */
internal class MenuFonts(val words: TextStyle, val title: TextStyle, val small: TextStyle, val hud: TextStyle, val ransom: List<FontFamily>)

private val Adventor = FontFamily(Font(R.font.persona_adventor_bold, FontWeight.Bold))
private val Rounded = FontFamily(Font(R.font.persona_rounded_extrabold, FontWeight.ExtraBold), Font(R.font.persona_rounded_black, FontWeight.Black))
private val BmSpace = FontFamily(Font(R.font.persona_bmspace))
private val Ransom = listOf(
    FontFamily(Font(R.font.persona_anton)), FontFamily(Font(R.font.persona_archivo_black)), FontFamily(Font(R.font.persona_abril_fatface)),
    FontFamily(Font(R.font.persona_bowlby_one)), FontFamily(Font(R.font.persona_rubik_mono_one)), FontFamily(Font(R.font.persona_bebas_neue)),
)

/** The font file the person set for game [number] (Settings, Games: Font), if any. */
internal fun customFontFile(context: Context, number: Int): File? =
    File(context.filesDir, "fonts").listFiles { f -> f.name.startsWith("p$number.") }?.firstOrNull()

@Composable
private fun rememberMenuFonts(number: Int): MenuFonts {
    val context = LocalContext.current
    val revision by MenuAssets.revision.collectAsState()
    return remember(number, revision) {
        val custom = customFontFile(context, number)?.let { f -> runCatching { FontFamily(Font(f)) }.getOrNull() }
        when (number) {
            1, 2 -> {
                val face = custom ?: Adventor
                MenuFonts(
                    words = TextStyle(fontFamily = face, fontWeight = FontWeight.Bold, fontSize = 44.sp, letterSpacing = 3.sp),
                    title = TextStyle(fontFamily = face, fontWeight = FontWeight.Bold, fontSize = 50.sp, letterSpacing = 6.sp),
                    small = TextStyle(fontFamily = face, fontWeight = FontWeight.Bold),
                    hud = TextStyle(fontFamily = face, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp),
                    ransom = listOf(face),
                )
            }
            3, 4 -> {
                val face = custom ?: Rounded
                val slant = if (number == 3) TextGeometricTransform(skewX = -0.22f) else null
                MenuFonts(
                    words = TextStyle(fontFamily = face, fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = 1.sp, textGeometricTransform = slant),
                    title = TextStyle(fontFamily = face, fontWeight = FontWeight.Black, fontSize = 54.sp, textGeometricTransform = slant),
                    small = TextStyle(fontFamily = face, fontWeight = FontWeight.ExtraBold),
                    hud = if (number == 3) TextStyle(fontFamily = BmSpace, fontSize = 22.sp, letterSpacing = 2.sp)
                        else TextStyle(fontFamily = face, fontWeight = FontWeight.Black, fontSize = 20.sp),
                    ransom = listOf(face),
                )
            }
            else -> MenuFonts(
                words = TextStyle(fontFamily = custom ?: Ransom[1], fontSize = 40.sp),
                title = TextStyle(fontFamily = custom ?: Ransom[0], fontSize = 56.sp),
                small = TextStyle(fontFamily = custom ?: Ransom[1]),
                hud = TextStyle(fontFamily = custom ?: Ransom[0], fontSize = 26.sp),
                ransom = listOfNotNull(custom) + Ransom,
            )
        }
    }
}

/** What the person set changes ([revision]): the screens read their fonts and pictures again. */
internal object MenuAssets {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun changed() { revision.value++ }
}

// The title and the versions

@Composable
private fun Title(game: Game, version: Version, style: MenuStyle, fonts: MenuFonts, time: Float, enter: Float) {
    val subtitle = game.title.removePrefix("Persona ${game.number}").removePrefix(":").trim().ifEmpty { if (game.versions.size > 1) "" else version.label }
    Column(Modifier.padding(top = 14.dp).graphicsLayer { alpha = enter; translationY = (1f - enter) * -40.dp.toPx() }) {
        if (style.number == 5) {
            RansomText("PERSONA 5", fonts, 0, false, time, height = 58.dp)
        } else {
            Text("PERSONA ${game.number}", style = fonts.title.copy(color = style.ink, shadow = Shadow(style.shadow, Offset(4f, 4f), 0f)),
                modifier = Modifier.graphicsLayer { rotationZ = -4f; transformOrigin = TransformOrigin(0f, 0.5f) })
        }
        if (subtitle.isNotEmpty()) Text(subtitle.uppercase(), style = fonts.small.copy(color = style.accentInk, fontSize = 17.sp, letterSpacing = 4.sp),
            modifier = Modifier.padding(start = 6.dp, top = 2.dp))
    }
}

@Composable
private fun Versions(game: Game, version: Version, style: MenuStyle, fonts: MenuFonts, onVersion: (Version) -> Unit) {
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (v in game.versions) {
            val on = v == version
            Text(v.label.uppercase(), style = fonts.small.copy(color = if (on) style.bandInk else style.ink, fontSize = 15.sp, letterSpacing = 2.sp),
                modifier = Modifier.graphicsLayer { rotationZ = -4f }
                    .drawBehind {
                        val skew = size.height * 0.3f
                        val p = Path().apply { moveTo(skew, 0f); lineTo(size.width, 0f); lineTo(size.width - skew, size.height); lineTo(0f, size.height); close() }
                        drawPath(p, if (on) style.band else style.ink.copy(alpha = 0.12f))
                    }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onVersion(v) }
                    .padding(horizontal = 16.dp, vertical = 7.dp))
        }
    }
}

// A word of the menu

@Composable
private fun MenuWord(row: MenuRow, on: Boolean, style: MenuStyle, fonts: MenuFonts, time: Float, index: Int, scale: Float, modifier: Modifier, onClick: () -> Unit) {
    val lit by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "lit")
    val dim = if (row.usable) 1f else 0.38f
    // The chosen word leans out a little further and sways.
    val sway = if (on) sin(time * 3.2f) * 1.2f else 0f
    val tilt = (if (index % 2 == 0) -5f else -2.5f) + sway
    Box(
        modifier
            .graphicsLayer { rotationZ = tilt; val s = 1f + 0.08f * lit; scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f); translationX = 14.dp.toPx() * lit }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        if (style.number == 5) {
            RansomText(row.label, fonts, index, on, time, height = 50.dp * scale, alpha = dim)
        } else {
            val jitter = if (on && style.number == 4) (if (floor(time * 9f).toInt() % 7 == 0) 3.dp else 0.dp) else 0.dp
            Text(
                row.label,
                style = fonts.words.copy(fontSize = fonts.words.fontSize * scale, color = (if (on) style.bandInk else style.ink).copy(alpha = dim), shadow = if (on) null else Shadow(style.shadow.copy(alpha = 0.6f * dim), Offset(3f, 3f), 0f)),
                modifier = Modifier.offset(x = jitter)
                    .drawBehind { if (lit > 0.01f) style.highlight(this, lit, time) }
                    .padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * A word as a ransom note (Persona 5's menus): each letter cut from somewhere else, its own face, box, size and tilt; chosen, the letters
 * turn red and white and shake. Each letter's look is fixed by the word and its place, so the word looks the same every time it is drawn.
 */
@Composable
private fun RansomText(text: String, fonts: MenuFonts, salt: Int, on: Boolean, time: Float, height: Dp, alpha: Float = 1f) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.graphicsLayer { this.alpha = alpha }) {
        text.forEachIndexed { i, ch ->
            if (ch == ' ') { Spacer(Modifier.width(height * 0.3f)); return@forEachIndexed }
            val seed = (text.hashCode() * 31 + i * 7919 + salt * 104729).let { it xor (it ushr 13) }
            fun pick(n: Int, shift: Int) = abs((seed ushr shift) % n)
            val face = fonts.ransom[pick(fonts.ransom.size, 1)]
            val scheme = pick(4, 5)
            val (paper, ink) = when {
                on -> if (i % 2 == 0) Color(0xFFE5112F) to Color.White else Color.White to Color(0xFFE5112F)
                scheme == 0 -> Color.Black to Color.White
                scheme == 1 -> Color.White to Color.Black
                scheme == 2 -> Color(0xFFE5112F) to Color.White
                else -> Color.Transparent to Color.White
            }
            val size = (0.82f + pick(5, 9) * 0.08f)
            val baseTilt = (pick(9, 11) - 4) * 2.2f
            val shake = if (on) sin(time * 18f + i) * 2.5f else 0f
            val letter = if (i == 0 || pick(3, 15) == 0) ch.uppercaseChar() else ch.lowercaseChar()
            Box(
                Modifier.padding(horizontal = 1.dp)
                    .graphicsLayer { rotationZ = baseTilt + shake; translationY = (pick(5, 17) - 2) * 1.5f }
                    .background(paper)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter.toString(), style = TextStyle(fontFamily = face, fontSize = (height.value * 0.72f * size).sp, color = ink,
                    shadow = if (paper == Color.Transparent) Shadow(Color.Black, Offset(3f, 3f), 0f) else null))
            }
        }
    }
}

// The line at the bottom

@Composable
private fun Hud(number: Int, style: MenuStyle, fonts: MenuFonts, time: Float) {
    val now = remember(floor(time / 30f)) { Calendar.getInstance() }
    val day = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[now.get(Calendar.DAY_OF_WEEK) - 1]
    val date = "${now.get(Calendar.MONTH) + 1}/${now.get(Calendar.DAY_OF_MONTH)}"
    when (number) {
        1, 2 -> Row(verticalAlignment = Alignment.CenterVertically) {
            val phase = moonPhase(now.timeInMillis)
            Canvas(Modifier.size(30.dp)) { drawMoon(phase, style.ink) }
            Spacer(Modifier.width(10.dp))
            Text(moonName(phase).uppercase(), style = fonts.hud.copy(color = style.ink))
        }
        3 -> Row(verticalAlignment = Alignment.Bottom) {
            Text(date, style = fonts.hud.copy(color = style.ink, fontSize = 30.sp))
            Spacer(Modifier.width(8.dp))
            Text(day, style = fonts.hud.copy(color = style.accentInk))
            Spacer(Modifier.width(14.dp))
            Text(timeOfDay(now.get(Calendar.HOUR_OF_DAY)).uppercase(), style = fonts.hud.copy(color = style.ink, fontSize = 16.sp))
        }
        4 -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$date ($day)", style = fonts.hud.copy(color = Color(0xFFF6C623)), modifier = Modifier.background(Color(0xFF16120A)).padding(horizontal = 10.dp, vertical = 2.dp))
            Spacer(Modifier.width(10.dp))
            Text(timeOfDay(now.get(Calendar.HOUR_OF_DAY)).uppercase(), style = fonts.hud.copy(color = style.ink, fontSize = 16.sp))
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            RansomText(date, fonts, 3, false, time, height = 40.dp)
            Spacer(Modifier.width(6.dp))
            RansomText(day.take(2), fonts, 5, false, time, height = 30.dp)
        }
    }
}

private fun timeOfDay(hour: Int) = when (hour) {
    0 -> "Dark Hour"
    in 1..5 -> "Late Night"
    in 6..8 -> "Early Morning"
    in 9..11 -> "Morning"
    in 12..14 -> "Daytime"
    in 15..17 -> "After School"
    in 18..21 -> "Evening"
    else -> "Night"
}

/** The moon's age as a fraction of its month (0 new, 0.5 full), from a known new moon (6 January 2000, 18:14 UTC). */
internal fun moonPhase(millis: Long): Float {
    val days = (millis - 947_182_440_000L) / 86_400_000.0
    val f = (days / 29.530588853) % 1.0
    return (if (f < 0) f + 1.0 else f).toFloat()
}

/** Persona 1 and 2 count the moon in eighths, from new to full and back. */
internal fun moonName(phase: Float): String {
    val eighth = ((phase * 16 + 1).toInt() / 2) % 8
    return when (eighth) { 0 -> "New Moon"; 4 -> "Full Moon"; in 1..3 -> "${eighth}/8 Moon"; else -> "${8 - eighth}/8 Moon" }
}

private fun DrawScope.drawMoon(phase: Float, color: Color) {
    val r = size.minDimension / 2
    drawCircle(color.copy(alpha = 0.18f), r)
    // The lit part: the disc, with the shadow's ellipse taken off.
    val lit = Path().apply {
        val c = center
        val k = cos(phase * 2 * PI).toFloat() // 1 new, -1 full
        val waxing = phase < 0.5f
        addArc(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r), if (waxing) -90f else 90f, 180f)
        val ex = r * abs(k)
        arcTo(androidx.compose.ui.geometry.Rect(c.x - ex, c.y - r, c.x + ex, c.y + r), if (waxing) 90f else -90f, if ((k > 0) == waxing) -180f else 180f, false)
        close()
    }
    drawPath(lit, color)
}

// The character

/** Game [number]'s character: the person's picture, the app's, else a stand-in figure in the game's colours. */
@Composable
private fun Portrait(number: Int, style: MenuStyle, time: Float, modifier: Modifier) {
    val context = LocalContext.current
    val revision by MenuAssets.revision.collectAsState()
    val picture: ImageBitmap? = remember(number, revision) { characterBitmap(context, number) }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        if (picture != null) {
            // An outline of the panel's edge colour behind the picture, as the games cut their characters out.
            Image(picture, null, contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(style.outline),
                modifier = Modifier.fillMaxSize().graphicsLayer { translationX = 6.dp.toPx(); translationY = -2.dp.toPx() })
            Image(picture, "Character", contentScale = ContentScale.Fit, alignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize())
        } else Canvas(Modifier.fillMaxSize()) { standIn(style, time) }
    }
}

/** The character's picture: the person's (Settings, Games: Character), else the app's own (`assets/characters/pN.png`). */
internal fun characterFile(context: Context, number: Int): File? = File(context.filesDir, "art/character-p$number.png").takeIf { it.isFile }

private fun characterBitmap(context: Context, number: Int): ImageBitmap? = runCatching {
    characterFile(context, number)?.let { BitmapFactory.decodeFile(it.path) }
        ?: context.assets.open("characters/p$number.png").use { BitmapFactory.decodeStream(it) }
}.getOrNull()?.asImageBitmap()

/** A stand-in figure until the game's picture is in: head, hair, collar, coat, in the game's colours, cut out with a white edge. */
private fun DrawScope.standIn(style: MenuStyle, time: Float) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val headR = h * 0.085f
    val headY = h * 0.2f
    val body = Path().apply {
        moveTo(cx - headR * 0.55f, headY + headR * 0.85f)
        lineTo(cx + headR * 0.55f, headY + headR * 0.85f)
        lineTo(cx + headR * 0.75f, headY + headR * 1.6f)
        cubicTo(cx + headR * 2.6f, headY + headR * 1.9f, cx + headR * 3.0f, headY + headR * 2.5f, cx + headR * 3.1f, h * 0.62f)
        lineTo(cx + headR * 2.6f, h)
        lineTo(cx - headR * 2.6f, h)
        lineTo(cx - headR * 3.1f, h * 0.62f)
        cubicTo(cx - headR * 3.0f, headY + headR * 2.5f, cx - headR * 2.6f, headY + headR * 1.9f, cx - headR * 0.75f, headY + headR * 1.6f)
        close()
    }
    val edge = 5.dp.toPx()
    // The white cut-out edge, then the figure.
    drawPath(body, style.outline, style = Stroke(edge * 2))
    drawCircle(style.outline, headR + edge, Offset(cx, headY))
    drawPath(body, style.figure)
    // The coat's opening and a collar.
    drawPath(Path().apply { moveTo(cx - headR * 0.75f, headY + headR * 1.6f); lineTo(cx, h * 0.55f); lineTo(cx + headR * 0.75f, headY + headR * 1.6f); close() }, style.outline)
    drawLine(style.figure, Offset(cx, headY + headR * 1.7f), Offset(cx, h * 0.5f), strokeWidth = edge * 1.6f, cap = StrokeCap.Round)
    drawCircle(style.figureLight, headR, Offset(cx, headY))
    // Hair, falling over one eye as the games' heroes wear it, moving a little.
    val sway = sin(time * 1.1f) * headR * 0.04f
    drawPath(Path().apply {
        moveTo(cx - headR * 1.05f, headY + headR * 0.2f)
        cubicTo(cx - headR * 1.1f, headY - headR * 1.4f, cx + headR * 1.2f, headY - headR * 1.4f, cx + headR * 1.05f, headY + headR * 0.1f)
        lineTo(cx + headR * 0.2f + sway, headY + headR * 0.9f)
        lineTo(cx - headR * 0.1f + sway, headY - headR * 0.1f)
        lineTo(cx - headR * 0.75f, headY + headR * 0.75f)
        close()
    }, style.figure)
    // Halftone shading down one side.
    val step = 9.dp.toPx()
    var y = h * 0.35f
    while (y < h) {
        var x = cx + headR * 0.9f
        while (x < cx + headR * 3f) {
            val r = step * 0.28f * ((x - cx) / (headR * 3f)).coerceIn(0f, 1f)
            if (r > 0.5f) drawCircle(style.outline.copy(alpha = 0.35f), r, Offset(x, y))
            x += step
        }
        y += step
    }
}

// The slanted panel

private fun panelPath(size: Size, wide: Boolean): Path = Path().apply {
    val w = size.width
    val h = size.height
    if (wide) { moveTo(w * 0.68f, 0f); lineTo(w, 0f); lineTo(w, h); lineTo(w * 0.47f, h); close() }
    else { moveTo(w * 0.62f, h * 0.30f); lineTo(w, h * 0.22f); lineTo(w, h); lineTo(w * 0.40f, h); close() }
}

// Each game's look

internal class MenuStyle(
    val number: Int,
    val base: Color,
    /** The words, the title. */
    val ink: Color,
    /** The subtitle, the day. */
    val accentInk: Color,
    /** The chosen word's band and the word on it. */
    val band: Color,
    val bandInk: Color,
    val shadow: Color,
    val panelEdge: Color,
    val outline: Color,
    val figure: Color,
    val figureLight: Color,
    val gaugeColor: Color,
) {
    /** The background behind everything. */
    fun background(scope: DrawScope, t: Float) = with(scope) {
        when (number) {
            1 -> moonBackground(t)
            2 -> sigilBackground(t)
            3 -> waterBackground(t)
            4 -> tvBackground(t)
            else -> sunburstBackground(t)
        }
    }

    /** The slanted panel's own pattern. */
    fun panel(scope: DrawScope, t: Float) = with(scope) {
        when (number) {
            1 -> { drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE6FF), Color(0xFFB9A6E8)))); rings(t, Color(0xFF3A1C5A), center = Offset(size.width * 0.82f, size.height * 0.34f)) }
            2 -> { drawRect(Color(0xFF0B0405)); embers(t) }
            3 -> { drawRect(Color(0xFFF4F7FA)); clock(t); cracks(Color(0xFF111418)) }
            4 -> tvPanel(t)
            else -> { drawRect(Color(0xFF0A0A0A)); stars(t) }
        }
    }

    /** Behind the chosen word: [lit] from 0 to 1 as it is chosen. */
    fun highlight(scope: DrawScope, lit: Float, t: Float) = with(scope) {
        val skew = size.height * 0.28f
        val w = size.width * lit
        val band = Path().apply { moveTo(skew, 0f); lineTo(w, 0f); lineTo(w - skew, size.height); lineTo(0f, size.height); close() }
        when (number) {
            3 -> {
                drawPath(band, this@MenuStyle.band)
                // A sweep of light along the band.
                val x = ((t * 0.6f) % 1f) * size.width * 1.6f - size.width * 0.3f
                clipPath(band) { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), x - 60f, x + 60f)) }
            }
            4 -> {
                drawPath(band, this@MenuStyle.band)
                clipPath(band) { var y = 0f; while (y < size.height) { drawRect(Color.White.copy(alpha = 0.07f), Offset(0f, y), Size(size.width, 2f)); y += 5f } }
            }
            2 -> { drawPath(band, this@MenuStyle.band); drawLine(Color(0xFF6B1F2A), Offset(-10f, size.height * 0.85f), Offset(w + 10f, size.height * 0.7f), strokeWidth = 4f) }
            else -> {
                drawPath(band, this@MenuStyle.band)
                drawCircle(Color(0xFF9B6BFF), size.height * 0.16f, Offset(w + size.height * 0.1f, size.height * 0.5f))
            }
        }
    }

    companion object {
        fun of(number: Int): MenuStyle = when (number) {
            1 -> MenuStyle(1, Color(0xFF241238), Color.White, Color(0xFFC9B8FF), Color.White, Color(0xFF3A1C5A), Color(0xFF0E0618), Color.White, Color.White, Color(0xFF1B0E2C), Color(0xFFD9CCF5), Color(0xFFC9B8FF))
            2 -> MenuStyle(2, Color(0xFF3A0E15), Color(0xFFF4E9E4), Color(0xFFE0B04A), Color(0xFFE0B04A), Color(0xFF3A0E15), Color.Black, Color(0xFFE0B04A), Color(0xFFF4E9E4), Color(0xFF14050A), Color(0xFFF1D9CF), Color(0xFFE0B04A))
            3 -> MenuStyle(3, Color(0xFF0A2C5E), Color.White, Color(0xFF8FD3FF), Color(0xFF29B6F6), Color.White, Color(0xFF041226), Color(0xFF111418), Color.White, Color(0xFF0B1220), Color(0xFFE8EEF5), Color(0xFF8FD3FF))
            4 -> MenuStyle(4, Color(0xFFF6C623), Color(0xFF16120A), Color(0xFF16120A), Color(0xFF16120A), Color(0xFFF6C623), Color(0xFFFFE27A), Color(0xFF16120A), Color.White, Color(0xFF16120A), Color(0xFFF7E7B0), Color(0xFF16120A))
            else -> MenuStyle(5, Color(0xFFD7102C), Color.White, Color.White, Color.White, Color(0xFFD7102C), Color.Black, Color.Black, Color.White, Color(0xFF0A0A0A), Color(0xFFF2F2F2), Color.White)
        }
    }
}

// The backgrounds

/** Persona 1: violet night, the moon with a glow, a ring of runes turning, squares drifting up, stars. */
private fun DrawScope.moonBackground(t: Float) {
    drawRect(Brush.radialGradient(listOf(Color(0xFF4A2475), Color(0xFF1A0B2B)), center = Offset(size.width * 0.3f, size.height * 0.3f), radius = size.maxDimension))
    val moon = Offset(size.width * 0.36f, size.height * 0.28f + sin(t * 0.4f) * 6f)
    val r = size.minDimension * 0.16f
    drawCircle(Brush.radialGradient(listOf(Color(0x66C9B8FF), Color.Transparent), center = moon, radius = r * 2.6f), r * 2.6f, moon)
    drawCircle(Color(0xFFEDE6FF).copy(alpha = 0.22f), r, moon)
    rings(t, Color.White.copy(alpha = 0.35f), moon, r * 1.35f)
    for (i in 0 until 22) {
        val sx = ((i * 0.6180339f) % 1f) * size.width
        val speed = 0.02f + (i % 5) * 0.006f
        val y = size.height - ((t * speed + i * 0.137f) % 1f) * (size.height + 80f)
        val s = 10f + (i % 4) * 7f
        rotate(t * 20f + i * 30f, Offset(sx, y)) { drawRect(Color.White.copy(alpha = 0.14f + (i % 3) * 0.05f), Offset(sx - s / 2, y - s / 2), Size(s, s), style = Stroke(2f)) }
    }
    stars(t, Color(0xFFEDE6FF), 40)
}

/** Persona 2: crimson dark, a jagged sigil turning, slashes of light, ash falling. */
private fun DrawScope.sigilBackground(t: Float) {
    drawRect(Brush.linearGradient(listOf(Color(0xFF6B1F2A), Color(0xFF14050A)), Offset.Zero, Offset(size.width, size.height)))
    val c = Offset(size.width * 0.3f, size.height * 0.45f)
    val r = size.minDimension * 0.42f
    rotate(t * 6f, c) {
        val star = Path()
        for (k in 0 until 24) {
            val a = k * PI.toFloat() / 12
            val rr = if (k % 2 == 0) r else r * 0.72f
            val p = Offset(c.x + cos(a) * rr, c.y + sin(a) * rr)
            if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        star.close()
        drawPath(star, Color(0xFFE0B04A).copy(alpha = 0.10f))
        drawPath(star, Color(0xFFE0B04A).copy(alpha = 0.25f), style = Stroke(3f))
    }
    for (k in 0 until 3) {
        val x = ((t * 0.12f + k * 0.37f) % 1.4f - 0.2f) * size.width
        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x - size.height * 0.5f, size.height), strokeWidth = 40f + k * 30f)
    }
    for (i in 0 until 50) {
        val x = ((i * 0.381966f) % 1f) * size.width + sin(t * 0.7f + i) * 18f
        val y = ((t * (0.03f + (i % 4) * 0.01f) + i * 0.113f) % 1f) * size.height
        drawCircle(Color(0xFFB8A9A6).copy(alpha = 0.35f), 1.5f + (i % 3), Offset(x, y))
    }
}

/** Persona 3: deep water, light moving through it in waves, bubbles rising. */
private fun DrawScope.waterBackground(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1F63B8), Color(0xFF062044))))
    for (k in 0 until 7) {
        val path = Path()
        val baseY = size.height * (0.08f + k * 0.14f)
        var x = 0f
        path.moveTo(0f, baseY)
        while (x <= size.width) {
            path.lineTo(x, baseY + sin(x / 90f + t * (0.8f + k * 0.15f) + k) * 14f + sin(x / 37f - t * 1.3f) * 5f)
            x += 12f
        }
        drawPath(path, Color.White.copy(alpha = 0.05f + (k % 2) * 0.03f), style = Stroke(10f + k * 3f))
    }
    for (i in 0 until 28) {
        val x = ((i * 0.618f) % 1f) * size.width + sin(t * 1.4f + i) * 10f
        val y = size.height - ((t * (0.05f + (i % 5) * 0.015f) + i * 0.071f) % 1f) * size.height
        drawCircle(Color.White.copy(alpha = 0.22f), 2f + (i % 4) * 1.5f, Offset(x, y), style = Stroke(1.5f))
    }
    // Light from above.
    for (k in 0 until 5) {
        val x = size.width * (0.1f + k * 0.18f) + sin(t * 0.3f + k) * 30f
        drawLine(Color.White.copy(alpha = 0.04f), Offset(x, 0f), Offset(x + 160f, size.height), strokeWidth = 50f)
    }
}

/** Persona 4: yellow, fog drifting, hazard stripes running along the top and the bottom. */
private fun DrawScope.tvBackground(t: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFFF6C623), Color(0xFFE09A12))))
    for (i in 0 until 9) {
        val x = ((t * (0.01f + i * 0.002f) + i * 0.21f) % 1.4f - 0.2f) * size.width
        val y = size.height * (0.15f + (i % 5) * 0.18f)
        drawOval(Color.White.copy(alpha = 0.12f), Offset(x - 220f, y - 70f), Size(440f, 140f))
    }
    hazard(t, 0f)
    hazard(-t, size.height - 26f)
}

private fun DrawScope.hazard(t: Float, y: Float) {
    val band = 26f
    drawRect(Color(0xFF16120A), Offset(0f, y), Size(size.width, band))
    val step = 40f
    var x = -step * 2 + (t * 40f) % step
    while (x < size.width + step) {
        drawPath(Path().apply { moveTo(x, y + band); lineTo(x + step / 2, y + band); lineTo(x + step, y); lineTo(x + step / 2, y); close() }, Color(0xFFF6C623))
        x += step
    }
}

/** Persona 5: red, a sunburst turning, halftone dots. */
private fun DrawScope.sunburstBackground(t: Float) {
    drawRect(Color(0xFFD7102C))
    val c = Offset(size.width * 0.28f, size.height * 0.4f)
    val r = size.maxDimension * 1.2f
    rotate(t * 4f, c) {
        for (k in 0 until 18) {
            val a0 = k * 2 * PI.toFloat() / 18
            val a1 = a0 + PI.toFloat() / 18
            drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + cos(a0) * r, c.y + sin(a0) * r); lineTo(c.x + cos(a1) * r, c.y + sin(a1) * r); close() }, Color(0xFFA10B21))
        }
    }
    val step = 22f
    var y = (t * 6f) % step
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            val f = (x / size.width).coerceIn(0f, 1f)
            drawCircle(Color.Black.copy(alpha = 0.22f), 1f + 5f * f * f, Offset(x + ((y / step).toInt() % 2) * step / 2, y))
            x += step
        }
        y += step
    }
}

// Patterns

private fun DrawScope.rings(t: Float, color: Color, center: Offset = this.center, radius: Float = size.minDimension * 0.42f) {
    for (k in 0 until 3) {
        val r = radius * (1f + k * 0.18f)
        drawCircle(color, r, center, style = Stroke(1.5f + k))
        rotate(t * (8f - k * 5f), center) {
            for (i in 0 until 24) {
                val a = i * 2 * PI.toFloat() / 24
                val inner = if (i % 3 == 0) r * 0.92f else r * 0.96f
                drawLine(color, Offset(center.x + cos(a) * inner, center.y + sin(a) * inner), Offset(center.x + cos(a) * r, center.y + sin(a) * r), strokeWidth = 2f)
            }
        }
    }
}

/** A clock's face over the panel (Persona 3 Reload's), its rings turning, its hands moving. */
private fun DrawScope.clock(t: Float) {
    val c = Offset(size.width * 0.86f, size.height * 0.42f)
    val r = size.minDimension * 0.46f
    val ink = Color(0xFF111418)
    for (k in 0 until 3) drawCircle(ink.copy(alpha = 0.55f - k * 0.12f), r * (1f - k * 0.16f), c, style = Stroke(10f - k * 3f))
    rotate(t * 3f, c) {
        for (i in 0 until 12) {
            val a = i * 2 * PI.toFloat() / 12
            val p0 = Offset(c.x + cos(a) * r * 0.78f, c.y + sin(a) * r * 0.78f)
            val p1 = Offset(c.x + cos(a) * r * 0.9f, c.y + sin(a) * r * 0.9f)
            drawLine(ink.copy(alpha = 0.6f), p0, p1, strokeWidth = if (i % 3 == 0) 14f else 6f)
        }
    }
    val sec = (t % 60f) / 60f * 2 * PI.toFloat() - PI.toFloat() / 2
    val min = (t / 3600f) * 2 * PI.toFloat() - PI.toFloat() / 2
    drawLine(ink, c, Offset(c.x + cos(min) * r * 0.55f, c.y + sin(min) * r * 0.55f), strokeWidth = 14f, cap = StrokeCap.Round)
    drawLine(ink.copy(alpha = 0.8f), c, Offset(c.x + cos(sec) * r * 0.72f, c.y + sin(sec) * r * 0.72f), strokeWidth = 4f, cap = StrokeCap.Round)
}

/** Cracks across the panel, from two points, always the same. */
private fun DrawScope.cracks(color: Color) {
    val random = java.util.Random(11)
    for (origin in listOf(Offset(size.width * 0.75f, size.height * 0.2f), Offset(size.width * 0.62f, size.height * 0.75f))) {
        repeat(9) {
            var p = origin
            var a = random.nextFloat() * 2 * PI.toFloat()
            val path = Path().apply { moveTo(p.x, p.y) }
            repeat(7) {
                a += (random.nextFloat() - 0.5f) * 0.9f
                val len = 30f + random.nextFloat() * 90f
                p = Offset(p.x + cos(a) * len, p.y + sin(a) * len)
                path.lineTo(p.x, p.y)
            }
            drawPath(path, color.copy(alpha = 0.8f), style = Stroke(2f + random.nextFloat() * 3f))
        }
    }
}

/** Persona 4's TV: black, static, scan lines, a flicker, now and then a rolling band. */
private fun DrawScope.tvPanel(t: Float) {
    drawRect(Color(0xFF0E0C08))
    val frame = floor(t * 24f).toInt()
    val random = java.util.Random(frame.toLong())
    repeat(900) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height
        drawRect(Color.White.copy(alpha = 0.10f + random.nextFloat() * 0.25f), Offset(x, y), Size(3f, 2f))
    }
    var y = 0f
    while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Size(size.width, 2f)); y += 6f }
    val band = ((t * 0.25f) % 1f) * (size.height + 200f) - 100f
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent), band - 60f, band + 60f), Offset(0f, band - 60f), Size(size.width, 120f))
    drawRect(Color(0xFFF6C623).copy(alpha = 0.05f + 0.03f * sin(t * 30f)))
}

private fun DrawScope.stars(t: Float, color: Color = Color.White, count: Int = 26) {
    for (i in 0 until count) {
        val x = ((i * 0.754877f) % 1f) * size.width
        val y = ((i * 0.569840f) % 1f) * size.height
        val twinkle = 0.4f + 0.6f * ((sin(t * (1.5f + i % 4) + i) + 1f) / 2f)
        val s = 3f + (i % 4) * 2f
        val star = Path().apply {
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4
                val rr = if (k % 2 == 0) s * 2 else s * 0.5f
                if (k == 0) moveTo(x + cos(a) * rr, y + sin(a) * rr) else lineTo(x + cos(a) * rr, y + sin(a) * rr)
            }
            close()
        }
        drawPath(star, color.copy(alpha = 0.5f * twinkle))
    }
}

private fun DrawScope.embers(t: Float) {
    for (i in 0 until 40) {
        val x = ((i * 0.381966f) % 1f) * size.width + sin(t + i) * 12f
        val y = size.height - ((t * (0.04f + (i % 5) * 0.012f) + i * 0.09f) % 1f) * size.height
        drawCircle(Color(0xFFE0B04A).copy(alpha = 0.25f + (i % 3) * 0.15f), 1.5f + (i % 3), Offset(x, y))
    }
}

@Suppress("unused")
private fun DrawScope.shifted(dx: Float, block: DrawScope.() -> Unit) = translate(dx, 0f) { block() }
