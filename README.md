# Chess Insight

A native Android chess-game viewer and analyzer built with Kotlin and Jetpack Compose.

## License and disclaimer

This project is released under the GNU General Public License, version 3. See [LICENSE](./LICENSE).

This app is vibe-coded and experimental. It is provided “as is,” without warranties or guarantees. To the fullest extent permitted by applicable law, the maintainers disclaim responsibility and liability for claims, damages, or other issues arising from its use. This notice does not limit rights or obligations that cannot legally be excluded.

## Features

- Import PGN games and FEN positions, paste PGN moves or FEN notation, browse moves, and play from any selected position.
- Play moves directly on the board; deviating from an imported game automatically branches and analyzes the new line.
- Evaluate a position with Stockfish and see the advantage for White or Black on the evaluation bar.
- Play Stockfish's next move from the current position.
- Analyze imported PGN games automatically with Stockfish 19 and show move-quality labels, played/best-move arrows, the evaluation after each move, and rough Elo estimates.
- Toggle automatic analysis on import or manually analyze/reanalyze a game at any time.
- Choose game-analysis depth from 12, 16, 20, or 25.
- View high-contrast chess pieces and, after game analysis, the move label and side-to-move advantage directly on the board.
- Import PNG images to OCR visible PGN/FEN notation. Board-only images are not yet converted into positions.

Move labels use a simple centipawn-loss heuristic; “Brilliant” is a best-move/evaluation-swing heuristic, not Chess.com's classifier. Estimated Elo is a rough performance estimate from average centipawn loss, not an official or reliable player rating.

## Privacy

Imported PGN and FEN data is processed on-device and is not uploaded by the app. Avoid adding personal names, credentials, signing keys, local machine paths, or private game data to source files. Keep secrets in ignored local configuration, and ask before adding features that transmit user or game data.

## Build

Open the project in Android Studio with Android SDK 35 installed and build the `app` module. The first build downloads the official Stockfish 19 Android arm64 release, verifies its SHA-256 digest, and packages the engine as an Android native library so it can be launched from the app's executable library directory. The resulting app is arm64-v8a only and includes the large embedded engine binary.

Stockfish is distributed under GPL-3.0-or-later. The APK includes its `Copying.txt`; the corresponding Stockfish 19 source is available at [official-stockfish/Stockfish, tag sf_19](https://github.com/official-stockfish/Stockfish/tree/sf_19). Anyone redistributing the built app must also meet the GPL source-distribution requirements. Chesslib is Apache-2.0 licensed.
