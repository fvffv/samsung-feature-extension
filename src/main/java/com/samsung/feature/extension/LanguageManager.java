package com.samsung.feature.extension;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent in-app Chinese / English presentation preference. */
public final class LanguageManager {
    private static final String PREFS = "ui_language";
    private static final String KEY_ENGLISH = "english";
    private static final Map<String, String> ENGLISH = new LinkedHashMap<>();
    private static final Map<String, String> ENGLISH_FRAGMENTS = new LinkedHashMap<>();

    static {
        // Main screen and feature catalog.
        add("三星功能扩展", "Samsung Feature Extension");
        add("当前模块包含以下功能。", "The module currently includes the following features.");
        add("三星文件管理 WebDAV", "Samsung My Files WebDAV");
        add("在添加网络存储中增加 WebDAV，支持目录浏览、上传、下载、复制、移动和删除等常用文件操作。", "Adds WebDAV to network storage with browsing, upload, download, copy, move, and delete support.");
        add("Expert RAW 200MP", "Expert RAW 200MP");
        add("为 Galaxy Expert RAW 解锁隐藏的 200MP 超高分辨率能力，目前主要面向 Ultra 机型。", "Unlocks the hidden 200 MP ultra-high-resolution mode in Galaxy Expert RAW, primarily for Ultra models.");
        add("三星相机专业视频增强", "Samsung Camera Pro Video Enhancements");
        add("解锁专业模式 4K120 HDR10+、8K 24/30/60 HDR10+ 录制，以及慢动作 UHD 240 帧录制。支持按 8K、4K、FHD 自定义视频录制码率。", "Unlocks Pro Video 4K120 HDR10+, 8K 24/30/60 HDR10+, and UHD 240 fps slow motion. Recording bitrates can be customized for 8K, 4K, and FHD.");
        add("文本通话自定义开场语", "Text Call Custom Greetings");
        add("自定义三星电话“语音转文字”和“代为说话”两种文本通话模式的开场播报内容；留空即可使用系统默认文案。", "Customize the opening messages for Samsung Phone Text Call transcription and speak-for-me modes. Leave a field blank to keep the system default.");
        add("应用分身全应用列表", "Dual Messenger Full App List");
        add("把应用分身的可用列表扩展为已安装的非系统应用。", "Extends the Dual Messenger list to installed non-system apps.");
        add("设备健康管理", "Device Health Manager");
        add("查看 Samsung Device Health Manager Service 的温控、电池、异常检测和后台限制状态，并提供常用控制开关。", "View thermal, battery, anomaly-detection, and background restriction status from Samsung Device Health Manager Service, with common control switches.");
        add("NFC 息屏刷卡", "NFC Screen-off Payments");
        add("控制是否允许 NFC 在息屏状态下按照亮屏解锁状态处理刷卡请求，默认关闭，可在此单独启用。", "Controls whether NFC processes payments while the screen is off as if the unlocked screen were on. Disabled by default and can be enabled here.");
        add("持续禁用应用兼容策略", "Keep App Compatibility Policies Disabled");
        add("保持开发者选项中的“Disable app compatibility policies”处于开启状态。开启后模块会在系统设置写回该值，避免它运行一段时间后自动关闭。", "Keeps the developer option “Disable app compatibility policies” enabled. When enabled, the module writes the value back through Android Settings so it does not turn itself off later.");
        add("保持 Disable app compatibility policies", "Keep Disable app compatibility policies");
        add("已开启：系统设置会持续保持该策略为禁用。", "Enabled: Android Settings will keep this policy disabled.");
        add("已关闭：不再干预系统的应用兼容策略设置。", "Disabled: the module no longer changes app compatibility-policy settings.");
        add("触控高采样率", "High Touch Sampling Rate");
        add("给手指触控补发三星原生 GOS TSP 高扫描率策略，熄屏后亮屏会自动脉冲重开一次。", "Re-sends Samsung's native GOS TSP high-sampling policy for finger touch, including an automatic off/on pulse after the screen turns back on.");
        add("全局旁路供电", "Global Bypass Charging");
        add("基于 Game Booster 的“游戏时暂停 USB PD 充电”机制，开启后全局维持 pass_through 旁路供电状态，关闭后恢复系统默认。", "Uses Game Booster's Pause USB PD charging while gaming mechanism to keep bypass charging enabled globally; disabling restores the system default.");
        add("充电显示与提示音", "Charging Display and Sounds");
        add("自定义 SystemUI 充电通知显示内容，支持变量模板，并可设置插入和拔出充电器时的提示音。", "Customize the SystemUI charging notification with variable templates and sounds for connecting or disconnecting a charger.");
        add("One UI 8.5 音频橡皮擦", "One UI 8.5 Audio Eraser");
        add("为 S24 Ultra 以下机型开放视频编辑工作室和快速面板的音频橡皮擦；快速面板会在检测到符合条件的媒体音频播放时自动显示。", "Enables Audio Eraser in Video Editor and Quick Panel for devices below S24 Ultra. Quick Panel appears when eligible media audio is playing.");
        add("One UI 主屏幕图标自定义", "One UI Home Icon Customization");
        add("为每个应用单独设置桌面图标、名称和字体样式，并在 One UI 主屏幕设置中增加入口。", "Set an individual home icon, label, and font style for each app, with an entry added to One UI Home settings.");
        add("自定义指纹图标", "Custom Fingerprint Icon");
        add("替换系统指纹验证位置显示的指纹图标，支持 Lottie JSON 动画或 PNG 静态图。", "Replace the fingerprint icon shown at the system authentication location with a Lottie JSON animation or PNG image.");
        add("系统字体自定义", "Custom System Font");
        add("在三星设置的字体风格页面中添加本地字体入口，可选择并应用本地 .ttf 字体文件。目前仅支持 ttf 格式。", "Adds a local-font entry to Samsung Settings > Font style. Local .ttf files can be selected and applied; only TTF is supported.");
        add("查看设置 >", "Settings >");
        add("已集成", "Included");

        // Main screen cards, updates, and diagnostics.
        add("语言", "Language");
        add("选择模块界面语言。切换后会立即刷新当前页面，后续打开的设置页也会保持此语言。", "Choose the module interface language. The current page refreshes immediately and future settings pages use the same language.");
        add("简体中文", "Simplified Chinese");
        add("版本与更新", "Version and Updates");
        add("已安装版本取自当前 APK；GitHub 发布版仅反映远端 Releases。", "Installed version comes from the current APK; GitHub release version reflects remote Releases only.");
        add("已安装版本", "Installed version");
        add("GitHub 发布版", "GitHub release");
        add("版本比较", "Version comparison");
        add("打开 GitHub", "Open GitHub");
        add("检查更新", "Check for updates");
        add("检查中...", "Checking...");
        add("正在检查", "Checking");
        add("检查失败", "Check failed");
        add("无法获取版本信息", "Unable to retrieve version information");
        add("未知", "Unknown");
        add("未获取到版本号", "No version number received");
        add("已是最新版本", "You are up to date");
        add("当前版本高于 Releases 最新版", "Installed version is newer than the latest release");
        add("发现新版本", "New version available");
        add("检查更新失败", "Update check failed");
        add("当前无法从 GitHub Releases 获取最新版本信息，请稍后重试。", "The latest version could not be retrieved from GitHub Releases. Please try again later.");
        add("提示：当前发布版本主要在 One UI 8.0 / 8.5 中测试；音频橡皮擦兼容层面向 One UI 8.5，请尽量保持系统、LSPosed 和相关三星应用为最新版本。", "Note: current releases are mainly tested on One UI 8.0 / 8.5. Audio Eraser compatibility targets One UI 8.5; keep the system, LSPosed, and relevant Samsung apps up to date.");
        add("诊断日志", "Diagnostic Logs");
        add("关闭后不再写入 LSPosed 日志和本地诊断文件，排查问题时再临时开启。", "When disabled, LSPosed logs and local diagnostic files are not written. Enable it temporarily only while troubleshooting.");
        add("日志输出", "Log output");
        add("已开启，会输出诊断日志，可能轻微增加后台开销。", "Enabled. Diagnostic logs are written and may slightly increase background overhead.");
        add("已关闭，推荐日常使用保持关闭。", "Disabled. Keeping it disabled is recommended for daily use.");

        // Text Call settings.
        add("文本通话开场语", "Text Call Opening Messages");
        add("自定义内容会在来电接听、主动拨号和通话中切换为文本通话时播报。留空即可保留三星的系统默认文案，也可选择继续播放对方说话声音。", "Custom messages are spoken when answering, dialing, or switching to Text Call. Leave fields blank to keep Samsung defaults, and optionally keep hearing the other caller.");
        add("两套原生开场语", "Two Native Opening Messages");
        add("语音转文字模式对应“将您的语音转换为文本并回复您”；代为说话模式对应“正在使用语音助手替我说话”。", "Transcription mode is the message about converting the caller's speech to text; speak-for-me mode is the message about the assistant speaking for you.");
        add("语音转文字模式开场语", "Speech-to-text Mode Opening Message");
        add("代为说话模式开场语", "Speak-for-me Mode Opening Message");
        add("留空使用默认：您好。我正在使用语音助手将您的语音转换为文本并回复您……", "Leave blank for default: Hello. I am using a voice assistant to convert your speech to text and reply to you...");
        add("留空使用默认：您好。我正在使用语音助手替我说话。请告诉我您来电的原因。", "Leave blank for default: Hello. I am using a voice assistant to speak for me. Please tell me the reason for your call.");
        add("文本通话时播放对方说话声音", "Play the Other Caller’s Voice During Text Call");
        add("默认关闭。开启后，对方的原始语音会继续从当前通话输出设备播放，同时仍保留语音转文字内容。", "Disabled by default. When enabled, the caller's original voice continues through the current call output while transcription remains available.");
        add("保存", "Save");
        add("恢复系统默认", "Restore System Defaults");
        add("已恢复系统默认开场语", "System default opening messages restored");
        add("已保存，下次启动文本通话时生效", "Saved. Takes effect the next time Text Call starts.");
        add("当前：两套开场语均使用三星系统默认内容。对方声音保持关闭。", "Current: both opening messages use Samsung defaults. The caller's voice is muted.");
        add("当前：两套开场语均使用三星系统默认内容。已开启播放对方声音。", "Current: both opening messages use Samsung defaults. Playing the caller's voice is enabled.");

        // Shared settings-page labels.
        add("相机视频码率", "Camera Video Bitrate");
        add("按 8K、4K、FHD 分别控制视频录制码率。检测方法：先打开此页面，然后打开相机视频录制界面（无需录制）即可读取默认码率，修改后的码率请以实际录制出来的为准！", "Control video recording bitrates separately for 8K, 4K, and FHD. To detect defaults, open this page and then open Camera video mode; no recording is required. Use actual recordings as the final result.");
        add("最近检测到的默认码率", "Recently Detected Default Bitrates");
        add("刷新检测结果", "Refresh Detection Results");
        add("检测方法：先打开此页面，然后打开相机视频录制界面（无需录制）即可读取默认码率，修改后的码率请以实际录制出来的为准！", "Detection: open this page first, then open Camera video mode. Default bitrates are read without recording; use the actual recorded output as the final result.");
        add("视频录制码率", "Video Recording Bitrate");
        add("目标 Mbps", "Target Mbps");
        add("全部恢复默认", "Restore All Defaults");
        add("已恢复系统默认", "System defaults restored");
        add("启用视频码率前请填写 Mbps", "Enter Mbps before enabling a video bitrate.");
        add("已保存，相机重启后更稳", "Saved. Restarting Camera is recommended.");
        add("视频默认码率\n", "Default Video Bitrates\n");
        add("尚未检测到\n", "Not detected yet\n");
        add("当前启用：", "Enabled now: ");
        add("保持系统默认，只记录检测结果。", "Using system defaults; detection results are recorded only.");
        add("NFC 刷卡模式", "NFC Payment Mode");
        add("选择 NFC 绕过锁屏限制的范围。切换后通常会在几秒内即时生效，不需要重启手机。", "Choose how NFC bypasses lock-screen restrictions. Changes normally apply within seconds without restarting the phone.");
        add("关闭\n使用系统默认 NFC 行为，需要解锁或亮屏时仍按系统要求处理。", "Off\nUse the system NFC behavior; unlocking or turning on the screen is still required by the system.");
        add("仅亮屏免解锁\n屏幕点亮但仍在锁屏界面时可刷卡；熄屏时不绕过系统限制。", "Screen-on only\nPayments work on the lock screen while the display is on; screen-off restrictions remain.");
        add("息屏免解锁\n熄屏和亮屏锁屏时都按已解锁亮屏状态处理，适合支付宝碰一碰等场景。", "Screen-off and screen-on\nTreat locked screen-off and screen-on states as unlocked screen-on, useful for tap-to-pay scenarios.");
        add("全局旁路供电", "Global Bypass Charging");
        add("启用全局旁路供电", "Enable Global Bypass Charging");
        add("当前状态：已启用。模块会在后台维持旁路供电开启。", "Current status: enabled. The module keeps bypass charging enabled in the background.");
        add("当前状态：已关闭。模块不再全局强制旁路供电。", "Current status: disabled. The module no longer forces bypass charging globally.");
        add("系统 pass_through 当前值：", "Current system pass_through value: ");
        add("触控高采样率", "High Touch Sampling Rate");
        add("强制开启手指高采样", "Force High Finger Touch Sampling");
        add("实时触控刷新率检测", "Live Touch Refresh-rate Detection");
        add("手指在下方区域连续滑动，显示最近触控刷新率。", "Keep sliding a finger in the area below to show the recent touch refresh rate.");
        add("在这里滑动", "Slide Here");
        add("继续滑动以计算实时触控刷新率...", "Keep sliding to calculate the live touch refresh rate...");
        add("当前状态：已关闭。触控采样率使用系统默认策略。", "Current status: disabled. Touch sampling follows the system policy.");
        add("设备健康管理", "Device Health Manager");
        add("刷新数据", "Refresh Data");
        add("禁用温控降频", "Disable Thermal Throttling");
        add("关闭亮度温控限制", "Disable Thermal Brightness Limit");
        add("关闭 CP/蜂窝温控限制", "Disable CP/Cellular Thermal Limit");
        add("温控状态", "Thermal Status");
        add("温度传感器", "Temperature Sensors");
        add("电池信息", "Battery Information");
        add("异常检测", "Anomaly Detection");
        add("高 CPU 记录", "High CPU Records");
        add("后台管控 / 限制应用", "Background Controls / Restricted Apps");
        add("手动限制应用后台", "Manually Restrict an App in Background");
        add("输入包名，例如 com.example.app", "Enter a package name, e.g. com.example.app");
        add("限制后台", "Restrict in Background");
        add("解除限制", "Remove Restriction");
        add("等待刷新...", "Waiting for refresh...");
        add("暂无数据", "No data");
        add("系统记录", "System Record");
        add("是", "Yes");
        add("否", "No");

        // Icon and fingerprint screens.
        add("桌面图标与名称自定义", "Home Icon and Label Customization");
        add("为每个应用单独设置桌面图标和显示名称。名称会跟图标一样通过 One UI 主屏幕绑定链路应用。", "Set a home icon and display name for each app. Names are applied through the same One UI Home binding path as icons.");
        add("搜索应用名称、包名或自定义名称", "Search app name, package name, or custom name");
        add("重启 One UI 主屏幕", "Restart One UI Home");
        add("系统默认", "System Default");
        add("无衬线", "Sans Serif");
        add("衬线", "Serif");
        add("等宽", "Monospace");
        add("窄体", "Condensed");
        add("中黑", "Medium");
        add("裁剪图标", "Crop Icon");
        add("裁剪桌面图标", "Crop Home Icon");
        add("拖动图片调整位置，双指缩放；保存前可选择方形、圆角或圆形图标。", "Drag the image to reposition it and pinch to zoom. Before saving, choose a square, rounded-square, or circular icon.");
        add("取消", "Cancel");
        add("图标形状", "Icon Shape");
        add("方形", "Square");
        add("圆角", "Rounded");
        add("圆形", "Circle");
        add("圆角度", "Corner Radius");
        add("自定义指纹图标", "Custom Fingerprint Icon");
        add("指纹图标", "Fingerprint Icon");
        add("启用自定义指纹图标", "Enable Custom Fingerprint Icon");
        add("循环播放动画", "Loop Animation");
        add("打开 LottieFiles", "Open LottieFiles");
        add("选择 Lottie JSON", "Choose Lottie JSON");
        add("选择 PNG", "Choose PNG");
        add("恢复默认", "Restore Default");
        add("Lottie 素材库", "Lottie Library");
        add("添加本地 Lottie JSON", "Add Local Lottie JSON");
        add("当前未选择文件。推荐使用 Lottie JSON；PNG 也可用于静态图案替换。", "No file is selected. Lottie JSON is recommended; PNG can also replace the icon with a static image.");
        add("状态：已有文件，但当前未启用。", "Status: a file is selected but is not enabled.");
        add("状态：使用系统默认指纹图标。", "Status: using the system default fingerprint icon.");

        // Charging display and sound settings.
        add("充电显示与提示音", "Charging Display and Sounds");
        add("修改 SystemUI 里的充电通知文字，并设置插入/拔出充电器时的提示音。修改后需要重启 SystemUI 或重启手机让 hook 生效。", "Change the charging notification text in SystemUI and choose sounds for connecting or disconnecting a charger. Restart SystemUI or the phone after changes for the hook to take effect.");
        add("启用自定义充电显示内容", "Enable Custom Charging Display");
        add("内容模板", "Content Template");
        add("变量：{level} 电量，{time} 剩余时间，{time_min} 剩余分钟，{type} 充电类型，{plug} 接入方式，{status} 状态，{current} 电流 mA，{voltage} 电压 V，{power} 功率 W，{temp} 温度，{system} 系统原文。", "Variables: {level} battery, {time} remaining time, {time_min} remaining minutes, {type} charging type, {plug} connection type, {status} status, {current} current mA, {voltage} voltage V, {power} power W, {temp} temperature, {system} system text.");
        add("保存显示设置", "Save Display Settings");
        add("恢复默认模板", "Restore Default Template");
        add("提示音设置", "Sound Settings");
        add("插入充电器提示音", "Charger Connected Sound");
        add("拔出充电器提示音", "Charger Disconnected Sound");
        add("跟随系统", "Follow System");
        add("使用系统拔出音", "Use System Disconnect Sound");
        add("使用自定义文件", "Use Custom File");
        add("关闭插入提示音", "Disable Connect Sound");
        add("关闭拔出提示音", "Disable Disconnect Sound");
        add("选择插入音", "Choose Connect Sound");
        add("清除插入音", "Clear Connect Sound");
        add("选择拔出音", "Choose Disconnect Sound");
        add("清除拔出音", "Clear Disconnect Sound");
        add("当前系统提示音", "Current System Sounds");
        add("播放系统插入音", "Play System Connect Sound");
        add("播放系统拔出音", "Play System Disconnect Sound");
        add("未选择自定义插入音", "No custom connect sound selected");
        add("未选择自定义拔出音", "No custom disconnect sound selected");
        add("预览", "Preview");
        add("还剩 XX 分钟充满电", "XX minutes until fully charged");

        // Device health manager states and controls.
        add("通过 LSPosed 接入 Samsung Device Health Manager Service，查看温控、电池、异常检测和后台限制状态。", "Uses LSPosed to access Samsung Device Health Manager Service and view thermal, battery, anomaly-detection, and background restriction status.");
        add("注意：关闭温控降频会提高发热和耗电风险，原生总开关可能触发手机重启。", "Warning: disabling thermal throttling can increase heat and power use. Samsung's native master switch may restart the phone.");
        add("正在等待 SDHMS Hook 响应...", "Waiting for the SDHMS hook response...");
        add("调用 SDHMS 原生隐藏总开关，开启后可能重启设备。", "Uses the hidden native SDHMS master switch; enabling it may restart the device.");
        add("发热时不再由该策略降低屏幕亮度。", "This policy will no longer lower display brightness when the phone is hot.");
        add("发热时不再由该策略限制蜂窝相关温控。", "This policy will no longer restrict cellular-related thermal behavior when the phone is hot.");
        add("确认关闭温控降频", "Confirm Disabling Thermal Throttling");
        add("这个开关会调用三星原生隐藏逻辑，可能导致设备重启，并带来更高发热风险。", "This switch invokes Samsung's hidden native logic. It may restart the device and increases heat risk.");
        add("继续", "Continue");
        add("请先输入包名", "Enter a package name first.");
        add("已发送请求，等待 SDHMS 响应...", "Request sent. Waiting for SDHMS response...");
        add("还没有收到响应。请确认 LSPosed 已勾选 Samsung Device Health Manager Service 作用域并重启。", "No response has been received. Confirm the Samsung Device Health Manager Service scope is selected in LSPosed, then restart the phone.");
        add("收到空响应。", "Received an empty response.");
        add("已连接 SDHMS Hook", "Connected to SDHMS hook");
        add("请求失败", "Request failed");
        add("刷新时间: ", "Refreshed: ");
        add("Limiter 可用: ", "Limiter available: ");
        add("总禁用温控降频: ", "Thermal throttling master disabled: ");
        add("亮度温控限制关闭: ", "Thermal brightness limit disabled: ");
        add("CP 温控限制关闭: ", "CP thermal limit disabled: ");
        add("当前亮度限制: ", "Current brightness limit: ");
        add("当前 HRR 限制: ", "Current HRR limit: ");
        add("当前 CP Low Mode: ", "Current CP low mode: ");
        add("当前 CP Cooling: ", "Current CP cooling: ");
        add("\n历史:\n", "\nHistory:\n");

        // Bypass charging and touch sampling descriptions.
        add("基于 Game Booster 的“游戏时暂停 USB PD 充电”机制，开启后会全局维持系统 pass_through 状态，让支持的 USB PD 充电器直接给手机供电并减少电池充放电。", "Based on Game Booster's Pause USB PD charging while gaming mechanism. When enabled, it keeps system pass_through active globally so a supported USB PD charger powers the phone directly and reduces battery cycling.");
        add("提示：该功能需要手机、充电器和线材都支持三星的 USB PD 旁路供电。开启后如果系统值没有立刻变为 1，请确认 LSPosed 中本模块已勾选“设备健康管理”和“Game Booster/游戏助推器”，然后重启手机。关闭后模块会写回 0，不再干预，Game Booster 自身逻辑继续按系统默认工作。", "Note: this requires Samsung USB PD bypass charging support from the phone, charger, and cable. If the system value does not become 1 immediately, confirm Device Health Manager and Game Booster scopes are selected in LSPosed, then restart. Disabling writes back 0 and stops module intervention.");
        add("开启后会通过三星原生 GOS TSP 策略给手指触控补发高扫描率命令。息屏后再次亮屏时，会先短暂发送关闭策略，再自动重新发送开启策略。该功能只影响触屏 TSP，不修改 S Pen 输入通道。", "When enabled, Samsung's native GOS TSP high-sampling command is re-sent for finger touch. After the screen turns back on, the policy is briefly turned off and re-enabled. This affects touch-screen TSP only, not the S Pen input channel.");
        add("提示：切换后通常无需重启手机；开启后只会定时补发 TSP 广播，不再直接写触控节点，也不再修改 GOS 服务状态。限制：连接充电器充电时，系统会强制降低触控采样率，此功能在充电状态下无法生效。若没有立即变化，请确认 LSPosed 中本模块已勾选“设备健康管理”，并重启手机。", "Note: restarting is normally unnecessary. The enabled mode periodically re-sends TSP broadcasts and no longer writes touch nodes or changes GOS service state. Limitation: while charging, the system forces a lower touch sampling rate, so this feature cannot take effect. If there is no immediate change, confirm the Device Health Manager scope in LSPosed and restart.");
        add("当前状态：已启用。系统会定时补发 TSP 广播，亮屏时会自动关开脉冲一次；充电状态下无法生效。", "Current status: enabled. The system periodically re-sends TSP broadcasts and pulses off/on after the screen turns on; it cannot take effect while charging.");
        add("当前估算：%.0f Hz    样本：%d    窗口：%d ms", "Current estimate: %.0f Hz    Samples: %d    Window: %d ms");

        // Fingerprint icon library and actions.
        add("可以在 LottieFiles 免费动画页查找 Lottie JSON 动画：https://lottiefiles.com/free-animations", "Find Lottie JSON animations at LottieFiles Free Animations: https://lottiefiles.com/free-animations");
        add("内置 9 个默认 Lottie JSON 素材，可直接点选启用；自己添加的素材会显示在下面，长按可删除。", "Nine default Lottie JSON assets are included and can be enabled directly. Added assets appear below and can be deleted by long pressing.");
        add("已保存，重启 BiometricSetting 后生效", "Saved. Takes effect after restarting BiometricSetting.");
        add("保存失败，请换一个文件再试", "Saving failed. Try another file.");
        add("无法打开链接", "Unable to open link");
        add("无法打开文件选择器", "Unable to open the file picker");
        add("状态：已启用。修改后建议强制停止或重启 BiometricSetting / 重启手机后测试。", "Status: enabled. After changes, force-stop or restart BiometricSetting, or restart the phone before testing.");
        add("自定义素材，循环预览，长按删除", "Custom asset, loop preview, long press to delete");
        add("默认素材，不能删除", "Default asset, cannot be deleted");
        add("默认素材不能删除", "Default assets cannot be deleted");
        add("删除素材", "Delete Asset");
        add("确定删除这个自定义素材吗？\n", "Delete this custom asset?\n");
        add("删除", "Delete");
        add("已删除素材", "Asset deleted");
        add("删除失败", "Deletion failed");
        add("PNG 静态图", "PNG Static Image");
        add("Lottie JSON 动画", "Lottie JSON Animation");

        // Home icon customization actions and fields.
        add("自定义桌面名称", "Custom Home Label");
        add("恢复名称", "Restore Label");
        add("自定义名称颜色", "Custom Label Color");
        add("颜色格式无效，请使用 #RRGGBB", "Invalid color format. Use #RRGGBB.");
        add("恢复默认颜色", "Restore Default Color");
        add("纯色", "Solid Color");
        add("留空表示默认颜色", "Leave blank for default color");
        add("启用渐变色", "Enable Gradient Color");
        add("渐变颜色", "Gradient Colors");
        add("系统字体", "System Font");
        add("选择字体文件", "Choose Font File");
        add("清除字体文件", "Clear Font File");
        add("加粗", "Bold");
        add("斜体", "Italic");
        add("自定义名称样式", "Custom Label Style");
        add("恢复默认样式", "Restore Default Style");
        add("恢复图标", "Restore Icon");
        add("全部恢复", "Restore All");
        add("恢复默认", "Restore Defaults");
        add("更换图标", "Change Icon");
        add("选图标", "Choose Icon");
        add("改名字", "Edit Label");
        add("设名字", "Set Label");
        add("改样式", "Edit Style");
        add("字体", "Font");
        add("恢复", "Restore");
        add("正在重启 One UI 主屏幕", "Restarting One UI Home");
        add("已重启 One UI 主屏幕", "One UI Home restarted");
        add("已请求重启，请返回桌面查看", "Restart requested. Return to Home to check.");
        add("默认", "Default");
        add("文件字体 ", "Font file ");
        add("纯色 ", "Solid color ");
        add("渐变 ", "Gradient ");

        // Dynamic values rendered with variable data.
        addFragment("视频默认码率\n", "Default Video Bitrates\n");
        addFragment("尚未检测到", "Not detected yet");
        addFragment("当前启用：", "Enabled now: ");
        addFragment("保持系统默认，只记录检测结果。", "Using system defaults; detection results are recorded only.");
        addFragment("系统 pass_through 当前值：", "Current system pass_through value: ");
        addFragment("当前估算：", "Current estimate: ");
        addFragment("样本：", "Samples: ");
        addFragment("窗口：", "Window: ");
        addFragment("刷新时间: ", "Refreshed: ");
        addFragment("Limiter 可用: ", "Limiter available: ");
        addFragment("总禁用温控降频: ", "Thermal throttling master disabled: ");
        addFragment("亮度温控限制关闭: ", "Thermal brightness limit disabled: ");
        addFragment("CP 温控限制关闭: ", "CP thermal limit disabled: ");
        addFragment("当前亮度限制: ", "Current brightness limit: ");
        addFragment("当前 HRR 限制: ", "Current HRR limit: ");
        addFragment("当前 CP Low Mode: ", "Current CP low mode: ");
        addFragment("当前 CP Cooling: ", "Current CP cooling: ");
        addFragment("历史:", "History:");
        addFragment("当前文件：", "Current file: ");
        addFragment("类型：", "Type: ");
        addFragment("已选择：", "Selected: ");
        addFragment("已启用素材：", "Enabled asset: ");
        addFragment("自定义文件：", "Custom file: ");
        addFragment("字体文件：", "Font file: ");
        addFragment("原名：", "Original label: ");
        addFragment("名称样式：", "Label style: ");
        addFragment("系统插入音：", "System connect sound: ");
        addFragment("普通：", "Normal: ");
        addFragment("快充：", "Fast charging: ");
        addFragment("系统拔出音：", "System disconnect sound: ");
        addFragment("系统未发现独立的充电拔出提示音，可使用自定义拔出音", "No separate system disconnect sound was found; a custom disconnect sound can be used.");
    }

