# Perimeter Defense: Mine & Match

Android tactical puzzle prototype combining Minesweeper, Match-3, and tower defense.

## Tech stack

- Kotlin
- Jetpack Compose / Material 3
- Min SDK 26, Target SDK 35
- JDK 17, Android Gradle Plugin 8.7.3, Gradle 8.9
- GitHub Actions for CI and debug APK artifacts

## Current milestone: Core Grid

- 6×6 grid with a safe revealed center
- Hidden mines and adjacent-mine counts
- Drill and capture interaction modes
- Basic match detection for horizontal and vertical runs
- Single-screen tactical UI prototype

## Build

The GitHub Actions workflow runs unit tests and builds a debug APK on pushes to `main` or `milestone-1-core-grid`, pull requests to `main`, and manual dispatches.

Open **Actions → Android CI** to download the `perimeter-defense-debug` artifact after a successful run.

## Package

`com.giksik.perimeterdefense`

## Roadmap

1. Core grid and Match-3 resolution
2. Radar, enemies, and combat
3. Hangar, progression, and DataStore persistence
4. AdMob/UMP, Play Billing, privacy policy, release AAB
