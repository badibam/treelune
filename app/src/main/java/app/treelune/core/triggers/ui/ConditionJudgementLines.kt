package app.treelune.core.triggers.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.strings.Strings
import app.treelune.core.ui.TextType
import app.treelune.core.ui.UI
import app.treelune.core.utils.FormatUtils

/**
 * The last judgement of a condition a trigger names (ConditionTriggers.judgements): its place in
 * the list of triggers ([index], from 0), its [verdict] (null unknown, or not judged yet when
 * [judgedAt] is null too), and why it fails, [failure].
 */
data class ConditionJudgementLine(val index: Int, val verdict: Boolean?, val judgedAt: Long?, val failure: String?) {
    companion object {
        /** The lines of a result (`condition_judgements`); none when it holds none. */
        fun list(value: Any?): List<ConditionJudgementLine> = (value as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().map {
            ConditionJudgementLine(
                index = (it["index"] as Number).toInt(),
                verdict = it["verdict"] as Boolean?,
                judgedAt = (it["judged_at"] as? Number)?.toLong(),
                failure = it["failure"] as String?
            )
        }
    }
}

/** One line per condition its triggers name: "Trigger 2: true, judged 3 min ago", its failure under it. */
@Composable
fun ConditionJudgementLines(lines: List<ConditionJudgementLine>) {
    val context = LocalContext.current
    val s = remember { Strings.`for`(context = context) }
    for (line in lines) {
        val verdict = when {
            line.judgedAt == null -> s.shared("trigger_condition_not_judged")
            line.verdict == true -> s.shared("trigger_condition_true")
            line.verdict == false -> s.shared("trigger_condition_false")
            else -> s.shared("trigger_condition_unknown")
        }
        val judged = line.judgedAt?.let { s.shared("trigger_condition_judged_at").format(FormatUtils.formatRelativeTime(it, context)) }
        UI.Text(s.shared("trigger_condition_line").format(line.index + 1, listOfNotNull(verdict, judged).joinToString(", ")), TextType.CAPTION)
        line.failure?.let { UI.Text(it, TextType.ERROR) }
    }
}
