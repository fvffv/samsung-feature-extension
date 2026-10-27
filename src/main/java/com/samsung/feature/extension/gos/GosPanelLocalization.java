package com.samsung.feature.extension.gos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * Localizes GOS's non-exported engineering panel without changing the system GOS APK.
 * The module UI supplies the language for each panel launch through its explicit bridge.
 */
final class GosPanelLocalization {
    private static final String GOS_PACKAGE = "com.samsung.android.game.gos";
    private static final Map<String, String> CHINESE = new HashMap<>();
    private static final Map<String, String> LITERALS = new HashMap<>();
    private static final Map<String, String> ENGLISH = new HashMap<>();
    private static volatile boolean installed;
    private static volatile boolean chineseEnabled;

    static {
        add("abc_action_bar_up_description", "向上返回");
        add("abc_action_mode_done", "完成");
        add("abc_capital_off", "关闭");
        add("abc_capital_on", "开启");
        add("abc_menu_alt_shortcut_label", "Alt+");
        add("abc_menu_ctrl_shortcut_label", "Ctrl+");
        add("abc_menu_delete_shortcut_label", "删除");
        add("abc_menu_enter_shortcut_label", "回车");
        add("abc_menu_function_shortcut_label", "功能键+");
        add("abc_menu_meta_shortcut_label", "Meta+");
        add("abc_menu_shift_shortcut_label", "Shift+");
        add("abc_menu_space_shortcut_label", "空格");
        add("abc_menu_sym_shortcut_label", "符号+");
        add("abc_prepend_shortcut_label", "菜单+");
        add("abc_search_hint", "搜索…");
        add("abc_searchview_description_clear", "清除搜索内容");
        add("abc_searchview_description_search", "搜索");
        add("abc_searchview_description_submit", "提交搜索");
        add("abc_searchview_description_voice", "语音搜索");
        add("abc_toolbar_collapse_description", "收起");
        add("_default", "默认");
        add("app_icon", "应用图标");
        add("app_name", "游戏优化服务");
        add("apply", "应用");
        add("apply_json_to_all_games", "将 JSON 应用于所有游戏");
        add("automatic_update_with_server_data", "使用服务器数据自动更新");
        add("available", "可用");
        add("boost_bus_index", "总线索引");
        add("boost_cpu_index", "CPU 索引");
        add("boost_get_supported_frequency_bus", "获取支持频率：总线");
        add("boost_get_supported_frequency_cpu", "获取支持频率：CPU");
        add("build_type", "构建类型");
        add("bus_control", "总线控制");
        add("caution_it_will_stop_running_apps_if_you_change_values", "注意：修改数值会强制停止正在运行的应用");
        add("change_mode_guide", "！！！请切换到“均衡”模式后再测试软件包数值！！！");
        add("choose_a_server_to_connect", "选择要连接的服务器。");
        add("choose_a_target_package", "选择目标应用包。");
        add("clear_logcat", "清除日志");
        add("current_value", "当前值");
        add("custom_dfs", "自定义 DFS：");
        add("custom_dfs_mode", "自定义 DFS 模式：");
        add("custom_dss", "自定义 DSS：");
        add("custom_package_values", "自定义应用包数值");
        add("custom_resolution_mode", "自定义分辨率模式：");
        add("debug_and_record_mode", "调试与记录模式");
        add("debug_mode", "调试模式");
        add("debugging_options_will_only_have_effect_until_next_gos_reboot", "调试选项仅在下次 GOS 重启前有效");
        add("delete_normal_update_alarm", "删除普通更新闹钟");
        add("delete_retry_update_alarm", "删除重试更新闹钟");
        add("device", "设备");
        add("device_name", "设备名称");
        add("dfs", "DFS");
        add("duration_sec", "持续时间（秒）");
        add("enable_feature", "启用功能");
        add("enable_interpolation", "启用插帧");
        add("enable_interpolation_tooltip", "未启用此开关时，下方设置不会生效。");
        add("enabled", "已启用");
        add("enabled_flag_by_user", "由用户启用");
        add("fake_device_name_n_only_for_server_api", "伪装设备名称（仅用于服务器 API）");
        add("feature", "功能");
        add("feature_name", "功能名称");
        add("game_play_time_mode", "游戏时长模式");
        add("gamemanager_ver", "游戏管理器版本");
        add("gfi", "帧率增强器");
        add("gfi_apply_watchdog_max_fps_limit", "应用看门狗最高 FPS 限制（防止画面损坏）");
        add("gfi_apply_watchdog_max_fps_limit_tooltip", "LSI 芯片组的帧率超过 70 时关闭帧率增强器，以避免画面损坏问题。");
        add("gfi_default_version_text", "GFI 1.1.0 - 发布版");
        add("gfi_delay", "延迟");
        add("gfi_dfs_maximum", "DFS 最大值：");
        add("gfi_dfs_offset", "DFS 偏移量：");
        add("gfi_dfs_smoothness", "DFS 平滑度：");
        add("gfi_enable_external_control", "启用外部控制");
        add("gfi_enable_fb_recording", "启用会话记录");
        add("gfi_enable_game_fps_offset", "启用游戏 FPS 偏移量");
        add("gfi_enable_smart_delay", "启用智能延迟");
        add("gfi_fill_in", "填入");
        add("gfi_game_fps", "游戏 FPS");
        add("gfi_gfps_offset", "GFPS 偏移量");
        add("gfi_interpolation_data_collection_mode", "数据收集模式：");
        add("gfi_interpolation_mode", "插帧模式：");
        add("gfi_keep_two_hwc_layers", "保持两个 HWC 图层");
        add("gfi_keep_two_hwc_layers_tooltip", "根据插帧需要在一个和两个 HWC 图层间切换，或始终保持两个图层。");
        add("gfi_latency_reduction", "延迟降低");
        add("gfi_log_level_spinner_tooltip", "选择要输出到日志的详细程度。");
        add("gfi_log_level_tooltip", "选择要输出到日志的详细程度。");
        add("gfi_max_consec_gl_compositions", "最大连续 GL 合成次数");
        add("gfi_max_pc_gl_compositions", "最大 GL 合成比例");
        add("gfi_maximum_version", "GFI 最高版本");
        add("gfi_min_acceptable_fps", "最低可接受 FPS");
        add("gfi_min_delay", "最小延迟");
        add("gfi_min_regal_stability", "最低 ReGaL 稳定性");
        add("gfi_minimum_gfps", "最低 GFPS");
        add("gfi_minimum_version", "GFI 最低版本");
        add("gfi_ml_latency_reduction", "机器学习延迟降低");
        add("gfi_mode_none", "不进行超分");
        add("gfi_no_interp_with_extra_layers", "额外 HWC 图层时不插帧");
        add("gfi_retime", "重定时");
        add("gfi_smart_delay_adjustment_rate", "智能延迟调整速率（秒）");
        add("gfi_smart_delay_adjustment_target", "智能延迟调整目标");
        add("gfi_smart_delay_change_rate", "智能延迟变化速率（毫秒）");
        add("gfi_smart_delay_threshold", "智能延迟阈值");
        add("gfi_unlimited", "不限制");
        add("gfi_version", "GFI 版本：");
        add("gfi_version_tooltip", "框架中安装的 GFI 版本。格式：主版本.次版本.修订版本 - 分支 校验和。");
        add("gfi_watermark_flag_CC_HWC", "CC/HWC");
        add("gfi_watermark_flag_CC_HWC_tooltip", "使用客户端合成器还是硬件合成器");
        add("gfi_watermark_flag_FB_OFF_ON", "帧率增强器 开/关");
        add("gfi_watermark_flag_FB_OFF_ON_tooltip", "帧率增强器是否已禁用或启用");
        add("gfi_watermark_flag_INTERPOLATED_HIT", "已插帧/命中");
        add("gfi_watermark_flag_INTERPOLATED_HIT_tooltip", "当前帧是否为插帧帧或命中帧");
        add("gfi_watermark_flags", "水印标识");
        add("gfi_write_to_frame_tracker", "写入帧追踪器");
        add("global_settings", "全局设置：");
        add("gos_routine_action_label", "游戏工坊");
        add("gos_ver", "GOS 版本");
        add("intelligence_to_use", "选择智能策略：");
        add("ipm_1_loglevel", "+1 日志级别");
        add("ipm_allow_ipm_during_charging", "充电时允许 SPA");
        add("ipm_cluster_checkbox_input_text", "等级/数值：");
        add("ipm_cluster_radio_level", "等级");
        add("ipm_cluster_radio_none", "无");
        add("ipm_cluster_radio_value", "数值");
        add("ipm_create_training_data_first_time", "创建训练数据（首次）");
        add("ipm_delete_training_data", "删除训练数据");
        add("ipm_enable_allow_ml_off", "启用允许关闭机器学习");
        add("ipm_enable_bus_freq", "启用总线频率");
        add("ipm_enable_bus_min_freq_control", "启用自动最低频率控制");
        add("ipm_enable_cpu_minlock", "启用 CPU 最低频率锁定");
        add("ipm_enable_custom_tfps", "启用自定义 TFPS");
        add("ipm_enable_frame_interpolation", "启用插帧");
        add("ipm_enable_frame_interpolation_tooltip", "插帧使用 GFI 提升可见帧率。GFI 帧率会按目标帧率计算。");
        add("ipm_enable_gpu_minlock", "启用 GPU 最低频率锁定");
        add("ipm_enable_lrpst", "启用 LRPST");
        add("ipm_enabled_locally", "本地已启用 SPA");
        add("ipm_enter_gpu_min_boost", "输入 GPU 最低加速值");
        add("ipm_frame_interpolation_decay_half_life", "衰减半衰期");
        add("ipm_frame_interpolation_decay_half_life_tooltip", "GFI 帧率衰减至新数值一半所需的秒数，可使帧率变化更平滑。");
        add("ipm_frame_interpolation_frame_rate_offset", "帧率偏移量");
        add("ipm_frame_interpolation_frame_rate_offset_tooltip", "加到计算出的 GFI 帧率上，使 GFI 帧率始终高于目标帧率固定数值。");
        add("ipm_frame_interpolation_temperature_offset", "温度偏移量");
        add("ipm_frame_interpolation_temperature_offset_tooltip", "从用于计算 GFI 帧率的设备温度中减去。增大后，GFI 帧率会在更高温度时才下降。");
        add("ipm_ignore_white_list", "忽略白名单");
        add("ipm_intel_apply_json", "应用 JSON");
        add("ipm_intel_cluster0", "集群 0");
        add("ipm_intel_cluster1", "集群 1");
        add("ipm_intel_cluster2", "集群 2");
        add("ipm_intel_cluster_warning", "警告：此设备不支持集群控制，请检查设备配置。");
        add("ipm_intel_json_config_hint", "在此输入智能策略 JSON 配置");
        add("ipm_intel_validate_json", "验证 JSON");
        add("ipm_last_run_statistics_from_the_ml_core", "机器学习核心上次运行统计");
        add("ipm_mode", "SPA 模式");
        add("ipm_mode_force", "强制使用此模式进行测试");
        add("ipm_only_capture_to_file_ipm_will_not_run", "仅捕获到文件（SPA 不会运行！）");
        add("ipm_pull_training_data", "拉取训练数据");
        add("ipm_push_training_data", "推送训练数据");
        add("ipm_revert_to_siop_if_high_temperature", "高温时回退到 SIOP");
        add("ipm_run_in_any_mode_w_wo_gamelauncher", "在任意模式运行（有/无游戏中心）");
        add("ipm_set_the_gpu_min_boost", "设置 GPU 最低加速值");
        add("ipm_stability_mode", "稳定性模式");
        add("ipm_status_of_spa", "SPA 状态");
        add("ipm_supertrain_mode", "超级训练模式");
        add("ipm_target_lrpst", "目标 LRPST");
        add("ipm_target_pst", "目标 PST");
        add("ipm_use_intel_json_config", "使用 JSON 配置智能策略");
        add("item_by_interface", "按接口");
        add("json_test_fragment_clear_siop_policy", "清除 SIOP 策略");
        add("json_test_fragment_game_performance_siop", "游戏性能 SIOP");
        add("json_test_fragment_game_sdk_switch", "开关 GameSDK");
        add("json_test_fragment_game_sdk_title", "GameSDK");
        add("json_test_fragment_set_global_json_policy", "设置全局策略");
        add("json_test_fragment_siop_cpu", "CPU");
        add("json_test_fragment_siop_default_level", "默认等级：");
        add("json_test_fragment_siop_default_mode", "默认模式：");
        add("json_test_fragment_siop_gpu", "GPU");
        add("last_automatic_full_update", "上次自动完整更新");
        add("last_automatic_update", "上次自动更新");
        add("latency_reduction", "延迟降低");
        add("launch_the_target_app", "启动目标应用");
        add("launch_the_target_application", "启动目标应用。");
        add("launchboost_test", "启动加速测试");
        add("log_level", "日志级别");
        add("model_name", "型号名称");
        add("navigation_drawer_close", "关闭导航抽屉");
        add("navigation_drawer_open", "打开导航抽屉");
        add("no_value", "无数值");
        add("normal_mode", "普通模式");
        add("not_available", "不可用");
        add("off_mode", "关闭模式");
        add("original_device_name", "原始设备名称");
        add("os_sdk_version", "系统 SDK 版本");
        add("os_version_incremental", "系统增量版本");
        add("os_version_release", "系统发布版本");
        add("package_spinner_tooltip", "选择要修改设置的游戏/应用包。");
        add("page_name_common_features_test", "常用功能测试");
        add("page_name_device_info", "设备信息");
        add("page_name_galaxy_apps", "Galaxy 应用");
        add("page_name_gfi_test", "GFI 测试");
        add("page_name_global_feature_flags", "全局功能标志");
        add("page_name_ipm_test", "SPA 测试");
        add("page_name_json_features_test", "JSON 功能测试");
        add("page_name_mode_test", "SIOP 模式测试");
        add("page_name_resume_boost", "恢复加速");
        add("page_name_server_api_test", "服务器 API 测试");
        add("page_name_update_alarm", "更新闹钟");
        add("password_toggle_content_description", "显示密码");
        add("performance_mode", "性能模式");
        add("refresh", "刷新");
        add("registered_in_server", "已在服务器注册");
        add("reset", "重置");
        add("reset_1", "重置（-1）");
        add("reset_full_update_time", "重置完整更新时间");
        add("reset_normal_update_time", "重置普通更新时间");
        add("restore", "还原");
        add("restore_all_data", "将所有数据还原为服务器数据");
        add("restore_all_data_description", "将全局和全部应用包数据还原为服务器数据");
        add("restore_global_data", "还原全局数据");
        add("restore_global_data_including_feature_flags", "还原包含功能标志的全局数据");
        add("restore_global_data_to_server_data", "将全局数据还原为服务器数据");
        add("restore_target_package_data_to_server_data", "将目标应用包数据还原为服务器数据");
        add("restore_to_the_original_name", "还原原始名称");
        add("routine_action_description", "游戏设置");
        add("seekbar_thread_control", "启用自动线程分配");
        add("select_target_package", "选择目标应用包");
        add("send_all_package_data_from_local_database_to_logcat", "将本地数据库中的全部应用包数据输出到日志");
        add("send_detail_package_data_to_logcat", "将详细应用包数据输出到日志");
        add("send_normal_update_alarm", "发送普通更新闹钟");
        add("send_retry_update_alarm", "发送重试更新闹钟");
        add("send_the_detail_target_package_data_from_the_local_db_to_logcat", "将本地数据库中的目标应用包详细数据输出到日志。");
        add("send_wifi_update_intent", "发送 Wi-Fi 更新意图");
        add("server_development", "开发环境");
        add("server_flag", "服务器标志");
        add("server_flag_settings_currently_on_effect_change_them_on_global_feature_flags", "当前生效的服务器标志设置（可在“全局功能标志”中更改）");
        add("server_policy", "服务器策略");
        add("server_product", "正式环境");
        add("server_qa", "测试环境");
        add("set", "设置");
        add("set_the_device_name", "设置设备名称");
        add("set_the_global_data_in_the_local_db", "设置本地数据库中的全局数据");
        add("set_the_target_package_data_in_the_local_db", "设置本地数据库中的目标应用包数据");
        add("show_all_package_data", "将全部应用包数据输出到日志");
        add("show_logcat", "显示日志");
        add("siop_mode_test", "SIOP 模式测试");
        add("start", "开始");
        add("start_the_target_game", "启动目标游戏");
        add("state", "状态");
        add("switch_network_connection", "切换自动更新（建议测试时关闭。）");
        add("target_current_control_will_limit_above_these_levels_soft_limits_expect_up_to_10", "目标电流控制：高于这些等级时会限制；为软限制，预期最高可达 10% 以上。");
        add("thread_control", "线程控制");
        add("title_logcat", "[日志]");
        add("unable", "无法执行");
        add("unit_d_c", "℃");
        add("unit_ma", "毫安");
        add("update_time_by_server_api", "通过服务器 API 更新时间");
        add("upload_data", "上传数据");
        add("upload_data_to_the_server_report_ringlog", "上传数据到服务器（报告、环形日志）");
        add("use_json_pkg_config", "使用 JSON 应用包配置");
        add("use_qa_server", "使用测试服务器");
        add("using_pkg_value", "使用应用包值");
        add("using_user_value", "使用用户值");
        add("uuid", "UUID");
        add("value_to_set", "要设置的数值");
        add("values", "数值");
        add("watchdog_expire", "看门狗超时（分钟）");
        add("watchdog_expire_tooltip", "未启用此开关时，下方设置不会生效。");
        add("you_can_test_the_global_feature_flags", "可在此测试全局功能标志");

        literal("The target app will be forced to stop. Is it OK?", "将强制停止目标应用，是否继续？");
        literal("All apps will be forced to stop. Is it OK?", "将强制停止所有应用，是否继续？");
        literal("Needs to stop the app", "需要停止应用");
        literal("SIOP_MODE is not available.", "SIOP 模式不可用。");
        literal("SIOP_MODE is not enabled.", "SIOP 模式未启用。");
        literal("Device name", "设备名称");
        literal("Failed. Please check your network.", "失败，请检查网络连接。");
        literal("Please wait.", "请稍候。");
        literal("Restored", "已还原");
        literal("successful", "成功");
        literal("wrong params", "参数错误");
        literal("CPU level (", "CPU 等级（");
        literal("DFS (1.0~120.0)", "DFS（1.0～120.0）");
        literal("DSS (1.0~100.0)", "DSS（1.0～100.0）");
        literal("DSS for the 1st OS Resolution (1.0~100.0)", "第一系统分辨率的 DSS（1.0～100.0）");
        literal("The app will be forced to stop. Is it OK?", "将强制停止该应用，是否继续？");
        literal("The value was applied. All running apps were stopped.", "数值已应用，所有正在运行的应用已停止。");
        literal("Apply the JSON file in \"/IPM/all_games.json\" on the external storage to all games JSON.", "将外部存储 /IPM/all_games.json 中的 JSON 配置应用到所有游戏。");
        literal("Enter a valid JSON to configure Intelligence.", "请输入有效的 JSON 以配置智能策略。");
        literal("The dir could not created. Please check application permission", "无法创建目录，请检查应用权限。");
        literal("dir could not created. Please check application permission", "无法创建目录，请检查应用权限。");
        literal("Please change the state to proper one before switching the enabled feature flag.", "切换已启用的功能标志前，请先将状态调整为正确值。");
        literal("RESUME_BOOST is not enabled.", "恢复加速未启用。");
        literal("mPkg is null", "未选择应用包。");
        literal("pkg is null", "未选择应用包。");
        literal("rootView is null", "页面视图不可用。");
        literal("not set", "未设置");
        literal("OFF", "关闭");
        literal("ON", "开启");
        literal("Shift Temperature", "温度偏移");
        literal("Target Short Side", "目标短边");
        literal("Target Short Side (1~", "目标短边（1～");
        literal("VRR max (48, 60, 96, 120)", "VRR 最大值（48、60、96、120）");
        literal("VRR min (48, 60, 96, 120)", "VRR 最小值（48、60、96、120）");
        literal("TSP policy (JSON)", "TSP 策略（JSON）");
        literal("siop_mode_policy (JSON)", "SIOP 模式策略（JSON）");
        literal("SiopMode", "SIOP 模式");
    }

