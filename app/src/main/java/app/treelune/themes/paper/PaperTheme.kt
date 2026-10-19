package app.treelune.themes.paper

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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
 * The cut paper theme (docs/design/paper-theme.md): the app in sheets of coloured paper cut with
 * scissors and laid a little askew on kraft, each on a hard shadow, never blurred. A tool's tile
 * is a sheet of its icon's colour (cream without one), tilted by its own seed; a group's title a
 * coral band and a torn strip; the page's title on a cream strip. Lucide cut in facets
 * (PaperIcons); in Bricolage Grotesque (PaperSize); under the finger a sheet is pressed flat on its
 * shadow, a window falls in as a sheet laid on the table (PaperPieces); two palettes of a handful
 * of OKLCH numbers (PaperPalette). Silent for now.
 */
@OptIn(ExperimentalFoundationApi::class)
object PaperTheme : ThemeContract {

    override fun name(context: Context): String =
        Strings.`for`(context = context, theme = "paper").theme("name")

    /** Lucide's icons, cut again by the theme as it draws them (PaperIcons): the drawables stay Lucide's. */
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

    /** Twelve between two columns, left to the grid: room for a shadow and a tilt. */
    @Composable
    override fun gridColumnGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Twelve between two rows, as between two columns. */
    @Composable
    override fun gridRowGapPx(): Int = with(LocalDensity.current) { 12f.dp.roundToPx() }

    /** Silent: the theme's sounds are chosen apart (docs/THEMES.md, Les sons). */
    override fun sound(signal: UISignal): Int? = null

    /** How far the content stands from the tile's top: 6 dp, 8 all round an icon's tile. */
    @Composable
    override fun tileFrame(displayMode: DisplayMode): Dp =
        paperSize().dp(if (displayMode == DisplayMode.ICON) 8f else 6f)

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
     * A round of coral cut by hand, laid on a round of mustard shifted down and right, both on the
     * shadow (the mockup); its icon in the ink that reads on the coral. Pressed, it lies flat.
     */
    @Composable
    override fun FloatingButton(action: ButtonAction, onClick: () -> Unit) {
        val size = paperSize()
        val c = paperColors
        Pressable(onClick = onClick) { pressed ->
            val coral = c.accent.srgb
            val mustard = c.colour(TagColor.YELLOW).srgb
            val shadow = c.shadow
            val under = size.dp(5f)
            val drop = size.dp(7f)
            Box(
                modifier = Modifier.size(size.floating)
                    .graphicsLayer { if (pressed) { translationX = under.toPx(); translationY = under.toPx() } }
                    .drawBehind {
                        val outline = BLOB.createOutline(this.size, layoutDirection, this)
                        if (!pressed) {
                            translate(drop.toPx(), drop.toPx()) { drawOutline(outline, shadow) }
                            translate(under.toPx(), under.toPx() * 0.8f) { drawOutline(outline, mustard) }
                        }
                        drawOutline(outline, coral)
                    },
                contentAlignment = Alignment.Center
            ) {
                NamedIcon(action.iconName, action.label(), size.dp(28f), c.onAccent.srgb)
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
                // Switched on, a button is the night blue, as the main one
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

    /** Each type in its size and weight; a section's title on its coral band, a little askew. */
    @Composable
    override fun Text(text: AnnotatedString, type: TextType, fillMaxWidth: Boolean, textAlign: TextAlign?, maxLines: Int) {
        val size = paperSize()
        val c = paperColors
        val override = LocalPaperInk.current
        if (type == TextType.HEADING && LocalPaperSection.current) {
            SectionBand(text, maxLines)
            return
        }
        val (style, ink) = when (type) {
            TextType.TITLE -> size.title to (override ?: c.ink.srgb)
            TextType.SUBTITLE -> size.subtitle to (override ?: c.ink.srgb)
            TextType.HEADING -> size.name to (override ?: c.ink.srgb)
            TextType.BODY -> size.body to (override ?: c.ink.srgb)
            TextType.STRONG -> size.strong to (override ?: c.ink.srgb)
            TextType.CAPTION -> size.caption to (override ?: c.dim.srgb)
            TextType.LABEL -> size.label to (override ?: c.dim.srgb)
            TextType.ERROR -> size.body to c.error.srgb
            TextType.WARNING -> size.body to c.warning.srgb
        }
        Line(text, style, ink, if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier, textAlign, maxLines)
    }

    /** Bricolage's 800 for the bold; Android's slant for the italic, Bricolage having none. */
    @Composable
    override fun emphasis(emphasis: Emphasis): SpanStyle = when (emphasis) {
        Emphasis.BOLD -> SpanStyle(fontWeight = FontWeight.ExtraBold)
        Emphasis.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    }

    /**
     * A card is a cream sheet on its shadow, straight, full so that a page's motif never runs under
     * its text; a highlighted one outlined in coral. A section's header is its content (the title
     * on its coral band, the group's buttons), then a torn strip of the cream sheet across the width.
     */
    @Composable
    override fun Card(type: CardType, size: Size, highlight: Boolean, content: @Composable () -> Unit) {
        val s = paperSize()
        val c = paperColors
        when (type) {
            CardType.SECTION_HEADER -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(Spacing.EDGE)), verticalArrangement = Arrangement.spacedBy(s.dp(4f))) {
                CompositionLocalProvider(LocalPaperSection provides true) { content() }
                TornStrip(s.dp(6f), c.sheet.srgb, SECTION_TILT)
            }
            CardType.DEFAULT -> {
                val shape = Cut.B.shape(s)
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .paperSheet(c.sheet.srgb, shape)
                        .let { if (highlight) it.outline(paperStroke(2f), c.accent.srgb, shape) else it }
                        .padding(horizontal = s.dp(12f), vertical = s.dp(10f))
                ) { content() }
            }
        }
    }

    /** A round of the state's colour, cut by hand. */
    @Composable
    override fun StatusIndicator(color: Color, size: Dp) {
        Box(modifier = Modifier.size(size).background(color, BLOB))
    }

    @Composable
    override fun statusColor(status: StatusColor): Color {
        val c = paperColors
        return when (status) {
            StatusColor.SUCCESS -> c.success
            StatusColor.WARNING -> c.warning
            StatusColor.ERROR -> c.error
            StatusColor.INFO -> c.info
            StatusColor.MUTED -> c.muted
        }.srgb
    }

