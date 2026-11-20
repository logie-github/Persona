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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
 * - Persona 3 (Reload's blue): under water at night, light falling in shafts from the surface, bubbles and specks rising, the big moon behind
 *   the character with the month's eight phases turning around it (tonight's lit); a glass panel with a glowing edge and a glint crossing it.
 *   Rounded heavy lettering, slanted (SKIP Std B's stand-in: Rounded M+), the words stepping down to the right; the chosen word on a white-to-cyan
 *   band with its echo rippling out and a pointer; the character cut out in white with a cyan glow. BM Space (the Persona 3 Reload page's) for
 *   the moon, the date, the day and the time of day.
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
internal fun PersonaMenu(
    game: Game, version: Version, state: GameState, onVersion: (Version) -> Unit, modifier: Modifier = Modifier,
    onHoldTitle: () -> Unit = {}, onHoldCharacter: () -> Unit = {},
) {
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
            style.edge(this, panel)
        }
        // The character, in front of the panel, breathing.
        Portrait(
            game.number, style, time, onHoldCharacter,
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
            // The title on one line: smaller where the screen is narrow (the buttons at the top right take some of it).
            val titleScale = (((if (wide) w * 0.62f else w) - 142.dp) / 330.dp).coerceIn(0.6f, 1f)
            Box(Modifier.padding(end = if (wide) 0.dp else 104.dp)) { Title(game, version, style, fonts, time, enter.value, titleScale, onHoldTitle) }
            if (game.versions.size > 1) Versions(game, version, style, fonts, onVersion)
            Spacer(Modifier.height(if (wide) 30.dp else 22.dp))
            state.rows.forEachIndexed { i, row ->
                val appear = ((enter.value - 0.12f * i / state.rows.size.coerceAtLeast(1)) / 0.7f).coerceIn(0f, 1f)
                // The zig-zag of the reference: each word a little to the right of the last, then back.
                // Persona 3's step down to the right, as its menus run.
                val zig = if (style.number == 3) i * (if (wide) 0.8f else 0.45f) else listOf(0f, 0.6f, 1f, 0.45f, 0.85f, 0.2f)[i % 6]
                MenuWord(
                    row, i == index, style, fonts, time, i, scale,
                    Modifier.padding(start = (zig * 34f).dp * (if (style.number == 3) scale else 1f), top = (if (style.number == 5) 4f else 9f).dp * scale, bottom = (if (style.number == 5) 4f else 9f).dp * scale)
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
private fun Title(game: Game, version: Version, style: MenuStyle, fonts: MenuFonts, time: Float, enter: Float, scale: Float, onHold: () -> Unit) {
    val subtitle = game.title.removePrefix("Persona ${game.number}").removePrefix(":").trim().ifEmpty { if (game.versions.size > 1) "" else version.label }
    val context = LocalContext.current
    val revision by MenuAssets.revision.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val logo by produceState(MenuPictures.cached(MenuPictures.Kind.LOGO, game.number), game.number, revision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { MenuPictures.menuPicture(context, MenuPictures.Kind.LOGO, game.number) }
    }
    val logoLook = remember(game.number, revision) { MenuPictures.look(context, MenuPictures.Kind.LOGO, game.number) }
    Column(
        Modifier.padding(top = 14.dp).graphicsLayer { alpha = enter; translationY = (1f - enter) * -40.dp.toPx() }
            .pointerInput(game.number) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onHold() }) },
    ) {
        val picture = remember(logo) { logo?.asImageBitmap() }
        if (picture != null) {
            // The person's logo in place of the lettering: its own colours (or the game's ink), a hard shadow, at its size.
            val height = 96.dp * scale * logoLook.size
            val width = height * (picture.width.toFloat() / picture.height)
            Box(Modifier.size(width.coerceAtMost(420.dp), height)) {
                // (Persona 4's own shadow is a light yellow: a logo there gets a dark one, as its lettering's box is.)
                Image(picture, null, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (style.number == 4) style.ink else style.shadow),
                    modifier = Modifier.fillMaxSize().graphicsLayer { translationX = 4.dp.toPx(); translationY = 4.dp.toPx(); alpha = 0.8f })
                Image(picture, "Logo", contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    colorFilter = if (logoLook.match) androidx.compose.ui.graphics.ColorFilter.tint(style.ink) else null, modifier = Modifier.fillMaxSize())
            }
        } else if (style.number == 5) {
            RansomText("PERSONA 5", fonts, 0, false, time, height = 58.dp)
        } else if (style.number == 3) {
            Text("PERSONA 3", maxLines = 1, softWrap = false, style = fonts.title.copy(fontSize = fonts.title.fontSize * scale, color = style.ink, shadow = Shadow(style.shadow, Offset(5f, 5f), 0f)))
            // A line of light under it, running out to the right.
            Canvas(Modifier.fillMaxWidth(0.8f).height(4.dp)) {
                drawRect(Brush.horizontalGradient(listOf(style.accentInk, style.accentInk.copy(alpha = 0f))))
                val x = ((time * 0.35f) % 1f) * size.width
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White, Color.Transparent), x - 40f, x + 40f))
            }
        } else {
            Text("PERSONA ${game.number}", maxLines = 1, softWrap = false, style = fonts.title.copy(fontSize = fonts.title.fontSize * scale, color = style.ink, shadow = Shadow(style.shadow, Offset(4f, 4f), 0f)),
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
    // Persona 3's words keep level (the lettering leans by itself), the chosen one drifting like something in water.
    val tilt = if (style.number == 3) (if (on) sin(time * 1.6f) * 0.6f else 0f) else (if (index % 2 == 0) -5f else -2.5f) + sway
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
                style = fonts.words.copy(fontSize = fonts.words.fontSize * scale, color = (if (on) style.bandInk else style.ink).copy(alpha = dim), shadow = if (on) null else if (style.number == 3) Shadow(style.shadow.copy(alpha = 0.9f * dim), Offset(5f, 5f), 0f) else Shadow(style.shadow.copy(alpha = 0.6f * dim), Offset(3f, 3f), 0f)),
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
        3 -> Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.drawBehind {
                // A dark glass tab, slanted like the lettering, with a cyan edge on its left.
                val skew = size.height * 0.35f
                drawPath(Path().apply { moveTo(skew, 0f); lineTo(size.width + skew, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }, Color(0xCC03102E))
                drawPath(Path().apply { moveTo(skew, 0f); lineTo(skew + 6.dp.toPx(), 0f); lineTo(6.dp.toPx(), size.height); lineTo(0f, size.height); close() }, style.accentInk)
            }.padding(start = 22.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        ) {
            val phase = moonPhase(now.timeInMillis)
            Canvas(Modifier.size(30.dp)) {
                drawCircle(style.accentInk.copy(alpha = 0.35f + 0.15f * sin(time * 2f)), size.minDimension * 0.62f, style = Stroke(1.5.dp.toPx()))
                drawMoon(phase, Color(0xFFF2FAFF))
            }
            Spacer(Modifier.width(12.dp))
            Text(date, style = fonts.hud.copy(color = style.ink, fontSize = 30.sp))
            Spacer(Modifier.width(8.dp))
            Text(day, style = fonts.hud.copy(color = style.accentInk))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(timeOfDay(now.get(Calendar.HOUR_OF_DAY)).uppercase(), maxLines = 1, softWrap = false, style = fonts.hud.copy(color = style.ink, fontSize = 15.sp))
                Text(moonName(phase).uppercase(), maxLines = 1, softWrap = false, style = fonts.hud.copy(color = style.accentInk, fontSize = 12.sp))
            }
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

/**
 * Game [number]'s character: the person's picture, else the app's ([MenuPictures]: in the game's look, at its size, facing the way it was set), else
 * a stand-in figure in the game's colours; always on the bottom edge. Held, [onHold] (its picture's dialog).
 */
@Composable
private fun Portrait(number: Int, style: MenuStyle, time: Float, onHold: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val revision by MenuAssets.revision.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // Made off the main thread the first time (the game's look goes over every pixel), then kept.
    val bitmap by produceState(MenuPictures.cached(MenuPictures.Kind.CHARACTER, number), number, revision) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MenuPictures.menuPicture(context, MenuPictures.Kind.CHARACTER, number) }
    }
    val picture = remember(bitmap) { bitmap?.asImageBitmap() }
    val look = remember(number, revision) { MenuPictures.look(context, MenuPictures.Kind.CHARACTER, number) }
    Box(
        modifier
            .pointerInput(number) { detectTapGestures(onLongPress = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); onHold() }) }
            .graphicsLayer { scaleX = look.size * (if (look.flip) -1f else 1f); scaleY = look.size; transformOrigin = TransformOrigin(0.5f, 1f) },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (picture != null) CutOut(picture, CutOutLook.of(number, style), time)
        else Canvas(Modifier.fillMaxSize()) { standIn(style, time) }
    }
}

