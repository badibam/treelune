package app.treelune.core.ui.components

import app.treelune.core.themes.IconColor
import app.treelune.core.themes.TagColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.treelune.core.icons.IconIndex
import app.treelune.core.icons.Icons
import app.treelune.core.ui.*
import app.treelune.core.strings.Strings
import app.treelune.core.strings.StringsContext

/** Icons shown for one search across all categories; a category shows all of its own. */
private const val SEARCH_RESULTS_SHOWN = 60

/** The icon whose cell sets the width of every cell of the picker's grids: a name of eight letters, longer ones wrapping. */
private const val ICON_SAMPLE = "calendar"

/**
 * An icon's colour (docs/reference.md, « Icons »): none, then each of IconColor.NAMES, every
 * choice a small tile of the thing as the theme draws it, its icon in that colour and its
 * [texture] on it, the colour's name under it, the chosen one marked.
 *
 * @param current The stored colour name; null for none
 * @param iconName The thing's icon; blank while it has none
 * @param texture The thing's stored texture; null for none
 * @param onChange The name chosen, null for none
 */
@Composable
fun IconColorSelector(current: String?, iconName: String, texture: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    val motif = app.treelune.core.themes.Texture.of(texture)
    ChoiceGrid(listOf<String?>(null) + IconColor.NAMES, sample = null) { name ->
        AppearanceChoice(iconName, IconColor.of(name), motif, colorName(name, s), current == name) { onChange(name) }
    }
}

/** The name of an icon's colour as the screen says it; null for none. */
private fun colorName(name: String?, s: app.treelune.core.strings.StringsContext): String =
    s.shared(name?.let { "tag_color_${it.lowercase()}" } ?: "icon_color_none")

/**
 * The icon's colour of a zone or a tool chosen on a page of its config form
 * (SettingPageEditor): its line shows the icon in the colour, the page the IconColorSelector.
 *
 * @param iconName The thing's icon; blank while it has none
 * @param texture The thing's stored texture; null for none
 */
fun iconColorPage(label: String, iconName: String, texture: String?) = object : app.treelune.core.fields.settings.SettingPageEditor {
    override val label = label

    @Composable
    override fun Summary(value: Any?) {
        val context = LocalContext.current
        val name = value as? String
        AppearanceSummary(iconName, IconColor.of(name), app.treelune.core.themes.Texture.of(texture), colorName(name, remember { Strings.`for`(context = context) }))
    }

    @Composable
    override fun Edit(value: Any?, onChange: (Any?) -> Unit) {
        IconColorSelector(current = value as? String, iconName = iconName, texture = texture, onChange = { onChange(it) })
    }
}

/**
 * Reusable icon selector: the current icon and a button opening the picker.
 *
 * The picker offers, in order: the [suggested] icons; a search on names and tags; and, when
 * nothing is searched, Lucide's categories, each opening its grid, where the search then
 * applies within the category. The search is the one the AI's ICONS command runs, so the same
 * words find the same icons on both sides. Tags are Lucide's, in English.
 *
 * @param current Currently selected icon
 * @param color Its colour, which it is shown in; null for a neutral icon
 * @param suggested Icons offered first: the tooltype's, or a starting set for a zone
 * @param onChange Callback called when an icon is selected
 * @param onClear Takes the chosen icon away, offered by a button while there is one to take away:
 *   what is left depends on the thing (none for a zone, its type's for a tool, the default one for
 *   an identity); null offers no button
 */
