# Perimeter Defense: Mine & Match (v1.0.0)

Android tactical puzzle game combining Minesweeper, Match-3, and tower defense.

## Gameplay & Features

- **6×6 Tactical Energy Matrix**: guaranteed playable opening Match-3 swipe, uniform mine placement, adjacent-mine counters, and long-press tactical mine flagging (`FLAGGED`).
- **Dual Modes & Direct Swipes**: drill hidden tiles, capture mines (+25 CR and launch them at the closest enemy), or swipe adjacent revealed ammo tiles directly to trigger Match-3 combos and cascades.
- **Tactical Battlefield Radar**: animated vector battlefield with enemy waves (Scouts, Raiders, Armored, Bosses), turret beams, impact sparks, and shield telemetry.
- **Hangar Upgrades & Economy**: spend earned credits (`CR`) on permanent run upgrades (Mortar, Cryo, Tesla, Base Shield) or tactical boosters (Free Scanner, Airstrike `40 CR`, Emergency Repair `30 CR`).
- **Pause & Tactical Guide**: built-in pause dialog with weapon legend, sound toggle, Hangar shortcut, and instant restart.
- **Architecture & Persistence**: pure Kotlin domain engine (`GridEngine`, `CombatEngine`), `PerimeterGameViewModel` (`StateFlow`), versioned DataStore persistence (game state, upgrades, best wave, audio setting), and bounded procedural PCM audio (`TacticalSoundSynth`).

## Tech Stack

- Kotlin 2.0.21, Jetpack Compose / Material 3, Lifecycle ViewModel
- Jetpack DataStore Preferences
- Min SDK 26, Target SDK 35, JDK 17, AGP 8.7.3, Gradle 8.9
- GitHub Actions CI (`testDebugUnitTest`, `lintDebug`, `assembleDebug`)

## Build & Release

- **Debug test & build**: `gradle testDebugUnitTest lintDebug assembleDebug`
- **Signed Release AAB**: export `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`, then run `gradle bundleRelease`.

## Package

`com.giksgik.perimeterdefense`