/** How each game cuts its character out: the edge's colour, a glow behind (pulsing) or none, and a hard shadow's colour. */
private class CutOutLook(val edge: Color, val glow: Color?, val shadow: Color, val shadowAlpha: Float) {
    companion object {
        fun of(number: Int, style: MenuStyle) = when (number) {
            1 -> CutOutLook(Color.White, Color(0xFFC9B8FF), Color(0xFF0E0618), 0.6f)
            2 -> CutOutLook(Color(0xFFF4E9E4), Color(0xFFE0B04A), Color.Black, 0.6f)
            3 -> CutOutLook(Color.White, style.accentInk, style.shadow, 0.55f)
            4 -> CutOutLook(Color.White, null, Color(0xFF16120A), 0.85f)
            else -> CutOutLook(Color.White, null, Color.Black, 0.9f)
        }
    }
}

/**
 * A picture cut out all round, as the games cut theirs: a glow behind it (pulsing slowly), a hard shadow down and to the right, a white edge (the
 * picture's shape in the edge's colour, moved a little in eight directions), then the picture; one canvas, the picture fitted to the bottom centre.
 */
@Composable
private fun CutOut(picture: ImageBitmap, look: CutOutLook, time: Float) {
    val edge = remember(look.edge) { androidx.compose.ui.graphics.ColorFilter.tint(look.edge) }
    val glow = remember(look.glow) { look.glow?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) } }
    val shadow = remember(look.shadow) { androidx.compose.ui.graphics.ColorFilter.tint(look.shadow) }
    Canvas(Modifier.fillMaxSize()) {
        val k = minOf(size.width / picture.width, size.height / picture.height)
        val w = (picture.width * k).toInt()
        val h = (picture.height * k).toInt()
        val x = ((size.width - w) / 2).toInt()
        val y = (size.height - h).toInt()
        val dst = androidx.compose.ui.unit.IntSize(w, h)
        fun at(dx: Float, dy: Float, filter: androidx.compose.ui.graphics.ColorFilter?, alpha: Float = 1f) =
            drawImage(picture, dstOffset = androidx.compose.ui.unit.IntOffset(x + dx.toInt(), y + dy.toInt()), dstSize = dst, colorFilter = filter, alpha = alpha)
        val g = 12.dp.toPx()
        if (glow != null) {
            val pulse = 0.16f + 0.06f * sin(time * 1.7f)
            for (d in 0 until 8) { val a = d * PI.toFloat() / 4; at(cos(a) * g, sin(a) * g, glow, pulse) }
        }
        at(g * 0.9f, g * 0.5f, shadow, look.shadowAlpha)
        val e = 3.dp.toPx()
        for (d in 0 until 8) { val a = d * PI.toFloat() / 4; at(cos(a) * e, sin(a) * e, edge) }
        at(0f, 0f, null)
    }
}

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
            3 -> nightSeaBackground(t, accentInk)
            4 -> tvBackground(t)
            else -> sunburstBackground(t)
        }
    }

    /** The slanted panel's own pattern. */
    fun panel(scope: DrawScope, t: Float) = with(scope) {
        when (number) {
            1 -> { drawRect(Brush.verticalGradient(listOf(Color(0xFFEDE6FF), Color(0xFFB9A6E8)))); rings(t, Color(0xFF3A1C5A), center = Offset(size.width * 0.82f, size.height * 0.34f)) }
            2 -> { drawRect(Color(0xFF0B0405)); embers(t) }
            3 -> glassPanel(t, accentInk)
            4 -> tvPanel(t)
            else -> { drawRect(Color(0xFF0A0A0A)); stars(t) }
        }
    }

    /** The panel's edge. */
    fun edge(scope: DrawScope, panel: Path) = with(scope) {
        if (number == 3) {
            // A glowing edge: a wide soft cyan line under a thin white one.
            drawPath(panel, accentInk.copy(alpha = 0.18f), style = Stroke(width = 22.dp.toPx()))
            drawPath(panel, accentInk.copy(alpha = 0.35f), style = Stroke(width = 9.dp.toPx()))
            drawPath(panel, Color.White, style = Stroke(width = 3.dp.toPx()))
        } else drawPath(panel, panelEdge, style = Stroke(width = 6.dp.toPx()))
    }

    /** Behind the chosen word: [lit] from 0 to 1 as it is chosen. */
    fun highlight(scope: DrawScope, lit: Float, t: Float) = with(scope) {
        val skew = size.height * 0.28f
        val w = size.width * lit
        val band = Path().apply { moveTo(skew, 0f); lineTo(w, 0f); lineTo(w - skew, size.height); lineTo(0f, size.height); close() }
        when (number) {
            3 -> {
                // Its shadow, deep blue, a little down and right.
                translate(8.dp.toPx() * lit, 6.dp.toPx() * lit) { drawPath(band, shadow) }
                // White to cyan.
                drawPath(band, Brush.horizontalGradient(listOf(Color.White, accentInk, this@MenuStyle.band), 0f, size.width))
                // A sweep of light along it.
                val x = ((t * 0.5f) % 1f) * size.width * 1.6f - size.width * 0.3f
                clipPath(band) { drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent), x - 70f, x + 70f)) }
                // Its echo, rippling out and fading, as a ring spreads on water.
                val ripple = (t * 0.6f) % 1f
                val grow = 1f + 0.18f * ripple
                val sx = size.width * lit
                scale(grow, grow, Offset(sx / 2, size.height / 2)) { drawPath(band, Color.White.copy(alpha = (1f - ripple) * 0.55f * lit), style = Stroke(2.dp.toPx())) }
                // The pointer, bobbing at its left.
                val bob = sin(t * 4f) * 4.dp.toPx()
                val ph = size.height * 0.36f
                val px = -10.dp.toPx() + bob
                drawPath(Path().apply { moveTo(px - ph * 0.9f, size.height / 2 - ph / 2); lineTo(px, size.height / 2); lineTo(px - ph * 0.9f, size.height / 2 + ph / 2); close() }, Color.White.copy(alpha = lit))
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
            3 -> MenuStyle(3, Color(0xFF061A45), Color.White, Color(0xFF7FE0FF), Color(0xFF2FB8F5), Color(0xFF06204F), Color(0xFF0D4FA8), Color.White, Color.White, Color(0xFF0B1A3A), Color(0xFFE8EEF5), Color(0xFF7FE0FF))
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

