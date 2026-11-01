package app.treelune.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.treelune.core.coordinator.Coordinator
import app.treelune.core.coordinator.isSuccess

/**
 * What reads a thing about to be deleted, in words, for its confirmation to say
 * (docs/design/dependents.md): read through `dependents.of` once [target] is given; null while it
 * reads, when nothing depends, or when it could not be read (the deletion's own answer then holds).
 *
 * @param target The thing, as `dependents.of` takes it; null for none yet
 */
@Composable
fun rememberDependents(target: Map<String, Any?>?): String? {
    val context = LocalContext.current
    var said by remember(target) { mutableStateOf<String?>(null) }
    LaunchedEffect(target) {
        target ?: return@LaunchedEffect
        val result = Coordinator(context).processUserAction("dependents.of", mapOf("target" to target))
        said = (result.data?.get("said") as? String)?.takeIf { result.isSuccess && it.isNotEmpty() }
    }
    return said
}
