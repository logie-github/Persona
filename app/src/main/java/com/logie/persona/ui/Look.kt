package com.logie.persona.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher's look: the Blizzard launcher's modern look (its Look.kt and Theme.kt: rounded dark sheets with a hairline edge, white
 * type, one accent), with the accent taken from the chosen game's own colours ([PersonaColors]): Persona 1's violet, 2's crimson, 3's
 * blue, 4's yellow, 5's red. Every shared piece ([FrameButton], [Sheet], [SettingCard], [PillSegmented], [Modifier.panel]) follows it.
 */
object Modern {
    val Background = Color(0xFF09080E)
    val Surface = Color(0xFF13111B)
    val SurfaceHigh = Color(0xFF1B1826)
    val SurfaceTop = Color(0xFF242032)
    val Outline = Color(0x1FFFFFFF)
    val OutlineStrong = Color(0x33FFFFFF)
    val Text = Color(0xFFF4F3F8)
    val Muted = Color(0xFF9D98AE)
    val Faint = Color(0xFF66617A)
    val Gold = Color(0xFFF0B84C)
    val Good = Color(0xFF39D98A)
    val Warn = Color(0xFFF5A524)
    val Bad = Color(0xFFFF5D5D)

    /** The chosen game's accent (the home sets it as a game is chosen). */
    var Accent by mutableStateOf(Color(0xFF8A5CF0))
    /** What is written on the accent: white, black on Persona 4's yellow. */
    var OnAccent by mutableStateOf(Color.White)
    val AccentBright: Color get() = Accent
    val AccentSoft: Color get() = Accent.copy(alpha = 0.16f)
    val AccentBrush: Brush get() = Brush.horizontalGradient(listOf(Accent, Accent.copy(red = (Accent.red * 1.12f).coerceAtMost(1f), green = (Accent.green * 1.12f).coerceAtMost(1f), blue = (Accent.blue * 1.12f).coerceAtMost(1f))))

    val Corner = 16.dp
    val SheetCorner = 24.dp
    val ButtonCorner = 12.dp

    val SheetBrush = Brush.verticalGradient(listOf(Color(0xFF181522), Color(0xFF0E0C15)))
}

/** The older names the ported screens use (the Blizzard launcher's [Colors]), in this look. */
object Colors {
    val Abyss get() = Modern.Background
    val Night get() = Modern.Surface
    val Deep get() = Modern.SurfaceHigh
    val Frost get() = Modern.SurfaceTop
    val Steel = Color(0xFF34304A)
    val Ice get() = Modern.AccentBright
    val Gold get() = Modern.Gold
    val Text get() = Modern.Text
    val Mist get() = Modern.Muted
}

private val Body = TextStyle(fontFamily = FontFamily.SansSerif, color = Modern.Text)

private val PersonaTypography = Typography(
    headlineLarge = Body.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = Body.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleLarge = Body.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = Body.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    titleSmall = Body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = Body.copy(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = Body.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = Body.copy(fontSize = 13.sp, lineHeight = 18.sp, color = Modern.Muted),
    labelLarge = Body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = Body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Modern.Muted),
)

@Composable
fun PersonaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Modern.Accent, onPrimary = Modern.OnAccent, secondary = Modern.Gold,
            background = Modern.Background, onBackground = Modern.Text, surface = Modern.Surface, onSurface = Modern.Text,
            surfaceVariant = Modern.SurfaceHigh, onSurfaceVariant = Modern.Muted, outline = Modern.OutlineStrong,
            surfaceContainer = Modern.SurfaceHigh, surfaceContainerHigh = Modern.SurfaceTop,
        ),
        typography = PersonaTypography,
        content = content,
    )
}

/** A popup's or a panel's surface: a rounded sheet with a hairline edge and a soft shadow. */
fun Modifier.panel(corner: Dp = Modern.SheetCorner, elevated: Boolean = true): Modifier {
    val shape = RoundedCornerShape(corner)
    return (if (elevated) this.shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black) else this)
        .clip(shape).background(Modern.SheetBrush).border(1.dp, Modern.Outline, shape)
}

