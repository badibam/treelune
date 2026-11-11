package app.treelune.tools.flashcards.ui

import app.treelune.core.utils.JsonUtils
import app.treelune.tools.flashcards.FlashcardsDeck
import app.treelune.tools.flashcards.FlashcardsDue.CardKey
import app.treelune.tools.flashcards.FlashcardsSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * A session as its screen keeps it, saved whole across a rotation: the session, the faces of its
 * cards as flashcards.due gave them at its launch, the card shown and whether its back is turned.
 *
 * @property total How many cards the session was launched with
 * @property fresh The cards launched as new, never reviewed before the session: marked « New » when
 *   shown, and counted apart at its end
 */
data class SessionState(
    val session: FlashcardsSession,
    val faces: Map<CardKey, Faces>,
    val shown: CardKey?,
    val turned: Boolean,
    val total: Int,
    val fresh: Set<CardKey>
) {
    /** A card's two faces: the values of its fields, by path, in their order; the first is the question or the answer. */
    data class Faces(val front: List<Pair<String, Any?>>, val back: List<Pair<String, Any?>>)

    /** The cards passed so far — rated Hard, Good or Easy —, which no longer wait in the session. */
    val passed: Set<CardKey> get() = faces.keys - session.waiting.map { it.card }.toSet()

    fun toJson(): JSONObject = JSONObject()
        .put("session", session.toJson())
        .put("faces", JSONArray(faces.map { (key, f) ->
            JSONObject().put("line", key.line).put("direction", key.direction).put("front", side(f.front)).put("back", side(f.back))
        }))
        .put("shown", shown?.let { JSONObject().put("line", it.line).put("direction", it.direction) } ?: JSONObject.NULL)
        .put("turned", turned)
        .put("total", total)
        .put("fresh", JSONArray(fresh.map { JSONObject().put("line", it.line).put("direction", it.direction) }))

    companion object {
        private fun side(values: List<Pair<String, Any?>>) = JSONArray(values.map { (path, value) ->
            JsonUtils.toJSONObject(mapOf("field" to path, "value" to value))
        })

        private fun sideOf(array: JSONArray): List<Pair<String, Any?>> =
            FlashcardsDeck.objects(array).map { it.getString("field") to JsonUtils.toValue(it.opt("value")) }

        /** A session launched over the cards flashcards.due answered. */
        fun started(data: Map<String, Any?>?): SessionState {
            val cards = (data?.get("cards") as? List<*>).orEmpty().map { it as Map<*, *> }
            fun valuesOf(side: Any?) = (side as List<*>).map { it as Map<*, *> }.map { it["field"] as String to it["value"] }
            val faces = cards.associate { card ->
                CardKey(card["source_entry"] as String, card["direction"] as String) to Faces(valuesOf(card["front"]), valuesOf(card["back"]))
            }
            // flashcards.due says of each card whether it is new (FlashcardsDue.Kind, lowercased)
            val fresh = cards.filter { it["kind"] == "new" }.map { CardKey(it["source_entry"] as String, it["direction"] as String) }.toSet()
            val session = FlashcardsSession.of(faces.keys.toList())
            return SessionState(session, faces, session.next(System.currentTimeMillis()), false, faces.size, fresh)
        }

        fun fromJson(json: JSONObject): SessionState = SessionState(
            session = FlashcardsSession.fromJson(json.getJSONObject("session")),
            faces = FlashcardsDeck.objects(json.getJSONArray("faces")).associate {
                CardKey(it.getString("line"), it.getString("direction")) to Faces(sideOf(it.getJSONArray("front")), sideOf(it.getJSONArray("back")))
            },
            shown = json.optJSONObject("shown")?.let { CardKey(it.getString("line"), it.getString("direction")) },
            turned = json.getBoolean("turned"),
            total = json.getInt("total"),
            fresh = FlashcardsDeck.objects(json.getJSONArray("fresh")).map { CardKey(it.getString("line"), it.getString("direction")) }.toSet()
        )
    }
}
