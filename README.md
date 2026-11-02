# Chess Insight

A native Android chess-game viewer and analyzer built with Kotlin and Jetpack Compose.

## License and disclaimer

This project is released under the GNU General Public License, version 3. See [LICENSE](./LICENSE).

This app is vibe-coded and experimental. It is provided “as is,” without warranties or guarantees. To the fullest extent permitted by applicable law, the maintainers disclaim responsibility and liability for claims, damages, or other issues arising from its use. This notice does not limit rights or obligations that cannot legally be excluded.

## Features

- Import PGN/FEN files, paste PGN moves or FEN notation, browse moves, and play from any selected position. PNG OCR can recognize visible notation.
- Read move history and engine continuations in standard chess notation (SAN), with screen-reader labels for board squares and legal moves.
- Copy an analyzed or edited game as standard PGN, including player names and custom starting FEN metadata.
- Play moves directly on the board; deviating from an imported game automatically branches and analyzes the new line.
- Evaluate a position with Stockfish and see the advantage for White or Black on the evaluation bar.
- Play Stockfish's next move from the current position.
- Analyze imported PGN games automatically with Stockfish 19 and show move-quality labels in separate White and Black columns, player-specific Best/Excellent/Brilliant/Blunder counts, played/best-move arrows, the engine continuation and evaluation after each move, and rough Elo estimates.
- Toggle automatic analysis on import or manually analyze/reanalyze a game at any time.
- Choose game-analysis depth from 12, 16, 20, or 25; the default is 12.
- Flip the board to view the position from White's or Black's perspective.
- Animate played moves and view clearer, outlined analysis arrows.
- Choose a promotion piece, copy the current position as FEN, and use the move history with correct numbering for custom FEN starts.
- See check, checkmate, stalemate, and draw status; selected pieces show legal captures (including en passant), and the previous move is highlighted.
- Analysis depth, auto-analysis, and board orientation are remembered between app launches.
- View high-contrast chess pieces and, after game analysis, the move label and side-to-move advantage directly on the board.
- Import PNG images to OCR visible PGN/FEN notation. Board-only images are not yet converted into positions.

Move labels use a simple centipawn-loss heuristic; “Brilliant” is a best-move/evaluation-swing heuristic, not Chess.com's classifier. Estimated Elo is a very rough engine-based estimate, not an official or reliable player rating. It requires at least eight moves per player and is adjusted toward 1500 for shorter samples.

## Privacy

Imported PGN and FEN data is processed on-device and is not uploaded by the app. Avoid adding personal names, credentials, signing keys, local machine paths, or private game data to source files. Keep secrets in ignored local configuration, and ask before adding features that transmit user or game data.

## Build

Open the project in Android Studio with Android SDK 35 installed and build the `app` module. The first build downloads the official Stockfish 19 Android arm64 release, verifies its SHA-256 digest, and packages the engine as an Android native library so it can be launched from the app's executable library directory. The resulting app is arm64-v8a only and includes the large embedded engine binary.

Stockfish is distributed under GPL-3.0-or-later. The APK includes its `Copying.txt`; the corresponding Stockfish 19 source is available at [official-stockfish/Stockfish, tag sf_19](https://github.com/official-stockfish/Stockfish/tree/sf_19). Anyone redistributing the built app must also meet the GPL source-distribution requirements. Chesslib is Apache-2.0 licensed.
