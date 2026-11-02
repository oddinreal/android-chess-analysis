package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Side

enum class MoveLabel(val title: String) {
    BRILLIANT("Brilliant"),
    BEST("Best"),
    EXCELLENT("Excellent"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
    BLUNDER("Blunder")
}

data class MoveReview(
    val ply: Int,
    val move: String,
    val playedMove: String,
    val label: MoveLabel,
    val centipawnLoss: Int,
    val bestMove: String,
    val evaluationAfterWhite: Int
)

data class GameReview(
    val moves: List<MoveReview>,
    val evaluationsWhite: List<Int>,
    val whiteEstimatedElo: Int?,
    val blackEstimatedElo: Int?,
    val depth: Int
)

class GameAnalyzer(private val engine: StockfishEngine) {
    suspend fun analyze(
        session: ChessSession,
        depth: Int,
        onProgress: suspend (completed: Int, total: Int) -> Unit
    ): GameReview {
        val reviews = mutableListOf<MoveReview>()
        var before = engine.analyze(session.fenAt(0), depth)
        val evaluationsWhite = mutableListOf(
            whitePerspective(before.scoreCp, session.boardAt(0).sideToMove)
        )
        session.moves.forEachIndexed { index, played ->
            val afterFen = session.fenAt(index + 1)
            val after = engine.analyze(afterFen, depth)
            val afterSideToMove = session.boardAt(index + 1).sideToMove
            val evaluationAfterWhite = whitePerspective(after.scoreCp, afterSideToMove)
            evaluationsWhite += evaluationAfterWhite
            val loss = (before.scoreCp + after.scoreCp).coerceAtLeast(0)
            val mover = if (session.boardAt(index).sideToMove == Side.WHITE) {
                BoardSide.WHITE
            } else {
                BoardSide.BLACK
            }
            val afterForMover = -after.scoreCp
            val improvement = afterForMover - before.scoreCp
            val label = classify(
                played = played,
                best = before.bestMove,
                loss = loss,
                improvement = improvement
            )
            reviews += MoveReview(
                ply = index + 1,
                move = if (mover == BoardSide.WHITE) "${index / 2 + 1}." else "${index / 2 + 1}...",
                playedMove = played,
                label = label,
                centipawnLoss = loss,
                bestMove = before.bestMove,
                evaluationAfterWhite = evaluationAfterWhite
            )
            onProgress(index + 1, session.moves.size)
            before = after
        }

        return GameReview(
            moves = reviews,
            evaluationsWhite = evaluationsWhite,
            whiteEstimatedElo = EloEstimator.estimate(
                reviews.filter { it.ply % 2 == 1 }.map(MoveReview::centipawnLoss)
            ),
            blackEstimatedElo = EloEstimator.estimate(
                reviews.filter { it.ply % 2 == 0 }.map(MoveReview::centipawnLoss)
            ),
            depth = depth
        )
    }

    private fun whitePerspective(scoreCp: Int, sideToMove: Side): Int =
        if (sideToMove == Side.WHITE) scoreCp else -scoreCp

    private fun classify(played: String, best: String, loss: Int, improvement: Int): MoveLabel =
        when {
            played.equals(best, ignoreCase = true) && improvement >= 150 -> MoveLabel.BRILLIANT
            played.equals(best, ignoreCase = true) && loss <= 10 -> MoveLabel.BEST
            loss <= 25 -> MoveLabel.EXCELLENT
            loss <= 60 -> MoveLabel.GOOD
            loss <= 120 -> MoveLabel.INACCURACY
            loss <= 250 -> MoveLabel.MISTAKE
            else -> MoveLabel.BLUNDER
        }

    private enum class BoardSide { WHITE, BLACK }
}
