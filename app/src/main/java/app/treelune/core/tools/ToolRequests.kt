package app.treelune.core.tools

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Asks for a tool opened from any screen, where its tool type opens (OpenAt): a chart's hole leads
 * to the entry to correct, a schema's box to a row of a view. The main screen, which holds the
 * zones, opens the tool's zone and the tool in it, as a notification does.
 */
object ToolRequests {
    private val _requests = MutableSharedFlow<Pair<String, OpenAt?>>(extraBufferCapacity = 1)
    val requests: SharedFlow<Pair<String, OpenAt?>> = _requests.asSharedFlow()

    /** Opens the tool [toolInstanceId], on [entryId] when given. */
    fun open(toolInstanceId: String, entryId: String? = null) = open(toolInstanceId, entryId?.let { OpenAt.Entry(it) })

    /** Opens the tool [toolInstanceId] at [at], its screen as usual when null. */
    fun open(toolInstanceId: String, at: OpenAt?) {
        _requests.tryEmit(toolInstanceId to at)
    }
}