    /** A label cut from its coloured sheet, on a small shadow, tilted by its word from −3° to +3°; its word in the ink that reads on it. */
    @Composable
    override fun Tag(text: String, color: TagColor) {
        val size = paperSize()
        val c = paperColors
        val tilt = ((text.hashCode() and Int.MAX_VALUE) % 7 - 3).toFloat()
        Box(
            modifier = Modifier.tilted(tilt)
                .sheet(c.colour(color).srgb, RoundedCornerShape(size.dp(2f)), c.shadow, size.dp(1f), size.dp(2f))
                .padding(horizontal = size.dp(8f), vertical = size.dp(1f))
        ) {
            Line(text, size.tag, c.onColour(color).srgb, maxLines = 1)
        }
    }

    /** A night-blue strip 12 dp tall, the part done a strip of mustard cut across at a slant over it (the mockup). */
    @Composable
    override fun Gauge(fraction: Float) {
        val size = paperSize()
        val c = paperColors
        val done = fraction.coerceIn(0f, 1f)
        Box(modifier = Modifier.fillMaxWidth().height(size.dp(12f))) { Strip(done, c.navy.srgb, c.colour(TagColor.YELLOW).srgb) }
    }

    @Composable
    override fun drawingTextStyle(): TextStyle = paperSize().caption.copy(color = paperColors.dim.srgb, fontWeight = FontWeight.ExtraBold)

    /** Smooth: one screen pixel. */
    @Composable
    override fun drawingUnit(): Float = 1f

    @Composable
    override fun Drawing(drawing: app.treelune.core.drawing.Drawing, modifier: Modifier) {
        PaperDrawing.Draw(drawing, drawingTextStyle(), paperColors, LocalPaperStrong.current, modifier)
    }

    /** A thin torn strip across the width, in the brown of the dashes. */
    @Composable
    override fun Divider() {
        Box(modifier = Modifier.padding(vertical = paperSize().dp(8f))) { TornStrip(paperSize().dp(3f), paperColors.rule.srgb, 0f) }
    }

    /** Two bars of 3 dp, one over the other, a finger's height: the text beside it keeps the width. */
    @Composable
    override fun DragHandle() {
        val size = paperSize()
        Box(modifier = Modifier.heightIn(min = size.touch), contentAlignment = Alignment.Center) { Bars(size.dp(20f)) }
    }

    @Composable
    override fun DragBar() {
        Box(modifier = Modifier.padding(vertical = paperSize().dp(4f))) { Bars(paperSize().dp(24f)) }
    }

    /** Lifted, an item rises on a longer shadow, more askew; put down, it falls back at once. */
    @Composable
    override fun ReorderItem(lifted: Boolean, content: @Composable () -> Unit) = Lifted(lifted, Cut.A.shape(paperSize()), content)

