# 1.29.1：语言与桌面图标形状

S Pen 空中鼠标和小窗等比例缩放现在使用模块的中英文偏好，包括首页名称和说明、页面标题、滑块数值、按钮、实时状态、计数与错误提示。页面恢复前台时检查语言是否变化并按需重建，避免保留旧语言。

图标现象在 One UI Home 17.5.05.29 上可见：中国农业银行、中国建设银行、有看头等图标仍是方形，其他图标已带圆角。应用没有模块自定义图标文件，因此不只是自定义图片裁剪的问题。

当前桌面的 IconViewImpl.setIcon 最后经 setIconIntoPosition → setCompoundDrawable 绑定 Drawable，没有在最终位置统一补蒙版。不同来源图标本来可能已带圆角，也可能是完整不透明位图。仅凭此现象不能认定系统崩溃或渲染故障；本版补上形状兼容处理。

LauncherIconShapeHook 仅在 One UI Home 主进程的图标最终绑定处处理 BitmapDrawable。四角均不透明的方形图标使用 AdaptiveIconDrawable.getIconMask 返回的系统形状裁剪；已有透明圆角、异形图标、其他 Drawable 保留原样。保留尺寸、透明度、色彩滤镜及 ConstantState 克隆行为，不改变应用的图标文件、桌面数据库或触摸区域。

识别缓存使用弱 Bitmap 引用及 generationId，位图更新会重新判定；不复制普通图标位图，不添加定时任务。硬件位图仅在首次识别时临时复制用于读取边角，随后释放副本。

检查：FeatureLanguageTest 覆盖两页 37 个中文 UI 字面量、动态错误与数值、中英文保留；IconMaskDeviceTest 在连接手机验证方形角透明、中心颜色保留、无重复包装、克隆仍裁剪、已圆角不变、位图修改后缓存失效，以及包装前已存在的绘制边界保留。真机截图已确认农业银行、建设银行、有看头等图标正确显示并带系统圆角；两页英文实时文案已通过 UI 层级核对，随后恢复原中文偏好。

构建：build-v97.ps1；产物 SamsungFeatureExtension-v1.29.1-Bilingual-IconShape.apk。
