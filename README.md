# Nokia Launcher

> 一个以诺基亚 S40 / S60 风格重塑的安卓桌面启动器（Launcher）
>
> 基于 [J2ME-Loader](https://github.com/nikita36078/J2ME-Loader) 改造，把 J2ME 应用与安卓原生应用融合在同一个诺基亚桌面上，并完整适配物理按键（方向键 / 左右软键 / 确认键）。

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84)](#)
[![Style](https://img.shields.io/badge/style-Nokia%20S40%2FS60-124191)](#)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](#)

---

## 那种熟悉的感觉，又回来了

```
   _______________________________
  |  [][]  中国移动       100%    |
  |                               |
  |     _______________           |
  |    |  > 信息        |         |
  |    |  > 联系人      |         |
  |    |  > 日历        |         |
  |    |  > 相册        |         |
  |    |  > 设置        |         |
  |    |__> 百宝箱 ______|         |
  |                               |
  |  [功能表]          [联系人]    |
  |_______________________________|
```

这不是一个普通启动器。它把诺基亚的功能表、软键栏、信号格，原样搬到了安卓上；
同时把 **J2ME-Loader 里安装的 JAR 应用** 收进「百宝箱」，让老游戏和安卓应用看起来像是同一类东西。

---

## 主要特性

- **诺基亚风格 UI**：顶部真实信号 / WiFi / 电量 / 时间状态栏，底部左右软键 + 中间键。
- **融合应用**：功能表第 1 页为系统功能（信息 / 联系人 / 通话记录 / 日历 / 相册 / 相机 / 设置 / 桌面设置），第 2 页起为安卓已装应用；JAR 应用统一收口到「百宝箱」。
- **桌面快捷栏**：可自由编辑的快捷方式，支持安卓应用与 JAR 混合摆放，图标即真实应用图标。
- **物理按键优先**：方向键在可选中项之间移动并高亮，确认键打开，左右软键映射到当前页面动作；所有可点击项均可被按键选中。
- **真实系统信息**：双卡信号、运营商名、WiFi、电量百分比、实时时间全部来自系统。
- **通知区**：读取系统通知并在桌面下半部滚动展示，支持清除。
- **按键音**：按下物理按键时播放系统按键音反馈。
- **成为默认桌面**：可作为系统 Home 桌面运行。

---

## 预览

真实设备截图将放置于 `docs/screenshot.png`。

```
   _______________________________
  |  ||   CHINA MOBILE  CHN UNICOM|
  |  ||   100%           87%      |
  |                               |
  |   [图] [乐] [视] [历] [夹]     |  <- 桌面快捷栏(可编辑)
  |   --------------------------- |
  |   [微] 微信       12:30        |
  |   [抖] 抖音                    |
  |   [地] 地图                    |
  |   ...滚动通知...               |
  |                               |
  |  [编辑]    [功能表]   [联系人] |
  |_______________________________|
```

---

## 构建与安装

环境要求（详见 `CODEBUDDY.md`）：

- Android SDK + **JDK 17**（不要用 JDK 8/11 或 GraalVM）
- NDK `22.1.7171670`
- 本地签名：`app/test.jks` + `keystore.properties`（已 git-ignore，切勿提交）

构建发行版 APK（推荐 `open` 渠道）：

```bash
.\gradlew.bat assembleOpenRelease -x lint
```

输出：`app/build/outputs/apk/open/release/J2ME_Loader-*-open-release.apk`

安装后设为默认桌面：进入系统「设置 → 主屏幕 / 默认启动器」，选择本应用。

---

## 设置入口说明

功能表内有两个入口，互不相同：

- **桌面设置**：本应用的设置（按键映射、快捷栏、通知、按键音、以及 J2ME-Loader 自身设置入口）。
- **设置**：安卓系统原生设置。

---

## 设计文档

完整的功能设计与实现路线见 [`docs/诺基亚桌面设计文档.md`](docs/诺基亚桌面设计文档.md)。

---

## 致谢

本项目是 [J2meLoader](https://github.com/NaikSoftware/J2meLoader) 的 fork。
感谢 [woesss](https://github.com/woesss)（[JL-Mod](https://github.com/woesss/JL-Mod) 作者）提供的开源 Mascot Capsule 实现。

## License

> Copyright 2017-2024 Nikita Shakarun.
> Licensed under the [Apache License, Version 2.0.](http://www.apache.org/licenses/LICENSE-2.0)
