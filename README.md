# Samsung Feature Extension / 三星功能扩展

![Samsung Feature Extension Icon](docs/icon.png)

Unofficial LSPosed module for Samsung / One UI devices.  
这是一个面向 Samsung / One UI 设备的非官方 LSPosed 模块。

- App name / 应用名: `三星功能扩展`
- Package name / 包名: `com.samsung.feature.extension`
- Latest version / 当前版本: `1.25`
- GitHub: [fvffv/samsung-feature-extension](https://github.com/fvffv/samsung-feature-extension)

## 1.25 更新记录 / Release Notes

### 新增与修复 / Added and Fixed

- 新增模块界面中文 / English 切换，并持久化用户选择；相机设置、设备健康、充电提示音等动态信息也会按当前语言显示。
- 新增文本通话设置：可自定义两种文本通话开场语，并可选择在文本通话时播放对方声音，同时保留语音转文字显示。
- 补充相机视频码率页面“检测方法”说明的英文翻译，修复设备健康 `Thermal Status` 和充电提示音页面的中文残留。
- 继续保留 Expert RAW、WebDAV、音频橡皮擦、相机 8K60 / UHD240、SystemUI 音频橡皮擦等兼容适配。

### 删除 / Removed

- 删除 Bixby OpenAI 接入功能及其配置界面、Provider、Hook 和客户端代码；模块界面不再显示该入口。
- 清理音频橡皮擦排查阶段的临时调试代码，保留通用日志开关供必要时诊断。

## 中文说明

### 功能

- 三星“我的文件”WebDAV 支持
  - 在“添加网络存储”中增加 WebDAV 入口，兼容旧版和新版“我的文件”相关实现。
  - 支持添加、显示、进入、目录浏览、新建文件夹、删除、上传、下载、复制、移动等常用操作。
  - 修复新版“我的文件”中网络存储菜单、WebDAV 入口、复制/移动/上传下载、完成提示跳转等兼容问题。
  - 修复 Android 16 对 `http://` WebDAV 的明文流量限制：目录浏览、上传和下载均走兼容的流式传输；建议服务器可用时优先使用 HTTPS。

- Expert RAW 增强
  - 为 Galaxy Expert RAW 解锁隐藏的 200MP 超高分辨率能力，主要面向 Ultra 机型。
  - 针对 S23 Ultra 兼容新版 Expert RAW，规避不支持的 24MP 快捷设置资源导致的启动闪退。
  - 同时兼容旧版 `H1.g / B2.a` 与 Expert RAW 5.0.08.2 新版 `R1.g / B0.g` 特征接口。
  - 为 S24 Ultra（`SM-S928* / e3*`）恢复 200MP RAW 选项，并保留旧版 Hook 路径。

- One UI 8.5 音频橡皮擦
  - 为 S24 Ultra 以下的 Galaxy S 机型开放视频编辑工作室的音频橡皮擦入口和 VEKit 功能门控。
  - 内置并校验 S24 Ultra 的原生框架、完整音频分离模型及 SNAAC 组件，支持音频分析和“保存副本”导出流程。
  - 解锁 SystemUI 16 快速面板的原生音频橡皮擦；入口只会在系统检测到符合条件的媒体音频播放时自动出现，不会强制常驻。
  - 快速面板沿用系统自带的播放监听和 SoundAlive `VOICE_BOOST_EFFECT` 控制链，支持强度调节及人声模式。
  - S24 Ultra 及更新 Ultra 机型继续使用系统原生实现，不替换其更新版本的 VEFramework。

- 三星相机专业视频增强
  - 解锁专业模式 4K120、FHD120 下的 HDR10+ 选项。
  - 解锁专业模式 8K 24/30/60fps 录制与对应 HDR10+ 选项。
  - 解锁慢动作 UHD 240fps 录制能力。
  - 为超级稳定菜单开放“水平锁定”；仅在原生超级稳定可用的分辨率下启用。
  - 新增视频码率设置页面，可读取默认码率，并为 8K、4K、FHD 等录制规格设置自定义码率。
  - 部分模式开启 HDR10+ 后录制时可能无法实时预览 HDR 效果，保存后可在相册查看实际 HDR10+ 效果。
  - 8K60 与 UHD240 对性能和散热要求较高，实际帧率和流畅度取决于设备芯片、温控和散热状态。

- NFC 息屏刷卡
  - 单独提供开关，控制是否允许 NFC 在息屏状态下按亮屏解锁状态处理刷卡请求。
  - 默认关闭，需要时可在模块界面启用。

- One UI 主屏幕图标与名称自定义
  - 在 One UI 主屏幕设置中注入入口。
  - 支持搜索应用、选择图片、裁剪图片、设置图标形状和圆角参数。
  - 支持按应用单独设置桌面显示名称。
  - 支持字体设置菜单，可设置字体文件、粗体、斜体、颜色、渐变等名称样式。
  - 启用渐变色后按渐变字体显示，不再同时使用单独字体颜色。
  - 支持快速重启 One UI 主屏幕。
  - 已避免 `onDraw` 或逐帧刷新路径，降低桌面卡顿和文件夹图标复用污染风险。

- 系统字体自定义
  - 在系统“字体大小和样式 / 字体风格”页面加入本地字体入口。
  - 支持导入多个 `.ttf` 字体文件。
  - 导入后的字体会出现在原版字体列表中，可切换、删除和管理。
  - 目前仅支持 TTF 字体文件。

- 自定义指纹图标
  - 替换系统指纹验证位置显示的指纹图标，覆盖锁屏和系统安全验证弹窗等路径。
  - 支持 Lottie JSON 动画和静态图片。
  - 内置 9 个默认 Lottie 素材，默认素材不可删除。
  - 支持从本地添加素材，列表中循环实时预览，长按可删除用户添加的素材。
  - 支持设置动画播放一次或循环播放。
  - 界面内提供 [LottieFiles 免费动画](https://lottiefiles.com/free-animations) 链接。

- 触控高采样率
  - 提供开关，尝试为手指触控发送三星 TSP 高采样策略。
  - 界面显示实时刷新率。
  - 熄屏后亮屏会再次尝试触发高采样。
  - 充电时系统可能限制高采样生效，界面中已加入提醒。

- 全局旁路供电
  - 基于 Game Booster 的“游戏时暂停 USB PD 充电”机制，提供全局旁路供电开关。
  - 关闭后恢复系统默认行为。
  - 支持桌面长按应用图标快捷操作，一键切换旁路供电开关。

- 充电显示与提示音
  - 自定义 SystemUI 充电显示内容，支持变量模板。
  - 支持修改插入和拔出充电器时的提示音。
  - 提供按钮用于读取并播放当前系统充电插入 / 拔出提示音。
  - 锁屏和息屏显示路径分开适配。

- 设备健康管理
  - 查看 Samsung Device Health Manager Service 的温控、电池、异常检测和后台限制相关状态。
  - 提供常用控制开关入口。
  - 兼容 SDHMS 1.0.0 versionCode 36 的 `k5.m5 / e5.b` 接口，同时保留旧版 `Q1.j2 / N1.b` 路径。

- 文本通话
  - 自定义文本通话的两种开场语。
  - 可单独开启或关闭文本通话时播放对方声音；关闭时仍显示对方语音转文字。

- 界面语言
  - 模块设置界面支持中文和 English 切换，并记住上次选择。
  - 动态的相机检测、Thermal Status、充电提示音状态等内容会随界面语言切换。

- 应用分身列表扩展
  - 尝试将应用分身的可用列表扩展为已安装的非系统应用。
  - 该功能依赖三星系统实现，部分系统版本可能存在行为差异。

- 日志开关
  - 模块主界面提供日志输出开关。
  - 默认关闭，减少 LSPosed 日志和本地诊断日志刷屏；排查问题时再临时开启。

- GitHub 更新检查
  - 主界面内显示当前版本和 GitHub Releases 最新版本。
  - 可检测是否为最新版，并提供 GitHub 仓库入口。

### 兼容性

- 需要 Samsung / One UI 设备。
- 需要 LSPosed。
- 当前主要在 One UI 8.0 / 8.5 环境测试；音频橡皮擦兼容层面向 One UI 8.5，请尽量保持系统组件、三星应用和模块本身为最新版本。
- 不同机型、地区包和三星应用版本可能存在差异，隐藏功能不保证在所有设备上都能稳定工作。

### 构建

构建脚本会自动处理中文或其他非 ASCII 工作路径：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-v77.ps1
```

最终 APK：

```text
.\SamsungFeatureExtension-v1.25-ExpertRAW-SDHMS-WebDAV-AudioEraser-OneUI85-SystemUI-Camera.apk
```

### 安装

```powershell
adb install --user 0 -r .\SamsungFeatureExtension-v1.25-ExpertRAW-SDHMS-WebDAV-AudioEraser-OneUI85-SystemUI-Camera.apk
```

### 说明

- `build/` 目录已被 git 忽略，不会上传到仓库。
- 当前仓库保留实际工作中的项目结构，没有改写成 Gradle 工程。
- 这是非官方模块，涉及 LSPosed hook 和三星隐藏能力，使用前请自行评估风险。

### 主要文件

- [AndroidManifest.xml](AndroidManifest.xml)
- [build-v51.ps1](build-v51.ps1)
- [SettingsActivity.java](src/main/java/com/samsung/feature/extension/SettingsActivity.java)
- [MyFilesWebDavHook.java](src/main/java/com/samsung/feature/extension/MyFilesWebDavHook.java)
- [GalaxyRaw200MpHook.java](src/main/java/com/samsung/feature/extension/galaxyraw200mp/GalaxyRaw200MpHook.java)
- [CameraProVideoHdr10Hook.java](src/main/java/com/samsung/feature/extension/camera/CameraProVideoHdr10Hook.java)
- [CameraOneUi85VideoUnlockHook.java](src/main/java/com/samsung/feature/extension/camera/CameraOneUi85VideoUnlockHook.java)
- [VideoEditorAudioEraserHook.java](src/main/java/com/samsung/feature/extension/videoeditor/VideoEditorAudioEraserHook.java)
- [SystemUiAudioEraserHook.java](src/main/java/com/samsung/feature/extension/audioeraser/SystemUiAudioEraserHook.java)
- [LanguageManager.java](src/main/java/com/samsung/feature/extension/LanguageManager.java)
- [TextCallGreetingHook.java](src/main/java/com/samsung/feature/extension/phone/TextCallGreetingHook.java)
- [TextCallGreetingSettingsActivity.java](src/main/java/com/samsung/feature/extension/TextCallGreetingSettingsActivity.java)
- [TouchSamplingHook.java](src/main/java/com/samsung/feature/extension/touchsampling/TouchSamplingHook.java)
- [PassThroughChargingHook.java](src/main/java/com/samsung/feature/extension/passthrough/PassThroughChargingHook.java)
- [ChargingStyleHook.java](src/main/java/com/samsung/feature/extension/charging/ChargingStyleHook.java)
- [FingerprintStyleHook.java](src/main/java/com/samsung/feature/extension/fingerprint/FingerprintStyleHook.java)
- [SettingsCustomFontHook.java](src/main/java/com/samsung/feature/extension/SettingsCustomFontHook.java)
- [LauncherIconCustomizerHook.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerHook.java)

## English

### 1.25 Release Notes

#### Added and Fixed

- Added a persistent Chinese / English switch for the module UI; dynamic camera, Device Health, and charging-sound values follow the selected language.
- Added Text Call settings for customizing both greeting messages and choosing whether the other party's voice is played while the transcript remains visible.
- Translated the camera bitrate detection instructions and removed remaining Chinese text from `Thermal Status` and charging-sound screens.
- Retained the Expert RAW, WebDAV, Audio Eraser, camera 8K60 / UHD240, and SystemUI Audio Eraser compatibility work.

#### Removed

- Removed the Bixby OpenAI integration, including its settings screen, Provider, Hook, client, and configuration classes.
- Removed temporary Audio Eraser investigation/debug code; the general log switch remains available for diagnostics.

### Features

- Samsung My Files WebDAV support
  - Adds WebDAV to the network storage flow, with compatibility paths for older and newer My Files builds.
  - Supports adding accounts, browsing directories, creating folders, deleting, uploading, downloading, copying, and moving files.
  - Includes compatibility fixes for menu injection, existing WebDAV entries, transfer actions, and completion jump prompts.
  - Fixes Android 16 cleartext restrictions for `http://` WebDAV with compatible streaming transfers for browsing, uploads, and downloads; use HTTPS when the server supports it.

- Expert RAW enhancements
  - Unlocks hidden 200MP ultra-high-resolution support for Galaxy Expert RAW, mainly for Ultra devices.
  - Adds S23 Ultra guards for newer Expert RAW builds where unsupported 24MP quick-setting resources can crash startup.
  - Supports both the legacy `H1.g / B2.a` path and the `R1.g / B0.g` feature API used by Expert RAW 5.0.08.2.
  - Restores the 200MP RAW option on S24 Ultra (`SM-S928* / e3*`) while retaining older hooks.

- One UI 8.5 Audio Eraser
  - Enables Samsung Video Editor Studio's Audio Eraser entry and VEKit feature gates on Galaxy S devices below S24 Ultra.
  - Bundles and verifies the S24 Ultra native framework, complete source-separation models, and SNAAC component needed for analysis and Save Copy export.
  - Unlocks SystemUI 16's native quick-panel Audio Eraser. Its banner appears only while SystemUI detects eligible media playback; it is not forced to stay visible.
  - Keeps the built-in playback monitor and SoundAlive `VOICE_BOOST_EFFECT` path for strength and voice-focus controls.
  - S24 Ultra and newer Ultra models continue using their native system implementation and newer VEFramework.

- Samsung Camera pro video enhancements
  - Unlocks HDR10+ for Pro Video 4K120 and FHD120.
  - Unlocks Pro Video 8K 24/30/60fps and HDR10+ for those modes.
  - Adds slow-motion UHD 240fps support.
  - Enables Horizon Lock in the Super Steady menu, limited to resolutions where stock Super Steady is available.
  - Adds a video bitrate page for reading default bitrates and overriding 8K, 4K, and FHD recording bitrates.
  - Some HDR10+ modes may not preview HDR during recording; the saved video should be checked in Gallery.
  - 8K60 and UHD240 are performance and thermal heavy, so actual results depend on the device, throttling, and cooling.

- NFC screen-off card tapping
  - Adds a dedicated switch for allowing NFC card requests while the screen is off.
  - Disabled by default.

- One UI Home icon and label customization
  - Injects an entry into One UI Home settings.
  - Supports app search, image selection, cropping, icon shape, and corner radius settings.
  - Supports per-app launcher label overrides.
  - Adds a font settings menu for label font file, bold, italic, color, and gradient effects.
  - When gradient is enabled, labels use gradient text instead of a separate solid color.
  - Includes quick restart for One UI Home.
  - Avoids `onDraw` and per-frame refresh paths to reduce launcher lag and folder icon reuse pollution.

- System font customization
  - Adds a local font entry to Samsung Settings font style page.
  - Supports importing multiple `.ttf` files.
  - Imported fonts appear in the original font list and can be selected, deleted, and managed.
  - TTF only for now.

- Custom fingerprint icon
  - Replaces the fingerprint icon shown by system biometric UI paths, including lock screen and authentication dialogs.
  - Supports Lottie JSON animations and static images.
  - Bundles 9 default Lottie materials that cannot be deleted.
  - Supports importing user materials, looping live preview in the list, and long-press deletion for user-added materials.
  - Supports play-once and loop playback modes.
  - Links to [LottieFiles free animations](https://lottiefiles.com/free-animations).

- Touch high sampling rate
  - Adds a switch that attempts to send Samsung TSP high-sampling policy for finger touch.
  - Shows live refresh rate in the UI.
  - Tries to reapply after screen-off to screen-on transitions.
  - Charging may prevent high sampling from taking effect, and the UI notes this limitation.

- Global pass-through charging
  - Uses the Game Booster “pause USB PD charging while gaming” mechanism as a global switch.
  - Disabling the switch restores the system default behavior.
  - Adds an app shortcut for toggling pass-through charging from the launcher long-press menu.

- Charging display and sounds
  - Customizes SystemUI charging text with variable templates.
  - Supports custom plug-in and unplug sounds.
  - Includes buttons for reading and previewing the current system charging sounds.
  - Handles lock screen and AOD display paths separately.

- Device Health Manager
  - Shows Samsung Device Health Manager Service thermal, battery, anomaly detection, and background restriction state.
  - Provides common control switches.
  - Supports the `k5.m5 / e5.b` API in SDHMS 1.0.0 versionCode 36 while retaining the older `Q1.j2 / N1.b` path.

- Text Call
  - Customizes both Text Call greeting messages.
  - Adds an independent switch for playing the other party's voice during Text Call while keeping the speech transcript available.

- Interface language
  - Supports Chinese and English in the module settings UI and remembers the selected language.
  - Dynamic camera detection, Thermal Status, and charging-sound values follow the selected language.

- Dual app list extension
  - Attempts to extend the dual app candidate list to installed non-system apps.
  - This depends on Samsung system behavior and may vary by version.

- Log switch
  - Adds a main-page log output switch.
  - Disabled by default to reduce LSPosed and local diagnostic log spam.

- GitHub update check
  - Shows current version and latest GitHub Releases version in the main UI.
  - Provides update status and a GitHub repository link.

### Compatibility

- Samsung / One UI device required.
- LSPosed required.
- Mainly tested on One UI 8.0 / 8.5. The Audio Eraser compatibility layer targets One UI 8.5; keep Samsung apps, system components, and the module itself updated.
- Hidden Samsung features may vary by model, region, and app version.

### Build

The build script handles non-ASCII checkout paths automatically:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-v77.ps1
```

Final APK:

```text
.\SamsungFeatureExtension-v1.25-ExpertRAW-SDHMS-WebDAV-AudioEraser-OneUI85-SystemUI-Camera.apk
```

### Install

```powershell
adb install --user 0 -r .\SamsungFeatureExtension-v1.25-ExpertRAW-SDHMS-WebDAV-AudioEraser-OneUI85-SystemUI-Camera.apk
```

### Notes

- `build/` is ignored and is not committed to the repository.
- This repository keeps the real working project layout and does not rewrite it into a Gradle project.
- This is an unofficial module that relies on LSPosed hooks and hidden Samsung behavior. Use it at your own risk.

## License

No separate license file has been added yet.
