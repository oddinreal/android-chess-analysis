package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessSessionTest {
    @Test
    fun playsLegalMovesAndBranchesFromEarlierPosition() {
        val afterTwoMoves = ChessSession()
            .play("e2e4")
            .play("e7e5")

        assertEquals(2, afterTwoMoves.ply)
        assertEquals("e2e4", afterTwoMoves.moves.first())
        assertTrue(afterTwoMoves.legalMovesFrom("g1").contains("g1f3"))

        val branch = afterTwoMoves.jumpTo(1).play("c7c5")
        assertEquals(listOf("e2e4", "c7c5"), branch.moves)
        assertEquals(2, branch.ply)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIllegalMoves() {
        ChessSession().play("e2e5")
    }

    @Test
    fun derivesMoveSideAndNumberFromCustomFen() {
        val session = ChessSession(
            startFen = "4k3/8/8/8/8/8/8/4K3 b - - 0 17",
            moves = listOf("e8e7", "e1e2", "e7e6")
        )

        assertEquals(Side.BLACK, session.moverAt(0))
        assertEquals("17...", session.moveNotationAt(0))
        assertEquals(Side.WHITE, session.moverAt(1))
        assertEquals("18.", session.moveNotationAt(1))
        assertEquals("18...", session.moveNotationAt(2))
    }

    @Test
    fun handlesInvalidFullmoveNumberWithoutBreakingNotation() {
        val session = ChessSession(
            startFen = "4k3/8/8/8/8/8/8/4K3 w - - 0 invalid",
            moves = listOf("e1e2")
        )

        assertEquals("1.", session.moveNotationAt(0))
    }

    @Test
    fun exposesEveryLegalPromotionChoice() {
        val session = ChessSession(
            startFen = "4k3/P7/8/8/8/8/8/4K3 w - - 0 1"
        )

        assertEquals(
            setOf("a7a8q", "a7a8r", "a7a8b", "a7a8n"),
            session.legalMovesFrom("a7").filter { it.startsWith("a7a8") }.toSet()
        )
        assertEquals(
            "QUEEN",
            session.play("a7a8q").pieceAt("a8").name.substringAfter('_')
        )
    }

    @Test
    fun reportsCheckmateAndStalematePositions() {
        val checkmate = ChessSession(
            startFen = "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1"
        )
        val stalemate = ChessSession(
            startFen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1"
        )
        val check = ChessSession(
            startFen = "4k3/8/8/8/8/8/4R3/4K3 b - - 0 1"
        )
        val insufficientMaterial = ChessSession(
            startFen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1"
        )

        assertEquals(PositionStatus.CHECKMATE, checkmate.positionStatus())
        assertEquals(PositionStatus.STALEMATE, stalemate.positionStatus())
        assertEquals(PositionStatus.CHECK, check.positionStatus())
        assertEquals(PositionStatus.DRAW, insufficientMaterial.positionStatus())
    }

    @Test
    fun identifiesCapturesIncludingEnPassant() {
        val session = ChessSession().let {
            it.play("e2e4").play("a7a6").play("e4e5").play("d7d5")
        }

        assertTrue(session.captureTargetsFrom("e5").contains("d6"))
        assertFalse(session.captureTargetsFrom("e5").contains("d5"))
        val regularCapture = ChessSession().play("e2e4").play("d7d5")
        assertTrue(regularCapture.captureTargetsFrom("e4").contains("d5"))
    }

    @Test
    fun exportsGameAsImportablePgnWithCustomStartingFen() {
        val session = ChessSession(
            startFen = "4k3/8/8/8/8/8/8/4K3 w - - 0 1",
            moves = listOf("e1e2", "e8e7"),
            whiteName = "White Player",
            blackName = "Black Player"
        )

        val imported = GameImport.fromText("export.pgn", session.toPgn())
        assertEquals(session.startFen, imported.startFen)
        assertEquals(session.moves, imported.moves)
        assertEquals(session.whiteName, imported.whiteName)
    }

    @Test
    fun keepsPlayerNamesOnSinglePgnHeaderLine() {
        val pgn = ChessSession(whiteName = "Player\n[Result \"1-0\"]").toPgn()

        assertEquals(1, pgn.lines().count { it.startsWith("[Result ") })
        assertTrue(pgn.lines().any { it.startsWith("[White \"Player [Result") })
    }

    @Test
    fun providesReadableSanForPlayedAndSuggestedMoves() {
        val session = ChessSession().play("e2e4").play("e7e5")

        assertEquals(listOf("e4", "e5"), session.sanMoves())
        assertEquals("Nf3", session.sanForMoveAt(2, "g1f3"))
        assertEquals(listOf("Nf3", "Nc6"), session.sanLineAt(2, listOf("g1f3", "b8c6")))
    }
}
