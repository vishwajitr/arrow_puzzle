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
| Banner | Under the board on the puzzle screen (standard). On Home it is **collapsible** (opens larger, the player collapses it); AdMob forbids collapsible banners during gameplay |
| Interstitial | On **Next** after every 3rd cleared level from level 20 (ad-free start), at most once per 60 s |
| Rewarded | "Watch an ad for a hint" when out of hints (shows the hint at once; after 20 s without a correct move it pulses as "Stuck?"); "Watch ad: +1 heart" in the out-of-hearts dialog; "Skip this level" (ad, or 2 hints) |
| App open | When returning to the app (warm start), never on cold launch or right after another ad |

**Skip level** (from level 11): offered after 60 s without a correct move, or in the out-of-hearts dialog. Pay with a rewarded ad or 2 hint tokens (a hint costs 1, so the hint stays the cheaper way through). A skipped level counts as cleared with 0 stars and can be replayed for stars. No interstitial follows a skip.

**Remove ads** (`data/Billing.kt`): a one-time Play purchase, product id `remove_ads`. It turns off the banner, interstitials and app-open ads; rewarded videos stay because they are opt-in. Play's purchase list is re-read on every start and resume (that is the restore; Settings also has **Restore purchases**) and cached in DataStore for offline play. Refunds turn ads back on.

**Analytics** (Firebase project `arrows-puzzle-84166`): `app/google-services.json` is needed to build and is kept out of git (download it from Firebase → Project settings → Android app `com.vishwajit.arrowescape`). Firebase logs retention (first_open, session_start, engagement), `ad_impression` once AdMob is linked to Firebase, and `in_app_purchase` itself. The app adds `level_start`, `level_end` (success, stars, mistakes), `hint_used` and `revive` (source token or ad), in `GameViewModel`. To watch events live: `adb shell setprop debug.firebase.analytics.app com.vishwajit.arrowescape`, then Firebase → DebugView.

Ads only load after Google's consent form (UMP) allows them. Settings shows **Ad privacy settings** where the region requires it. Before publishing, add the app's store listing to the AdMob app entry and set up the EU consent message in AdMob → Privacy & messaging.
