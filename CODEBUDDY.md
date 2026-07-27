# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

J2ME-Loader is a J2ME (MIDP/CLDC) emulator for Android. It runs legacy 2D/3D Java ME games by reimplementing the J2ME APIs on top of the Android runtime and translating MIDlet bytecode to run on Android. This repo is a fork of J2meLoader. It is a standard multi-module Gradle/Android project (Groovy DSL, AGP 8.5.1, Gradle 8.7). A skill documenting the local Gradle network/signing fixes lives at `.claude/skills/android-gradle-build` (read it before changing build config or signing).

## Common commands

Build a release APK (recommended local flavor `open`):
```
.\gradlew.bat assembleOpenRelease -x lint
```
The `-x lint` flag is needed because the project's Lint config can otherwise abort the build. Output: `app/build/outputs/apk/open/release/J2ME_Loader-*-open-release.apk`. Requires `keystore.properties` + `app/test.jks` (already present) and NDK 22.1.7171670.

Build a debug APK:
```
.\gradlew.bat assembleOpenDebug
```
Debug variant gets a `.debug` applicationId suffix and runs as `JL-Debug`. Use `installOpenDebug` to push to a connected device/emulator.

Run unit tests:
```
.\gradlew.bat testOpenDebugUnitTest
```
Instrumentation (on-device) tests: `.\gradlew.bat connectedOpenDebugAndroidTest`.

Clean and reconfigure:
```
.\gradlew.bat clean
.\gradlew.bat --stop
```

Other flavors: replace `Open` with `Play`/`Fdroid`/`Dev`/`Midlet` (e.g. `assemblePlayRelease -x lint`). The `dev` flavor computes a version code from git history at config time.

## Environment prerequisites (already configured in this checkout)

- `local.properties` points to the Android SDK (`sdk.dir`).
- `gradle.properties` sets a Java proxy (`127.0.0.1:7897`, Clash) and `org.gradle.java.home` to a **JDK 17 (Temurin/OpenJDK)**. Do NOT use GraalVM; do NOT use JDK 8/11 or a bare JRE.
- `settings.gradle` and `gradle-wrapper.properties` use Tencent/Aliyun mirrors + jitpack. Keep `jitpack.io` — many dependencies are `com.github.*` GitHub libraries.
- NDK version is pinned to `22.1.7171670` in `build.gradle` (`ext.NDK_VERSION`); install it via sdkmanager if missing.
- Release signing reads `app/test.jks` via `keystore.properties`. Both are git-ignored; do not commit them.

## Architecture

This is not a normal app — it is an emulator, so most of the "application logic" is a faithful reimplementation of the Java ME platform.

**Two Gradle modules.** `:app` is the emulator Android app. `:dexlib` (`com.android.dx`) is a fork of Android's `dx`/dexlib toolchain, compiled into the app and used at runtime to convert J2ME class files into Android-executable `.dex` so a MIDlet's own classes can be loaded and run on the ART runtime.

**J2ME API reimplemented in `javax.microedition.*`.** The largest source tree (`app/src/main/java/javax/...`, ~324 files) is the project's own implementation of the MIDP/CLDC classes — `MIDlet`, LCDUI (`Display`, `Canvas`, `Form`), RMS record store, media, networking, `m3g` (Mascot Capsule 3D), etc. A J2ME game's bytecode calls these classes, and the implementation bridges them to Android widgets, Canvas, and the native 3D libs. This package is the emulator's core; changes here directly affect game compatibility.

**Emulator core `org.microemu`.** A fork of the MicroEmu Java ME emulator handles class loading, the MIDlet lifecycle, and the event loop. `javax.microedition.shell.MicroActivity` (plus `MidletThread`/`MidletSystem`) is what actually starts and drives a MIDlet.

**Two-process isolation.** `MainActivity` (the launcher, file picker, app list) runs in the default process. The game itself runs in a separate `:midlet` process via `MicroActivity` (`android:process=":midlet"`, see `AndroidManifest.xml`), so a crashing MIDlet does not take down the host app. `com.nokia.mid.ui.NotificationBroadcastReceiver` also lives in `:midlet`.

**Native 3D via NDK.** `app/src/main/cpp` builds two shared libraries through ndkBuild (`Android.mk`): `javam3g` (Mascot Capsule 3D `m3g` over OpenGL ES 1.1, providing `javax.microedition.m3g`) and `micro3d` (Micro3D V3 engine bindings). This native code is why the project pins the older NDK 22.1.7171670 and why Gradle needs the NDK installed.

**App shell in `ru.playsoftware.j2meloader`.** The Android-side UI and services: `MainActivity`, `ConfigActivity`, `SettingsActivity`, `KeyMapperActivity`, Room database (per-app configs), file picker, and `storage.DocumentProvider`. `com.*`/`mmpp.*` hold Nokia UI extensions (`com.nokia.mid.ui`) and Mascot Capsule helpers.

**Product flavors (`app/build.gradle`).** `play`/`open`/`fdroid`/`dev` are the full emulator (`FULL_EMULATOR=true`), differing only in distribution channel, `versionNameSuffix`, and proguard files; `open` is the non-Play build and the one to use for local development. `midlet` is special: `FULL_EMULATOR=false`, and instead of building the emulator it builds a standalone Android APK *from a J2ME app's sources* (read from `src/midlet/resources/MIDLET-META-INF/MANIFEST.MF`). `dev` calls `generateVersionCode()` (git rev-list) at configuration time — a non-git working copy falls back to version code 1 (already patched in `app/build.gradle`).

**Release signing.** `signingConfigs.release` reads `keystore.properties` (when not running on the Bitrise CI). Debug builds use the default debug key; release builds need the local `test.jks`.

## Tips for working in this repo

- When fixing game-compatibility bugs, the relevant code is almost always under `javax.microedition.*` (API behavior) or `org.microemu` (runtime/lifecycle), not `ru.playsoftware.j2meloader` (which is just the Android shell).
- The `midlet` flavor is a porting tool, not the emulator; do not assume it shares the emulator's runtime behavior.
- `app/build.gradle` and `settings.gradle` are already tuned for the local network/JDK/signing. Before "fixing" dependency resolution or the Gradle distribution, consult the `android-gradle-build` skill to avoid breaking the working mirrors.
