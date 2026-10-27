package com.samsung.feature.extension.bixby;

import android.content.Context;
import android.os.BaseBundle;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Restores Bixby's Smart Execution entry point.
 *
 * <p>On the China build of Bixby Agent 4.0.85.9, the application manifest carries
 * {@code com.samsung.android.bixby.quick_command_supported=false}. In addition, its
 * GUI-agent availability provider follows a server-driven flag. This hook keeps the
 * local entry points available and presents the S25 Ultra model only inside Bixby's
 * own process while it builds its service requests. System identity, region, account,
 * and all other applications remain unchanged.</p>
 */
public final class BixbySmartExecutionHook implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.samsung.android.bixby.agent";
    private static final String QUICK_COMMAND_METADATA =
            "com.samsung.android.bixby.quick_command_supported";
    private static final String FEATURE_CLASS = "au.g1";
    private static final String FEATURE_GETTER = "f";
    private static final String FEATURE_NAME = "IS_QUICK_COMMAND_SUPPORTED";
    private static final String DEVICE_MODEL_HEADER = "x-device-model-name";
    private static final String GUI_AGENT_SUPPORTED_MODEL = "SM-S9380";
    private static final String HTTP_HEADER_BUILDER_CLASS = "androidx.appcompat.app.n0";
    private static final String HTTP_HEADER_BUILDER_METHOD = "o";
    private static final String GUI_AGENT_PROVIDER_CLASS =
            "com.samsung.android.bixby.agent.guiagent.GuiAgentContentProvider";
    private static final String GUI_AGENT_SUPPORT_METHOD = "isGuiAgentSupport";
    private static final String DELTA_FEATURE_CLASS = "u40.i";
    private static final String DELTA_FEATURE_REFRESH_METHOD = "z";
    private static final String GUI_AGENT_STATE_CLASS = "h11.b";
    private static final String GUI_AGENT_ONLINE_FIELD = "b";
    private static final String GUI_AGENT_ACTION_CLASS = "v40.w";
    private static final String GUI_AGENT_LEGACY_ACTION = "RUN_GUI_AGENT";
    private static final String GUI_AGENT_HANDLER_CLASS = "sp.a";
    private static final int GUI_AGENT_HANDLER_INDEX = 0x0c;
    private static final String CONVERSATION_AREA_CLASS = "y30.z";
    private static final String GUI_AGENT_TITLE_RESOURCE_CLASS = "m20.k";
    private static final String GUI_AGENT_TITLE_RESOURCE_FIELD = "ai_agent_button_text";
    private static final String GUI_AGENT_FALLBACK_TITLE = "智能执行";

    private static volatile boolean metadataHookInstalled;
    private static volatile boolean featureHookInstalled;
    private static volatile boolean processModelHookInstalled;
    private static volatile boolean requestHeaderHookInstalled;
    private static volatile boolean guiAgentProviderHookInstalled;
    private static volatile boolean guiAgentStateHookInstalled;
    private static volatile boolean guiAgentActionHookInstalled;
    private static volatile boolean guiAgentRefreshHookInstalled;
    private static volatile boolean guiAgentExecuteHookInstalled;
    private static volatile boolean guiAgentTextTriggerHookInstalled;
    private static volatile boolean guiAgentContextReadHookInstalled;
    private static final AtomicBoolean headerReplacementLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentSupportLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentOnlineLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentActionReplacementLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentActionListDumpLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentActionInjectedLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentExecuteReplacementLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentTextTriggerLogged = new AtomicBoolean(false);
    private static final AtomicBoolean guiAgentContextPending = new AtomicBoolean(false);
    private static final AtomicInteger guiAgentContextReads = new AtomicInteger(0);

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        installMetadataHook();
        installFeatureHook(lpparam.classLoader);
        installProcessModelHook();
        installRequestHeaderHook(lpparam.classLoader);
        installGuiAgentStateHook(lpparam.classLoader);
        installGuiAgentProviderHook(lpparam.classLoader);
        installGuiAgentActionHook(lpparam.classLoader);
        installGuiAgentRefreshHook(lpparam.classLoader);
        installGuiAgentExecuteHook(lpparam.classLoader);
        installGuiAgentContextReadHook(lpparam.classLoader);
        installGuiAgentTextTriggerHook(lpparam.classLoader);
    }

    /**
     * Bixby reads {@link Build#MODEL} directly in several independently-versioned
     * HTTP and gRPC builders. The field belongs to this app process's VM, so changing
     * it here cannot alter the Android system property or any other application.
     */
    private static void installProcessModelHook() {
        if (processModelHookInstalled) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (processModelHookInstalled) {
                return;
            }
            try {
                Field model = Build.class.getDeclaredField("MODEL");
                model.setAccessible(true);
                model.set(null, GUI_AGENT_SUPPORTED_MODEL);
                processModelHookInstalled = true;
                log("Bixby process model set to " + GUI_AGENT_SUPPORTED_MODEL);
            } catch (Throwable throwable) {
                log("process-model hook failed: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * Covers the manifest-read path. Keeping this at the metadata boundary makes the
     * hook work for builds where Bixby's feature implementation class is renamed.
     */
    private static void installMetadataHook() {
        if (metadataHookInstalled) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (metadataHookInstalled) {
                return;
            }
            try {
                Method getBoolean = BaseBundle.class.getDeclaredMethod("getBoolean", String.class);
                XposedBridge.hookMethod(getBoolean, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length == 1
                                && QUICK_COMMAND_METADATA.equals(param.args[0])) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });
                metadataHookInstalled = true;
            } catch (Throwable throwable) {
                log("manifest metadata hook failed: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * Covers Bixby's already-created feature object, including a false value cached
     * before the metadata read. The enum-name check prevents touching other flags.
     */
    private static void installFeatureHook(ClassLoader classLoader) {
        if (featureHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (featureHookInstalled) {
                return;
            }
            try {
                Class<?> featureClass = Class.forName(FEATURE_CLASS, false, classLoader);
                Method getFeatureEnabled = featureClass.getDeclaredMethod(FEATURE_GETTER);
                getFeatureEnabled.setAccessible(true);
                XposedBridge.hookMethod(getFeatureEnabled, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object feature = param.thisObject;
                        if (feature instanceof Enum
                                && FEATURE_NAME.equals(((Enum) feature).name())) {
                            param.setResult(Boolean.TRUE);
                        }
                    }
                });
                featureHookInstalled = true;
            } catch (Throwable throwable) {
                // The exact metadata hook remains usable when this obfuscated class moves.
                log("feature cache hook skipped: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * The China GUI-agent capsule makes its availability decision on the device model
     * carried by Bixby's HTTP header. This targets Bixby's own multi-value HTTP-header
     * builder rather than {@code HashMap}, so the builder retains its required
     * {@code List<String>} values. Build.MODEL, the Android system property, sales
     * code, country, and all unrelated request headers remain untouched.
     */
    private static void installRequestHeaderHook(ClassLoader classLoader) {
        if (requestHeaderHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (requestHeaderHookInstalled) {
                return;
            }
            try {
                Class<?> headerBuilder = Class.forName(HTTP_HEADER_BUILDER_CLASS, false, classLoader);
                Method putHeader = headerBuilder.getDeclaredMethod(
                        HTTP_HEADER_BUILDER_METHOD, String.class, String.class);
                putHeader.setAccessible(true);
                XposedBridge.hookMethod(putHeader, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length != 2
                                || !DEVICE_MODEL_HEADER.equals(param.args[0])) {
                            return;
                        }
                        param.args[1] = GUI_AGENT_SUPPORTED_MODEL;
                        if (headerReplacementLogged.compareAndSet(false, true)) {
                            log("replaced Bixby HTTP model header");
                        }
                    }
                });
                requestHeaderHookInstalled = true;
            } catch (Throwable throwable) {
                log("HTTP model-header hook failed: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * The capability provider is queried by Bixby's GUI-agent entry points. Its
     * original value is reset by a server action list on older supported models;
     * returning true here prevents that local reset from hiding the entry point.
     */
    private static void installGuiAgentProviderHook(ClassLoader classLoader) {
        if (guiAgentProviderHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentProviderHookInstalled) {
                return;
            }
            try {
                Class<?> provider = Class.forName(GUI_AGENT_PROVIDER_CLASS, false, classLoader);
                Method call = provider.getDeclaredMethod(
                        "call", String.class, String.class, Bundle.class);
                call.setAccessible(true);
                XposedBridge.hookMethod(call, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length == 0
                                || !GUI_AGENT_SUPPORT_METHOD.equals(param.args[0])) {
                            return;
                        }
                        Object result = param.getResult();
                        Bundle bundle = result instanceof Bundle ? (Bundle) result : new Bundle();
                        bundle.putBoolean(GUI_AGENT_SUPPORT_METHOD, true);
                        param.setResult(bundle);
                        if (guiAgentSupportLogged.compareAndSet(false, true)) {
                            log("forced GUI-agent capability provider to supported");
                        }
                    }
                });
                guiAgentProviderHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent provider hook skipped: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * Newer service responses use {@code turnOnGuiAgentButton}, while the older
     * China client only marks the feature online for {@code RUN_GUI_AGENT}. Keep
     * the availability flag online after the client refreshes its server-driven
     * action list. The tag itself is deliberately left to Bixby's normal action
     * handler so it appears only for an actual Smart Execution request and then
     * follows the app's ordinary dismissal lifecycle.
     */
    private static void installGuiAgentStateHook(ClassLoader classLoader) {
        if (guiAgentStateHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentStateHookInstalled) {
                return;
            }
            try {
                Class<?> deltaFeature = Class.forName(DELTA_FEATURE_CLASS, false, classLoader);
                Class<?> guiAgentState = Class.forName(GUI_AGENT_STATE_CLASS, false, classLoader);
                final Field onlineField = guiAgentState.getDeclaredField(GUI_AGENT_ONLINE_FIELD);
                onlineField.setAccessible(true);
                Method refresh = deltaFeature.getDeclaredMethod(DELTA_FEATURE_REFRESH_METHOD);
                refresh.setAccessible(true);
                XposedBridge.hookMethod(refresh, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        forceGuiAgentOnline(onlineField);
                    }
                });
                forceGuiAgentOnline(onlineField);
                guiAgentStateHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent state hook skipped: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * Bixby 4.0.85.9 renamed the server action that exposes Smart Execution.
     * The local action list and click dispatcher still understand only
     * {@code RUN_GUI_AGENT}. Normalize the newer names while the action model is
     * being constructed; this keeps the normal click and dismissal lifecycle
     * intact and does not select the tag during startup or feature refresh.
     */
    private static void installGuiAgentActionHook(ClassLoader classLoader) {
        if (guiAgentActionHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentActionHookInstalled) {
                return;
            }
            try {
                Class<?> actionClass = Class.forName(GUI_AGENT_ACTION_CLASS, false, classLoader);
                int hookedConstructors = 0;
                for (Constructor<?> constructor : actionClass.getDeclaredConstructors()) {
                    constructor.setAccessible(true);
                    XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null) {
                                return;
                            }
                            for (int i = 0; i < param.args.length; i++) {
                                if (!(param.args[i] instanceof String)) {
                                    continue;
                                }
                                String action = (String) param.args[i];
                                if (!isGuiAgentAction(action)
                                        || GUI_AGENT_LEGACY_ACTION.equals(action)) {
                                    continue;
                                }
                                param.args[i] = GUI_AGENT_LEGACY_ACTION;
                                if (guiAgentActionReplacementLogged.compareAndSet(false, true)) {
                                    log("normalized GUI-agent action: " + action);
                                }
                            }
                        }
                    });
                    hookedConstructors++;
                }
                if (hookedConstructors == 0) {
                    log("GUI-agent action hook skipped: constructor not found");
                    return;
                }
                guiAgentActionHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent action hook skipped: " + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * The action-bar list is populated by a coroutine callback and immediately
     * consumed by {@code u40.i.z()}. Some Bixby builds deserialize the action
     * object through a synthetic constructor that does not reliably pass through
     * the normal constructor hook. Normalize the already-created list at this
     * boundary as well, before LiveData is posted to the UI.
     */
    private static void installGuiAgentRefreshHook(ClassLoader classLoader) {
        if (guiAgentRefreshHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentRefreshHookInstalled) {
                return;
            }
            try {
                Class<?> deltaFeature = Class.forName(DELTA_FEATURE_CLASS, false, classLoader);
                Method refresh = deltaFeature.getDeclaredMethod(DELTA_FEATURE_REFRESH_METHOD);
                refresh.setAccessible(true);
                XposedBridge.hookMethod(refresh, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        // The 4.0.85.9 China service can return an empty action list even
                        // after the capability flag has been accepted.  Put the local
                        // action back before z() filters/posts the list so both the
                        // action bar and the online-state scan see it.
                        ensureGuiAgentAction(param.thisObject, "y", true);
                        ensureGuiAgentAction(param.thisObject, "A", false);
                        ensureGuiAgentAction(param.thisObject, "D", false);
                        normalizeActionList(param.thisObject, "y");
                        normalizeActionList(param.thisObject, "A");
                        normalizeActionList(param.thisObject, "D");
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        // A coroutine may replace y/A while z() is running.  Re-check
                        // after the original method as well; the operation is idempotent.
                        ensureGuiAgentAction(param.thisObject, "y", true);
                        ensureGuiAgentAction(param.thisObject, "A", false);
                        ensureGuiAgentAction(param.thisObject, "D", false);
                        forceGuiAgentOnline(findGuiAgentOnlineField(param.thisObject));
                    }
                });
                guiAgentRefreshHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent action-list hook skipped: "
                        + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * Re-adds the action locally when the server response contains no GUI-agent
     * entry.  The action is deliberately a normal {@code v40.w} instance, so the
     * existing action-bar click path (and its regular dismissal lifecycle) is used.
     */
    private static void ensureGuiAgentAction(Object owner, String fieldName, boolean allowEmpty) {
        if (owner == null) {
            return;
        }
        try {
            Field listField = findField(owner.getClass(), fieldName);
            if (listField == null) {
                return;
            }
            listField.setAccessible(true);
            Object value = listField.get(owner);
            java.util.List<?> source;
            if (value == null) {
                source = java.util.Collections.emptyList();
            } else if (value instanceof java.util.List) {
                source = (java.util.List<?>) value;
            } else {
                return;
            }
            if (!allowEmpty && source.isEmpty()) {
                return;
            }

            for (Object item : source) {
                if (isGuiAgentAction(readActionName(item))) {
                    return;
                }
            }

            Object action = createGuiAgentAction(owner);
            if (action == null) {
                return;
            }
            java.util.ArrayList<Object> replacement = new java.util.ArrayList<Object>(source);
            replacement.add(action);
            listField.set(owner, replacement);
            if (guiAgentActionInjectedLogged.compareAndSet(false, true)) {
                log("injected local GUI-agent action into " + fieldName);
            }
        } catch (Throwable throwable) {
            log("GUI-agent action injection skipped: "
                    + throwable.getClass().getSimpleName());
        }
    }

    private static Object createGuiAgentAction(Object owner) {
        try {
            ClassLoader classLoader = owner.getClass().getClassLoader();
            Class<?> actionClass = Class.forName(GUI_AGENT_ACTION_CLASS, false, classLoader);
            Class<?> managerClass = Class.forName("y30.c", false, classLoader);
            Field managerField = findField(owner.getClass(), "b");
            Object manager = null;
            if (managerField != null) {
                managerField.setAccessible(true);
                manager = managerField.get(owner);
            }
            String title = resolveGuiAgentTitle(classLoader);

            // This is the compact constructor used by the current and several
            // previous Bixby builds.  Its fourth string is the action-bar callback
            // field (v40.w.h), which q2 dispatches as RUN_GUI_AGENT.
            try {
                Constructor<?> compact = actionClass.getDeclaredConstructor(
                        String.class, String.class, String.class, String.class, managerClass);
                compact.setAccessible(true);
                return compact.newInstance(title, "OtherHandler", "RUN_GUI_AGENT",
                        GUI_AGENT_LEGACY_ACTION, manager);
            } catch (NoSuchMethodException ignored) {
                // Fall through to the full data constructor used by older builds.
            }

            for (Constructor<?> constructor : actionClass.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length != 10 || types[0] != String.class || types[1] != String.class
                        || types[2] != String.class || types[3] != String.class
                        || types[4] != String.class || types[5] != boolean.class
                        || types[6] != String.class || types[7] != managerClass) {
                    continue;
                }
                constructor.setAccessible(true);
                return constructor.newInstance(title, "OtherHandler", "RUN_GUI_AGENT",
                        "smart execution", null, true, GUI_AGENT_LEGACY_ACTION, manager,
                        null, null);
            }
        } catch (Throwable throwable) {
            log("GUI-agent action construction failed: "
                    + throwable.getClass().getSimpleName());
        }
        return null;
    }

    private static String resolveGuiAgentTitle(ClassLoader classLoader) {
        try {
            Class<?> contextHolder = Class.forName("md.a", false, classLoader);
            Method getContext = contextHolder.getDeclaredMethod("a0");
            getContext.setAccessible(true);
            Object contextObject = getContext.invoke(null);
            if (contextObject instanceof Context) {
                Class<?> resourceClass = Class.forName(
                        GUI_AGENT_TITLE_RESOURCE_CLASS, false, classLoader);
                Field resourceId = resourceClass.getDeclaredField(GUI_AGENT_TITLE_RESOURCE_FIELD);
                resourceId.setAccessible(true);
                String title = ((Context) contextObject).getString(resourceId.getInt(null));
                if (title != null && !title.trim().isEmpty()) {
                    return title;
                }
            }
        } catch (Throwable ignored) {
            // The resource name changed in some old builds; use the stable label.
        }
        return GUI_AGENT_FALLBACK_TITLE;
    }

    private static String readActionName(Object item) {
        if (item == null || !GUI_AGENT_ACTION_CLASS.equals(item.getClass().getName())) {
            return null;
        }
        try {
            Field actionField = findField(item.getClass(), "h");
            if (actionField != null) {
                actionField.setAccessible(true);
                Object value = actionField.get(item);
                return value instanceof String ? (String) value : null;
            }
        } catch (Throwable ignored) {
            // Optional compatibility inspection.
        }
        return null;
    }

    private static void normalizeActionList(Object owner, String fieldName) {
        if (owner == null) {
            return;
        }
        try {
            Field listField = findField(owner.getClass(), fieldName);
            if (listField == null) {
                return;
            }
            listField.setAccessible(true);
            Object value = listField.get(owner);
            if (!(value instanceof java.util.List)) {
                return;
            }
            java.util.List<?> source = (java.util.List<?>) value;
            if (guiAgentActionListDumpLogged.compareAndSet(false, true)) {
                log("GUI-agent action list " + fieldName + " size=" + source.size());
                for (Object entry : source) {
                    if (entry == null) {
                        continue;
                    }
                    Field entryAction = findField(entry.getClass(), "h");
                    Object actionValue = null;
                    if (entryAction != null) {
                        entryAction.setAccessible(true);
                        actionValue = entryAction.get(entry);
                    }
                    log("GUI-agent action entry class=" + entry.getClass().getName()
                            + " action=" + actionValue);
                }
            }
            java.util.ArrayList<Object> normalized = null;
            for (int i = 0; i < source.size(); i++) {
                Object item = source.get(i);
                Object replacement = normalizeActionObject(item);
                if (replacement != item) {
                    if (normalized == null) {
                        normalized = new java.util.ArrayList<Object>(source);
                    }
                    normalized.set(i, replacement);
                }
            }
            if (normalized != null) {
                listField.set(owner, normalized);
            }
        } catch (Throwable throwable) {
            // Action-list compatibility is optional; never affect Bixby startup.
            log("GUI-agent action-list normalization skipped: "
                    + throwable.getClass().getSimpleName());
        }
    }

    private static Object normalizeActionObject(Object item) {
        if (item == null || !GUI_AGENT_ACTION_CLASS.equals(item.getClass().getName())) {
            return item;
        }
        try {
            Field actionField = findField(item.getClass(), "h");
            if (actionField == null) {
                return item;
            }
            actionField.setAccessible(true);
            Object rawAction = actionField.get(item);
            if (!(rawAction instanceof String)) {
                return item;
            }
            String action = (String) rawAction;
            if (!isGuiAgentAction(action) || GUI_AGENT_LEGACY_ACTION.equals(action)) {
                return item;
            }
            Constructor<?> copy = item.getClass().getDeclaredConstructor(
                    String.class, String.class, String.class, String.class, String.class,
                    boolean.class, String.class, Class.forName("y30.c", false,
                            item.getClass().getClassLoader()), String.class, Integer.class);
            copy.setAccessible(true);
            Field b = findField(item.getClass(), "b");
            Field c = findField(item.getClass(), "c");
            Field d = findField(item.getClass(), "d");
            Field e = findField(item.getClass(), "e");
            Field f = findField(item.getClass(), "f");
            Field g = findField(item.getClass(), "g");
            Field i = findField(item.getClass(), "i");
            Field j = findField(item.getClass(), "j");
            Field k = findField(item.getClass(), "k");
            if (b == null || c == null || d == null || e == null || f == null
                    || g == null || i == null || j == null || k == null) {
                return item;
            }
            for (Field field : new Field[] {b, c, d, e, f, g, i, j, k}) {
                field.setAccessible(true);
            }
            Object replacement = copy.newInstance(
                    b.get(item), c.get(item), d.get(item), e.get(item), f.get(item),
                    g.getBoolean(item), GUI_AGENT_LEGACY_ACTION, i.get(item), j.get(item),
                    k.get(item));
            if (guiAgentActionReplacementLogged.compareAndSet(false, true)) {
                log("normalized GUI-agent action-list entry: " + action);
            }
            return replacement;
        } catch (Throwable throwable) {
            log("GUI-agent action copy failed: " + throwable.getClass().getSimpleName());
            return item;
        }
    }

    /**
     * The server-side phrase resolver can use a newer GUI-agent action name than
     * the local China handler registration.  Normalize that name at the handler
     * boundary and make the turn-on variants select the local AiAgent tag before
     * the original handler checks its state.
     */
    private static void installGuiAgentExecuteHook(ClassLoader classLoader) {
        if (guiAgentExecuteHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentExecuteHookInstalled) {
                return;
            }
            try {
                Class<?> handlerClass = Class.forName(GUI_AGENT_HANDLER_CLASS, false, classLoader);
                int hooked = 0;
                for (Method method : handlerClass.getDeclaredMethods()) {
                    if (!"executeAction".equals(method.getName())
                            || method.getParameterTypes().length != 4) {
                        continue;
                    }
                    method.setAccessible(true);
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 2
                                    || !(param.args[1] instanceof String)) {
                                return;
                            }
                            Field indexField = findField(param.thisObject.getClass(), "a");
                            if (indexField == null) {
                                return;
                            }
                            try {
                                indexField.setAccessible(true);
                                if (indexField.getInt(param.thisObject)
                                        != GUI_AGENT_HANDLER_INDEX) {
                                    return;
                                }
                            } catch (Throwable ignored) {
                                return;
                            }

                            String action = (String) param.args[1];
                            String canonical = canonicalGuiAgentAction(action);
                            if (canonical == null) {
                                return;
                            }
                            if (!canonical.equals(action)) {
                                param.args[1] = canonical;
                                if (guiAgentExecuteReplacementLogged.compareAndSet(false, true)) {
                                    log("normalized GUI-agent execute action: " + action);
                                }
                            }
                            if ("TurnOnGuiAgentButton".equals(canonical)
                                    || "EnableGuiAgentTag".equals(canonical)) {
                                turnOnGuiAgentTag(param.thisObject.getClass().getClassLoader());
                            }
                        }
                    });
                    hooked++;
                }
                if (hooked > 0) {
                    guiAgentExecuteHookInstalled = true;
                } else {
                    log("GUI-agent execute hook skipped: method not found");
                }
            } catch (Throwable throwable) {
                log("GUI-agent execute hook skipped: "
                        + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * When the AiAgent tag is absent, the China service can classify a GUI
     * instruction as an unsupported capsule.  Add the same GuiAutomation
     * multimodal context used by Bixby's tag implementation, without changing
     * the visible tag state or bypassing the normal conversation request.
     */
    private static void installGuiAgentTextTriggerHook(ClassLoader classLoader) {
        if (guiAgentTextTriggerHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentTextTriggerHookInstalled) {
                return;
            }
            try {
                Class<?> conversationArea = Class.forName(
                        CONVERSATION_AREA_CLASS, false, classLoader);
                Method processText = conversationArea.getDeclaredMethod(
                        "v", String.class, boolean.class);
                processText.setAccessible(true);
                XposedBridge.hookMethod(processText, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args == null || param.args.length == 0
                                || !(param.args[0] instanceof String)
                                || !isLikelyGuiAgentInstruction((String) param.args[0])) {
                            return;
                        }
                        if (addGuiAutomationContext(param.thisObject.getClass().getClassLoader())
                                && guiAgentTextTriggerLogged.compareAndSet(false, true)) {
                            log("injected GUI-agent context for direct instruction without tag");
                        }
                    }
                });
                guiAgentTextTriggerHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent text trigger hook skipped: "
                        + throwable.getClass().getSimpleName());
            }
        }
    }

    /**
     * A native GuiAgent tag is consumed by the request builder and then removed
     * by the normal conversation lifecycle.  Direct text requests do not toggle
     * that tag, so explicitly remove the injected context after the request
     * builder has read it (the 4.0.85.9 client reads it twice).
     */
    private static void installGuiAgentContextReadHook(ClassLoader classLoader) {
        if (guiAgentContextReadHookInstalled || classLoader == null) {
            return;
        }
        synchronized (BixbySmartExecutionHook.class) {
            if (guiAgentContextReadHookInstalled) {
                return;
            }
            try {
                Class<?> manager = Class.forName("sx.a", false, classLoader);
                Method getContext = manager.getDeclaredMethod("b");
                getContext.setAccessible(true);
                XposedBridge.hookMethod(getContext, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!guiAgentContextPending.get()) {
                            return;
                        }
                        Object result = param.getResult();
                        Object copied = copyContextResult(result, classLoader);
                        if (copied != null) {
                            param.setResult(copied);
                        }
                        if (guiAgentContextReads.incrementAndGet() >= 2
                                && guiAgentContextPending.compareAndSet(true, false)) {
                            removeInjectedGuiAutomation(param.thisObject, classLoader);
                        }
                    }
                });
                guiAgentContextReadHookInstalled = true;
            } catch (Throwable throwable) {
                log("GUI-agent context read hook skipped: "
                        + throwable.getClass().getSimpleName());
            }
        }
    }

    private static Object copyContextResult(Object result, ClassLoader classLoader) {
        try {
            if (result == null) {
                return null;
            }
            Field contentsField = findField(result.getClass(), "a");
            if (contentsField == null) {
                return null;
            }
            contentsField.setAccessible(true);
            Object contents = contentsField.get(result);
            if (!(contents instanceof List)) {
                return null;
            }
            Class<?> contextClass = Class.forName("i00.c", false, classLoader);
            Constructor<?> constructor = contextClass.getDeclaredConstructor(List.class);
            constructor.setAccessible(true);
            return constructor.newInstance(new ArrayList<>((List<?>) contents));
        } catch (Throwable throwable) {
            log("GUI-agent context copy failed: " + throwable.getClass().getSimpleName());
            return null;
        }
    }

    private static void removeInjectedGuiAutomation(Object manager, ClassLoader classLoader) {
        try {
            Field contentsField = findField(manager.getClass(), "a");
            if (contentsField != null) {
                contentsField.setAccessible(true);
                Object contents = contentsField.get(manager);
                if (contents instanceof List) {
                    List<?> list = (List<?>) contents;
                    list.removeIf(item -> {
                        try {
                            Field typeField = findField(item.getClass(), "b");
                            if (typeField == null) {
                                return false;
                            }
                            typeField.setAccessible(true);
                            Object type = typeField.get(item);
                            return type instanceof Enum
                                    && "GuiAutomation".equals(((Enum<?>) type).name());
                        } catch (Throwable ignored) {
                            return false;
                        }
                    });
                }
            }
            setStaticBoolean(classLoader, "bt.c", "a", false);
            setStaticBoolean(classLoader, "bt.c", "s", false);
            guiAgentContextReads.set(0);
            log("cleared one-shot GUI-agent context after request read");
        } catch (Throwable throwable) {
            log("GUI-agent context cleanup failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static boolean isLikelyGuiAgentInstruction(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim().replace(" ", "").replace("　", "");
        if (text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String[] markers = {
                "智能执行", "smart execution", "smart_execution", "ai agent", "aiagent",
                "帮我点", "请帮我点", "帮我点击", "请帮我点击", "帮我在", "请帮我在",
                "点击", "点开", "点按", "长按", "滑动", "拖动", "填写", "滚动",
                "选择", "切换", "在其中", "在里面", "open ", "tap ", "click ",
                "swipe ", "scroll ", "type "
        };
        for (String marker : markers) {
            String needle = marker.toLowerCase(Locale.ROOT).replace(" ", "");
            if (lower.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean addGuiAutomationContext(ClassLoader classLoader) {
        if (classLoader == null) {
            return false;
        }
        try {
            // A user-selected tag already owns its normal lifecycle.  Do not
            // convert it into a one-shot injected context or clear it here.
            if (getStaticBoolean(classLoader, "bt.c", "s")) {
                return false;
            }
            Class<?> urlProvider = Class.forName("cr.a", false, classLoader);
            Field providerField = findField(urlProvider, "a");
            if (providerField == null) {
                return false;
            }
            providerField.setAccessible(true);
            String url = null;
            for (Method method : urlProvider.getDeclaredMethods()) {
                if ("h".equals(method.getName()) && method.getParameterTypes().length == 0
                        && method.getReturnType() == String.class) {
                    method.setAccessible(true);
                    // cr.a.h() is a static Kotlin method.  Passing the singleton
                    // instance here works on some ART builds but throws on others.
                    Object value = method.invoke(null);
                    url = value instanceof String ? (String) value : null;
                    break;
                }
            }
            Class<?> coreService = Class.forName(
                    "com.samsung.android.bixby.agent.coreservice.BixbyCoreService", false, classLoader);
            Field coreField = findField(coreService, "e");
            if (coreField == null) {
                return false;
            }
            coreField.setAccessible(true);
            Object core = coreField.get(null);
            if (core == null) {
                log("GUI-agent context injection deferred: core service is not ready");
                return false;
            }
            Class<?> attachmentType = Class.forName("i00.a", false, classLoader);
            Object guiAutomation = Enum.valueOf((Class) attachmentType, "GuiAutomation");
            Method addContext = core.getClass().getDeclaredMethod(
                    "w", attachmentType, String.class);
            addContext.setAccessible(true);
            // These are the same transient multimodal-state markers written by
            // GuiAgentManager$2.addGuiAgentTagIfNeeded().  They do not select or
            // render the Smart Execution chip; they keep the request assembler
            // from discarding the newly-added context as stale.
            setStaticBoolean(classLoader, "bt.c", "a", true);
            setStaticBoolean(classLoader, "bt.c", "s", true);
            addContext.invoke(core, guiAutomation, url == null ? "" : url);
            guiAgentContextReads.set(0);
            guiAgentContextPending.set(true);
            return true;
        } catch (Throwable throwable) {
            log("GUI-agent context injection failed: " + throwable.getClass().getSimpleName());
        }
        return false;
    }

    private static void setStaticBoolean(ClassLoader classLoader, String className,
                                         String fieldName, boolean value) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.setBoolean(null, value);
        } catch (Throwable ignored) {
            // Older clients do not have one or both transient marker fields.
        }
    }

    private static boolean getStaticBoolean(ClassLoader classLoader, String className,
                                            String fieldName) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String canonicalGuiAgentAction(String action) {
        if (action == null || !isGuiAgentAction(action)) {
            return null;
        }
        String normalized = action.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        if ("turnonguiagentbutton".equals(normalized)) {
            return "TurnOnGuiAgentButton";
        }
        if ("enableguiagenttag".equals(normalized)) {
            return "EnableGuiAgentTag";
        }
        if ("gotoguiagent".equals(normalized)) {
            return "GoToGUIAgent";
        }
        // RUN_GUI_AGENT is a UI-only action-bar callback.  The capsule handler
        // accepts GO_TO_GUI_AGENT for an utterance and preserves its query bundle.
        return "GoToGUIAgent";
    }

    private static void turnOnGuiAgentTag(ClassLoader classLoader) {
        try {
            Class<?> helper = Class.forName("y30.b0", false, classLoader);
            Method turnOn = helper.getDeclaredMethod("d");
            turnOn.setAccessible(true);
            turnOn.invoke(null);
        } catch (Throwable ignored) {
            // The normal handler can still turn the tag on when its manager is ready.
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Field findGuiAgentOnlineField(Object owner) {
        try {
            ClassLoader classLoader = owner == null
                    ? BixbySmartExecutionHook.class.getClassLoader()
                    : owner.getClass().getClassLoader();
            Class<?> stateClass = Class.forName(GUI_AGENT_STATE_CLASS, false, classLoader);
            Field field = stateClass.getDeclaredField(GUI_AGENT_ONLINE_FIELD);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isGuiAgentAction(String action) {
        if (action == null) {
            return false;
        }
        String normalized = action.replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
        return "runguiagent".equals(normalized)
                || "turnonguiagentbutton".equals(normalized)
                || "enableguiagenttag".equals(normalized)
                || "gotoguiagent".equals(normalized)
                || "startguiagent".equals(normalized)
                || "actionrunguiagent".equals(normalized)
                || "actionstartguiagent".equals(normalized)
                || "smartexecution".equals(normalized)
                || "opensmartexecution".equals(normalized);
    }

    private static void forceGuiAgentOnline(Field onlineField) {
        if (onlineField == null) {
            return;
        }
        try {
            onlineField.setBoolean(null, true);
            if (guiAgentOnlineLogged.compareAndSet(false, true)) {
                log("kept GUI-agent availability online after feature refresh");
            }
        } catch (Throwable throwable) {
            log("GUI-agent state update failed: " + throwable.getClass().getSimpleName());
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log("SFE.BixbySmartExecution: " + message);
        } catch (Throwable ignored) {
            // Logging must not affect Bixby startup.
        }
    }
}
