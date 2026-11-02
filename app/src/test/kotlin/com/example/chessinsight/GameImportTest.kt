package com.example.chessinsight

import org.junit.Assert.assertEquals
import org.junit.Test

class GameImportTest {
    @Test
    fun importsPgnMovesAsUci() {
        val session = GameImport.fromText(
            "game.pgn",
            """
                [Event "Test"]
                [Site "?"]
                [Date "2026.01.01"]
                [Round "1"]
                [White "White Player"]
                [Black "Black Player"]
                [Result "*"]

                1. e4 e5 2. Nf3 Nc6 *
            """.trimIndent()
        )

        assertEquals(listOf("e2e4", "e7e5", "g1f3", "b8c6"), session.moves)
        assertEquals("White Player", session.whiteName)
        assertEquals(4, session.ply)
    }

    @Test
    fun importsFenPosition() {
        val session = GameImport.fromText(
            "position.fen",
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
        )

        assertEquals("4k3/8/8/8/8/8/8/4K3 w - - 0 1", session.startFen)
        assertEquals(0, session.moves.size)
    }

    @Test
    fun inputDefaultsToPgnAndAcceptsMovesWithoutHeaders() {
        val session = GameImport.fromText("input.pgn", "1. e4 e5 2. Nf3 *")

        assertEquals(listOf("e2e4", "e7e5", "g1f3"), session.moves)
    }

    @Test
    fun inputAutomaticallyAcceptsFen() {
        val fen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
        val session = GameImport.fromText("input.pgn", fen)

        assertEquals(fen, session.startFen)
        assertEquals(0, session.moves.size)
    }

    @Test
    fun fenScannerHandlesShortOcrTextAndFindsPositionsAcrossWhitespace() {
        assertEquals(null, GameImport.findFen("one two three"))
        assertEquals(
            "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
            GameImport.findFen("Position:\n4k3/8/8/8/8/8/8/4K3 w - - 0 1")
        )
    }

    @Test
    fun malformedPastedFenReportsFenValidationInsteadOfPgnFailure() {
        try {
            GameImport.fromText("input.pgn", "4k3/8/8 w - - 0")
        } catch (exception: IllegalArgumentException) {
            assertEquals("A FEN position must contain six fields.", exception.message)
            return
        }
        throw AssertionError("Malformed FEN should be rejected.")
    }
}
