package app.treelune.themes.eink

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
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
import app.treelune.core.themes.TextureIntensity
import app.treelune.core.themes.ThemeContract
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonDisplay
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.ComponentState
import app.treelune.core.ui.DialogType
import app.treelune.core.ui.DisplayMode
import app.treelune.core.ui.Duration
import app.treelune.core.ui.Emphasis
import app.treelune.core.ui.FeedbackType
import app.treelune.core.ui.FieldInput
import app.treelune.core.ui.FieldModifier
import app.treelune.core.ui.FieldType
import app.treelune.core.ui.Size
import app.treelune.core.ui.SliderSteps
import app.treelune.core.ui.Spacing
import app.treelune.core.ui.StatusColor
import app.treelune.core.ui.TextType
import app.treelune.core.ui.components.LocalButtonFill
import app.treelune.core.ui.confirmMessage
import app.treelune.core.ui.defaultType
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
 * The e-reader theme (docs/design/eink-theme.md): the app as on an e-reader. Pure black and white,
 * no grey: a frame of 2 dp round every tile and card, square; a group's title in capitals over a
 * rule of 3 dp; a page's title in a band of ink. Whatever the core gives in colour becomes a
 * pattern of the ink (EinkPalette): a tag's colour, an icon's, a chart's series. Nothing moves:
 * under the finger a piece inverts at once, a window appears at once on a full shadow (EinkPieces).
 * Atkinson Hyperlegible Next, the running text at 16 sp (EinkSize). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object EinkTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "eink").theme("name")

    /** Lucide's icons as they are: their even stroke reads in black and white. */
    override val iconSource = IconSource.LUCIDE

    /** Black and white only: no hue to turn, and no hue slider in the settings. */
    override val hueShifts = false

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

    /** Eight between two columns and two rows, left to the grid: each tile's frame stands apart. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { GRID_GAP_DP.dp.roundToPx() }

    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { GRID_GAP_DP.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, Les sons). */
    override fun sound(signal: UISignal): Int? = null

    /** A tile's name flush with its icon's side, as a page of a book. */
    override fun tileNameAlign(): TextAlign = TextAlign.Start

    /** The content 8 dp inside the tile's frame of 2 dp. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp = einkSize().dp(FRAME + 8f)

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

    /** A square of ink ringed with the ground, so it stands off whatever it floats over. Pressed, it inverts. */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = einkSize()
        val c = einkColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier
                    .background(c.ground)
                    .padding(einkStroke(RING))
                    .size(size.floating)
                    .background(if (pressed) c.ground else c.ink)
                    .outline(einkStroke(FRAME), c.ink),
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(26f), if (pressed) c.ink else c.ground)
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
                // Switched on, a button is a block of ink, as the main one
                Block(if (active) ButtonType.PRIMARY else buttonType, enabled, pressed, if (buttonType == ButtonType.DANGER) action.iconName else null) { Word(label) }
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

    /**
     * Each type in its size and weight, all in the one ink: the order of texts is told by weight
     * and size. A group's title (HEADING in a section's header) in spaced capitals, a tile's name
     * (HEADING elsewhere) in bold; both broken between words only.
     */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = einkSize()
        val ink = LocalEinkInk.current ?: einkColors.ink
        val section = LocalEinkSection.current
        val style = when (type) {
            TextType.TITLE -> size.title
            TextType.SUBTITLE -> size.subtitle
            TextType.HEADING -> if (section) size.heading else size.name
            TextType.BODY -> size.body
            TextType.STRONG -> size.strong
            TextType.CAPTION -> size.caption
            TextType.LABEL -> size.label
            // A state is told by its words and its weight: the theme has one ink
            TextType.ERROR -> size.strong
            TextType.WARNING -> size.body.copy(fontWeight = FontWeight.SemiBold)
        }
        val shown = if (type == TextType.HEADING && section) text.toUpperCase(LocaleList(Locale.getDefault().toLanguageTag())) else text
        Line(shown, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines, words = type == TextType.HEADING)
    }

    /** STRONG's weight; Atkinson's own italic. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        Emphasis.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    }

    /**
     * A card is a frame of 2 dp round its content, on the ground, full so that a page's motif
     * never runs under its text; a highlighted one framed in 3 dp. A section's header is its
     * content over a rule of 3 dp on the tiles' width, its title in capitals; the screens pad a
     * header's content by UI.Space.M all round, and the space under it is given back but for the
     * rule and a little air, so the rule stands close under its title.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = einkSize()
        val c = einkColors
        when (type) {
            CardType.SECTION_HEADER -> {
                val under = spacing(Spacing.M)
                val edge = spacing(Spacing.EDGE)
                val rule = einkStroke(SECTION_RULE)
                val air = s.dp(4f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val height = (placeable.height - under.roundToPx() + air.roundToPx() + rule.roundToPx()).coerceAtLeast(rule.roundToPx())
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        .drawBehind {
                            val w = rule.toPx()
                            val e = edge.toPx()
                            drawRect(c.ink, Offset(e, this.size.height - w), GeometrySize(this.size.width - 2 * e, w))
                        }
                ) {
                    CompositionLocalProvider(LocalEinkSection provides true) { content() }
                }
            }
            CardType.DEFAULT -> {
                Box(
                    modifier = Modifier.fillMaxWidth().background(c.ground)
                        .outline(einkStroke(if (highlight) 3f else FRAME), c.ink)
                        .padding(horizontal = s.dp(12f), vertical = s.dp(10f))
                ) { content() }
            }
        }
    }

    /** A square of the state's colour: the ink, the state being said by its words. */
    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color))
    }

    /** Every state in the ink: the theme has no other colour, and a state is said by its icon and its words. */
    @Composable
    override fun statusColor(status: StatusColor): Color = einkColors.ink

    /** A square of its colour's pattern before its text, the whole in a frame of 1.5 dp. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = einkSize()
        val c = einkColors
        val ink = LocalEinkInk.current ?: c.ink
        val ground = if (ink == c.ink) c.ground else c.ink
        Row(
            modifier = Modifier.outline(einkStroke(1.5f), ink).padding(start = size.dp(3f), end = size.dp(6f), top = size.dp(2f), bottom = size.dp(2f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(5f))
        ) {
            Swatch(color, size.dp(12f), ink, ground)
            Line(text, size.caption.copy(fontSize = size.caption.fontSize * (14f / 13f)), ink, maxLines = 1)
        }
    }

    /** A frame of 2 dp, 14 dp high, filled with the ink to [fraction]. */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = einkSize()
        val c = einkColors
        Box(modifier = Modifier.fillMaxWidth().height(size.dp(14f)).outline(einkStroke(FRAME), c.ink)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(size.dp(14f)).background(c.ink))
        }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = einkSize().caption.copy(color = einkColors.ink)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        EinkDrawing.Draw(drawing, drawingTextStyle(), einkColors, modifier)
    }

    /** A rule of 2 dp across the width, in the ink. */
    @Composable
    override fun Divider() {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = einkSize().dp(8f)).height(einkStroke(FRAME)).background(einkColors.ink))
    }

    /** Two bars of 3 dp, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = einkSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = einkSize().dp(4f))) { Bars(einkSize().dp(24f)) }
    }

    /** Lifted, an item stands off on its full shadow; put down, it falls back at once. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, content)

    /**
     * A block of ink, its words in the ground's colour, a rule of the ground between two; a word
     * touched inverts. Words a finger's target tall, going to a second line when they do not
     * hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = einkSize()
        val c = einkColors
        val rule = einkStroke(1f)
        FlowRow(modifier = Modifier.background(c.ink)) {
            actions.forEachIndexed { index, action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = size.touch)
                            .let { if (pressed) it.background(c.ground).outline(einkStroke(FRAME), c.ink) else it }
                            .drawBehind { if (index > 0) drawRect(c.ground, Offset.Zero, GeometrySize(rule.toPx(), this.size.height)) }
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.label, size.button, if (pressed) c.ink else c.ground, maxLines = 1)
                    }
                }
            }
        }
    }

    /**
     * The ink, whatever the colour: the theme draws a colour as a pattern of it (Tag, ItemIcon,
     * Drawing), so a colour the core paints itself (a texture's motif) stays the ink.
     */
    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = EinkPalettes.colors(mode).ink

    // =====================================
    // FEEDBACK
    // =====================================

    /** A block of ink at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { EinkToasts.show(message, shown) }
    }

    /**
     * A block of ink ringed with the ground and the ink, its message in the ground's colour, its
     * action in underlined capitals; the state's icon before the message, but for an information.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = einkSize()
        val c = einkColors
        val mark = when (type) {
            FeedbackType.SUCCESS -> "check"
            FeedbackType.ERROR -> "circle-alert"
            FeedbackType.WARNING -> "triangle-alert"
            FeedbackType.INFO -> null
        }
        Ringed {
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = size.touch).background(c.ink).padding(start = size.dp(16f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
            ) {
                mark?.let { NamedIcon(it, null, size.dp(20f), c.ground) }
                Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, c.ground) }
                if (action != null && onAction != null) {
                    Pressable(onClick = onAction) { pressed ->
                        Box(
                            modifier = Modifier.heightIn(min = size.touch).let { if (pressed) it.background(c.ground) else it }.padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(action.uppercase(Locale.getDefault()), size.button.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.08f.em(), textDecoration = TextDecoration.Underline), if (pressed) c.ink else c.ground, maxLines = 1)
                        }
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

    /** Three squares filling one after the other, by jumps: the squares as large as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val side = when (size) {
            Size.XS -> 6f
            Size.S -> 8f
            Size.M -> 10f
            Size.L -> 14f
            Size.XL, Size.XXL -> 18f
        }
        Squares(einkSize().dp(side))
    }

    /** Lucide's drawing; a background, when asked, is a square of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint ?: LocalEinkInk.current ?: einkColors.ink),
            modifier = Modifier.size(size).let { m -> background?.let { m.background(it) } ?: m }
        )
    }

    /**
     * A zone's or a tool's icon in the ink, its colour a square of 8 dp of its pattern at its lower
     * right corner, framed. The square stands within the icon's room: a neutral icon takes the
     * same, so names beside icons line up.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val s = einkSize()
        val c = einkColors
        val ink = LocalEinkInk.current ?: c.ink
        val ground = if (ink == c.ink) c.ground else c.ink
        Box(modifier = Modifier.size(size)) {
            Image(painter = painterResource(resourceId), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(size))
            if (color != null) {
                // A ring of what is under it round the square, so it stands off the icon's strokes
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .background(ground)
                        .padding(einkStroke(1f))
                ) { Swatch(color, s.dp(8f), ink, ground) }
            }
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
     * A month on seven columns, its days in figures; the day chosen inverted, today framed in
     * 2 dp. The month and its year in heavy letters between two chevrons.
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
        val size = einkSize()
        val c = einkColors
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
                    val name = month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale).replaceFirstChar { it.titlecase(locale) } + " " + month.year
                    Line(name, size.subtitle.copy(fontWeight = FontWeight.ExtraBold), c.ink, maxLines = 1)
                }
                PlainIconButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = WeekFields.of(locale).firstDayOfWeek
            val days = (0 until 7).map { first.plus(it.toLong()) }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(4f)), contentAlignment = Alignment.Center) {
                            Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale).uppercase(locale), size.label.copy(fontWeight = FontWeight.Bold), c.ink)
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
                                        val filled = lit != pressed
                                        Box(
                                            modifier = Modifier.fillMaxSize()
                                                .let { if (filled) it.background(c.ink) else it }
                                                .let { if (date == today && !filled) it.outline(einkStroke(FRAME), c.ink) else it },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val style = if (lit || date == today) size.body.copy(fontWeight = FontWeight.ExtraBold) else size.body
                                            Line(n.toString(), style, if (filled) c.ground else c.ink)
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

    /** Two counters, hours and minutes, in large figures, each between square + and − buttons. */
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(einkSize().dp(8f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", einkSize().number, einkColors.ink)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The ground; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        Box(modifier = Modifier.fillMaxSize().background(einkColors.ground)) {
            CompositionLocalProvider(LocalEinkInk provides null) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /**
     * A textured page's whole ground: the screen's ground and the motif in the ink over it, as on
     * the thing's tile, whatever its icon's colour; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val ground = einkColors.ground
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), ground, textureStyle())
        val half = halftoneOf(ground)
        val modifier = if (brush == null) Modifier.fillMaxSize()
            else Modifier.fillMaxSize().background(ground).background(brush).let { m -> half?.let { m.background(it) } ?: m }
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = einkSize()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(4f), vertical = size.dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    @Composable
    override fun ZoneCardContainer(texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(texture, onClick, onLongClick, content)

    /** A triangle of ink in the corner, ringed with what is under it: something waits. */
    @Composable
    override fun WaitingMark() {
        val side = einkSize().dp(14f)
        val ring = einkStroke(1.5f)
        val ink = LocalEinkInk.current ?: einkColors.ink
        val ground = if (ink == einkColors.ink) einkColors.ground else einkColors.ink
        Box(modifier = Modifier.size(side).drawBehind {
            val r = ring.toPx()
            fun corner(inset: Float) = Path().apply {
                moveTo(inset * 2.4f, inset); lineTo(size.width - inset, inset); lineTo(size.width - inset, size.height - inset * 2.4f); close()
            }
            drawPath(corner(0f), ground)
            drawPath(corner(r), ink)
        })
    }

    /** A cell of the grid in edit mode: a square in dashes of 1 dp. */
    @Composable
    override fun GridCell() {
        val stroke = einkStroke(1f)
        val ink = einkColors.ink
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val w = stroke.toPx()
            drawRect(ink, Offset(w / 2, w / 2), GeometrySize(size.width - w, size.height - w),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * w, 3 * w))))
        })
    }

    /**
     * The tile being moved is lifted: on its full shadow, standing off up and to the left; put
     * down, it falls back at once. The others step back behind a screen of the ground, the cells
     * showing through them: a fade the theme draws in two colours.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        val aside = state == app.treelune.core.ui.GridTileState.ASIDE
        val screen = EinkPatterns.screen(ASIDE_CLEARED, einkColors.ground, Color.Transparent, einkPatternDp())
        Box(modifier = Modifier.drawWithAsideScreen(aside, screen)) {
            Lifted(chosen) { CompositionLocalProvider(LocalEinkLifted provides chosen) { content() } }
        }
    }

    /** A square of ink with Lucide's timer in the ground's colour, lit every other second: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = einkSize()
        val ink = LocalEinkInk.current ?: einkColors.ink
        val ground = if (ink == einkColors.ink) einkColors.ground else einkColors.ink
        val transition = rememberInfiniteTransition(label = "running")
        // 0 or 1, by jumps: the square full, then framed
        val phase by transition.animateFloat(0f, 2f, infiniteRepeatable(tween(2 * TICK_MS, easing = LinearEasing), RepeatMode.Restart), label = "tick")
        val full = phase < 1f
        Box(
            modifier = Modifier.size(size.dp(18f)).background(if (full) ink else ground).outline(einkStroke(1.5f), ink),
            contentAlignment = Alignment.Center
        ) {
            NamedIcon("timer", null, size.dp(12f), if (full) ground else ink)
        }
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(texture, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it: a frame of 2 dp, the ground inside, its motif
     * in the ink across it; pressed, it inverts, ink and ground exchanged.
     */
    @Composable
    private fun Tile(texture: app.treelune.core.themes.ItemTexture?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = einkColors
        val frame = tileFrame(DisplayMode.CONDENSED)
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            val surface = if (pressed) c.ink else c.ground
            // The motif in the ink, whatever the icon's colour: a colour is a pattern, and the motif is one already
            val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), surface, textureStyle())
            val half = if (brush != null) halftoneOf(surface) else null
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surface)
                    .let { m -> brush?.let { m.background(it) } ?: m }
                    .let { m -> half?.let { m.background(it) } ?: m }
                    .outline(einkStroke(FRAME), c.ink)
                    .padding(frame)
            ) {
                CompositionLocalProvider(LocalEinkInk provides if (pressed) c.ground else null) { content() }
            }
        }
    }

    /**
     * The page's title in a band of ink across the width, in the ground's colour, the page's icon
     * before it; shrunk to hold in two lines, never under a dialog's title. The subtitle under it
     * in the band. The app's mark, the launcher's monochrome layer, before the home screen's title.
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = einkSize()
        val c = einkColors
        Row(
            modifier = Modifier.fillMaxWidth().background(c.ink).padding(horizontal = spacing(Spacing.EDGE), vertical = size.dp(10f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(12f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalEinkInk provides c.ground) {
                if (appMark) {
                    // The layer's central 72 units of 108 hold the drawing: drawn larger and cut, it fills the icon's room
                    Box(modifier = Modifier.size(size.dp(28f)).clipToBounds(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_monochrome),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(c.ground),
                            modifier = Modifier.requiredSize(size.dp(28f) * LAUNCHER_LAYER)
                        )
                    }
                } else icon?.let { name ->
                    if (iconColor == null) NamedIcon(name, null, size.icon, c.ground)
                    else app.treelune.core.ui.UI.ItemIcon(name, iconColor, size.icon)
                }
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = title,
                        style = size.pageTitle.copy(
                            color = c.ground,
                            hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                            lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    subtitle?.let { Line(it, size.label, c.ground) }
                }
            }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** Heavier than the text, in the ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, einkSize().label, LocalEinkInk.current ?: einkColors.ink)
    }

    /**
     * A box framed in 2 dp, 3 dp while the field has the focus or is wrong; the label above, and
     * inside in italics while it is empty.
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
        val size = einkSize()
        val c = einkColors
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
                Boxed(focused || error) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            readOnly = state == ComponentState.READONLY,
                            enabled = state != ComponentState.DISABLED,
                            textStyle = size.body.copy(color = c.ink),
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
                                    if (value.text.isEmpty()) Line(label, size.body.copy(fontStyle = FontStyle.Italic), c.ink, maxLines = 1)
                                    inner()
                                }
                            }
                        )
                        if (isPassword) {
                            Box(modifier = Modifier.combinedClickable(onClick = { revealed = !revealed })) {
                                NamedIcon(if (revealed) "eye-off" else "eye", null, size.icon, c.ink)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The choice shown as a field, its icon before it, a chevron at its end; the options in a
     * window, under their sections' titles, the chosen one inverted.
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
        val size = einkSize()
        val c = einkColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Boxed(pressed) {
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
                        // A section's title above its first option, in capitals, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(16f), bottom = size.dp(4f))) {
                                Line(section.uppercase(Locale.getDefault()), size.heading, c.ink)
                            }
                        }
                        val chosen = index == selected
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            val filled = chosen != pressed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (filled) it.background(c.ink) else it }
                                    .padding(horizontal = size.dp(12f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalEinkInk provides if (filled) c.ground else null) {
                                    OptionIconOf(index)
                                    Box(modifier = Modifier.weight(1f)) { Line(option, size.body, if (filled) c.ground else c.ink) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A square of 22 dp outlined in 2 dp: empty, or full of ink with a check of the ground. */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = einkSize()
        val c = einkColors
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                val full = checked != pressed
                Box(
                    modifier = Modifier
                        .size(size.dp(22f))
                        .let { if (full) it.background(c.ink) else it }
                        .outline(einkStroke(FRAME), c.ink),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) NamedIcon("check", null, size.dp(16f), if (full) c.ground else c.ink)
                }
                label?.let { Line(it, size.body, c.ink) }
            }
        }
    }

    /** The label across, a rectangle outlined at the end whose square knob jumps: full of ink when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = einkSize()
        val c = einkColors
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink) }
                val inset = size.dp(4f)
                Box(
                    modifier = Modifier
                        .size(size.dp(44f), size.dp(24f))
                        .let { if (checked) it.background(c.ink) else it }
                        .outline(einkStroke(FRAME), c.ink)
                        .padding(inset),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.size(size.dp(16f)).background(if (checked) c.ground else c.ink))
                }
            }
        }
    }

    /** Segments outlined side by side, sharing their rule: the tab shown inverted. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = einkSize()
        val c = einkColors
        val stroke = einkStroke(FRAME)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }, modifier = Modifier.offset(x = -stroke * index)) { pressed ->
                    val filled = (index == selected) != pressed
                    Box(
                        modifier = Modifier.heightIn(min = size.touch)
                            .let { if (filled) it.background(c.ink) else it }
                            .outline(stroke, c.ink)
                            .padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(label, size.button.copy(fontWeight = FontWeight.Bold), if (filled) c.ground else c.ink, maxLines = 1)
                    }
                }
            }
        }
    }

    /** Two segments outlined side by side, sharing their rule: the chosen one inverted. */
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
        val size = einkSize()
        val c = einkColors
        val stroke = einkStroke(FRAME)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
                listOf(true to trueLabel, false to falseLabel).forEachIndexed { index, (answer, text) ->
                    val chosen = value == answer
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        // The second segment over the first's right rule: one rule between them
                        modifier = (if (compact) Modifier else Modifier.weight(1f)).offset(x = -stroke * index)
                    ) { pressed ->
                        val filled = chosen != pressed
                        Box(
                            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                                .heightIn(min = size.touch)
                                .let { if (filled) it.background(c.ink) else it }
                                .outline(stroke, c.ink)
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text, size.button.copy(fontWeight = FontWeight.Bold), if (filled) c.ground else c.ink, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /** Never shown: the theme has no hue to turn (hueShifts), and the settings draw no slider for it. */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) = Unit

    /**
     * The value above in heavy figures, a rail of 2 dp across the width and a square of ink for
     * the knob; a touch or a drag anywhere on it answers the stop under the finger. No answer yet
     * shows the rail in dashes alone and "—".
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
        val size = einkSize()
        val c = einkColors
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
                Line(value?.let { String.format(Locale.getDefault(), "%.${decimals}f", it) } ?: "—", size.sliderValue, c.ink)
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
                val knob = size.dp(20f)
                val trackWidth = maxWidth
                val rail = einkStroke(FRAME)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(rail).drawBehind {
                        val h = this.size.height
                        val dash = if (fraction == null) PathEffect.dashPathEffect(floatArrayOf(3 * h, 2 * h)) else null
                        drawLine(c.ink, Offset(0f, h / 2), Offset(this.size.width, h / 2), strokeWidth = h, pathEffect = dash)
                    })
                    if (fraction != null) {
                        val travel = with(LocalDensity.current) { (trackWidth - knob).toPx() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                                .size(knob)
                                .background(c.ink)
                        )
                    }
                }
            }
            if (minLabel.isNotEmpty() || maxLabel.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Line(minLabel, size.caption, c.ink)
                    Line(maxLabel, size.caption, c.ink)
                }
            }
        }
    }

    // =====================================
    // PALETTES
    // =====================================

    /** For what Material still draws at the app's root: the two colours under every name. */
    override fun getColorScheme(mode: PaletteMode, hueShift: Int): ColorScheme {
        val c = EinkPalettes.colors(mode)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.ink, onPrimary = c.ground,
            primaryContainer = c.ink, onPrimaryContainer = c.ground,
            secondary = c.ink, onSecondary = c.ground,
            secondaryContainer = c.ink, onSecondaryContainer = c.ground,
            tertiary = c.ink, onTertiary = c.ground,
            tertiaryContainer = c.ink, onTertiaryContainer = c.ground,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.ground, onSurfaceVariant = c.ink,
            surfaceTint = c.ground, inverseSurface = c.ink, inverseOnSurface = c.ground, inversePrimary = c.ground,
            error = c.ink, onError = c.ground,
            errorContainer = c.ink, onErrorContainer = c.ground,
            outline = c.ink, outlineVariant = c.ink, scrim = Color.Black
        ) else lightColorScheme(
            primary = c.ink, onPrimary = c.ground,
            primaryContainer = c.ink, onPrimaryContainer = c.ground,
            secondary = c.ink, onSecondary = c.ground,
            secondaryContainer = c.ink, onSecondaryContainer = c.ground,
            tertiary = c.ink, onTertiary = c.ground,
            tertiaryContainer = c.ink, onTertiaryContainer = c.ground,
            background = c.ground, onBackground = c.ink,
            surface = c.ground, onSurface = c.ink,
            surfaceVariant = c.ground, onSurfaceVariant = c.ink,
            surfaceTint = c.ground, inverseSurface = c.ink, inverseOnSurface = c.ground, inversePrimary = c.ground,
            error = c.ink, onError = c.ground,
            errorContainer = c.ink, onErrorContainer = c.ground,
            outline = c.ink, outlineVariant = c.ink, scrim = Color.Black
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /** Three squares filling one after the other, by jumps: the AI's wait asks nothing of the user. */
    @Composable
    override fun AIThinkingIndicator() {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = einkSize().dp(12f)), contentAlignment = Alignment.CenterStart) {
            Squares(einkSize().dp(10f))
        }
    }

    /**
     * The user's message is a block of ink pushed to the right, its text in the ground's colour;
     * the AI's a frame of 2 dp on the left; the system's a frame in dashes. Both on the ground,
     * full: a long text never stands on a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = einkSize()
        val c = einkColors
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .background(c.ink)
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) {
                    CompositionLocalProvider(LocalEinkInk provides c.ground) { content() }
                }
            }
            MessageSender.AI -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Box(
                    modifier = Modifier.padding(end = size.dp(24f)).background(c.ground).outline(einkStroke(FRAME), c.ink)
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) { content() }
            }
            MessageSender.SYSTEM -> Box(
                modifier = Modifier.fillMaxWidth().background(c.ground).dashedOutline(einkStroke(1.5f), c.ink)
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) { content() }
        }
    }

    /** A double frame, 2 dp, 2 dp of air, 2 dp: the user is asked. The title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val size = einkSize()
        val c = einkColors
        val stroke = einkStroke(FRAME)
        Box(modifier = Modifier.fillMaxWidth().background(c.ground).outline(stroke, c.ink).padding(stroke * 2).outline(stroke, c.ink)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(12f), vertical = size.dp(12f)),
                verticalArrangement = Arrangement.spacedBy(size.dp(8f))
            ) {
                Text(title, TextType.TITLE, true, null)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(8f)), content = actions)
            }
        }
    }

    // =====================================
    // PIECES
    // =====================================

    /**
     * A text in one style and colour, cut with an ellipsis past [maxLines]; hyphenated, or broken
     * between [words] only, as a heading is.
     */
    @Composable
    private fun Line(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, words: Boolean = false) =
        Line(AnnotatedString(text), style, color, modifier, align, maxLines, words)

    /** The same, its runs keeping their emphasis. */
    @Composable
    private fun Line(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, words: Boolean = false) {
        BasicText(
            text = text,
            modifier = modifier,
            style = style.copy(
                color = color,
                textAlign = align ?: TextAlign.Unspecified,
                hyphens = if (words) androidx.compose.ui.text.style.Hyphens.None else androidx.compose.ui.text.style.Hyphens.Auto,
                lineBreak = if (words) androidx.compose.ui.text.style.LineBreak.Heading else androidx.compose.ui.text.style.LineBreak.Paragraph
            ),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines
        )
    }

    /** A button's word, in the ink its block imposes. */
    @Composable
    private fun Word(text: String) {
        Line(text, LocalEinkWord.current, LocalEinkInk.current ?: einkColors.ink, maxLines = 1)
    }

    /**
     * A button a finger's target tall: a block of ink for the main one, an outline of 2 dp for a
     * secondary one and a destructive one, its word heavier and its icon before it; its word
     * alone and underlined for the others; outlined in dashes when disabled, its word light.
     * Pressed, it inverts: the block becomes an outline, an outline a block.
     */
    @Composable
    private fun Block(type: ButtonType, enabled: Boolean, pressed: Boolean, icon: String? = null, content: @Composable () -> Unit) {
        val size = einkSize()
        val c = einkColors
        val stroke = einkStroke(FRAME)
        val ink = c.ink
        val ground = c.ground
        // What the button is drawn as: filled with the ink, outlined, or its word alone
        val filled = when (type) {
            ButtonType.PRIMARY -> !pressed
            else -> pressed
        } && enabled
        val outlined = !filled && enabled && (type == ButtonType.PRIMARY || type == ButtonType.SECONDARY || type == ButtonType.DANGER)
        val plain = enabled && !filled && !outlined
        val word = if (filled) ground else ink
        val weight = when {
            !enabled -> FontWeight.Normal
            type == ButtonType.DANGER -> FontWeight.ExtraBold
            else -> FontWeight.SemiBold
        }
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .let { if (filled) it.background(ink) else it }
                .let { if (outlined) it.outline(stroke, ink) else it }
                .let { if (!enabled) it.dashedOutline(stroke, ink) else it }
                .padding(horizontal = if (plain) size.dp(6f) else size.dp(16f)),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.dp(6f))) {
                if (icon != null) NamedIcon(icon, null, size.dp(20f), word)
                CompositionLocalProvider(
                    LocalEinkInk provides word,
                    LocalEinkWord provides size.button.copy(fontWeight = weight, textDecoration = if (plain) TextDecoration.Underline else null)
                ) { content() }
            }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; switched on ([active]) or the
     * main one, a block of ink. Pressed, it inverts. In a band of ink, it takes the band's colours.
     */
    @Composable
    private fun IconSquare(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = einkSize()
        val c = einkColors
        val ink = LocalEinkInk.current ?: c.ink
        val ground = if (ink == c.ink) c.ground else c.ink
        val block = (active || type == ButtonType.PRIMARY) && enabled
        val filled = block != pressed && enabled
        Box(
            modifier = Modifier.size(size.touch)
                .let { if (filled) it.background(ink) else it }
                .let { if (!enabled) it.dashedOutline(einkStroke(1f), ink) else it },
            contentAlignment = Alignment.Center
        ) { NamedIcon(icon, description, size.icon, if (filled) ground else ink) }
    }

    /** A button with a word, that acts. */
    @Composable
    private fun WordButton(text: String, onClick: () -> Unit, strong: Boolean = false, danger: Boolean = false, enabled: Boolean = true) {
        val type = when {
            danger -> ButtonType.DANGER
            strong -> ButtonType.PRIMARY
            else -> ButtonType.DEFAULT
        }
        Pressable(onClick = onClick, enabled = enabled) { pressed -> Block(type, enabled, pressed, if (danger) "trash" else null) { Word(text) } }
    }

    /** An icon alone a finger's target square, inverting under the finger: a month's arrow. */
    @Composable
    private fun PlainIconButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> IconSquare(icon, null, ButtonType.DEFAULT, true, pressed, false) }
    }

    /** A square − or + outlined in 2 dp, a finger's target. */
    @Composable
    private fun SquareButton(icon: String, onClick: () -> Unit) {
        val size = einkSize()
        val c = einkColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.touch, size.dp(40f)).let { if (pressed) it.background(c.ink) else it }.outline(einkStroke(FRAME), c.ink),
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, null, size.dp(20f), if (pressed) c.ground else c.ink) }
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

    /** A square [side] wide of [color]'s pattern, outlined when the pattern is the ground alone (grey). */
    @Composable
    private fun Swatch(color: TagColor, side: Dp, ink: Color, ground: Color) {
        val brush = EinkPatterns.of(color, ink, ground, einkPatternDp())
        Box(
            modifier = Modifier.size(side).background(brush)
                .let { if (color == TagColor.GREY) it.outline(einkStroke(1.5f), ink) else it }
        )
    }

    /** Two bars of 3 dp, [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = einkSize()
        val ink = LocalEinkInk.current ?: einkColors.ink
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(5f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(einkStroke(3f)).background(ink)) }
        }
    }

    /** What is written in a field, in a box framed in 2 dp, 3 dp when [strong]; a finger's target tall. */
    @Composable
    private fun Boxed(strong: Boolean, content: @Composable () -> Unit) {
        val size = einkSize()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .background(einkColors.ground)
                .outline(einkStroke(if (strong) 3f else FRAME), einkColors.ink)
                .padding(horizontal = size.dp(12f), vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /**
     * Three squares [side] wide, outlined, filling one after the other by jumps of 400 ms, then
     * emptying. Android's animations off, they stand still, full.
     */
    @Composable
    private fun Squares(side: Dp) {
        val size = einkSize()
        val ink = LocalEinkInk.current ?: einkColors.ink
        val transition = rememberInfiniteTransition(label = "squares")
        // 0 to 4: how many squares are full, the fourth step emptying them all
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = SQUARES_STEPS.toFloat(),
            animationSpec = infiniteRepeatable(tween(SQUARES_STEP_MS * SQUARES_STEPS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        val full = phase.toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(size.dp(6f))) {
            repeat(3) { i ->
                Box(
                    modifier = Modifier.size(side)
                        .let { if (i < full) it.background(ink) else it }
                        .outline(einkStroke(FRAME), ink)
                )
            }
        }
    }

    /**
     * A window over the screen, appearing at once: a rectangle of the ground framed in 2 dp on a
     * full shadow of 6 dp, its content, its actions under it.
     */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            val size = einkSize()
            val c = einkColors
            val shadow = size.dp(DIALOG_SHADOW)
            Column(
                modifier = Modifier
                    .padding(end = shadow, bottom = shadow)
                    .fillMaxWidth()
                    .hardShadow(shadow, c.ink)
                    .background(c.ground)
                    .outline(einkStroke(FRAME), c.ink)
                    .padding(start = size.dp(16f), end = size.dp(16f), top = size.dp(16f), bottom = size.dp(12f)),
                verticalArrangement = Arrangement.spacedBy(size.dp(16f))
            ) {
                CompositionLocalProvider(LocalEinkInk provides null) {
                    Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(8f)), content = content)
                    app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
                }
            }
        }
    }

    /** A block of ink ringed with the ground then the ink, so it stands off any page: a toast, a snackbar. */
    @Composable
    private fun Ringed(content: @Composable () -> Unit) {
        val c = einkColors
        Box(modifier = Modifier.fillMaxWidth().background(c.ground).outline(einkStroke(FRAME), c.ink).padding(einkStroke(FRAME + RING))) { content() }
    }

    /** A number in large figures between a square + above and − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = einkSize()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            SquareButton("plus") { onChange((value + 1) % modulo) }
            Line(String.format(Locale.ROOT, "%02d", value), size.number, einkColors.ink)
            SquareButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a block of ink across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = EinkToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            EinkToasts.dismiss(shown.id)
        }
        val size = einkSize()
        val c = einkColors
        Box(modifier = modifier.fillMaxWidth().padding(spacing(Spacing.EDGE))) {
            Ringed {
                Box(modifier = Modifier.fillMaxWidth().background(c.ink).padding(horizontal = size.dp(16f), vertical = size.dp(14f))) {
                    Line(shown.message, size.body, c.ground)
                }
            }
        }
    }

    /**
     * The checker of [surface] laid over a subtle motif (Réglages › Interface, Textures): half its
     * ink cleared, the motif staying two colours. Nothing for a marked one, drawn whole.
     */
    @Composable
    private fun halftoneOf(surface: Color): Brush? =
        if (CurrentTheme.textureIntensity == TextureIntensity.SUBTLE) EinkPatterns.halftone(surface, einkPatternDp()) else null

    /** A tile stepping back while another is moved: a screen of the ground over it. */
    private fun Modifier.drawWithAsideScreen(aside: Boolean, screen: Brush): Modifier =
        if (!aside) this else this.then(Modifier.drawWithContent { drawContent(); drawRect(screen) })

    /**
     * How the motifs are laid (docs/design/tool-texture.md): a cell's unit a dp at the size step,
     * so a motif grows with everything else, its cells on the pitch of 8 dp; an ICON motif's icon
     * sixteen units, sixteen around it. Not smoothed: a motif stays two colours.
     */
    @Composable
    private fun textureStyle(): app.treelune.core.themes.TextureStyle {
        val unit = with(LocalDensity.current) { einkSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = true, iconUnits = 16, iconGapUnits = 16)
    }

    /** A frame's width; a section's rule; the ring round a floating piece; a dialog's shadow, in dp. */
    private const val FRAME = 2f
    private const val SECTION_RULE = 3f
    private const val RING = 3f
    private const val DIALOG_SHADOW = 6f

    /** A launcher layer's side over the part of it a launcher shows. */
    private const val LAUNCHER_LAYER = 108f / 72f

    private const val MAX_GRID_DP = 480
    private const val GRID_GAP_DP = 8f
    /** The share of a tile stepping back that the ground's screen covers. */
    private const val ASIDE_CLEARED = 0.5f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val SQUARES_STEPS = 4
    private const val SQUARES_STEP_MS = 400
    private const val TICK_MS = 1000

    private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
}

/** Whether a text stands in a section's header: a HEADING there is a group's title, in capitals. */
internal val LocalEinkSection = compositionLocalOf { false }

/** The style a button's word takes: its block sets its weight, and underlines it when it is a word alone. */
internal val LocalEinkWord = compositionLocalOf { TextStyle.Default }

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object EinkToasts {
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