    private GosPanelLocalization() {
    }

    static void setUseEnglish(boolean useEnglish) {
        chineseEnabled = !useEnglish;
    }

    static void install(Context context) {
        if (installed) {
            return;
        }
        synchronized (GosPanelLocalization.class) {
            if (installed) {
                return;
            }
            try {
                buildLiteralDictionary(context);
                XposedHelpers.findAndHookMethod(Resources.class, "getText", Integer.TYPE,
                        new ResourceHook(null));
                XposedHelpers.findAndHookMethod(Resources.class, "getString", Integer.TYPE,
                        new ResourceHook(null));
                XposedHelpers.findAndHookMethod(Resources.class, "getString", Integer.TYPE,
                        Object[].class, new ResourceHook(Object[].class));
                XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                        TextView.BufferType.class, new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(TextView.class, "setText", Integer.TYPE,
                        new ResourceTextViewHook(false));
                XposedHelpers.findAndHookMethod(TextView.class, "setHint", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(TextView.class, "setHint", Integer.TYPE,
                        new ResourceTextViewHook(true));
                XposedHelpers.findAndHookMethod(View.class, "setContentDescription", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(Activity.class, "setTitle", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(Activity.class, "setTitle", Integer.TYPE,
                        new ActivityTitleResourceHook());
                XposedHelpers.findAndHookMethod(AlertDialog.Builder.class, "setTitle", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(AlertDialog.Builder.class, "setMessage", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(ProgressDialog.class, "setMessage", CharSequence.class,
                        new TextArgumentHook(0));
                XposedHelpers.findAndHookMethod(Toast.class, "makeText", android.content.Context.class,
                        CharSequence.class, Integer.TYPE, new TextArgumentHook(1));
                XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate", Integer.TYPE,
                        ViewGroup.class, Boolean.TYPE, new InflateHook());
                XposedHelpers.findAndHookMethod(LayoutInflater.class, "inflate", Integer.TYPE,
                        ViewGroup.class, new InflateHook());
                installActivityTraversal(context);
                installed = true;
            } catch (Throwable ignored) {
                // The panel stays usable in its original language if a future GOS build changes APIs.
            }
        }
    }

    private static final class ResourceHook extends XC_MethodHook {
        private final Class<?> formatArgumentType;

        ResourceHook(Class<?> formatArgumentType) {
            this.formatArgumentType = formatArgumentType;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (!chineseEnabled || !(param.thisObject instanceof Resources)
                    || param.args == null || param.args.length == 0
                    || !(param.args[0] instanceof Integer)) {
                return;
            }
            Object[] formatArgs = null;
            if (formatArgumentType != null && param.args.length > 1
                    && param.args[1] instanceof Object[]) {
                formatArgs = (Object[]) param.args[1];
            }
            Object translated = translateResource((Resources) param.thisObject,
                    ((Integer) param.args[0]).intValue(), formatArgs);
            if (translated != null) {
                param.setResult(translated);
            }
        }
    }

    private static final class TextArgumentHook extends XC_MethodHook {
        private final int index;

        TextArgumentHook(int index) {
            this.index = index;
        }

        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            if (param.args != null && param.args.length > index
                    && param.args[index] instanceof CharSequence) {
                param.args[index] = translateLiteral((CharSequence) param.args[index]);
            }
        }
    }

    /**
     * XML attributes are resolved by AppCompat after Resource#getText has already returned.  This
     * hook therefore reads the final TextView value and catches layouts which bypass that hook.
     */
    private static final class ResourceTextViewHook extends XC_MethodHook {
        private final boolean hint;

        ResourceTextViewHook(boolean hint) {
            this.hint = hint;
        }

        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (!(param.thisObject instanceof TextView)) {
                return;
            }
            TextView view = (TextView) param.thisObject;
            if (hint) {
                replaceHint(view);
            } else {
                replaceText(view);
            }
        }
    }

    private static final class ActivityTitleResourceHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            if (param.thisObject instanceof Activity) {
                Activity activity = (Activity) param.thisObject;
                CharSequence title = activity.getTitle();
                CharSequence translated = translateLiteral(title);
                if (translated != null && !translated.equals(title)) {
                    activity.setTitle(translated);
                }
            }
        }
    }

