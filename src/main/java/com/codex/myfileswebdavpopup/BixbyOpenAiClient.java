package com.codex.myfileswebdavpopup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public final class BixbyOpenAiClient {
    private static final Object HISTORY_LOCK = new Object();
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_HISTORY_CHARS = 12000;
    private static final ArrayList<HistoryMessage> HISTORY = new ArrayList<HistoryMessage>();
    private static String activeHistoryKey = "";

    public interface StreamCallback {
        void onDelta(String delta);
    }

    private static final class HistoryMessage {
        final String role;
        final String content;

        HistoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private BixbyOpenAiClient() {
    }

    public static String chat(BixbyOpenAiConfig config, String userText) throws Exception {
        return chatInternal(config, userText, false, null);
    }

    public static String chatStreaming(BixbyOpenAiConfig config, String userText,
            StreamCallback callback) throws Exception {
        return chatInternal(config, userText, true, callback);
    }

    private static String chatInternal(BixbyOpenAiConfig config, String userText,
            boolean stream, StreamCallback callback) throws Exception {
        if (config == null) {
            config = BixbyOpenAiConfig.defaults();
        }
        if (config.apiKey.length() == 0) {
            throw new IllegalStateException("API Key 不能为空");
        }
        if (isClearHistoryCommand(userText)) {
            clearHistory(config, "user-command");
            return "上下文已清空";
        }

        JSONObject body = new JSONObject();
        body.put("model", config.model);
        body.put("stream", stream);
        body.put("temperature", 0.7d);

        String historyKey = historyKey(config);
        JSONArray messages = buildMessages(config, historyKey);
        messages.put(message("user", userText));
        body.put("messages", messages);
        log("BixbyOpenAi: request with context messages=" + messages.length()
                + ", historyMessages=" + historySize(historyKey));

        byte[] requestBody = body.toString().getBytes("UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(config.chatCompletionsEndpoint()).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", stream ? "text/event-stream" : "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);

        OutputStream output = connection.getOutputStream();
        try {
            output.write(requestBody);
            output.flush();
        } finally {
            output.close();
        }

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            String response = readString(connection.getErrorStream());
            throw new IllegalStateException("HTTP " + code + ": " + clip(response, 800));
        }

        if (stream) {
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("text/event-stream")) {
                String reply = readEventStream(connection.getInputStream(), callback);
                rememberTurn(historyKey, userText, reply);
                return reply;
            }
        }
        String response = readString(connection.getInputStream());
        JSONObject json = new JSONObject(response);
        String reply = parseChatResponse(json, response);
        rememberTurn(historyKey, userText, reply);
        return reply;
    }

    public static void clearHistory(BixbyOpenAiConfig config, String reason) {
        synchronized (HISTORY_LOCK) {
            HISTORY.clear();
            activeHistoryKey = config != null ? historyKey(config) : "";
        }
        log("BixbyOpenAi: context history cleared, reason=" + reason);
    }

    public static void rememberExternalMessage(BixbyOpenAiConfig config, String role,
            String content, String reason) {
        if (config == null) {
            config = BixbyOpenAiConfig.defaults();
        }
        String safeRole = normalizeRole(role);
        String safeContent = sanitizeHistoryContent(content);
        if (safeContent.length() == 0) {
            return;
        }
        synchronized (HISTORY_LOCK) {
            ensureHistoryKeyLocked(historyKey(config));
            if (containsRecentHistoryLocked(safeRole, safeContent, 8)) {
                return;
            }
            HISTORY.add(new HistoryMessage(safeRole, safeContent));
            trimHistoryLocked();
            log("BixbyOpenAi: external context remembered, role=" + safeRole
                    + ", reason=" + reason
                    + ", messages=" + HISTORY.size()
                    + ", chars=" + historyCharsLocked()
                    + ", text=" + clip(safeContent, 180));
        }
    }

    private static JSONArray buildMessages(BixbyOpenAiConfig config, String historyKey) throws Exception {
        JSONArray messages = new JSONArray();
        if (config.systemPrompt.length() > 0) {
            messages.put(message("system", config.systemPrompt));
        }
        synchronized (HISTORY_LOCK) {
            ensureHistoryKeyLocked(historyKey);
            trimHistoryLocked();
            for (int i = 0; i < HISTORY.size(); i++) {
                HistoryMessage item = HISTORY.get(i);
                if (item.content != null && item.content.length() > 0) {
                    messages.put(message(item.role, item.content));
                }
            }
        }
        return messages;
    }

    private static void rememberTurn(String historyKey, String userText, String reply) {
        if (userText == null || userText.length() == 0 || reply == null || reply.length() == 0) {
            return;
        }
        synchronized (HISTORY_LOCK) {
            ensureHistoryKeyLocked(historyKey);
            addHistoryMessageLocked("user", userText);
            addHistoryMessageLocked("assistant", reply);
            trimHistoryLocked();
            log("BixbyOpenAi: context history updated, messages=" + HISTORY.size()
                    + ", chars=" + historyCharsLocked());
        }
    }

    private static void addHistoryMessageLocked(String role, String content) {
        String safeContent = sanitizeHistoryContent(content);
        if (safeContent.length() == 0) {
            return;
        }
        String safeRole = normalizeRole(role);
        if (containsRecentHistoryLocked(safeRole, safeContent, 2)) {
            return;
        }
        HISTORY.add(new HistoryMessage(safeRole, safeContent));
    }

    private static boolean containsRecentHistoryLocked(String role, String content, int maxLookback) {
        if (role == null || content == null) {
            return false;
        }
        int from = Math.max(0, HISTORY.size() - Math.max(1, maxLookback));
        for (int i = HISTORY.size() - 1; i >= from; i--) {
            HistoryMessage item = HISTORY.get(i);
            if (item != null && role.equals(item.role) && content.equals(item.content)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeRole(String role) {
        if ("system".equals(role) || "user".equals(role) || "assistant".equals(role)) {
            return role;
        }
        return "assistant";
    }

    private static String sanitizeHistoryContent(String content) {
        if (content == null) {
            return "";
        }
        return content.trim();
    }

    private static int historySize(String historyKey) {
        synchronized (HISTORY_LOCK) {
            ensureHistoryKeyLocked(historyKey);
            return HISTORY.size();
        }
    }

    private static void ensureHistoryKeyLocked(String historyKey) {
        String safeKey = historyKey == null ? "" : historyKey;
        if (!safeKey.equals(activeHistoryKey)) {
            HISTORY.clear();
            activeHistoryKey = safeKey;
            log("BixbyOpenAi: context history reset for config change");
        }
    }

    private static void trimHistoryLocked() {
        while (HISTORY.size() > MAX_HISTORY_MESSAGES) {
            HISTORY.remove(0);
        }
        while (historyCharsLocked() > MAX_HISTORY_CHARS && HISTORY.size() > 2) {
            HISTORY.remove(0);
        }
    }

    private static int historyCharsLocked() {
        int total = 0;
        for (int i = 0; i < HISTORY.size(); i++) {
            String content = HISTORY.get(i).content;
            if (content != null) {
                total += content.length();
            }
        }
        return total;
    }

    private static String historyKey(BixbyOpenAiConfig config) {
        if (config == null) {
            return "";
        }
        return config.chatCompletionsEndpoint() + "\n" + config.model + "\n" + config.systemPrompt;
    }

    private static boolean isClearHistoryCommand(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.toLowerCase()
                .replace(" ", "")
                .replace("\n", "")
                .replace("\t", "");
        return "清空上下文".equals(normalized)
                || "清除上下文".equals(normalized)
                || "重置上下文".equals(normalized)
                || "clearcontext".equals(normalized)
                || "resetchat".equals(normalized);
    }

    private static String parseChatResponse(JSONObject json, String response) {
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return clip(response, 1200);
        }
        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            return clip(response, 1200);
        }
        JSONObject message = choice.optJSONObject("message");
        if (message != null) {
            String content = jsonText(message, "content");
            if (content.length() > 0) {
                return content;
            }
        }
        JSONObject delta = choice.optJSONObject("delta");
        if (delta != null) {
            String content = jsonText(delta, "content");
            if (content.length() > 0) {
                return content;
            }
        }
        return clip(response, 1200);
    }

    private static JSONObject message(String role, String content) throws Exception {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String readEventStream(InputStream input, StreamCallback callback) throws Exception {
        if (input == null) {
            return "";
        }
        StringBuilder full = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || !line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) {
                    break;
                }
                JSONObject json = new JSONObject(data);
                JSONArray choices = json.optJSONArray("choices");
                if (choices == null || choices.length() == 0) {
                    continue;
                }
                JSONObject choice = choices.optJSONObject(0);
                if (choice == null) {
                    continue;
                }
                JSONObject delta = choice.optJSONObject("delta");
                String content = delta == null ? "" : jsonText(delta, "content");
                if (content.length() == 0) {
                    JSONObject message = choice.optJSONObject("message");
                    content = message == null ? "" : jsonText(message, "content");
                }
                if (content.length() == 0) {
                    continue;
                }
                full.append(content);
                if (callback != null) {
                    callback.onDelta(content);
                }
            }
        } finally {
            reader.close();
        }
        return full.toString();
    }

    private static String jsonText(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key) || object.isNull(key)) {
            return "";
        }
        String value = object.optString(key, "");
        return "null".equals(value) ? "" : value;
    }

    private static String readString(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        input.close();
        return new String(output.toByteArray(), "UTF-8");
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

    private static void log(String message) {
        try {
            DiagnosticLogger.log(message);
        } catch (Throwable ignored) {
            // Keep chat requests independent from diagnostics.
        }
    }
}