/**
 * Persona 3: the sea at night, from under the water. Deep blue going darker downwards, light falling in shafts from the surface (top right) and
 * swaying, soft blurs of light drifting, the big moon behind the character with the month's eight phases turning round it (tonight's lit),
 * bubbles rising and wobbling, specks drifting, and the edges darkened.
 */
private fun DrawScope.nightSeaBackground(t: Float, glow: Color) {
    val w = size.width
    val h = size.height
    val wide = w > h
    drawRect(Brush.linearGradient(listOf(Color(0xFF0E4FB0), Color(0xFF082A6B), Color(0xFF020A22)), Offset(w, 0f), Offset(w * 0.2f, h)))
    // The surface's light, from above the top right.
    val source = Offset(w * 0.78f, -h * 0.25f)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = source, radius = h * 0.9f), h * 0.9f, source)
    // Shafts of light fanning down from it, swaying.
    for (k in 0 until 9) {
        val a = (PI / 2).toFloat() + (k - 4) * 0.13f + sin(t * 0.25f + k * 1.7f) * 0.035f
        val spread = 0.018f + (k % 3) * 0.012f
        val len = h * 1.5f
        val ray = Path().apply {
            moveTo(source.x, source.y)
            lineTo(source.x + cos(a - spread) * len, source.y + sin(a - spread) * len)
            lineTo(source.x + cos(a + spread) * len, source.y + sin(a + spread) * len)
            close()
        }
        val strength = 0.07f + 0.05f * ((sin(t * 0.6f + k * 2.3f) + 1f) / 2f)
        drawPath(ray, Brush.radialGradient(listOf(Color.White.copy(alpha = strength), Color.Transparent), center = source, radius = len))
    }
    // Soft blurs of light, drifting up slowly.
    for (i in 0 until 10) {
        val r = (26f + (i % 4) * 16f) * density
        val x = ((i * 0.618034f) % 1f) * w + sin(t * 0.2f + i) * 30f
        val y = h + r - ((t * (0.008f + (i % 3) * 0.004f) + i * 0.173f) % 1f) * (h + 2 * r)
        drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.10f + (i % 3) * 0.03f), Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
    }
    // The edges darker.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color(0xAA010614)), center = Offset(w * 0.45f, h * 0.45f), radius = maxOf(w, h) * 0.85f))
    // The moon, behind where the character stands, and its phases round it.
    val moon = if (wide) Offset(w * 0.79f, h * 0.33f) else Offset(w * 0.72f, h * 0.47f)
    val mr = if (wide) h * 0.21f else w * 0.21f
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), glow.copy(alpha = 0.12f), Color.Transparent), center = moon, radius = mr * 2.4f), mr * 2.4f, moon)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFF6FBFF), Color(0xFFCFE9FA)), center = Offset(moon.x - mr * 0.3f, moon.y - mr * 0.3f), radius = mr * 1.4f), mr, moon)
    // Its seas, faint.
    for ((dx, dy, rr) in listOf(Triple(-0.3f, -0.2f, 0.28f), Triple(0.25f, 0.1f, 0.2f), Triple(-0.05f, 0.4f, 0.16f), Triple(0.35f, -0.35f, 0.1f), Triple(-0.45f, 0.25f, 0.09f)))
        drawCircle(Color(0xFF7FB4DA).copy(alpha = 0.22f), mr * rr, Offset(moon.x + dx * mr, moon.y + dy * mr))
    val ring = mr * 1.38f
    drawCircle(Color.White.copy(alpha = 0.22f), ring, moon, style = Stroke(1.5f * density))
    drawCircle(Color.White.copy(alpha = 0.10f), ring * 1.12f, moon, style = Stroke(1f * density))
    val tonight = ((moonPhase(System.currentTimeMillis()) * 8 + 0.5f).toInt()) % 8
    val turn = t * 4f
    for (k in 0 until 8) {
        val a = (turn - 90f + k * 45f) * PI.toFloat() / 180f
        val c = Offset(moon.x + cos(a) * ring, moon.y + sin(a) * ring)
        val r = mr * (if (k == tonight) 0.12f else 0.085f)
        if (k == tonight) drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.8f), Color.Transparent), center = c, radius = r * 2.8f), r * 2.8f, c)
        translate(c.x - r, c.y - r) { drawPhase(k / 8f, r, if (k == tonight) Color.White else Color(0xFFDDF2FF)) }
    }
    // Bubbles rising, wobbling, each with a glint.
    for (i in 0 until 30) {
        val r = (2f + (i % 5) * 1.6f) * density
        val speed = 0.035f + (i % 7) * 0.009f
        val x = ((i * 0.7548777f) % 1f) * w + sin(t * (1.2f + (i % 3) * 0.4f) + i) * 9f * density
        val y = h + r - ((t * speed + i * 0.0913f) % 1f) * (h + 2 * r)
        val c = Offset(x, y)
        drawCircle(Color.White.copy(alpha = 0.06f), r, c)
        drawCircle(Color.White.copy(alpha = 0.45f), r, c, style = Stroke(1.1f * density))
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.28f, Offset(x - r * 0.35f, y - r * 0.35f))
    }
    // Specks drifting.
    for (i in 0 until 60) {
        val x = ((i * 0.569840f) % 1f) * w + sin(t * 0.3f + i * 0.7f) * 14f
        val y = (((i * 0.754877f) + t * 0.006f * (1 + i % 3)) % 1f) * h
        drawCircle(Color(0xFFBFE8FF).copy(alpha = 0.18f + (i % 4) * 0.06f), (0.8f + (i % 3) * 0.5f) * density, Offset(x, y))
    }
}

