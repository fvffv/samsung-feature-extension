# Samsung Feature Extension

![Samsung Feature Extension Icon](docs/icon.png)

一个面向 Samsung / One UI 的 LSPosed 模块项目，当前整合了多个日常增强功能，应用名为“`三星功能扩展`”，包名为 `com.samsung.feature.extension`。

> Unofficial LSPosed module for Samsung devices. Not affiliated with Samsung.

## Features

- 我的文件 WebDAV 支持
  - 添加、显示、进入、上传、下载、复制、移动等基础链路可用
- Expert RAW 相关增强
- NFC 息屏刷卡开关
- Bixby 自定义 OpenAI / DeepSeek 兼容 Chat Completions API 接入（实验功能）
- One UI 主屏幕自定义
  - 按应用自定义桌面图标
  - 按应用自定义桌面显示名称
  - 名称样式支持字体、粗体、斜体、渐变色
  - 支持从本地文件导入字体
  - 支持快速重启 One UI 主屏幕

## Environment

- Samsung / One UI 设备
- LSPosed
- Android SDK Build-Tools `35.0.0`
- JDK `21.0.10`

## Build

由于中文路径可能导致 `aapt2` / `javac` 构建不稳定，建议先映射一个盘符：

```powershell
cmd /c "subst W: ""D:\桌面\AI工作区\功能添加"""
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "W:\MyFilesWebDavPopupLsp\build-v50.ps1"
```

默认输出 APK：

```text
D:\桌面\AI工作区\功能添加\MyFilesWebDavPopupLsp.apk
```

## Install

```powershell
D:\桌面\AI工作区\功能添加\.tools\android-sdk\platform-tools\adb.exe install -r "D:\桌面\AI工作区\功能添加\MyFilesWebDavPopupLsp.apk"
```

## Project Notes

- `build/` 目录为本地构建产物，已在 git 中忽略，不会上传到仓库。
- 当前仓库保留的是持续迭代中的真实工程结构，没有重写为 Gradle 项目。
- One UI 主屏幕相关 hook 以明确绑定链路替换为主，避免 `onDraw` 或逐帧刷新带来的卡顿风险。

## Main Files

- [AndroidManifest.xml](AndroidManifest.xml)
- [build-v50.ps1](build-v50.ps1)
- [LauncherIconCustomizerHook.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerHook.java)
- [LauncherIconCustomizerActivity.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerActivity.java)
- [LauncherIconCustomizerStore.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerStore.java)
- [LauncherIconCustomizerProvider.java](src/main/java/com/samsung/feature/extension/LauncherIconCustomizerProvider.java)

## License

暂未附加单独许可证；如需公开协作，建议后续补充。
