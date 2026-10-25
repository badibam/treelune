package app.treelune.core.ai.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.ai.budget.Budget
import app.treelune.core.strings.Strings
import app.treelune.core.ui.CardType
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI

/**
 * Under the AI limits (docs/AI.md, « Daily budget »): what the app's day has spent against its
 * budget, and the providers configured whose model has no price, which the budget cannot count.
 */
@Composable
fun BudgetCard() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var unpriced by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { unpriced = Budget.unpriced(context) }
    UI.Card(type = CardType.DEFAULT) {
        Column(modifier = Modifier.fillMaxWidth().padding(UI.Space.L), verticalArrangement = Arrangement.spacedBy(UI.Space.S)) {
            BudgetLine()
            if (unpriced.isNotEmpty()) UI.Text(text = s.shared("budget_unpriced").format(unpriced.joinToString(", ")), type = TextType.WARNING)
        }
    }
}

/**
 * What the app's day has spent, against its budget when one is set: « Today: 0.42 $ of 2.00 $ »,
 * in the theme's warning past its alert; « ≥ » when some calls are of unknown cost.
 */
@Composable
fun BudgetLine() {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    var today by remember { mutableStateOf<Budget.Today?>(null) }
    LaunchedEffect(Unit) { today = Budget.today(context) }
    val shown = today ?: return
    val spent = Budget.money(shown.spent).let { if (shown.unknown > 0) s.shared("ai_cost_at_least").format(it) else it }
    val text = shown.budget?.let { s.shared("budget_today_of").format(spent, Budget.money(it)) } ?: s.shared("budget_today").format(spent)
    UI.Text(text = text, type = if (shown.alerted || shown.reached) TextType.WARNING else TextType.BODY)
}
