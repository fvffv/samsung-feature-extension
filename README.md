# Samsung Feature Extension / 三星功能扩展

![Samsung Feature Extension Icon](docs/icon.png)

Unofficial LSPosed module for Samsung / One UI devices.  
这是一个面向 Samsung / One UI 设备的非官方 LSPosed 模块。

App name / 应用名: `三星功能扩展`  
Package name / 包名: `com.samsung.feature.extension`

## 中文说明

### 功能

- 三星“我的文件” WebDAV 支持
  - 可添加、浏览、上传、下载、复制、移动
- Expert RAW 相关增强
- NFC 息屏刷卡开关
- Bixby 自定义 OpenAI / DeepSeek 兼容 Chat Completions API 接入
- One UI 主屏幕自定义
  - 按应用自定义桌面图标
  - 按应用自定义桌面名称
  - 名称样式支持字体、粗体、斜体、渐变
  - 支持导入自定义字体文件
  - 支持快速重启 One UI Home

### 环境

- Samsung / One UI 设备
- LSPosed
- Android SDK Build-Tools `35.0.0`
- JDK `21.0.10`

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
- 当前仓库保留的是实际工作中的项目结构，没有改写成 Gradle 工程。
- One UI 主屏幕相关 hook 避免使用 `onDraw` 或逐帧刷新，以降低卡顿风险。

### 主要文件

- [AndroidManifest.xml](AndroidManifest.xml)
- [build-v50.ps1](build-v50.ps1)
- [LauncherIconCustomizerHook.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerHook.java)
- [LauncherIconCustomizerActivity.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerActivity.java)
- [LauncherIconCustomizerStore.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerStore.java)
- [LauncherIconCustomizerProvider.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerProvider.java)

## English

### Features

- Samsung My Files WebDAV support
  - add, browse, upload, download, copy, move
- Expert RAW enhancements
- NFC screen-off card toggle
- Bixby custom OpenAI / DeepSeek compatible Chat Completions API access
- One UI launcher customization
  - per-app icon override
  - per-app label override
  - label style: font, bold, italic, gradient
  - import custom font files
  - quick restart for One UI Home

### Environment

- Samsung / One UI device
- LSPosed
- Android SDK Build-Tools `35.0.0`
- JDK `21.0.10`

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
- One UI launcher hooks avoid `onDraw` or per-frame refresh paths to reduce lag risk.

## License

No separate license file has been added yet.
