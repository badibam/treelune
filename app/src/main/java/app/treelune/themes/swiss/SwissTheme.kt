package app.treelune.themes.swiss

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The Swiss theme (docs/design/swiss-theme.md): the app as a Swiss poster. A strict grid on a
 * pitch of 8 dp, black ink on an off-white ground and a single red for the app's signals, no
 * shadow and no rounding: a tile is a thick rule over its content, flush left; the numbers large,
 * in Archivo's black weight (SwissSize). Under the finger a piece inverts, and a window fades in
 * (SwissPieces); two palettes of a handful of OKLCH numbers (SwissPalette). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object SwissTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "swiss").theme("name")

    /** Lucide's icons as they are: their even stroke suits a grotesque. */
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

    /** Sixteen between two rows: a tile has no frame, its rule alone parting it from the one above. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { 16f.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, The sounds). */
    override fun sound(signal: UISignal): Int? = null

    /** A tile's name flush with its icon's side, on the grid's left line, as every text of the theme. */
    override fun tileNameAlign(): TextAlign = TextAlign.Start

    /**
     * How far the content stands from the tile's edge: under the rule and 8 dp of air, a tile
     * having no margin at its sides (its content flush with its rule's left end); 8 dp all round
     * an icon's tile, a block of ink.
     */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp =
        if (displayMode == DisplayMode.ICON) swissSize().dp(8f) else swissSize().dp(TILE_RULE + 8f)

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

    /** A square of red, its icon in what is written on the red. Pressed, it inverts. */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = swissSize()
        val c = swissColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.floating)
                    .background(if (pressed) c.onAccent.srgb else c.accent.srgb)
                    .let { if (pressed) it.outline(swissStroke(2f), c.accent.srgb) else it },
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(26f), if (pressed) c.accent.srgb else c.onAccent.srgb)
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

    /**
     * Each type in its size and weight; a group's or a tile's name (HEADING) in capitals, spaced,
     * its lines broken between words only: a name never cut by a hyphen.
     */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = swissSize()
        val c = swissColors
        val override = LocalSwissInk.current
        val (style, ink) = when (type) {
            TextType.TITLE -> size.title to (override ?: c.ink.srgb)
            TextType.SUBTITLE -> size.subtitle to (override ?: c.ink.srgb)
            TextType.HEADING -> size.heading to (override ?: c.ink.srgb)
            TextType.BODY -> size.body to (override ?: c.ink.srgb)
            TextType.STRONG -> size.strong to (override ?: c.ink.srgb)
            TextType.CAPTION -> size.caption to (override ?: c.dim.srgb)
            TextType.LABEL -> size.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) to (override ?: c.dim.srgb)
            TextType.ERROR -> size.body to c.error.srgb
            TextType.WARNING -> size.body to c.warning.srgb
        }
        val shown = if (type == TextType.HEADING) text.toUpperCase(LocaleList(Locale.getDefault().toLanguageTag())) else text
        Line(shown, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines, words = type == TextType.HEADING)
    }

    /** The weight of STRONG; Archivo's own italic, at both its weights. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Emphasis.ITALIC -> SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
    }

    /**
     * A card is content under a rule of 1 dp, flush left, on the ground, full so that a page's
     * motif never runs under its text; a highlighted one under the tile's 3 dp rule. A section's
     * header is its content alone, on the motif: the 3 dp rules of the tiles under it are the
     * section's line, a rule of its own would double them. The screens pad a header's content by
     * UI.Space.M all round; the space under it is given back, so that a title stands closer to
     * its tiles than to those above it.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = swissSize()
        val ink = swissColors.ink.srgb
        when (type) {
            CardType.SECTION_HEADER -> {
                val under = spacing(app.treelune.core.ui.Spacing.M)
                Box(modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height - under.roundToPx()).coerceAtLeast(0)) { placeable.place(0, 0) }
                }) { content() }
            }
            CardType.DEFAULT -> {
                val rule = if (highlight) TILE_RULE else 1f
                Box(
                    modifier = Modifier.fillMaxWidth().background(swissColors.ground.srgb).topRule(swissStroke(rule), ink)
                        .padding(top = s.dp(rule + 8f), bottom = s.dp(12f))
                ) { content() }
            }
        }
    }

    /** A square of the state's colour. */
    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color))
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val c = swissColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }.srgb
    }

    /** A small square of its colour before its text in the ink: the user's colours are marks, never a fill. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = swissSize()
        val c = swissColors
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(size.dp(6f))) {
            Box(modifier = Modifier.size(size.dp(8f)).background(c.tag(color).srgb))
            Line(text, size.name.copy(letterSpacing = 0.sp), LocalSwissInk.current ?: c.ink.srgb, maxLines = 1)
        }
    }

    /** A bar of 6 dp: the track in grey, filled with the ink; never red, even full. */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = swissSize()
        val c = swissColors
        Box(modifier = Modifier.fillMaxWidth().height(size.dp(6f)).background(c.track.srgb)) {
            Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(size.dp(6f)).background(c.ink.srgb))
        }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = swissSize().caption.copy(color = swissColors.dim.srgb)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        SwissDrawing.Draw(drawing, drawingTextStyle(), swissColors, modifier)
    }

    /** A rule of 1 dp across the width, in the ink. */
    @Composable
    override fun Divider() {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = swissSize().dp(8f)).height(swissStroke(1f)).background(swissColors.ink.srgb))
    }

    /** Two bars of 2 dp, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = swissSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = swissSize().dp(4f))) { Bars(swissSize().dp(24f)) }
    }

    /** Lifted, an item takes an outline and stands off; put down, it falls back at once. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, content)

    /**
     * A block of ink, its words in capitals in the ground's colour, a rule of the ground between
     * two; a word touched inverts. Words a finger's target tall, going to a second line when they
     * do not hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = swissSize()
        val c = swissColors
        val rule = swissStroke(1f)
        FlowRow(modifier = Modifier.background(c.ink.srgb)) {
            actions.forEachIndexed { index, action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = size.touch)
                            .let { if (pressed) it.background(c.ground.srgb).outline(rule, c.ink.srgb) else it }
                            .drawBehind { if (index > 0) drawRect(c.ground.srgb, Offset.Zero, GeometrySize(rule.toPx(), this.size.height)) }
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.label.uppercase(Locale.getDefault()), size.button, if (pressed) c.ink.srgb else c.ground.srgb, maxLines = 1)
                    }
                }
            }
        }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = SwissPalettes.colors(mode, 0).tag(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================


    /**
     * A block of ink, its message in the ground's colour, its action in underlined capitals; a
     * square of the state's colour before the message, but for a mere information.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = swissSize()
        val c = swissColors
        val mark = when (type) {
            FeedbackType.SUCCESS -> c.success
            FeedbackType.ERROR -> c.error
            FeedbackType.WARNING -> c.warning
            FeedbackType.INFO -> null
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch).background(c.ink.srgb).padding(start = size.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            mark?.let { Box(modifier = Modifier.size(size.dp(8f)).background(it.srgb)) }
            Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, c.ground.srgb) }
            if (action != null && onAction != null) {
                Pressable(onClick = onAction) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).let { if (pressed) it.background(c.ground.srgb) else it }.padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.uppercase(Locale.getDefault()), size.button.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), if (pressed) c.ink.srgb else c.ground.srgb, maxLines = 1)
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

    /** A bar of ink running from left to right along a grey track of 2 dp, as long as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val s = swissSize()
        val length = s.dp(
            when (size) {
                Size.XS -> 24f
                Size.S -> 48f
                Size.M -> 96f
                Size.L -> 160f
                Size.XL, Size.XXL -> 240f
            }
        )
        RunningBar(Modifier.width(length))
    }

    /** Lucide's drawing; a background, when asked, is a square of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        Image(
            painter = painterResource(resourceId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint ?: LocalSwissInk.current ?: swissColors.ink.srgb),
            modifier = Modifier.size(size).let { m -> background?.let { m.background(it) } ?: m }
        )
    }

    /**
     * A zone's or a tool's icon in the ink (in the ground's colour on an icon's tile, a block of
     * ink), its colour a square of 6 dp at its lower right corner. The square stands within the
     * icon's room: a neutral icon takes the same, so names beside icons line up.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val s = swissSize()
        val c = swissColors
        val ink = LocalSwissInk.current ?: c.ink.srgb
        Box(modifier = Modifier.size(size)) {
            Image(painter = painterResource(resourceId), contentDescription = null, colorFilter = ColorFilter.tint(ink), modifier = Modifier.size(size))
            if (color != null) {
                val mark = s.dp(6f)
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .size(mark + swissStroke(1f) * 2)
                        // A rule of what is under it round the square, so it stands off the icon's strokes
                        .background(if (LocalSwissInk.current != null) c.ink.srgb else c.ground.srgb)
                        .padding(swissStroke(1f))
                        .background(c.tag(color).srgb)
                )
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
     * A month on seven columns, its days in figures without a round; the day chosen a square of
     * ink, today underlined with the tile's rule. The month and its year in heavy capitals between
     * two chevrons.
     */
    @Composable
    override fun DatePicker(selectedDate: String, today: LocalDate, onDateSelected: (String) -> Unit, onDismiss: () -> Unit) {
        // "" means nothing chosen yet, and the picker opens on the caller's today by contract
        val initial = DateUtils.parseDisplayDate(selectedDate) ?: today
        var chosen by remember { mutableStateOf(initial) }
        var month by remember { mutableStateOf(YearMonth.from(initial)) }
        val locale = Locale.getDefault()
        val size = swissSize()
        val c = swissColors
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
                    Line(name.uppercase(locale), size.title.copy(fontSize = size.title.fontSize * 0.82f, letterSpacing = 0.04f.em()), c.ink.srgb, maxLines = 1)
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
                                            modifier = Modifier.fillMaxSize().let { if (filled) it.background(c.ink.srgb) else it },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val style = if (lit || date == today) size.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black) else size.body
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Line(n.toString(), style, if (filled) c.ground.srgb else c.ink.srgb)
                                                // Today underlined with the tile's rule, under its figures
                                                if (date == today && !lit) Box(modifier = Modifier.width(size.dp(16f)).height(swissStroke(TILE_RULE)).background(c.ink.srgb))
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
    }

    /** Two counters, hours and minutes, in figures the page title's size, each between square − and + buttons. */
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(swissSize().dp(8f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", swissSize().number, swissColors.ink.srgb)
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
        Box(modifier = Modifier.fillMaxSize().background(swissColors.ground.srgb)) {
            CompositionLocalProvider(LocalSwissInk provides null) {
                content()
            }
        }
    }

    /**
     * A textured page's whole ground: the screen's ground and the motif in the ink over it, as on
     * the thing's tile, whatever its icon's colour; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val ground = swissColors.ground.srgb
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), ground, textureStyle())
        val modifier = if (brush == null) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(ground).background(brush)
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = swissSize()
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

    /** A square of red with an exclamation mark in heavy figures. */
    @Composable
    override fun WaitingMark() {
        val size = swissSize()
        val c = swissColors
        Box(modifier = Modifier.size(size.dp(18f)).background(c.accent.srgb), contentAlignment = Alignment.Center) {
            Line("!", size.button.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black, letterSpacing = 0.sp), c.onAccent.srgb)
        }
    }

    /** A cell of the grid in edit mode: a square outline of 1 dp, the ink at a fifth. */
    @Composable
    override fun GridCell() {
        val stroke = swissStroke(1f)
        val color = swissColors.ink.srgb.copy(alpha = CELL_ALPHA)
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val w = stroke.toPx()
            drawRect(color, Offset(w / 2, w / 2), GeometrySize(size.width - w, size.height - w), style = Stroke(w))
        })
    }

    /**
     * The tile being moved is lifted: outlined, standing off up and to the left; put down, it falls
     * back at once. The others step back, faded, the cells showing through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen) { CompositionLocalProvider(LocalSwissLifted provides chosen) { content() } }
        }
    }

    /** A square of ink with Lucide's timer in the ground's colour: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = swissSize()
        val c = swissColors
        Box(modifier = Modifier.size(size.dp(18f)).background(c.ink.srgb), contentAlignment = Alignment.Center) {
            NamedIcon("timer", null, size.dp(12f), c.ground.srgb)
        }
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, texture, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it: a rule of 3 dp across its top, its content under
     * it flush with the rule's left end, the ground showing through; its motif under the rule. An
     * icon's tile is a block of ink, its icon in the ground's colour. Pressed, a tile inverts.
     */
    @Composable
    private fun Tile(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = swissColors
        val block = displayMode == DisplayMode.ICON
        val frame = tileFrame(displayMode)
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            // An icon's tile is ink at rest and the ground pressed; the others the reverse
            val filled = block != pressed
            val surface = if (filled) c.ink.srgb else c.ground.srgb
            // The motif in the ink, whatever the icon's colour: the user's colours are marks, never a fill
            val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), surface, textureStyle())
            val rule = swissStroke(TILE_RULE)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surface)
                    // The motif under the rule, never over it
                    .drawBehind {
                        brush?.let {
                            val top = if (block) 0f else rule.toPx()
                            drawRect(it, Offset(0f, top), GeometrySize(size.width, size.height - top))
                        }
                    }
                    .let {
                        if (block) { if (pressed) it.outline(swissStroke(2f), c.ink.srgb) else it }
                        else it.topRule(rule, if (pressed) c.ground.srgb else c.ink.srgb)
                    }
                    .let { if (block) it.padding(frame) else it.padding(top = frame) }
            ) {
                CompositionLocalProvider(LocalSwissInk provides if (filled) c.ground.srgb else null) { content() }
            }
        }
    }

    /**
     * The page's icon above its title, the title in the theme's largest letters, flush left on
     * the tiles' line; shrunk to hold in two lines, never under a dialog's title. The subtitle
     * under it, in small capitals. A textured page's motif is its ground's (PageGround).
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = swissSize()
        val c = swissColors
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(Spacing.EDGE)),
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            // The app's mark: the launcher's monochrome layer, in the ink
            if (appMark) {
                // The layer's central 72 units of 108 hold the drawing: drawn larger and cut, it fills the icon's room
                Box(modifier = Modifier.size(size.icon).clipToBounds(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_monochrome),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(c.ink.srgb),
                        modifier = Modifier.requiredSize(size.icon * LAUNCHER_LAYER)
                    )
                }
            } else icon?.let { name ->
                if (iconColor == null) NamedIcon(name, null, size.icon, c.ink.srgb)
                else app.treelune.core.ui.UI.ItemIcon(name, iconColor, size.icon)
            }
            BasicText(
                text = title,
                style = size.pageTitle.copy(
                    color = c.ink.srgb,
                    hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                    lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp),
                modifier = Modifier.fillMaxWidth()
            )
            subtitle?.let { Line(it.uppercase(Locale.getDefault()), size.label, c.dim.srgb) }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In small spaced capitals, the secondary ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        val shown = label.uppercase(Locale.getDefault())
        Line(if (required) "$shown *" else shown, swissSize().label, swissColors.dim.srgb)
    }

    /**
     * No box: a rule of 1 dp under the text, 2 dp while the field has the focus, the error's colour
     * when it is wrong; the label above, and again inside while it is empty.
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
        val size = swissSize()
        val c = swissColors
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
                Underlined(focused || error, if (error) c.error.srgb else c.ink.srgb) {
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
     * panel of the dialog's frame, under their sections' titles, the chosen one a row of ink.
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
        val size = swissSize()
        val c = swissColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Underlined(pressed, c.ink.srgb) {
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
                                Line(section.uppercase(Locale.getDefault()), size.heading, c.dim.srgb)
                            }
                        }
                        val chosen = index == selected
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            val filled = chosen != pressed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (filled) it.background(c.ink.srgb) else it }
                                    .padding(horizontal = size.dp(12f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalSwissInk provides if (filled) c.ground.srgb else null) {
                                    OptionIconOf(index)
                                    Box(modifier = Modifier.weight(1f)) { Line(option, size.body, if (filled) c.ground.srgb else c.ink.srgb) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A square of 20 dp outlined in 2 dp: empty, or full of ink when checked, without a check mark. */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = swissSize()
        val c = swissColors
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                Box(
                    modifier = Modifier
                        .size(size.dp(20f))
                        .let { if (checked != pressed) it.background(c.ink.srgb) else it }
                        .outline(swissStroke(2f), c.ink.srgb)
                )
                label?.let { Line(it, size.body, c.ink.srgb) }
            }
        }
    }

    /** The label across, a rectangle outlined at the end whose square knob jumps: full of ink when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = swissSize()
        val c = swissColors
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink.srgb) }
                val inset = size.dp(4f)
                Box(
                    modifier = Modifier
                        .size(size.dp(44f), size.dp(24f))
                        .let { if (checked) it.background(c.ink.srgb) else it }
                        .outline(swissStroke(2f), c.ink.srgb)
                        .padding(inset),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.size(size.dp(16f)).background(if (checked) c.ground.srgb else c.ink.srgb))
                }
            }
        }
    }

    /** Labels in capitals: the tab shown in the ink over the tile's rule, the others in the secondary ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = swissSize()
        val c = swissColors
        Row(
            modifier = Modifier.fillMaxWidth().bottomRule(swissStroke(1f), c.track.srgb).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(size.dp(20f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    val shown = index == selected
                    Box(
                        modifier = Modifier.heightIn(min = size.touch)
                            .let { if (shown || pressed) it.bottomRule(swissStroke(TILE_RULE), c.ink.srgb) else it },
                        contentAlignment = Alignment.Center
                    ) {
                        Line(label.uppercase(Locale.getDefault()), size.button, if (shown || pressed) c.ink.srgb else c.dim.srgb, maxLines = 1)
                    }
                }
            }
        }
    }

    /** Two segments outlined side by side, sharing their rule: the chosen one a block of ink. */
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
        val size = swissSize()
        val c = swissColors
        val stroke = swissStroke(2f)
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
                                .let { if (filled) it.background(c.ink.srgb) else it }
                                .outline(stroke, c.ink.srgb)
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text.uppercase(Locale.getDefault()), size.button, if (filled) c.ground.srgb else c.ink.srgb, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * The red at each shift, saturated, along a band outlined in the ink; the shift chosen a slot
     * outlined in the ink over a rule of the ground. A touch or a drag anywhere on it answers.
     */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) {
        val size = swissSize()
        val c = swissColors
        val mode = CurrentTheme.paletteMode
        val colors = remember(mode, shifts) {
            (shifts step HUE_STRIP_STEP).map { SwissPalettes.colors(mode, it).accent.srgb } + SwissPalettes.colors(mode, shifts.last).accent.srgb
        }
        val slot = size.dp(10f)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .horizontalPick { onValueChange(app.treelune.core.ui.RangePick.valueAt(it, shifts)) },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(size.dp(24f)).background(Brush.horizontalGradient(colors)).outline(swissStroke(1f), c.ink.srgb))
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - slot) * app.treelune.core.ui.RangePick.fractionOf(value, shifts))
                    .size(slot, size.dp(32f))
                    .outline(swissStroke(2f), c.ink.srgb)
                    .padding(swissStroke(2f))
                    .outline(swissStroke(2f), c.ground.srgb)
            )
        }
    }

    /**
     * The value above in heavy figures, a rail of 2 dp across the width and a bar of 4 × 24 dp for
     * the knob; a touch or a drag anywhere on it answers the stop under the finger. No answer yet
     * shows the rail alone and "—".
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
        val size = swissSize()
        val c = swissColors
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
                val knob = size.dp(4f)
                val trackWidth = maxWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(swissStroke(2f)).background(if (fraction != null) c.ink.srgb else c.track.srgb))
                    if (fraction != null) {
                        val travel = with(LocalDensity.current) { (trackWidth - knob).toPx() }
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((travel * fraction).roundToInt(), 0) }
                                .size(knob, size.dp(24f))
                                .background(c.ink.srgb)
                        )
                    }
                }
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
        val c = SwissPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.ink.srgb, onPrimary = c.ground.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onAccent.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.ground.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.ground.srgb,
            outline = c.ink.srgb, outlineVariant = c.track.srgb
        ) else lightColorScheme(
            primary = c.ink.srgb, onPrimary = c.ground.srgb,
            secondary = c.ink.srgb, onSecondary = c.ground.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onAccent.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.ground.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.ground.srgb,
            outline = c.ink.srgb, outlineVariant = c.track.srgb
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /** Three squares of ink filling one after the other, then emptying: the AI's wait asks nothing of the user. */
    @Composable
    override fun AIThinkingIndicator() {
        val size = swissSize()
        val c = swissColors
        val transition = rememberInfiniteTransition(label = "thinking")
        // 0 to 4: how many squares are full, the fourth step emptying them all
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = THINKING_STEPS.toFloat(),
            animationSpec = infiniteRepeatable(tween(THINKING_STEP_MS * THINKING_STEPS, easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        val full = phase.toInt()
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(12f)), contentAlignment = Alignment.CenterStart) {
            Row(horizontalArrangement = Arrangement.spacedBy(size.dp(6f))) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier.size(size.dp(8f))
                            .let { if (i < full) it.background(c.ink.srgb) else it }
                            .outline(swissStroke(2f), c.ink.srgb)
                    )
                }
            }
        }
    }

    /**
     * The user's message is a block of ink pushed to the right, its text in the ground's colour;
     * the AI's has no bubble, its text flush left under a rule of 1 dp; the system's under the
     * same rule, in the secondary ink. Both on the ground, full: a long text never stands on a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = swissSize()
        val c = swissColors
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .background(c.ink.srgb)
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) {
                    CompositionLocalProvider(LocalSwissInk provides c.ground.srgb) { content() }
                }
            }
            MessageSender.AI -> Box(modifier = Modifier.fillMaxWidth().background(c.ground.srgb).topRule(swissStroke(1f), c.ink.srgb).padding(top = size.dp(9f))) { content() }
            MessageSender.SYSTEM -> Box(modifier = Modifier.fillMaxWidth().background(c.ground.srgb).topRule(swissStroke(1f), c.dim.srgb).padding(top = size.dp(9f))) {
                CompositionLocalProvider(LocalSwissInk provides c.dim.srgb) { content() }
            }
        }
    }

    /** A red rule of 3 dp over it, a signal that the user is asked: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: (@Composable () -> Unit)?) {
        val size = swissSize()
        Column(
            modifier = Modifier.fillMaxWidth().topRule(swissStroke(TILE_RULE), swissColors.accent.srgb).padding(top = size.dp(TILE_RULE + 8f)),
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Text(title, TextType.TITLE, true, null)
            content()
            if (actions != null) app.treelune.core.ui.components.ButtonRow(align = Alignment.CenterHorizontally, mainLast = true, content = actions)
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

    /** A button's word, in capitals, in the ink its block imposes. */
    @Composable
    private fun Word(text: String) {
        Line(text.uppercase(Locale.getDefault()), swissSize().button, LocalSwissInk.current ?: swissColors.ink.srgb, maxLines = 1)
    }

    /**
     * A button a finger's target tall: a block of ink for the main one, an outline of 2 dp for a
     * secondary one, red for a destructive one, its word alone and underlined for the others;
     * grey when disabled. Pressed, it inverts: the block becomes an outline, an outline a block.
     */
    @Composable
    private fun Block(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = swissSize()
        val c = swissColors
        val stroke = swissStroke(2f)
        val ink = c.ink.srgb
        val ground = c.ground.srgb
        // What the button is drawn as: a block (fill), an outline (line), or its word alone
        val (fill, line, word) = when {
            !enabled -> when (type) {
                ButtonType.PRIMARY -> Triple(c.track.srgb, null, c.dim.srgb)
                ButtonType.SECONDARY, ButtonType.DANGER -> Triple(null, c.track.srgb, c.dim.srgb)
                else -> Triple(null, null, c.dim.srgb)
            }
            type == ButtonType.PRIMARY -> if (pressed) Triple(ground, ink, ink) else Triple(ink, null, ground)
            type == ButtonType.SECONDARY -> if (pressed) Triple(ink, null, ground) else Triple(null, ink, ink)
            type == ButtonType.DANGER -> if (pressed) Triple(c.accent.srgb, null, c.onAccent.srgb) else Triple(null, c.accent.srgb, c.accent.srgb)
            else -> if (pressed) Triple(ink, null, ground) else Triple(null, null, ink)
        }
        val plain = fill == null && line == null
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .let { m -> fill?.let { m.background(it) } ?: m }
                .let { m -> line?.let { m.outline(stroke, it) } ?: m }
                .padding(horizontal = if (plain) size.dp(4f) else size.dp(16f)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = if (plain) Modifier.bottomRule(swissStroke(1f), word) else Modifier) {
                CompositionLocalProvider(LocalSwissInk provides word) { content() }
            }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; switched on ([active]) or the
     * main one, a block of ink. Pressed, it inverts.
     */
    @Composable
    private fun IconSquare(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = swissSize()
        val c = swissColors
        val block = (active || type == ButtonType.PRIMARY) && enabled
        val filled = block != pressed && enabled
        val tint = when {
            !enabled -> c.track.srgb
            filled -> c.ground.srgb
            type == ButtonType.DANGER -> c.accent.srgb
            else -> c.ink.srgb
        }
        Box(
            modifier = Modifier.size(size.touch).let { if (filled) it.background(if (type == ButtonType.DANGER && pressed) c.accent.srgb else c.ink.srgb) else it },
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

    /** An icon alone a finger's target square, inverting under the finger: a month's arrow. */
    @Composable
    private fun PlainIconButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> IconSquare(icon, null, ButtonType.DEFAULT, true, pressed, false) }
    }

    /** A square − or + outlined in 2 dp, a finger's target. */
    @Composable
    private fun SquareButton(icon: String, onClick: () -> Unit) {
        val size = swissSize()
        val c = swissColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.touch, size.dp(40f)).let { if (pressed) it.background(c.ink.srgb) else it }.outline(swissStroke(2f), c.ink.srgb),
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, null, size.dp(20f), if (pressed) c.ground.srgb else c.ink.srgb) }
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

    /** Two bars of 2 dp, [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = swissSize()
        val ink = swissColors.ink.srgb
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(5f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(swissStroke(2f)).background(ink)) }
        }
    }

    /** What is written in a field, over a rule of 1 dp, 2 dp when [strong]; a finger's target tall. */
    @Composable
    private fun Underlined(strong: Boolean, color: Color, content: @Composable () -> Unit) {
        val size = swissSize()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .bottomRule(swissStroke(if (strong) 2f else 1f), color)
                .padding(vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /** A bar of ink running along a grey track of 2 dp, again and again. */
    @Composable
    private fun RunningBar(modifier: Modifier) {
        val c = swissColors
        val transition = rememberInfiniteTransition(label = "loading")
        val at by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOADING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "run"
        )
        Box(modifier = modifier.height(swissStroke(2f)).background(c.track.srgb).drawBehind {
            // A third of the track, entering at the left and leaving at the right
            val run = size.width / 3
            val start = -run + at * (size.width + run)
            val left = start.coerceAtLeast(0f)
            val right = (start + run).coerceAtMost(size.width)
            if (right > left) drawRect(c.ink.srgb, Offset(left, 0f), GeometrySize(right - left, size.height))
        })
    }

    /**
     * A window over the screen, fading in: a rectangle of the ground outlined in 1 dp under a bar
     * of ink of 6 dp, its content, its actions under it. The only closed frame of the theme.
     */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            FadeIn {
                val size = swissSize()
                val c = swissColors
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.ground.srgb)
                        .outline(swissStroke(1f), c.ink.srgb)
                        .topRule(swissStroke(DIALOG_BAR), c.ink.srgb)
                        .padding(start = size.dp(16f), end = size.dp(16f), top = size.dp(DIALOG_BAR + 16f), bottom = size.dp(12f)),
                    verticalArrangement = Arrangement.spacedBy(size.dp(16f))
                ) {
                    CompositionLocalProvider(LocalSwissInk provides null) {
                        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(8f)), content = content)
                        app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
                    }
                }
            }
        }
    }

    /** A number in the page title's figures between a square + above and − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = swissSize()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            SquareButton("plus") { onChange((value + 1) % modulo) }
            Line(String.format(Locale.ROOT, "%02d", value), size.number, swissColors.ink.srgb)
            SquareButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }


    /**
     * How the motifs are laid (docs/design/tool-texture.md): a cell's unit a dp at the size step,
     * smoothed, so a motif grows with everything else, its cells on the pitch of 8 dp; an ICON
     * motif's icon sixteen units, sixteen around it.
     */
    @Composable
    private fun textureStyle(): app.treelune.core.themes.TextureStyle {
        val unit = with(LocalDensity.current) { swissSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = false, iconUnits = 16, iconGapUnits = 16)
    }

    /** The tile's rule, and the dialog's bar, in dp. */
    private const val TILE_RULE = 3f
    private const val DIALOG_BAR = 6f

    /** A launcher layer's side over the part of it a launcher shows. */
    private const val LAUNCHER_LAYER = 108f / 72f

    /** Degrees between two colours the hue band computes. */
    private const val HUE_STRIP_STEP = 10

    private const val MAX_GRID_DP = 480
    private const val CELL_ALPHA = 0.2f
    private const val ASIDE_ALPHA = 0.4f
    private const val LOADING_MS = 1100
    private const val THINKING_STEPS = 4
    private const val THINKING_STEP_MS = 300

    private fun Float.em() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Em)
}

