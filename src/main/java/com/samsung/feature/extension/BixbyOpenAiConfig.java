package com.samsung.feature.extension;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

public final class BixbyOpenAiConfig {
    public static final String AUTHORITY = "com.samsung.feature.extension.bixbyopenai";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String METHOD_GET = "get";
    public static final String METHOD_SET = "set";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_BASE_URL = "baseUrl";
    public static final String EXTRA_API_KEY = "apiKey";
    public static final String EXTRA_MODEL = "model";
    public static final String EXTRA_SYSTEM_PROMPT = "systemPrompt";

    private static final String MODULE_PACKAGE = "com.samsung.feature.extension";
    private static final String PREFS = "bixby_openai";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";

    public final boolean enabled;
    public final String baseUrl;
    public final String apiKey;
    public final String model;
    public final String systemPrompt;

    public BixbyOpenAiConfig(boolean enabled, String baseUrl, String apiKey, String model, String systemPrompt) {
        this.enabled = enabled;
        this.baseUrl = nonEmpty(baseUrl, DEFAULT_BASE_URL);
        this.apiKey = trim(apiKey);
        this.model = nonEmpty(model, DEFAULT_MODEL);
        this.systemPrompt = trim(systemPrompt);
    }

    public static BixbyOpenAiConfig defaults() {
        return new BixbyOpenAiConfig(false, DEFAULT_BASE_URL, "", DEFAULT_MODEL,
                "你是三星 Bixby 的自定义大模型后端，请用简洁自然的中文回答。");
    }

    public static BixbyOpenAiConfig load(Context context) {
        if (context == null) {
            return defaults();
        }
        if (MODULE_PACKAGE.equals(context.getPackageName())) {
            return loadLocal(context);
        }
        try {
            Bundle bundle = context.getContentResolver().call(URI, METHOD_GET, null, null);
            if (bundle != null) {
                return fromBundle(bundle);
            }
        } catch (Throwable ignored) {
            // Fall through to defaults. Hooks must never crash the target app.
        }
        return defaults();
    }

    public static BixbyOpenAiConfig loadLocal(Context context) {
        if (context == null) {
            return defaults();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        BixbyOpenAiConfig defaults = defaults();
        return new BixbyOpenAiConfig(
                prefs.getBoolean(EXTRA_ENABLED, defaults.enabled),
                prefs.getString(EXTRA_BASE_URL, defaults.baseUrl),
                prefs.getString(EXTRA_API_KEY, defaults.apiKey),
                prefs.getString(EXTRA_MODEL, defaults.model),
                prefs.getString(EXTRA_SYSTEM_PROMPT, defaults.systemPrompt)
        );
    }

    public static void saveLocal(Context context, BixbyOpenAiConfig config) {
        if (context == null || config == null) {
            return;
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(EXTRA_ENABLED, config.enabled)
                .putString(EXTRA_BASE_URL, config.baseUrl)
                .putString(EXTRA_API_KEY, config.apiKey)
                .putString(EXTRA_MODEL, config.model)
                .putString(EXTRA_SYSTEM_PROMPT, config.systemPrompt)
                .apply();
        context.getContentResolver().notifyChange(URI, null);
    }

    public static BixbyOpenAiConfig fromBundle(Bundle bundle) {
        BixbyOpenAiConfig defaults = defaults();
        if (bundle == null) {
            return defaults;
        }
        return new BixbyOpenAiConfig(
                bundle.getBoolean(EXTRA_ENABLED, defaults.enabled),
                bundle.getString(EXTRA_BASE_URL, defaults.baseUrl),
                bundle.getString(EXTRA_API_KEY, defaults.apiKey),
                bundle.getString(EXTRA_MODEL, defaults.model),
                bundle.getString(EXTRA_SYSTEM_PROMPT, defaults.systemPrompt)
        );
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_ENABLED, enabled);
        bundle.putString(EXTRA_BASE_URL, baseUrl);
        bundle.putString(EXTRA_API_KEY, apiKey);
        bundle.putString(EXTRA_MODEL, model);
        bundle.putString(EXTRA_SYSTEM_PROMPT, systemPrompt);
        bundle.putString("endpoint", chatCompletionsEndpoint());
        return bundle;
    }

    public String chatCompletionsEndpoint() {
        return nonEmpty(baseUrl, DEFAULT_BASE_URL);
    }

    public String summary() {
        return "{enabled=" + enabled
                + ", endpoint=" + chatCompletionsEndpoint()
                + ", model=" + model
                + ", hasKey=" + (apiKey.length() > 0)
                + ", promptLen=" + systemPrompt.length()
                + "}";
    }

    static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nonEmpty(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed.length() == 0 ? fallback : trimmed;
    }
}