    private static final class InflateHook extends XC_MethodHook {
        @Override
        protected void afterHookedMethod(MethodHookParam param) {
            Object result = param.getResult();
            if (result instanceof View) {
                localizeViewTree((View) result);
            }
        }
    }

    private static String translateResource(Resources resources, int id, Object[] formatArgs) {
        try {
            if (!GOS_PACKAGE.equals(resources.getResourcePackageName(id))) {
                return null;
            }
            String translated = CHINESE.get(resources.getResourceEntryName(id));
            if (translated == null) {
                return null;
            }
            if (formatArgs != null && formatArgs.length > 0) {
                return String.format(Locale.SIMPLIFIED_CHINESE, translated, formatArgs);
            }
            return translated;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CharSequence translateLiteral(CharSequence value) {
        if (value == null) {
            return value;
        }
        String source = value.toString();
        if (!chineseEnabled) {
            String english = ENGLISH.get(source);
            return english == null ? value : english;
        }
        String translated = LITERALS.get(source);
        if (translated != null) {
            return translated;
        }
        if (source.endsWith(" (fake)")) {
            return source.substring(0, source.length() - 7) + "（伪装）";
        }
        String partial = source.replace("currentTime: ", "当前时间：")
                .replace("lastUpdate: ", "上次更新：")
                .replace("lastFullyUpdate: ", "上次完整更新：")
                .replace(" (global: ", "（全局：")
                .replace(", Pkg data", "，应用包数据")
                .replace("Device name: ", "设备名称：")
                .replace("File ", "文件 ")
                .replace(" not exist", " 不存在");
        return partial.equals(source) ? value : partial;
    }

    private static void buildLiteralDictionary(Context context) {
        if (context == null) {
            return;
        }
        Resources resources = context.getResources();
        for (Map.Entry<String, String> entry : CHINESE.entrySet()) {
            try {
                int id = resources.getIdentifier(entry.getKey(), "string", GOS_PACKAGE);
                if (id == 0) {
                    continue;
                }
                String english = resources.getText(id).toString();
                putLiteral(english, entry.getValue());
            } catch (Throwable ignored) {
                // A changed future GOS resource is simply left in its original language.
            }
        }
    }

    private static void installActivityTraversal(Context context) {
        if (!(context instanceof Application)) {
            return;
        }
        ((Application) context).registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(Activity activity) {
                    }

                    @Override
                    public void onActivityResumed(Activity activity) {
                        try {
                            localizeViewTree(activity.getWindow().getDecorView());
                        } catch (Throwable ignored) {
                            // Localization must never interfere with opening GOS's test panel.
                        }
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {
                    }

                    @Override
                    public void onActivityStopped(Activity activity) {
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                    }

                    @Override
                    public void onActivityDestroyed(Activity activity) {
                    }
                });
    }

    private static void localizeViewTree(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof TextView) {
            replaceText((TextView) view);
            replaceHint((TextView) view);
        }
        CharSequence description = view.getContentDescription();
        CharSequence translatedDescription = translateLiteral(description);
        if (translatedDescription != null && !translatedDescription.equals(description)) {
            view.setContentDescription(translatedDescription);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                localizeViewTree(group.getChildAt(index));
            }
        }
    }

    private static void replaceText(TextView view) {
        CharSequence source = view.getText();
        CharSequence translated = translateLiteral(source);
        if (translated != null && !translated.equals(source)) {
            view.setText(translated);
        }
    }

    private static void replaceHint(TextView view) {
        CharSequence source = view.getHint();
        CharSequence translated = translateLiteral(source);
        if (translated != null && !translated.equals(source)) {
            view.setHint(translated);
        }
    }

    private static void add(String resourceName, String chinese) {
        CHINESE.put(resourceName, chinese);
    }

    private static void literal(String english, String chinese) {
        putLiteral(english, chinese);
    }

    private static void putLiteral(String english, String chinese) {
        if (english == null || chinese == null || english.equals(chinese)) {
            return;
        }
        LITERALS.put(english, chinese);
        if (!ENGLISH.containsKey(chinese)) {
            ENGLISH.put(chinese, english);
        }
    }
}
