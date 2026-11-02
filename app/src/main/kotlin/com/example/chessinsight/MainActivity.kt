package com.example.chessinsight

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.bhlangonijr.chesslib.Piece
import com.github.bhlangonijr.chesslib.Side
import com.github.bhlangonijr.chesslib.Square
import java.util.Locale
import kotlinx.coroutines.launch

private val Ink = Color(0xFF202B3C)
private val Background = Color(0xFFF3F5F9)
private val Panel = Color(0xFFFFFFFF)
private val Accent = Color(0xFF5C63D8)
private val Muted = Color(0xFF68758A)
private val PlayedMoveColor = Color(0xFFE97845)
private val BestMoveColor = Color(0xFF2479C8)

private data class PositionEvaluation(
    val fen: String,
    val scoreWhiteCp: Int,
    val mateWhiteIn: Int?
)

private data class BoardMoveAnimation(
    val fromFile: Int,
    val fromRank: Int,
    val toFile: Int,
    val toRank: Int,
    val piece: Piece
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondary = Color(0xFF5279B7),
                    background = Background,
                    surface = Panel,
                    onSurface = Ink
                )
            ) {
                ChessInsightApp()
            }
        }
    }
}

@Composable
private fun ChessInsightApp() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val engine = remember { StockfishEngine(context.applicationContext) }
    val preferences = remember(context) {
        context.getSharedPreferences("chess_insight_preferences", Context.MODE_PRIVATE)
    }
    var session by remember { mutableStateOf(ChessSession()) }
    var boardSide by rememberSaveable {
        mutableStateOf(
            if (preferences.getBoolean("black_perspective", false)) Side.BLACK else Side.WHITE
        )
    }
    var selectedOrigin by remember { mutableStateOf<String?>(null) }
    var review by remember { mutableStateOf<GameReview?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var engineMessage by remember { mutableStateOf<String?>(null) }
    var engineBusy by remember { mutableStateOf(false) }
    var importSelection by remember { mutableStateOf<Pair<Uri, String?>?>(null) }
    var analysisProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var notationEntry by remember { mutableStateOf("") }
    var positionEvaluation by remember { mutableStateOf<PositionEvaluation?>(null) }
    var analysisDepth by rememberSaveable {
        mutableStateOf(preferences.getInt("analysis_depth", 12).takeIf { it in listOf(12, 16, 20, 25) } ?: 12)
    }
    var showNotationInput by remember { mutableStateOf(false) }
    var autoAnalyzeImportedGames by rememberSaveable {
        mutableStateOf(preferences.getBoolean("auto_analyze", true))
    }
    var promotionMoves by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(analysisDepth) {
        preferences.edit().putInt("analysis_depth", analysisDepth).apply()
    }
    LaunchedEffect(autoAnalyzeImportedGames) {
        preferences.edit().putBoolean("auto_analyze", autoAnalyzeImportedGames).apply()
    }
    LaunchedEffect(boardSide) {
        preferences.edit().putBoolean("black_perspective", boardSide == Side.BLACK).apply()
    }

    suspend fun analyzeGame(targetSession: ChessSession) {
        engineBusy = true
        analysisProgress = 0 to targetSession.moves.size
        errorMessage = null
        engineMessage = "Analyzing game at depth $analysisDepth…"
        try {
            review = GameAnalyzer(engine).analyze(targetSession, analysisDepth) { done, total ->
                analysisProgress = done to total
            }
            engineMessage = "Game analysis complete · depth $analysisDepth."
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Game analysis failed."
            engineMessage = null
        } finally {
            analysisProgress = null
            engineBusy = false
        }
    }

    fun playMove(uci: String): ChessSession? {
        val current = session
        val followsGame = current.moves.getOrNull(current.ply)
            ?.equals(uci, ignoreCase = true) == true
        val updated = current.play(uci)
        session = updated
        selectedOrigin = null
        promotionMoves = emptyList()
        positionEvaluation = null
        errorMessage = null
        engineMessage = null
        if (followsGame) return null

        review = null
        return updated
    }

    fun loadGame(imported: ChessSession) {
        session = imported
        review = null
        selectedOrigin = null
        promotionMoves = emptyList()
        positionEvaluation = null
        errorMessage = null
        engineMessage = null
        notationEntry = ""
        showNotationInput = false
        if (autoAnalyzeImportedGames && imported.moves.isNotEmpty()) {
            scope.launch { analyzeGame(imported) }
        }
    }

    DisposableEffect(engine) {
        onDispose { engine.close() }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        importSelection = it?.let { uri -> uri to "import.png" }
    }

    val notationFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        importSelection = it?.let { uri -> uri to null }
    }

    LaunchedEffect(importSelection) {
        val (uri, selectedName) = importSelection ?: return@LaunchedEffect
        try {
            val displayName = selectedName
                ?: contentDisplayName(context, uri).orEmpty().ifBlank { "import.pgn" }
            loadGame(GameImport.fromUri(context, uri, displayName))
        } catch (exception: Exception) {
            errorMessage = exception.message ?: "Could not import that file."
        } finally {
            importSelection = null
        }
    }

    val board = remember(session) { session.boardAt() }
    val hasLegalMoves = remember(board) { board.legalMoves().isNotEmpty() }
    val positionStatus = remember(session) { session.positionStatus() }
    val captureTargets = remember(session, selectedOrigin) {
        selectedOrigin?.let(session::captureTargetsFrom).orEmpty()
    }
    val lastMoveSquares = remember(session) {
        session.moves.getOrNull(session.ply - 1)?.let { move ->
            setOf(move.substring(0, 2), move.substring(2, 4))
        }.orEmpty()
    }
    val legalMoves = remember(session, selectedOrigin) {
        selectedOrigin?.let(session::legalMovesFrom).orEmpty()
    }
    val legalTargets = remember(legalMoves) { legalMoves.map { it.substring(2, 4) }.toSet() }
    val positionFen = remember(session) { session.fenAt() }
    val currentEvaluation = positionEvaluation?.takeIf { it.fen == positionFen }
        ?: review?.evaluationsWhite?.getOrNull(session.ply)?.let {
            PositionEvaluation(positionFen, it, null)
        }
    val selectedReviewMove = review?.moves?.getOrNull(session.ply - 1)
    val sanMoves = remember(session) { session.sanMoves() }
    val selectedBestSan = selectedReviewMove?.let { move ->
        remember(session, move) { session.sanForMoveAt(move.ply - 1, move.bestMove) }
    }
    val selectedBestLineSan = selectedReviewMove?.let { move ->
        remember(session, move) { session.sanLineAt(move.ply - 1, move.bestLine) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !engineBusy && analysisProgress == null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onClick = { notationFilePicker.launch(arrayOf("*/*")) }
                ) {
                    Text("Open file", maxLines = 1, fontSize = 11.sp)
                }

                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !engineBusy && analysisProgress == null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onClick = { showNotationInput = !showNotationInput }
                ) {
                    Text(
                        if (showNotationInput) "Hide paste" else "Paste PGN / FEN",
                        maxLines = 1,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    modifier = Modifier.weight(0.85f),
                    enabled = !engineBusy && analysisProgress == null,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    onClick = { imagePicker.launch(arrayOf("image/png")) }
                ) {
                    Text("Scan image", maxLines = 1, fontSize = 11.sp)
                }
            }

            errorMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFED)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFB42318),
                            fontSize = 13.sp
                        )
                        TextButton(onClick = { errorMessage = null }) { Text("Dismiss") }
                    }
                }
            }

            engineMessage?.let { message ->
                Text(
                    message,
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            if (showNotationInput) item {
                OutlinedTextField(
                    value = notationEntry,
                    onValueChange = { notationEntry = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !engineBusy && analysisProgress == null,
                    label = { Text("Paste PGN or FEN") },
                    supportingText = { Text("Paste game notation or a six-field FEN position.") },
                    minLines = 2,
                    maxLines = 4,
                    trailingIcon = {
                        TextButton(
                            enabled = !engineBusy && analysisProgress == null,
                            onClick = {
                                try {
                                    val imported = GameImport.fromText("input.pgn", notationEntry)
                                    loadGame(imported)
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Could not read PGN or FEN."
                                }
                            }
                        ) {
                            Text("Load")
                        }
                    }
                )
            }

            item {
                EvaluationBar(
                    scoreWhiteCp = currentEvaluation?.scoreWhiteCp,
                    mateWhiteIn = currentEvaluation?.mateWhiteIn
                )
            }

            selectedReviewMove?.let { moveReview ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "${moveReview.move} ${sanMoves.getOrNull(moveReview.ply - 1) ?: moveReview.playedMove} · ${moveReview.label.title}",
                                color = labelColor(moveReview.label),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Best ${selectedBestSan ?: moveReview.bestMove} · ${evaluationSummary(moveReview.evaluationAfterWhite)}",
                                color = Ink,
                                fontSize = 12.sp
                            )
                            if (moveReview.bestLine.isNotEmpty()) {
                                Text(
                                    "Engine line: ${selectedBestLineSan.orEmpty().take(6).joinToString(" ")}",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("Played move", color = PlayedMoveColor, fontSize = 11.sp)
                                Text("Best move", color = BestMoveColor, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        boardSide = if (boardSide == Side.WHITE) Side.BLACK else Side.WHITE
                        selectedOrigin = null
                    }
                ) {
                    Text(
                        "Flip board · playing as ${
                            if (boardSide == Side.WHITE) "White" else "Black"
                        }"
                    )
                }
            }

            item {
                BoardView(
                    session = session,
                    boardSide = boardSide,
                    selectedOrigin = selectedOrigin,
                    legalTargets = legalTargets,
                    captureTargets = captureTargets,
                    lastMoveSquares = lastMoveSquares,
                    reviewMove = selectedReviewMove,
                    enabled = !engineBusy && analysisProgress == null,
                    onSquareTap = { square ->
                        val selected = selectedOrigin
                        val destinationMoves = legalMoves.filter { it.substring(2, 4) == square }
                        if (selected != null && destinationMoves.isNotEmpty()) {
                            if (destinationMoves.size > 1) {
                                promotionMoves = destinationMoves
                            } else {
                                try {
                                    val variation = playMove(destinationMoves.single())
                                    if (variation != null) {
                                        scope.launch { analyzeGame(variation) }
                                    }
                                } catch (exception: Exception) {
                                    errorMessage = exception.message
                                }
                                selectedOrigin = null
                            }
                        } else {
                            val piece = session.pieceAt(square)
                            if (piece != Piece.NONE && piece.pieceSide == board.sideToMove) {
                                selectedOrigin = square
                            } else if (selected != null) {
                                selectedOrigin = null
                            }
                        }
                    }
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${session.whiteName}  ·  ${session.blackName}",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        when (positionStatus) {
                            PositionStatus.CHECKMATE -> "Checkmate"
                            PositionStatus.STALEMATE -> "Stalemate · draw"
                            PositionStatus.DRAW -> "Draw"
                            PositionStatus.CHECK -> "${if (board.sideToMove == Side.WHITE) "White" else "Black"} in check"
                            PositionStatus.ACTIVE -> if (board.sideToMove == Side.WHITE) "White to move" else "Black to move"
                        },
                        color = Muted,
                        maxLines = 1,
                        fontSize = 12.sp
                    )
                }
            }

            item {
                Text(
                    if (engineBusy) "Analyzing… controls are paused until the engine finishes."
                    else "Tap a piece, then a highlighted square to play. Alternate moves are analyzed automatically.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            if (session.moves.isEmpty() && session.startFen == ChessSession.STARTING_FEN) {
                item {
                    Text(
                        "Ready to study? Open a PGN file or paste a game to see its move history and analysis.",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !engineBusy && analysisProgress == null,
                    onClick = {
                        scope.launch {
                            engineBusy = true
                            engineMessage = "Evaluating this position…"
                            errorMessage = null
                            try {
                                val result = engine.analyze(positionFen, analysisDepth)
                                val whiteToMove = board.sideToMove == Side.WHITE
                                positionEvaluation = PositionEvaluation(
                                    fen = positionFen,
                                    scoreWhiteCp = if (whiteToMove) result.scoreCp else -result.scoreCp,
                                    mateWhiteIn = result.mateIn?.let { if (whiteToMove) it else -it }
                                )
                                engineMessage = "Position evaluated at depth $analysisDepth."
                            } catch (exception: Exception) {
                                errorMessage = exception.message ?: "Stockfish could not analyze this position."
                                engineMessage = null
                            } finally {
                                engineBusy = false
                            }
                        }
                    }
                ) { Text("Analyze position", maxLines = 1) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = !engineBusy && analysisProgress == null,
                    onClick = {
                        clipboard.setText(AnnotatedString(positionFen))
                        engineMessage = "Current position FEN copied."
                        errorMessage = null
                    }
                ) { Text("Copy FEN", maxLines = 1) }
                }
            }

            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = session.moves.isNotEmpty() && !engineBusy && analysisProgress == null,
                    onClick = {
                        try {
                            clipboard.setText(AnnotatedString(session.toPgn()))
                            engineMessage = "Game copied as PGN."
                            errorMessage = null
                        } catch (exception: Exception) {
                            errorMessage = exception.message ?: "Could not export this game as PGN."
                        }
                    }
                ) { Text("Copy game as PGN") }
            }

            if (session.moves.isNotEmpty()) {
                item {
                    Text(
                        "MOVE HISTORY  ·  tap a move to jump there",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.moves.forEachIndexed { index, move ->
                            val active = session.ply == index + 1
                            Text(
                                text = "${session.moveNotationAt(index)} ${sanMoves.getOrNull(index) ?: move}",
                                color = if (active) Color.White else Ink,
                                modifier = Modifier
                                    .background(if (active) Accent else Panel, RoundedCornerShape(8.dp))
                                    .clickable(enabled = !engineBusy && analysisProgress == null) {
                                        session = session.jumpTo(index + 1)
                                        selectedOrigin = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("GAME ANALYSIS", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Choose engine depth", color = Muted, fontSize = 12.sp)
                            }
                            Text("DEPTH $analysisDepth", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(12, 16, 20, 25).forEach { depth ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (analysisDepth == depth) Accent else Color(0xFFE8ECF3))
                                        .clickable(enabled = !engineBusy && analysisProgress == null) {
                                            analysisDepth = depth
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        depth.toString(),
                                        color = if (analysisDepth == depth) Color.White else Ink,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            "Imported PGN games are analyzed automatically. You can also analyze again below.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-analyze imported PGN", color = Ink, fontSize = 13.sp)
                            Switch(
                                checked = autoAnalyzeImportedGames,
                                onCheckedChange = { autoAnalyzeImportedGames = it },
                                enabled = !engineBusy && analysisProgress == null
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = session.moves.isNotEmpty() && !engineBusy && analysisProgress == null,
                            onClick = { scope.launch { analyzeGame(session) } }
                        ) { Text("Analyze / reanalyze game") }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = hasLegalMoves && !engineBusy && analysisProgress == null,
                        onClick = {
                            scope.launch {
                                engineBusy = true
                                engineMessage = "Stockfish is thinking…"
                                errorMessage = null
                                var variationToAnalyze: ChessSession? = null
                                try {
                                    val result = engine.analyze(session.fenAt(), analysisDepth)
                                    if (result.bestMove.isNotBlank() && result.bestMove != "(none)") {
                                        val evaluation = result.mateIn?.let { "M$it" } ?: run {
                                            "${if (result.scoreCp > 0) "+" else ""}${formatPawns(result.scoreCp)}"
                                        }
                                        val pv = result.principalVariation.take(5).joinToString(" ")
                                        variationToAnalyze = playMove(result.bestMove)
                                        engineMessage = "Stockfish played ${result.bestMove} · $evaluation for side to move" +
                                            if (pv.isBlank()) "" else " · PV $pv"
                                    } else {
                                        engineMessage = "No legal moves in this position."
                                    }
                                } catch (exception: Exception) {
                                    errorMessage = exception.message ?: "Stockfish could not analyze this position."
                                    engineMessage = null
                                } finally {
                                    engineBusy = false
                                }
                                variationToAnalyze?.let { analyzeGame(it) }
                            }
                        }
                    ) { Text("Play Stockfish best move") }
                }
            }

            analysisProgress?.let { (done, total) ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Analyzing move $done of $total · depth $analysisDepth", color = Muted)
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (total == 0) 0f else done.toFloat() / total },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            review?.let { gameReview ->
                item { ReviewSummary(gameReview, session.whiteName, session.blackName) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "WHITE MOVES",
                            modifier = Modifier.weight(1f),
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "BLACK MOVES",
                            modifier = Modifier.weight(1f),
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                val whiteMoves = gameReview.moves.filter { it.mover == Side.WHITE }
                val blackMoves = gameReview.moves.filter { it.mover == Side.BLACK }
                val moveRows = List(maxOf(whiteMoves.size, blackMoves.size)) { index ->
                    whiteMoves.getOrNull(index) to blackMoves.getOrNull(index)
                }
                items(moveRows) { (whiteMove, blackMove) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MoveReviewCard(
                            moveReview = whiteMove,
                            displayMove = whiteMove?.let { sanMoves.getOrNull(it.ply - 1) } ?: whiteMove?.playedMove.orEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { selectedMove ->
                            session = session.jumpTo(selectedMove.ply)
                            selectedOrigin = null
                        }
                        MoveReviewCard(
                            moveReview = blackMove,
                            displayMove = blackMove?.let { sanMoves.getOrNull(it.ply - 1) } ?: blackMove?.playedMove.orEmpty(),
                            modifier = Modifier.weight(1f)
                        ) { selectedMove ->
                            session = session.jumpTo(selectedMove.ply)
                            selectedOrigin = null
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            }

            promotionMoves.takeIf { it.isNotEmpty() }?.let { candidates ->
                AlertDialog(
                    onDismissRequest = { promotionMoves = emptyList() },
                    title = { Text("Choose promotion") },
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            candidates.forEach { move ->
                                TextButton(
                                    onClick = {
                                        promotionMoves = emptyList()
                                        try {
                                            playMove(move)?.let { variation ->
                                                scope.launch { analyzeGame(variation) }
                                            }
                                        } catch (exception: Exception) {
                                            errorMessage = exception.message
                                        }
                                    }
                                ) {
                                    Text(promotionName(move))
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { promotionMoves = emptyList() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = session.ply > 0 && !engineBusy && analysisProgress == null,
                    onClick = { session = session.jumpTo(0); selectedOrigin = null }
                ) { Text("Start", maxLines = 1, fontSize = 12.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = session.ply > 0 && !engineBusy && analysisProgress == null,
                    onClick = { session = session.jumpTo(session.ply - 1); selectedOrigin = null }
                ) { Text("‹ Back", maxLines = 1, fontSize = 12.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = session.ply < session.moves.size && !engineBusy && analysisProgress == null,
                    onClick = { session = session.jumpTo(session.ply + 1); selectedOrigin = null }
                ) { Text("Next ›", maxLines = 1, fontSize = 12.sp) }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = session.ply < session.moves.size && !engineBusy && analysisProgress == null,
                    onClick = { session = session.jumpTo(session.moves.size); selectedOrigin = null }
                ) { Text("End", maxLines = 1, fontSize = 12.sp) }
            }
            Text(
                "Position ${session.ply} of ${session.moves.size} moves",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun EvaluationBar(scoreWhiteCp: Int?, mateWhiteIn: Int?) {
    val whiteAdvantage = scoreWhiteCp?.let { it > 0 } ?: false
    val blackAdvantage = scoreWhiteCp?.let { it < 0 } ?: false
    val whiteShare = ((scoreWhiteCp ?: 0) / 800f + 0.5f).coerceIn(0.04f, 0.96f)
    val evaluationText = when {
        scoreWhiteCp == null -> "Analyze position to see who is better"
        mateWhiteIn == 0 -> if (whiteAdvantage) "White wins by checkmate" else "Black wins by checkmate"
        mateWhiteIn != null -> "${if (whiteAdvantage) "White" else "Black"} mates in ${kotlin.math.abs(mateWhiteIn)}"
        scoreWhiteCp == 0 -> "Equal · 0.00"
        else -> "${if (whiteAdvantage) "White" else "Black"} is better · " +
            "+${formatPawns(kotlin.math.abs(scoreWhiteCp))}"
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("WHITE", color = if (whiteAdvantage) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(evaluationText, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("BLACK", color = if (blackAdvantage) Accent else Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF26334A))
        ) {
            Box(
                modifier = Modifier.weight(whiteShare).fillMaxSize().background(Color(0xFFF5F0E5))
            )
            Box(
                modifier = Modifier.weight(1f - whiteShare).fillMaxSize().background(Color(0xFF26334A))
            )
        }
    }
}

@Composable
private fun BoardView(
    session: ChessSession,
    boardSide: Side,
    selectedOrigin: String?,
    legalTargets: Set<String>,
    captureTargets: Set<String>,
    lastMoveSquares: Set<String>,
    reviewMove: MoveReview?,
    enabled: Boolean,
    onSquareTap: (String) -> Unit
) {
    val board = remember(session) { session.boardAt() }
    var previousSession by remember { mutableStateOf(session) }
    var movingPiece by remember { mutableStateOf<BoardMoveAnimation?>(null) }
    val moveProgress = remember { Animatable(1f) }

    LaunchedEffect(session) {
        val previous = previousSession
        previousSession = session
        movingPiece = null
        moveProgress.snapTo(1f)

        val move = session.moves.getOrNull(previous.ply)
        if (
            session.startFen == previous.startFen &&
            session.ply == previous.ply + 1 &&
            session.moves.take(previous.ply) == previous.moves.take(previous.ply) &&
            move != null && move.length >= 4
        ) {
            val fromFile = move[0] - 'a'
            val fromRank = move[1] - '1'
            val toFile = move[2] - 'a'
            val toRank = move[3] - '1'
            if (fromFile in 0..7 && fromRank in 0..7 && toFile in 0..7 && toRank in 0..7) {
                val destination = move.substring(2, 4)
                val piece = board.getPiece(Square.valueOf(destination.uppercase()))
                if (piece != Piece.NONE) {
                    movingPiece = BoardMoveAnimation(fromFile, fromRank, toFile, toRank, piece)
                    moveProgress.snapTo(0f)
                    moveProgress.animateTo(
                        1f,
                        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                    )
                    movingPiece = null
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .border(2.dp, Color(0xFFCBD3DF), RoundedCornerShape(14.dp))
    ) {
        val cellSize = maxWidth / 8
        val animation = movingPiece
        val animationProgress = moveProgress.value

        Column(modifier = Modifier.fillMaxSize()) {
            val ranks = if (boardSide == Side.WHITE) 8 downTo 1 else 1..8
            val files = if (boardSide == Side.WHITE) 'a'..'h' else 'h' downTo 'a'
            for (rank in ranks) {
                Row(modifier = Modifier.weight(1f)) {
                    for (file in files) {
                        val square = "$file$rank"
                        val piece = board.getPiece(Square.valueOf(square.uppercase()))
                        val light = (file.code - 'a'.code + rank) % 2 == 0
                        val squareColor = when {
                            square == selectedOrigin -> Color(0xFFF2CE78)
                            square in lastMoveSquares -> Color(0xFFD8B957)
                            light -> Color(0xFFF1E8D8)
                            else -> Color(0xFF788CA8)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(squareColor)
                                .semantics {
                                    val pieceName = if (piece == Piece.NONE) {
                                        "empty"
                                    } else {
                                        piece.name.substringAfter('_').lowercase()
                                    }
                                    val destination = when {
                                        square in captureTargets -> ", legal capture"
                                        square in legalTargets -> ", legal move"
                                        else -> ""
                                    }
                                    contentDescription = "$square, $pieceName$destination"
                                }
                                .clickable(enabled = enabled) { onSquareTap(square) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (square in legalTargets && square !in captureTargets && piece == Piece.NONE) {
                                Box(
                                    Modifier.size(12.dp).background(Color(0x887A876E), CircleShape)
                                )
                            }
                            if (
                                piece != Piece.NONE &&
                                !(animation != null && square == "${'a' + animation.toFile}${animation.toRank + 1}")
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                    drawChessPiece(piece)
                                }
                            }
                            if (square in captureTargets) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(5.dp)) {
                                    drawCircle(
                                        color = Color(0xCCB54238),
                                        radius = size.minDimension * 0.43f,
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (animation != null) {
            val fromColumn = if (boardSide == Side.WHITE) animation.fromFile else 7 - animation.fromFile
            val fromRow = if (boardSide == Side.WHITE) 7 - animation.fromRank else animation.fromRank
            val toColumn = if (boardSide == Side.WHITE) animation.toFile else 7 - animation.toFile
            val toRow = if (boardSide == Side.WHITE) 7 - animation.toRank else animation.toRank
            Canvas(
                modifier = Modifier
                    .offset(
                        x = cellSize * (
                            toColumn + (fromColumn - toColumn) * (1f - animationProgress)
                        ),
                        y = cellSize * (toRow + (fromRow - toRow) * (1f - animationProgress))
                    )
                    .size(cellSize)
                    .padding(4.dp)
            ) {
                drawChessPiece(animation.piece)
            }
        }

        if (reviewMove != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellWidth = size.width / 8f
                val cellHeight = size.height / 8f

                fun drawMoveArrow(uci: String, color: Color, laneOffsetDp: Float) {
                    if (uci.length < 4) return
                    val fromFile = uci[0] - 'a'
                    val fromRank = uci[1] - '1'
                    val toFile = uci[2] - 'a'
                    val toRank = uci[3] - '1'
                    if (fromFile !in 0..7 || fromRank !in 0..7 || toFile !in 0..7 || toRank !in 0..7) {
                        return
                    }

                    val viewFromFile = if (boardSide == Side.WHITE) fromFile else 7 - fromFile
                    val viewFromRank = if (boardSide == Side.WHITE) 7 - fromRank else fromRank
                    val viewToFile = if (boardSide == Side.WHITE) toFile else 7 - toFile
                    val viewToRank = if (boardSide == Side.WHITE) 7 - toRank else toRank
                    val rawStart = Offset(
                        (viewFromFile + 0.5f) * cellWidth,
                        (viewFromRank + 0.5f) * cellHeight
                    )
                    val rawEnd = Offset(
                        (viewToFile + 0.5f) * cellWidth,
                        (viewToRank + 0.5f) * cellHeight
                    )
                    val dx = rawEnd.x - rawStart.x
                    val dy = rawEnd.y - rawStart.y
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (distance == 0f) return

                    val ux = dx / distance
                    val uy = dy / distance
                    val lane = laneOffsetDp.dp.toPx()
                    val perpendicular = Offset(-uy * lane, ux * lane)
                    val start = rawStart + perpendicular
                    val end = rawEnd + perpendicular
                    val headLength = minOf(cellWidth, cellHeight) * 0.3f
                    val baseX = end.x - ux * headLength
                    val baseY = end.y - uy * headLength
                    val halfWidth = headLength * 0.62f
                    val strokeWidth = 6.dp.toPx()
                    drawLine(
                        color = Color(0x660D1725),
                        start = start,
                        end = Offset(baseX, baseY),
                        strokeWidth = strokeWidth + 7.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White,
                        start = start,
                        end = Offset(baseX, baseY),
                        strokeWidth = strokeWidth + 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = color.copy(alpha = 0.94f),
                        start = start,
                        end = Offset(baseX, baseY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                    val arrowHead = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(baseX - uy * halfWidth, baseY + ux * halfWidth)
                        lineTo(baseX + uy * halfWidth, baseY - ux * halfWidth)
                        close()
                    }
                    val shadowOffset = 2.dp.toPx()
                    withTransform({ translate(top = shadowOffset) }) {
                        drawPath(arrowHead, Color(0x550D1725))
                    }
                    drawPath(arrowHead, Color.White, style = Stroke(width = 3.dp.toPx()))
                    drawPath(arrowHead, color.copy(alpha = 0.96f))
                    drawCircle(Color.White, radius = strokeWidth * 1.05f, center = start)
                    drawCircle(color, radius = strokeWidth * 0.7f, center = start)
                }

                if (reviewMove.bestMove.equals(reviewMove.playedMove, ignoreCase = true)) {
                    drawMoveArrow(reviewMove.bestMove, BestMoveColor, 0f)
                } else {
                    drawMoveArrow(reviewMove.bestMove, BestMoveColor, -5f)
                    drawMoveArrow(reviewMove.playedMove, PlayedMoveColor, 5f)
                }
            }
        }
    }
}

private fun evaluationSummary(scoreWhiteCp: Int): String = when {
    scoreWhiteCp in -10..10 -> "Equal · 0.00"
    scoreWhiteCp > 0 -> "White better · +${formatPawns(scoreWhiteCp)}"
    else -> "Black better · +${formatPawns(kotlin.math.abs(scoreWhiteCp))}"
}

private fun formatPawns(centipawns: Int): String =
    String.format(Locale.ROOT, "%.2f", centipawns / 100.0)

private fun promotionName(move: String): String = when (move.lastOrNull()?.lowercaseChar()) {
    'q' -> "Queen"
    'r' -> "Rook"
    'b' -> "Bishop"
    'n' -> "Knight"
    else -> "Promote"
}

private fun contentDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    ) ?: return null
    return cursor.use {
        val nameColumn = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameColumn < 0 || !it.moveToFirst()) null else it.getString(nameColumn)
    }
}

@Composable
private fun MoveReviewCard(
    moveReview: MoveReview?,
    displayMove: String,
    modifier: Modifier = Modifier,
    onClick: (MoveReview) -> Unit
) {
    if (moveReview == null) {
        Spacer(modifier)
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = modifier.fillMaxWidth().clickable { onClick(moveReview) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "${moveReview.move} $displayMove",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Text(
                moveReview.label.title,
                color = labelColor(moveReview.label),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            Text("Best ${moveReview.bestMove} · ${moveReview.centipawnLoss} cp loss", color = Muted, fontSize = 10.sp)
            Text(evaluationSummary(moveReview.evaluationAfterWhite), color = Ink, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ReviewSummary(review: GameReview, whiteName: String, blackName: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("PLAYER PERFORMANCE & MOVE QUALITY", color = Accent, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerMoveSummaryColumn(
                    modifier = Modifier.weight(1f),
                    side = Side.WHITE,
                    name = whiteName,
                    estimate = review.whiteEstimatedElo,
                    summary = review.summaryFor(Side.WHITE)
                )
                PlayerMoveSummaryColumn(
                    modifier = Modifier.weight(1f),
                    side = Side.BLACK,
                    name = blackName,
                    estimate = review.blackEstimatedElo,
                    summary = review.summaryFor(Side.BLACK)
                )
            }
            Text(
                "Very rough engine-based estimates, rounded to 50 points and adjusted toward 1500 " +
                    "for short samples; not official ratings. Engine depth ${review.depth}.",
                color = Muted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PlayerMoveSummaryColumn(
    modifier: Modifier,
    side: Side,
    name: String,
    estimate: Int?,
    summary: PlayerMoveSummary
) {
    val player = if (side == Side.WHITE) "WHITE" else "BLACK"
    val rating = estimate?.let { "$it Elo" }
        ?: "Need ${(EloEstimator.MIN_MOVES - summary.moveCount).coerceAtLeast(0)} more moves"
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "$player · $name",
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(rating, color = Muted, fontSize = 12.sp)
        Text("Moves ${summary.moveCount} · Best ${summary.bestMoves}", color = Ink, fontSize = 11.sp)
        Text("Excellent ${summary.excellentMoves} · Brilliant ${summary.brilliantMoves}", color = Ink, fontSize = 11.sp)
        Text("Blunders ${summary.blunders}", color = labelColor(MoveLabel.BLUNDER), fontSize = 11.sp)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawChessPiece(piece: Piece) {
    val scale = size.minDimension / 100f
    val left = (size.width - 100f * scale) / 2f
    val top = (size.height - 100f * scale) / 2f
    fun point(x: Float, y: Float) = Offset(left + x * scale, top + y * scale)
    fun oval(path: Path, x1: Float, y1: Float, x2: Float, y2: Float) {
        val first = point(x1, y1)
        val second = point(x2, y2)
        path.addOval(Rect(first.x, first.y, second.x, second.y))
    }

    val silhouette = Path()
    fun polygon(vararg coordinates: Float) {
        silhouette.moveTo(point(coordinates[0], coordinates[1]).x, point(coordinates[0], coordinates[1]).y)
        for (index in 2 until coordinates.size step 2) {
            val next = point(coordinates[index], coordinates[index + 1])
            silhouette.lineTo(next.x, next.y)
        }
        silhouette.close()
    }

    when (piece.name.substringAfter('_')) {
        "PAWN" -> {
            oval(silhouette, 39f, 13f, 61f, 35f)
            polygon(44f, 37f, 56f, 37f, 57f, 45f, 62f, 54f, 67f, 69f, 33f, 69f, 38f, 54f, 43f, 45f)
        }
        "ROOK" -> polygon(
            32f, 20f, 43f, 20f, 43f, 30f, 48f, 30f, 48f, 20f, 53f, 20f,
            53f, 30f, 58f, 30f, 58f, 20f, 69f, 20f, 67f, 40f, 62f, 44f,
            66f, 69f, 34f, 69f, 38f, 44f, 33f, 40f
        )
        "KNIGHT" -> {
            silhouette.moveTo(point(31f, 75f).x, point(31f, 75f).y)
            silhouette.cubicTo(
                point(37f, 59f).x, point(37f, 59f).y,
                point(32f, 48f).x, point(32f, 48f).y,
                point(38f, 38f).x, point(38f, 38f).y
            )
            silhouette.lineTo(point(34f, 26f).x, point(34f, 26f).y)
            silhouette.lineTo(point(52f, 34f).x, point(52f, 34f).y)
            silhouette.cubicTo(
                point(63f, 29f).x, point(63f, 29f).y,
                point(72f, 39f).x, point(72f, 39f).y,
                point(69f, 52f).x, point(69f, 52f).y
            )
            silhouette.lineTo(point(63f, 64f).x, point(63f, 64f).y)
            silhouette.lineTo(point(70f, 75f).x, point(70f, 75f).y)
            silhouette.close()
        }
        "BISHOP" -> {
            oval(silhouette, 45f, 13f, 55f, 23f)
            polygon(43f, 27f, 57f, 27f, 60f, 39f, 66f, 55f, 61f, 69f, 39f, 69f, 34f, 55f, 40f, 39f)
        }
        "QUEEN" -> {
            oval(silhouette, 28f, 22f, 42f, 36f)
            oval(silhouette, 43f, 12f, 57f, 26f)
            oval(silhouette, 58f, 22f, 72f, 36f)
            polygon(32f, 38f, 68f, 38f, 64f, 53f, 60f, 67f, 40f, 67f, 36f, 53f)
        }
        else -> {
            polygon(46f, 12f, 54f, 12f, 54f, 24f, 65f, 24f, 65f, 32f, 54f, 32f, 54f, 39f, 59f, 45f, 66f, 58f, 62f, 69f, 38f, 69f, 34f, 58f, 41f, 45f, 46f, 39f, 46f, 32f, 35f, 32f, 35f, 24f, 46f, 24f)
        }
    }
    polygon(27f, 70f, 73f, 70f, 78f, 80f, 22f, 80f)
    polygon(19f, 82f, 81f, 82f, 85f, 91f, 15f, 91f)

    val white = piece.pieceSide == Side.WHITE
    val fillColor = if (white) Color(0xFFFFFFFF) else Color(0xFF202B3C)
    val outlineColor = if (white) Color(0xFF26354A) else Color(0xFFFFF3D8)
    val detailColor = if (white) Color(0xFF64748B) else Color(0xFFF0C978)
    drawPath(silhouette, Color(0x44000000), style = Stroke(width = 7f * scale))
    drawPath(silhouette, outlineColor, style = Stroke(width = 5f * scale))
    drawPath(silhouette, fillColor)

    fun detailLine(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = 2.4f) {
        drawLine(
            color = detailColor,
            start = point(x1, y1),
            end = point(x2, y2),
            strokeWidth = width * scale,
            cap = StrokeCap.Round
        )
    }
    when (piece.name.substringAfter('_')) {
        "PAWN" -> {
            drawCircle(detailColor, radius = 2.8f * scale, center = point(50f, 24f))
            detailLine(44f, 49f, 56f, 49f)
        }
        "ROOK" -> {
            detailLine(40f, 42f, 60f, 42f)
            detailLine(43f, 48f, 57f, 48f)
        }
        "KNIGHT" -> {
            drawCircle(detailColor, radius = 2.2f * scale, center = point(56f, 41f))
            detailLine(40f, 57f, 52f, 61f, 2f)
        }
        "BISHOP" -> {
            detailLine(47f, 31f, 54f, 37f, 3f)
            detailLine(45f, 51f, 55f, 61f)
        }
        "QUEEN" -> detailLine(40f, 48f, 60f, 48f)
        else -> {
            detailLine(50f, 48f, 50f, 64f)
            detailLine(43f, 56f, 57f, 56f)
        }
    }
    detailLine(35f, 75f, 65f, 75f)
}

private fun labelColor(label: MoveLabel): Color = when (label) {
    MoveLabel.BRILLIANT -> Color(0xFF007C83)
    MoveLabel.BEST -> Accent
    MoveLabel.EXCELLENT -> Color(0xFF2878BC)
    MoveLabel.GOOD -> Color(0xFF5F6F86)
    MoveLabel.INACCURACY -> Color(0xFF9A6900)
    MoveLabel.MISTAKE -> Color(0xFFC65D22)
    MoveLabel.BLUNDER -> Color(0xFFC53636)
}
