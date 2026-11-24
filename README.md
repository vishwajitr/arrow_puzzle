# Arrow Escape

Tap an arrow and it slides out the way it points, if nothing is in its way. Clear the grid by finding the order.

Kotlin, Jetpack Compose (Material 3), MVVM with StateFlow, DataStore, Navigation-Compose, manual DI. minSdk 24.

## Build

Requires JDK 17 and the Android SDK (platform 37). `local.properties` must contain `sdk.dir=...`.

```bash
./gradlew testDebugUnitTest   # game logic, solver, generator, tutorial levels, generation benchmark
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk (Google TEST ads)
./gradlew assembleRelease     # R8-minified, real AdMob ids; unsigned until you add a signingConfig
```

## Publishing

Release signing, store listing text, graphics, Data safety answers and upload steps: `store/LISTING.md`. Privacy policy: `PRIVACY.md`, published at https://github.com/vishwajitr/arrow_puzzle/blob/main/PRIVACY.md (the URL used in Play Console). Bump `VERSION_CODE` in `gradle.properties` for every upload.

## Layout

| Path | What |
|---|---|
| `game/Model.kt` | `Arrow` (cells head-first + direction), `Level`, `Board` occupancy grid |
| `game/Solver.kt` | Greedy solver: removing a free arrow never hurts, so greedy is complete. Returns order + depth |
| `game/Generator.kt` | `DifficultyConfig` table + seeded generator (level N is always the same level) |
| `game/Game.kt` | Pure rules: hearts, mistakes, stars, revive, hint |
| `game/Levels.kt` | Tutorial JSON loader (levels 1–10), else the generator |
| `game/GameViewModel.kt` | Wraps `Game`, emits UI state + one-shot board animation events |
| `ui/Board.kt` | Canvas board: snake movement, bounce-and-shake, hint glow, hit-testing |
| `data/` | DataStore prefs, SoundPool effects, AdMob |

**Solvable by construction.** The generator places arrows one at a time, and each new arrow's exit ray only has to be clear of arrows placed before it. Removing them in reverse order always works. The solver still verifies every level, and the unit tests check levels 11–500.

**Shaped boards.** From level 41 on, every even level uses a silhouette (heart, circle, diamond, cross, hourglass, ring; `Shape` in `game/Generator.kt`). Cells outside the shape hold no arrows, but exit rays pass straight through them, so the same construction and solver still apply. From level 131 on, arrows get longer and snakier. Boards fill to a target `density` (75% early, 95% at the top). A second fill pass plugs gaps with arrows that are removed last: their own path may cross anything, but no cell of theirs sits in another arrow's path, so levels stay solvable.

## Styles

Settings → **Style** switches between **Classic** for adults (default: slim near-black arrows on white, a calm blue accent for the main button and hint glow, outlined controls) and **Playful** for kids (a bright colour per arrow on white, a violet accent, emoji hearts, filled pill buttons). Both use pure white backgrounds in light mode; dark mode is still available. It works with the light/dark theme setting. Everything that differs lives in one `Look` in `ui/Theme.kt`, so the screens are shared.

## Tutorial levels

`app/src/main/assets/levels/N.json` is generated from the ASCII designs in `tools/tutorial_levels.py` (uppercase = head, lowercase = body). The script also checks that every level is solvable:

```bash
python3 tools/tutorial_levels.py app/src/main/assets/levels
```

## Sounds

`app/src/main/res/raw/{exit,block,win}.ogg` are synthesized placeholder beeps. To replace one, drop in a short mono `.ogg` with the same name (under 1 s, under 100 KB). Ogg Vorbis is SoundPool's most reliable format:

```bash
ffmpeg -i my_sound.wav -ac 1 -c:a libvorbis -q:a 4 app/src/main/res/raw/exit.ogg
```

## Ads (AdMob)

The real IDs (`ARROW_ADMOB_*`) live in `~/.gradle/gradle.properties`, outside the repo; the project's `gradle.properties` holds Google's test IDs. Release builds use the real ones and warn if they are missing. **Debug builds always use Google's public test IDs**, so you can never click your own real ads, which would get the account flagged.

| Unit | Where |
|---|---|
| Banner | Under the board on the puzzle screen |
| Interstitial | On **Next** after every 3rd cleared level, at most once per 90 s |
| Rewarded | "Watch an ad for +1 hint" when out of hints, also offered in the out-of-hearts dialog |
| App open | When returning to the app (warm start), never on cold launch or right after another ad |

Ads only load after Google's consent form (UMP) allows them. Settings shows **Ad privacy settings** where the region requires it. Before publishing, add the app's store listing to the AdMob app entry and set up the EU consent message in AdMob → Privacy & messaging.
