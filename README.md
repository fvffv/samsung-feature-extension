# Samsung Feature Extension / 三星功能扩展

![Samsung Feature Extension Icon](docs/icon.png)

Unofficial LSPosed module for Samsung / One UI devices.  
这是一个面向 Samsung / One UI 设备的非官方 LSPosed 模块。

- App name / 应用名: `三星功能扩展`
- Package name / 包名: `com.samsung.feature.extension`
- Latest version / 当前版本: `1.2`
- GitHub: [fvffv/samsung-feature-extension](https://github.com/fvffv/samsung-feature-extension)

## 中文说明

### 功能

- 三星“我的文件”WebDAV 支持
  - 在“添加网络存储”中增加 WebDAV 入口，兼容旧版和新版“我的文件”相关实现。
  - 支持添加、显示、进入、目录浏览、新建文件夹、删除、上传、下载、复制、移动等常用操作。
  - 修复新版“我的文件”中网络存储菜单、WebDAV 入口、复制/移动/上传下载、完成提示跳转等兼容问题。

- Expert RAW 增强
  - 为 Galaxy Expert RAW 解锁隐藏的 200MP 超高分辨率能力，主要面向 Ultra 机型。
  - 针对 S23 Ultra 兼容新版 Expert RAW，规避不支持的 24MP 快捷设置资源导致的启动闪退。
  - 保留 S24 Ultra 已验证可用路径。

- 三星相机专业视频增强
  - 解锁专业模式 4K120、FHD120 下的 HDR10+ 选项。
  - 解锁专业模式 8K 24/30/60fps 录制与对应 HDR10+ 选项。
  - 解锁慢动作 UHD 240fps 录制能力。
  - 新增视频码率设置页面，可读取默认码率，并为 8K、4K、FHD 等录制规格设置自定义码率。
  - 部分模式开启 HDR10+ 后录制时可能无法实时预览 HDR 效果，保存后可在相册查看实际 HDR10+ 效果。
  - 8K60 与 UHD240 对性能和散热要求较高，实际帧率和流畅度取决于设备芯片、温控和散热状态。

- NFC 息屏刷卡
  - 单独提供开关，控制是否允许 NFC 在息屏状态下按亮屏解锁状态处理刷卡请求。
  - 默认关闭，需要时可在模块界面启用。

- Bixby OpenAI 接入（测试版）
  - 让 Bixby 调用自定义 OpenAI / DeepSeek 兼容 Chat Completions API。
  - 支持自定义接口地址、模型和密钥等参数。
  - 目前仍为测试版功能，行为会继续调整。

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
- 当前主要在 One UI 8.0 环境测试过，请尽量保持系统组件、三星应用和模块本身为最新版本。
- 不同机型、地区包和三星应用版本可能存在差异，隐藏功能不保证在所有设备上都能稳定工作。

### 构建

如果项目所在路径包含中文或其他非 ASCII 字符，建议先把项目上级目录映射到临时盘符再构建：

```powershell
$projectRoot = Resolve-Path .
$workspaceRoot = Split-Path $projectRoot -Parent
$projectName = Split-Path $projectRoot -Leaf
cmd /c "subst W: `"$workspaceRoot`""
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ("W:\{0}\build-v50.ps1" -f $projectName)
```

构建脚本会把最终 APK 复制到项目上级目录：

```text
..\MyFilesWebDavPopupLsp.apk
```

### 安装

```powershell
$projectRoot = Resolve-Path .
$workspaceRoot = Split-Path $projectRoot -Parent
adb install --user 0 -r (Join-Path $workspaceRoot "MyFilesWebDavPopupLsp.apk")
```

### 说明

- `build/` 目录已被 git 忽略，不会上传到仓库。
- 当前仓库保留实际工作中的项目结构，没有改写成 Gradle 工程。
- 这是非官方模块，涉及 LSPosed hook 和三星隐藏能力，使用前请自行评估风险。

### 主要文件

- [AndroidManifest.xml](AndroidManifest.xml)
- [build-v50.ps1](build-v50.ps1)
- [SettingsActivity.java](src/main/java/com/samsung/feature/extension/SettingsActivity.java)
- [MyFilesWebDavHook.java](src/main/java/com/samsung/feature/extension/MyFilesWebDavHook.java)
- [GalaxyRaw200MpHook.java](src/main/java/com/samsung/feature/extension/galaxyraw200mp/GalaxyRaw200MpHook.java)
- [CameraProVideoHdr10Hook.java](src/main/java/com/samsung/feature/extension/camera/CameraProVideoHdr10Hook.java)
- [TouchSamplingHook.java](src/main/java/com/samsung/feature/extension/touchsampling/TouchSamplingHook.java)
- [PassThroughChargingHook.java](src/main/java/com/samsung/feature/extension/passthrough/PassThroughChargingHook.java)
- [ChargingStyleHook.java](src/main/java/com/samsung/feature/extension/charging/ChargingStyleHook.java)
- [FingerprintStyleHook.java](src/main/java/com/samsung/feature/extension/fingerprint/FingerprintStyleHook.java)
- [SettingsCustomFontHook.java](src/main/java/com/samsung/feature/extension/SettingsCustomFontHook.java)
- [LauncherIconCustomizerHook.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerHook.java)

## English

### Features

- Samsung My Files WebDAV support
  - Adds WebDAV to the network storage flow, with compatibility paths for older and newer My Files builds.
  - Supports adding accounts, browsing directories, creating folders, deleting, uploading, downloading, copying, and moving files.
  - Includes compatibility fixes for menu injection, existing WebDAV entries, transfer actions, and completion jump prompts.

- Expert RAW enhancements
  - Unlocks hidden 200MP ultra-high-resolution support for Galaxy Expert RAW, mainly for Ultra devices.
  - Adds S23 Ultra guards for newer Expert RAW builds where unsupported 24MP quick-setting resources can crash startup.
  - Keeps the S24 Ultra path that has already been tested.

- Samsung Camera pro video enhancements
  - Unlocks HDR10+ for Pro Video 4K120 and FHD120.
  - Unlocks Pro Video 8K 24/30/60fps and HDR10+ for those modes.
  - Adds slow-motion UHD 240fps support.
  - Adds a video bitrate page for reading default bitrates and overriding 8K, 4K, and FHD recording bitrates.
  - Some HDR10+ modes may not preview HDR during recording; the saved video should be checked in Gallery.
  - 8K60 and UHD240 are performance and thermal heavy, so actual results depend on the device, throttling, and cooling.

- NFC screen-off card tapping
  - Adds a dedicated switch for allowing NFC card requests while the screen is off.
  - Disabled by default.

- Bixby OpenAI integration (beta)
  - Lets Bixby call custom OpenAI / DeepSeek compatible Chat Completions APIs.
  - Supports custom endpoint, model, API key, and related parameters.
  - Still experimental.

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
- Currently mainly tested on One UI 8.0. Keep Samsung apps, system components, and the module itself updated.
- Hidden Samsung features may vary by model, region, and app version.

### Build

If your checkout path contains non-ASCII characters, map the parent workspace to a temporary drive letter before building:

```powershell
$projectRoot = Resolve-Path .
$workspaceRoot = Split-Path $projectRoot -Parent
$projectName = Split-Path $projectRoot -Leaf
cmd /c "subst W: `"$workspaceRoot`""
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ("W:\{0}\build-v50.ps1" -f $projectName)
```

The build script copies the final APK to the parent directory of the project:

```text
..\MyFilesWebDavPopupLsp.apk
```

### Install

```powershell
$projectRoot = Resolve-Path .
$workspaceRoot = Split-Path $projectRoot -Parent
adb install --user 0 -r (Join-Path $workspaceRoot "MyFilesWebDavPopupLsp.apk")
```

### Notes

- `build/` is ignored and is not committed to the repository.
- This repository keeps the real working project layout and does not rewrite it into a Gradle project.
- This is an unofficial module that relies on LSPosed hooks and hidden Samsung behavior. Use it at your own risk.

## License

No separate license file has been added yet.
