# 三星录屏原画

在三星截屏的“视频质量”下拉列表末尾添加“原画 / Original”，保留原来的三项。作用域为 com.samsung.android.app.smartcapture；模块首页提供系统设置入口。

原画标志使用三星截屏自己的 screenrecorder_preferences 中独立的 sfe_native_quality 布尔值，不给三星原有 video_quality 写入无法解析的字符串。选择原来的质量选项会关闭原画。

全屏以 Display.getRealSize 得到的当前系统分辨率建立采集表面，跳过原 1080p 尺寸缩小；区域录制沿用三星的选区坐标和 OpenGL 裁切，在原生尺寸基础上得到选区输出像素。编码器要求时向下做最小尺寸对齐；极小或硬件不支持的选区不保证可录制。

NativeRecordingProfile 查询硬件 AVC 编码器在指定尺寸下的帧率和码率范围：帧率取显示屏支持上限与编码器上限的较小值，再取最高受支持码率。去掉原固定 AVC Main / Level 4 限制，并给录屏虚拟显示请求相应刷新率。仍是 AVC 压缩录像，“原画”指原生像素尺寸，并不等于数学无损。

2026-10-27，SM-S9280 / One UI 8.5、三星截屏 6.1.80.31：硬件 c2.qti.avc.encoder 接受 1440×3120、120 fps、220000000 bit/s，也接受 800×600 同帧率码率；已通过真实 MediaCodec configure / createInputSurface / start / stop 检查。1.30 / 194 已构建、签名校验并安装。此检查不等于已验证三星录屏实际输出文件的尺寸、平均帧率、画面帧数或持续稳定性；这些仍需实录验证。实际码率会随编码器与画面复杂程度变化。

构建脚本 build-v98.ps1；算法与设备检查入口 tests/NativeRecordingDeviceTest.java。该功能没有定时轮询任务。
