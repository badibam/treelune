package app.treelune.themes.ink

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.treelune.R
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The ink theme (docs/design/ink-theme.md): the app as an ink wash painting. Cream paper, black
 * ink and a single seal red, plenty of empty space: a tile is a wash of irregular edge with no
 * outline, a group's title is spaced over a stroke of the brush, and every line, the icons
 * included, is traced by one brush (InkBrush) in Literata's company (InkSize). Under the finger a
 * wash darkens, a window fades in slowly (InkPieces); two palettes of a handful of OKLCH numbers
 * (InkPalette). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object InkTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "ink").theme("name")

    /** Lucide's drawings, traced by the theme's brush (InkIcon). */
    override val iconSource = IconSource.LUCIDE

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

    /** Sixteen between two columns, left to the grid: a wash fades out before its edge, and air parts the tiles. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { 16f.dp.roundToPx() }

    /** Twenty between two rows. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { 20f.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, Les sons). */
    override fun sound(signal: UISignal): Int? = null

    /** Eight above and below, fourteen at the sides, as the mockup's tile; eight round an icon alone. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp =
        if (displayMode == DisplayMode.ICON) inkSize().dp(8f) else inkSize().dp(TILE_PAD)

    @Composable
    override fun spacing(spacing: Spacing): Dp = (
        when (spacing) {
            Spacing.XS -> 4f
            Spacing.S -> 8f
            Spacing.M -> 16f
            Spacing.L -> 24f
            Spacing.XL -> 40f
            Spacing.EDGE -> 20f
        }
    ).dp

    // =====================================
    // INTERACTIVE
    // =====================================

    @Composable
    override fun Button(type: ButtonType, size: Size, state: ComponentState, onClick: () -> Unit, content: @Composable () -> Unit) {
        val enabled = state == ComponentState.NORMAL || state == ComponentState.SUCCESS
        Pressable(onClick = onClick, enabled = enabled) { pressed ->
            ButtonFace(type, enabled, pressed) { content() }
        }
    }

    /** A pebble of ink, its icon in the paper's colour. Pressed, its ink thins a step. */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = inkSize()
        val c = inkColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.floating)
                    .background(if (pressed) c.pressedSolid else c.solid, BlobShape(action.iconName.hashCode())),
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(26f), c.onSolid)
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
                IconButton((icon ?: action.iconName), label, buttonType, enabled, pressed, active)
            } else {
                // Switched on, a button is a stroke full of ink, as the main one
                ButtonFace(if (active) ButtonType.PRIMARY else buttonType, enabled, pressed) { Word(label) }
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

    /** Each type in its size and weight; a group's or a tile's name (HEADING) bold and spaced. */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = inkSize()
        val c = inkColors
        val override = LocalInkInk.current
        val (style, ink) = when (type) {
            TextType.TITLE -> size.title to (override ?: c.ink)
            TextType.SUBTITLE -> size.subtitle to (override ?: c.ink)
            TextType.HEADING -> size.heading to (override ?: c.ink)
            TextType.BODY -> size.body to (override ?: c.ink)
            TextType.STRONG -> size.strong to (override ?: c.ink)
            TextType.CAPTION -> size.caption to (override ?: c.dim)
            TextType.LABEL -> size.caption.copy(fontWeight = FontWeight.SemiBold) to (override ?: c.dim)
            TextType.ERROR -> size.body to c.error
            TextType.WARNING -> size.body to c.warning
        }
        Line(text, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /** The weight of STRONG, cut at a text's optical size; Literata's own italic, at both its weights. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = FontWeight.SemiBold)
        Emphasis.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    }

    /**
     * A card is a pale wash, full under it, so that a page's motif never runs under its text, its
     * edge wavering and fading into the paper round it (cardWash); a highlighted one a step darker. A section's header is its content over a stroke of the brush,
     * as a group of tiles' title, on the motif.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = inkSize()
        val c = inkColors
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(s.dp(6f))) {
                content()
                Box(modifier = Modifier.fillMaxWidth().height(s.dp(6f)).brushed(InkBrush.Shape.LINE, s.dp(GROUP_RULE), c.ink))
            }
            CardType.DEFAULT -> Box(
                modifier = Modifier.fillMaxWidth()
                    .cardWash(if (highlight) c.pressedWash else c.cardWash, pebble(s, 10f, 14f, 12f, 9f), with(LocalDensity.current) { s.dp(CARD_FEATHER).toPx() }, CARD_SEED)
                    .padding(horizontal = s.dp(16f), vertical = s.dp(10f))
            ) { content() }
        }
    }

    /** A drop of the state's colour. */
    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color, BlobShape(STATUS_SEED)))
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val c = inkColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }
    }

    /** A small dab of gouache of its colour before its text in the ink: the user's colours are marks, never a fill. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = inkSize()
        val c = inkColors
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.dp(6f))) {
            // A tag's mark keeps its colour whole: a mark, held to 3 on the paper
            Box(modifier = Modifier.size(size.dp(10f)).gouache(c.tag(color), color.ordinal, reach = 0.55f))
            Line(text, size.label.copy(letterSpacing = 0.sp, fontSize = size.label.fontSize * 1.08f), LocalInkInk.current ?: c.ink, maxLines = 1)
        }
    }

    /** A stroke of the brush tapering off over a track of pale wash; never red, even full. */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = inkSize()
        val c = inkColors
        val f = fraction.coerceIn(0f, 1f)
        Box(
            modifier = Modifier.fillMaxWidth().height(size.dp(10f))
                .drawBehind { drawSpread(c.trackWash, center) }
        ) {
            if (f > 0f) Box(modifier = Modifier.fillMaxWidth(f).fillMaxHeight().brushed(InkBrush.Shape.LINE, size.dp(6f), LocalInkInk.current ?: c.ink))
        }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = inkSize().caption.copy(color = inkColors.dim)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        InkDrawing.Draw(drawing, drawingTextStyle(), inkColors, modifier)
    }

    /** A thin stroke of the brush across the width. */
    @Composable
    override fun Divider() {
        val size = inkSize()
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(6f)).height(size.dp(5f)).brushed(InkBrush.Shape.LINE, size.dp(1.2f), inkColors.ink))
    }

    /** Two short strokes, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = inkSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = inkSize().dp(4f))) { Bars(inkSize().dp(24f)) }
    }

    /** Lifted, an item takes a darker wash and stands off; put down, it falls back. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted) {
        val size = inkSize()
        Box(modifier = if (lifted) Modifier.background(inkColors.pressedWash, pebble(size, 10f, 14f, 12f, 9f)) else Modifier) { content() }
    }

    /**
     * A dark wash laid over the page, its words in the paper's colour; a word touched is
     * underlined with the brush. Words a finger's target tall, going to a second line when they
     * do not hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = inkSize()
        val c = inkColors
        FlowRow(modifier = Modifier.background(c.deepWash, pebble(size, 14f, 10f, 16f, 12f)).padding(horizontal = size.dp(4f))) {
            actions.forEach { action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).padding(horizontal = size.dp(12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Underlined(pressed, c.onSolid) { Line(action.label, size.button.copy(fontWeight = FontWeight.Normal), c.onSolid, maxLines = 1) }
                    }
                }
            }
        }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = InkPalettes.colors(mode, 0).tag(color)

    // =====================================
    // FEEDBACK
    // =====================================

    /** A dark wash at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { InkToasts.show(message, shown) }
    }

    /**
     * A dark wash, its message in the paper's colour, its action underlined; a drop of the state's
     * colour before the message, but for a mere information.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = inkSize()
        val c = inkColors
        val mark = when (type) {
            FeedbackType.SUCCESS -> c.success
            FeedbackType.ERROR -> c.error
            FeedbackType.WARNING -> c.warning
            FeedbackType.INFO -> null
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .background(c.deepWash, pebble(size, 14f, 10f, 16f, 12f)).padding(start = size.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            mark?.let { StatusIndicator(it, size.dp(8f)) }
            Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, c.onSolid) }
            if (action != null && onAction != null) {
                Pressable(onClick = onAction) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Underlined(true, c.onSolid) { Line(action, size.button, if (pressed) c.dim else c.onSolid, maxLines = 1) }
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

    /** An ensō tracing itself, again and again, as large as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val s = inkSize()
        val side = s.dp(
            when (size) {
                Size.XS -> 16f
                Size.S -> 24f
                Size.M -> 36f
                Size.L -> 48f
                Size.XL, Size.XXL -> 64f
            }
        )
        TracingEnso(side)
    }

    /** Lucide's drawing traced by the brush; a background, when asked, is a soft square of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        val ink = tint ?: LocalInkInk.current ?: inkColors.ink
        InkIcon(
            resourceId, size, ink, contentDescription,
            Modifier.let { m -> background?.let { m.background(it, RoundedCornerShape(size * 0.25f)) } ?: m }
        )
    }

    /**
     * A zone's or a tool's icon traced in the ink, on a dab of gouache of its colour reaching past
     * it, its shape the icon's own. The dab takes no room: a neutral icon takes the same, so names
     * beside icons line up.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val c = inkColors
        val ink = LocalInkInk.current ?: c.ink
        Box(modifier = Modifier.size(size).let { if (color != null) it.gouache(c.gouache(color), resourceId) else it }) {
            InkIcon(resourceId, size, ink)
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
     * A month on seven columns, its days in figures; the day chosen a drop of ink, today circled
     * by an ensō. The month and its year spaced, between two chevrons.
     */
    @Composable
    override fun DatePicker(selectedDate: String, onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
        val zone = AppConfigManager.getDateTimeConfig().getZoneId()
        // "" means nothing chosen yet, and the picker opens on today by contract
        val initial = (DateUtils.parseDateForFilter(selectedDate) ?: System.currentTimeMillis())
            .let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        var chosen by remember { mutableStateOf(initial) }
        var month by remember { mutableStateOf(YearMonth.from(initial)) }
        val today = LocalDate.now(zone)
        val locale = Locale.getDefault()
        val size = inkSize()
        val c = inkColors
        val s = Strings.`for`(context = LocalContext.current)

        DialogFrame(onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onDateSelected(DateUtils.formatDateForDisplay(chosen.atStartOfDay(zone).toInstant().toEpochMilli()))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlainIconButton("chevron-left") { month = month.minusMonths(1) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val name = month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale)
                        .replaceFirstChar { it.titlecase(locale) } + " " + month.year
                    Line(name, size.title.copy(fontSize = size.title.fontSize * 0.82f, letterSpacing = size.heading.letterSpacing), c.ink, maxLines = 1)
                }
                PlainIconButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = WeekFields.of(locale).firstDayOfWeek
            val days = (0 until 7).map { first.plus(it.toLong()) }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(4f)), contentAlignment = Alignment.Center) {
                            Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale).uppercase(locale), size.label, c.dim)
                        }
                    }
                }
                val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
                val cells = lead + month.lengthOfMonth()
                (0 until (cells + 6) / 7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val n = week * 7 + column - lead + 1
                            Box(modifier = Modifier.weight(1f).height(size.dp(40f)), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    Pressable(onClick = { chosen = date }, modifier = Modifier.fillMaxSize()) { pressed ->
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            val drop = Modifier.size(size.dp(34f))
                                            when {
                                                lit -> Box(drop.background(c.solid, BlobShape(DAY_SEED)))
                                                pressed -> Box(drop.background(c.pressedWash, BlobShape(DAY_SEED)))
                                                date == today -> Box(Modifier.size(size.dp(36f)).brushed(InkBrush.Shape.ENSO, size.dp(1.4f), c.ink, 0.92f))
                                            }
                                            val style = if (lit) size.strong else size.body
                                            Line(n.toString(), style, if (lit) c.onSolid else c.ink)
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

    /** Two counters, hours and minutes, in large figures, each between a + and a − traced by the brush. */
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(inkSize().dp(10f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", inkSize().number, inkColors.ink)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The paper; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(inkColors.ground)) {
            CompositionLocalProvider(LocalInkInk provides null) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /**
     * A textured page's whole ground: the paper and the motif in the ink over it, as on the
     * thing's tile, whatever its icon's colour; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val ground = inkColors.ground
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), ground, textureStyle())
        val modifier = if (brush == null) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(ground).background(brush)
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = inkSize()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(4f), vertical = size.dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    override fun ZoneCardContainer(texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(DisplayMode.CONDENSED, texture, onClick, onLongClick, content)

    /** A round seal of red with an exclamation mark. */
    @Composable
    override fun WaitingMark() = Seal(inkSize().dp(20f), "!", round = true)

    /** A cell of the grid in edit mode: a round of very pale wash. */
    @Composable
    override fun GridCell() {
        Box(modifier = Modifier.fillMaxSize().colorWash(inkColors.ink, CELL_ALPHA))
    }

    /**
     * The tile being moved is lifted: its wash darker, standing off up and to the left; put down,
     * it falls back. The others step back, faded, the cells showing through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen) { content() }
        }
    }

    /** An ensō in the ink, drawn in one stroke: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = inkSize()
        Box(modifier = Modifier.size(size.dp(16f)).brushed(InkBrush.Shape.ENSO, size.dp(1.4f), LocalInkInk.current ?: inkColors.ink))
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, texture, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it: a wash of irregular edge, dense at its heart and
     * gone at its edge in every direction, no outline, bleeding into half the gaps around it; its motif within
     * the same edge. An icon's tile has no
     * wash at rest, its icon alone on the paper. Pressed or lifted, the wash darkens a step.
     */
    @Composable
    private fun Tile(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = inkColors
        val bare = displayMode == DisplayMode.ICON
        val frame = tileFrame(displayMode)
        val lifted = LocalInkLifted.current
        // The motif in the ink, whatever the icon's colour: the user's colours are marks, never a fill
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), c.ground, textureStyle())
        val bleedX = gridColumnGapPx() / 2f
        val bleedY = gridRowGapPx() / 2f
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            val wash = when {
                pressed || lifted -> c.pressedWash
                bare -> Color.Transparent
                else -> c.wash
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tileWash(wash, brush, bleedX, bleedY)
                    .let { if (bare) it.padding(frame) else it.padding(horizontal = inkSize().dp(TILE_SIDE), vertical = frame) }
            ) { content() }
        }
    }

    /**
     * The page's icon on the wash of its colour, above its title; the title centred and spaced, a
     * seal of red holding its initial beside it, shrunk to hold in two lines, never under a
     * dialog's title. The subtitle under it, in the secondary ink. A textured page's motif is its
     * ground's (PageGround).
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = inkSize()
        val c = inkColors
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(Spacing.EDGE)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(size.dp(10f))
        ) {
            // The app's mark: the launcher's monochrome layer, in the ink
            if (appMark) {
                // The layer's central 72 units of 108 hold the drawing: drawn larger and cut, it fills the icon's room
                Box(modifier = Modifier.size(size.dp(28f)).clipToBounds(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(c.ink),
                        modifier = Modifier.requiredSize(size.dp(28f) * LAUNCHER_LAYER)
                    )
                }
            } else icon?.let { name ->
                if (iconColor == null) NamedIcon(name, null, size.dp(28f), c.ink)
                else app.treelune.core.ui.UI.ItemIcon(name, iconColor, size.dp(28f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.dp(12f), Alignment.CenterHorizontally)) {
                BasicText(
                    text = title,
                    style = size.pageTitle.copy(
                        color = c.ink,
                        textAlign = TextAlign.Center,
                        hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp),
                    modifier = Modifier.weight(1f, fill = false)
                )
                initial(title)?.let { Seal(size.dp(26f), it) }
            }
            subtitle?.let { Line(it, size.label, c.dim, align = TextAlign.Center) }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In the secondary ink, a little spaced; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, inkSize().label, inkColors.dim)
    }

    /**
     * No box: a thin stroke of the brush under the text, heavier while the field has the focus, in
     * the error's colour when it is wrong; the label above, and again inside while it is empty.
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
        labelAbove: Boolean
    ) {
        val size = inkSize()
        val c = inkColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (labelAbove) FieldLabel(label, required)
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
                Field(focused || error, if (error) c.error else c.ink) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            readOnly = state == ComponentState.READONLY,
                            enabled = state != ComponentState.DISABLED,
                            textStyle = size.body.copy(color = if (error) c.error else c.ink),
                            cursorBrush = SolidColor(c.ink),
                            keyboardOptions = FieldInput.keyboardOptions(fieldType),
                            visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                            interactionSource = source,
                            modifier = Modifier
                                .weight(1f)
                                .let { m -> fieldModifier.focusRequester?.let { m.focusRequester(it) } ?: m }
                                .let { m -> fieldModifier.onFocusChanged?.let { m.onFocusChanged(it) } ?: m },
                            decorationBox = { inner ->
                                Box {
                                    if (value.text.isEmpty()) Line(label, size.body, c.dim, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        if (isPassword) {
                            Box(modifier = Modifier.combinedClickable(onClick = { revealed = !revealed })) {
                                NamedIcon(if (revealed) "eye-off" else "eye", null, size.icon, c.dim)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The choice shown as a field, its icon before it, a chevron at its end; the options in a
     * panel of the dialog's frame, under their sections' titles, the chosen one on a pale wash.
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
        val size = inkSize()
        val c = inkColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Field(pressed, c.ink) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OptionIconOf(selected)
                        Box(modifier = Modifier.weight(1f)) { Line(shown, size.body, c.ink, maxLines = 1) }
                        NamedIcon("chevron-down", null, size.dp(20f), c.ink)
                    }
                }
            }
        }
        if (open) {
            DialogFrame(onDismiss = { open = false }, actions = {}) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { index, option ->
                        // A section's title above its first option, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(16f), bottom = size.dp(4f))) {
                                Line(section, size.heading, c.dim)
                            }
                        }
                        val chosen = index == selected
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (chosen || pressed) it.background(if (pressed) c.pressedWash else c.cardWash, pebble(size, 10f, 14f, 12f, 9f)) else it }
                                    .padding(horizontal = size.dp(14f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OptionIconOf(index)
                                Box(modifier = Modifier.weight(1f)) { Line(option, if (chosen) size.strong else size.body, c.ink) }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A round traced by the brush; checked, the same round holding a small round seal of red. */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = inkSize()
        val c = inkColors
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                Box(modifier = Modifier.size(size.dp(22f)), contentAlignment = Alignment.Center) {
                    if (pressed) Box(Modifier.size(size.dp(22f)).background(c.pressedWash, BlobShape(CHECK_SEED)))
                    Box(Modifier.size(size.dp(22f)).brushed(InkBrush.Shape.RING, size.dp(1.6f), c.ink))
                    if (checked) Seal(size.dp(13f), null, round = true)
                }
                label?.let { Line(it, size.body, c.ink) }
            }
        }
    }

    /** The label across, a pebble traced at the end whose round knob slides: full of ink when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = inkSize()
        val c = inkColors
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink) }
                Box(
                    modifier = Modifier
                        .size(size.dp(46f), size.dp(26f))
                        .let {
                            if (checked) it.padding(size.dp(1.5f)).background(c.solid, BlobShape(CHECK_SEED))
                            else it.brushed(InkBrush.Shape.PEBBLE, size.dp(1.4f), c.ink)
                        }
                        .padding(horizontal = size.dp(6f)),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.size(size.dp(14f)).background(if (checked) c.onSolid else c.ink, BlobShape(KNOB_SEED)))
                }
            }
        }
    }

    /** The tab shown in the ink, underlined by the brush; the others in the secondary ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = inkSize()
        val c = inkColors
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(size.dp(24f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    val shown = index == selected
                    Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) {
                        Underlined(shown || pressed, c.ink, heavy = true) {
                            Line(label, if (shown) size.button else size.button.copy(fontWeight = FontWeight.Normal), if (shown || pressed) c.ink else c.dim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /** Two words side by side: the chosen one on a wash of the track's ink. */
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
        val size = inkSize()
        val c = inkColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(modifier = if (compact) Modifier else Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(4f))) {
                listOf(true to trueLabel, false to falseLabel).forEach { (answer, text) ->
                    val chosen = value == answer
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        modifier = if (compact) Modifier else Modifier.weight(1f)
                    ) { pressed ->
                        Box(
                            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                                .heightIn(min = size.touch)
                                .let { if (chosen || pressed) it.background(if (pressed) c.pressedWash else c.trackWash, pebble(size, 18f, 14f, 20f, 12f)) else it }
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text, if (chosen) size.button else size.button.copy(fontWeight = FontWeight.Normal), c.ink, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * The red at each shift, saturated, along a band of soft ends; the shift chosen a ring of ink
     * over a ring of the paper. A touch or a drag anywhere on it answers.
     */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) {
        val size = inkSize()
        val c = inkColors
        val mode = CurrentTheme.paletteMode
        val colors = remember(mode, shifts) {
            (shifts step HUE_STRIP_STEP).map { InkPalettes.colors(mode, it).accent } + InkPalettes.colors(mode, shifts.last).accent
        }
        val slot = size.dp(16f)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .horizontalPick { onValueChange(app.treelune.core.ui.RangePick.valueAt(it, shifts)) },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(size.dp(24f)).background(Brush.horizontalGradient(colors), pebble(size, 12f, 9f, 13f, 10f)))
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - slot) * app.treelune.core.ui.RangePick.fractionOf(value, shifts))
                    .size(slot, size.dp(32f))
                    .drawBehind {
                        val w = 2.dp.toPx()
                        drawOval(c.ink, style = Stroke(w))
                        drawOval(c.ground, topLeft = Offset(w, w), size = androidx.compose.ui.geometry.Size(this.size.width - 2 * w, this.size.height - 2 * w), style = Stroke(w))
                    }
            )
        }
    }

    /**
     * The value above in bold figures, a thin stroke of the brush across the width and a drop of
     * ink for the knob; a touch or a drag anywhere on it answers the stop under the finger. No
     * answer yet shows the stroke in the secondary ink and "—".
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
        val size = inkSize()
        val c = inkColors
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
                Line(value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—", size.sliderValue, if (value != null) c.ink else c.dim)
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
                val knob = size.dp(18f)
                val trackWidth = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(size.dp(5f)).brushed(InkBrush.Shape.LINE, size.dp(1.4f), if (fraction != null) c.ink else c.dim))
                    if (fraction != null) {
                        val travel = with(LocalDensity.current) { (trackWidth - knob).toPx() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                                .size(knob)
                                .background(c.ink, BlobShape(KNOB_SEED))
                        )
                    }
                }
            }
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Line(minLabel, size.caption, c.dim)
                    Line(maxLabel, size.caption, c.dim)
                }
            }
        }
    }

    // =====================================
    // PALETTES
    // =====================================

    /** For what Material still draws at the app's root: the palette's colours under its names. */
    override fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme {
        val c = InkPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.ink, onPrimary = c.ground,
            secondary = c.ink, onSecondary = c.ground,
            tertiary = c.accent, onTertiary = c.onAccent,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.cardWash, onSurfaceVariant = c.dim,
            error = c.error, onError = c.ground,
            outline = c.ink, outlineVariant = c.trackWash
        ) else lightColorScheme(
            primary = c.ink, onPrimary = c.ground,
            secondary = c.ink, onSecondary = c.ground,
            tertiary = c.accent, onTertiary = c.onAccent,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.cardWash, onSurfaceVariant = c.dim,
            error = c.error, onError = c.ground,
            outline = c.ink, outlineVariant = c.trackWash
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /** Three drops of ink spreading and paling one after the other: the AI's wait asks nothing of the user. */
    @Composable
    override fun AIThinkingIndicator() {
        val size = inkSize()
        val c = inkColors
        val transition = rememberInfiniteTransition(label = "thinking")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(THINKING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(12f)), contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy(size.dp(12f)), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { i ->
                    // Each drop lands when the phase reaches it, then spreads and pales over the next step
                    val t = ((phase - i + 3f) % 3f) / 3f
                    Box(
                        modifier = Modifier.size(size.dp(10f))
                            .drawBehind {
                                val r = this.size.minDimension / 2 * (1f + 0.6f * t)
                                drawCircle(c.ink, r, alpha = 1f - 0.75f * t)
                            }
                    )
                }
            }
        }
    }

    /**
     * The user's message is a pale wash pushed to the right; the AI's has no bubble, its text on
     * the paper; the system's in the secondary ink. Both on the paper, full: a long text never
     * stands on a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = inkSize()
        val c = inkColors
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .background(c.cardWash, pebble(size, 16f, 12f, 6f, 14f))
                        .padding(horizontal = size.dp(14f), vertical = size.dp(10f))
                ) { content() }
            }
            MessageSender.AI -> Box(modifier = Modifier.fillMaxWidth().background(c.ground).padding(vertical = size.dp(4f))) { content() }
            MessageSender.SYSTEM -> Box(modifier = Modifier.fillMaxWidth().background(c.ground).padding(vertical = size.dp(4f))) {
                CompositionLocalProvider(LocalInkInk provides c.dim) { content() }
            }
        }
    }

    /** A pale wash with a small seal of red in its corner, a sign that the user is asked: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val size = inkSize()
        Box(modifier = Modifier.fillMaxWidth().background(inkColors.cardWash, pebble(size, 12f, 9f, 13f, 10f))) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = size.dp(14f), end = size.dp(28f), top = size.dp(12f), bottom = size.dp(12f)),
                verticalArrangement = Arrangement.spacedBy(size.dp(8f))
            ) {
                Text(title, TextType.TITLE, true, null)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(8f)), content = actions)
            }
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(size.dp(10f))) { Seal(size.dp(14f), null) }
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

    /** A button's word, in the ink its stroke imposes. */
    @Composable
    private fun Word(text: String) {
        Line(text, inkSize().button, LocalInkInk.current ?: inkColors.ink, maxLines = 1)
    }

    /**
     * A button a finger's target tall: a touch of the brush full of ink for the main one, a pebble
     * traced round a secondary one, red for a destructive one, its word alone underlined by the
     * brush for the others; a pale wash when disabled. Pressed, its ink thins a step, or a wash
     * comes under its word.
     */
    @Composable
    private fun ButtonFace(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = inkSize()
        val c = inkColors
        val shape = pebble(size, 22f, 18f, 24f, 16f)
        // What the button is drawn as: a fill, a traced outline, or its word alone
        val fill: Color?
        val line: Color?
        val word: Color
        when {
            !enabled -> { fill = if (type == ButtonType.PRIMARY) c.trackWash else null; line = null; word = c.dim }
            type == ButtonType.PRIMARY -> { fill = if (pressed) c.pressedSolid else c.solid; line = null; word = c.onSolid }
            type == ButtonType.SECONDARY -> { fill = if (pressed) c.cardWash else null; line = c.ink; word = c.ink }
            type == ButtonType.DANGER -> { fill = if (pressed) c.cardWash else null; line = c.accent; word = c.accent }
            else -> { fill = if (pressed) c.cardWash else null; line = null; word = c.ink }
        }
        // Its word alone: a tertiary or neutral button, or a disabled one that is not the main one
        val plain = type !in OUTLINED || (!enabled && type != ButtonType.PRIMARY)
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .let { m -> fill?.let { m.background(it, shape) } ?: m }
                .let { m -> line?.let { m.padding(vertical = size.dp(4f)).brushed(InkBrush.Shape.PEBBLE, size.dp(1.6f), it) } ?: m }
                .padding(horizontal = if (plain) size.dp(6f) else size.dp(18f)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalInkInk provides word) {
                if (plain && enabled) Underlined(true, word) { content() } else content()
            }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; switched on ([active]) or the
     * main one, on a pebble of ink. Pressed, a wash comes under it.
     */
    @Composable
    private fun IconButton(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = inkSize()
        val c = inkColors
        val block = (active || type == ButtonType.PRIMARY) && enabled
        val tint = when {
            !enabled -> c.dim
            block -> c.onSolid
            type == ButtonType.DANGER -> c.accent
            else -> c.ink
        }
        Box(
            modifier = Modifier.size(size.touch).let {
                when {
                    block -> it.background(if (pressed) c.pressedSolid else c.solid, BlobShape(icon.hashCode()))
                    pressed -> it.background(c.pressedWash, BlobShape(icon.hashCode()))
                    else -> it
                }
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
        Pressable(onClick = onClick, enabled = enabled) { pressed -> ButtonFace(type, enabled, pressed) { Word(text) } }
    }

    /** An icon alone a finger's target square, a wash under it while pressed: a month's arrow. */
    @Composable
    private fun PlainIconButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> IconButton(icon, null, ButtonType.DEFAULT, true, pressed, false) }
    }

    /** A Lucide icon by name, traced by the brush, at [size], in [tint]. */
    @Composable
    private fun NamedIcon(name: String, description: String?, size: Dp, tint: Color) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        InkIcon(resource, size, tint, description)
    }

    /** Two short strokes of the brush, [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = inkSize()
        val ink = inkColors.ink
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(2f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(size.dp(4f)).brushed(InkBrush.Shape.LINE, size.dp(1.6f), ink)) }
        }
    }

    /** What is written in a field, over a thin stroke of the brush, heavier when [strong]; a finger's target tall. */
    @Composable
    private fun Field(strong: Boolean, color: Color, content: @Composable () -> Unit) {
        val size = inkSize()
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(10f), bottom = size.dp(4f)), contentAlignment = Alignment.CenterStart) { content() }
            Box(modifier = Modifier.fillMaxWidth().height(size.dp(if (strong) 7f else 5f)).brushed(InkBrush.Shape.LINE, size.dp(if (strong) 2f else 1.2f), color))
        }
    }

    /** [content] over a short stroke of the brush as wide as it, when [shown]; the same room when not. */
    @Composable
    private fun Underlined(shown: Boolean, color: Color, heavy: Boolean = false, content: @Composable () -> Unit) {
        val size = inkSize()
        Column(modifier = Modifier.width(IntrinsicSize.Max), horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Box(
                modifier = Modifier.fillMaxWidth().height(size.dp(if (heavy) 6f else 5f))
                    .let { if (shown) it.brushed(InkBrush.Shape.LINE, size.dp(if (heavy) 2.4f else 1.2f), color) else it }
            )
        }
    }

    /** An ensō [side] across, tracing itself, then fading, again and again. */
    @Composable
    private fun TracingEnso(side: Dp) {
        val c = inkColors
        val transition = rememberInfiniteTransition(label = "loading")
        val at by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOADING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "trace"
        )
        // Three quarters of the time to trace it, the last quarter to let it fade
        val sweep = (at / 0.75f).coerceIn(0.05f, 1f) * InkBrush.ENSO_SWEEP
        val fade = if (at < 0.75f) 1f else 1f - (at - 0.75f) / 0.25f
        Box(modifier = Modifier.size(side).alpha(fade).brushed(InkBrush.Shape.ENSO, side * 0.06f, LocalInkInk.current ?: c.ink, sweep))
    }

    /**
     * A window over the screen, fading in: the paper under a stroke of the brush, its content, its
     * actions under it.
     */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            FadeIn {
                val size = inkSize()
                val c = inkColors
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.ground, pebble(size, 6f, 10f, 8f, 5f))
                        .padding(start = size.dp(18f), end = size.dp(18f), top = size.dp(14f), bottom = size.dp(12f)),
                    verticalArrangement = Arrangement.spacedBy(size.dp(14f))
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(size.dp(7f)).brushed(InkBrush.Shape.LINE, size.dp(DIALOG_RULE), c.ink))
                    CompositionLocalProvider(LocalInkInk provides null) {
                        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(8f)), content = content)
                        app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
                    }
                }
            }
        }
    }

    /** A number in large figures between a + above and a − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = inkSize()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            PlainIconButton("plus") { onChange((value + 1) % modulo) }
            Line(String.format(Locale.ROOT, "%02d", value), size.number, inkColors.ink)
            PlainIconButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a dark wash across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = InkToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            InkToasts.dismiss(shown.id)
        }
        val size = inkSize()
        val c = inkColors
        Box(modifier = modifier.fillMaxWidth().padding(spacing(Spacing.EDGE))) {
            FadeIn {
                Box(modifier = Modifier.fillMaxWidth().background(c.deepWash, pebble(size, 14f, 10f, 16f, 12f)).padding(horizontal = size.dp(16f), vertical = size.dp(14f))) {
                    Line(shown.message, size.body, c.onSolid)
                }
            }
        }
    }

    /** A title's first letter, as its seal holds it; none for a title without one. */
    private fun initial(title: String): String? {
        val first = title.trim().firstOrNull { it.isLetterOrDigit() } ?: return null
        return first.uppercase(Locale.getDefault())
    }

    /**
     * How the motifs are laid (docs/design/tool-texture.md): a cell's unit a dp at the size step,
     * smoothed, so a motif grows with everything else; an ICON motif's icon sixteen units, sixteen
     * around it.
     */
    @Composable
    private fun textureStyle(): app.treelune.core.themes.TextureStyle {
        val unit = with(LocalDensity.current) { inkSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = false, iconUnits = 16, iconGapUnits = 16)
    }

    /** The buttons drawn as a shape (filled or traced), the others being their word alone. */
    private val OUTLINED = setOf(ButtonType.PRIMARY, ButtonType.SECONDARY, ButtonType.DANGER)

    /** A tile's air above and below its content, and at its sides, in dp. */
    private const val TILE_PAD = 8f
    private const val TILE_SIDE = 14f

    /** How far a card's wash fades past its edge, in dp, and the draw its edge wavers by. */
    private const val CARD_FEATHER = 6f
    private const val CARD_SEED = 7

    /** The draws of the round things' dabs (BlobShape), one per kind; a button's is its icon's. */
    private const val STATUS_SEED = 11
    private const val DAY_SEED = 13
    private const val CHECK_SEED = 17
    private const val KNOB_SEED = 19

    /** The brush's width under a group's title, and at the top of a window, in dp. */
    private const val GROUP_RULE = 2.6f
    private const val DIALOG_RULE = 3f

    /** A launcher layer's side over the part of it a launcher shows. */
    private const val LAUNCHER_LAYER = 108f / 72f

    /** Degrees between two colours the hue band computes. */
    private const val HUE_STRIP_STEP = 10

    private const val MAX_GRID_DP = 480
    private const val CELL_ALPHA = 0.12f
    private const val ASIDE_ALPHA = 0.4f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val LOADING_MS = 1600
    private const val THINKING_MS = 1500
}

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object InkToasts {
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
