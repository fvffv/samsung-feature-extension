package com.samsung.feature.extension;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.util.Arrays;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class BixbyOpenAiHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.samsung.android.bixby.agent";
    private static final String SOLVE_CONFIG =
            "com.samsung.android.bixby.agent.cnfeaturecommon.config.SolveProblemConfig";
    private static final String RTC_CONFIG =
            "com.samsung.android.bixby.agent.cnfeaturecommon.config.RtcConfig";
    private static final String VOLCENGINE_CHATBOT =
            "com.samsung.android.bixby.agent.rtcvideo.volcengine.C2397h";
    private static final String CHAT_ACTIVITY =
            "com.samsung.android.bixby.agent.cnfeatureui.ui.chatbixbyagent.ChatBixbyAgentActivity";
    private static final String BYTEDANCE_PROBLEM_SOLVING =
            "com.samsung.android.bixby.agent.cnfeaturecommon.config.ByteDanceProblemSolving";
    private static final String ARK_LLM_CONFIG =
            "com.samsung.android.bixby.agent.rtcvideo.volcengine.ChatBotRequest$ARKLLMConfig";
    private static final String MUSIC_GENERATION_POPUP =
            "com.samsung.android.bixby.agent.mainui.widget.base.MusicGenerationPopup";
    private static final String ACTION_BAR_VIEW =
            "com.samsung.android.bixby.agent.mainui.widget.actionbar.ActionBarView";
    private static final long CONFIG_CACHE_MS = 1000L;
    private static final int ID_TYPING_EDIT_TEXT = 0x7f090833;
    private static final int ID_TYPING_MODE_ENTER_BUTTON = 0x7f090834;
    private static final int ID_SEND_BUTTON = 0x7f090697;
    private static final int ID_RV_MESSAGE = 0x7f090647;
    private static final int ID_WEB_VIEW = 0x7f090872;
    private static final int ID_CONVERSATION_INDICATOR_VIEW = 0x7f0901eb;
    private static final int ID_CONVERSATION_INDICATOR_VIEW_ALT = 0x7f0901ee;
    private static final int ID_PROCESSING_BUTTON = 0x7f090589;
    private static final String TAG_CHAT_PANEL = "samsung_feature_extension_bixby_openai_chat_panel";
    private static final String TAG_MESSAGE_LIST = "samsung_feature_extension_bixby_openai_message_list";
    private static final int MAX_NATIVE_RENDERING_EVENTS = 80;

    private static volatile Context appContext;
    private static volatile BixbyOpenAiConfig cachedConfig;
    private static volatile long cachedAtMs;
    private static volatile boolean applicationHookInstalled;
    private static volatile boolean lifecycleCallbacksRegistered;
    private static volatile boolean chatClickHookInstalled;
    private static volatile boolean nativeRecyclerProbeInstalled;
    private static volatile boolean nativeTextSetProbeInstalled;
    private static volatile boolean nativeWebViewProbeInstalled;
    private static volatile WeakReference<Activity> currentForegroundActivity = new WeakReference<Activity>(null);
    private static volatile WeakReference<Activity> currentChatActivity = new WeakReference<Activity>(null);
    private static volatile String lastSentText = "";
    private static volatile long lastSentAtMs;
    private static final Set<View> wrappedSendButtons =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<Activity> dumpedChatActivities =
            Collections.newSetFromMap(new WeakHashMap<Activity, Boolean>());
    private static final Set<View> observedNativeMessageLists =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final Set<String> hookedNativeAdapterClasses =
            Collections.synchronizedSet(new HashSet<String>());
    private static final Map<View, List<String>> nativeRenderingEventBuffers =
            Collections.synchronizedMap(new WeakHashMap<View, List<String>>());
    private static final Map<View, String> nativePostMessageRequestIds =
            Collections.synchronizedMap(new WeakHashMap<View, String>());
    private static final Map<View, Integer> nativeOriginalVisibility =
            Collections.synchronizedMap(new WeakHashMap<View, Integer>());
    private static final Map<View, Boolean> nativeOriginalEnabled =
            Collections.synchronizedMap(new WeakHashMap<View, Boolean>());
    private static final Map<View, Float> nativeOriginalAlpha =
            Collections.synchronizedMap(new WeakHashMap<View, Float>());
    private static final Map<TextView, CharSequence> nativeOriginalHints =
            Collections.synchronizedMap(new WeakHashMap<TextView, CharSequence>());
    private static final ThreadLocal<Boolean> suppressNativeScriptCapture =
            new ThreadLocal<Boolean>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET.equals(lpparam.packageName)) {
            return;
        }
        log("BixbyOpenAi: loading in " + lpparam.packageName + ", process=" + lpparam.processName);
        install("Application.attach", new Installer() {
            @Override
            public void install() throws Throwable {
                hookApplicationContext();
            }
        });
        final ClassLoader classLoader = lpparam.classLoader;
        install("SolveProblemConfig", new Installer() {
            @Override
            public void install() throws Throwable {
                hookSolveProblemConfig(classLoader);
            }
        });
        install("RtcConfig", new Installer() {
            @Override
            public void install() throws Throwable {
                hookRtcConfig(classLoader);
            }
        });
        install("ProblemSolving apiKey", new Installer() {
            @Override
            public void install() throws Throwable {
                hookProblemSolvingApiKey(classLoader);
            }
        });
        install("Volcengine ARK LLM routing", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVolcengineArkLlmRouting(classLoader);
            }
        });
        install("Volcengine SDK model routing", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVolcengineSdkModelRouting(classLoader);
            }
        });
        install("Preset skill entry diagnostics", new Installer() {
            @Override
            public void install() throws Throwable {
                hookPresetSkillEntrypoints(classLoader);
            }
        });
        install("Volcengine ChatBot diagnostics", new Installer() {
            @Override
            public void install() throws Throwable {
                hookVolcengineDiagnostics(classLoader);
            }
        });
        install("Conversation diagnostics", new Installer() {
            @Override
            public void install() throws Throwable {
                hookConversationDiagnostics(classLoader);
            }
        });
        install("Chat UI bridge", new Installer() {
            @Override
            public void install() throws Throwable {
                hookChatUiBridge(classLoader);
            }
        });
        install("HTTP diagnostics", new Installer() {
            @Override
            public void install() throws Throwable {
                hookHttpDiagnostics(classLoader);
            }
        });
    }

    private static void hookApplicationContext() throws Throwable {
        if (applicationHookInstalled) {
            return;
        }
        applicationHookInstalled = true;
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        attach.setAccessible(true);
        XposedBridge.hookMethod(attach, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Application) {
                    registerActivityCallbacks((Application) param.thisObject);
                }
                if (param.args != null && param.args.length > 0 && param.args[0] instanceof Context) {
                    setContext((Context) param.args[0]);
                } else if (param.thisObject instanceof Application) {
                    setContext(((Application) param.thisObject).getApplicationContext());
                }
            }
        });
    }

    private static void setContext(Context context) {
        if (context == null) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        appContext = applicationContext != null ? applicationContext : context;
        try {
            DiagnosticLogger.init(appContext);
        } catch (Throwable ignored) {
            // LSPosed log still receives the same messages.
        }
        BixbyOpenAiConfig config = config(true);
        log("BixbyOpenAi: context ready, config=" + config.summary());
    }

    private static void registerActivityCallbacks(Application application) {
        if (application == null || lifecycleCallbacksRegistered) {
            return;
        }
        lifecycleCallbacksRegistered = true;
        try {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                    rememberForegroundActivity(activity, "created");
                }

                @Override
                public void onActivityStarted(Activity activity) {
                    rememberForegroundActivity(activity, "started");
                }

                @Override
                public void onActivityResumed(Activity activity) {
                    rememberForegroundActivity(activity, "resumed");
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
            log("BixbyOpenAi: Activity lifecycle callback registered");
        } catch (Throwable t) {
            log("BixbyOpenAi: Activity lifecycle callback register failed");
            log(t);
        }
    }

    private static void rememberForegroundActivity(final Activity activity, final String reason) {
        if (activity == null) {
            return;
        }
        currentForegroundActivity = new WeakReference<Activity>(activity);
        if (isChatActivity(activity)) {
            currentChatActivity = new WeakReference<Activity>(activity);
            removeCustomChatPanel(activity);
            log("BixbyOpenAi: foreground chat activity reason=" + reason
                    + ", class=" + activity.getClass().getName());
            dumpChatNativeViewTree(activity, reason);
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isChatActivity(activity)) {
                    currentChatActivity = new WeakReference<Activity>(activity);
                    removeCustomChatPanel(activity);
                    rememberVisibleSendButtons(activity, "lifecycle-" + reason);
                    dumpChatNativeViewTree(activity, "delayed-" + reason);
                    log("BixbyOpenAi: foreground activity became chat page reason=" + reason
                            + ", class=" + activity.getClass().getName());
                }
            }
        }, 350L);
    }

    private static void hookSolveProblemConfig(final ClassLoader classLoader) throws Throwable {
        final Class<?> solveClass = Class.forName(SOLVE_CONFIG, false, classLoader);
        Constructor<?> constructor = solveClass.getDeclaredConstructor(String.class, String.class, String.class);
        constructor.setAccessible(true);
        XposedBridge.hookMethod(constructor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (!active(config)) {
                    return;
                }
                applySolveProblemFields(param.thisObject, config, null);
                log("BixbyOpenAi: SolveProblemConfig constructed -> " + config.summary());
            }
        });

        XposedHelpers.findAndHookMethod(SOLVE_CONFIG, classLoader, "getApiUrl", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (active(config)) {
                    param.setResult(config.chatCompletionsEndpoint());
                }
            }
        });
        XposedHelpers.findAndHookMethod(SOLVE_CONFIG, classLoader, "getModelId", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (active(config)) {
                    param.setResult(config.model);
                }
            }
        });
        XposedHelpers.findAndHookMethod(SOLVE_CONFIG, classLoader, "getSolveProblemPrompt", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (active(config) && config.systemPrompt.length() > 0) {
                    param.setResult(config.systemPrompt);
                }
            }
        });
        XposedHelpers.findAndHookMethod(SOLVE_CONFIG, classLoader, "copy",
                String.class, String.class, String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (!active(config) || param.args == null || param.args.length < 3) {
                            return;
                        }
                        if (config.systemPrompt.length() > 0) {
                            param.args[0] = config.systemPrompt;
                        }
                        param.args[1] = config.model;
                        param.args[2] = config.chatCompletionsEndpoint();
                    }
                });
    }

    private static void hookRtcConfig(final ClassLoader classLoader) throws Throwable {
        final Class<?> rtcClass = Class.forName(RTC_CONFIG, false, classLoader);
        final Class<?> solveClass = Class.forName(SOLVE_CONFIG, false, classLoader);
        final Class<?> jsonArrayClass = Class.forName("com.google.gson.JsonArray", false, classLoader);

        Constructor<?> constructor = rtcClass.getDeclaredConstructor(
                Integer.class, Boolean.class, Boolean.class, Boolean.class,
                String.class, String.class, List.class, jsonArrayClass,
                String.class, Integer.class, List.class, solveClass);
        constructor.setAccessible(true);
        XposedBridge.hookMethod(constructor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (!active(config)) {
                    return;
                }
                Object original = param.args != null && param.args.length > 11 ? param.args[11] : null;
                Object replacement = buildSolveProblemConfig(classLoader, config, original);
                if (replacement != null) {
                    XposedHelpers.setObjectField(param.thisObject, "solveProblemConfig", replacement);
                    log("BixbyOpenAi: RtcConfig constructed with custom solveProblemConfig");
                }
            }
        });

        XposedHelpers.findAndHookMethod(RTC_CONFIG, classLoader, "getSolveProblemConfig", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (!active(config)) {
                    return;
                }
                Object replacement = buildSolveProblemConfig(classLoader, config, param.getResult());
                if (replacement != null) {
                    param.setResult(replacement);
                }
            }
        });
        XposedHelpers.findAndHookMethod(RTC_CONFIG, classLoader, "copy",
                Integer.class, Boolean.class, Boolean.class, Boolean.class,
                String.class, String.class, List.class, jsonArrayClass,
                String.class, Integer.class, List.class, solveClass,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (!active(config) || param.args == null || param.args.length < 12) {
                            return;
                        }
                        Object replacement = buildSolveProblemConfig(classLoader, config, param.args[11]);
                        if (replacement != null) {
                            param.args[11] = replacement;
                        }
                    }
                });
    }

    private static void hookVolcengineDiagnostics(ClassLoader classLoader) throws Throwable {
        final Class<?> chatbotClass = Class.forName(VOLCENGINE_CHATBOT, false, classLoader);
        Method signedPost = chatbotClass.getDeclaredMethod("a", String.class, String.class);
        signedPost.setAccessible(true);
        hookMember(signedPost, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String action = stringArg(param, 1);
                BixbyOpenAiConfig config = config(false);
                if (active(config)) {
                    if ("StartVoiceChat".equals(action) && param.args != null && param.args.length > 0) {
                        param.args[0] = rewriteVolcengineStartBody(stringArg(param, 0), config);
                    }
                    log("BixbyOpenAi: Volcengine signed POST action=" + action
                            + ", custom endpoint=" + config.chatCompletionsEndpoint()
                            + ", body=" + clip(stringArg(param, 0), 1200));
                } else {
                    log("BixbyOpenAi: Volcengine signed POST action=" + action
                            + ", custom backend disabled, body=" + clip(stringArg(param, 0), 500));
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (active(config(false))) {
                    log("BixbyOpenAi: Volcengine signed POST response action=" + stringArg(param, 1)
                            + ", result=" + clip(String.valueOf(param.getResult()), 1200));
                }
            }
        });
        hookNamedMethods(chatbotClass, "g", "Volcengine.startVoiceChat");
        hookNamedMethods(chatbotClass, "i", "Volcengine.updateVoiceChat");
        hookNamedMethods(chatbotClass, "h", "Volcengine.stopVoiceChat");
    }

    private static void hookVolcengineArkLlmRouting(final ClassLoader classLoader) throws Throwable {
        final Class<?> arkClass = Class.forName(ARK_LLM_CONFIG, false, classLoader);
        final Class<?> userPromptClass = Class.forName(
                "com.samsung.android.bixby.agent.rtcvideo.volcengine.ChatBotRequest$UserPrompt",
                false, classLoader);
        final Class<?> userPromptArrayClass =
                java.lang.reflect.Array.newInstance(userPromptClass, 0).getClass();
        final Class<?> visionClass = Class.forName(
                "com.samsung.android.bixby.agent.rtcvideo.volcengine.ChatBotRequest$VisionConfig",
                false, classLoader);
        final Class<?> jsonArrayClass = Class.forName("com.google.gson.JsonArray", false, classLoader);

        Constructor<?> constructor = arkClass.getDeclaredConstructor(
                String.class, String.class, String.class, Integer.class, Float.class, Float.class,
                String.class, List.class, userPromptArrayClass, int.class, visionClass,
                boolean.class, jsonArrayClass, String.class);
        constructor.setAccessible(true);
        hookMember(constructor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (active(config)) {
                    applyArkLlmConfigFields(param.thisObject, config, "constructor");
                }
            }
        });

        XposedHelpers.findAndHookMethod(ARK_LLM_CONFIG, classLoader, "copy",
                String.class, String.class, String.class, Integer.class, Float.class, Float.class,
                String.class, List.class, userPromptArrayClass, int.class, visionClass,
                boolean.class, jsonArrayClass, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (!active(config) || param.args == null || param.args.length < 14) {
                            return;
                        }
                        param.args[1] = config.model;
                        param.args[7] = mergeSystemMessages(param.args[7], config);
                        log("BixbyOpenAi: ARK LLM copy routed EndPointId -> " + config.model);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config)) {
                            applyArkLlmConfigFields(param.getResult(), config, "copy-result");
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(ARK_LLM_CONFIG, classLoader, "getEndPointId",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config)) {
                            param.setResult(config.model);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(ARK_LLM_CONFIG, classLoader, "getSystemMessages",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config)) {
                            param.setResult(mergeSystemMessages(param.getResult(), config));
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(ARK_LLM_CONFIG, classLoader, "setSystemMessages",
                List.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config) && param.args != null && param.args.length > 0) {
                            param.args[0] = mergeSystemMessages(param.args[0], config);
                            log("BixbyOpenAi: ARK LLM system prompt merged for native tool router");
                        }
                    }
                });
    }

    private static void hookVolcengineSdkModelRouting(ClassLoader classLoader) {
        String[] classNames = new String[] {
                "com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest",
                "com.volcengine.ark.runtime.model.context.CreateContextRequest",
                "com.volcengine.ark.runtime.model.context.chat.ContextChatCompletionRequest",
                "com.volcengine.ark.runtime.model.bot.completion.chat.BotChatCompletionRequest",
                "com.volcengine.ark.runtime.model.content.generation.CreateContentGenerationTaskRequest",
                "com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest"
        };
        int count = 0;
        for (int i = 0; i < classNames.length; i++) {
            count += hookModelCarrier(classLoader, classNames[i]);
            count += hookModelCarrier(classLoader, classNames[i] + "$Builder");
        }
        log("BixbyOpenAi: Volcengine SDK model routing hook count=" + count);
    }

    private static int hookModelCarrier(ClassLoader classLoader, final String className) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            int count = 0;
            Method[] methods = clazz.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                final Method method = methods[i];
                Class<?>[] types = method.getParameterTypes();
                if (("setModel".equals(method.getName()) || "model".equals(method.getName()))
                        && types.length == 1 && types[0] == String.class) {
                    method.setAccessible(true);
                    hookMember(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            BixbyOpenAiConfig config = config(false);
                            if (!active(config) || param.args == null || param.args.length == 0) {
                                return;
                            }
                            Object old = param.args[0];
                            param.args[0] = config.model;
                            if (old == null || !config.model.equals(String.valueOf(old))) {
                                log("BixbyOpenAi: " + className + "." + method.getName()
                                        + " model routed old=" + old + ", new=" + config.model);
                            }
                        }
                    });
                    count++;
                } else if ("getModel".equals(method.getName()) && types.length == 0
                        && method.getReturnType() == String.class) {
                    method.setAccessible(true);
                    hookMember(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            BixbyOpenAiConfig config = config(false);
                            if (active(config)) {
                                param.setResult(config.model);
                            }
                        }
                    });
                    count++;
                }
            }
            return count;
        } catch (Throwable t) {
            log("BixbyOpenAi: model carrier hook skipped, class=" + className);
            log(t);
            return 0;
        }
    }

    private static void hookPresetSkillEntrypoints(ClassLoader classLoader) {
        int count = 0;
        count += hookFunction2Setter(classLoader, MUSIC_GENERATION_POPUP,
                "setOnSnapshotChanged", "MusicGenerationPopup.onSnapshotChanged");
        count += hookFunction2Setter(classLoader, ACTION_BAR_VIEW,
                "setOnInteractionSnapshot", "ActionBarView.onInteractionSnapshot");
        log("BixbyOpenAi: preset skill entry hook count=" + count);
    }

    private static int hookFunction2Setter(final ClassLoader classLoader, String className,
            String setterName, final String label) {
        try {
            Class<?> targetClass = Class.forName(className, false, classLoader);
            final Class<?> function2Class = Class.forName("Qw.m", false, classLoader);
            Method setter = targetClass.getDeclaredMethod(setterName, function2Class);
            setter.setAccessible(true);
            hookMember(setter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args == null || param.args.length == 0 || param.args[0] == null) {
                        return;
                    }
                    param.args[0] = wrapFunction2(classLoader, function2Class, param.args[0], label);
                }
            });
            return 1;
        } catch (Throwable t) {
            log("BixbyOpenAi: preset skill entry hook skipped, class=" + className
                    + ", setter=" + setterName);
            log(t);
            return 0;
        }
    }

    private static Object wrapFunction2(ClassLoader classLoader, Class<?> function2Class,
            final Object original, final String label) {
        if (original == null) {
            return null;
        }
        try {
            if (java.lang.reflect.Proxy.isProxyClass(original.getClass())) {
                return original;
            }
            return java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] {function2Class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if ("invoke".equals(method.getName())) {
                                log("BixbyOpenAi: preset skill entry " + label
                                        + " args=" + describeArgs(args, 800));
                            }
                            return method.invoke(original, args);
                        }
                    });
        } catch (Throwable t) {
            log("BixbyOpenAi: wrap Function2 failed, label=" + label);
            log(t);
            return original;
        }
    }

    private static void hookConversationDiagnostics(ClassLoader classLoader) {
        int count = 0;
        count += hookConstructorsForLog(classLoader, "Fl.C0328a0", "SolveProblem.HasQuestion");
        count += hookConstructorsForLog(classLoader, "Fl.C0336e0", "SolveProblem.UpdateAccumulatedText");
        count += hookConstructorsForLog(classLoader, "Fl.C0338f0", "SolveProblem.UpdateAnswerPageData");
        count += hookConstructorsForLog(classLoader, "Fl.Y", "SolveProblem.ChangeProgress");
        count += hookConstructorsForLog(classLoader, "Fl.Z", "SolveProblem.HandleError");
        count += hookConstructorsForLog(classLoader, "Fl.C0358t", "SolveProblem.Answer");
        count += hookConstructorsForLog(classLoader, "Fl.C0364z", "SolveProblem.Reasoning");
        count += hookConstructorsForLog(classLoader, "Fl.C0363y", "SolveProblem.Load");
        count += hookConstructorsForLog(classLoader, "Fl.C0360v", "SolveProblem.EnterAnswer");
        count += hookConstructorsForLog(classLoader, "Fl.C0361w", "SolveProblem.EnterReasoning");
        count += hookConstructorsForLog(classLoader, "Fl.C0359u", "SolveProblem.End");
        count += hookConstructorsForLog(classLoader, "Fl.J0", "SolveProblem.State");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.cnfeaturecommon.bo.ChatModel", "CN.ChatModel");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.cnfeaturecommon.conversationhistory.entity.ConversationItem",
                "CNCommon.ConversationItem");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.cnfeatureui.ui.conversationhistory.entity.ConversationItem",
                "CNUI.ConversationItem");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.cnfeatureui.ui.conversationhistory.entity.Message",
                "CNUI.Message");
        hookDataBindingText(classLoader);
        hookNativeRendererDiagnostics(classLoader);
        hookChatActivityIntents(classLoader);
        log("BixbyOpenAi: conversation diagnostics hook count=" + count);
    }

    private static void hookNativeRendererDiagnostics(ClassLoader classLoader) {
        int count = 0;
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.base.viewmodel.RendererMessage$InflateWebView",
                "MainUI.RendererMessage.InflateWebView");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.base.viewmodel.RendererMessage$NotifyStartProcessing",
                "MainUI.RendererMessage.NotifyStartProcessing");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.base.viewmodel.RendererMessage$RendererRequested",
                "MainUI.RendererMessage.RendererRequested");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.base.viewmodel.RendererMessage$ShowRenderer",
                "MainUI.RendererMessage.ShowRenderer");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.base.viewmodel.RendererMessage$ClearRenderer",
                "MainUI.RendererMessage.ClearRenderer");
        count += hookConstructorsForLog(classLoader,
                "com.samsung.android.bixby.agent.mainui.main.flexible.viewmodel.WindowMessage$ClearConversationDriver",
                "MainUI.WindowMessage.ClearConversationDriver");
        hookNativeRecyclerViewDiagnostics(classLoader);
        hookNativeTextSetDiagnostics();
        hookNativeWebViewDiagnostics();
        log("BixbyOpenAi: native renderer diagnostics hook count=" + count);
    }

    private static void hookNativeRecyclerViewDiagnostics(ClassLoader classLoader) {
        if (nativeRecyclerProbeInstalled) {
            return;
        }
        nativeRecyclerProbeInstalled = true;
        try {
            final Class<?> recyclerViewClass =
                    Class.forName("androidx.recyclerview.widget.RecyclerView", false, classLoader);
            final Class<?> adapterClass =
                    Class.forName("androidx.recyclerview.widget.RecyclerView$Adapter", false, classLoader);
            Method setAdapter = recyclerViewClass.getDeclaredMethod("setAdapter", adapterClass);
            setAdapter.setAccessible(true);
            hookMember(setAdapter, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!active(config(false)) || !(param.thisObject instanceof View)) {
                        return;
                    }
                    View recyclerView = (View) param.thisObject;
                    if (!isLikelyNativeMessageRecyclerView(recyclerView)) {
                        return;
                    }
                    observedNativeMessageLists.add(recyclerView);
                    Object adapter = param.args != null && param.args.length > 0 ? param.args[0] : null;
                    log("BixbyOpenAi: NativeRenderer rv_message.setAdapter adapter="
                            + describeAdapter(adapter) + ", view=" + describeView(recyclerView)
                            + ", stack=" + stackSnippet(8));
                    hookNativeAdapterClass(adapter);
                }
            });
            hookNativeAdapterNotifyMethods(adapterClass);
            log("BixbyOpenAi: NativeRenderer RecyclerView diagnostics installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: NativeRenderer RecyclerView diagnostics skipped");
            log(t);
        }
    }

    private static void hookNativeAdapterNotifyMethods(Class<?> adapterClass) {
        if (!hookedNativeAdapterClasses.add(adapterClass.getName() + "#notify")) {
            return;
        }
        Method[] methods = adapterClass.getDeclaredMethods();
        int count = 0;
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            if (method.getName() == null || !method.getName().startsWith("notify")) {
                continue;
            }
            method.setAccessible(true);
            hookMember(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active(config(false)) || !isLikelyNativeMessageAdapter(param.thisObject)) {
                        return;
                    }
                    log("BixbyOpenAi: NativeRenderer adapter." + method.getName()
                            + " adapter=" + describeAdapter(param.thisObject)
                            + ", args=" + describeArgs(param.args, 320)
                            + ", stack=" + stackSnippet(7));
                }
            });
            count++;
        }
        log("BixbyOpenAi: NativeRenderer Adapter notify hooks=" + count);
    }

    private static void hookNativeAdapterClass(final Object adapter) {
        if (adapter == null) {
            return;
        }
        final Class<?> adapterClass = adapter.getClass();
        final String className = adapterClass.getName();
        if (!hookedNativeAdapterClasses.add(className)) {
            return;
        }
        int count = 0;
        Method[] methods = adapterClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            final Method method = methods[i];
            String name = method.getName();
            if (!"onBindViewHolder".equals(name) && !"getItemViewType".equals(name)
                    && !"getItemCount".equals(name) && !"submitList".equals(name)) {
                continue;
            }
            method.setAccessible(true);
            hookMember(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active(config(false))) {
                        return;
                    }
                    if ("submitList".equals(method.getName())) {
                        log("BixbyOpenAi: NativeRenderer " + className + ".submitList args="
                                + describeArgs(param.args, 900) + ", stack=" + stackSnippet(8));
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!active(config(false))) {
                        return;
                    }
                    String name = method.getName();
                    if ("onBindViewHolder".equals(name)) {
                        Object holder = param.args != null && param.args.length > 0 ? param.args[0] : null;
                        Object position = param.args != null && param.args.length > 1 ? param.args[1] : "";
                        log("BixbyOpenAi: NativeRenderer " + className + ".onBindViewHolder position="
                                + position + ", holder=" + describeViewHolder(holder)
                                + ", stack=" + stackSnippet(8));
                    } else if ("getItemCount".equals(name)) {
                        Object result = param.getResult();
                        log("BixbyOpenAi: NativeRenderer " + className + ".getItemCount result="
                                + result + ", adapter=" + describeAdapter(param.thisObject));
                    } else if ("getItemViewType".equals(name)) {
                        log("BixbyOpenAi: NativeRenderer " + className + ".getItemViewType args="
                                + describeArgs(param.args, 120) + ", result=" + param.getResult());
                    }
                }
            });
            count++;
        }
        log("BixbyOpenAi: NativeRenderer adapter class hooks=" + count + ", class=" + className);
    }

    private static void hookNativeTextSetDiagnostics() {
        if (nativeTextSetProbeInstalled) {
            return;
        }
        nativeTextSetProbeInstalled = true;
        try {
            Method setText = TextView.class.getDeclaredMethod("setText", CharSequence.class);
            hookTextSetMethod(setText, "setText");
            try {
                Class<?> bufferTypeClass = Class.forName("android.widget.TextView$BufferType");
                Method setTextWithBuffer = TextView.class.getDeclaredMethod(
                        "setText", CharSequence.class, bufferTypeClass);
                hookTextSetMethod(setTextWithBuffer, "setTextBuffer");
            } catch (Throwable t) {
                log("BixbyOpenAi: NativeRenderer TextView.setText buffer overload skipped");
                log(t);
            }
            log("BixbyOpenAi: NativeRenderer TextView.setText diagnostics installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: NativeRenderer TextView.setText diagnostics skipped");
            log(t);
        }
    }

    private static void hookTextSetMethod(Method method, final String label) {
        method.setAccessible(true);
        hookMember(method, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!active(config(false)) || !(param.thisObject instanceof TextView)
                        || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof CharSequence)) {
                    return;
                }
                TextView textView = (TextView) param.thisObject;
                CharSequence text = (CharSequence) param.args[0];
                if (!shouldLogNativeTextSet(textView, text)) {
                    return;
                }
                log("BixbyOpenAi: NativeRenderer TextView." + label
                        + " view=" + describeView(textView)
                        + ", text=" + clip(String.valueOf(text), 700)
                        + ", stack=" + stackSnippet(10));
            }
        });
    }

    private static void hookNativeWebViewDiagnostics() {
        if (nativeWebViewProbeInstalled) {
            return;
        }
        nativeWebViewProbeInstalled = true;
        try {
            final Class<?> webViewClass = Class.forName("android.webkit.WebView");
            hookWebViewMethod(webViewClass, "loadUrl", new Class<?>[] {String.class}, 0);
            hookWebViewMethod(webViewClass, "loadDataWithBaseURL",
                    new Class<?>[] {String.class, String.class, String.class, String.class, String.class}, 1);
            Class<?> callbackClass = Class.forName("android.webkit.ValueCallback");
            hookWebViewMethod(webViewClass, "evaluateJavascript",
                    new Class<?>[] {String.class, callbackClass}, 0);
            log("BixbyOpenAi: NativeRenderer WebView diagnostics installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: NativeRenderer WebView diagnostics skipped");
            log(t);
        }
    }

    private static void hookWebViewMethod(Class<?> webViewClass, final String methodName,
            Class<?>[] parameterTypes, final int payloadIndex) {
        try {
            final Method method = webViewClass.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            hookMember(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active(config(false)) || !(param.thisObject instanceof View)
                            || param.args == null || payloadIndex >= param.args.length) {
                        return;
                    }
                    View view = (View) param.thisObject;
                    String payload = String.valueOf(param.args[payloadIndex]);
                    if ("evaluateJavascript".equals(methodName)
                            && payload.contains("\"method\":\"clear\"")) {
                        resetNativeRenderingState(view, "renderer-clear");
                    } else if ("loadDataWithBaseURL".equals(methodName)) {
                        resetNativeRenderingState(view, "renderer-load");
                    } else if ("evaluateJavascript".equals(methodName)) {
                        rememberNativeRendererPostMessage(view, payload, "renderer-script");
                    }
                    if (!shouldLogWebRendererCall(view, payload)) {
                        return;
                    }
                    log("BixbyOpenAi: NativeRenderer WebView." + methodName
                            + " view=" + describeView(view)
                            + ", payload=" + clip(payload, 900)
                            + ", stack=" + stackSnippet(10));
                }
            });
        } catch (Throwable t) {
            log("BixbyOpenAi: NativeRenderer WebView." + methodName + " skipped");
            log(t);
        }
    }

    private static void hookHttpDiagnostics(ClassLoader classLoader) {
        try {
            Method openConnection = URL.class.getDeclaredMethod("openConnection");
            hookMember(openConnection, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object target = param.thisObject;
                    String url = target == null ? "" : String.valueOf(target);
                    if (shouldLogNetwork(url)) {
                        log("BixbyOpenAi: URL.openConnection url=" + clip(url, 800));
                    }
                }
            });
        } catch (Throwable t) {
            log("BixbyOpenAi: URL.openConnection diagnostics skipped");
            log(t);
        }
        try {
            Method openConnectionProxy = URL.class.getDeclaredMethod("openConnection", Proxy.class);
            hookMember(openConnectionProxy, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object target = param.thisObject;
                    String url = target == null ? "" : String.valueOf(target);
                    if (shouldLogNetwork(url)) {
                        log("BixbyOpenAi: URL.openConnection(proxy) url=" + clip(url, 800));
                    }
                }
            });
        } catch (Throwable t) {
            log("BixbyOpenAi: URL.openConnection(proxy) diagnostics skipped");
            log(t);
        }
        hookConstructorsForNetworkLog(classLoader, "com.squareup.okhttp.B", "OkHttp.Request");
        hookConstructorsForNetworkLog(classLoader, "com.squareup.okhttp.F", "OkHttp.Response");
    }

    private static void hookNamedMethods(Class<?> targetClass, String methodName, final String label) {
        int count = 0;
        Method[] methods = targetClass.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!methodName.equals(method.getName())) {
                continue;
            }
            method.setAccessible(true);
            hookMember(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (active(config(false))) {
                        log("BixbyOpenAi: " + label + " call args=" + describeArgs(param.args, 900));
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (active(config(false)) && param.getThrowable() != null) {
                        log("BixbyOpenAi: " + label + " threw " + param.getThrowable());
                    } else if (active(config(false)) && param.getResult() != null) {
                        log("BixbyOpenAi: " + label + " result=" + clip(String.valueOf(param.getResult()), 900));
                    }
                }
            });
            count++;
        }
        log("BixbyOpenAi: " + label + " method hooks=" + count);
    }

    private static int hookConstructorsForLog(ClassLoader classLoader, String className, final String label) {
        try {
            Class<?> targetClass = Class.forName(className, false, classLoader);
            Constructor<?>[] constructors = targetClass.getDeclaredConstructors();
            int count = 0;
            for (int i = 0; i < constructors.length; i++) {
                Constructor<?> constructor = constructors[i];
                constructor.setAccessible(true);
                hookMember(constructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!active(config(false))) {
                            return;
                        }
                        String objectText = String.valueOf(param.thisObject);
                        if ("SolveProblem.State".equals(label) && !isInterestingState(objectText)) {
                            return;
                        }
                        log("BixbyOpenAi: " + label
                                + " constructed args=" + describeArgs(param.args, 700)
                                + ", value=" + clip(objectText, 1000));
                    }
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            log("BixbyOpenAi: " + label + " diagnostics skipped, class=" + className
                    + ", error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            return 0;
        }
    }

    private static int hookConstructorsForNetworkLog(ClassLoader classLoader, String className, final String label) {
        try {
            Class<?> targetClass = Class.forName(className, false, classLoader);
            Constructor<?>[] constructors = targetClass.getDeclaredConstructors();
            int count = 0;
            for (int i = 0; i < constructors.length; i++) {
                Constructor<?> constructor = constructors[i];
                constructor.setAccessible(true);
                hookMember(constructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!active(config(false))) {
                            return;
                        }
                        String text = String.valueOf(param.thisObject);
                        if (shouldLogNetwork(text)) {
                            log("BixbyOpenAi: " + label + " " + clip(text, 900));
                        }
                    }
                });
                count++;
            }
            log("BixbyOpenAi: " + label + " constructor hooks=" + count);
            return count;
        } catch (Throwable t) {
            log("BixbyOpenAi: " + label + " diagnostics skipped, class=" + className
                    + ", error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            return 0;
        }
    }

    private static void hookDataBindingText(ClassLoader classLoader) {
        try {
            Class<?> observableField = Class.forName("androidx.databinding.h", false, classLoader);
            Method setMethod = observableField.getDeclaredMethod("k", Object.class);
            setMethod.setAccessible(true);
            hookMember(setMethod, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active(config(false)) || param.args == null || param.args.length == 0) {
                        return;
                    }
                    Object value = param.args[0];
                    if (!(value instanceof String)) {
                        return;
                    }
                    String text = (String) value;
                    if (text.length() == 0 || !isLikelyInterestingStack()) {
                        return;
                    }
                    log("BixbyOpenAi: DataBinding text set value=" + clip(text, 500)
                            + ", stack=" + stackSnippet(7));
                }
            });
            log("BixbyOpenAi: DataBinding text diagnostics installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: DataBinding text diagnostics skipped");
            log(t);
        }
    }

    private static void hookChatActivityIntents(ClassLoader classLoader) {
        try {
            Class<?> activityClass = Class.forName(CHAT_ACTIVITY, false, classLoader);
            Method onNewIntent = activityClass.getDeclaredMethod("onNewIntent", Intent.class);
            onNewIntent.setAccessible(true);
            hookMember(onNewIntent, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (active(config(false)) && param.args != null && param.args.length > 0) {
                        log("BixbyOpenAi: ChatBixbyAgentActivity.onNewIntent "
                                + describeIntent((Intent) param.args[0]));
                    }
                }
            });
            Method onCreate = activityClass.getDeclaredMethod("onCreate", Bundle.class);
            onCreate.setAccessible(true);
            hookMember(onCreate, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!active(config(false)) || !(param.thisObject instanceof Activity)) {
                        return;
                    }
                    Intent intent = ((Activity) param.thisObject).getIntent();
                    log("BixbyOpenAi: ChatBixbyAgentActivity.onCreate intent=" + describeIntent(intent));
                }
            });
            log("BixbyOpenAi: ChatBixbyAgentActivity diagnostics installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: ChatBixbyAgentActivity diagnostics skipped");
            log(t);
        }
    }

    private static void hookChatUiBridge(ClassLoader classLoader) throws Throwable {
        final Class<?> activityClass = Class.forName(CHAT_ACTIVITY, false, classLoader);
        Method onCreate = activityClass.getDeclaredMethod("onCreate", Bundle.class);
        onCreate.setAccessible(true);
        hookMember(onCreate, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity) {
                    postRememberChatActivity((Activity) param.thisObject, "onCreate");
                }
            }
        });
        try {
            Method onNewIntent = activityClass.getDeclaredMethod("onNewIntent", Intent.class);
            onNewIntent.setAccessible(true);
            hookMember(onNewIntent, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.thisObject instanceof Activity) {
                        postRememberChatActivity((Activity) param.thisObject, "onNewIntent");
                    }
                }
            });
        } catch (Throwable t) {
            log("BixbyOpenAi: Chat UI bridge onNewIntent hook skipped");
            log(t);
        }
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        onResume.setAccessible(true);
        hookMember(onResume, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity && isChatActivity((Activity) param.thisObject)) {
                    postRememberChatActivity((Activity) param.thisObject, "onResume");
                }
            }
        });
        hookChatSendClicks();
    }

    private static void hookChatSendClicks() throws Throwable {
        if (chatClickHookInstalled) {
            return;
        }
        chatClickHookInstalled = true;
        Method setOnClickListener = View.class.getDeclaredMethod("setOnClickListener", View.OnClickListener.class);
        setOnClickListener.setAccessible(true);
        hookMember(setOnClickListener, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) {
                if (!(param.thisObject instanceof View) || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof View.OnClickListener)) {
                    return;
                }
                final View view = (View) param.thisObject;
                if (!isLikelyChatSendButton(view)) {
                    return;
                }
                final View.OnClickListener original = (View.OnClickListener) param.args[0];
                if (original.getClass().getName().startsWith(BixbyOpenAiHook.class.getName())) {
                    return;
                }
                param.args[0] = new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!handleCustomChatSend(v, true, "button-listener")) {
                            original.onClick(v);
                        }
                    }
                };
                rememberWrappedButton(view, "setOnClickListener");
            }
        });

        Method performClick = View.class.getDeclaredMethod("performClick");
        performClick.setAccessible(true);
        hookMember(performClick, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View
                        && handleCustomChatSend((View) param.thisObject, true, "performClick")) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });

        Method setEditorActionListener = TextView.class.getDeclaredMethod(
                "setOnEditorActionListener", TextView.OnEditorActionListener.class);
        setEditorActionListener.setAccessible(true);
        hookMember(setEditorActionListener, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(final MethodHookParam param) {
                if (!(param.thisObject instanceof TextView) || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof TextView.OnEditorActionListener)) {
                    return;
                }
                final TextView textView = (TextView) param.thisObject;
                if (!isLikelyChatQuestionInput(textView)) {
                    return;
                }
                final TextView.OnEditorActionListener original =
                        (TextView.OnEditorActionListener) param.args[0];
                param.args[0] = new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if (isSendEditorAction(actionId, event)
                                && handleCustomChatSend(v, false, "editor-listener")) {
                            return true;
                        }
                        return original.onEditorAction(v, actionId, event);
                    }
                };
                rememberChatInput(textView, "setOnEditorActionListener");
            }
        });

        Method onEditorAction = TextView.class.getDeclaredMethod("onEditorAction", int.class);
        onEditorAction.setAccessible(true);
        hookMember(onEditorAction, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof TextView
                        && isLikelyChatQuestionInput((TextView) param.thisObject)
                        && param.args != null && param.args.length > 0
                        && isSendEditorAction(((Integer) param.args[0]).intValue(), null)
                        && handleCustomChatSend((View) param.thisObject, false, "editor-action")) {
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }

    private static void postRememberChatActivity(final Activity activity, final String reason) {
        if (activity == null || !isChatActivity(activity)) {
            return;
        }
        currentChatActivity = new WeakReference<Activity>(activity);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rememberVisibleSendButtons(activity, reason);
            }
        }, 120L);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                rememberVisibleSendButtons(activity, reason + "+late");
            }
        }, 700L);
    }

    private static void rememberVisibleSendButtons(Activity activity, String reason) {
        if (activity == null || !isChatActivity(activity)) {
            return;
        }
        currentChatActivity = new WeakReference<Activity>(activity);
        View send = activity.findViewById(ID_SEND_BUTTON);
        if (send != null && isLikelyChatSendButton(send)) {
            rememberWrappedButton(send, reason);
        }
        View enter = activity.findViewById(ID_TYPING_MODE_ENTER_BUTTON);
        if (enter != null && isLikelyChatSendButton(enter)) {
            rememberWrappedButton(enter, reason);
        }
    }

    private static void rememberWrappedButton(View view, String reason) {
        if (view == null) {
            return;
        }
        Activity activity = findChatActivity(view);
        if (activity != null) {
            currentChatActivity = new WeakReference<Activity>(activity);
        }
        synchronized (wrappedSendButtons) {
            if (wrappedSendButtons.add(view)) {
                log("BixbyOpenAi: Chat send button observed, reason=" + reason
                        + ", id=" + resourceEntryName(view));
            }
        }
    }

    private static void rememberChatInput(TextView textView, String reason) {
        Activity activity = findChatActivity(textView);
        if (activity != null) {
            currentChatActivity = new WeakReference<Activity>(activity);
        }
        log("BixbyOpenAi: Chat input observed, reason=" + reason
                + ", id=" + resourceEntryName(textView));
    }

    private static View findConversationRoot(View source, Activity activity) {
        if (source == null) {
            return null;
        }
        View root = source.getRootView();
        if (hasConversationUi(root)) {
            return root;
        }
        if (activity != null) {
            View content = activity.findViewById(android.R.id.content);
            if (hasConversationUi(content)) {
                return content;
            }
        }
        View current = source;
        View best = null;
        while (current != null) {
            if (hasConversationUi(current)) {
                best = current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        if (best != null) {
            return best;
        }
        return hasConversationUi(source) ? source : null;
    }

    private static boolean hasConversationUi(View root) {
        if (root == null || isInsideCustomPanel(root)) {
            return false;
        }
        EditText editText = findQuestionEditText(root);
        if (editText == null) {
            return false;
        }
        if (findNativeRendererWebView(root) != null) {
            return true;
        }
        return findViewByIdDeep(root, ID_SEND_BUTTON) != null
                || findViewByIdDeep(root, ID_TYPING_MODE_ENTER_BUTTON) != null;
    }

    private static boolean handleCustomChatSend(final View source, boolean requireSendButton, String trigger) {
        if (source == null) {
            return false;
        }
        if (requireSendButton && !isLikelyChatSendButton(source)) {
            return false;
        }
        final BixbyOpenAiConfig config = config(false);
        if (!active(config)) {
            log("BixbyOpenAi: custom chat send skipped, inactive config, trigger=" + trigger
                    + ", config=" + config.summary());
            return false;
        }
        final Activity activity = findChatActivity(source);
        final View conversationRoot = findConversationRoot(source, activity);
        if (conversationRoot == null) {
            log("BixbyOpenAi: custom chat send skipped, conversation root not found, trigger=" + trigger
                    + ", sourceId=" + resourceEntryName(source)
                    + ", sourceClass=" + source.getClass().getName()
                    + ", context=" + (source.getContext() == null ? "null" : source.getContext().getClass().getName()));
            return false;
        }
        final EditText editText = findQuestionEditText(conversationRoot);
        if (editText == null) {
            log("BixbyOpenAi: custom chat send skipped, typing EditText not found, trigger=" + trigger);
            return false;
        }
        String text = String.valueOf(editText.getText()).trim();
        if (text.length() == 0) {
            log("BixbyOpenAi: custom chat send skipped, empty text, trigger=" + trigger);
            return false;
        }
        if (shouldPassThroughToBixby(text)) {
            log("BixbyOpenAi: native Bixby skill passthrough, trigger=" + trigger
                    + ", text=" + clip(text, 260));
            return false;
        }
        long now = System.currentTimeMillis();
        if (text.equals(lastSentText) && now - lastSentAtMs < 1200L) {
            log("BixbyOpenAi: custom chat send swallowed duplicate, trigger=" + trigger);
            return true;
        }
        lastSentText = text;
        lastSentAtMs = now;
        final String question = text;
        editText.setText("");

        if (activity != null) {
            removeCustomChatPanel(activity);
        }
        final View nativeRenderer = findNativeRendererWebView(conversationRoot);
        if (nativeRenderer == null) {
            log("BixbyOpenAi: native renderer send skipped, BixbyWebView not found, trigger=" + trigger
                    + ", root=" + describeView(conversationRoot));
            return false;
        }
        syncVisibleNativeConversationContext(conversationRoot, config, "before-custom-send");
        final String requestId = String.valueOf(System.currentTimeMillis());
        renderNativeUserMessage(nativeRenderer, question, requestId);
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startNativeThinkingUi(conversationRoot, nativeRenderer, requestId);
                log("BixbyOpenAi: native renderer chat request start trigger=" + trigger
                        + ", endpoint=" + config.chatCompletionsEndpoint()
                        + ", model=" + config.model + ", question=" + clip(question, 260));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            final Handler mainHandler = new Handler(Looper.getMainLooper());
                            final StringBuilder streamedReply = new StringBuilder();
                            final long[] lastPublishAt = new long[] {0L};
                            final boolean[] streamStarted = new boolean[] {false};
                            final String reply = BixbyOpenAiClient.chatStreaming(config, question,
                                    new BixbyOpenAiClient.StreamCallback() {
                                        @Override
                                        public void onDelta(String delta) {
                                            if (delta == null || delta.length() == 0) {
                                                return;
                                            }
                                            final String partial;
                                            synchronized (streamedReply) {
                                                streamedReply.append(delta);
                                                partial = streamedReply.toString();
                                            }
                                            long now = System.currentTimeMillis();
                                            if (now - lastPublishAt[0] < 120L) {
                                                return;
                                            }
                                            lastPublishAt[0] = now;
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!streamStarted[0]) {
                                                        streamStarted[0] = true;
                                                        setNativeConversationIndicatorState(conversationRoot, "Streaming");
                                                    }
                                                    renderNativeAssistantPartialMessage(nativeRenderer, requestId, partial);
                                                }
                                            });
                                        }
                                    });
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    String finalReply = reply;
                                    if (finalReply == null || finalReply.length() == 0) {
                                        synchronized (streamedReply) {
                                            finalReply = streamedReply.toString();
                                        }
                                    }
                                    renderNativeAssistantMessage(nativeRenderer, requestId,
                                            nonEmpty(finalReply, "\u81ea\u5b9a\u4e49 AI \u6ca1\u6709\u8fd4\u56de\u5185\u5bb9"), false);
                                    finishNativeThinkingUi(conversationRoot);
                                }
                            });
                            log("BixbyOpenAi: native renderer chat response ok, length="
                                    + (reply == null ? 0 : reply.length()));
                        } catch (final Throwable t) {
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                @Override
                                public void run() {
                                    renderNativeAssistantMessage(nativeRenderer, requestId,
                                            "\u81ea\u5b9a\u4e49 AI \u8bf7\u6c42\u5931\u8d25: "
                                                    + clip(String.valueOf(t.getMessage()), 500), false);
                                    finishNativeThinkingUi(conversationRoot);
                                }
                            });
                            log("BixbyOpenAi: native renderer chat request failed");
                            log(t);
                        }
                    }
                }, "BixbyOpenAiChat").start();
            }
        }, 120L);
        return true;
    }

    private static void renderNativeUserMessage(View nativeRenderer, String question, String requestId) {
        if (nativeRenderer == null || question == null || question.length() == 0) {
            return;
        }
        long ts = System.currentTimeMillis();
        appendNativeRenderingEvents(nativeRenderer, new String[] {
                nativeAsrEvent(requestId, ts, "START", null),
                nativeAsrEvent(requestId, ts + 1L, "TEXT", question),
                nativeAsrEvent(requestId, ts + 2L, "END", null),
                nativeOnStartEvent(requestId, ts + 3L, nativeConversationId(nativeRenderer))
        });
        boolean published = publishNativeRenderingState(nativeRenderer, requestId, "user-turn");
        if (!published) {
            published = dispatchNativeUserMessage(nativeRenderer, requestId, question);
        }
        log("BixbyOpenAi: native renderer user message injected, requestId=" + requestId
                + ", statePublished=" + published + ", text=" + clip(question, 260));
    }

    private static void renderNativeAssistantMessage(View nativeRenderer, String requestId,
            String reply, boolean temporary) {
        if (nativeRenderer == null) {
            return;
        }
        String safeReply = nonEmpty(reply, "\u81ea\u5b9a\u4e49 AI \u6ca1\u6709\u8fd4\u56de\u5185\u5bb9");
        String safeRequestId = nonEmpty(requestId, String.valueOf(System.currentTimeMillis()));
        upsertNativeAssistantMessageEvent(nativeRenderer, safeRequestId, safeReply, temporary, true);
        boolean published = dispatchNativeDialogMessage(nativeRenderer, safeRequestId, safeReply,
                temporary, "");
        if (!published) {
            published = publishNativeRenderingState(nativeRenderer, safeRequestId, "assistant-turn-fallback");
        }
        dispatchNativeAsrEvent(nativeRenderer, "RESULT_LOADED", null, "result-loaded");
        log("BixbyOpenAi: native renderer assistant message injected, requestId=" + safeRequestId
                + ", statePublished=" + published + ", length=" + safeReply.length());
    }

    private static void renderNativeAssistantThinkingMessage(View nativeRenderer, String requestId) {
        if (nativeRenderer == null) {
            return;
        }
        String safeRequestId = nonEmpty(requestId, String.valueOf(System.currentTimeMillis()));
        upsertNativeAssistantMessageEvent(nativeRenderer, safeRequestId, "", true, false);
        boolean published = publishNativeRenderingState(nativeRenderer, safeRequestId, "assistant-thinking");
        if (!published) {
            published = dispatchNativeDialogMessage(nativeRenderer, safeRequestId, true);
        }
        log("BixbyOpenAi: native renderer assistant thinking started, requestId=" + safeRequestId
                + ", statePublished=" + published);
    }

    private static void renderNativeAssistantPartialMessage(View nativeRenderer, String requestId, String reply) {
        if (nativeRenderer == null || reply == null || reply.length() == 0) {
            return;
        }
        String safeRequestId = nonEmpty(requestId, String.valueOf(System.currentTimeMillis()));
        upsertNativeAssistantMessageEvent(nativeRenderer, safeRequestId, reply, false, false);
        boolean published = dispatchNativeDialogMessage(nativeRenderer, safeRequestId, reply,
                false, "Stream");
        if (!published) {
            published = publishNativeRenderingState(nativeRenderer, safeRequestId,
                    "assistant-stream-fallback");
        }
        log("BixbyOpenAi: native renderer assistant stream updated, requestId=" + safeRequestId
                + ", statePublished=" + published + ", length=" + reply.length());
    }

    private static boolean dispatchNativeUserMessage(View nativeRenderer, String requestId, String question) {
        boolean ok = false;
        ok |= dispatchNativeAsrEvent(nativeRenderer, "START", null, "asr-start");
        ok |= dispatchNativeAsrEvent(nativeRenderer, "TEXT", question, "asr-text");
        ok |= dispatchNativeAsrEvent(nativeRenderer, "END", null, "asr-end");
        return ok;
    }

    private static boolean dispatchNativeAsrEvent(View nativeRenderer, String type,
            String value, String reason) {
        if (nativeRenderer == null || type == null || type.length() == 0) {
            return false;
        }
        StringBuilder script = new StringBuilder(180);
        script.append("window.postMessage({\"method\":\"onAsrEvent\",\"args\":[{\"type\":")
                .append(jsonString(type));
        if (value != null) {
            script.append(",\"value\":").append(jsonString(value));
        }
        script.append("}]}, window.location.origin);");
        return evaluateNativeRendererScript(nativeRenderer, script.toString(), reason);
    }

    private static boolean dispatchNativeDialogMessage(View nativeRenderer, String requestId,
            String text, boolean temporary, String dialogMode) {
        if (nativeRenderer == null || requestId == null || requestId.length() == 0) {
            return false;
        }
        String safeText = text == null ? "" : text;
        String script = "window.postMessage({\"method\":\"onDialogMessage\",\"args\":["
                + jsonString(requestId) + ","
                + jsonString(safeText)
                + ",{\"isTemporary\":" + (temporary ? "true" : "false")
                + ",\"dialogMode\":" + jsonString(nonEmpty(dialogMode, ""))
                + ",\"locale\":\"zh-CN\"}]}, window.location.origin);";
        return evaluateNativeRendererScript(nativeRenderer, script, "dialog-message");
    }

    private static boolean dispatchNativeDialogMessage(View nativeRenderer, String requestId,
            boolean temporary) {
        return dispatchNativeDialogMessage(nativeRenderer, requestId, "", temporary, "Stream");
    }

    private static void startNativeThinkingUi(View conversationRoot, View nativeRenderer, String requestId) {
        setNativeTypingWaitingState(conversationRoot, true);
        setNativeConversationIndicatorState(conversationRoot, "Processing");
        renderNativeAssistantThinkingMessage(nativeRenderer, requestId);
    }

    private static void finishNativeThinkingUi(View conversationRoot) {
        setNativeTypingWaitingState(conversationRoot, false);
        setNativeConversationIndicatorState(conversationRoot, "Idle");
    }

    private static void setNativeTypingWaitingState(View conversationRoot, boolean waiting) {
        if (conversationRoot == null) {
            return;
        }
        try {
            EditText editText = findQuestionEditText(conversationRoot);
            if (editText != null) {
                if (waiting) {
                    rememberTextViewHint(editText);
                    rememberViewEnabled(editText);
                    rememberViewAlpha(editText);
                    editText.setHint("\u7b49\u5f85\u4e2d");
                    editText.setEnabled(false);
                    editText.setAlpha(0.72f);
                } else {
                    restoreTextViewHint(editText);
                    restoreViewEnabled(editText);
                    restoreViewAlpha(editText);
                }
            }
            View send = findViewByIdDeep(conversationRoot, ID_TYPING_MODE_ENTER_BUTTON);
            View sendAlt = findViewByIdDeep(conversationRoot, ID_SEND_BUTTON);
            setWaitingButtonState(send, waiting);
            if (sendAlt != send) {
                setWaitingButtonState(sendAlt, waiting);
            }
            View processing = findViewByIdDeep(conversationRoot, ID_PROCESSING_BUTTON);
            if (processing != null) {
                if (waiting) {
                    rememberViewVisibility(processing);
                    rememberViewAlpha(processing);
                    processing.setVisibility(View.VISIBLE);
                    processing.setAlpha(1.0f);
                } else {
                    restoreViewVisibility(processing);
                    restoreViewAlpha(processing);
                }
            }
            log("BixbyOpenAi: native typing waiting state " + (waiting ? "started" : "finished")
                    + ", edit=" + describeView(editText)
                    + ", send=" + describeView(send)
                    + ", processing=" + describeView(processing)
                    + ", root=" + describeView(conversationRoot));
        } catch (Throwable t) {
            log("BixbyOpenAi: native typing waiting state failed, waiting=" + waiting);
            log(t);
        }
    }

    private static void setWaitingButtonState(View view, boolean waiting) {
        if (view == null) {
            return;
        }
        if (waiting) {
            rememberViewEnabled(view);
            rememberViewAlpha(view);
            view.setEnabled(false);
            view.setAlpha(0.35f);
        } else {
            restoreViewEnabled(view);
            restoreViewAlpha(view);
        }
    }

    private static void rememberTextViewHint(TextView view) {
        if (view != null && !nativeOriginalHints.containsKey(view)) {
            nativeOriginalHints.put(view, view.getHint());
        }
    }

    private static void restoreTextViewHint(TextView view) {
        if (view == null) {
            return;
        }
        if (nativeOriginalHints.containsKey(view)) {
            view.setHint(nativeOriginalHints.remove(view));
        }
    }

    private static void rememberViewVisibility(View view) {
        if (view != null && !nativeOriginalVisibility.containsKey(view)) {
            nativeOriginalVisibility.put(view, Integer.valueOf(view.getVisibility()));
        }
    }

    private static void restoreViewVisibility(View view) {
        if (view == null) {
            return;
        }
        Integer visibility = nativeOriginalVisibility.remove(view);
        if (visibility != null) {
            view.setVisibility(visibility.intValue());
        }
    }

    private static void rememberViewEnabled(View view) {
        if (view != null && !nativeOriginalEnabled.containsKey(view)) {
            nativeOriginalEnabled.put(view, Boolean.valueOf(view.isEnabled()));
        }
    }

    private static void restoreViewEnabled(View view) {
        if (view == null) {
            return;
        }
        Boolean enabled = nativeOriginalEnabled.remove(view);
        if (enabled != null) {
            view.setEnabled(enabled.booleanValue());
        }
    }

    private static void rememberViewAlpha(View view) {
        if (view != null && !nativeOriginalAlpha.containsKey(view)) {
            nativeOriginalAlpha.put(view, Float.valueOf(view.getAlpha()));
        }
    }

    private static void restoreViewAlpha(View view) {
        if (view == null) {
            return;
        }
        Float alpha = nativeOriginalAlpha.remove(view);
        if (alpha != null) {
            view.setAlpha(alpha.floatValue());
        }
    }

    private static synchronized void appendNativeRenderingEvents(View nativeRenderer, String[] events) {
        if (nativeRenderer == null || events == null || events.length == 0) {
            return;
        }
        List<String> buffer = nativeRenderingEventBuffers.get(nativeRenderer);
        if (buffer == null) {
            buffer = new ArrayList<String>();
            nativeRenderingEventBuffers.put(nativeRenderer, buffer);
        }
        for (int i = 0; i < events.length; i++) {
            if (events[i] != null && events[i].length() > 0) {
                buffer.add(events[i]);
            }
        }
        while (buffer.size() > MAX_NATIVE_RENDERING_EVENTS) {
            buffer.remove(0);
        }
    }

    private static synchronized void upsertNativeAssistantMessageEvent(View nativeRenderer,
            String requestId, String reply, boolean temporary, boolean finish) {
        if (nativeRenderer == null || requestId == null || requestId.length() == 0) {
            return;
        }
        List<String> buffer = nativeRenderingEventBuffers.get(nativeRenderer);
        if (buffer == null) {
            buffer = new ArrayList<String>();
            nativeRenderingEventBuffers.put(nativeRenderer, buffer);
        }
        long ts = System.currentTimeMillis();
        String requestNeedle = "\"requestId\":" + jsonString(requestId);
        String dialogEvent = nativeDialogMessageEvent(requestId, ts, reply, temporary,
                finish ? "" : "Stream");
        int insertAt = -1;
        for (int i = 0; i < buffer.size(); i++) {
            String event = buffer.get(i);
            if (event != null && event.contains("\"$type\":\"DialogMessage\"")
                    && event.contains(requestNeedle)) {
                buffer.set(i, dialogEvent);
                insertAt = i;
                break;
            }
        }
        if (insertAt < 0) {
            buffer.add(dialogEvent);
            insertAt = buffer.size() - 1;
        }
        if (finish && !hasNativeOnEndEvent(buffer, requestNeedle)) {
            int endAt = Math.min(insertAt + 1, buffer.size());
            buffer.add(endAt, nativeOnEndEvent(requestId, ts + 1L));
        }
        while (buffer.size() > MAX_NATIVE_RENDERING_EVENTS) {
            buffer.remove(0);
        }
    }

    private static boolean hasNativeOnEndEvent(List<String> buffer, String requestNeedle) {
        if (buffer == null || requestNeedle == null) {
            return false;
        }
        for (int i = 0; i < buffer.size(); i++) {
            String event = buffer.get(i);
            if (event != null && event.contains("\"$type\":\"OnEnd\"")
                    && event.contains(requestNeedle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean setNativeConversationIndicatorState(View conversationRoot, String stateName) {
        if (conversationRoot == null || stateName == null || stateName.length() == 0) {
            return false;
        }
        View indicator = findViewByIdDeep(conversationRoot, ID_CONVERSATION_INDICATOR_VIEW);
        if (indicator == null) {
            indicator = findViewByIdDeep(conversationRoot, ID_CONVERSATION_INDICATOR_VIEW_ALT);
        }
        try {
            ClassLoader classLoader = indicator != null
                    ? indicator.getClass().getClassLoader()
                    : conversationRoot.getClass().getClassLoader();
            Object state = createConversationIndicatorState(classLoader, stateName);
            if (state == null) {
                log("BixbyOpenAi: native thinking state create failed, state=" + stateName);
                return false;
            }
            if (indicator != null && invokeConversationIndicatorStateSetter(
                    indicator, state, stateName, "indicator-view")) {
                indicator.setVisibility(View.VISIBLE);
                return true;
            }
            if (invokeConversationIndicatorStateSetterFromGraph(conversationRoot, state, stateName, classLoader)) {
                if (indicator != null) {
                    indicator.setVisibility(View.VISIBLE);
                }
                return true;
            }
            if (indicator == null) {
                log("BixbyOpenAi: native thinking indicator not found, state=" + stateName
                        + ", graphSearch=no-match, root=" + describeView(conversationRoot));
            } else {
                log("BixbyOpenAi: native thinking setState method not found, view="
                        + describeView(indicator) + ", state=" + stateName
                        + ", methods=" + methodNames(indicator.getClass(), 24));
                indicator.setVisibility(View.VISIBLE);
            }
            return false;
        } catch (Throwable t) {
            log("BixbyOpenAi: native thinking indicator state failed, state=" + stateName
                    + ", view=" + describeView(indicator));
            log(t);
            return false;
        }
    }

    private static Object createConversationIndicatorState(ClassLoader classLoader, String stateName)
            throws Exception {
        String base = "com.samsung.android.bixby.agent.mainui.common.conversationIndicator."
                + "ConversationIndicatorState$";
        if ("Streaming".equals(stateName)) {
            Class<?> clazz = Class.forName(base + "Streaming", false, classLoader);
            Constructor<?> constructor = clazz.getDeclaredConstructor(float.class);
            constructor.setAccessible(true);
            return constructor.newInstance(Float.valueOf(0.7f));
        }
        Class<?> clazz = Class.forName(base + stateName, false, classLoader);
        Field[] fields = clazz.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && clazz.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) {
                    return value;
                }
            }
        }
        Constructor<?> constructor = clazz.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static boolean invokeConversationIndicatorStateSetter(Object target,
            Object state, String stateName, String source) {
        if (target == null || state == null) {
            return false;
        }
        try {
            Method method = findConversationIndicatorSetStateMethod(target.getClass(), state);
            if (method == null) {
                return false;
            }
            method.setAccessible(true);
            method.invoke(target, state);
            log("BixbyOpenAi: native thinking indicator state applied, state=" + stateName
                    + ", source=" + source
                    + ", target=" + target.getClass().getName()
                    + ", method=" + method.getName());
            return true;
        } catch (Throwable t) {
            log("BixbyOpenAi: native thinking indicator setter failed, state=" + stateName
                    + ", source=" + source
                    + ", target=" + target.getClass().getName());
            log(t);
            return false;
        }
    }

    private static boolean invokeConversationIndicatorStateSetterFromGraph(Object root,
            Object state, String stateName, ClassLoader appClassLoader) {
        if (root == null || state == null) {
            return false;
        }
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>();
        ArrayList<Object> queue = new ArrayList<Object>();
        queue.add(root);
        int inspected = 0;
        for (int i = 0; i < queue.size() && i < 180; i++) {
            Object current = queue.get(i);
            if (current == null || seen.containsKey(current)) {
                continue;
            }
            seen.put(current, Boolean.TRUE);
            inspected++;
            if (current != root && invokeConversationIndicatorStateSetter(
                    current, state, stateName, "object-graph")) {
                log("BixbyOpenAi: native thinking graph match inspected=" + inspected);
                return true;
            }
            enqueueConversationIndicatorGraphChildren(current, queue, seen, appClassLoader);
        }
        log("BixbyOpenAi: native thinking graph no setter, state=" + stateName
                + ", inspected=" + inspected);
        return false;
    }

    private static void enqueueConversationIndicatorGraphChildren(Object current,
            ArrayList<Object> queue, IdentityHashMap<Object, Boolean> seen, ClassLoader appClassLoader) {
        if (current == null || queue.size() >= 220) {
            return;
        }
        if (current instanceof Map) {
            for (Object value : ((Map<?, ?>) current).values()) {
                enqueueConversationIndicatorGraphValue(value, queue, seen, appClassLoader);
            }
            return;
        }
        if (current instanceof Iterable) {
            for (Object value : (Iterable<?>) current) {
                enqueueConversationIndicatorGraphValue(value, queue, seen, appClassLoader);
            }
            return;
        }
        Class<?> clazz = current.getClass();
        while (clazz != null && clazz != Object.class && queue.size() < 220) {
            Field[] fields = clazz.getDeclaredFields();
            for (int i = 0; i < fields.length && queue.size() < 220; i++) {
                Field field = fields[i];
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                        || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    enqueueConversationIndicatorGraphValue(
                            field.get(current), queue, seen, appClassLoader);
                } catch (Throwable ignored) {
                    // Some framework fields are not worth failing the indicator bridge for.
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void enqueueConversationIndicatorGraphValue(Object value,
            ArrayList<Object> queue, IdentityHashMap<Object, Boolean> seen, ClassLoader appClassLoader) {
        if (value == null || seen.containsKey(value) || queue.size() >= 220
                || !isConversationIndicatorGraphCandidate(value, appClassLoader)) {
            return;
        }
        queue.add(value);
    }

    private static boolean isConversationIndicatorGraphCandidate(Object value, ClassLoader appClassLoader) {
        if (value == null) {
            return false;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Class
                || value instanceof ClassLoader || value instanceof Thread
                || value instanceof Handler || value instanceof Looper) {
            return false;
        }
        Class<?> clazz = value.getClass();
        if (clazz.isEnum() || clazz.isArray()) {
            return false;
        }
        if (value instanceof Map || value instanceof Iterable) {
            return true;
        }
        ClassLoader loader = clazz.getClassLoader();
        if (loader != null && loader == appClassLoader) {
            return true;
        }
        String name = clazz.getName();
        return name.startsWith("com.samsung.android.bixby.")
                || name.startsWith("Zi.") || name.startsWith("zi.")
                || name.startsWith("Ph.") || name.startsWith("ph.")
                || name.startsWith("fo.") || name.startsWith("dh.");
    }

    private static Method findConversationIndicatorSetStateMethod(Class<?> viewClass, Object state) {
        if (viewClass == null || state == null) {
            return null;
        }
        Method best = findConversationIndicatorSetStateMethod(viewClass.getMethods(), state, true);
        if (best != null) {
            return best;
        }
        Class<?> current = viewClass;
        while (current != null && current != Object.class) {
            best = findConversationIndicatorSetStateMethod(current.getDeclaredMethods(), state, false);
            if (best != null) {
                return best;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findConversationIndicatorSetStateMethod(Method[] methods,
            Object state, boolean publicOnly) {
        if (methods == null) {
            return null;
        }
        Class<?> stateClass = state.getClass();
        Method fallback = null;
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 1 || !types[0].isAssignableFrom(stateClass)) {
                continue;
            }
            String name = method.getName().toLowerCase();
            if ("setstate".equals(name) || name.contains("indicatorstate")) {
                return method;
            }
            if (name.contains("state")) {
                fallback = method;
            } else if (!publicOnly && fallback == null
                    && types[0].getName().contains("ConversationIndicatorState")) {
                fallback = method;
            }
        }
        return fallback;
    }

    private static String methodNames(Class<?> clazz, int max) {
        if (clazz == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Method[] methods = clazz.getMethods();
        for (int i = 0; i < methods.length && i < max; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(methods[i].getName());
        }
        return builder.toString();
    }

    private static synchronized void resetNativeRenderingState(View nativeRenderer, String reason) {
        if (nativeRenderer == null) {
            return;
        }
        List<String> removed = nativeRenderingEventBuffers.remove(nativeRenderer);
        nativePostMessageRequestIds.remove(nativeRenderer);
        if (removed != null && removed.size() > 0) {
            log("BixbyOpenAi: native renderer state reset reason=" + reason
                    + ", events=" + removed.size()
                    + ", view=" + describeView(nativeRenderer));
        }
    }

    private static void rememberNativeRenderingStateFromScript(View nativeRenderer,
            String script, String reason) {
        if (nativeRenderer == null || script == null || script.length() == 0) {
            return;
        }
        try {
            String state = extractRenderingStateFromScript(script);
            if (state == null || state.length() == 0) {
                return;
            }
            rememberNativeRenderingState(nativeRenderer, state, reason);
        } catch (Throwable t) {
            log("BixbyOpenAi: native renderer state sync failed reason=" + reason
                    + ", script=" + clip(script, 900));
            log(t);
        }
    }

    private static void rememberNativeRendererPostMessage(View nativeRenderer,
            String script, String reason) {
        if (nativeRenderer == null || script == null || script.length() == 0
                || Boolean.TRUE.equals(suppressNativeScriptCapture.get())) {
            return;
        }
        try {
            JSONObject outer = extractPostMessageObject(script);
            if (outer == null) {
                return;
            }
            String method = outer.optString("method", "");
            JSONArray args = outer.optJSONArray("args");
            if ("setRenderingState".equals(method)) {
                rememberNativeRenderingStateFromScript(nativeRenderer, script, reason);
                if (args != null && args.length() > 0) {
                    rememberNativeContextFromRenderingState(args.optString(0, ""), reason);
                }
            } else if ("onDialogMessage".equals(method)) {
                rememberNativeDialogPostMessage(nativeRenderer, args, reason);
            } else if ("onAsrEvent".equals(method)) {
                rememberNativeAsrPostMessage(nativeRenderer, args, reason);
            }
        } catch (Throwable t) {
            log("BixbyOpenAi: native renderer postMessage sync failed reason=" + reason
                    + ", script=" + clip(script, 900));
            log(t);
        }
    }

    private static void rememberNativeDialogPostMessage(View nativeRenderer,
            JSONArray args, String reason) {
        if (args == null || args.length() < 2) {
            return;
        }
        String requestId = jsonArrayText(args, 0);
        String text = jsonArrayText(args, 1);
        JSONObject options = args.optJSONObject(2);
        boolean temporary = options != null && options.optBoolean("isTemporary", false);
        String dialogMode = options == null ? "" : options.optString("dialogMode", "");
        if (requestId.length() == 0) {
            requestId = nativeRequestId(nativeRenderer, false);
        }
        appendNativeRenderingEvents(nativeRenderer, new String[] {
                nativeDialogMessageEvent(requestId, System.currentTimeMillis(), text,
                        temporary, dialogMode)
        });
        if (shouldRememberNativeText(text) && !temporary && !"Stream".equals(dialogMode)) {
            BixbyOpenAiClient.rememberExternalMessage(config(false), "assistant", text,
                    "native-dialog-" + reason);
        }
    }

    private static void rememberNativeAsrPostMessage(View nativeRenderer,
            JSONArray args, String reason) {
        if (args == null || args.length() == 0) {
            return;
        }
        JSONObject event = args.optJSONObject(0);
        if (event == null) {
            return;
        }
        String type = event.optString("type", "");
        String value = event.optString("value", "");
        String requestId = nativeRequestId(nativeRenderer, "START".equals(type));
        appendNativeRenderingEvents(nativeRenderer, new String[] {
                nativeAsrEvent(requestId, System.currentTimeMillis(), type, value)
        });
        if ("END".equals(type)) {
            nativePostMessageRequestIds.remove(nativeRenderer);
        }
        if ("TEXT".equals(type) && shouldRememberNativeText(value)) {
            BixbyOpenAiClient.rememberExternalMessage(config(false), "user", value,
                    "native-asr-" + reason);
        }
    }

    private static String nativeRequestId(View nativeRenderer, boolean forceNew) {
        String requestId = forceNew ? null : nativePostMessageRequestIds.get(nativeRenderer);
        if (requestId == null || requestId.length() == 0) {
            requestId = "native-" + System.currentTimeMillis() + "-"
                    + Integer.toHexString(System.identityHashCode(nativeRenderer));
            nativePostMessageRequestIds.put(nativeRenderer, requestId);
        }
        return requestId;
    }

    private static void rememberNativeContextFromRenderingState(String state, String reason)
            throws Exception {
        if (state == null || state.length() == 0) {
            return;
        }
        JSONObject json = new JSONObject(state);
        JSONArray events = json.optJSONArray("events");
        if (events == null) {
            return;
        }
        int remembered = 0;
        int from = Math.max(0, events.length() - 24);
        for (int i = from; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) {
                continue;
            }
            String type = event.optString("$type", "");
            if ("DialogMessage".equals(type)) {
                String text = event.optString("text", "");
                boolean temporary = event.optBoolean("isTemporary", false);
                String dialogMode = event.optString("dialogMode", "");
                if (shouldRememberNativeText(text) && !temporary && !"Stream".equals(dialogMode)) {
                    BixbyOpenAiClient.rememberExternalMessage(config(false), "assistant", text,
                            "native-state-" + reason);
                    remembered++;
                }
            } else if ("OnAsrEvent".equals(type)) {
                JSONObject asrEvent = event.optJSONObject("asrEvent");
                if (asrEvent != null && "TEXT".equals(asrEvent.optString("type", ""))) {
                    String value = asrEvent.optString("value", "");
                    if (shouldRememberNativeText(value)) {
                        BixbyOpenAiClient.rememberExternalMessage(config(false), "user", value,
                                "native-state-" + reason);
                        remembered++;
                    }
                }
            }
        }
        if (remembered > 0) {
            log("BixbyOpenAi: native rendering state context remembered, count="
                    + remembered + ", reason=" + reason);
        }
    }

    private static JSONObject extractPostMessageObject(String script) throws Exception {
        int start = script.indexOf("window.postMessage(");
        if (start < 0) {
            return null;
        }
        start += "window.postMessage(".length();
        int end = script.lastIndexOf(", window.location.origin");
        if (end <= start) {
            end = script.lastIndexOf(");");
        }
        if (end <= start) {
            return null;
        }
        String outerJson = script.substring(start, end).trim();
        return new JSONObject(outerJson);
    }

    private static String extractRenderingStateFromScript(String script) throws Exception {
        JSONObject outer = extractPostMessageObject(script);
        if (outer == null) {
            return null;
        }
        if (!"setRenderingState".equals(outer.optString("method", ""))) {
            return null;
        }
        JSONArray args = outer.optJSONArray("args");
        if (args == null || args.length() == 0) {
            return null;
        }
        return args.optString(0, "");
    }

    private static synchronized void rememberNativeRenderingState(View nativeRenderer,
            String state, String reason) throws Exception {
        JSONObject json = new JSONObject(state);
        JSONArray events = json.optJSONArray("events");
        if (events == null) {
            return;
        }
        List<String> buffer = new ArrayList<String>();
        int from = Math.max(0, events.length() - MAX_NATIVE_RENDERING_EVENTS);
        for (int i = from; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event != null) {
                buffer.add(event.toString());
            }
        }
        nativeRenderingEventBuffers.put(nativeRenderer, buffer);
        log("BixbyOpenAi: native renderer state synced reason=" + reason
                + ", events=" + buffer.size()
                + ", length=" + state.length()
                + ", view=" + describeView(nativeRenderer));
    }

    private static synchronized String nativeRenderingState(View nativeRenderer) {
        List<String> buffer = nativeRenderingEventBuffers.get(nativeRenderer);
        StringBuilder builder = new StringBuilder(256);
        builder.append("{\"version\":1,\"events\":[");
        if (buffer != null) {
            for (int i = 0; i < buffer.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(buffer.get(i));
            }
        }
        builder.append("]}");
        return builder.toString();
    }

    private static synchronized int nativeRenderingEventCount(View nativeRenderer) {
        List<String> buffer = nativeRenderingEventBuffers.get(nativeRenderer);
        return buffer == null ? 0 : buffer.size();
    }

    private static boolean publishNativeRenderingState(View nativeRenderer, String requestId, String reason) {
        String state = nativeRenderingState(nativeRenderer);
        String script = "window.postMessage({\"method\":\"setRenderingState\",\"args\":["
                + jsonString(state) + "]}, window.location.origin);";
        boolean ok = evaluateNativeRendererScript(nativeRenderer, script, "set-rendering-state-" + reason);
        log("BixbyOpenAi: native renderer setRenderingState published reason=" + reason
                + ", requestId=" + requestId
                + ", events=" + nativeRenderingEventCount(nativeRenderer)
                + ", length=" + state.length()
                + ", ok=" + ok);
        return ok;
    }

    private static String nativeConversationId(View nativeRenderer) {
        int identity = nativeRenderer == null ? 0 : System.identityHashCode(nativeRenderer);
        return "tr-samsung-feature-extension-" + Integer.toHexString(identity);
    }

    private static String nativeAsrEvent(String requestId, long ts, String type, String value) {
        StringBuilder builder = new StringBuilder(160);
        builder.append("{\"$type\":\"OnAsrEvent\",\"asrEvent\":{\"type\":")
                .append(jsonString(type));
        if (value != null) {
            builder.append(",\"value\":").append(jsonString(value));
        }
        builder.append("},\"ts\":").append(ts)
                .append(",\"requestId\":").append(jsonString(requestId))
                .append('}');
        return builder.toString();
    }

    private static String nativeOnStartEvent(String requestId, long ts, String conversationId) {
        StringBuilder builder = new StringBuilder(160);
        builder.append("{\"$type\":\"OnStart\",\"requestId\":")
                .append(jsonString(requestId))
                .append(",\"ts\":").append(ts);
        if (conversationId != null && conversationId.length() > 0) {
            builder.append(",\"conversationId\":").append(jsonString(conversationId));
        }
        builder.append('}');
        return builder.toString();
    }

    private static String nativeOnEndEvent(String requestId, long ts) {
        return "{\"$type\":\"OnEnd\",\"requestId\":"
                + jsonString(requestId)
                + ",\"ts\":" + ts + "}";
    }

    private static String nativeDialogMessageEvent(String requestId, long ts, String text,
            boolean temporary, String dialogMode) {
        return "{\"$type\":\"DialogMessage\",\"text\":" + jsonString(text)
                + ",\"isTemporary\":" + (temporary ? "true" : "false")
                + ",\"dialogMode\":" + jsonString(nonEmpty(dialogMode, ""))
                + ",\"locale\":\"zh-CN\",\"requestId\":"
                + jsonString(requestId)
                + ",\"ts\":" + ts + "}";
    }

    private static boolean evaluateNativeRendererScript(View nativeRenderer, String script, String reason) {
        if (nativeRenderer == null || script == null || script.length() == 0) {
            return false;
        }
        try {
            Method method = nativeRenderer.getClass().getMethod(
                    "evaluateJavascript", String.class, Class.forName("android.webkit.ValueCallback"));
            suppressNativeScriptCapture.set(Boolean.TRUE);
            try {
                method.invoke(nativeRenderer, script, null);
            } finally {
                suppressNativeScriptCapture.remove();
            }
            log("BixbyOpenAi: native renderer script sent reason=" + reason
                    + ", view=" + describeView(nativeRenderer)
                    + ", script=" + clip(script, 900));
            return true;
        } catch (Throwable t) {
            log("BixbyOpenAi: native renderer script failed reason=" + reason
                    + ", view=" + describeView(nativeRenderer));
            log(t);
            return false;
        }
    }

    private static View findNativeRendererWebView(Activity activity) {
        if (activity == null) {
            return null;
        }
        View byId = activity.findViewById(ID_WEB_VIEW);
        if (isNativeRendererWebView(byId)) {
            return byId;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        return findNativeRendererWebView(content);
    }

    private static View findNativeRendererWebView(View view) {
        if (view == null) {
            return null;
        }
        if (isNativeRendererWebView(view)) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findNativeRendererWebView(group.getChildAt(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean isNativeRendererWebView(View view) {
        if (view == null || isInsideCustomPanel(view)) {
            return false;
        }
        if (view.getId() == ID_WEB_VIEW || "web_view".equals(resourceEntryName(view))) {
            return true;
        }
        String className = view.getClass().getName().toLowerCase();
        return className.contains("bixbywebview")
                || (className.contains("webview") && findChatActivity(view) != null);
    }

    private static void removeCustomChatPanel(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            ViewGroup content = activity.findViewById(android.R.id.content);
            if (content == null) {
                return;
            }
            View existingPanel = content.findViewWithTag(TAG_CHAT_PANEL);
            if (existingPanel != null) {
                content.removeView(existingPanel);
                log("BixbyOpenAi: removed legacy custom chat panel");
            }
        } catch (Throwable t) {
            log("BixbyOpenAi: remove legacy custom chat panel failed");
            log(t);
        }
    }

    private static LinearLayout ensureChatMessageList(Activity activity) {
        if (activity == null) {
            return null;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        View existingPanel = content.findViewWithTag(TAG_CHAT_PANEL);
        if (existingPanel instanceof ViewGroup) {
            View existingList = ((ViewGroup) existingPanel).findViewWithTag(TAG_MESSAGE_LIST);
            if (existingList instanceof LinearLayout) {
                existingPanel.setVisibility(View.VISIBLE);
                return (LinearLayout) existingList;
            }
        }

        FrameLayout panel = new FrameLayout(activity);
        panel.setTag(TAG_CHAT_PANEL);
        panel.setClickable(false);
        panel.setBackgroundColor(Color.rgb(248, 249, 251));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(false);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 12));
        LinearLayout messageList = new LinearLayout(activity);
        messageList.setTag(TAG_MESSAGE_LIST);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 16));
        scrollView.addView(messageList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        panel.addView(scrollView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ViewGroup.LayoutParams rawParams;
        if (content instanceof FrameLayout) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            params.setMargins(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 104));
            rawParams = params;
        } else {
            rawParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        content.addView(panel, rawParams);
        log("BixbyOpenAi: custom chat panel attached, root=" + content.getClass().getName());
        return messageList;
    }

    private static TextView appendChatMessage(Activity activity, LinearLayout messageList,
            String text, boolean user) {
        TextView bubble = new TextView(activity);
        bubble.setText(text);
        bubble.setTextSize(15.5f);
        bubble.setTextColor(Color.rgb(25, 28, 33));
        bubble.setLineSpacing(dp(activity, 2), 1.0f);
        bubble.setPadding(dp(activity, 12), dp(activity, 9), dp(activity, 12), dp(activity, 9));
        bubble.setTextIsSelectable(true);
        bubble.setBackground(makeBubbleBackground(activity, user));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = user ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(dp(activity, 6), dp(activity, 4), dp(activity, 6), dp(activity, 8));
        bubble.setMaxWidth(Math.max(dp(activity, 220),
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 72)));
        messageList.addView(bubble, params);
        scrollChatToBottom(messageList);
        return bubble;
    }

    private static GradientDrawable makeBubbleBackground(Context context, boolean user) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(context, 18));
        if (user) {
            drawable.setColor(Color.rgb(219, 234, 254));
            drawable.setStroke(dp(context, 1), Color.rgb(191, 219, 254));
        } else {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(context, 1), Color.rgb(220, 224, 230));
        }
        return drawable;
    }

    private static void scrollChatToBottom(final LinearLayout messageList) {
        if (messageList == null) {
            return;
        }
        messageList.post(new Runnable() {
            @Override
            public void run() {
                Object parent = messageList.getParent();
                if (parent instanceof ScrollView) {
                    ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
                }
            }
        });
    }

    private static Activity findChatActivity(View view) {
        Activity activity = activityFromContext(view.getContext());
        if ((activity == null || !isChatActivity(activity)) && view.getRootView() != null) {
            activity = activityFromContext(view.getRootView().getContext());
        }
        if (activity != null && isChatActivity(activity)) {
            currentChatActivity = new WeakReference<Activity>(activity);
            return activity;
        }
        WeakReference<Activity> reference = currentChatActivity;
        Activity current = reference == null ? null : reference.get();
        if (current != null && isChatActivity(current)) {
            return current;
        }
        WeakReference<Activity> foregroundReference = currentForegroundActivity;
        Activity foreground = foregroundReference == null ? null : foregroundReference.get();
        if (foreground != null && isChatActivity(foreground)) {
            currentChatActivity = new WeakReference<Activity>(foreground);
            return foreground;
        }
        return null;
    }

    private static Activity activityFromContext(Context context) {
        Context current = context;
        while (current != null) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            if (!(current instanceof ContextWrapper)) {
                return null;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return null;
    }

    private static boolean isChatActivity(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        String name = activity.getClass().getName();
        if (CHAT_ACTIVITY.equals(name) || name.toLowerCase().contains("chatbixby")) {
            return true;
        }
        return hasChatUi(activity);
    }

    private static boolean hasChatUi(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            return activity.findViewById(ID_TYPING_EDIT_TEXT) != null
                    || activity.findViewById(ID_SEND_BUTTON) != null
                    || activity.findViewById(ID_TYPING_MODE_ENTER_BUTTON) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isLikelyChatSendButton(View view) {
        if (view == null) {
            return false;
        }
        int id = view.getId();
        if (id == ID_SEND_BUTTON || id == ID_TYPING_MODE_ENTER_BUTTON) {
            return true;
        }
        String name = resourceEntryName(view);
        return name.contains("send_button") || name.contains("typing_mode_enter_button");
    }

    private static boolean isLikelyChatQuestionInput(TextView textView) {
        if (textView == null) {
            return false;
        }
        if (textView.getId() == ID_TYPING_EDIT_TEXT) {
            return true;
        }
        String name = resourceEntryName(textView);
        if (name.contains("typing_edit_text")) {
            return true;
        }
        Activity activity = findChatActivity(textView);
        if (activity != null && isChatActivity(activity) && textView instanceof EditText) {
            return true;
        }
        return textView instanceof EditText && hasConversationUi(textView.getRootView());
    }

    private static boolean isSendEditorAction(int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEND
                || actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_GO
                || actionId == EditorInfo.IME_ACTION_SEARCH) {
            return true;
        }
        return event != null
                && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                && event.getAction() == KeyEvent.ACTION_UP
                && !event.isShiftPressed();
    }

    private static boolean shouldPassThroughToBixby(String text) {
        String normalized = normalizeCommand(text);
        if (normalized.length() == 0) {
            return false;
        }
        if (isNativeBixbyToolPrompt(normalized)) {
            return true;
        }
        String[] prefixes = new String[] {
                "打开", "启动", "运行", "开启", "关掉", "关闭", "退出", "停止",
                "拨打", "打电话", "呼叫", "发短信", "发信息", "发送短信", "发送信息",
                "导航", "带我去", "去", "搜索附近", "路线",
                "播放", "暂停", "继续播放", "下一首", "上一首", "放一首",
                "设置", "调高", "调低", "增大", "减小", "静音", "取消静音",
                "打开蓝牙", "关闭蓝牙", "打开wifi", "关闭wifi", "打开无线", "关闭无线",
                "打开热点", "关闭热点", "打开手电筒", "关闭手电筒",
                "定闹钟", "设置闹钟", "取消闹钟", "提醒我", "新建提醒", "创建提醒",
                "新建日程", "创建日程", "打开相机", "拍照", "录像", "截图",
                "open", "launch", "start", "run", "close", "stop", "call", "text",
                "message", "navigate", "play", "pause", "resume", "set", "turnon", "turnoff"
        };
        for (int i = 0; i < prefixes.length; i++) {
            if (normalized.startsWith(normalizeCommand(prefixes[i]))) {
                return true;
            }
        }
        String[] commandWords = new String[] {
                "抖音", "微信", "qq", "支付宝", "淘宝", "京东", "地图", "高德", "百度地图",
                "相机", "电话", "短信", "联系人", "日历", "闹钟", "设置", "蓝牙",
                "wifi", "无线网", "热点", "手电筒", "音量", "亮度", "飞行模式"
        };
        for (int i = 0; i < commandWords.length; i++) {
            if (normalized.contains(normalizeCommand(commandWords[i]))
                    && containsAny(normalized, new String[] {
                    "打开", "启动", "关闭", "拨打", "发送", "播放", "设置", "调高", "调低",
                    "open", "launch", "start", "close", "call", "play", "set"
            })) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNativeBixbyToolPrompt(String normalized) {
        String[] nativeToolPhrases = new String[] {
                "音乐生成", "生成音乐", "生成歌曲", "生成纯音乐", "帮我生成歌曲", "帮我生成纯音乐",
                "文档思维导图", "思维导图", "脑图", "生成脑图", "生成思维导图",
                "生成文档", "文档总结", "总结文档", "文档大纲", "生成ppt", "生成幻灯片",
                "今日新闻", "今天新闻", "新闻摘要", "今日热点", "热点新闻",
                "今日运势", "今天运势", "星座运势", "运势",
                "写一首歌", "作曲", "歌词", "生成歌词"
        };
        for (int i = 0; i < nativeToolPhrases.length; i++) {
            String phrase = normalizeCommand(nativeToolPhrases[i]);
            if (normalized.startsWith(phrase) || normalized.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, String[] needles) {
        for (int i = 0; i < needles.length; i++) {
            if (text.contains(normalizeCommand(needles[i]))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeCommand(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase()
                .replace(" ", "")
                .replace("\t", "")
                .replace("\n", "")
                .replace("，", "")
                .replace(",", "")
                .replace("。", "")
                .replace(".", "")
                .replace("！", "")
                .replace("!", "")
                .replace("？", "")
                .replace("?", "");
    }

    private static EditText findQuestionEditText(Activity activity) {
        if (activity == null) {
            return null;
        }
        View typed = activity.findViewById(ID_TYPING_EDIT_TEXT);
        if (typed instanceof EditText) {
            return (EditText) typed;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        EditText first = findFirstEditText(content, false);
        EditText nonEmpty = findFirstEditText(content, true);
        return nonEmpty != null ? nonEmpty : first;
    }

    private static EditText findQuestionEditText(View root) {
        if (root == null) {
            return null;
        }
        View typed = findViewByIdDeep(root, ID_TYPING_EDIT_TEXT);
        if (typed instanceof EditText) {
            return (EditText) typed;
        }
        EditText nonEmpty = findFirstEditText(root, true);
        return nonEmpty != null ? nonEmpty : findFirstEditText(root, false);
    }

    private static EditText findFirstEditText(View view, boolean requireText) {
        if (view == null) {
            return null;
        }
        if (view instanceof EditText) {
            EditText editText = (EditText) view;
            if (!requireText || String.valueOf(editText.getText()).trim().length() > 0) {
                return editText;
            }
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = findFirstEditText(group.getChildAt(i), requireText);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static View findViewByIdDeep(View root, int id) {
        if (root == null || id == View.NO_ID) {
            return null;
        }
        if (root.getId() == id) {
            return root;
        }
        if (!(root instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findViewByIdDeep(group.getChildAt(i), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) {
            return "";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return String.valueOf(view.getId());
        }
    }

    private static void dumpChatNativeViewTree(Activity activity, String reason) {
        if (!active(config(false)) || activity == null) {
            return;
        }
        synchronized (dumpedChatActivities) {
            if (!dumpedChatActivities.add(activity)) {
                return;
            }
        }
        try {
            View root = activity.findViewById(android.R.id.content);
            StringBuilder builder = new StringBuilder();
            int[] count = new int[] {0};
            appendInterestingViewTree(root, builder, 0, count);
            log("BixbyOpenAi: NativeRenderer chat view tree reason=" + reason
                    + ", activity=" + activity.getClass().getName()
                    + ", nodes=" + count[0] + "\n" + clip(builder.toString(), 5000));
        } catch (Throwable t) {
            log("BixbyOpenAi: NativeRenderer chat view tree dump failed");
            log(t);
        }
    }

    private static void appendInterestingViewTree(View view, StringBuilder builder, int depth, int[] count) {
        if (view == null || count[0] > 90) {
            return;
        }
        boolean interesting = isInterestingChatView(view);
        if (interesting) {
            for (int i = 0; i < depth && i < 10; i++) {
                builder.append("  ");
            }
            builder.append(describeView(view));
            if (view instanceof TextView) {
                CharSequence text = ((TextView) view).getText();
                if (text != null && text.length() > 0) {
                    builder.append(" text=").append(clip(String.valueOf(text), 160));
                }
            }
            builder.append('\n');
            count[0]++;
        }
        if (!(view instanceof ViewGroup) || depth > 18) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            appendInterestingViewTree(group.getChildAt(i), builder, depth + 1, count);
        }
    }

    private static boolean isInterestingChatView(View view) {
        if (view == null) {
            return false;
        }
        String className = view.getClass().getName().toLowerCase();
        String resourceName = resourceEntryName(view).toLowerCase();
        if (view.getId() == ID_RV_MESSAGE || view.getId() == ID_TYPING_EDIT_TEXT
                || view.getId() == ID_SEND_BUTTON || view.getId() == ID_TYPING_MODE_ENTER_BUTTON) {
            return true;
        }
        if (resourceName.contains("message") || resourceName.contains("conversation")
                || resourceName.contains("renderer") || resourceName.contains("typing")
                || resourceName.contains("send_button")) {
            return true;
        }
        return className.contains("recyclerview")
                || className.contains("webview")
                || className.contains("streaming")
                || className.contains("renderer")
                || className.contains("conversation");
    }

    private static String describeView(View view) {
        if (view == null) {
            return "View{null}";
        }
        return view.getClass().getName()
                + "{id=" + resourceEntryName(view)
                + ", visibility=" + view.getVisibility()
                + ", size=" + view.getWidth() + "x" + view.getHeight()
                + ", hash=" + Integer.toHexString(System.identityHashCode(view)) + "}";
    }

    private static boolean isLikelyNativeMessageRecyclerView(View view) {
        if (view == null || isInsideCustomPanel(view)) {
            return false;
        }
        if (view.getId() == ID_RV_MESSAGE) {
            return true;
        }
        String resourceName = resourceEntryName(view).toLowerCase();
        if (resourceName.contains("rv_message")) {
            return true;
        }
        String className = view.getClass().getName().toLowerCase();
        return className.contains("recyclerview")
                && findChatActivity(view) != null
                && (resourceName.contains("message") || resourceName.contains("conversation"));
    }

    private static boolean isLikelyNativeMessageAdapter(Object adapter) {
        if (adapter == null) {
            return false;
        }
        String className = adapter.getClass().getName();
        if (hookedNativeAdapterClasses.contains(className)) {
            return true;
        }
        String lower = className.toLowerCase();
        return lower.contains("message")
                || lower.contains("conversation")
                || lower.contains("renderer")
                || lower.contains("mainui");
    }

    private static boolean shouldLogNativeTextSet(TextView textView, CharSequence text) {
        if (textView == null || text == null || isInsideCustomPanel(textView)) {
            return false;
        }
        if (textView.getId() == ID_TYPING_EDIT_TEXT
                || textView.getId() == ID_SEND_BUTTON
                || textView.getId() == ID_TYPING_MODE_ENTER_BUTTON) {
            return false;
        }
        String value = String.valueOf(text).trim();
        if (value.length() == 0 || value.length() == 1) {
            return false;
        }
        Activity activity = findChatActivity(textView);
        if (activity == null) {
            return false;
        }
        String className = textView.getClass().getName().toLowerCase();
        String resourceName = resourceEntryName(textView).toLowerCase();
        return isDescendantOfObservedNativeList(textView)
                || className.contains("streaming")
                || className.contains("renderer")
                || className.contains("conversation")
                || resourceName.contains("message")
                || resourceName.contains("answer")
                || resourceName.contains("renderer")
                || isLikelyInterestingStack();
    }

    private static boolean isInsideCustomPanel(View view) {
        View current = view;
        while (current != null) {
            Object tag = current.getTag();
            if (TAG_CHAT_PANEL.equals(tag) || TAG_MESSAGE_LIST.equals(tag)) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean isDescendantOfObservedNativeList(View view) {
        View current = view;
        while (current != null) {
            if (current.getId() == ID_RV_MESSAGE || observedNativeMessageLists.contains(current)) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static String describeAdapter(Object adapter) {
        if (adapter == null) {
            return "null";
        }
        return adapter.getClass().getName()
                + "{hash=" + Integer.toHexString(System.identityHashCode(adapter))
                + ", text=" + clip(String.valueOf(adapter), 220) + "}";
    }

    private static String describeViewHolder(Object holder) {
        if (holder == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(holder.getClass().getName())
                .append("{hash=").append(Integer.toHexString(System.identityHashCode(holder))).append('}');
        View itemView = viewFromHolder(holder);
        if (itemView != null) {
            builder.append(", itemView=").append(describeView(itemView));
            String texts = collectViewTexts(itemView, 6, 700);
            if (texts.length() > 0) {
                builder.append(", texts=[").append(texts).append(']');
            }
        }
        return clip(builder.toString(), 1200);
    }

    private static View viewFromHolder(Object holder) {
        if (holder == null) {
            return null;
        }
        try {
            Object itemView = XposedHelpers.getObjectField(holder, "itemView");
            return itemView instanceof View ? (View) itemView : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String collectViewTexts(View view, int maxTexts, int maxLength) {
        StringBuilder builder = new StringBuilder();
        int[] count = new int[] {0};
        appendViewTexts(view, builder, count, maxTexts, maxLength);
        return clip(builder.toString(), maxLength);
    }

    private static void syncVisibleNativeConversationContext(View conversationRoot,
            BixbyOpenAiConfig config, String reason) {
        if (conversationRoot == null || config == null) {
            return;
        }
        try {
            String snapshot = visibleNativeConversationSnapshot(conversationRoot);
            if (snapshot.length() == 0) {
                return;
            }
            BixbyOpenAiClient.rememberExternalMessage(config, "system",
                    "Visible native Bixby conversation before custom model:\n" + snapshot,
                    reason);
            log("BixbyOpenAi: visible native conversation context synced, length="
                    + snapshot.length() + ", reason=" + reason);
        } catch (Throwable t) {
            log("BixbyOpenAi: visible native conversation context sync failed, reason=" + reason);
            log(t);
        }
    }

    private static String visibleNativeConversationSnapshot(View conversationRoot) {
        StringBuilder builder = new StringBuilder();
        View byId = findViewByIdDeep(conversationRoot, ID_RV_MESSAGE);
        appendNativeConversationSnapshot(builder, byId, 2600);
        synchronized (observedNativeMessageLists) {
            for (View view : observedNativeMessageLists) {
                appendNativeConversationSnapshot(builder, view, 2600);
                if (builder.length() >= 2600) {
                    break;
                }
            }
        }
        return clip(builder.toString(), 2600);
    }

    private static void appendNativeConversationSnapshot(StringBuilder builder,
            View view, int maxLength) {
        if (view == null || builder.length() >= maxLength) {
            return;
        }
        String texts = collectViewTexts(view, 18, maxLength - builder.length());
        if (texts.length() == 0) {
            return;
        }
        if (builder.indexOf(texts) >= 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(texts);
    }

    private static void appendViewTexts(View view, StringBuilder builder, int[] count,
            int maxTexts, int maxLength) {
        if (view == null || count[0] >= maxTexts || builder.length() >= maxLength) {
            return;
        }
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) {
                String value = String.valueOf(text).trim();
                if (value.length() > 0) {
                    if (builder.length() > 0) {
                        builder.append(" | ");
                    }
                    builder.append(resourceEntryName(view)).append('=').append(clip(value, 180));
                    count[0]++;
                }
            }
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            appendViewTexts(group.getChildAt(i), builder, count, maxTexts, maxLength);
        }
    }

    private static boolean shouldLogWebRendererCall(View view, String payload) {
        if (view == null || isInsideCustomPanel(view) || payload == null || payload.length() == 0) {
            return false;
        }
        Activity activity = findChatActivity(view);
        if (activity == null && !isLikelyInterestingStack()) {
            return false;
        }
        String lower = payload.toLowerCase();
        return lower.contains("renderer")
                || lower.contains("conversation")
                || lower.contains("message")
                || lower.contains("answer")
                || lower.contains("question")
                || lower.contains("chat")
                || lower.contains("bixby")
                || lower.contains("javascript")
                || lower.contains("html")
                || activity != null;
    }

    private static int dp(Context context, int value) {
        float density = context == null ? 1.0f : context.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    private static String nonEmpty(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static String jsonArrayText(JSONArray array, int index) {
        if (array == null || index < 0 || index >= array.length()) {
            return "";
        }
        Object value = array.opt(index);
        if (value == null || value == JSONObject.NULL) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static boolean shouldRememberNativeText(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() == 0 || "null".equalsIgnoreCase(trimmed)) {
            return false;
        }
        return trimmed.length() <= 4000;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        builder.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(c);
                    }
                    break;
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static void hookMember(Member member, XC_MethodHook callback) {
        try {
            if (member instanceof Method) {
                ((Method) member).setAccessible(true);
            } else if (member instanceof Constructor) {
                ((Constructor<?>) member).setAccessible(true);
            }
            XposedBridge.hookMethod(member, callback);
        } catch (Throwable t) {
            log("BixbyOpenAi: hookMember failed, member=" + member);
            log(t);
        }
    }

    private static void hookProblemSolvingApiKey(ClassLoader classLoader) throws Throwable {
        Class<?> keyClass = Class.forName(BYTEDANCE_PROBLEM_SOLVING, false, classLoader);
        Constructor<?> constructor = keyClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        XposedBridge.hookMethod(constructor, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                BixbyOpenAiConfig config = config(false);
                if (active(config)) {
                    setObjectFieldQuietly(param.thisObject, "apiKey", config.apiKey);
                }
            }
        });
        XposedHelpers.findAndHookMethod(BYTEDANCE_PROBLEM_SOLVING, classLoader,
                "getApiKey", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config)) {
                            param.setResult(config.apiKey);
                        }
                    }
                });
        XposedHelpers.findAndHookMethod(BYTEDANCE_PROBLEM_SOLVING, classLoader,
                "copy", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BixbyOpenAiConfig config = config(false);
                        if (active(config) && param.args != null && param.args.length > 0) {
                            param.args[0] = config.apiKey;
                        }
                    }
                });
    }

    private static String rewriteVolcengineStartBody(String body, BixbyOpenAiConfig config) {
        if (body == null || body.length() == 0 || !active(config)) {
            return body;
        }
        try {
            JSONObject root = new JSONObject(body);
            JSONObject topConfig = root.optJSONObject("Config");
            if (topConfig == null) {
                return body;
            }
            JSONObject llmConfig = topConfig.optJSONObject("LLMConfig");
            if (llmConfig == null) {
                return body;
            }
            String oldEndPointId = llmConfig.optString("EndPointId", "");
            llmConfig.put("EndPointId", config.model);
            JSONArray systemMessages = llmConfig.optJSONArray("SystemMessages");
            if (systemMessages == null) {
                systemMessages = new JSONArray();
            }
            appendJsonStringOnce(systemMessages, config.systemPrompt);
            llmConfig.put("SystemMessages", systemMessages);
            log("BixbyOpenAi: StartVoiceChat LLM routed oldEndPointId=" + oldEndPointId
                    + ", newModel=" + config.model
                    + ", customEndpoint=" + config.chatCompletionsEndpoint()
                    + ", systemMessages=" + systemMessages.length());
            return root.toString();
        } catch (Throwable t) {
            log("BixbyOpenAi: StartVoiceChat body rewrite failed");
            log(t);
            return body;
        }
    }

    private static void applyArkLlmConfigFields(Object target, BixbyOpenAiConfig config, String reason) {
        if (target == null || !active(config)) {
            return;
        }
        String oldEndPointId = safeCallString(target, "getEndPointId", "");
        setObjectFieldQuietly(target, "EndPointId", config.model);
        try {
            Object messages = XposedHelpers.getObjectField(target, "SystemMessages");
            setObjectFieldQuietly(target, "SystemMessages", mergeSystemMessages(messages, config));
        } catch (Throwable t) {
            log("BixbyOpenAi: ARK LLM system message field merge skipped, reason=" + reason);
            log(t);
        }
        log("BixbyOpenAi: ARK LLM config routed reason=" + reason
                + ", oldEndPointId=" + oldEndPointId
                + ", newModel=" + config.model
                + ", customEndpoint=" + config.chatCompletionsEndpoint());
    }

    private static List<String> mergeSystemMessages(Object value, BixbyOpenAiConfig config) {
        ArrayList<String> merged = new ArrayList<String>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (item != null) {
                    String text = String.valueOf(item);
                    if (text.length() > 0) {
                        merged.add(text);
                    }
                }
            }
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            for (int i = 0; i < array.length; i++) {
                if (array[i] != null) {
                    String text = String.valueOf(array[i]);
                    if (text.length() > 0) {
                        merged.add(text);
                    }
                }
            }
        } else if (value instanceof String && ((String) value).length() > 0) {
            merged.add((String) value);
        }
        if (config != null && config.systemPrompt.length() > 0 && !merged.contains(config.systemPrompt)) {
            merged.add(config.systemPrompt);
        }
        return merged;
    }

    private static void appendJsonStringOnce(JSONArray array, String value) {
        if (array == null || value == null || value.length() == 0) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i, null))) {
                return;
            }
        }
        array.put(value);
    }

    private static Object buildSolveProblemConfig(ClassLoader classLoader, BixbyOpenAiConfig config, Object original) {
        try {
            Class<?> solveClass = Class.forName(SOLVE_CONFIG, false, classLoader);
            Constructor<?> constructor = solveClass.getDeclaredConstructor(String.class, String.class, String.class);
            constructor.setAccessible(true);
            String prompt = config.systemPrompt.length() > 0
                    ? config.systemPrompt
                    : safeCallString(original, "getSolveProblemPrompt", BixbyOpenAiConfig.defaults().systemPrompt);
            return constructor.newInstance(prompt, config.model, config.chatCompletionsEndpoint());
        } catch (Throwable t) {
            log("BixbyOpenAi: build SolveProblemConfig failed");
            log(t);
            return null;
        }
    }

    private static void applySolveProblemFields(Object target, BixbyOpenAiConfig config, Object original) {
        if (target == null || config == null) {
            return;
        }
        if (config.systemPrompt.length() > 0) {
            setObjectFieldQuietly(target, "solveProblemPrompt", config.systemPrompt);
        } else {
            setObjectFieldQuietly(target, "solveProblemPrompt",
                    safeCallString(original, "getSolveProblemPrompt", BixbyOpenAiConfig.defaults().systemPrompt));
        }
        setObjectFieldQuietly(target, "modelId", config.model);
        setObjectFieldQuietly(target, "apiUrl", config.chatCompletionsEndpoint());
    }

    private static void setObjectFieldQuietly(Object target, String fieldName, Object value) {
        try {
            XposedHelpers.setObjectField(target, fieldName, value);
        } catch (Throwable t) {
            log("BixbyOpenAi: set field failed, field=" + fieldName + ", type=" + target.getClass().getName());
            log(t);
        }
    }

    private static BixbyOpenAiConfig config(boolean force) {
        long now = System.currentTimeMillis();
        BixbyOpenAiConfig current = cachedConfig;
        if (!force && current != null && now - cachedAtMs < CONFIG_CACHE_MS) {
            return current;
        }
        current = BixbyOpenAiConfig.load(appContext);
        cachedConfig = current;
        cachedAtMs = now;
        return current;
    }

    private static boolean active(BixbyOpenAiConfig config) {
        return config != null && config.enabled && config.apiKey.length() > 0;
    }

    private static boolean shouldLogNetwork(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        return lower.contains("volc")
                || lower.contains("deepseek")
                || lower.contains("openai")
                || lower.contains("chat/completions")
                || lower.contains("ai_search")
                || lower.contains("appbuilder")
                || lower.contains("rtcvideo")
                || lower.contains("conversation")
                || lower.contains("bixby");
    }

    private static boolean isInterestingState(String text) {
        if (text == null) {
            return false;
        }
        if (text.contains("command=,") && text.contains("accumulatedText=,")) {
            return false;
        }
        return true;
    }

    private static boolean isLikelyInterestingStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            String name = stack[i].getClassName();
            if (name == null) {
                continue;
            }
            String lower = name.toLowerCase();
            if (lower.startsWith("kn.")
                    || lower.startsWith("sb.")
                    || lower.contains("mainui")
                    || lower.contains("renderer")
                    || lower.contains("streaming")
                    || lower.contains("chatbixby")
                    || lower.contains("conversationhistory")
                    || lower.contains("conversation")
                    || lower.contains("rtcvideo")
                    || lower.contains("volcengine")
                    || lower.contains("solveproblem")) {
                return true;
            }
        }
        return false;
    }

    private static String stackSnippet(int maxFrames) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (int i = 3; i < stack.length && written < maxFrames; i++) {
            String className = stack[i].getClassName();
            if (className == null || className.startsWith("com.samsung.feature.extension.")) {
                continue;
            }
            if (written > 0) {
                builder.append(" <- ");
            }
            builder.append(className).append('.').append(stack[i].getMethodName()).append(':')
                    .append(stack[i].getLineNumber());
            written++;
        }
        return clip(builder.toString(), 800);
    }

    private static String safeCallString(Object target, String methodName, String fallback) {
        if (target == null) {
            return fallback;
        }
        try {
            Object value = XposedHelpers.callMethod(target, methodName);
            return value instanceof String && ((String) value).length() > 0 ? (String) value : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String describeArgs(Object[] args, int max) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(describeValue(args[i]));
            if (builder.length() > max) {
                builder.append("...");
                break;
            }
        }
        builder.append(']');
        return clip(builder.toString(), max);
    }

    private static String describeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Intent) {
            return describeIntent((Intent) value);
        }
        if (value instanceof Bundle) {
            return describeBundle((Bundle) value);
        }
        String type = value.getClass().getName();
        if (value instanceof String) {
            return "\"" + clip((String) value, 260) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        return type + "{" + clip(String.valueOf(value), 360) + "}";
    }

    private static String describeIntent(Intent intent) {
        if (intent == null) {
            return "Intent{null}";
        }
        StringBuilder builder = new StringBuilder("Intent{action=");
        builder.append(intent.getAction());
        builder.append(", data=").append(intent.getDataString());
        builder.append(", extras=").append(describeBundle(intent.getExtras()));
        builder.append('}');
        return clip(builder.toString(), 1200);
    }

    private static String describeBundle(Bundle bundle) {
        if (bundle == null) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        java.util.Set<String> keys = bundle.keySet();
        for (String key : keys) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append(key).append('=');
            Object value;
            try {
                value = bundle.get(key);
            } catch (Throwable t) {
                value = t.getClass().getSimpleName();
            }
            if (isSensitiveKey(key)) {
                builder.append("***");
            } else if (value instanceof Bundle) {
                builder.append(describeBundle((Bundle) value));
            } else {
                builder.append(clip(String.valueOf(value), 260));
            }
        }
        builder.append('}');
        return clip(builder.toString(), 1200);
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("password")
                || lower.contains("passwd")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("apikey")
                || lower.contains("api_key")
                || lower.contains("authorization");
    }

    private static String stringArg(XC_MethodHook.MethodHookParam param, int index) {
        if (param == null || param.args == null || index < 0 || index >= param.args.length) {
            return "";
        }
        Object value = param.args[index];
        return value == null ? "" : String.valueOf(value);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private static void install(String name, Installer installer) {
        try {
            installer.install();
            log("BixbyOpenAi: " + name + " installed");
        } catch (Throwable t) {
            log("BixbyOpenAi: " + name + " skipped");
            log(t);
        }
    }

    private static void log(String message) {
        if (!DiagnosticLogger.isEnabled()) {
            return;
        }
        XposedBridge.log("BixbyOpenAi: " + message);
        try {
            DiagnosticLogger.log(message);
        } catch (Throwable ignored) {
            // Ignore logging fallback failures.
        }
    }

    private static void log(Throwable throwable) {
        if (!DiagnosticLogger.isEnabled()) {
            return;
        }
        XposedBridge.log(throwable);
        try {
            DiagnosticLogger.log(throwable);
        } catch (Throwable ignored) {
            // Ignore logging fallback failures.
        }
    }

    private interface Installer {
        void install() throws Throwable;
    }
}
