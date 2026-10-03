# WOTD Solver

**Binance Word of the Day Screenshot Solver** — an Android app that analyzes a
WOTD puzzle screenshot and finds possible answers. Fully offline: no login, no
backend, no API keys. Screenshots never leave the device.

## Features

- **Screenshot analysis** — pick an image from storage or capture with the
  camera; the app locates the puzzle grid itself (3–8 letters, detected
  dynamically, never hard-coded)
- **Letter recognition** — on-device OCR tuned for WOTD tiles (template
  matching against Android-rendered glyphs); low-confidence letters are flagged
  `?` for review instead of being silently trusted
- **Tile color detection** — green / yellow / gray via hue sampling with
  tolerance for brightness and compression differences
- **Exact Wordle constraint engine** — candidates are filtered by simulating
  the true coloring function, so duplicate letters are handled correctly
- **Crypto-first ranking** — known past WODL answers rank highest, then a
  curated crypto / trading / finance vocabulary, then general English
- **Best next guess** — suggests the most informative next guess with a reason
- **Edit Detection** — tap a tile to cycle its color, long-press to change its
  letter, then re-solve
- **Manual Mode** — type guesses and tile colors yourself when a screenshot
  won't scan
- **History** — solved puzzles stored on-device only (open / delete / clear,
  can be disabled)
- **Settings** — dark / light theme, automatic analysis toggle
- **About** — developer contact with tap-to-email and tap-to-WhatsApp

## Project structure

```
AndroidManifest.xml
build.sh                  # manual build script (no Gradle daemon needed)
src/com/taqijafri/wotdsolver/
  vision/VisionCore.kt       # platform-agnostic: grid detection, tile colors, OCR
  vision/AndroidTemplates.kt # A-Z letter templates rendered with Android fonts
  solver/SolverCore.kt       # Wordle constraint engine + ranking + best guess
  solver/PuzzleAnalyzer.kt   # screenshot pixels -> guesses -> candidates
  data/WordDatabase.kt       # offline word lists (res/raw)
  data/HistoryStore.kt       # on-device JSON history
  data/SettingsStore.kt      # SharedPreferences settings
  ui/                        # activities (home, result, manual, edit, history, settings, about)
  ScreenshotAnalyzer.kt      # Bitmap -> PuzzleAnalyzer bridge
  ScreenshotProvider.kt      # serves camera captures (no androidx FileProvider)
  ImageHolder.kt             # in-process image/analysis holder
res/
  layout/ values/ drawable/
  raw/words_3.txt … words_8.txt   # offline dictionaries, best-first order
tools/words/
  past_answers.txt         # known past WODL answers (top ranking tier)
  curated_final.txt        # hand-curated crypto/trading/finance vocabulary
  rebuild.py               # rebuilds res/raw/words_N.txt from the tiers above
```

The word database is expandable without code changes: append lowercase words
to `tools/words/curated_final.txt` and run `tools/words/rebuild.py`.

## Building

Requirements: Android SDK with build-tools 34.0.0, platform android-35,
JDK 17, and `kotlin-compiler-embeddable` 1.9.x.

```bash
./build.sh
# -> WOTD-Solver.apk (zipaligned + signed)
```

`build.sh` compiles with kotlinc directly, dexes with d8, packages with aapt2,
and signs with apksigner. A release keystore is generated on first run
(`wotdsolver-release.keystore`, git-ignored — back it up; reuse it for updates
so Android accepts them as the same app).

## Notes

- `minSdk 26`, `targetSdk 35`, zero external dependencies (Android framework only).
- Camera captures are served through a minimal built-in `ContentProvider`;
  gallery picks use `ACTION_OPEN_DOCUMENT` (no storage permission needed).

## Developer

**Muhammad Taqi** — taqijafri398@gmail.com — 03077982214 (WhatsApp)
