# S Pen 空中鼠标

## 实现

- 设置入口：模块首页 → S Pen 空中鼠标，默认关闭，25–300% 灵敏度。需要授予模块 Root 权限。
- 作用域：`com.samsung.android.service.aircommand`，已同步 manifest、xposed_scope、scope.list 与 xposed_init。
- 运行于 AirCommand 主进程，按稳定 AIDL descriptor 发现 `ISPenRemoteService`，注册 BUTTON=0 / AIR_MOTION=1。
- 使用三星 SDK 的连续相对位移，将数值转换为 EV_REL 鼠标位移；保留小数增量，交给系统管理指针速度/加速和屏幕边界。这是转动笔身/手腕的相对控制，并非光学跟踪笔尖的绝对位置。
- 1.28.2 增加 0–100% 自适应低通防抖，默认 50%；0 为原始直通。过滤的是累计相对位置，小幅抖动使用较大时间常数、快速移动减小时间常数。没有硬死区或速度外推，保留亚像素累积，不增加传感器信息。
- 有待输出位移时以 8 ms 目标间隔细分发送，实际节奏仍受 Android 调度、整数位移量化和系统指针加速影响，不保证固定 125 Hz。停止输入后最多 160 ms 收敛；按钮边界前排空旧位移，防止松开后继续拖动；关闭模式立即丢弃待处理位移。
- 设置页显示近期 SDK 接收次数/秒与非零位移提交次数/秒，计数在 AirCommand 层，非内核实际消费率。必须持续移动时观察；静止期间均下降，不应将此读数当作硬件采样率。
- AirCommand 的两组 SDK 回调表在鼠标活动期间将前台应用视为 SDK 输入消费者，阻止原隔空动作重复执行。
- 当前版本不使用自绘悬浮光标，也不调用 injectInputEvent 模拟手指。模块运行 root `/system/bin/uinput -`，创建名称为 `SFE S Pen Mouse <session>` 的 EV_REL / BTN_LEFT 设备。Android InputReader 识别为 MOUSE 并显示原生鼠标光标。
- 按钮 DOWN/UP 转换为鼠标左键按下/松开。按住时仍发送位移，支持原生鼠标拖动和长按后拖拽。10 秒安全超时释放左键。
- 关闭、插笔、锁屏、息屏或服务销毁时关闭私有管道，uinput 读到 EOF 后移除设备并清理状态；注销 SDK 回调，让原服务恢复传感器默认模式。
- 配置通知加 2 秒轮询兜底。只允许模块 UID 写配置，只允许 AirCommand UID 报告状态和取得鼠标管道；管道仅接受限幅相对移动与左键状态，不接收任意 shell 命令。外部应用不能直接注入坐标。
- 不写死混淆类名。无法识别接口结构时停用并报告原因；不等于已经验证所有 S22/S23/S24 固件。

## 当前测试设备与证据

- SM-S9280，AirCommand 7.8.18.44，root + LSPosed。
- 设备确认 AirCommand 具有 INJECT_EVENTS 和 INTERNAL_SYSTEM_WINDOW 权限。
- 反编译参考：`analysis/spen-mouse/apktool/smali/.../BleSpenSimplePointerShowService.smali` 和 `RemoteSpenInternalBindingService.smali`。
- 基础版 1.28 / 189 已成功构建、APK v1/v2/v3 签名校验、ADB 覆盖安装。拖拽修订版本为 1.28.1 / 190。
- `tests/SpenMouseMotionTest.java` 验证方向、灵敏度、分数累积、按住按钮仍可移动且不释放按钮、非有限输入。
- 真机已确认 hook 加载、SDK 两组回调注册，以及真实按钮/位移事件到达。
- 已发现并删除重复 SENSOR_ON 请求：首次成功，重复 BLE 请求 status=133，导致连接/持续移动异常。由 SDK 注册唯一负责开启传感器。
- 外部 shell 调用配置 set、伪造运行状态 status 均被 SecurityException 拒绝。
- 旧自绘光标方案已由用户确认移动正常，但点击被 Android 的不可信遮挡检查丢弃。依照用户要求，改为系统原生鼠标，不关闭全局遮挡保护。
- 原生接口预检已验证内核设备 CURSOR | EXTERNAL、Android Sources=MOUSE、系统 PointerController 显示，以及关闭输入管道后设备被移除。
- 用户已确认 1.28.1 可以操作及按住移动，随后要求增加防抖和平滑。
- 本机 AirCommand 7.8.18.44 的反编译文件 `analysis/spen-mouse/apktool/smali/F2/o.smali:2604` 中，SDK 位移回调对 elapsedRealtime 差值比较 `0x23`（35 ms），不足时累积到 Point；连续 SDK 位移发送理论上限约 28.6 次/秒（按钮会重置计时）。这是已确认的软件限速，不是 BLE 或传感器硬件上限的证据。本版没有修改三星的这一限速或传感器命令。
- 合成测试：单次 35 单位位移拆成 6 个非零事件，总位移仍为 35；正负交替抖动样本在 50% 强度下行程从 160 降到 81，最终回到原点。另覆盖多强度、双轴、变间隔、微小位移、按键边界、关闭/重置、非有限数值。这些是算法测试，不等于真实手部抖动或主观跟手性验收。

## 构建和检查

`./build-v95.ps1`

产物：`SamsungFeatureExtension-v1.28.2-SpenAirMouse-Smooth.apk`。

读取运行状态：

```text
adb shell content call --uri content://com.samsung.feature.extension.spenmouse --method get
```

开启后检查 `dumpsys input` 的 `SFE S Pen Mouse <session>` 来源为 MOUSE；`dumpsys window` 中不应有旧自绘光标窗口。关闭后虚拟鼠标设备应消失。

官方接口说明：https://developer.samsung.com/galaxy-spen-remote/s-pen-remote-sdk.html

系统 uinput 协议：https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-14.0.0_r58/cmds/uinput/