    /**
     * A night-blue strip on its shadow, its words in cream, a cream line between two; a word
     * touched in cream, its text in the night blue. Words a finger's target tall, going to a
     * second line when they do not hold on one.
     */
    @Composable
    override fun SelectionBar(actions: List<SelectionAction>) {
        val size = paperSize()
        val c = paperColors
        val rule = paperStroke(2f)
        val navy = c.navy.srgb
        val cream = c.onNavy.srgb
        FlowRow(modifier = Modifier.paperSheet(navy, Cut.B.shape(size))) {
            actions.forEachIndexed { index, action ->
                Pressable(onClick = action.onClick) { pressed ->
                    Box(
                        modifier = Modifier
                            .heightIn(min = size.touch)
                            .let { if (pressed) it.background(cream) else it }
                            .drawBehind { if (index > 0) drawRect(cream.copy(alpha = 0.35f), Offset.Zero, GeometrySize(rule.toPx(), this.size.height)) }
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action.label, size.button, if (pressed) navy else cream, maxLines = 1)
                    }
                }
            }
        }
    }

    override fun getTagColor(color: TagColor, mode: PaletteMode): Color = PaperPalettes.colors(mode, 0).colour(color).srgb

    // =====================================
    // FEEDBACK
    // =====================================

    /** A night-blue sheet at the bottom of the screen (FullScreen shows it), for as long as Android's would last. */
    override fun Toast(context: Context, message: String, duration: Duration) {
        val shown = if (duration == Duration.SHORT) TOAST_SHORT_MS else TOAST_LONG_MS
        Handler(Looper.getMainLooper()).post { PaperToasts.show(message, shown) }
    }

    /**
     * A night-blue sheet on its shadow, its message in cream, its action in the mustard; a round
     * of the state's colour before the message, but for a mere information.
     */
    @Composable
    override fun Snackbar(type: FeedbackType, message: String, action: String?, onAction: (() -> Unit)?) {
        val size = paperSize()
        val c = paperColors
        val mark = when (type) {
            FeedbackType.SUCCESS -> c.success
            FeedbackType.ERROR -> c.error
            FeedbackType.WARNING -> c.warning
            FeedbackType.INFO -> null
        }
        val cream = c.onNavy.srgb
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch).paperSheet(c.navy.srgb, Cut.A.shape(size)).padding(start = size.dp(16f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            mark?.let { Box(modifier = Modifier.size(size.dp(10f)).background(it.srgb, BLOB)) }
            Box(modifier = Modifier.weight(1f).padding(vertical = size.dp(12f))) { Line(message, size.body, cream) }
            if (action != null && onAction != null) {
                Pressable(onClick = onAction) { pressed ->
                    Box(
                        modifier = Modifier.heightIn(min = size.touch).pressedTint(pressed, Color.Black, Cut.A.shape(size)).padding(horizontal = size.dp(16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(action, size.button, c.colour(TagColor.YELLOW).srgb, maxLines = 1)
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

    /** Confetti falling one after the other, as large as [size] asks. */
    @Composable
    override fun LoadingIndicator(size: Size) {
        val piece = when (size) {
            Size.XS -> 6f
            Size.S -> 8f
            Size.M -> 10f
            Size.L -> 12f
            Size.XL, Size.XXL -> 16f
        }
        Confetti(paperSize().dp(piece))
    }

    /** Lucide cut from paper, on its shadow; a background, when asked, is a round cut by hand behind it. */
    @Composable
    override fun Icon(resourceId: Int, size: Dp, contentDescription: String?, tint: Color?, background: Color?) {
        Box(modifier = Modifier.size(size).let { m -> background?.let { m.background(it, BLOB) } ?: m }, contentAlignment = Alignment.Center) {
            CutIcon(resourceId, size, tint ?: LocalPaperInk.current ?: paperColors.ink.srgb, paperColors.shadow, contentDescription)
        }
    }

    /**
     * A zone's or a tool's icon on a round cut by hand, on its small shadow: of its colour, the
     * icon in the ink that reads on it, or cream without one; on a tile, cut from the tile's own
     * colour already, the round is always cream, as in the mockup. The round is a third larger than
     * the icon's room, so names beside icons line up.
     */
    @Composable
    override fun ItemIcon(resourceId: Int, size: Dp, color: TagColor?) {
        val s = paperSize()
        val c = paperColors
        val onTile = LocalPaperInTile.current
        val (fill, ink) = if (color != null && !onTile) c.colour(color).srgb to c.onColour(color).srgb else c.sheet.srgb to c.ink.srgb
        Box(
            modifier = Modifier.size(size * BADGE_SCALE).sheet(fill, BLOB, c.shadow, s.dp(1f), s.dp(2f)),
            contentAlignment = Alignment.Center
        ) {
            CutIcon(resourceId, size * ICON_IN_BADGE, ink, null)
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
     * A month on seven columns, its days in figures; the day chosen on a round of coral cut by hand,
     * askew on its shadow, today ringed in the ink. The month and its year in the titles' cut
     * between two chevrons.
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
        val size = paperSize()
        val c = paperColors
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
                    Line(name, size.subtitle, c.ink.srgb, maxLines = 1)
                }
                PlainIconButton("chevron-right") { month = month.plusMonths(1) }
            }
            val first = WeekFields.of(locale).firstDayOfWeek
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
                val ring = paperStroke(2f)
                (0 until (cells + 6) / 7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        (0 until 7).forEach { column ->
                            val n = week * 7 + column - lead + 1
                            Box(modifier = Modifier.weight(1f).height(size.dp(40f)), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val date = month.atDay(n)
                                    val lit = date == chosen
                                    Pressable(onClick = { chosen = date }, modifier = Modifier.fillMaxSize()) { pressed ->
                                        val ink = c.ink.srgb
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Box(
                                                modifier = Modifier.size(size.dp(36f))
                                                    .let { if (lit) it.tilted(DAY_TILT).sheet(c.accent.srgb, BLOB, c.shadow, size.dp(1f), size.dp(2f)) else it }
                                                    .let { if (date == today && !lit) it.outline(ring, ink, BLOB) else it }
                                                    .pressedTint(pressed, ink, BLOB),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                val style = if (lit || date == today) size.strong else size.body
                                                Line(n.toString(), style, if (lit) c.onAccent.srgb else c.ink.srgb)
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

    /** Hours and minutes in the titles' figures on two cream sheets, each between − and + buttons; no dial. */
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(paperSize().dp(8f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                Counter(hour, 24, Cut.B) { hour = it }
                Line(":", paperSize().number, paperColors.ink.srgb)
                Counter(minute, 60, Cut.D) { minute = it }
            }
        }
    }

    // =====================================
    // CONTAINERS
    // =====================================

    /** The kraft and its fibres at 115°; the toasts show over its bottom. */
    @Composable
    override fun FullScreen(content: @Composable () -> Unit) {
        val c = paperColors
        Box(modifier = Modifier.fillMaxSize().kraft(c.ground.srgb, c.rule.srgb)) {
            CompositionLocalProvider(LocalPaperInk provides null, LocalPaperStrong provides null, LocalPaperInTile provides false) {
                content()
                ToastHost(Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    /**
     * A textured page's whole ground: the kraft without its fibres and the motif over it, as on the
     * thing's tile, whatever its colour; nothing for a page without one.
     */
    @Composable
    override fun PageGround(texture: app.treelune.core.themes.ItemTexture?, content: @Composable () -> Unit) {
        val ground = paperColors.ground.srgb
        val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), ground, textureStyle())
        val modifier = if (brush == null) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(ground).background(brush)
        Box(modifier = modifier) { content() }
    }

    /** A row across the width, its pieces spaced; a button of an icon alone there is a round of the night blue (the mockup). */
    @Composable
    override fun HeaderBar(content: @Composable RowScope.() -> Unit) {
        val size = paperSize()
        CompositionLocalProvider(LocalPaperInBar provides true) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = size.dp(8f), vertical = size.dp(6f)),
                horizontalArrangement = Arrangement.spacedBy(size.dp(8f)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }

    /** A zone is a sheet too, on the home screen: the tool's tile. */
    @Composable
    override fun ZoneCardContainer(texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(DisplayMode.CONDENSED, texture, color, seed, onClick, onLongClick, content)

    /** A round of coral cut by hand, askew on its shadow, an exclamation mark in heavy ink (the mockup). */
    @Composable
    override fun WaitingMark() {
        val size = paperSize()
        val c = paperColors
        Mark(c.accent.srgb, WAIT_TILT) {
            Line("!", size.button.copy(letterSpacing = 0.sp), c.onAccent.srgb)
        }
    }

    /** A cell of the grid in edit mode: a rectangle dashed in brown on the kraft. */
    @Composable
    override fun GridCell() {
        val stroke = paperStroke(2f)
        val radius = paperSize().dp(4f)
        val color = paperColors.rule.srgb
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val w = stroke.toPx()
            val r = radius.toPx()
            drawRoundRect(color, Offset(w / 2, w / 2), GeometrySize(size.width - w, size.height - w), CornerRadius(r, r),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3 * w, 2 * w))))
        })
    }

    /**
     * The tile being moved is lifted: it rises on a longer shadow, more askew; put down, it falls
     * back at once. The others step back, faded, the cells showing through them.
     */
    @Composable
    override fun GridTile(state: app.treelune.core.ui.GridTileState, content: @Composable () -> Unit) {
        val chosen = state == app.treelune.core.ui.GridTileState.CHOSEN
        Box(modifier = Modifier.alpha(if (state == app.treelune.core.ui.GridTileState.ASIDE) ASIDE_ALPHA else 1f)) {
            Lifted(chosen, Cut.A.shape(paperSize())) { CompositionLocalProvider(LocalPaperLifted provides chosen) { content() } }
        }
    }

    /** A round of the night blue cut by hand, Lucide's timer cut in cream: a stopwatch runs. */
    @Composable
    override fun RunningMark() {
        val size = paperSize()
        val c = paperColors
        Mark(c.navy.srgb, -WAIT_TILT) { NamedIcon("timer", null, size.dp(13f), c.onNavy.srgb, shadow = false) }
    }

    @Composable
    override fun ToolCardContainer(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) =
        Tile(displayMode, texture, color, seed, onClick, onLongClick, content)

    /**
     * A tile filling the cells the grid gives it: a sheet of its icon's colour (cream without one),
     * its corners cut and its tilt read from its [seed], on its hard shadow, its motif on it; what
     * is written on it in the ink that reads on that colour, what must stand out on it (a checked
     * box, a chart) in that ink too, or the night blue on cream (LocalPaperStrong). Pressed, it
     * lies flat on its shadow.
     */
    @Composable
    private fun Tile(displayMode: DisplayMode, texture: app.treelune.core.themes.ItemTexture?, color: TagColor?, seed: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
        val c = paperColors
        val size = paperSize()
        val laid = remember(seed) { Laid(seed) }
        val shape = laid.cut.shape(size)
        val frame = tileFrame(displayMode)
        val surface = color?.let { c.colour(it).srgb } ?: c.sheet.srgb
        val ink = color?.let { c.onColour(it).srgb }
        Box(modifier = Modifier.fillMaxSize().tilted(laid.tilt)) {
            Pressable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxSize()) { pressed ->
                // The theme's motif tone, whatever the sheet's colour
                val brush = app.treelune.core.themes.rememberTextureBrush(texture?.copy(color = null), surface, textureStyle())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .paperSheet(surface, shape, pressed)
                        .drawBehind { brush?.let { drawOutline(shape.createOutline(this.size, layoutDirection, this), it) } }
                        .let { if (displayMode == DisplayMode.ICON) it.padding(frame) else it.padding(start = size.dp(10f), end = size.dp(10f), top = frame, bottom = size.dp(4f)) }
                ) {
                    CompositionLocalProvider(
                        LocalPaperInk provides ink,
                        LocalPaperStrong provides ink,
                        LocalPaperInTile provides true
                    ) { content() }
                }
            }
        }
    }

    /**
     * The page's title on a strip of the cream sheet laid at −2° on its shadow, centred, the page's
     * icon on its round before it; shrunk to hold in two lines, never under a dialog's title. The
     * subtitle under it on a smaller strip, the other way. The app's mark is its launcher icon, in
     * its colours, on a round cut by hand.
     */
    @Composable
    override fun PageHeader(
        title: String,
        subtitle: String?,
        icon: String?,
        iconColor: TagColor?,
        appMark: Boolean
    ) {
        val size = paperSize()
        val c = paperColors
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing(Spacing.EDGE), vertical = size.dp(10f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(size.dp(10f), Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                if (appMark) Box(modifier = Modifier.sheet(Color.Transparent, BLOB, c.shadow, size.dp(1f), size.dp(2f))) {
                    app.treelune.core.ui.components.LauncherMark(size.dp(PAGE_ICON) * BADGE_SCALE, BLOB)
                }
                else icon?.let { app.treelune.core.ui.UI.ItemIcon(it, iconColor, size.dp(PAGE_ICON)) }
                Box(modifier = Modifier.weight(1f, fill = false).tilted(TITLE_TILT).paperSheet(c.sheet.srgb, Cut.A.shape(size)).padding(horizontal = size.dp(18f), vertical = size.dp(2f))) {
                    BasicText(
                        text = title,
                        style = size.pageTitle.copy(
                            color = c.ink.srgb,
                            textAlign = TextAlign.Center,
                            hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                            lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(minFontSize = size.title.fontSize, maxFontSize = size.pageTitle.fontSize, stepSize = 2.sp)
                    )
                }
            }
            subtitle?.let {
                Box(modifier = Modifier.tilted(-TITLE_TILT / 2).paperSheet(c.sheet.srgb, Cut.C.shape(size)).padding(horizontal = size.dp(8f))) {
                    Line(it, size.caption, c.dim.srgb)
                }
            }
        }
    }

    // =====================================
    // FORMS
    // =====================================

    /** In heavy ink; a field that must be answered says so by an asterisk. */
    @Composable
    override fun FieldLabel(label: String, required: Boolean) {
        if (label.isBlank()) return
        Line(if (required) "$label *" else label, paperSize().label, LocalPaperInk.current ?: paperColors.ink.srgb)
    }

    /**
     * A white sheet on its shadow, outlined in 2 dp of the ink, of the coral while the field has
     * the focus, of the error's colour when it is wrong; the label above, and again inside while
     * it is empty.
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
        val size = paperSize()
        val c = paperColors
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
                Boxed(
                    when {
                        error -> c.error.srgb
                        focused -> c.accent.srgb
                        else -> c.ink.srgb
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
     * The choice shown as a field, its icon before it, a chevron at its end; the options on a sheet
     * of the dialog's frame, under their sections' titles, the chosen one a strip of the night blue.
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
        val size = paperSize()
        val c = paperColors
        // An option's icon, as its item's is drawn, before its name
        @Composable
        fun OptionIconOf(index: Int?) {
            val icon = index?.let { icons.getOrNull(it) } ?: return
            Box(modifier = Modifier.padding(end = size.dp(12f))) { app.treelune.core.ui.UI.ItemIcon(icon.name, icon.color, size.dp(20f)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            FieldLabel(label, required)
            Pressable(onClick = { open = true }) { pressed ->
                Boxed(if (pressed) c.accent.srgb else c.ink.srgb, pressed) {
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
                        // A section's title above its first option, not chosen
                        val section = sections.getOrNull(index)
                        if (section != null && section != sections.getOrNull(index - 1)) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = size.dp(16f), bottom = size.dp(4f))) {
                                Line(section, size.label, c.dim.srgb)
                            }
                        }
                        val chosen = index == selected
                        val shape = Cut.C.shape(size)
                        Pressable(onClick = { onSelect(index); open = false }, modifier = Modifier.fillMaxWidth()) { pressed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = size.touch)
                                    .let { if (chosen) it.paperSheet(c.navy.srgb, shape) else it }
                                    .pressedTint(pressed, c.ink.srgb, shape)
                                    .padding(horizontal = size.dp(12f)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompositionLocalProvider(LocalPaperInk provides if (chosen) c.onNavy.srgb else null) {
                                    OptionIconOf(index)
                                    Box(modifier = Modifier.weight(1f)) { Line(option, if (chosen) size.strong else size.body, if (chosen) c.onNavy.srgb else c.ink.srgb) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** A white square with uneven corners, tilted at −4°, outlined in the ink; checked, full of the coral (the mockup). */
    @Composable
    override fun Checkbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String?) {
        val size = paperSize()
        val c = paperColors
        val shape = RoundedCornerShape(size.dp(3f), size.dp(5f), size.dp(2f), size.dp(4f))
        Pressable(onClick = { onCheckedChange(!checked) }) { pressed ->
            Row(
                modifier = Modifier.heightIn(min = size.touch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(size.dp(12f))
            ) {
                Box(
                    modifier = Modifier.size(size.dp(20f)).tilted(BOX_TILT)
                        .background(if (checked) c.accent.srgb else c.white.srgb, shape)
                        .outline(paperStroke(2f), c.ink.srgb, shape)
                        .pressedTint(pressed, c.ink.srgb, shape)
                )
                label?.let { Line(it, size.body, LocalPaperInk.current ?: c.ink.srgb) }
            }
        }
    }

    /** The label across, then a strip of white paper outlined in the ink whose round knob slides: the night blue when on, its knob cream. */
    @Composable
    override fun Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String) {
        val size = paperSize()
        val c = paperColors
        Pressable(onClick = { onCheckedChange(!checked) }, modifier = Modifier.fillMaxWidth()) { pressed ->
            Row(modifier = Modifier.fillMaxWidth().heightIn(min = size.touch), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) { Line(label, size.body, c.ink.srgb) }
                val shape = Cut.A.shape(size)
                Box(
                    modifier = Modifier.size(size.dp(52f), size.dp(28f))
                        .background(if (checked) c.navy.srgb else c.white.srgb, shape)
                        .outline(paperStroke(2f), if (checked) c.navy.srgb else c.ink.srgb, shape)
                        .pressedTint(pressed, c.ink.srgb, shape)
                        .padding(size.dp(4f)),
                    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.size(size.dp(20f)).background(if (checked) c.onNavy.srgb else c.ink.srgb, BLOB))
                }
            }
        }
    }

    /** Labels in heavy ink: the tab shown a cream sheet standing out, a little askew, the others in the secondary ink. */
    @Composable
    override fun Tabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val size = paperSize()
        val c = paperColors
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = size.dp(4f), bottom = size.dp(4f)),
            horizontalArrangement = Arrangement.spacedBy(size.dp(4f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Pressable(onClick = { onSelect(index) }) { pressed ->
                    val shown = index == selected
                    val shape = Cut.entries[index % Cut.entries.size].shape(size)
                    Box(
                        modifier = Modifier.heightIn(min = size.touch)
                            .let { if (shown) it.tilted(TAB_TILT).offset(y = -size.dp(3f)).paperSheet(c.sheet.srgb, shape) else it }
                            .pressedTint(pressed && !shown, c.ink.srgb, shape)
                            .padding(horizontal = size.dp(14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Line(label, size.button, if (shown) c.ink.srgb else c.dim.srgb, maxLines = 1)
                    }
                }
            }
        }
    }

    /** Two cream sheets side by side: the chosen one the night blue, tilted a little over the other. */
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
        val size = paperSize()
        val c = paperColors
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(4f))) {
            if (label.isNotBlank()) FieldLabel(label, required)
            Row(modifier = if (compact) Modifier else Modifier.fillMaxWidth()) {
                listOf(true to trueLabel, false to falseLabel).forEachIndexed { index, (answer, text) ->
                    val chosen = value == answer
                    val shape = (if (index == 0) Cut.A else Cut.C).shape(size)
                    Pressable(
                        onClick = { if (value != answer) onValueChange(answer) else if (emptiable) onValueChange(null) },
                        modifier = if (compact) Modifier else Modifier.weight(1f)
                    ) { pressed ->
                        Box(
                            modifier = (if (compact) Modifier else Modifier.fillMaxWidth())
                                .heightIn(min = size.touch)
                                .tilted(if (chosen) CHOSEN_TILT else 0f)
                                .paperSheet(if (chosen) c.navy.srgb else c.sheet.srgb, shape, pressed)
                                .padding(horizontal = size.dp(16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Line(text, size.button, if (chosen) c.onNavy.srgb else c.ink.srgb, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    /**
     * The coral at each shift, saturated, along a strip with uneven corners on its shadow; the
     * shift chosen a round cut by hand, ringed in the ink over a ring of cream. A touch or a drag
     * anywhere on it answers.
     */
    @Composable
    override fun HueSlider(value: Int, shifts: IntRange, onValueChange: (Int) -> Unit) {
        val size = paperSize()
        val c = paperColors
        val mode = CurrentTheme.paletteMode
        val colors = remember(mode, shifts) {
            (shifts step HUE_STRIP_STEP).map { PaperPalettes.colors(mode, it).accent.srgb } + PaperPalettes.colors(mode, shifts.last).accent.srgb
        }
        val knob = size.dp(30f)
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().heightIn(min = size.touch)
                .horizontalPick { onValueChange(app.treelune.core.ui.RangePick.valueAt(it, shifts)) },
            contentAlignment = Alignment.CenterStart
        ) {
            val band = size.dp(24f)
            val shape = Cut.A.shape(size)
            val shadow = c.shadow
            val dx = size.dp(SHADOW_X)
            val dy = size.dp(SHADOW_Y)
            Box(modifier = Modifier.fillMaxWidth().height(band).drawBehind {
                val outline = shape.createOutline(this.size, layoutDirection, this)
                translate(dx.toPx(), dy.toPx()) { drawOutline(outline, shadow) }
                drawOutline(outline, Brush.horizontalGradient(colors))
            })
            val ink = c.ink.srgb
            val cream = c.sheet.srgb
            val ring = paperStroke(3f)
            Box(
                modifier = Modifier
                    .offset(x = (maxWidth - knob) * app.treelune.core.ui.RangePick.fractionOf(value, shifts))
                    .size(knob)
                    .outline(ring, ink, BLOB)
                    .padding(ring)
                    .outline(ring, cream, BLOB)
            )
        }
    }

    /**
     * The value above in heavy figures, a night-blue strip 12 dp tall across the width, the part
     * chosen a strip of mustard cut at a slant up to a round of cream the finger moves; a touch or
     * a drag anywhere on it answers. No answer yet shows the strip alone and "—".
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
        val size = paperSize()
        val c = paperColors
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
                val knob = size.dp(24f)
                val room = maxWidth - knob
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = size.touch)
                        .pointerInput(min, lastStop, step, width) { detectTapGestures { answer(it.x) } }
                        .pointerInput(min, lastStop, step, width) { detectHorizontalDragGestures { change, _ -> answer(change.position.x) } },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(size.dp(12f))) { Strip(fraction ?: 0f, c.navy.srgb, c.colour(TagColor.YELLOW).srgb) }
                    if (fraction != null) {
                        Box(
                            modifier = Modifier
                                .offset(x = room * fraction)
                                .size(knob)
                                .paperSheet(c.sheet.srgb, BLOB)
                                .outline(paperStroke(2f), c.ink.srgb, BLOB)
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
        val c = PaperPalettes.colors(mode, hueShift)
        fun scheme(dark: Boolean) = if (dark) darkColorScheme(
            primary = c.navy.srgb, onPrimary = c.onNavy.srgb,
            secondary = c.ink.srgb, onSecondary = c.sheet.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onAccent.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.sheet.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.sheet.srgb,
            outline = c.ink.srgb, outlineVariant = c.rule.srgb
        ) else lightColorScheme(
            primary = c.navy.srgb, onPrimary = c.onNavy.srgb,
            secondary = c.ink.srgb, onSecondary = c.sheet.srgb,
            tertiary = c.accent.srgb, onTertiary = c.onAccent.srgb,
            background = c.ground.srgb, onBackground = c.ink.srgb,
            surface = c.sheet.srgb, onSurface = c.ink.srgb,
            surfaceVariant = c.ground.srgb, onSurfaceVariant = c.dim.srgb,
            error = c.error.srgb, onError = c.sheet.srgb,
            outline = c.ink.srgb, outlineVariant = c.rule.srgb
        )
        return scheme(mode == PaletteMode.DARK)
    }

    // =====================================
    // AI
    // =====================================

    /** Three confetti falling one after the other, then again. The AI's wait asks nothing of the user. */
    @Composable
    override fun AIThinkingIndicator() {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = paperSize().dp(8f)), contentAlignment = Alignment.CenterStart) {
            Confetti(paperSize().dp(10f))
        }
    }

    /**
     * The user's message is a night-blue sheet pushed to the right, its text in cream; the AI's a
     * cream sheet, the system's the same in the secondary ink. Straight, full sheets: a long text
     * never stands askew nor on a motif.
     */
    @Composable
    override fun MessageBubble(sender: MessageSender, content: @Composable () -> Unit) {
        val size = paperSize()
        val c = paperColors
        when (sender) {
            MessageSender.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .padding(start = size.dp(48f))
                        .paperSheet(c.navy.srgb, Cut.A.shape(size))
                        .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
                ) {
                    CompositionLocalProvider(LocalPaperInk provides c.onNavy.srgb) { content() }
                }
            }
            MessageSender.AI -> Box(
                modifier = Modifier.fillMaxWidth().paperSheet(c.sheet.srgb, Cut.D.shape(size))
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) { content() }
            MessageSender.SYSTEM -> Box(
                modifier = Modifier.fillMaxWidth().paperSheet(c.sheet.srgb, Cut.C.shape(size))
                    .padding(horizontal = size.dp(12f), vertical = size.dp(10f))
            ) {
                CompositionLocalProvider(LocalPaperInk provides c.dim.srgb) { content() }
            }
        }
    }

    /** A cream sheet under a ribbon of coral, a signal that the user is asked: the title, the content, the actions. */
    @Composable
    override fun InteractionCard(title: String, content: @Composable ColumnScope.() -> Unit, actions: @Composable RowScope.() -> Unit) {
        val size = paperSize()
        val c = paperColors
        val shape = Cut.B.shape(size)
        val ribbon = size.dp(RIBBON)
        val coral = c.accent.srgb
        Column(
            modifier = Modifier.fillMaxWidth()
                .paperSheet(c.sheet.srgb, shape)
                .drawWithContent {
                    drawContent()
                    clipPath(Path().apply { addOutline(shape.createOutline(this@drawWithContent.size, layoutDirection, this@drawWithContent)) }) {
                        drawRect(coral, Offset.Zero, GeometrySize(this.size.width, ribbon.toPx()))
                    }
                }
                .padding(start = size.dp(12f), end = size.dp(12f), top = size.dp(RIBBON + 10f), bottom = size.dp(4f)),
            verticalArrangement = Arrangement.spacedBy(size.dp(8f))
        ) {
            Text(title, TextType.TITLE, true, null)
            content()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(size.dp(8f), Alignment.End), content = actions)
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

    /** A section's title on a band of coral tilted at −1.5° on its shadow, in the ink that reads on the coral. */
    @Composable
    private fun SectionBand(text: AnnotatedString, maxLines: Int) {
        val size = paperSize()
        val c = paperColors
        Box(
            modifier = Modifier.padding(vertical = size.dp(4f)).tilted(SECTION_TILT)
                .paperSheet(c.accent.srgb, RoundedCornerShape(size.dp(2f)))
                .padding(horizontal = size.dp(10f), vertical = size.dp(1f))
        ) {
            Line(text, size.section, c.onAccent.srgb, maxLines = maxLines)
        }
    }

    /** A strip torn along both its edges, [height] tall across the width, of [color], laid at [tilt] degrees. */
    @Composable
    private fun TornStrip(height: Dp, color: Color, tilt: Float) {
        Box(modifier = Modifier.fillMaxWidth().height(height).tilted(-tilt / 3f).drawBehind {
            val w = this.size.width
            val h = this.size.height
            // The edges zigzag a little every so often, the same tear at every width
            val tear = Path().apply {
                val teeth = (w / (h * 8f)).toInt().coerceAtLeast(2)
                moveTo(0f, h * 0.2f)
                for (i in 1..teeth) lineTo(w * i / teeth, h * (if (i % 2 == 0) 0.2f else 0f) + h * 0.1f * (i % 3))
                for (i in teeth downTo 0) lineTo(w * i / teeth, h * (if (i % 2 == 0) 0.9f else 1f) - h * 0.1f * (i % 3))
                close()
            }
            drawPath(tear, color)
        })
    }

    /** A strip of [rail] filling the box, and over it the part [done] in [fill], its end cut at a slant. */
    @Composable
    private fun Strip(done: Float, rail: Color, fill: Color) {
        val corner = paperSize().dp(2f)
        Box(modifier = Modifier.fillMaxSize().drawBehind {
            val r = corner.toPx()
            drawRoundRect(rail, cornerRadius = CornerRadius(r, r))
            if (done > 0f) {
                val end = this.size.width * done
                val h = this.size.height
                // Cut across at a slant, a strip of paper, its top and bottom a little uneven
                drawPath(Path().apply {
                    moveTo(0f, 0f)
                    lineTo(end, h * 0.1f)
                    lineTo((end - h * 0.25f).coerceAtLeast(0f), h)
                    lineTo(0f, h * 0.9f)
                    close()
                }, fill)
            }
        })
    }

    /** A button's word, in the ink its sheet imposes. */
    @Composable
    private fun Word(text: String) {
        Line(text, paperSize().button, LocalPaperInk.current ?: paperColors.ink.srgb, maxLines = 1)
    }

    /**
     * A button a finger's target tall: a night-blue sheet for the main one, a cream sheet outlined
     * in the ink for a secondary one, a coral sheet for a destructive one, its word alone for the
     * others; the sheets on their shadow. Disabled, the same without a shadow, faded. Pressed, a
     * sheet lies flat on its shadow, a word alone darkens.
     */
    @Composable
    private fun Block(type: ButtonType, enabled: Boolean, pressed: Boolean, content: @Composable () -> Unit) {
        val size = paperSize()
        val c = paperColors
        val ink = LocalPaperInk.current ?: c.ink.srgb
        val shape = when (type) {
            ButtonType.PRIMARY -> Cut.A
            ButtonType.SECONDARY -> Cut.B
            ButtonType.DANGER -> Cut.C
            else -> Cut.D
        }.shape(size)
        // What the button is drawn as: a sheet and its outline, or its word alone
        val (sheet, line, word) = when (type) {
            ButtonType.PRIMARY -> Triple(c.navy.srgb, null, c.onNavy.srgb)
            ButtonType.SECONDARY -> Triple(c.sheet.srgb, c.ink.srgb, c.ink.srgb)
            ButtonType.DANGER -> Triple(c.accent.srgb, null, c.onAccent.srgb)
            else -> Triple(null, null, ink)
        }
        Box(
            // Filling (LocalButtonFill), the button takes the width it is given
            modifier = Modifier.let { if (LocalButtonFill.current) it.fillMaxWidth() else it }
                .heightIn(min = size.touch).widthIn(min = size.touch)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .let { m ->
                    sheet?.let { m.sheet(it, shape, if (enabled) c.shadow else Color.Transparent, size.dp(SHADOW_X), size.dp(SHADOW_Y), pressed) }
                        ?: m.pressedTint(pressed, c.ink.srgb, shape)
                }
                .let { m -> line?.let { m.outline(paperStroke(2f), it, shape) } ?: m }
                .padding(horizontal = if (sheet == null) size.dp(8f) else size.dp(16f)),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalPaperInk provides word) { content() }
        }
    }

    /**
     * An icon alone, a finger's target square, without a frame; in the header's bar, switched on
     * ([active]) or the main one, a round of the night blue cut by hand on its shadow, the icon in
     * cream. Pressed, a round lies flat, a bare icon darkens.
     */
    @Composable
    private fun IconSquare(icon: String, description: String?, type: ButtonType, enabled: Boolean, pressed: Boolean, active: Boolean) {
        val size = paperSize()
        val c = paperColors
        val round = (active || type == ButtonType.PRIMARY || LocalPaperInBar.current) && enabled
        val tint = when {
            !enabled -> c.dim.srgb.copy(alpha = DISABLED_ALPHA)
            round -> c.onNavy.srgb
            type == ButtonType.DANGER -> c.error.srgb
            else -> LocalPaperInk.current ?: c.ink.srgb
        }
        Box(modifier = Modifier.size(size.touch), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(size.dp(44f))
                    .let { if (round) it.paperSheet(c.navy.srgb, BLOB, pressed) else it.pressedTint(pressed, c.ink.srgb, BLOB) },
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, description, size.dp(22f), tint, shadow = !round) }
        }
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
        CompositionLocalProvider(LocalPaperInBar provides false) {
            Pressable(onClick = onClick) { pressed -> IconSquare(icon, null, ButtonType.DEFAULT, true, pressed, false) }
        }
    }

    /** A − or + on a cream sheet outlined in the ink, a finger's target. */
    @Composable
    private fun CounterButton(icon: String, cut: Cut, onClick: () -> Unit) {
        val size = paperSize()
        val c = paperColors
        val shape = cut.shape(size)
        Pressable(onClick = onClick) { pressed ->
            Box(
                modifier = Modifier.size(size.touch, size.dp(40f)).paperSheet(c.sheet.srgb, shape, pressed).outline(paperStroke(2f), c.ink.srgb, shape),
                contentAlignment = Alignment.Center
            ) { NamedIcon(icon, null, size.dp(20f), c.ink.srgb) }
        }
    }

    /** A Lucide icon by name, cut from paper, at [size], in [tint], on its shadow unless told not to be. */
    @Composable
    private fun NamedIcon(name: String, description: String?, size: Dp, tint: Color, shadow: Boolean = true) {
        val resource = requireNotNull(Icons.drawable(LocalContext.current, name)) { "No drawable for the icon $name" }
        CutIcon(resource, size, tint, if (shadow) paperColors.shadow else null, description)
    }

    /** A mark on an icon's corner: a round of [fill] cut by hand, tilted [tilt] degrees on its shadow, [content] in its middle. */
    @Composable
    private fun Mark(fill: Color, tilt: Float, content: @Composable () -> Unit) {
        val size = paperSize()
        Box(
            modifier = Modifier.size(size.dp(22f)).tilted(tilt).sheet(fill, BLOB, paperColors.shadow, size.dp(1f), size.dp(2f)),
            contentAlignment = Alignment.Center
        ) { content() }
    }

    /** Two bars of 3 dp, [width] wide, one over the other: a grip. */
    @Composable
    private fun Bars(width: Dp) {
        val size = paperSize()
        val ink = paperColors.ink.srgb
        val bar = paperStroke(3f)
        Column(verticalArrangement = Arrangement.spacedBy(size.dp(5f))) {
            repeat(2) { Box(modifier = Modifier.width(width).height(bar).background(ink, RoundedCornerShape(bar / 3))) }
        }
    }

    /** What is written in a field: a white sheet on its shadow outlined in 2 dp of [line]; a finger's target tall. */
    @Composable
    private fun Boxed(line: Color, pressed: Boolean = false, content: @Composable () -> Unit) {
        val size = paperSize()
        val shape = Cut.A.shape(size)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = size.touch)
                .paperSheet(paperColors.white.srgb, shape, pressed)
                .outline(paperStroke(2f), line, shape)
                .padding(horizontal = size.dp(12f), vertical = size.dp(10f)),
            contentAlignment = Alignment.CenterStart
        ) { content() }
    }

    /**
     * Three confetti [piece] wide, coral, mustard and teal, each falling in turn from above its
     * place, turning as it falls, then all again.
     */
    @Composable
    private fun Confetti(piece: Dp) {
        val c = paperColors
        val transition = rememberInfiniteTransition(label = "confetti")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = CONFETTI.toFloat() + 1f,
            animationSpec = infiniteRepeatable(tween(CONFETTI_STEP_MS * (CONFETTI + 1), easing = LinearEasing), RepeatMode.Restart),
            label = "phase"
        )
        val colours = listOf(c.accent.srgb, c.colour(TagColor.YELLOW).srgb, c.colour(TagColor.TEAL).srgb)
        val shadow = c.shadow
        val gap = piece
        Box(modifier = Modifier.size(piece * CONFETTI + gap * (CONFETTI - 1), piece * 3).drawBehind {
            val w = piece.toPx()
            val step = w + gap.toPx()
            repeat(CONFETTI) { i ->
                // Before its turn a piece waits above, out of sight; in its turn it falls; after, it lies
                val t = (phase - i).coerceIn(0f, 1f)
                if (phase < i) return@repeat
                val y = (this.size.height - w * 1.2f) * t
                val x = i * step
                rotate(degrees = (1f - t) * 90f + (i - 1) * 20f, pivot = Offset(x + w / 2, y + w * 0.6f)) {
                    drawRect(shadow, Offset(x + 1.5f * density, y + 1.5f * density), GeometrySize(w, w * 1.2f))
                    drawRect(colours[i], Offset(x, y), GeometrySize(w, w * 1.2f))
                }
            }
        })
    }

    /**
     * A window over the screen, falling in as a sheet laid on the table: a cream sheet, straight, on
     * its shadow, its content, its actions under it.
     */
    @Composable
    private fun DialogFrame(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
        app.treelune.core.ui.components.FloatingDialog(onDismissRequest = onDismiss, margin = spacing(Spacing.EDGE)) {
            FallIn {
                val size = paperSize()
                val c = paperColors
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .paperSheet(c.sheet.srgb, Cut.B.shape(size))
                        .padding(start = size.dp(16f), end = size.dp(16f), top = size.dp(16f), bottom = size.dp(12f)),
                    verticalArrangement = Arrangement.spacedBy(size.dp(16f))
                ) {
                    CompositionLocalProvider(LocalPaperInk provides null, LocalPaperStrong provides null, LocalPaperInTile provides false, LocalPaperInBar provides false) {
                        Column(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(size.dp(8f)), content = content)
                        app.treelune.core.ui.components.ButtonRow(align = Alignment.End, mainLast = true, content = actions)
                    }
                }
            }
        }
    }

    /** A number in the titles' figures on a cream sheet, between a + above and a − under, wrapping round at [modulo]. */
    @Composable
    private fun Counter(value: Int, modulo: Int, cut: Cut, onChange: (Int) -> Unit) {
        val size = paperSize()
        val c = paperColors
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(size.dp(8f))) {
            CounterButton("plus", Cut.A) { onChange((value + 1) % modulo) }
            Box(modifier = Modifier.paperSheet(c.sheet.srgb, cut.shape(size)).padding(horizontal = size.dp(12f), vertical = size.dp(8f))) {
                Line(String.format(Locale.ROOT, "%02d", value), size.number, c.ink.srgb)
            }
            CounterButton("minus", Cut.C) { onChange((value + modulo - 1) % modulo) }
        }
    }

    /** The toast shown, a night-blue sheet across the bottom of the screen. */
    @Composable
    private fun ToastHost(modifier: Modifier) {
        val shown = PaperToasts.current ?: return
        LaunchedEffect(shown.id) {
            delay(shown.millis)
            PaperToasts.dismiss(shown.id)
        }
        val size = paperSize()
        val c = paperColors
        Box(modifier = modifier.fillMaxWidth().padding(spacing(Spacing.EDGE))) {
            FallIn {
                Box(modifier = Modifier.fillMaxWidth().paperSheet(c.navy.srgb, Cut.C.shape(size)).padding(horizontal = size.dp(16f), vertical = size.dp(14f))) {
                    Line(shown.message, size.body, c.onNavy.srgb)
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
        val unit = with(LocalDensity.current) { paperSize().dp(1f).toPx() }
        return app.treelune.core.themes.TextureStyle(unitPx = unit, pixel = false, iconUnits = 16, iconGapUnits = 16)
    }

    /** A round cut by hand: a little oval and uneven (the mockup's 48 % 52 % 46 % 54 %). */
    private val BLOB: Shape = RoundedCornerShape(48, 52, 46, 54)

    /** The tilts of the pieces laid askew, in degrees: a group's title, the page's title, the waiting mark, a checkbox, the chosen tab, day and answer. */
    private const val SECTION_TILT = -1.5f
    private const val TITLE_TILT = -2f
    private const val WAIT_TILT = 8f
    private const val BOX_TILT = -4f
    private const val TAB_TILT = -1f
    private const val DAY_TILT = -6f
    private const val CHOSEN_TILT = -1.5f

    /** The coral ribbon over an interaction's card, in dp. */
    private const val RIBBON = 8f

    /** A round's side over the icon's room; the icon's side in it, over the same room. */
    private const val BADGE_SCALE = 1.33f
    private const val ICON_IN_BADGE = 0.72f

    /** The page icon's room, in dp, before its round. */
    private const val PAGE_ICON = 30f

    /** How much a disabled button keeps of itself. */
    private const val DISABLED_ALPHA = 0.5f

    /** Degrees between two colours the hue band computes. */
    private const val HUE_STRIP_STEP = 10

    private const val MAX_GRID_DP = 480
    private const val ASIDE_ALPHA = 0.4f
    private const val TOAST_SHORT_MS = 2000L
    private const val TOAST_LONG_MS = 3500L
    private const val CONFETTI = 3
    private const val CONFETTI_STEP_MS = 350
}

/** Whether what is drawn stands on a tile, cut from its own colour: an item's icon there is on a cream round. */
internal val LocalPaperInTile = compositionLocalOf { false }

/** Whether what is drawn stands in a header's bar: an icon alone there is a round of the night blue (the mockup). */
internal val LocalPaperInBar = compositionLocalOf { false }

/** The kraft: [ground], and its fibres at 115° in [fibre], faint, every 7 dp. */
private fun Modifier.kraft(ground: Color, fibre: Color): Modifier = drawBehind {
    drawRect(ground)
    val gap = 7f * density
    val width = 2f * density
    val tone = fibre.copy(alpha = FIBRE_ALPHA)
    // Lines at 115°: going up to the right; drawn across a span wide enough to cover every corner
    val slope = kotlin.math.tan(Math.toRadians(25.0)).toFloat()
    val run = size.height * slope
    var x = -run
    while (x < size.width + run) {
        drawLine(tone, Offset(x, size.height), Offset(x + run, 0f), strokeWidth = width)
        x += gap / kotlin.math.cos(Math.toRadians(25.0)).toFloat()
    }
}

private const val FIBRE_ALPHA = 0.09f

/** The toasts waiting, the oldest shown first; posted on the main thread. */
internal object PaperToasts {
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
