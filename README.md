# Samsung Feature Extension

![Samsung Feature Extension Icon](docs/icon.png)

Unofficial LSPosed module for Samsung / One UI devices.

App name: `三星功能扩展`  
Package name: `com.samsung.feature.extension`

## Features

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

## Environment

- Samsung / One UI device
- LSPosed
- Android SDK Build-Tools `35.0.0`
- JDK `21.0.10`

## Build

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

## Install

```powershell
$projectRoot = Resolve-Path .
$workspaceRoot = Split-Path $projectRoot -Parent
adb install -r (Join-Path $workspaceRoot "MyFilesWebDavPopupLsp.apk")
```

## Project Notes

- `build/` is ignored and is not committed to the repository.
- This repository keeps the real working project layout and does not rewrite it into a Gradle project.
- One UI launcher hooks avoid `onDraw` or per-frame refresh paths to reduce lag risk.

## Main Files

- [AndroidManifest.xml](AndroidManifest.xml)
- [build-v50.ps1](build-v50.ps1)
- [LauncherIconCustomizerHook.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerHook.java)
- [LauncherIconCustomizerActivity.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerActivity.java)
- [LauncherIconCustomizerStore.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerStore.java)
- [LauncherIconCustomizerProvider.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerProvider.java)

## License

No separate license file has been added yet.