/** A moon of [phase] (0 new, 0.5 full) in a box of radius [r] at the origin. */
private fun DrawScope.drawPhase(phase: Float, r: Float, color: Color) {
    val c = Offset(r, r)
    // The dark part, deep blue, and the disc's edge.
    drawCircle(Color(0xFF0A2560), r, c)
    drawCircle(color, r, c, style = Stroke(maxOf(1f, r * 0.12f)))
    val k = cos(phase * 2 * PI).toFloat()
    val waxing = phase < 0.5f
    if (abs(phase) < 0.01f) return
    val lit = Path().apply {
        addArc(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * r, 2 * r), if (waxing) -90f else 90f, 180f)
        val ex = r * abs(k)
        arcTo(androidx.compose.ui.geometry.Rect(r - ex, 0f, r + ex, 2 * r), if (waxing) 90f else -90f, if ((k > 0) == waxing) -180f else 180f, false)
        close()
    }
    drawPath(lit, color)
}

/** Persona 3's panel: glass, lighter at the top, ripples of light running down it, and a glint crossing it now and then. */
private fun DrawScope.glassPanel(t: Float, glow: Color) {
    drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), glow.copy(alpha = 0.06f), Color(0xFF041435).copy(alpha = 0.30f))))
    // Lines of light running down, as light on water seen through glass.
    for (k in 0 until 6) {
        val y = (((t * 0.04f) + k / 6f) % 1f) * size.height
        val path = Path()
        var x = 0f
        path.moveTo(0f, y)
        while (x <= size.width) {
            path.lineTo(x, y + sin(x / (34f * density) + t * 1.4f + k) * 5f * density)
            x += 10f
        }
        drawPath(path, Color.White.copy(alpha = 0.10f), style = Stroke(1.5f * density))
    }
    // A glint every eight seconds, slanting across.
    val g = (t % 8f) / 1.6f
    if (g < 1f) {
        val x = -size.width * 0.3f + g * size.width * 1.6f
        rotate(20f, Offset(x, size.height / 2)) {
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.22f), Color.Transparent), x - 50f * density, x + 50f * density),
                Offset(x - 50f * density, -size.height), Size(100f * density, size.height * 3))
        }
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
