package app.treelune.themes.metro

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.icons.IconSource
import app.treelune.core.icons.Icons
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.PaletteMode
import app.treelune.core.themes.SelectionAction
import app.treelune.core.themes.TagColor
import app.treelune.core.themes.ThemeContract
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.Duration
import app.treelune.core.ui.FeedbackType
import app.treelune.core.ui.FieldInput
import app.treelune.core.ui.FieldModifier
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.Size
import app.treelune.core.ui.SliderSteps
import app.treelune.core.ui.Spacing
import app.treelune.core.ui.StatusColor
import app.treelune.core.ui.TextType
import app.treelune.core.ui.Emphasis
import app.treelune.core.ui.components.LocalButtonFill
import app.treelune.core.ui.confirmMessage
import app.treelune.core.ui.defaultType
import app.treelune.core.ui.horizontalPick
import app.treelune.core.ui.label
import app.treelune.core.ui.sound.UISignal
import app.treelune.core.ui.sound.scrollEndSound
import app.treelune.core.utils.AppConfigManager
import app.treelune.core.utils.DateUtils
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The metro theme (docs/design/metro-theme.md): the app as a metro's signage. Each tool is a line
 * in its icon's colour: a white panel with corners of 4 dp under a thick line of that colour, its
 * icon in a roundel of it, what is drawn inside the tile (a gauge, a checked box, a chart) in it
 * too. A page's header is a night-blue band, its title in white, as a station's name on its sign;
 * a group's title is a stop on a line. The red is for the alerts alone. In Overpass, after the
 * signs of the American highways (MetroSize); under the finger a piece darkens, a window slides
 * in as a train enters the station (MetroPieces); two palettes of a handful of OKLCH numbers
 * (MetroPalette). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object MetroTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "metro").theme("name")

    /** Lucide's icons as they are: their even, rounded stroke suits a signage font. */
    override val iconSource = IconSource.LUCIDE
    override val hueShifts = true

    // =====================================
    // GRID, SPACES, SOUNDS
    // =====================================

    /** A quarter of what the three gaps leave, the grid growing no wider than 480 dp. */
    @Composable
    override fun gridCellPx(availableWidthPx: Int): Int {
        val density = LocalDensity.current
        val width = minOf(availableWidthPx, with(density) { MAX_GRID_DP.dp.roundToPx() })
        return (width - 3 * gridColumnGapPx()) / 4
    }

    /** Square cells. */
    @Composable
    override fun gridRowPx(cellPx: Int): Int = cellPx

    /** Twelve between two columns, left to the grid: each tile fills its cells. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Twelve between two rows, as between two columns. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, The sounds). */
    override fun sound(signal: UISignal): Int? = null

    /** A tile's name centered in the width its icon leaves. */
    override fun tileNameAlign(): TextAlign = TextAlign.Center

    /** How far the content stands from the tile's top: under its line and 4 dp of air; 8 dp all round an icon's tile. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp =
        if (displayMode == DisplayMode.ICON) metroSize().dp(8f) else metroSize().dp(TILE_LINE + 4f)

    @Composable
    override fun spacing(spacing: Spacing): Dp = (
        when (spacing) {
            Spacing.XS -> 4f
            Spacing.S -> 8f
            Spacing.M -> 16f
            Spacing.L -> 24f
            Spacing.XL -> 32f
            Spacing.EDGE -> 16f
        }
    ).dp

    // =====================================
    // INTERACTIVE
    // =====================================

    @Composable
    override fun Button(type: ButtonType, size: Size, state: ComponentState, onClick: () -> Unit, content: @Composable () -> Unit) {
        val enabled = state == ComponentState.NORMAL || state == ComponentState.SUCCESS
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            Block(type, enabled, pressed) { content() }
        }
    }

    /**
     * A connection's roundel: a disc of the fill, ringed with the ground then with the fill again,
     * its icon in white. Pressed, it darkens.
     */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = metroSize()
        val c = metroColors
        Pressable(onClick = onClick) { pressed ->
            val fill = c.fill.srgb
            val ground = c.ground.srgb
            val outer = metroStroke(3f)
            val gap = metroStroke(4f)
            Box(
                modifier = Modifier.size(size.floating).drawBehind {
                    val r = this.size.minDimension / 2
                    drawCircle(fill, r)
                    drawCircle(ground, r - outer.toPx())
                    drawCircle(fill, r - outer.toPx() - gap.toPx())
                    if (pressed) drawCircle(Color.Black, r, alpha = PRESSED_ALPHA)
                },
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(24f), c.onFill.srgb)
            }
        }
    }

    @Composable
    override fun ActionButton(
        action: ButtonAction,
        display: ButtonDisplay,
        size: Size,
        type: ButtonType?,
        enabled: Boolean,
        requireConfirmation: Boolean,
        confirmMessage: String?,
        active: Boolean,
        icon: String?,
        onClick: () -> Unit
    ) {
        var confirming by rememberSaveable { mutableStateOf(false) }
        val label = action.label()
        val buttonType = type ?: action.defaultType()
        Pressable(onClick = { if (requireConfirmation) confirming = true else onClick() }, enabled = enabled) { pressed ->
            if (display == ButtonDisplay.ICON) {
                IconSquare((icon ?: action.iconName), label, buttonType, enabled, pressed, active)
            } else {
                // Switched on, a button is a fill, as the main one
                Block(if (active) ButtonType.PRIMARY else buttonType, enabled, pressed) { Word(label) }
            }
        }
        if (confirming && requireConfirmation) {
            Dialog(
                type = if (action == ButtonAction.DELETE) DialogType.DANGER else DialogType.CONFIRM,
                onConfirm = { confirming = false; onClick() },
                onCancel = { confirming = false },
                confirmEnabled = true
            ) {
                Text(confirmMessage ?: action.confirmMessage(), TextType.BODY, false, null)
            }
        }
    }

    // =====================================
    // DISPLAY
    // =====================================

    /** Each type in its size and weight; a section's title (a stop) in spaced capitals. */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = metroSize()
        val c = metroColors
        val override = LocalMetroInk.current
        val section = type == TextType.HEADING && LocalMetroSection.current
        val (style, ink) = when (type) {
            TextType.TITLE -> size.title to (override ?: c.ink.srgb)
            TextType.SUBTITLE -> size.subtitle to (override ?: c.ink.srgb)
            TextType.HEADING -> (if (section) size.section else size.name) to (override ?: c.ink.srgb)
            TextType.BODY -> size.body to (override ?: c.ink.srgb)
            TextType.STRONG -> size.strong to (override ?: c.ink.srgb)
            TextType.CAPTION -> size.caption to (override ?: c.dim.srgb)
            TextType.LABEL -> size.label to (override ?: c.dim.srgb)
            TextType.ERROR -> size.body to c.error.srgb
            TextType.WARNING -> size.body to c.warning.srgb
        }
        val shown = if (section) text.toUpperCase(LocaleList(Locale.getDefault().toLanguageTag())) else text
        Line(shown, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /** The weight of STRONG; Overpass's own italic. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        Emphasis.ITALIC -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }

    /**
     * A card is a white panel with corners of 4 dp over a hairline, full so that a page's motif
     * never runs under its text; a highlighted one under the night blue's line. A section's header
     * is a stop: a round ringed in the night blue before its content, a thick line of the night
     * blue under them, leaving the stop.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = metroSize()
        val c = metroColors
        val radius = s.dp(RADIUS)
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.padding(start = spacing(Spacing.EDGE))) { Stop(s.dp(STOP), c.ink.srgb) }
                    Box(modifier = Modifier.weight(1f)) {
                        CompositionLocalProvider(LocalMetroSection provides true) { content() }
                    }
                }
                // The line leaves the stop's middle and runs to the page's edge
                val rule = metroStroke(SECTION_LINE)
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = spacing(Spacing.EDGE) + s.dp(STOP) / 2 - rule / 2, end = spacing(Spacing.EDGE))
                        .height(rule)
                        .panel(c.ink.srgb, rule / 2)
                )
            }
            CardType.DEFAULT -> Box(
                modifier = Modifier.fillMaxWidth()
                    .plate(metroStroke(1f), c.track.srgb, radius)
                    .panel(c.panel.srgb, radius)
                    .let { if (highlight) it.headLine(metroStroke(TILE_LINE), c.ink.srgb, radius) else it }
                    .padding(start = s.dp(12f), end = s.dp(12f), top = s.dp(if (highlight) TILE_LINE + 10f else 10f), bottom = s.dp(10f))
            ) { content() }
        }
    }

    /** A round of the state's colour. */
    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color, CircleShape))
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val c = metroColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }.srgb
    }

    /** A rectangle of its line's colour with corners of 3 dp, its word in white or the deep night blue, whichever reads. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = metroSize()
        val c = metroColors
        Box(modifier = Modifier.panel(c.line(color).srgb, size.dp(3f)).padding(horizontal = size.dp(8f), vertical = size.dp(2f))) {
            Line(text, size.tag, c.onLine(color).srgb, maxLines = 1)
        }
    }

    /**
     * A line of 6 dp on a grey rail, in the tile's line colour (the night blue outside a tile),
     * ending on a stop; nothing done shows the rail alone.
     */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = metroSize()
        val c = metroColors
        val line = LocalMetroLine.current ?: c.ink.srgb
        val rail = c.track.srgb
        val panel = c.panel.srgb
        val thick = size.dp(6f)
        val stop = size.dp(14f)
        val ring = metroStroke(3f)
        val done = fraction.coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(stop).drawBehind {
            val t = thick.toPx()
            val r = stop.toPx() / 2
            // The rail stops short of the stop's half at each end, so the stop never leaves it
            val top = (this.size.height - t) / 2
            drawRoundRect(rail, Offset(0f, top), GeometrySize(this.size.width, t), CornerRadius(t / 2, t / 2))
            if (done > 0f) {
                val end = r + (this.size.width - 2 * r) * done
                drawRoundRect(line, Offset(0f, top), GeometrySize(end, t), CornerRadius(t / 2, t / 2))
                drawCircle(panel, r, Offset(end, this.size.height / 2))
                drawCircle(line, r - ring.toPx() / 2, Offset(end, this.size.height / 2), style = Stroke(ring.toPx()))
            }
        })
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = metroSize().caption.copy(color = metroColors.dim.srgb, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        MetroDrawing.Draw(drawing, drawingTextStyle(), metroColors, LocalMetroLine.current, modifier)
    }

    /** A hairline of 1 dp across the width, in the grey of a rail. */
    @Composable
    override fun Divider() {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = metroSize().dp(8f)).height(metroStroke(1f)).background(metroColors.track.srgb))
    }

    /** Two rounded bars of 2 dp, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = metroSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = metroSize().dp(4f))) { Bars(metroSize().dp(24f)) }
    }

    /** Lifted, an item rises on a flat shadow; put down, it falls back at once. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, metroSize().dp(RADIUS), content)

    /**
     * A fill with corners of 4 dp, its words in white, a hairline of white between two; a word
     * touched in white, its text in the fill's colour. Words a finger's target tall, going to a
     * second line when they do not hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = metroSize()
        val c = metroColors
        val rule = metroStroke(1f)
        val fill = c.fill.srgb
        val white = c.onFill.srgb
        FlowRow(modifier = Modifier.panel(fill, size.dp(RADIUS))) {
            actions.forEachIndexed { index, action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = size.touch)
                            .let { if (pressed) it.background(white) else it }
                            .drawBehind { if (index > 0) drawRect(white.copy(alpha = 0.5f), Offset.Zero, GeometrySize(rule.toPx(), this.size.height)) }
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.label, size.button, if (pressed) fill else white, maxLines = 1)
                    }
                }
            }
        }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = MetroPalettes.colors(mode, 0).line(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================

    /** A fill at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { MetroToasts.show(message, shown) }
    }

    /**
     * A fill with corners of 4 dp, its message in white, its action in white capitals; a round of
     * the state's colour before the message, but for a mere information.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = metroSize()
        val c = metroColors
        val mark = when (type) {
            FeedbackType.SUCCESS -> c.success
            FeedbackType.ERROR -> c.error
            FeedbackType.WARNING -> c.warning
            FeedbackType.INFO -> null
        }
        val white = c.onFill.srgb
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch).panel(c.fill.srgb, size.dp(RADIUS)).padding(start = size.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            mark?.let { Box(modifier = Modifier.size(size.dp(10f)).background(it.srgb, CircleShape)) }
            Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, white) }
            if (action != null && onAction != null) {
                Pressable(onClick = onAction) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).pressedTint(pressed, Color.Black, size.dp(RADIUS)).padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.uppercase(Locale.getDefault()), size.section, white, maxLines = 1)
                    }
                }
            }
        }
    }

    @Composable
    override fun ConfirmDialog(title: String, message: String, confirmText: String?, cancelText: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(cancelText ?: s.shared("action_cancel"), onDismiss)
            WordButton(confirmText ?: s.shared("action_confirm"), onConfirm, strong = true)
        }) {
            Text(title, TextType.TITLE, false, null)
            Text(message, TextType.BODY, false, null)
        }
    }

    // =====================================
    // SYSTEM
    // =====================================

    /** A train between stops: a round of the night blue gliding along a grey line of 4 dp, as long as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val s = metroSize()
        val length = s.dp(
            when (size) {
                Size.XS -> 24f
                Size.S -> 48f
                Size.M -> 96f
                Size.L -> 160f
                Size.XL, Size.XXL -> 240f
            }
        )
        Train(Modifier.width(length))
    }

    /** Lucide's drawing; a background, when asked, is a round of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint ?: LocalMetroInk.current ?: metroColors.ink.srgb),
            modifier = Modifier.size(size).let { m -> background?.let { m.background(it, CircleShape) } ?: m }
        )
    }

    /**
     * A zone's or a tool's icon in a roundel of its line's colour, as a line's number, the icon in
     * white or the deep night blue, whichever reads; a neutral one in a roundel of the same size
     * ringed in grey, so names beside icons line up. On an icon's tile, already of its colour, the
     * icon is bare.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val c = metroColors
        val imposed = LocalMetroInk.current
        if (LocalMetroOnLine.current) {
            Image(painter = painterResource(resourceId), contentDescription = null, colorFilter = ColorFilter.tint(imposed ?: c.ink.srgb), modifier = Modifier.size(size))
            return
        }
        val ring = metroStroke(2f)
        // A roundel larger than the icon: the icon at four fifths of its room, the room a third larger
        val badge = size * BADGE_SCALE
        val (fill, ink) = if (color != null) c.line(color).srgb to c.onLine(color).srgb else null to (imposed ?: c.ink.srgb)
        val ringColor = imposed?.copy(alpha = 0.5f) ?: c.track.srgb
        Box(
            modifier = Modifier.size(badge).drawBehind {
                if (fill != null) drawCircle(fill)
                else drawCircle(ringColor, this.size.minDimension / 2 - ring.toPx() / 2, style = Stroke(ring.toPx()))
            },
            contentAlignment = Alignment.Center
        ) {
            Image(painter = painterResource(resourceId), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(size * ICON_IN_BADGE))
        }
    }

    @Composable
    override fun Dialog(type: DialogType, onConfirm: () -> Unit, onCancel: () -> Unit, confirmEnabled: Boolean, content: @Composable () -> Unit) {
        val s = Strings.`for`(context = LocalContext.current)
        val cancel = s.shared("action_cancel")
        val (confirmText, cancelText) = when (type) {
            DialogType.CONFIGURE, DialogType.CONFIRM -> s.shared("action_confirm") to cancel
            DialogType.CREATE -> s.shared("action_create") to cancel
            DialogType.EDIT -> s.shared("action_save") to cancel
            DialogType.DANGER -> s.shared("action_delete") to cancel
            DialogType.SELECTION -> null to cancel
            DialogType.INFO -> s.shared("action_ok") to null
        }
        DialogFrame(onDismiss = onCancel, actions = {
            if (cancelText != null) WordButton(cancelText, onCancel)
            if (confirmText != null) WordButton(confirmText, onConfirm, strong = true, danger = type == DialogType.DANGER, enabled = confirmEnabled)
        }) {
            Box(modifier = Modifier.scrollEndSound()) { content() }
        }
    }

    /**
     * A month on seven columns, its days in figures; the day chosen in a round of the fill, today in
     * a round ringed in the night blue. The month and its year in heavy capitals between two chevrons.
     */
    @Composable
    override fun DatePicker(selectedDate: String, today: LocalDate, onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
        // "" means nothing chosen yet, and the picker opens on the caller's today by contract
        val initial = DateUtils.parseDisplayDate(selectedDate) ?: today
        var chosen by remember { mutableStateOf(initial) }
        var month by remember { mutableStateOf(YearMonth.from(initial)) }
        val locale = Locale.getDefault()
        val size = metroSize()
        val c = metroColors
        val s = Strings.`for`(context = LocalContext.current)

        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onDateSelected(DateUtils.formatDisplayDate(chosen))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlainIconButton("chevron-left") { month = month.minusMonths(1) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val name = month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale) + " " + month.year
                    Line(name.uppercase(locale), size.section.copy(fontSize = size.section.fontSize * 1.2f), c.ink.srgb, maxLines = 1)
                }
                PlainIconButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = DateUtils.weekStart()
            val days = (0 until 7).map { first.plus(it.toLong()) }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(4f)), contentAlignment = Alignment.Center) {
                            Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale).uppercase(locale), size.label, c.dim.srgb)
                        }
                    }
                }
                val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
                val cells = lead + month.lengthOfMonth()
                val ring = metroStroke(2f)
                (0 until (cells + 6) / 7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val n = week * 7 + column - lead + 1
                            Box(modifier = Modifier.weight(1f).height(size.dp(40f)), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    Pressable(onClick = { chosen = date }, modifier = Modifier.fillMaxSize()) { pressed ->
                                        val fill = c.fill.srgb
                                        val ink = c.ink.srgb
                                        Box(
                                            modifier = Modifier.fillMaxSize().drawBehind {
                                                val r = minOf(this.size.width, this.size.height) / 2 - 2f
                                                when {
                                                    lit -> drawCircle(fill, r)
                                                    date == today -> drawCircle(ink, r - ring.toPx() / 2, style = Stroke(ring.toPx()))
                                                }
                                                if (pressed) drawCircle(ink, r, alpha = PRESSED_ALPHA)
                                            },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val style = if (lit || date == today) size.strong.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else size.body
                                            Line(n.toString(), style, if (lit) c.onFill.srgb else c.ink.srgb)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A departures board: hours and minutes in heavy white figures on two night-blue panels, each between − and + buttons. */
    @Composable
    override fun TimePicker(selectedTime: String, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
        // "" means nothing chosen yet, and the picker opens on now by contract
        val (h, m) = DateUtils.parseTime(selectedTime)
            ?: ZonedDateTime.now(AppConfigManager.getDateTimeConfig().getZoneId()).let { it.hour to it.minute }
        var hour by remember { mutableIntStateOf(h) }
        var minute by remember { mutableIntStateOf(m) }
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metroSize().dp(8f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", metroSize().number, metroColors.ink.srgb)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The off-white ground; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(metroColors.ground.srgb)) {
            CompositionLocalProvider(LocalMetroInk provides null, LocalMetroLine provides null, LocalMetroOnLine provides false) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /**
     * A textured page's whole ground: the screen's ground and the motif in the night blue over it,
     * as on the thing's tile, whatever its line's colour; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val ground = metroColors.ground.srgb
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), ground, textureStyle())
        val modifier = if (brush == null) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(ground).background(brush)
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = metroSize()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(4f), vertical = size.dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    /** A zone is a line too, on the home screen: the tool's tile. */
    @Composable
    override fun ZoneCardContainer(texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(DisplayMode.CONDENSED, texture, color, onClick, onLongClick, content)

    /** A round of the red ringed with the panel, an exclamation mark in heavy white. */
    @Composable
    override fun WaitingMark() {
        val size = metroSize()
        val c = metroColors
        Mark(c.accent.srgb) {
            Line("!", size.button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = 0.sp), c.onFill.srgb)
        }
    }

    /** A cell of the grid in edit mode: a rectangle with corners of 4 dp, dashed in the grey of a rail. */
    @Composable
    override fun GridCell() {
        val stroke = metroStroke(1f)
        val radius = metroSize().dp(RADIUS)
        val color = metroColors.track.srgb
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val w = stroke.toPx()
            val r = radius.toPx()
            drawRoundRect(color, Offset(w / 2, w / 2), GeometrySize(size.width - w, size.height - w), CornerRadius(r, r),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * w, 3 * w))))
        })
    }

    /**
     * The tile being moved is lifted: it rises on a flat shadow; put down, it falls back at once.
     * The others step back, faded, the cells showing through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen, metroSize().dp(RADIUS)) { CompositionLocalProvider(LocalMetroLifted provides chosen) { content() } }
        }
    }

    /** A round of the fill ringed with the panel, Lucide's timer in white: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = metroSize()
        val c = metroColors
        Mark(c.fill.srgb) { NamedIcon("timer", null, size.dp(12f), c.onFill.srgb) }
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, texture, color, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it, a line: a white panel with corners of 4 dp under
     * a line of 6 dp of its icon's colour (grey without one), a hairline under it, its motif under
     * the line; what is drawn inside takes the line's colour (LocalMetroLine). An icon's tile is a
     * block of its line's colour with corners of 10 dp, its icon bare in white or the deep night
     * blue. Pressed, a tile darkens.
     */
    @Composable
    private fun Tile(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = metroColors
        val size = metroSize()
        val block = displayMode == DisplayMode.ICON
        val frame = tileFrame(displayMode)
        val shown = color ?: TagColor.GREY
        val line = c.line(shown).srgb
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            val radius = size.dp(if (block) ICON_TILE_RADIUS else RADIUS)
            val surface = if (block) line else c.panel.srgb
            // The motif in the night blue, whatever the line's colour
            val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), surface, textureStyle())
            val head = metroStroke(TILE_LINE)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .let { if (block) it else it.plate(metroStroke(1f), c.track.srgb, radius) }
                    .panel(surface, radius)
                    // The motif under the line, never over it, inside the panel's corners
                    .drawBehind {
                        brush?.let {
                            val top = if (block) 0f else head.toPx()
                            val r = radius.toPx()
                            drawRoundRect(it, Offset(0f, top), GeometrySize(this.size.width, this.size.height - top), CornerRadius(r, r))
                        }
                    }
                    .let { if (block) it else it.headLine(head, line, radius) }
                    .pressedTint(pressed, c.ink.srgb, radius)
                    .let { if (block) it.padding(frame) else it.padding(start = size.dp(10f), end = size.dp(10f), top = frame, bottom = size.dp(4f)) }
            ) {
                CompositionLocalProvider(
                    LocalMetroInk provides if (block) c.onLine(shown).srgb else null,
                    LocalMetroLine provides color?.let { line },
                    LocalMetroOnLine provides block
                ) { content() }
            }
        }
    }

    /**
     * A station's sign: a night-blue band across the width under the bar, the page's icon in the
     * roundel of its line before the title in white, flush left; shrunk to hold in two lines,
     * never under a dialog's title. The subtitle under it, in white, softer. The app's mark is
     * its launcher icon, in its colours, whose blue is the band's.
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = metroSize()
        val c = metroColors
        val white = c.onFill.srgb
        Row(
            modifier = Modifier.fillMaxWidth().background(c.band.srgb)
                .padding(horizontal = spacing(Spacing.EDGE), vertical = size.dp(14f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(12f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalMetroInk provides white) {
                if (appMark) app.treelune.core.ui.components.LauncherMark(size.dp(PAGE_ICON) * BADGE_SCALE, CircleShape)
                else icon?.let { app.treelune.core.ui.UI.ItemIcon(it, iconColor, size.dp(PAGE_ICON)) }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(size.dp(2f))) {
                BasicText(
                    text = title,
                    style = size.pageTitle.copy(
                        color = white,
                        hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp),
                    modifier = Modifier.fillMaxWidth()
                )
                subtitle?.let { Line(it, size.label, white.copy(alpha = SUBTITLE_ALPHA)) }
            }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In bold, the ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, metroSize().label, LocalMetroInk.current ?: metroColors.ink.srgb)
    }

    /**
     * A white panel with corners of 4 dp, outlined in 2 dp of grey, of the night blue while the
     * field has the focus, of the error's colour when it is wrong; the label above, and again
     * inside while it is empty.
     */
    @Composable
    override fun FormField(
        label: String,
        value: TextFieldValue,
        onChange: (TextFieldValue) -> Unit,
        fieldType: FieldType,
        state: ComponentState,
        readonly: Boolean,
        onClick: (() -> Unit)?,
        contentDescription: String?,
        required: Boolean,
        fieldModifier: FieldModifier,
        labelAbove: Boolean,
        labelActions: (@Composable () -> Unit)?
    ) {
        val size = metroSize()
        val c = metroColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (labelAbove) LabelLine(label, required, labelActions)
            if (readonly) {
                val shown = value.text.ifBlank { Strings.`for`(context = LocalContext.current).shared("label_no_value") }
                Box(modifier = if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier) {
                    Text(shown, TextType.BODY, false, null)
                }
            } else {
                val isPassword = fieldType == FieldType.PASSWORD
                var revealed by remember { mutableStateOf(false) }
                val source = remember { MutableInteractionSource() }
                val focused by source.collectIsFocusedAsState()
                val error = state == ComponentState.ERROR
                Boxed(
                    when {
                        error -> c.error.srgb
                        focused -> c.ink.srgb
                        else -> c.track.srgb
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            readOnly = state == ComponentState.READONLY,
                            enabled = state != ComponentState.DISABLED,
                            textStyle = size.body.copy(color = (if (error) c.error else c.ink).srgb),
                            cursorBrush = SolidColor(c.ink.srgb),
                            keyboardOptions = FieldInput.keyboardOptions(fieldType),
                            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                            interactionSource = source,
                            modifier = Modifier
                                .weight(1f)
                                .let { m -> fieldModifier.focusRequester?.let { m.focusRequester(it) } ?: m }
                                .let { m -> fieldModifier.onFocusChanged?.let { m.onFocusChanged(it) } ?: m },
                            decorationBox = { inner ->
                                Box {
                                    if (value.text.isEmpty()) Line(label, size.body, c.dim.srgb, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        if (isPassword) {
                            Box(modifier = Modifier.combinedClickable(onClick = { revealed = !revealed })) {
                                NamedIcon(if (revealed) "eye-off" else "eye", null, size.icon, c.dim.srgb)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The choice shown as a field, its icon before it, a chevron at its end; the options in a
     * panel of the dialog's frame, under their sections' titles, the chosen one a row of the fill.
     */
    @Composable
    override fun FormSelection(
        label: String,
        options: List<String>,
        sections: List<String?>,
        icons: List<app.treelune.core.themes.OptionIcon?>,
        selected: Int?,
        shown: String,
        onSelect: (Int) -> Unit,
        required: Boolean
    ) {
        var open by remember { mutableStateOf(false) }
        val size = metroSize()
        val c = metroColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Boxed(if (pressed) c.ink.srgb else c.track.srgb) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OptionIconOf(selected)
                        Box(modifier = Modifier.weight(1f)) { Line(shown, size.body, c.ink.srgb, maxLines = 1) }
                        NamedIcon("chevron-down", null, size.dp(20f), c.ink.srgb)
                    }
                }
            }
        }
        if (open) {
            DialogFrame(onDismiss = { open = false }, actions = {}) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { index, option ->
                        // A section's title above its first option, in capitals, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(16f), bottom = size.dp(4f))) {
                                Line(section.uppercase(Locale.getDefault()), size.section, c.dim.srgb)
                            }
                        }
                        val chosen = index == selected
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (chosen) it.panel(c.fill.srgb, size.dp(RADIUS)) else it }
                                    .pressedTint(pressed, c.ink.srgb, size.dp(RADIUS))
                                    .padding(horizontal = size.dp(12f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalMetroInk provides if (chosen) c.onFill.srgb else null) {
                                    OptionIconOf(index)
                                    Box(modifier = Modifier.weight(1f)) { Line(option, if (chosen) size.strong else size.body, if (chosen) c.onFill.srgb else c.ink.srgb) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A round of 22 dp ringed in 3 dp: empty, or full when checked, in the tile's line colour (the night blue elsewhere). */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = metroSize()
        val c = metroColors
        val line = LocalMetroLine.current ?: c.ink.srgb
        val ring = metroStroke(3f)
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                Box(modifier = Modifier.size(size.dp(22f)).drawBehind {
                    val r = this.size.minDimension / 2
                    if (checked) drawCircle(line, r) else drawCircle(line, r - ring.toPx() / 2, style = Stroke(ring.toPx()))
                    if (pressed) drawCircle(c.ink.srgb, r, alpha = PRESSED_ALPHA)
                })
                label?.let { Line(it, size.body, LocalMetroInk.current ?: c.ink.srgb) }
            }
        }
    }

    /** The label across, a pill outlined at the end whose round knob slides: full of the fill when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = metroSize()
        val c = metroColors
        val ring = metroStroke(2f)
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) { pressed ->
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink.srgb) }
                val ink = c.ink.srgb
                val fill = c.fill.srgb
                val panel = c.panel.srgb
                Box(modifier = Modifier.size(size.dp(48f), size.dp(28f)).drawBehind {
                    val h = this.size.height
                    val w = ring.toPx()
                    if (checked) drawRoundRect(fill, cornerRadius = CornerRadius(h / 2, h / 2))
                    else drawRoundRect(ink, Offset(w / 2, w / 2), GeometrySize(this.size.width - w, h - w), CornerRadius(h / 2, h / 2), style = Stroke(w))
                    val knob = h / 2 - 4 * density
                    val x = if (checked) this.size.width - h / 2 else h / 2
                    drawCircle(panel, knob, Offset(x, h / 2))
                    if (!checked) drawCircle(ink, knob - w / 2, Offset(x, h / 2), style = Stroke(w))
                    if (pressed) drawRoundRect(ink, cornerRadius = CornerRadius(h / 2, h / 2), alpha = PRESSED_ALPHA)
                })
            }
        }
    }

    /** Labels in bold: the tab shown in the ink over a rounded line of 4 dp, the others in the secondary ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = metroSize()
        val c = metroColors
        val hair = metroStroke(1f)
        val track = c.track.srgb
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind { drawRect(track, Offset(0f, this.size.height - hair.toPx()), GeometrySize(this.size.width, hair.toPx())) }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(size.dp(20f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    val shown = index == selected
                    val ink = c.ink.srgb
                    val line = metroStroke(TAB_LINE)
                    Box(
                        modifier = Modifier.heightIn(min = size.touch)
                            .drawBehind {
                                if (shown || pressed) {
                                    val t = line.toPx()
                                    drawRoundRect(ink, Offset(0f, this.size.height - t), GeometrySize(this.size.width, t), CornerRadius(t / 2, t / 2), alpha = if (shown) 1f else 0.4f)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Line(label, size.button, if (shown) c.ink.srgb else c.dim.srgb, maxLines = 1)
                    }
                }
            }
        }
    }

    /** Two segments outlined side by side, sharing their outline, rounded at their outer ends: the chosen one a fill. */
    @Composable
    override fun BooleanField(
        label: String,
        value: Boolean?,
        onValueChange: (Boolean?) -> Unit,
        trueLabel: String,
        falseLabel: String,
        required: Boolean,
        emptiable: Boolean,
        compact: Boolean
    ) {
        val size = metroSize()
        val c = metroColors
        val stroke = metroStroke(2f)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
                listOf(true to trueLabel, false to falseLabel).forEachIndexed { index, (answer, text) ->
                    val chosen = value == answer
                    val first = index == 0
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        // The second segment over the first's right outline: one line between them
                        modifier = (if (compact) Modifier else Modifier.weight(1f)).offset(x = -stroke * index)
                    ) { pressed ->
                        val ink = c.ink.srgb
                        val fill = c.fill.srgb
                        val r = size.dp(RADIUS)
                        Box(
                            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                                .heightIn(min = size.touch)
                                .drawBehind {
                                    val w = stroke.toPx()
                                    val rr = r.toPx()
                                    // Rounded at the outer end only: drawn wider and cut at the inner one
                                    val extra = rr * 2
                                    val left = if (first) 0f else -extra
                                    val width = this.size.width + extra
                                    clipRect(0f, 0f, this.size.width, this.size.height) {
                                        if (chosen) drawRoundRect(fill, Offset(left, 0f), GeometrySize(width, this.size.height), CornerRadius(rr, rr))
                                        drawRoundRect(if (chosen) fill else ink, Offset(left + w / 2, w / 2), GeometrySize(width - w, this.size.height - w), CornerRadius(rr, rr), style = Stroke(w))
                                        if (pressed) drawRoundRect(ink, Offset(left, 0f), GeometrySize(width, this.size.height), CornerRadius(rr, rr), alpha = PRESSED_ALPHA)
                                    }
                                }
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text, size.button, if (chosen) c.onFill.srgb else c.ink.srgb, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * The red at each shift, saturated, along a pill; the shift chosen a round ringed in the ink
     * over a ring of the panel. A touch or a drag anywhere on it answers.
     */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) {
        val size = metroSize()
        val c = metroColors
        val mode = CurrentTheme.paletteMode
        val colors = remember(mode, shifts) {
            (shifts step HUE_STRIP_STEP).map { MetroPalettes.colors(mode, it).accent.srgb } + MetroPalettes.colors(mode, shifts.last).accent.srgb
        }
        val knob = size.dp(28f)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .horizontalPick { onValueChange(app.treelune.core.ui.RangePick.valueAt(it, shifts)) },
            contentAlignment = Alignment.CenterStart
        ) {
            val band = size.dp(24f)
            Box(modifier = Modifier.fillMaxWidth().height(band).drawBehind {
                drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(this.size.height / 2, this.size.height / 2))
            })
            val ink = c.ink.srgb
            val panel = c.panel.srgb
            val ring = metroStroke(3f)
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - knob) * app.treelune.core.ui.RangePick.fractionOf(value, shifts))
                    .size(knob)
                    .drawBehind {
                        val r = this.size.minDimension / 2
                        val w = ring.toPx()
                        drawCircle(ink, r - w / 2, style = Stroke(w))
                        drawCircle(panel, r - w * 1.5f, style = Stroke(w))
                    }
            )
        }
    }

    /**
     * The value above in heavy figures, a rail of 6 dp across the width, the part chosen in the
     * night blue up to a stop the finger moves; a touch or a drag anywhere on it answers the stop
     * under the finger. No answer yet shows the rail alone and "—".
     */
    @Composable
    override fun SliderField(
        label: String,
        value: Double?,
        onValueChange: (Double?) -> Unit,
        min: Double,
        max: Double,
        step: Double,
        minLabel: String,
        maxLabel: String,
        required: Boolean
    ) {
        val size = metroSize()
        val c = metroColors
        val strings = Strings.`for`(context = LocalContext.current)
        val lastStop = SliderSteps.lastStop(min, max, step)
        val decimals = SliderSteps.decimals(min, step)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(size.dp(16f)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Line(value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—", size.sliderValue, (if (value != null) c.ink else c.dim).srgb)
                if (value != null && !required) WordButton(strings.shared("action_clear"), { onValueChange(null) })
            }
            // The gestures below are installed once: they call the latest onValueChange, which
            // writes into the settings as they are now, not as they were when the slider was drawn
            val latest by rememberUpdatedState(onValueChange)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val width = constraints.maxWidth.toFloat()
                fun answer(x: Float) {
                    val raw = min + (x / width).coerceIn(0f, 1f) * (lastStop - min)
                    latest(SliderSteps.snap(raw, min, step))
                }
                val fraction = value?.let { ((it - min) / (lastStop - min)).toFloat().coerceIn(0f, 1f) }
                val ink = c.ink.srgb
                val rail = c.track.srgb
                val panel = c.panel.srgb
                val thick = size.dp(6f)
                val knob = size.dp(20f)
                val ring = metroStroke(3f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } }
                        .drawBehind {
                            val t = thick.toPx()
                            val r = knob.toPx() / 2
                            val top = (this.size.height - t) / 2
                            drawRoundRect(rail, Offset(0f, top), GeometrySize(this.size.width, t), CornerRadius(t / 2, t / 2))
                            if (fraction != null) {
                                val at = r + (this.size.width - 2 * r) * fraction
                                drawRoundRect(ink, Offset(0f, top), GeometrySize(at, t), CornerRadius(t / 2, t / 2))
                                drawCircle(panel, r, Offset(at, this.size.height / 2))
                                drawCircle(ink, r - ring.toPx() / 2, Offset(at, this.size.height / 2), style = Stroke(ring.toPx()))
                            }
                        }
                )
            }
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Line(minLabel, size.caption, c.dim.srgb)
                    Line(maxLabel, size.caption, c.dim.srgb)
                }
            }
        }
    }

    // =====================================
    // PALETTES
    // =====================================

    /** For what Material still draws at the app's root: the palette's colours under its names. */
    override fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme {
        val c = MetroPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.fill.srgb, onPrimary = c.onFill.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onFill.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.panel.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.ground.srgb,
            outline = c.ink.srgb, outlineVariant = c.track.srgb
        ) else lightColorScheme(
            primary = c.fill.srgb, onPrimary = c.onFill.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onFill.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.panel.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.panel.srgb,
            outline = c.ink.srgb, outlineVariant = c.track.srgb
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /**
     * Three stops on a grey line lighting one after the other, then going out: a train on its way.
     * The AI's wait asks nothing of the user.
     */
    @Composable
    override fun AIThinkingIndicator() {
        val size = metroSize()
        val c = metroColors
        val transition = rememberInfiniteTransition(label = "thinking")
        // 0 to 4: how many stops are lit, the fourth step putting them all out
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = THINKING_STEPS.toFloat(),
            animationSpec = infiniteRepeatable(tween(THINKING_STEP_MS * THINKING_STEPS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        val lit = phase.toInt()
        val ink = c.ink.srgb
        val track = c.track.srgb
        val panel = c.panel.srgb
        val stop = size.dp(14f)
        val gap = size.dp(26f)
        val ring = metroStroke(3f)
        val rail = metroStroke(2f)
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(12f)), contentAlignment = Alignment.CenterStart) {
            Box(modifier = Modifier.size(stop * 3 + gap * 2, stop).drawBehind {
                val r = stop.toPx() / 2
                val step = stop.toPx() + gap.toPx()
                drawLine(track, Offset(r, r), Offset(r + 2 * step, r), strokeWidth = rail.toPx())
                repeat(3) { i ->
                    val center = Offset(r + i * step, r)
                    if (i < lit) drawCircle(ink, r, center)
                    else {
                        drawCircle(panel, r, center)
                        drawCircle(track, r - ring.toPx() / 2, center, style = Stroke(ring.toPx()))
                    }
                }
            })
        }
    }

    /**
     * The user's message is a fill pushed to the right, its text in white; the AI's a white panel
     * over a hairline, the system's the same in the secondary ink. Full panels: a long text never
     * stands on a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = metroSize()
        val c = metroColors
        val radius = size.dp(RADIUS)
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .panel(c.fill.srgb, radius)
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) {
                    CompositionLocalProvider(LocalMetroInk provides c.onFill.srgb) { content() }
                }
            }
            MessageSender.AI -> Box(
                modifier = Modifier.fillMaxWidth().plate(metroStroke(1f), c.track.srgb, radius).panel(c.panel.srgb, radius)
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) { content() }
            MessageSender.SYSTEM -> Box(
                modifier = Modifier.fillMaxWidth().plate(metroStroke(1f), c.track.srgb, radius).panel(c.panel.srgb, radius)
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) {
                CompositionLocalProvider(LocalMetroInk provides c.dim.srgb) { content() }
            }
        }
    }

    /** A white panel under a red line of 6 dp, a signal that the user is asked: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: (@Composable () -> Unit)?) {
        val size = metroSize()
        val c = metroColors
        val radius = size.dp(RADIUS)
        Column(
            modifier = Modifier.fillMaxWidth()
                .plate(metroStroke(1f), c.track.srgb, radius)
                .panel(c.panel.srgb, radius)
                .headLine(metroStroke(TILE_LINE), c.accent.srgb, radius)
                .padding(start = size.dp(12f), end = size.dp(12f), top = size.dp(TILE_LINE + 10f), bottom = size.dp(4f)),
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Text(title, TextType.TITLE, true, null)
            content()
            if (actions != null) app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
        }
    }

    // =====================================
    // PIECES
    // =====================================

    /** A text in one style and colour, cut with an ellipsis past [maxLines]. */
    @Composable
    private fun Line(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) =
        Line(AnnotatedString(text), style, color, modifier, align, maxLines)

    /** The same, its runs keeping their emphasis. */
    @Composable
    private fun Line(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style.copy(
                color = color,
                textAlign = align ?: TextAlign.Unspecified,
                hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines
        )
    }

    /** A button's word, in the ink its block imposes. */
    @Composable
    private fun Word(text: String) {
        Line(text, metroSize().button, LocalMetroInk.current ?: metroColors.ink.srgb, maxLines = 1)
    }

    /**
     * A button a finger's target tall, corners of 4 dp: a fill for the main one, an outline of 2 dp
     * for a secondary one, a red outline for a destructive one, its word alone for the others; grey
     * when disabled. Pressed, it darkens.
     */
    @Composable
    private fun Block(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = metroSize()
        val c = metroColors
        val stroke = metroStroke(2f)
        val radius = size.dp(RADIUS)
        val ink = LocalMetroInk.current ?: c.ink.srgb
        // What the button is drawn as: a fill, an outline (line), or its word alone
        val (fill, line, word) = when {
            !enabled -> when (type) {
                ButtonType.PRIMARY -> Triple(c.track.srgb, null, c.dim.srgb)
                ButtonType.SECONDARY, ButtonType.DANGER -> Triple(null, c.track.srgb, c.dim.srgb)
                else -> Triple(null, null, c.dim.srgb)
            }
            type == ButtonType.PRIMARY -> Triple(c.fill.srgb, null, c.onFill.srgb)
            type == ButtonType.SECONDARY -> Triple(null, ink, ink)
            type == ButtonType.DANGER -> Triple(null, c.danger.srgb, c.danger.srgb)
            else -> Triple(null, null, ink)
        }
        val plain = fill == null && line == null
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .let { m -> fill?.let { m.panel(it, radius) } ?: m }
                .let { m -> line?.let { m.outline(stroke, it, radius) } ?: m }
                // A fill darkens with black, the rest with the night blue
                .pressedTint(pressed, if (fill != null) Color.Black else c.ink.srgb, radius)
                .padding(horizontal = if (plain) size.dp(8f) else size.dp(16f)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalMetroInk provides word) { content() }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; switched on ([active]) or the
     * main one, a round of the fill. Pressed, it darkens.
     */
    @Composable
    private fun IconSquare(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = metroSize()
        val c = metroColors
        val filled = (active || type == ButtonType.PRIMARY) && enabled
        val tint = when {
            !enabled -> c.track.srgb
            filled -> c.onFill.srgb
            type == ButtonType.DANGER -> c.danger.srgb
            else -> LocalMetroInk.current ?: c.ink.srgb
        }
        val fill = c.fill.srgb
        val press = if (filled) Color.Black else c.ink.srgb
        Box(
            modifier = Modifier.size(size.touch).drawBehind {
                if (filled) drawCircle(fill)
                if (pressed) drawCircle(press, alpha = PRESSED_ALPHA)
            },
            contentAlignment = Alignment.Center
        ) { NamedIcon(icon, description, size.icon, tint) }
    }

    /** A button with a word, that acts. */
    @Composable
    private fun WordButton(text: String, onClick: () -> Unit, strong: Boolean = false, danger: Boolean = false, enabled: Boolean = true) {
        val type = when {
            danger -> ButtonType.DANGER
            strong -> ButtonType.PRIMARY
            else -> ButtonType.DEFAULT
        }
        Pressable(onClick = onClick, enabled = enabled) { pressed -> Block(type, enabled, pressed) { Word(text) } }
    }

    /** An icon alone a finger's target square, darkening under the finger: a month's arrow. */
    @Composable
    private fun PlainIconButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> IconSquare(icon, null, ButtonType.DEFAULT, true, pressed, false) }
    }

    /** A − or + outlined in 2 dp with corners of 4 dp, a finger's target. */
    @Composable
    private fun CounterButton(icon: String, onClick: () -> Unit) {
        val size = metroSize()
        val c = metroColors
        val radius = size.dp(RADIUS)
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.touch, size.dp(40f)).outline(metroStroke(2f), c.ink.srgb, radius).pressedTint(pressed, c.ink.srgb, radius),
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, null, size.dp(20f), c.ink.srgb) }
        }
    }

    /** A Lucide icon by name, at [size], in [tint]. */
    @Composable
    private fun NamedIcon(name: String, description: String?, size: Dp, tint: Color) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        Image(
            painter = painterResource(resource),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier.size(size)
        )
    }

    /** A stop: a round of the panel ringed in [color], [side] across. */
    @Composable
    private fun Stop(side: Dp, color: Color) {
        val ring = metroStroke(4f)
        val panel = metroColors.panel.srgb
        Box(modifier = Modifier.size(side).drawBehind {
            val r = this.size.minDimension / 2
            drawCircle(panel, r)
            drawCircle(color, r - ring.toPx() / 2, style = Stroke(ring.toPx()))
        })
    }

    /** A mark on an icon's corner: a round of [fill] ringed with the panel, [content] in its middle. */
    @Composable
    private fun Mark(fill: Color, content: @Composable () -> Unit) {
        val size = metroSize()
        val ring = metroStroke(2f)
        val panel = metroColors.panel.srgb
        Box(
            modifier = Modifier.size(size.dp(20f)).drawBehind {
                val r = this.size.minDimension / 2
                drawCircle(panel, r)
                drawCircle(fill, r - ring.toPx())
            },
            contentAlignment = Alignment.Center
        ) { content() }
    }

    /** Two rounded bars of 2 dp, [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = metroSize()
        val ink = metroColors.ink.srgb
        val bar = metroStroke(2f)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(5f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(bar).panel(ink, bar / 2)) }
        }
    }

    /** What is written in a field: a white panel with corners of 4 dp outlined in 2 dp of [line]; a finger's target tall. */
    @Composable
    private fun Boxed(line: Color, content: @Composable () -> Unit) {
        val size = metroSize()
        val radius = size.dp(RADIUS)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .panel(metroColors.panel.srgb, radius)
                .outline(metroStroke(2f), line, radius)
                .padding(horizontal = size.dp(12f), vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /** A round of the night blue gliding along a grey line of 4 dp, again and again. */
    @Composable
    private fun Train(modifier: Modifier) {
        val c = metroColors
        val size = metroSize()
        val transition = rememberInfiniteTransition(label = "loading")
        val at by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOADING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "run"
        )
        val track = c.track.srgb
        val ink = c.ink.srgb
        val rail = size.dp(4f)
        val dot = size.dp(12f)
        Box(modifier = modifier.height(dot).drawBehind {
            val t = rail.toPx()
            val r = dot.toPx() / 2
            drawRoundRect(track, Offset(0f, (this.size.height - t) / 2), GeometrySize(this.size.width, t), CornerRadius(t / 2, t / 2))
            drawCircle(ink, r, Offset(r + at * (this.size.width - 2 * r), this.size.height / 2))
        })
    }

    /**
     * A window over the screen, sliding in: a white panel with corners of 4 dp under a line of the
     * night blue of 6 dp, its content, its actions under it.
     */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            SlideIn {
                val size = metroSize()
                val c = metroColors
                val radius = size.dp(RADIUS)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .panel(c.panel.srgb, radius)
                        .headLine(metroStroke(DIALOG_LINE), c.ink.srgb, radius)
                        .padding(start = size.dp(16f), end = size.dp(16f), top = size.dp(DIALOG_LINE + 16f), bottom = size.dp(12f)),
                    verticalArrangement = Arrangement.spacedBy(size.dp(16f))
                ) {
                    CompositionLocalProvider(LocalMetroInk provides null, LocalMetroLine provides null, LocalMetroOnLine provides false) {
                        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(8f)), content = content)
                        app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
                    }
                }
            }
        }
    }

    /** A number in white figures on a night-blue panel, between a + above and a − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = metroSize()
        val c = metroColors
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(6f))) {
            CounterButton("plus") { onChange((value + 1) % modulo) }
            Box(modifier = Modifier.panel(c.band.srgb, size.dp(RADIUS)).padding(horizontal = size.dp(10f), vertical = size.dp(8f))) {
                Line(String.format(Locale.ROOT, "%02d", value), size.number, c.onFill.srgb)
            }
            CounterButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a fill across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = MetroToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            MetroToasts.dismiss(shown.id)
        }
        val size = metroSize()
        val c = metroColors
        Box(modifier = modifier.fillMaxWidth().padding(spacing(Spacing.EDGE))) {
            SlideIn {
                Box(modifier = Modifier.fillMaxWidth().panel(c.fill.srgb, size.dp(RADIUS)).padding(horizontal = size.dp(16f), vertical = size.dp(14f))) {
                    Line(shown.message, size.body, c.onFill.srgb)
                }
            }
        }
    }

    /**
     * How the motifs are laid (docs/design/tool-texture.md): a cell's unit a dp at the size step,
     * smoothed, so a motif grows with everything else; an ICON motif's icon sixteen units, sixteen
     * around it.
     */
    @Composable
    private fun textureStyle(): app.treelune.core.themes.TextureStyle {
        val unit = with(LocalDensity.current) { metroSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = false, iconUnits = 16, iconGapUnits = 16)
    }

    /** A panel's corners, an icon's tile's, in dp. */
    private const val RADIUS = 4f
    private const val ICON_TILE_RADIUS = 10f

    /** The line over a tile, a dialog's, a section's, a chosen tab's, in dp; a stop's side. */
    private const val TILE_LINE = 6f
    private const val DIALOG_LINE = 6f
    private const val SECTION_LINE = 4f
    private const val TAB_LINE = 4f
    private const val STOP = 14f

    /** A roundel's side over the icon's room; the icon's side in it, over the same room. */
    private const val BADGE_SCALE = 1.33f
    private const val ICON_IN_BADGE = 0.8f

    /** The page icon's room, in dp, before its roundel. */
    private const val PAGE_ICON = 30f

    /** The subtitle's white on the band, softer than the title's. */
    private const val SUBTITLE_ALPHA = 0.75f

    /** Degrees between two colours the hue band computes. */
    private const val HUE_STRIP_STEP = 10

    private const val MAX_GRID_DP = 480
    private const val ASIDE_ALPHA = 0.4f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val LOADING_MS = 1400
    private const val THINKING_STEPS = 4
    private const val THINKING_STEP_MS = 300
}

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object MetroToasts {
    data class Shown(val id: Long, val message: String, val millis: Long)

    private val queue = mutableStateListOf<Shown>()
    private var next = 0L

    val current: Shown? get() = queue.firstOrNull()

    fun show(message: String, millis: Long) {
        queue.add(Shown(next++, message, millis))
    }

    fun dismiss(id: Long) {
        queue.removeAll { it.id == id }
    }
}
