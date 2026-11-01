package com.example.chessinsight

import android.content.Context
import android.net.Uri
import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.pgn.PgnHolder
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object GameImport {
    suspend fun fromUri(context: Context, uri: Uri, displayName: String): ChessSession {
        return if (displayName.substringAfterLast('.', "").equals("png", ignoreCase = true)) {
            fromImage(context, uri)
        } else {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("Could not read the selected file.")
            fromText(displayName, content)
        }
    }

    fun fromText(displayName: String, content: String): ChessSession {
        val text = content.trim()
        require(text.isNotEmpty()) { "Paste PGN moves or a FEN position first." }

        if (displayName.endsWith(".fen", ignoreCase = true)) {
            val board = parseFen(text)
            return ChessSession(startFen = board.fen)
        }

        parseFenOrNull(text)?.let { board ->
            return ChessSession(startFen = board.fen)
        }

        val holder = PgnHolder("import.pgn")
        val hasHeaders = Regex("""(?m)^\s*\[[A-Za-z][A-Za-z0-9_]*\s+""").containsMatchIn(text)
        val pgn = if (hasHeaders) text else {
            "[Event \"Imported game\"]\n[Site \"?\"]\n[Date \"????.??.??\"]\n" +
                "[Round \"-\"]\n[White \"White\"]\n[Black \"Black\"]\n[Result \"*\"]\n\n$text"
        }
        holder.loadPgn(pgn)
        val game = holder.games.firstOrNull() ?: error("No readable PGN game was found.")
        val startFen = game.fen?.takeIf { it.isNotBlank() } ?: ChessSession.STARTING_FEN
        val moves = game.halfMoves.map { it.toString() }
        val session = ChessSession(
            startFen = startFen,
            moves = moves,
            ply = moves.size,
            whiteName = game.whitePlayer?.name?.takeIf { it.isNotBlank() } ?: "White",
            blackName = game.blackPlayer?.name?.takeIf { it.isNotBlank() } ?: "Black"
        )
        session.boardAt()
        return session
    }

    private suspend fun fromImage(context: Context, uri: Uri): ChessSession {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val recognized = try {
            recognizer.process(image).await().text
        } finally {
            recognizer.close()
        }
        if (recognized.isBlank()) {
            error("No readable chess notation was found in the PNG.")
        }

        val fen = findFen(recognized)
        if (fen != null) return fromText("position.fen", fen)

        val pgn = if (recognized.contains("[Event")) recognized else {
            "[Event \"PNG import\"]\n[Site \"?\"]\n[Date \"????.??.??\"]\n" +
                "[Round \"-\"]\n[White \"White\"]\n[Black \"Black\"]\n[Result \"*\"]\n\n" +
                recognized.replace('\n', ' ') + " *"
        }
        return fromText("image.pgn", pgn)
    }

    private fun findFen(text: String): String? {
        val tokens = text.replace('\n', ' ').split(Regex("\\s+"))
        for (start in 0..(tokens.size - 6)) {
            val candidate = tokens.subList(start, start + 6).joinToString(" ")
            parseFenOrNull(candidate)?.let { return it.fen }
        }
        return null
    }

    private fun parseFenOrNull(text: String): Board? = try {
        parseFen(text)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun parseFen(text: String): Board {
        val fen = text.trim()
        require(fen.split(Regex("\\s+")).size == 6) { "A FEN position must contain six fields." }
        val board = Board()
        try {
            board.loadFromFen(fen)
        } catch (exception: RuntimeException) {
            throw IllegalArgumentException("Invalid FEN position.", exception)
        }
        return board
    }
}