@Composable
fun IconSelector(
    current: String,
    color: TagColor? = null,
    suggested: List<String> = emptyList(),
    onChange: (String) -> Unit,
    onClear: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    val head: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UI.Space.M)) {
            UI.Text(s.shared("tools_config_label_icon"), TextType.LABEL)
            UI.ItemIcon(iconName = current, color = color, size = 32.dp)
        }
    }
    val buttons: @Composable () -> Unit = {
        UI.ActionButton(
            action = ButtonAction.SELECT,
            onClick = { showDialog = true }
        )
        if (onClear != null) UI.Button(type = ButtonType.DEFAULT, onClick = onClear) {
            UI.Text(s.shared("action_clear"), TextType.LABEL)
        }
    }
    // The label, the icon and the buttons on one line when they hold; else the buttons under them,
    // over the whole width, side by side or one under the other (ButtonRow): never a word cut
    val space = UI.Space.M
    androidx.compose.ui.layout.SubcomposeLayout(Modifier.fillMaxWidth()) { constraints ->
        val gap = space.roundToPx()
        val free = androidx.compose.ui.unit.Constraints(maxHeight = constraints.maxHeight)
        val headPlaced = subcompose("head", head).single().measure(free)
        val natural = subcompose("natural", buttons).map { it.measure(free) }
        val inline = headPlaced.width + gap + natural.sumOf { it.width } + gap * (natural.size - 1).coerceAtLeast(0) <= constraints.maxWidth
        val rowWidth = if (inline) constraints.maxWidth - headPlaced.width - gap else constraints.maxWidth
        val row = subcompose("row") { ButtonRow(content = buttons) }.single()
            .measure(androidx.compose.ui.unit.Constraints(maxWidth = rowWidth, maxHeight = constraints.maxHeight))
        if (inline) {
            val height = maxOf(headPlaced.height, row.height)
            layout(constraints.maxWidth, height) {
                headPlaced.place(0, (height - headPlaced.height) / 2)
                row.place(headPlaced.width + gap, (height - row.height) / 2)
            }
        } else {
            layout(constraints.maxWidth, headPlaced.height + gap + row.height) {
                headPlaced.place(0, 0)
                row.place(0, headPlaced.height + gap)
            }
        }
    }

    if (showDialog) {
        IconPickerDialog(
            current = current,
            suggested = suggested,
            s = s,
            onPick = {
                onChange(it)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun IconPickerDialog(
    current: String,
    suggested: List<String>,
    s: StringsContext,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val index = remember { Icons.index(context) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val words = query.split(' ', ',').filter { it.isNotBlank() }
    val openCategory = category

    UI.Dialog(
        type = DialogType.SELECTION,
        onConfirm = {},
        onCancel = onDismiss
    ) {
        // A category or a search can hold hundreds of icons: the dialog body scrolls
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UI.Space.S)
        ) {
            UI.Text(s.shared("tools_config_dialog_choose_icon"), TextType.SUBTITLE)

            UI.FormField(
                label = s.shared("tools_config_dialog_search"),
                value = query,
                onChange = { query = it },
                required = false
            )

            when {
                openCategory != null -> {
                    UI.ActionButton(
                        action = ButtonAction.BACK,
                        onClick = { category = null }
                    )
                    UI.Text(categoryTitle(s, openCategory), TextType.LABEL)
                    val result = index.search(words, listOf(openCategory), Int.MAX_VALUE)
                    IconGrid(result.matches.map { it.name }, current, onPick, s)
                }

                words.isNotEmpty() -> {
                    val result = index.search(words, emptyList(), SEARCH_RESULTS_SHOWN)
                    UI.Text(
                        if (result.truncated) s.shared("tools_config_dialog_results_truncated").format(result.matches.size, result.total)
                        else s.shared("tools_config_dialog_results").format(result.total),
                        TextType.CAPTION
                    )
                    IconGrid(result.matches.map { it.name }, current, onPick, s)
                }

                else -> {
                    val offered = suggested.mapNotNull { index.resolve(it) }.distinct()
                    if (offered.isNotEmpty()) {
                        UI.Text(s.shared("tools_config_dialog_suggested_icons"), TextType.LABEL)
                        IconGrid(offered, current, onPick, s)
                    }
                    UI.Text(s.shared("tools_config_dialog_categories"), TextType.LABEL)
                    CategoryList(index.categories, s) { category = it }
                }
            }
        }
    }
}

/** A category's title from the strings system: Lucide's ids, translated like any other text. */
private fun categoryTitle(s: StringsContext, id: String): String =
    s.shared("icon_category_${id.replace('-', '_')}")

@Composable
private fun CategoryList(
    categories: List<IconIndex.Category>,
    s: StringsContext,
    onOpen: (String) -> Unit
) {
    categories.forEach { category ->
        UI.Button(
            type = ButtonType.DEFAULT,
            onClick = { onOpen(category.id) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(UI.Space.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UI.Space.M)
            ) {
                UI.Icon(iconName = category.icon, size = 24.dp)
                Box(modifier = Modifier.weight(1f)) {
                    UI.Text(categoryTitle(s, category.id), TextType.BODY)
                }
                UI.Text(category.count.toString(), TextType.CAPTION)
            }
        }
    }
}

@Composable
private fun IconGrid(
    names: List<String>,
    current: String,
    onPick: (String) -> Unit,
    s: StringsContext
) {
    if (names.isEmpty()) {
        UI.Text(s.shared("tools_config_dialog_no_result"), TextType.CAPTION)
        return
    }
    // As many icons on a row as hold with a name of eight letters (ChoiceGrid)
    ChoiceGrid(names, sample = ICON_SAMPLE) { name ->
        UI.Button(
            type = if (current == name) ButtonType.PRIMARY else ButtonType.DEFAULT,
            // The smallest padding a text button has: the name needs the width
            size = Size.S,
            onClick = { onPick(name) }
        ) {
            Column(
                modifier = Modifier.heightIn(min = 64.dp).padding(UI.Space.XS),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                UI.Icon(iconName = name, size = 28.dp)
                UI.Text(
                    text = name,
                    type = TextType.CAPTION,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
