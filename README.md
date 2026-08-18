# Samsung Feature Extension / 三星功能扩展

![Samsung Feature Extension Icon](docs/icon.png)

Samsung / One UI 设备的 LSPosed 功能扩展模块。

- 应用名 / App name: `三星功能扩展`
- 包名 / Package name: `com.samsung.feature.extension`
- 版本 / Version: `1.25`
- GitHub: [fvffv/samsung-feature-extension](https://github.com/fvffv/samsung-feature-extension)

## 中文功能

- **WebDAV 网络存储**
  - 在“我的文件”网络存储中添加 WebDAV。
  - 支持连接服务器、浏览目录、预览、重命名、新建文件夹、删除、上传、下载、复制和移动。

- **Expert RAW 200MP RAW**
  - 在 Galaxy Ultra 机型的 Expert RAW 中提供 200MP RAW 拍摄选项。

- **音频橡皮擦**
  - 在视频编辑工作室中使用音频分析、人声和环境声分离、音频清除及保存副本。
  - 在系统下拉面板中使用音频橡皮擦，并调节清除强度和人声模式。

- **相机视频功能**
  - 专业视频 HDR10+。
  - 8K 24/30/60fps 录制。
  - 慢动作 UHD 240fps 录制。
  - 超级稳定水平锁定。
  - 读取和设置 8K、4K、FHD 视频码率。

- **NFC 息屏刷卡**
  - 设置息屏状态下的 NFC 刷卡行为。

- **One UI 主屏幕自定义**
  - 自定义应用图标、图标形状、圆角、应用名称和名称字体。
  - 支持图片裁剪、字体颜色、渐变、粗体和斜体。
  - 提供快速重启 One UI 主屏幕。

- **系统字体**
  - 导入、切换和管理本地 TTF 字体。

- **指纹图标**
  - 自定义锁屏和系统验证界面的指纹图标。
  - 支持 Lottie 动画、静态图片、循环播放和单次播放。

- **触控高采样率**
  - 设置手指触控采样策略并查看实时刷新率。

- **全局旁路供电**
  - 设置游戏时暂停 USB PD 充电的旁路供电状态。
  - 支持桌面图标快捷切换。

- **充电显示与提示音**
  - 自定义充电显示文本和变量模板。
  - 设置插入、拔出充电器时的提示音，并预览系统音频。

- **设备健康管理**
  - 查看温控、电池、异常检测和后台限制状态。
  - 提供常用设备健康控制开关。

- **文本通话**
  - 自定义两种文本通话开场语。
  - 设置文本通话时是否播放对方声音，同时显示语音转文字。

- **应用分身列表**
  - 在应用分身页面查看可用应用列表。

## English Features

- **WebDAV network storage**
  - Adds WebDAV to Samsung My Files network storage.
  - Connects to servers and supports directory browsing, preview, rename, folder creation, delete, upload, download, copy, and move.

- **Expert RAW 200MP RAW**
  - Provides a 200MP RAW capture option in Expert RAW on Galaxy Ultra devices.

- **Audio Eraser**
  - Provides audio analysis, voice and ambient sound separation, audio removal, and Save Copy in Video Editor Studio.
  - Provides Audio Eraser controls in the system quick panel, including removal strength and voice focus.

- **Camera video features**
  - HDR10+ for Pro Video.
  - 8K recording at 24/30/60fps.
  - UHD 240fps slow motion.
  - Horizon Lock for Super Steady.
  - Read and set 8K, 4K, and FHD video bitrates.

- **NFC screen-off card tapping**
  - Controls NFC card tapping while the screen is off.

- **One UI Home customization**
  - Customize app icons, icon shapes, corner radius, app labels, and label fonts.
  - Supports image cropping, font colors, gradients, bold, and italic styles.
  - Provides a quick One UI Home restart action.

- **System fonts**
  - Import, select, and manage local TTF fonts.

- **Fingerprint icon**
  - Customize the fingerprint icon used by the lock screen and system authentication UI.
  - Supports Lottie animations, static images, looping, and one-shot playback.

- **Touch high sampling rate**
  - Set the finger-touch sampling policy and view the live refresh rate.

- **Global pass-through charging**
  - Set the USB PD pass-through charging state used while gaming.
  - Includes a launcher shortcut for quick toggling.

- **Charging display and sounds**
  - Customize charging text and variable templates.
  - Set and preview plug-in and unplug sounds.

- **Device Health Manager**
  - View thermal, battery, anomaly detection, and background restriction states.
  - Provides common Device Health controls.

- **Text Call**
  - Customize both Text Call greeting messages.
  - Choose whether the other party's voice is played while the speech transcript remains visible.

- **Dual app list**
  - View the available application list for dual apps.

## Main Source Files

- [SettingsActivity.java](src/main/java/com/samsung/feature/extension/SettingsActivity.java)
- [MyFilesWebDavHook.java](src/main/java/com/samsung/feature/extension/MyFilesWebDavHook.java)
- [GalaxyRaw200MpHook.java](src/main/java/com/samsung/feature/extension/galaxyraw200mp/GalaxyRaw200MpHook.java)
- [CameraOneUi85VideoUnlockHook.java](src/main/java/com/samsung/feature/extension/camera/CameraOneUi85VideoUnlockHook.java)
- [VideoEditorAudioEraserHook.java](src/main/java/com/samsung/feature/extension/videoeditor/VideoEditorAudioEraserHook.java)
- [SystemUiAudioEraserHook.java](src/main/java/com/samsung/feature/extension/audioeraser/SystemUiAudioEraserHook.java)
- [TextCallGreetingHook.java](src/main/java/com/samsung/feature/extension/phone/TextCallGreetingHook.java)
