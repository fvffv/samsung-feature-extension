package com.codex.myfileswebdavpopup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

final class WebDavStore {
    private static final String PREF_NAME = "myfiles_webdav_servers";
    private static final String KEY_IDS = "ids";
    private static final String KEY_NEXT_ID = "next_id";
    private static final String KEY_SERVER_PREFIX = "server.";

    private static volatile WebDavStore instance;

    private final SharedPreferences preferences;

    private WebDavStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    static WebDavStore get(Context context) {
        if (instance == null) {
            synchronized (WebDavStore.class) {
                if (instance == null) {
                    instance = new WebDavStore(context);
                }
            }
        }
        return instance;
    }

    synchronized long nextId() {
        long next = preferences.getLong(KEY_NEXT_ID, System.currentTimeMillis());
        preferences.edit().putLong(KEY_NEXT_ID, next + 1L).apply();
        return next;
    }

    synchronized void save(ServerConfig config) throws Exception {
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_IDS, new HashSet<String>()));
        ids.add(String.valueOf(config.id));
        preferences.edit()
                .putStringSet(KEY_IDS, ids)
                .putString(KEY_SERVER_PREFIX + config.id, config.toJson().toString())
                .commit();
    }

    synchronized ServerConfig get(long id) {
        if (id <= 0) {
            return null;
        }
        String json = preferences.getString(KEY_SERVER_PREFIX + id, null);
        if (json == null) {
            return null;
        }
        try {
            return ServerConfig.fromJson(new JSONObject(json));
        } catch (Exception ignored) {
            return null;
        }
    }

    synchronized void delete(long id) {
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_IDS, new HashSet<String>()));
        ids.remove(String.valueOf(id));
        preferences.edit()
                .putStringSet(KEY_IDS, ids)
                .remove(KEY_SERVER_PREFIX + id)
                .commit();
    }

    synchronized ArrayList<Bundle> listBundles() {
        ArrayList<Bundle> result = new ArrayList<>();
        Set<String> ids = preferences.getStringSet(KEY_IDS, new HashSet<String>());
        for (String rawId : ids) {
            try {
                ServerConfig config = get(Long.parseLong(rawId));
                if (config != null) {
                    result.add(config.toBundle());
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed legacy entries.
            }
        }
        return result;
    }

    static final class ServerConfig {
        long id;
        String name;
        String scheme;
        String host;
        int port;
        String basePath;
        String username;
        String password;
        boolean anonymous;
        long addedTime;

        static ServerConfig fromBundle(Bundle bundle) {
            ServerConfig config = new ServerConfig();
            if (bundle != null) {
                config.id = bundle.getLong(WebDavBackend.KEY_SERVER_ID, -1L);
                config.name = bundle.getString(WebDavBackend.KEY_SERVER_NAME, "");
                config.port = bundle.getInt(WebDavBackend.KEY_SERVER_PORT, 443);
                config.username = bundle.getString(WebDavBackend.KEY_ACCOUNT_NAME, "");
                config.password = bundle.getString(WebDavBackend.KEY_ACCOUNT_PASSWORD, "");
                config.anonymous = bundle.getBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, false);
                config.addedTime = bundle.getLong(WebDavBackend.KEY_SERVER_ADDED_TIME, System.currentTimeMillis());
                parseAddress(config,
                        bundle.getString(WebDavBackend.KEY_SERVER_ADDRESS, ""),
                        bundle.getString(WebDavBackend.KEY_SHARED_FOLDER, ""));
            }
            if (isEmpty(config.scheme)) {
                config.scheme = "https";
            }
            if (config.port <= 0) {
                config.port = "http".equals(config.scheme) ? 80 : 443;
            }
            if (isEmpty(config.basePath)) {
                config.basePath = "/";
            }
            normalizeSchemeForPort(config);
            if (isEmpty(config.name)) {
                config.name = config.host;
            }
            return config;
        }

        static ServerConfig fromJson(JSONObject json) throws Exception {
            ServerConfig config = new ServerConfig();
            config.id = json.optLong("id", -1L);
            config.name = json.optString("name", "");
            config.scheme = json.optString("scheme", "https");
            config.host = json.optString("host", "");
            config.port = json.optInt("port", "http".equals(config.scheme) ? 80 : 443);
            config.basePath = normalizePath(json.optString("basePath", "/"));
            config.username = json.optString("username", "");
            config.password = json.optString("password", "");
            config.anonymous = json.optBoolean("anonymous", false);
            config.addedTime = json.optLong("addedTime", System.currentTimeMillis());
            normalizeSchemeForPort(config);
            return config;
        }

        JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("name", nullToEmpty(name));
            json.put("scheme", nullToEmpty(scheme));
            json.put("host", nullToEmpty(host));
            json.put("port", port);
            json.put("basePath", normalizePath(basePath));
            json.put("username", nullToEmpty(username));
            json.put("password", nullToEmpty(password));
            json.put("anonymous", anonymous);
            json.put("addedTime", addedTime);
            return json;
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putLong(WebDavBackend.KEY_SERVER_ID, id);
            bundle.putString(WebDavBackend.KEY_SERVER_NAME, nullToEmpty(name));
            bundle.putString(WebDavBackend.KEY_SERVER_ADDRESS, nullToEmpty(host));
            bundle.putInt(WebDavBackend.KEY_SERVER_PORT, port);
            bundle.putBoolean(WebDavBackend.KEY_IS_ANONYMOUS_MODE, anonymous);
            bundle.putString(WebDavBackend.KEY_ACCOUNT_NAME, nullToEmpty(username));
            bundle.putString(WebDavBackend.KEY_ACCOUNT_PASSWORD, nullToEmpty(password));
            bundle.putString(WebDavBackend.KEY_SHARED_FOLDER, pathForUi(basePath));
            bundle.putLong(WebDavBackend.KEY_SERVER_ADDED_TIME, addedTime);
            bundle.putString(WebDavBackend.KEY_ENCODING_TYPE, "UTF-8");
            bundle.putString("securityMode", "WebDAV");
            return bundle;
        }

        String rootUrl() {
            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://").append(host);
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                builder.append(':').append(port);
            }
            return builder.toString();
        }

        private static void parseAddress(ServerConfig config, String rawAddress, String rawShared) {
            String address = nullToEmpty(rawAddress).trim();
            String shared = nullToEmpty(rawShared).trim();

            if (("http:".equalsIgnoreCase(address) || "https:".equalsIgnoreCase(address)) && shared.length() > 0) {
                address = address + "/" + shared;
                shared = "";
            }

            if (address.startsWith("http://") || address.startsWith("https://")) {
                Uri uri = Uri.parse(address);
                config.scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
                config.host = uri.getHost() != null ? uri.getHost() : "";
                int uriPort = uri.getPort();
                if (uriPort > 0) {
                    config.port = uriPort;
                }
                String path = uri.getEncodedPath();
                if (!isEmpty(shared)) {
                    path = joinPath(path, shared);
                }
                config.basePath = normalizePath(path);
                return;
            }

            config.scheme = "https";
            String host = address;
            String path = shared;
            int slash = address.indexOf('/');
            if (slash >= 0) {
                host = address.substring(0, slash);
                path = joinPath(address.substring(slash), shared);
            }
            int colon = host.lastIndexOf(':');
            if (colon > 0 && colon < host.length() - 1) {
                try {
                    config.port = Integer.parseInt(host.substring(colon + 1));
                    host = host.substring(0, colon);
                } catch (NumberFormatException ignored) {
                    // Keep original host if this was not a host:port form.
                }
            }
            config.host = host;
            config.basePath = normalizePath(path);
        }

        private static void normalizeSchemeForPort(ServerConfig config) {
            if ("http".equals(config.scheme) && (config.port == 443 || config.port == 8443)) {
                config.scheme = "https";
            }
        }

        private static String pathForUi(String path) {
            String normalized = normalizePath(path);
            if ("/".equals(normalized)) {
                return "";
            }
            return normalized.startsWith("/") ? normalized.substring(1) : normalized;
        }
    }

    private static String normalizePath(String path) {
        if (isEmpty(path)) {
            return "/";
        }
        String value = path.trim();
        if (isEmpty(value)) {
            return "/";
        }
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        while (value.contains("//")) {
            value = value.replace("//", "/");
        }
        return value;
    }

    private static String joinPath(String left, String right) {
        String a = normalizePath(left);
        String b = nullToEmpty(right);
        if (isEmpty(b)) {
            return a;
        }
        if (b.startsWith("/")) {
            b = b.substring(1);
        }
        if ("/".equals(a)) {
            return "/" + b;
        }
        return a + "/" + b;
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
