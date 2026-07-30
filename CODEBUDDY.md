# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.



##  目标概述

本应用的目标是成为一个**诺基亚风格的全功能安卓桌面启动器（Launcher）**，在J2ME-Loader的基础上修改而来，并作为系统默认 Home 桌面运行。核心诉求：

1. **外观**：模仿诺基亚 S40/S60 风格——顶部状态栏 + 中间内容区 + 底部软键栏。
2. **融合 J2ME 与安卓**：将 J2ME-Loader 已安装的 JAR 应用与安卓原生应用**视觉上无缝融合**，但 JAR 只在「百宝箱」中展示，不混入功能表。
3. **物理按键优先**：方向键导航 + 左右软键 + 确认键，所有可选项都可被方向键选中并高亮，最大程度模拟真机。
4. **功能对等**：桌面上的联系人、信息、通话记录等入口映射为安卓系统功能。
5. **真实系统信息**：顶栏显示真实信号（含双卡）、WiFi、电量、运营商、时间。
6. **通知展示**：读取系统通知并展示在桌面指定区域，支持滚动与清除。
7. **按键音**：按下物理按键时播放提示音。
8. **可配置**：提供桌面设置入口 + 复用 J2ME-Loader 自身设置入口；快捷栏可编辑。


## 开发重心与入口说明（重要）

**本仓库的开发重心已经转移到「诺基亚桌面」，而不是原本的 J2ME-Loader 主界面。**

- 真正的桌面（Home / Launcher）入口是 **`ru.playsoftware.j2meloader.nokia.NokiaDesktopActivity`**，它在 `AndroidManifest.xml` 中同时声明了 `LAUNCHER` + `HOME` + `DEFAULT` 三个 category，即应用图标入口和按 Home 键都会进入这个诺基亚桌面。
- 原本的 **`MainActivity`** 是 J2ME-Loader 自带的启动器 / 文件选择器 / 应用列表界面，**它不再是本应用的主界面，也不是开发重点**。它只是作为「百宝箱」里启动 JAR 应用、以及复用其设置入口的底层壳存在。
- 因此，调试、截图、功能验证时，应当启动 / 操作的是 `NokiaDesktopActivity`，而不是 `MainActivity`。例如：
  ```bash
  adb shell am start -n io.github.cctyl.nokia.debug/ru.playsoftware.j2meloader.nokia.NokiaDesktopActivity
  ```
  或直接模拟按 Home 键进入桌面：
  ```bash
  adb shell input keyevent KEYCODE_HOME
  ```
- 新增功能、改 UI、加逻辑时，优先在 `app/src/main/java/ru/playsoftware/j2meloader/nokia/` 目录下的诺基亚桌面相关代码中进行，而非 J2ME-Loader 原有的 `MainActivity` 等模块。


## J2ME-Loader介绍

J2ME-Loader is a J2ME (MIDP/CLDC) emulator for Android. It runs legacy 2D/3D Java ME games by reimplementing the J2ME APIs on top of the Android runtime and translating MIDlet bytecode to run on Android. This repo is a fork of J2meLoader. It is a standard multi-module Gradle/Android project (Groovy DSL, AGP 8.5.1, Gradle 8.7). A skill documenting the local Gradle network/signing fixes lives at `.claude/skills/android-gradle-build` (read it before changing build config or signing).



## 调试与安装

使用adb 安装应用，并且以debug模式来安装，这样编译速度快。

调试方面，使用adb截图理解，再使用adb 模拟点击来操作。




## 重要事项

在应该加日志的地方，都要加上日志输出，尽可能多的加日志。方便排查问题。

没有我的允许，不能私自提交git。


## Android 4.4 (API 19) 兼容性踩坑经验

本应用需兼容到 Android 4.4 (API 19)。4.4 设备实测（序列号 `4a24ecf`，240×320）暴露了多处以高版本不崩、低版本必崩的问题，修复后沉淀经验如下：

