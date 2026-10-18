package app.treelune.themes.blueprint

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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.treelune.R
import app.treelune.core.ai.data.MessageSender
import app.treelune.core.icons.IconSource
import app.treelune.core.icons.Icons
import app.treelune.core.strings.Strings
import app.treelune.core.themes.CurrentTheme
import app.treelune.core.themes.Lch
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

/**
 * The blueprint theme (docs/design/blueprint-theme.md): the app as an architect's plan. Fine lines
 * drawn with a ruler on grid paper, each side of a frame running past its corners as on a sketch;
 * hatching for what is filled (a gauge's progress, a chart's bars, a piece pressed); a title block
 * for a window; a revision triangle for what waits. The light palette is a whiteprint, the dark a
 * blueprint (BlueprintPalette). Titles, names and labels in the hand lettering of a plan, every
 * other text in IBM Plex Mono (BlueprintSize). Lucide redrawn thin, its lines overshooting too
 * (BlueprintIcons). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object BlueprintTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "blueprint").theme("name")

    /** Lucide, redrawn by the theme with its own line (BlueprintIcons). */
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

    /** Twelve between two columns, left to the grid: room for the overshooting lines of two tiles. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Twelve between two rows, as between two columns. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, Les sons). */
    override fun sound(signal: UISignal): Int? = null

    /** How far the content stands from the tile's top: 6 dp under its line; 8 dp all round an icon's tile. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp =
        if (displayMode == DisplayMode.ICON) blueprintSize().dp(8f) else blueprintSize().dp(6f)

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
     * A detail's callout: a circle of the paper ringed twice in the ink, its icon in the ink.
     * Pressed, the ink tints it.
     */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        Pressable(onClick = onClick) { pressed ->
            val ink = c.ink.srgb
            val paper = c.paper.srgb
            val line = blueprintStroke(LINE)
            val gap = blueprintStroke(4f)
            Box(
                modifier = Modifier.size(size.floating).drawBehind {
                    val r = this.size.minDimension / 2
                    val w = line.toPx()
                    drawCircle(paper, r)
                    if (pressed) drawCircle(ink, r, alpha = PRESSED_FILL)
                    drawCircle(ink, r - w / 2, style = Stroke(w))
                    drawCircle(ink, r - w * 1.5f - gap.toPx(), style = Stroke(w))
                },
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(26f), ink)
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

    /** Titles, names and labels in the lettering, in capitals; the rest in Plex Mono. */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = blueprintSize()
        val c = blueprintColors
        val override = LocalBlueprintInk.current
        val section = type == TextType.HEADING && LocalBlueprintSection.current
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
        Line(text, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /** Plex Mono's medium for the bold; its italic. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = FontWeight.Medium)
        Emphasis.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    }

    /**
     * A card is the paper in a plain outline, full so that the grid and a page's motif never run
     * under its text; a highlighted one in a title block's double frame. A section's header is its
     * title, then an axis in dashes across the width.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = blueprintSize()
        val c = blueprintColors
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(s.dp(2f))) {
                CompositionLocalProvider(LocalBlueprintSection provides true) { content() }
                DashedRule(Modifier.padding(horizontal = spacing(Spacing.EDGE)), c.ink.srgb.copy(alpha = RULE_ALPHA))
            }
            CardType.DEFAULT -> Box(
                modifier = Modifier.fillMaxWidth()
                    .fill(c.paper.srgb)
                    .let { if (highlight) it.doubleFrame(blueprintStroke(LINE), s.dp(3f), c.ink.srgb) else it.plainFrame(blueprintStroke(LINE), c.ink.srgb) }
                    .padding(horizontal = s.dp(if (highlight) 14f else 12f), vertical = s.dp(if (highlight) 12f else 10f))
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
        val c = blueprintColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }.srgb
    }

    /** An outline of its pencil, its word in the same pencil. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = blueprintSize()
        val crayon = blueprintColors.crayon(color).srgb
        Box(modifier = Modifier.plainFrame(blueprintStroke(LINE), crayon).padding(horizontal = size.dp(7f), vertical = size.dp(1f))) {
            Line(text, size.tag, crayon, maxLines = 1)
        }
    }

    /**
     * A rail outlined in the ink, what is done hatched in the tile's pencil (the ink outside a
     * tile), closed by a line where it stops.
     */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = blueprintSize()
        val c = blueprintColors
        val crayon = LocalBlueprintCrayon.current ?: c.ink.srgb
        val ink = c.ink.srgb
        val line = blueprintStroke(LINE)
        val hatch = blueprintStroke(HATCH_LINE)
        val pitch = size.dp(HATCH_PITCH)
        val done = fraction.coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(size.dp(10f)).drawBehind {
            val w = line.toPx()
            if (done > 0f) {
                val end = this.size.width * done
                drawHatching(Offset.Zero, GeometrySize(end, this.size.height), crayon, hatch.toPx(), pitch.toPx())
                drawLine(crayon, Offset(end - w / 2, 0f), Offset(end - w / 2, this.size.height), w)
            }
            drawRect(ink, Offset(w / 2, w / 2), GeometrySize(this.size.width - w, this.size.height - w), style = Stroke(w))
        })
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = blueprintSize().caption.copy(color = blueprintColors.dim.srgb)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        BlueprintDrawing.Draw(drawing, drawingTextStyle(), blueprintColors, LocalBlueprintCrayon.current, modifier)
    }

    /** A fine dashed line across the width, in the secondary ink. */
    @Composable
    override fun Divider() {
        DashedRule(Modifier.padding(vertical = blueprintSize().dp(8f)), blueprintColors.dim.srgb)
    }

    /** Two lines, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = blueprintSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = blueprintSize().dp(4f))) { Bars(blueprintSize().dp(24f)) }
    }

    /** Lifted, an item moves up and to the left, its place left in dashes; put down, it falls back at once. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, content)

    /**
     * A strip of the paper in the sketch's frame, its words in the lettering, a fine line between
     * two; a word touched in a fill of the ink, its word in the paper. Words a finger's target
     * tall, going to a second line when they do not hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = blueprintSize()
        val c = blueprintColors
        val line = blueprintStroke(LINE)
        val hair = blueprintStroke(1f)
        val ink = c.ink.srgb
        val paper = c.paper.srgb
        FlowRow(modifier = Modifier.fill(paper).sketchFrame(line, ink, blueprintStroke(OVERSHOOT))) {
            actions.forEachIndexed { index, action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = size.touch)
                            .let { if (pressed) it.background(ink) else it }
                            .drawBehind { if (index > 0) drawRect(ink, Offset.Zero, GeometrySize(hair.toPx(), this.size.height)) }
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.label, size.button, if (pressed) paper else ink, maxLines = 1)
                    }
                }
            }
        }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = BlueprintPalettes.colors(mode, 0).crayon(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================

    /** A small title block at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { BlueprintToasts.show(message, shown) }
    }

    /**
     * A small title block full of the ink, a hairline of the paper inside its edge, its message in
     * the paper, its action in the lettering. No mark of the state: no colour of a state reads on
     * the ink in both palettes, and the message says it.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = blueprintSize()
        val c = blueprintColors
        val paper = c.paper.srgb
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch).inkBlock().padding(start = size.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, paper) }
            if (action != null && onAction != null) {
                Pressable(onClick = onAction) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).pressedHatch(pressed, paper, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH)).padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action, size.button, paper, maxLines = 1)
                    }
                }
            }
        }
    }

    @Composable
    override fun ConfirmDialog(title: String, message: String, confirmText: String?, cancelText: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(title = title, onDismiss = onDismiss, actions = {
            WordButton(cancelText ?: s.shared("action_cancel"), onDismiss)
            WordButton(confirmText ?: s.shared("action_confirm"), onConfirm, strong = true)
        }) {
            Text(message, TextType.BODY, false, null)
        }
    }

    // =====================================
    // SYSTEM
    // =====================================

    /** A compass drawing a circle round its centre's cross, then rubbing it out, as large as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val s = blueprintSize()
        val side = s.dp(
            when (size) {
                Size.XS -> 16f
                Size.S -> 24f
                Size.M -> 40f
                Size.L -> 56f
                Size.XL, Size.XXL -> 72f
            }
        )
        Compass(side)
    }

    /** The theme's drawing of a Lucide icon; a background, when asked, is a round of it behind the icon. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        BlueprintIcon(
            resourceId, size, tint ?: LocalBlueprintInk.current ?: blueprintColors.ink.srgb, contentDescription,
            Modifier.let { m -> background?.let { m.background(it, CircleShape) } ?: m }
        )
    }

    /** A zone's or a tool's icon drawn in its pencil, a neutral one in the ink: no badge, so every icon takes the same room. */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val c = blueprintColors
        val tint = LocalBlueprintInk.current ?: color?.let { c.crayon(it).srgb } ?: c.ink.srgb
        BlueprintIcon(resourceId, size, tint)
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
        DialogFrame(title = null, onDismiss = onCancel, actions = {
            if (cancelText != null) WordButton(cancelText, onCancel)
            if (confirmText != null) WordButton(confirmText, onConfirm, strong = true, danger = type == DialogType.DANGER, enabled = confirmEnabled)
        }) {
            Box(modifier = Modifier.scrollEndSound()) { content() }
        }
    }

    /**
     * A month on seven columns, its days in figures; the day chosen circled by a fine line, today
     * underlined. The month and its year in the lettering between two chevrons, in the title
     * block's head.
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
        val size = blueprintSize()
        val c = blueprintColors
        val s = Strings.`for`(context = LocalContext.current)

        DialogFrame(title = null, onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onDateSelected(DateUtils.formatDateForDisplay(chosen.atStartOfDay(zone).toInstant().toEpochMilli()))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PlainIconButton("chevron-left") { month = month.minusMonths(1) }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val name = month.month.getDisplayName(DateTextStyle.FULL_STANDALONE, locale) + " " + month.year
                    Line(name, size.title, c.ink.srgb, maxLines = 1)
                }
                PlainIconButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = WeekFields.of(locale).firstDayOfWeek
            val days = (0 until 7).map { first.plus(it.toLong()) }
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(4f)), contentAlignment = Alignment.Center) {
                            Line(day.getDisplayName(DateTextStyle.NARROW_STANDALONE, locale), size.label, c.dim.srgb)
                        }
                    }
                }
                val lead = ((month.atDay(1).dayOfWeek.value - first.value) + 7) % 7
                val cells = lead + month.lengthOfMonth()
                val line = blueprintStroke(LINE)
                (0 until (cells + 6) / 7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val n = week * 7 + column - lead + 1
                            Box(modifier = Modifier.weight(1f).height(size.dp(40f)), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    val isToday = date == today
                                    Pressable(onClick = { chosen = date }, modifier = Modifier.fillMaxSize()) { pressed ->
                                        val ink = c.ink.srgb
                                        Box(
                                            modifier = Modifier.fillMaxSize().drawBehind {
                                                val r = minOf(this.size.width, this.size.height) / 2 - 2f
                                                val w = line.toPx()
                                                if (pressed) drawCircle(ink, r, alpha = PRESSED_FILL)
                                                if (lit) drawCircle(ink, r - w / 2, style = Stroke(w))
                                            },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Line(n.toString(), if (lit || isToday) size.strong else size.body, c.ink.srgb)
                                                if (isToday) Box(modifier = Modifier.width(size.dp(16f)).height(line).background(ink))
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

    /** Hours and minutes in large figures, each in a sketch's frame between − and + buttons; no dial. */
    @Composable
    override fun TimePicker(selectedTime: String, onTimeSelected: (String) -> Unit, onDismiss: () -> Unit) {
        // "" means nothing chosen yet, and the picker opens on now by contract
        val (h, m) = DateUtils.parseTime(selectedTime)
            ?: ZonedDateTime.now(AppConfigManager.getDateTimeConfig().getZoneId()).let { it.hour to it.minute }
        var hour by remember { mutableIntStateOf(h) }
        var minute by remember { mutableIntStateOf(m) }
        val s = Strings.`for`(context = LocalContext.current)
        DialogFrame(title = null, onDismiss = onDismiss, actions = {
            WordButton(s.shared("action_cancel"), onDismiss)
            WordButton(s.shared("action_ok"), {
                onTimeSelected(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                onDismiss()
            }, strong = true)
        }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(blueprintSize().dp(12f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24) { hour = it }
                Line(":", blueprintSize().number, blueprintColors.ink.srgb)
                Counter(minute, 60) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The paper and its grid, large squares of 40 dp over small ones of 8; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        val c = blueprintColors
        val paper = c.paper.srgb
        val major = c.grid
        val minor = c.subgrid
        Box(modifier = Modifier.fillMaxSize().background(paper).drawBehind { drawGrid(minor, major) }) {
            CompositionLocalProvider(LocalBlueprintInk provides null, LocalBlueprintCrayon provides null) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /**
     * A textured page's whole ground: the paper, hiding the grid, and the motif in the ink over it,
     * as on the thing's tile, whatever its pencil; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val paper = blueprintColors.paper.srgb
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), paper, textureStyle())
        val modifier = if (brush == null) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(paper).background(brush)
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced. */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = blueprintSize()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(4f), vertical = size.dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }

    /** A zone's tile is a tool's. */
    @Composable
    override fun ZoneCardContainer(texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(DisplayMode.CONDENSED, texture, color, onClick, onLongClick, content)

    /** A revision triangle of the alert's colour, an exclamation mark in the paper. */
    @Composable
    override fun WaitingMark() {
        val size = blueprintSize()
        val c = blueprintColors
        val alert = c.alert.srgb
        Box(
            modifier = Modifier.size(size.dp(22f), size.dp(20f)).drawBehind {
                drawPath(Path().apply {
                    moveTo(this@drawBehind.size.width / 2, 0f)
                    lineTo(this@drawBehind.size.width, this@drawBehind.size.height)
                    lineTo(0f, this@drawBehind.size.height)
                    close()
                }, alert)
            }.padding(top = size.dp(6f)),
            contentAlignment = Alignment.Center
        ) {
            Line("!", size.caption.copy(fontWeight = FontWeight.Medium, fontSize = size.caption.fontSize * 0.95f), c.paper.srgb)
        }
    }

    /** A cell of the grid in edit mode: a frame dashed in the secondary ink. */
    @Composable
    override fun GridCell() {
        Box(modifier = Modifier.fillMaxSize().sketchFrame(blueprintStroke(1f), blueprintColors.dim.srgb, 0.dp, dashed = true))
    }

    /**
     * The tile being moved is lifted: it moves up and to the left, its frame thicker, its place left
     * in dashes; put down, it falls back at once. The others step back, faded, the cells showing
     * through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen) { CompositionLocalProvider(LocalBlueprintLifted provides chosen) { content() } }
        }
    }

    /** A circle of the paper ringed in the ink, its centre's cross: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = blueprintSize()
        val c = blueprintColors
        val paper = c.paper.srgb
        val ink = c.ink.srgb
        val line = blueprintStroke(LINE)
        Box(modifier = Modifier.size(size.dp(20f)).drawBehind { drawCentreMark(paper, ink, line.toPx()) })
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, texture, color, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it: the paper, hiding the grid, in a frame whose sides
     * run past its corners; its motif inside; what is drawn inside takes its pencil
     * (LocalBlueprintCrayon). Lifted, its frame thickens. Pressed, it fills with light hatching.
     */
    @Composable
    private fun Tile(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = blueprintColors
        val size = blueprintSize()
        val icon = displayMode == DisplayMode.ICON
        val frame = tileFrame(displayMode)
        val paper = c.paper.srgb
        val ink = c.ink.srgb
        val line = blueprintStroke(if (LocalBlueprintLifted.current) LIFTED_LINE else LINE)
        val overshoot = blueprintStroke(OVERSHOOT)
        Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
            // The motif in the ink, whatever the pencil
            val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), paper, textureStyle())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .fill(paper)
                    .let { m -> brush?.let { m.background(it) } ?: m }
                    .pressedHatch(pressed, ink, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH))
                    .sketchFrame(line, ink, overshoot)
                    .let { if (icon) it.padding(frame) else it.padding(start = size.dp(10f), end = size.dp(10f), top = frame, bottom = size.dp(4f)) }
            ) {
                CompositionLocalProvider(LocalBlueprintCrayon provides color?.let { c.crayon(it).srgb }) { content() }
            }
        }
    }

    /**
     * A page's title in the lettering, in a sketch's frame centred on the page: the page's icon in
     * its pencil in a cell before it, the scale's cell ("1:1") after it, as the title block of a
     * plan; shrunk to hold in two lines. The subtitle under it in the secondary ink. The app's mark
     * is the launcher's monochrome layer in the ink.
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = blueprintSize()
        val c = blueprintColors
        val ink = c.ink.srgb
        val line = blueprintStroke(LINE)
        val scale = Strings.`for`(context = LocalContext.current, theme = "blueprint").theme("scale")
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(Spacing.EDGE), vertical = size.dp(10f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(size.dp(6f))
        ) {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min).fill(c.paper.srgb).sketchFrame(line, ink, blueprintStroke(OVERSHOOT)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appMark || icon != null) {
                    Box(modifier = Modifier.padding(horizontal = size.dp(10f), vertical = size.dp(6f)), contentAlignment = Alignment.Center) {
                        if (appMark) {
                            // The layer's central 72 units of 108 hold the drawing: drawn larger and cut, it fills the icon's room
                            Box(modifier = Modifier.size(size.icon).clipToBounds(), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(R.drawable.ic_launcher_monochrome),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(ink),
                                    modifier = Modifier.requiredSize(size.icon * LAUNCHER_LAYER)
                                )
                            }
                        } else icon?.let { app.treelune.core.ui.UI.ItemIcon(it, iconColor, size.icon) }
                    }
                    CellLine(line, ink)
                }
                BasicText(
                    text = title.uppercase(Locale.getDefault()),
                    style = size.pageTitle.copy(
                        color = ink,
                        textAlign = TextAlign.Center,
                        hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                        lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp),
                    modifier = Modifier.weight(1f, fill = false).padding(horizontal = size.dp(16f), vertical = size.dp(6f))
                )
                CellLine(line, ink)
                Box(modifier = Modifier.padding(horizontal = size.dp(10f)), contentAlignment = Alignment.Center) {
                    Line(scale, size.scale, ink, maxLines = 1)
                }
            }
            subtitle?.let { Line(it, size.caption, c.dim.srgb, align = TextAlign.Center) }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In the lettering, the ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, blueprintSize().label, LocalBlueprintInk.current ?: blueprintColors.ink.srgb)
    }

    /**
     * A line under the text, thicker while the field has the focus, of the error's colour when it
     * is wrong; the label above in the lettering, and again inside in the secondary ink while it
     * is empty.
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
        val size = blueprintSize()
        val c = blueprintColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(2f))) {
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
                Underlined(
                    color = if (error) c.error.srgb else c.ink.srgb,
                    thick = error || focused
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
                                    if (value.text.isEmpty()) Line(AnnotatedString(label), size.body, c.dim.srgb, maxLines = 1, capitals = false)
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
     * title block, under their sections' titles, the chosen one a row of the ink.
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
        val size = blueprintSize()
        val c = blueprintColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(2f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Underlined(c.ink.srgb, thick = pressed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OptionIconOf(selected)
                        Box(modifier = Modifier.weight(1f)) { Line(shown, size.body, c.ink.srgb, maxLines = 1) }
                        NamedIcon("chevron-down", null, size.dp(20f), c.ink.srgb)
                    }
                }
            }
        }
        if (open) {
            DialogFrame(title = label.takeIf { it.isNotBlank() }, onDismiss = { open = false }, actions = {}) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { index, option ->
                        // A section's title above its first option, in the lettering, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(16f), bottom = size.dp(4f))) {
                                Line(section, size.label, c.dim.srgb)
                            }
                        }
                        val chosen = index == selected
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (chosen) it.fill(c.ink.srgb) else it }
                                    .pressedHatch(pressed, c.ink.srgb, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH))
                                    .padding(horizontal = size.dp(12f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalBlueprintInk provides if (chosen) c.paper.srgb else null) {
                                    OptionIconOf(index)
                                    Box(modifier = Modifier.weight(1f)) { Line(option, if (chosen) size.strong else size.body, if (chosen) c.paper.srgb else c.ink.srgb) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A square outlined in the ink, checked with the theme's check in the tile's pencil (the ink elsewhere). */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = blueprintSize()
        val c = blueprintColors
        val crayon = LocalBlueprintCrayon.current ?: c.ink.srgb
        val ink = c.ink.srgb
        val line = blueprintStroke(LINE)
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                Box(
                    modifier = Modifier.size(size.dp(20f))
                        .pressedHatch(pressed, ink, blueprintStroke(HATCH_LINE), size.dp(4f))
                        .plainFrame(line, ink),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) NamedIcon("check", null, size.dp(16f), crayon)
                }
                label?.let { Line(it, size.body, LocalBlueprintInk.current ?: c.ink.srgb) }
            }
        }
    }

    /** The label across, a pill outlined at the end whose round knob, marked with a centre's cross, slides: the pill full of the ink when on. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = blueprintSize()
        val c = blueprintColors
        val line = blueprintStroke(LINE)
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) { pressed ->
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink.srgb) }
                val ink = c.ink.srgb
                val paper = c.paper.srgb
                Box(modifier = Modifier.size(size.dp(50f), size.dp(28f)).drawBehind {
                    val h = this.size.height
                    val w = line.toPx()
                    if (checked) drawRoundRect(ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2))
                    else drawRoundRect(ink, Offset(w / 2, w / 2), GeometrySize(this.size.width - w, h - w), androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2), style = Stroke(w))
                    if (pressed) drawRoundRect(ink, cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2), alpha = PRESSED_FILL)
                    val r = h / 2 - 4 * density
                    val x = if (checked) this.size.width - h / 2 else h / 2
                    translateCentreMark(Offset(x, h / 2), r, paper, ink, w)
                })
            }
        }
    }

    /** Labels in the lettering: the tab shown in the ink over a double line, the others in the secondary ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        val hair = blueprintStroke(1f)
        val line = blueprintStroke(LINE)
        val dim = c.dim.srgb
        Row(
            modifier = Modifier.fillMaxWidth()
                .drawBehind { drawRect(dim, Offset(0f, this.size.height - hair.toPx()), GeometrySize(this.size.width, hair.toPx())) }
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(size.dp(22f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    val shown = index == selected
                    val ink = c.ink.srgb
                    val gap = size.dp(2.5f)
                    Box(
                        modifier = Modifier.heightIn(min = size.touch)
                            .drawBehind {
                                if (shown || pressed) {
                                    val w = line.toPx()
                                    val alpha = if (shown) 1f else 0.4f
                                    drawRect(ink, Offset(0f, this.size.height - w), GeometrySize(this.size.width, w), alpha = alpha)
                                    drawRect(ink, Offset(0f, this.size.height - 2 * w - gap.toPx()), GeometrySize(this.size.width, w), alpha = alpha)
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

    /** Two segments outlined side by side, sharing their outline: the chosen one a fill of the ink. */
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
        val size = blueprintSize()
        val c = blueprintColors
        val line = blueprintStroke(LINE)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
                listOf(true to trueLabel, false to falseLabel).forEachIndexed { index, (answer, text) ->
                    val chosen = value == answer
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        // The second segment over the first's right outline: one line between them
                        modifier = (if (compact) Modifier else Modifier.weight(1f)).offset(x = -line * index)
                    ) { pressed ->
                        val ink = c.ink.srgb
                        Box(
                            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                                .heightIn(min = size.touch)
                                .let { if (chosen) it.fill(ink) else it }
                                .pressedHatch(pressed, ink, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH))
                                .plainFrame(line, ink)
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text, size.button, if (chosen) c.paper.srgb else c.ink.srgb, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * A saturated blue at each shift, along a band outlined in the ink; the shift chosen a square
     * outlined in the ink over a ring of the paper. A touch or a drag anywhere on it answers.
     */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        val mode = CurrentTheme.paletteMode
        val colors = remember(mode, shifts) {
            (shifts step HUE_STRIP_STEP).map { hueSample(mode, it) } + hueSample(mode, shifts.last)
        }
        val knob = size.dp(30f)
        val line = blueprintStroke(LINE)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .horizontalPick { onValueChange(app.treelune.core.ui.RangePick.valueAt(it, shifts)) },
            contentAlignment = Alignment.CenterStart
        ) {
            val ink = c.ink.srgb
            val paper = c.paper.srgb
            Box(modifier = Modifier.fillMaxWidth().height(size.dp(24f)).drawBehind {
                drawRect(Brush.horizontalGradient(colors))
                val w = line.toPx()
                drawRect(ink, Offset(w / 2, w / 2), GeometrySize(this.size.width - w, this.size.height - w), style = Stroke(w))
            })
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - knob) * app.treelune.core.ui.RangePick.fractionOf(value, shifts))
                    .size(knob, size.dp(34f))
                    .drawBehind {
                        val w = line.toPx()
                        drawRect(paper, Offset(w, w), GeometrySize(this.size.width - 2 * w, this.size.height - 2 * w), style = Stroke(3 * density))
                        drawRect(ink, Offset(w / 2, w / 2), GeometrySize(this.size.width - w, this.size.height - w), style = Stroke(w))
                    }
            )
        }
    }

    /**
     * The value above in figures; under it a dimension line from the start to the value, its arrow
     * at the start and a tick at each end, over a fine rail across the width; the finger's knob a
     * circle marked with a centre's cross. A touch or a drag anywhere on it answers the stop under
     * the finger. No answer yet shows the rail alone and "—".
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
        val size = blueprintSize()
        val c = blueprintColors
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
                val dim = c.dim.srgb
                val paper = c.paper.srgb
                val line = blueprintStroke(LINE)
                val hair = blueprintStroke(1f)
                val knob = size.dp(20f)
                val arrow = size.dp(8f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } }
                        .drawBehind {
                            val mid = this.size.height / 2
                            val r = knob.toPx() / 2
                            drawLine(dim, Offset(0f, mid), Offset(this.size.width, mid), hair.toPx())
                            if (fraction != null) {
                                val at = r + (this.size.width - 2 * r) * fraction
                                val w = line.toPx()
                                val a = arrow.toPx()
                                drawLine(ink, Offset(w / 2, mid - r * 0.8f), Offset(w / 2, mid + r * 0.8f), hair.toPx())
                                drawLine(ink, Offset(0f, mid), Offset(at, mid), w)
                                drawPath(Path().apply { moveTo(0f, mid); lineTo(a, mid - a * 0.45f); lineTo(a, mid + a * 0.45f); close() }, ink)
                                translateCentreMark(Offset(at, mid), r, paper, ink, w)
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
        val c = BlueprintPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.ink.srgb, onPrimary = c.paper.srgb,
            secondary = c.ink.srgb, onSecondary = c.paper.srgb,
            tertiary = c.alert.srgb, onTertiary = c.paper.srgb,
            background = c.paper.srgb, onBackground = c.ink.srgb,
            surface = c.paper.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.paper.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.paper.srgb,
            outline = c.ink.srgb, outlineVariant = c.dim.srgb
        ) else lightColorScheme(
            primary = c.ink.srgb, onPrimary = c.paper.srgb,
            secondary = c.ink.srgb, onSecondary = c.paper.srgb,
            tertiary = c.alert.srgb, onTertiary = c.paper.srgb,
            background = c.paper.srgb, onBackground = c.ink.srgb,
            surface = c.paper.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.paper.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.paper.srgb,
            outline = c.ink.srgb, outlineVariant = c.dim.srgb
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /**
     * A dimension line drawing itself between its two ticks, its arrow at the start, then rubbed
     * out, again and again. The AI's wait asks nothing of the user.
     */
    @Composable
    override fun AIThinkingIndicator() {
        val size = blueprintSize()
        val c = blueprintColors
        val transition = rememberInfiniteTransition(label = "thinking")
        val drawn by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(THINKING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "drawn"
        )
        val ink = c.ink.srgb
        val dim = c.dim.srgb
        val line = blueprintStroke(LINE)
        val hair = blueprintStroke(1f)
        val arrow = size.dp(8f)
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = size.dp(12f)), contentAlignment = Alignment.CenterStart) {
            Box(modifier = Modifier.size(size.dp(120f), size.dp(20f)).drawBehind {
                val mid = this.size.height / 2
                val h = hair.toPx()
                drawLine(dim, Offset(h / 2, 0f), Offset(h / 2, this.size.height), h)
                drawLine(dim, Offset(this.size.width - h / 2, 0f), Offset(this.size.width - h / 2, this.size.height), h)
                // Drawn over the first four fifths of the turn, then held a moment before it starts again
                val end = this.size.width * (drawn / THINKING_DRAW).coerceAtMost(1f)
                if (end > 0f) {
                    val a = arrow.toPx()
                    drawLine(ink, Offset(0f, mid), Offset(end, mid), line.toPx())
                    drawPath(Path().apply { moveTo(0f, mid); lineTo(a, mid - a * 0.45f); lineTo(a, mid + a * 0.45f); close() }, ink)
                }
            })
        }
    }

    /**
     * The user's message is a fill of the ink pushed to the right, its text in the paper; the AI's
     * the paper in a sketch's frame, the system's the same in the secondary ink. Full: a long text
     * never stands on the grid or a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        val line = blueprintStroke(LINE)
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .fill(c.ink.srgb)
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) {
                    CompositionLocalProvider(LocalBlueprintInk provides c.paper.srgb) { content() }
                }
            }
            MessageSender.AI -> Box(
                modifier = Modifier.fillMaxWidth().fill(c.paper.srgb).sketchFrame(line, c.ink.srgb, blueprintStroke(OVERSHOOT))
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) { content() }
            MessageSender.SYSTEM -> Box(
                modifier = Modifier.fillMaxWidth().fill(c.paper.srgb).sketchFrame(line, c.dim.srgb, blueprintStroke(OVERSHOOT))
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) {
                CompositionLocalProvider(LocalBlueprintInk provides c.dim.srgb) { content() }
            }
        }
    }

    /** The paper in a sketch's frame of the alert's colour, a band hatched in it across its top: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        val alert = c.alert.srgb
        Column(
            modifier = Modifier.fillMaxWidth()
                .fill(c.paper.srgb)
                .sketchFrame(blueprintStroke(LINE), alert, blueprintStroke(OVERSHOOT)),
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(size.dp(INTERACTION_BAND)).hatched(alert, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH)))
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = size.dp(12f), end = size.dp(12f), bottom = size.dp(4f)),
                verticalArrangement = Arrangement.spacedBy(size.dp(8f))
            ) {
                Text(title, TextType.TITLE, true, null)
                content()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(8f), Alignment.End), content = actions)
            }
        }
    }

    // =====================================
    // PIECES
    // =====================================

    /** A text in one style and colour, cut with an ellipsis past [maxLines]; the lettering in capitals. */
    @Composable
    private fun Line(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) =
        Line(AnnotatedString(text), style, color, modifier, align, maxLines)

    /** The same, its runs keeping their emphasis; [capitals] false keeps a lettering text as written (a field's hint). */
    @Composable
    private fun Line(text: AnnotatedString, style: TextStyle, color: Color, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE, capitals: Boolean = true) {
        val shown = if (capitals && style.isLettering) text.toUpperCase(LocaleList(Locale.getDefault().toLanguageTag())) else text
        BasicText(
            text = shown,
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
        Line(text, blueprintSize().button, LocalBlueprintInk.current ?: blueprintColors.ink.srgb, maxLines = 1)
    }

    /**
     * A button a finger's target tall: a fill of the ink for the main one, a sketch's frame for a
     * secondary one, the same in the alert's colour for a destructive one, its word alone for the
     * others; disabled, a dashed frame and the secondary ink. Pressed, a fill lightens and the rest
     * fills with hatching.
     */
    @Composable
    private fun Block(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        val line = blueprintStroke(LINE)
        val overshoot = blueprintStroke(OVERSHOOT)
        val ink = LocalBlueprintInk.current ?: c.ink.srgb
        // What the button is drawn as: a fill, a frame (dashed or not), or its word alone
        data class Look(val fill: Color?, val frame: Color?, val dashed: Boolean, val word: Color)
        val look = when {
            !enabled -> when (type) {
                ButtonType.PRIMARY, ButtonType.SECONDARY, ButtonType.DANGER -> Look(null, c.dim.srgb, true, c.dim.srgb)
                else -> Look(null, null, false, c.dim.srgb)
            }
            type == ButtonType.PRIMARY -> Look(c.ink.srgb, null, false, c.paper.srgb)
            type == ButtonType.SECONDARY -> Look(null, ink, false, ink)
            type == ButtonType.DANGER -> Look(null, c.alert.srgb, false, c.alert.srgb)
            else -> Look(null, null, false, ink)
        }
        val plain = look.fill == null && look.frame == null
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .let { m -> look.fill?.let { m.fill(if (pressed) lerp(it, c.paper.srgb, PRESSED_FILL) else it) } ?: m }
                .let { if (look.fill == null) it.pressedHatch(pressed, ink, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH)) else it }
                .let { m -> look.frame?.let { m.sketchFrame(line, it, overshoot, dashed = look.dashed) } ?: m }
                .padding(horizontal = if (plain) size.dp(8f) else size.dp(16f)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalBlueprintInk provides look.word) { content() }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; switched on ([active]) or the main
     * one, a circle of the ink. Pressed, the ink tints it.
     */
    @Composable
    private fun IconSquare(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = blueprintSize()
        val c = blueprintColors
        val filled = (active || type == ButtonType.PRIMARY) && enabled
        val tint = when {
            !enabled -> c.dim.srgb
            filled -> c.paper.srgb
            type == ButtonType.DANGER -> c.alert.srgb
            else -> LocalBlueprintInk.current ?: c.ink.srgb
        }
        val ink = c.ink.srgb
        Box(
            modifier = Modifier.size(size.touch).drawBehind {
                if (filled) drawCircle(ink)
                if (pressed) drawCircle(if (filled) Color.White else ink, alpha = PRESSED_FILL)
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

    /** An icon alone a finger's target square: a month's arrow. */
    @Composable
    private fun PlainIconButton(icon: String, onClick: () -> Unit) {
        Pressable(onClick = onClick) { pressed -> IconSquare(icon, null, ButtonType.DEFAULT, true, pressed, false) }
    }

    /** A − or + in a sketch's frame, a finger's target. */
    @Composable
    private fun CounterButton(icon: String, onClick: () -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.touch, size.dp(40f))
                    .pressedHatch(pressed, c.ink.srgb, blueprintStroke(HATCH_LINE), size.dp(HATCH_PITCH))
                    .sketchFrame(blueprintStroke(LINE), c.ink.srgb, blueprintStroke(OVERSHOOT)),
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, null, size.dp(20f), c.ink.srgb) }
        }
    }

    /** A Lucide icon by name, redrawn by the theme, at [size], in [tint]. */
    @Composable
    private fun NamedIcon(name: String, description: String?, size: Dp, tint: Color) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        BlueprintIcon(resource, size, tint, description)
    }

    /** Two lines [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = blueprintSize()
        val ink = blueprintColors.ink.srgb
        val bar = blueprintStroke(LINE)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(5f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(bar).background(ink)) }
        }
    }

    /** A dashed line across the width, in [color]. */
    @Composable
    private fun DashedRule(modifier: Modifier, color: Color) {
        val line = blueprintStroke(LINE)
        Box(modifier = modifier.fillMaxWidth().height(line).drawBehind {
            val w = line.toPx()
            drawLine(color, Offset(0f, w / 2), Offset(this.size.width, w / 2), w, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(4 * w, 3 * w)))
        })
    }

    /** A line between two cells of a title block, its full height. */
    @Composable
    private fun CellLine(width: Dp, color: Color) {
        Box(modifier = Modifier.fillMaxHeight().width(width).background(color))
    }

    /** What is written in a field: a line under it, [thick] while it has the focus; a finger's target tall. */
    @Composable
    private fun Underlined(color: Color, thick: Boolean, content: @Composable () -> Unit) {
        val size = blueprintSize()
        val line = blueprintStroke(if (thick) FOCUS_LINE else LINE)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .drawBehind {
                    val w = line.toPx()
                    drawRect(color, Offset(0f, this.size.height - w), GeometrySize(this.size.width, w))
                }
                .padding(horizontal = size.dp(2f), vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /** A compass at work: an arc drawn round a centre's cross to a full circle, then rubbed out from its start. */
    @Composable
    private fun Compass(side: Dp) {
        val c = blueprintColors
        val transition = rememberInfiniteTransition(label = "loading")
        // 0 to 1 draws the circle, 1 to 2 rubs it out
        val turn by transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f,
            animationSpec = infiniteRepeatable(tween(LOADING_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "turn"
        )
        val ink = c.ink.srgb
        val dim = c.dim.srgb
        val line = blueprintStroke(LINE)
        val hair = blueprintStroke(1f)
        Box(modifier = Modifier.size(side).drawBehind {
            val w = line.toPx()
            val r = this.size.minDimension / 2 - w
            val centre = Offset(this.size.width / 2, this.size.height / 2)
            val cross = r * 0.25f
            drawLine(dim, Offset(centre.x - cross, centre.y), Offset(centre.x + cross, centre.y), hair.toPx())
            drawLine(dim, Offset(centre.x, centre.y - cross), Offset(centre.x, centre.y + cross), hair.toPx())
            val start = if (turn <= 1f) 0f else (turn - 1f) * 360f
            val sweep = if (turn <= 1f) turn * 360f else 360f - start
            val topLeft = Offset(centre.x - r, centre.y - r)
            drawArc(ink, start - 90f, sweep, false, topLeft, GeometrySize(2 * r, 2 * r), style = Stroke(w))
            // The compass's leg, from the centre to where the lead draws
            val at = Math.toRadians((start + sweep - 90f).toDouble())
            drawLine(dim, centre, Offset(centre.x + r * kotlin.math.cos(at).toFloat(), centre.y + r * kotlin.math.sin(at).toFloat()), hair.toPx())
        })
    }

    /**
     * A window over the screen, fading in: a title block, the paper in a double frame; its [title]
     * in the lettering in a head cell over a line, when it has one; its content, its actions under it.
     */
    @Composable
    private fun DialogFrame(title: String?, onDismiss: () -> Unit, actions: @Composable RowScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            FadeIn {
                val size = blueprintSize()
                val c = blueprintColors
                val line = blueprintStroke(LINE)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fill(c.paper.srgb)
                        .doubleFrame(line, size.dp(3f), c.ink.srgb)
                        .padding(size.dp(DIALOG_INSET)),
                    verticalArrangement = Arrangement.spacedBy(size.dp(12f))
                ) {
                    CompositionLocalProvider(LocalBlueprintInk provides null, LocalBlueprintCrayon provides null) {
                        if (title != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(size.dp(8f))) {
                                Box(modifier = Modifier.padding(horizontal = size.dp(8f))) { Line(title, size.title, c.ink.srgb) }
                                Box(modifier = Modifier.fillMaxWidth().height(line).background(c.ink.srgb))
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f, fill = false).padding(horizontal = size.dp(8f)),
                            verticalArrangement = Arrangement.spacedBy(size.dp(8f)),
                            content = content
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(4f)),
                            horizontalArrangement = Arrangement.spacedBy(size.dp(8f), Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                            content = actions
                        )
                    }
                }
            }
        }
    }

    /** A number in large figures in a sketch's frame, between a + above and a − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, onChange: (Int) -> Unit) {
        val size = blueprintSize()
        val c = blueprintColors
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(10f))) {
            CounterButton("plus") { onChange((value + 1) % modulo) }
            Box(
                modifier = Modifier.sketchFrame(blueprintStroke(LINE), c.ink.srgb, blueprintStroke(OVERSHOOT))
                    .padding(horizontal = size.dp(10f), vertical = size.dp(6f))
            ) {
                Line(String.format(Locale.ROOT, "%02d", value), size.number, c.ink.srgb)
            }
            CounterButton("minus") { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a small title block full of the ink across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = BlueprintToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            BlueprintToasts.dismiss(shown.id)
        }
        val size = blueprintSize()
        val c = blueprintColors
        Box(modifier = modifier.fillMaxWidth().padding(spacing(Spacing.EDGE))) {
            FadeIn {
                Box(modifier = Modifier.fillMaxWidth().inkBlock().padding(horizontal = size.dp(16f), vertical = size.dp(14f))) {
                    Line(shown.message, size.body, c.paper.srgb)
                }
            }
        }
    }

    /** A fill of the ink with a hairline of the paper inside its edge: a small title block, full. */
    @Composable
    private fun Modifier.inkBlock(): Modifier {
        val c = blueprintColors
        val ink = c.ink.srgb
        val paper = c.paper.srgb
        val inset = blueprintStroke(3f)
        val hair = blueprintStroke(1f)
        return this.drawBehind {
            drawRect(ink)
            val i = inset.toPx()
            val h = hair.toPx()
            drawRect(paper, Offset(i + h / 2, i + h / 2), GeometrySize(size.width - 2 * i - h, size.height - 2 * i - h), style = Stroke(h))
        }
    }

    /** An outline [width] thick round the bounds, inside them, without overshoot: a card, a tag, a box. */
    private fun Modifier.plainFrame(width: Dp, color: Color): Modifier = this.drawBehind {
        val w = width.toPx()
        drawRect(color, Offset(w / 2, w / 2), GeometrySize(size.width - w, size.height - w), style = Stroke(w))
    }

    /** The grid of the paper: a line every [GRID_MINOR] dp, a stronger one every [GRID_MAJOR]. */
    private fun DrawScope.drawGrid(minor: Color, major: Color) {
        val step = GRID_MINOR * density
        val every = (GRID_MAJOR / GRID_MINOR).toInt()
        val w = density.coerceAtLeast(1f)
        var i = 0
        var x = 0f
        while (x <= size.width) {
            drawLine(if (i % every == 0) major else minor, Offset(x, 0f), Offset(x, size.height), w)
            x += step; i++
        }
        i = 0
        var y = 0f
        while (y <= size.height) {
            drawLine(if (i % every == 0) major else minor, Offset(0f, y), Offset(size.width, y), w)
            y += step; i++
        }
    }

    /** A knob: a circle of [fill] at [centre], [radius] across, ringed in [ink], marked with its centre's cross. */
    private fun DrawScope.translateCentreMark(centre: Offset, radius: Float, fill: Color, ink: Color, width: Float) {
        drawCircle(fill, radius, centre)
        drawCircle(ink, radius - width / 2, centre, style = Stroke(width))
        val arm = radius * 0.7f
        drawLine(ink, Offset(centre.x - arm, centre.y), Offset(centre.x + arm, centre.y), width * HAIRLINE)
        drawLine(ink, Offset(centre.x, centre.y - arm), Offset(centre.x, centre.y + arm), width * HAIRLINE)
    }

    /** The same mark filling the bounds. */
    private fun DrawScope.drawCentreMark(fill: Color, ink: Color, width: Float) =
        translateCentreMark(Offset(size.width / 2, size.height / 2), size.minDimension / 2, fill, ink, width)

    /** A saturated blue at the hue shift [shift] of [mode]'s palette, for the hue band. */
    private fun hueSample(mode: PaletteMode, shift: Int): Color =
        Lch(HUE_SAMPLE_LIGHTNESS, HUE_SAMPLE_CHROMA, BlueprintPalettes.colors(mode, shift).numbers.blueHue).srgb

    /**
     * How the motifs are laid (docs/design/tool-texture.md): a cell's unit a dp at the size step,
     * smoothed, so a motif grows with everything else; an ICON motif's icon sixteen units, sixteen
     * around it.
     */
    @Composable
    private fun textureStyle(): app.treelune.core.themes.TextureStyle {
        val unit = with(LocalDensity.current) { blueprintSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = false, iconUnits = 16, iconGapUnits = 16)
    }

    /** A focused field's line, in dp. */
    private const val FOCUS_LINE = 2.5f

    /** Hatching's lines and their pitch, in dp. */
    private const val HATCH_LINE = 1f
    private const val HATCH_PITCH = 6f

    /** A fill's lightening, or the ink's tint, under the finger. */
    private const val PRESSED_FILL = 0.2f

    /** A section's axis, the ink at this opacity. */
    private const val RULE_ALPHA = 0.75f

    /** The hatched band over an interaction card, in dp; a dialog's room inside its frame. */
    private const val INTERACTION_BAND = 8f
    private const val DIALOG_INSET = 10f

    /** The paper's grid: a small square's side and a large one's, in dp. */
    private const val GRID_MINOR = 8f
    private const val GRID_MAJOR = 40f

    /** The launcher's monochrome layer over the part of it that holds the drawing. */
    private const val LAUNCHER_LAYER = 108f / 72f

    /** The hue band's colours: the blue, saturated. */
    private const val HUE_SAMPLE_LIGHTNESS = 0.62f
    private const val HUE_SAMPLE_CHROMA = 0.16f

    /** Degrees between two colours the hue band computes. */
    private const val HUE_STRIP_STEP = 10

    private const val MAX_GRID_DP = 480
    private const val ASIDE_ALPHA = 0.4f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val LOADING_MS = 2400
    private const val THINKING_MS = 1600
    /** The part of the AI's turn the dimension line takes to draw itself; the rest it stays drawn. */
    private const val THINKING_DRAW = 0.8f
}

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object BlueprintToasts {
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
