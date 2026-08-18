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
  - 支持服务器地址、账户信息和网络存储列表管理。
  - 支持浏览目录、预览、重命名、新建文件夹、删除、上传、下载、复制和移动。

- **Expert RAW 200MP RAW**
  - 在 Galaxy Ultra 机型的 Expert RAW 中提供 200MP RAW 拍摄选项。
  - 可在高分辨率模式下拍摄和保存 RAW 照片。

- **音频橡皮擦**
  - 让 S24 Ultra 以下机型也可使用音频橡皮擦功能。
  - 在视频编辑工作室中使用音频分析、人声和环境声分离、音频清除及保存副本。
  - 在系统下拉面板中使用音频橡皮擦，并调节清除强度和人声模式。

- **相机视频功能**
  - 在专业视频中使用 HDR10+、4K120、FHD120 和 8K 录制选项。
  - 选择 8K 24/30/60fps 视频帧率。
  - 在慢动作模式中使用 UHD 240fps。
  - 读取当前视频码率，并设置 8K、4K、FHD 视频码率。

- **NFC 息屏刷卡**
  - 通过独立开关设置息屏状态下的 NFC 刷卡行为。

- **One UI 主屏幕自定义**
  - 自定义应用图标、图标形状、圆角、应用名称和名称字体。
  - 支持图片选择与裁剪、字体颜色、渐变、粗体和斜体。
  - 提供快速重启 One UI 主屏幕。

- **系统字体**
  - 导入、切换、删除和管理本地 TTF 字体。

- **指纹图标**
  - 自定义锁屏和系统验证界面的指纹图标。
  - 支持内置或本地导入的 Lottie 动画、静态图片、循环播放和单次播放。

- **触控高采样率**
  - 设置手指触控采样策略，并在页面中查看实时刷新率。

- **全局旁路供电**
  - 设置游戏时暂停 USB PD 充电的旁路供电状态。
  - 支持模块页面和桌面图标快捷切换。

- **充电显示与提示音**
  - 自定义充电显示文本和变量模板。
  - 分别设置插入、拔出充电器时的提示音，并预览系统音频。

- **设备健康管理**
  - 查看 Thermal Status、温控、电池、异常检测和后台限制状态。
  - 提供温控和后台限制等常用设备健康控制开关。

- **文本通话**
  - 自定义两种文本通话开场语。
  - 设置文本通话时是否播放对方声音，同时显示对方语音转文字。

- **应用分身列表**
  - 在应用分身页面扩展并查看可用应用列表。

## English Features

- **WebDAV network storage**
  - Adds WebDAV to Samsung My Files network storage.
  - Manages server addresses, account details, and network-storage entries.
  - Supports directory browsing, preview, rename, folder creation, delete, upload, download, copy, and move.

- **Expert RAW 200MP RAW**
  - Provides a 200MP RAW capture option in Expert RAW on Galaxy Ultra devices.
  - Captures and saves RAW photos in high-resolution mode.

- **Audio Eraser**
  - Makes Audio Eraser available on models below Galaxy S24 Ultra.
  - Provides audio analysis, voice and ambient sound separation, audio removal, and Save Copy in Video Editor Studio.
  - Provides Audio Eraser controls in the system quick panel, including removal strength and voice focus.

- **Camera video features**
  - HDR10+, 4K120, FHD120, and 8K options for Pro Video.
  - 8K recording at 24/30/60fps.
  - UHD 240fps slow motion.
  - Read the current bitrate and set 8K, 4K, and FHD video bitrates.

- **NFC screen-off card tapping**
  - Uses a dedicated switch to control NFC card tapping while the screen is off.

- **One UI Home customization**
  - Customize app icons, icon shapes, corner radius, app labels, and label fonts.
  - Supports image selection and cropping, font colors, gradients, bold, and italic styles.
  - Provides a quick One UI Home restart action.

- **System fonts**
  - Import, select, delete, and manage local TTF fonts.

- **Fingerprint icon**
  - Customize the fingerprint icon used by the lock screen and system authentication UI.
  - Supports built-in or locally imported Lottie animations, static images, looping, and one-shot playback.

- **Touch high sampling rate**
  - Set the finger-touch sampling policy and view the live refresh rate in the settings page.

- **Global pass-through charging**
  - Set the USB PD pass-through charging state used while gaming.
  - Includes module-page and launcher shortcuts for quick toggling.

- **Charging display and sounds**
  - Customize charging text and variable templates.
  - Set plug-in and unplug sounds separately, and preview system audio.

- **Device Health Manager**
  - View Thermal Status, temperature control, battery, anomaly detection, and background restriction states.
  - Provides common temperature-control and background-restriction controls.

- **Text Call**
  - Customize both Text Call greeting messages.
  - Choose whether the other party's voice is played while their speech transcript remains visible.

- **Dual app list**
  - Expand and view the available application list for dual apps.

## Main Source Files

- [SettingsActivity.java](src/main/java/com/samsung/feature/extension/SettingsActivity.java)
- [MyFilesWebDavHook.java](src/main/java/com/samsung/feature/extension/MyFilesWebDavHook.java)
- [GalaxyRaw200MpHook.java](src/main/java/com/samsung/feature/extension/galaxyraw200mp/GalaxyRaw200MpHook.java)
- [CameraOneUi85VideoUnlockHook.java](src/main/java/com/samsung/feature/extension/camera/CameraOneUi85VideoUnlockHook.java)
- [VideoEditorAudioEraserHook.java](src/main/java/com/samsung/feature/extension/videoeditor/VideoEditorAudioEraserHook.java)
- [SystemUiAudioEraserHook.java](src/main/java/com/samsung/feature/extension/audioeraser/SystemUiAudioEraserHook.java)
- [TextCallGreetingHook.java](src/main/java/com/samsung/feature/extension/phone/TextCallGreetingHook.java)
