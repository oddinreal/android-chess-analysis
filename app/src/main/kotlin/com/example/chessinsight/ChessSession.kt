package com.example.chessinsight

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import com.github.bhlangonijr.chesslib.move.Move
import com.github.bhlangonijr.chesslib.move.MoveList

enum class PositionStatus {
    ACTIVE,
    CHECK,
    CHECKMATE,
    STALEMATE,
    DRAW
}

data class ChessSession(
    val startFen: String = STARTING_FEN,
    val moves: List<String> = emptyList(),
    val ply: Int = 0,
    val whiteName: String = "White",
    val blackName: String = "Black"
) {
    fun boardAt(targetPly: Int = ply): Board {
        require(targetPly in 0..moves.size)
        val board = Board()
        board.loadFromFen(startFen)
        moves.take(targetPly).forEach { uci ->
            val move = Move(uci, board.sideToMove)
            check(board.doMove(move, true)) { "Illegal move in imported game: $uci" }
        }
        return board
    }

    fun fenAt(targetPly: Int = ply): String = boardAt(targetPly).fen

    fun moverAt(moveIndex: Int): Side {
        require(moveIndex in moves.indices)
        val startingSide = startingSide()
        return if (moveIndex % 2 == 0) startingSide else opposite(startingSide)
    }

    fun moveNumberAt(moveIndex: Int): Int {
        require(moveIndex in moves.indices)
        val initialMoveNumber = startFen.trim().split(Regex("\\s+"))
            .getOrNull(5)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val blackStarts = startingSide() == Side.BLACK
        return initialMoveNumber + (moveIndex + if (blackStarts) 1 else 0) / 2
    }

    fun moveNotationAt(moveIndex: Int): String =
        if (moverAt(moveIndex) == Side.WHITE) "${moveNumberAt(moveIndex)}."
        else "${moveNumberAt(moveIndex)}..."

    fun positionStatus(targetPly: Int = ply): PositionStatus {
        val board = boardAt(targetPly)
        return when {
            board.isMated -> PositionStatus.CHECKMATE
            board.isStaleMate -> PositionStatus.STALEMATE
            board.isDraw -> PositionStatus.DRAW
            board.isKingAttacked -> PositionStatus.CHECK
            else -> PositionStatus.ACTIVE
        }
    }

    fun toPgn(): String {
        val moveList = MoveList(startFen)
        val board = boardAt(0)
        moves.forEach { uci ->
            val move = Move(uci, board.sideToMove)
            check(board.doMove(move, true)) { "Illegal move in imported game: $uci" }
            moveList.add(move)
        }
        fun header(value: String) = value
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val headers = buildList {
            add("[Event \"Chess Insight study\"]")
            add("[White \"${header(whiteName)}\"]")
            add("[Black \"${header(blackName)}\"]")
            if (startFen != STARTING_FEN) {
                add("[SetUp \"1\"]")
                add("[FEN \"$startFen\"]")
            }
            add("[Result \"*\"]")
        }
        return headers.joinToString("\n") + "\n\n" + moveList.toSanWithMoveNumbers() + " *"
    }

    fun sanMoves(): List<String> {
        val moveList = MoveList(startFen)
        val board = boardAt(0)
        moves.forEach { uci ->
            val move = Move(uci, board.sideToMove)
            check(board.doMove(move, true)) { "Illegal move in imported game: $uci" }
            moveList.add(move)
        }
        return moveList.toSanArray().toList()
    }

    fun sanForMoveAt(targetPly: Int, uci: String): String {
        val board = boardAt(targetPly)
        val move = Move(uci, board.sideToMove)
        require(board.legalMoves().contains(move)) { "That is not a legal move." }
        return MoveList(board.fen).apply { add(move) }.toSanArray().single()
    }

    fun sanLineAt(targetPly: Int, uciMoves: List<String>): List<String> {
        val board = boardAt(targetPly)
        val line = MoveList(board.fen)
        uciMoves.forEach { uci ->
            val move = Move(uci, board.sideToMove)
            require(board.legalMoves().contains(move)) { "Illegal engine continuation move: $uci" }
            check(board.doMove(move, true)) { "Could not apply engine continuation move: $uci" }
            line.add(move)
        }
        return line.toSanArray().toList()
    }

    fun captureTargetsFrom(squareName: String): Set<String> {
        val board = boardAt()
        val origin = Square.valueOf(squareName.uppercase())
        val enPassantTarget = board.enPassant
        return board.legalMoves()
            .filter { it.from == origin }
            .filter { board.getPiece(it.to) != Piece.NONE || it.to == enPassantTarget }
            .map { it.to.toString().lowercase() }
            .toSet()
    }

    fun legalMovesFrom(squareName: String): List<String> {
        val board = boardAt()
        val origin = Square.valueOf(squareName.uppercase())
        return board.legalMoves()
            .filter { it.from == origin }
            .map(Move::toString)
    }

    fun play(uci: String): ChessSession {
        val board = boardAt()
        val move = Move(uci, board.sideToMove)
        require(board.legalMoves().contains(move)) { "That is not a legal move." }
        check(board.doMove(move, true)) { "The move could not be applied." }
        return copy(moves = moves.take(ply) + uci.lowercase(), ply = ply + 1)
    }

    fun jumpTo(targetPly: Int): ChessSession = copy(ply = targetPly.coerceIn(0, moves.size))

    fun pieceAt(squareName: String): Piece {
        val board = boardAt()
        return board.getPiece(Square.valueOf(squareName.uppercase()))
    }

    private fun startingSide(): Side =
        if (startFen.trim().split(Regex("\\s+")).getOrNull(1) == "b") Side.BLACK else Side.WHITE

    private fun opposite(side: Side): Side =
        if (side == Side.WHITE) Side.BLACK else Side.WHITE

    companion object {
        const val STARTING_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }
}