/** A small rounded card inside a panel, tinted when [selected]. */
fun Modifier.box(selected: Boolean = false, corner: Dp = 12.dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this.clip(shape).background(if (selected) Modern.AccentSoft else Color.White.copy(alpha = 0.035f))
        .border(1.dp, if (selected) Modern.Accent.copy(alpha = 0.7f) else Modern.Outline, shape)
}

/** A button: the accent for the main one, a quiet outlined one for the others. */
@Composable
fun FrameButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 900f), label = "lit")
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = RoundedCornerShape(Modern.ButtonCorner)
    Box(
        modifier
            .graphicsLayer { val k = 1f - 0.03f * lit; scaleX = k; scaleY = k }
            .heightIn(min = 44.dp)
            .clip(shape)
            .then(if (primary) Modifier.background(Modern.AccentBrush) else Modifier.background(Color.White.copy(alpha = 0.07f)).border(1.dp, Modern.Outline, shape))
            .drawBehind { drawRect(Color.White.copy(alpha = 0.10f * lit)) }
            .clickable(interactionSource = interaction, indication = null, onClick = {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label(text), style = MaterialTheme.typography.labelLarge.copy(color = if (primary) Modern.OnAccent else Modern.Text, fontSize = if (primary) 15.sp else 14.sp), maxLines = 1)
    }
}

/** A button's words: as written, but a label written all in capitals is put in sentence case. */
internal fun label(text: String): String =
    if (text.length > 1 && text == text.uppercase() && text.any { it.isLetter() }) text.lowercase().replaceFirstChar { it.uppercase() } else text

@Composable
fun FrameTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** The standard popup: a sheet with its title, an optional subtitle, a close button and its content. */
@Composable
fun Sheet(title: String, onClose: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, maxWidth: Dp = 600.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(14.dp).widthIn(max = maxWidth).fillMaxWidth().panel()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FrameTitle(title)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Modern.Muted, modifier = Modifier.padding(top = 2.dp)) }
            }
            CloseButton(onClose)
        }
        content()
    }
}

@Composable
fun CloseButton(onClick: () -> Unit) {
    Box(
        Modifier.padding(6.dp).size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.Close, "Close", tint = Modern.Text, modifier = Modifier.size(20.dp)) }
}

/** A section heading inside a sheet or page: small, quiet, in capitals. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.bodySmall.copy(color = Modern.Muted, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, fontSize = 11.sp),
        modifier = modifier,
    )
}

/** A titled block of settings. */
@Composable
internal fun SettingCard(title: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let { SectionLabel(it, Modifier.padding(start = 4.dp)) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Modern.Corner)).background(Color.White.copy(alpha = 0.04f))
                .border(1.dp, Modern.Outline, RoundedCornerShape(Modern.Corner)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** A pill-shaped segmented switch with a sliding highlight. */
@Composable
internal fun <T> PillSegmented(options: List<Pair<String, T>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Modern.Outline, RoundedCornerShape(22.dp)).padding(4.dp),
    ) {
        val cell = maxWidth / options.size.coerceAtLeast(1)
        val x by androidx.compose.animation.core.animateDpAsState(cell * index, spring(dampingRatio = 0.8f, stiffness = 500f), label = "pill")
        Box(Modifier.offset(x = x).width(cell).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Modern.AccentBrush))
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, (text, value) ->
                val on = i == index
                val color by animateColorAsState(if (on) Modern.OnAccent else Modern.Muted, label = "pillText")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(value) },
                    contentAlignment = Alignment.Center,
                ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(color = color, fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
internal fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Modern.Text, modifier = Modifier.weight(1f))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Modern.OnAccent, checkedTrackColor = Modern.Accent, uncheckedThumbColor = Modern.Muted,
                uncheckedTrackColor = Modern.SurfaceTop, uncheckedBorderColor = Modern.OutlineStrong),
        )
    }
}

/** A thin divider. */
@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Modern.Outline))
}

@Composable
fun Gap(height: Dp) = Spacer(Modifier.height(height))
