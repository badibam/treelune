package app.treelune.core.ai.processing

import app.treelune.core.selection.EntryPeriod
import app.treelune.core.utils.DateTimeConverter
import app.treelune.core.utils.JsonUtils
import java.time.ZoneId

/**
 * A period as the model writes it, `{"start", "end"}`: a fixed bound in ISO 8601
 * (`"2026-10-30T18:11:00+01:00"`, an offset-less one in the app's zone), a relative one as it is
 * stored. Inside, a fixed bound is milliseconds (TimeResolver), so it is converted here, on the
 * way in, for every command that takes a period: TOOL_DATA, EXPORT_DATA, ROUTINE_RUNS, HISTORY,
 * a variable's terms.
 */
object ModelPeriod {

    /**
     * [written] with its fixed bounds in milliseconds, as a period is stored.
     *
     * @throws IllegalArgumentException on a bound that does not read as ISO 8601
     */
    fun stored(written: Map<*, *>, zone: ZoneId): Map<String, Any?> = written.entries.associate { (edge, bound) ->
        edge.toString() to ((bound as? String)?.let { DateTimeConverter.isoToTimestamp(it, zone) } ?: bound)
    }

    /**
     * [written] read as a period, its fixed bounds in milliseconds.
     *
     * @throws IllegalArgumentException on a bound that does not read
     */
    fun read(written: Map<*, *>, zone: ZoneId, text: (String) -> String): EntryPeriod =
        EntryPeriod.fromJson(JsonUtils.toJSONObject(stored(written, zone)), text)
}