1. **矢量图 / drawable 膨胀**：4.4 的 `Resources` 在膨胀含特定 `vectorDrawables` 或 drawable 的布局时易抛 `InflateException` / `invalid drawable`。涉及顶栏、桌面背景等图形资源时，优先用兼容写法（如 `AppCompat` 矢量、或自定义 `Drawable`）；构建侧已开启 `vectorDrawables.useSupportLibrary`。
2. **`android.telephony.SubscriptionManager` 是 API 22+ 才有的类**。`StatusBarController` 中对该类的强制类型转换必须用 `Build.VERSION.SDK_INT >= 22` 守卫，否则 4.4 上 `NoClassDefFoundError`。其余使用点（双卡监听、`getPhoneCount` 等）也须守卫并降级单卡。
3. **设备管理员激活页 `ACTION_ADD_DEVICE_ADMIN` 不能用 `FLAG_ACTIVITY_NEW_TASK` 启动**。4.4（及部分 ROM）的 `DeviceAdminAdd` 会直接拒绝：`W/DeviceAdminAdd: Cannot start ADD_DEVICE_ADMIN as a new task`，导致锁屏按钮「点击无反应」（激活页不弹出）。该 intent 应从前台 Activity 上下文启动（**不加** NEW_TASK）；只有当 `context` 非 Activity 时才补 NEW_TASK 兜底（实际调用方均为前台 Activity，见 `NokiaLockScreen`）。

通用原则：所有 API 22+ 的类/方法引用都要 `SDK_INT` 守卫；Dalvik 验证器对运行时不执行到的高版本类引用只会打 `VFY Could not find class '...'` **无害告警**，不算崩溃。低版本设备（尤其 4.4）建议用「修一处→构建→装到 4a24ecf 实测」的迭代方式，以设备真实崩溃为准逐个修，而非盲目猜测。

## 分辨率适配

本应用需要适配三种分辨率

- 240 * 320 （重要）
- 320 * 480 （重要）
- 现代 16:9 及以上比例的长条形屏幕 （次要）


## 设备说明
通过tcpip链接的设备是 320*480分辨率的，可以直接通过adb安装应用。
通过usb链接的，adb查看名为jz5dauzlu8euw4e6 的设备，是小米设备，是 现代 16:9 及以上比例的长条形屏幕，不支持直接通过adb安装应用，你推送到 `adb -s jz5dauzlu8euw4e6 push "d:/project/nokia_desktop/app/build/outputs/apk/open/debug/J2ME_Loader-1.8.2-open-debug.apk" /sdcard/Download/J2ME_Loader-open-debug.apk` 设备文件中即可。我会来安装。
这个设备当然也支持adb 查看日志等操作，只是不支持直接安装。


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

**Two-process isolation.** `MainActivity` (the original J2ME-Loader launcher, file picker, app list) runs in the default process — note that it is **no longer the app's main UI**; the actual Home/desktop entry is `NokiaDesktopActivity` (see 开发重心与入口说明). The game itself runs in a separate `:midlet` process via `MicroActivity` (`android:process=":midlet"`, see `AndroidManifest.xml`), so a crashing MIDlet does not take down the host app. `com.nokia.mid.ui.NotificationBroadcastReceiver` also lives in `:midlet`.

**Native 3D via NDK.** `app/src/main/cpp` builds two shared libraries through ndkBuild (`Android.mk`): `javam3g` (Mascot Capsule 3D `m3g` over OpenGL ES 1.1, providing `javax.microedition.m3g`) and `micro3d` (Micro3D V3 engine bindings). This native code is why the project pins the older NDK 22.1.7171670 and why Gradle needs the NDK installed.

**App shell in `ru.playsoftware.j2meloader`.** The Android-side UI and services: `MainActivity` (legacy J2ME-Loader launcher, not the main UI anymore), `NokiaDesktopActivity` (the real Home/desktop — the current development focus), `ConfigActivity`, `SettingsActivity`, `KeyMapperActivity`, Room database (per-app configs), file picker, and `storage.DocumentProvider`. The Nokia desktop code lives under `ru.playsoftware.j2meloader.nokia.*`. `com.*`/`mmpp.*` hold Nokia UI extensions (`com.nokia.mid.ui`) and Mascot Capsule helpers.

**Product flavors (`app/build.gradle`).** `play`/`open`/`fdroid`/`dev` are the full emulator (`FULL_EMULATOR=true`), differing only in distribution channel, `versionNameSuffix`, and proguard files; `open` is the non-Play build and the one to use for local development. `midlet` is special: `FULL_EMULATOR=false`, and instead of building the emulator it builds a standalone Android APK *from a J2ME app's sources* (read from `src/midlet/resources/MIDLET-META-INF/MANIFEST.MF`). `dev` calls `generateVersionCode()` (git rev-list) at configuration time — a non-git working copy falls back to version code 1 (already patched in `app/build.gradle`).

**Release signing.** `signingConfigs.release` reads `keystore.properties` (when not running on the Bitrise CI). Debug builds use the default debug key; release builds need the local `test.jks`.

