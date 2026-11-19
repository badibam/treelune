package app.treelune.core.look

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A zone's tile pieces (TilePieces): stored as a JSON array in their order, none as null. */
class TilePiecesTest {

    @Test
    fun theList_keepsItsOrderAndSummaries_noneIsNull() {
        val pieces = listOf(TilePieces.Piece("b", true), TilePieces.Piece("a", false))
        assertEquals(pieces, TilePieces.read(TilePieces.write(pieces)))
        assertNull(TilePieces.write(emptyList()))
        assertEquals(emptyList<TilePieces.Piece>(), TilePieces.read(null))
    }

    @Test
    fun aCommandsList_isReadAsStored() {
        assertEquals(listOf(TilePieces.Piece("t", true)), TilePieces.of(JSONArray("""[{"id":"t","summary":true}]""")))
        assertEquals(emptyList<TilePieces.Piece>(), TilePieces.of(org.json.JSONObject.NULL))
    }
}