    private LanguageManager() {
    }

    public static boolean isEnglish(Context context) {
        if (context == null) {
            return false;
        }
        try {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ENGLISH, false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setEnglish(Context context, boolean english) {
        if (context == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENGLISH, english)
                .apply();
    }

    public static String text(Context context, String source) {
        if (source == null || !isEnglish(context)) {
            return source;
        }
        String translated = ENGLISH.get(source);
        if (translated != null) {
            return translated;
        }
        String result = source;
        for (Map.Entry<String, String> entry : ENGLISH_FRAGMENTS.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public static void applyToActivity(final Activity activity) {
        if (activity == null || !isEnglish(activity)) {
            return;
        }
        CharSequence title = activity.getTitle();
        if (title != null) {
            activity.setTitle(text(activity, title.toString()));
        }
        View decor = activity.getWindow() != null ? activity.getWindow().getDecorView() : null;
        if (decor == null) {
            return;
        }
        applyToViewTree(activity, decor);
        decor.post(new Runnable() {
            @Override
            public void run() {
                View current = activity.getWindow() != null
                        ? activity.getWindow().getDecorView() : null;
                if (current != null) {
                    applyToViewTree(activity, current);
                }
            }
        });
    }

    public static void applyToViewTree(Context context, View view) {
        if (view == null || !isEnglish(context)) {
            return;
        }
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence value = textView.getText();
            if (!(textView instanceof EditText) && value != null) {
                String translated = text(context, value.toString());
                if (!translated.equals(value.toString())) {
                    textView.setText(translated);
                }
            }
            CharSequence hint = textView.getHint();
            if (hint != null) {
                String translatedHint = text(context, hint.toString());
                if (!translatedHint.equals(hint.toString())) {
                    textView.setHint(translatedHint);
                }
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToViewTree(context, group.getChildAt(i));
            }
        }
    }

    private static void add(String chinese, String english) {
        ENGLISH.put(chinese, english);
    }

    private static void addFragment(String chinese, String english) {
        ENGLISH_FRAGMENTS.put(chinese, english);
    }
}
