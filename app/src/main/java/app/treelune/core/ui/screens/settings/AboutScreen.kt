package app.treelune.core.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.ButtonAction
import app.treelune.core.ui.ButtonType
import app.treelune.core.ui.CardType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * What the app is, at Settings › About: its version as a bug report names it (the name and the
 * build), its licence, and the ways to its site, in the app's language (`about_site_url`), and its source code, opened in the browser.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = UI.Space.L),
        verticalArrangement = Arrangement.spacedBy(UI.Space.L)
    ) {
        UI.PageHeader(title = s.shared("settings_about"), leftButton = ButtonAction.BACK, onLeftClick = onBack)
        UI.Card(type = CardType.DEFAULT) {
            Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
                UI.Text(s.shared("about_version").format(app.treelune.BuildConfig.VERSION_NAME, app.treelune.BuildConfig.VERSION_CODE), TextType.HEADING, selectable = true)
                UI.Text(s.shared("about_licence"), TextType.BODY)
            }
        }
        UI.FormActions {
            UI.Button(type = ButtonType.DEFAULT, onClick = { open(s.shared("about_site_url")) }) { UI.Text(s.shared("about_site"), TextType.BODY) }
            UI.Button(type = ButtonType.DEFAULT, onClick = { open(SOURCE) }) { UI.Text(s.shared("about_source"), TextType.BODY) }
        }
    }
}

/** Its source code, which F-Droid builds the published app from. */
private const val SOURCE = "https://github.com/badibam/treelune"
