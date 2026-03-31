# Build Requirements & Dependencies

> Linked from [CLAUDE.md](../CLAUDE.md)

## Android Build Requirements

**JDK 17 is required** (JDK 21+ breaks Gradle 8.3). `android/gradle.properties` is committed and contains shared settings — do **not** put `org.gradle.java.home` there. Instead, put your machine-specific JDK path in `~/.gradle/gradle.properties` (macOS/Linux) or `%USERPROFILE%\.gradle\gradle.properties` (Windows). Create the file if it doesn't exist:

```properties
# macOS — ~/.gradle/gradle.properties
org.gradle.java.home=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home

# Windows — %USERPROFILE%\.gradle\gradle.properties
org.gradle.java.home=C:\\Program Files\\jdk-17.0.18+8
```

**Android SDK** location must be set in `android/local.properties` (not committed — create it if missing):

```properties
# macOS
sdk.dir=/Users/<you>/Library/Android/sdk

# Windows
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Build Constraints

- Gradle wrapper: 8.3, AGP: 8.1.1. Do not upgrade — RN 0.73's Gradle plugin has Kotlin warnings that become errors under Gradle 8.11+.
- `androidx.core` and `androidx.transition` are pinned in `android/build.gradle` via `resolutionStrategy` to stay within `compileSdk 34`.
- The `native_modules.gradle` path in `android/settings.gradle` and `android/app/build.gradle` points to `node_modules/react-native/node_modules/@react-native-community/cli-platform-android/` (not root `node_modules/`) due to npm hoisting.

## Physical Device (Windows + tablet)

1. Enable Developer Options on device -> turn on USB Debugging
2. Connect via USB and accept the "Allow USB debugging?" prompt
3. Verify: `adb devices` — must show `device` (not `unauthorized`)
4. Run Metro + `run-android` as normal

## Emulator (macOS or Windows without tablet)

Create and start an AVD via Android Studio's Device Manager before running `run-android`. React Native will auto-detect the running emulator.

## Known Dependency Quirks

**Do not upgrade these packages** — pinned to last versions compatible with RN 0.73 (which lacks `BaseReactPackage`):

- `react-native-screens@3.35.0` (3.36+ breaks)
- `react-native-safe-area-context@4.10.0` (4.11+ breaks)
- `react-native-blob-util@0.19.11` (0.21+ breaks)
- `@react-navigation/native@6.x` + `@react-navigation/native-stack@6.x` (v7 requires screens 4.x)
- `@react-native-async-storage/async-storage@1.23.1` (v2+ requires Kotlin 2.1.0 via KSP; project uses Kotlin 1.8.0)
- `react-native-gesture-handler@~2.14.0` + `react-native-reanimated@~3.6.0` (required by navigation; do not upgrade independently)

## Git / GitHub

`android/app/build/` and `android/local.properties` are gitignored — never commit them. `android/gradle.properties` **is** committed (shared settings only). Each developer's JDK path goes in `~/.gradle/gradle.properties` (never committed). The debug APK is 164MB and will be rejected by GitHub.
