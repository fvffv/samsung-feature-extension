package com.samsung.feature.extension;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class WebDavBackend {
    static final String KEY_REQ_CODE = "reqCode";

    static final String KEY_ERR_INFO = "errInfo";
    static final String KEY_ERR_CODE = "errCode";
    static final String KEY_ERR_MSG = "errMsg";
    static final String KEY_IS_SUCCESS = "isSuccess";
    static final String KEY_IS_VALID_REQUEST = "isValidRequest";
    static final String KEY_RESULT = "result";

    static final String KEY_SERVER_LIST = "serverList";
    static final String KEY_SERVER_ID = "serverId";
    static final String KEY_SERVER_NAME = "serverName";
    static final String KEY_SERVER_ADDRESS = "serverAddr";
    static final String KEY_SERVER_PORT = "serverPort";
    static final String KEY_SRC_SERVER_ID = "srcServerId";
    static final String KEY_DST_SERVER_ID = "dstServerId";
    static final String KEY_ACCOUNT_NAME = "accountName";
    static final String KEY_ACCOUNT_PASSWORD = "accountPassword";
    static final String KEY_IS_ANONYMOUS_MODE = "isAnonymousMode";
    static final String KEY_SHARED_FOLDER = "sharedFolder";
    static final String KEY_SERVER_ADDED_TIME = "serverAddedTime";
    static final String KEY_ENCODING_TYPE = "encodingType";

    static final String KEY_FILE_LIST = "fileList";
    static final String KEY_TOTAL_PAGE_COUNT = "totalPageCount";
    static final String KEY_RESPONSE_PAGE_NUMBER = "responsePageNumber";
    static final String KEY_REQUEST_PAGE_NUMBER = "requestPageNumber";
    static final String KEY_FILE_DESCRIPTOR = "fileDescriptor";
    static final String KEY_FILE_OBJECT = "fileObject";
    static final String KEY_FILE_PATH = "filePath";
    static final String KEY_FILE_NAME = "fileName";
    static final String KEY_FILE_SIZE = "fileSize";
    static final String KEY_FILE_DATE = "fileDate";
    static final String KEY_IS_DIRECTORY = "isDirectory";
    static final String KEY_MIME_TYPE = "mimeType";

    static final String KEY_SOURCE_PATH = "sourcePath";
    static final String KEY_PARENT_PATH = "parentPath";
    static final String KEY_NEW_NAME = "newName";
    static final String KEY_DESTINATION_FOLDER_PATH = "dstFolderPath";
    static final String KEY_DESTINATION_FILE_NAME = "dstFileName";
    static final String KEY_OPERATION_FILE_SIZE = "operationFileSize";

    private static final int REQ_GET_SERVER_LIST = 1;
    private static final int REQ_ADD_SERVER = 2;
    private static final int REQ_UPDATE_SERVER = 4;
    private static final int REQ_DELETE_SERVER = 6;
    private static final int REQ_GET_FILE_LIST = 9;
    private static final int REQ_GET_FILE_OBJECT = 10;
    private static final int REQ_VERIFY_SERVER_INFO = 13;
    private static final int REQ_REMOVE_CACHED_FILE_LIST = 17;
    private static final int REQ_OPEN_STREAM_VIDEO = 18;
    private static final int REQ_GET_SERVER_INFO_BY_ID = 19;
    private static final int REQ_CREATE_FOLDER = 121;
    private static final int REQ_RENAME = 122;
    private static final int REQ_UPLOAD = 123;
    private static final int REQ_GET_FILE_DESCRIPTOR = 124;
    private static final int REQ_DELETE = 125;
    private static final int REQ_INTERNAL_COPY = 126;
    private static final int REQ_INTERNAL_MOVE = 127;
    private static final int REQ_EXIST = 130;

    private static final AtomicLong REQUEST_IDS = new AtomicLong(System.currentTimeMillis());
    private static volatile Object cachedUploadProgressCallback;
    private static volatile ProgressBridge cachedUploadProgressBridge;
    private static volatile boolean uploadProgressProbeLogged;
    private static volatile boolean uploadProgressSuccessLogged;
    private static volatile boolean uploadProgressFailureLogged;
    private static volatile Context appContext;
    private static volatile Activity currentManageActivity;

    private WebDavBackend() {
    }

    static void init(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
            DiagnosticLogger.init(appContext);
        }
    }

    static void setCurrentManageActivity(Activity activity) {
        if (activity != null) {
            currentManageActivity = activity;
            init(activity);
            DiagnosticLogger.log("current WebDAV manage activity set: " + activity.getClass().getName());
        }
    }

    static void clearCurrentManageActivity(Activity activity) {
        if (activity != null && currentManageActivity == activity) {
            currentManageActivity = null;
            DiagnosticLogger.log("current WebDAV manage activity cleared");
        }
    }

    static long handleAsync(final int reqCode, final Bundle args, final Object callback) {
        final long requestId = REQUEST_IDS.incrementAndGet();
        DiagnosticLogger.log("async request start id=" + requestId
                + ", reqCode=" + reqName(reqCode)
                + ", args=" + describeBundle(args));
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Bundle result = handleSync(reqCode, args);
                DiagnosticLogger.log("async request finish id=" + requestId
                        + ", reqCode=" + reqName(reqCode)
                        + ", success=" + (result != null && result.getBoolean(KEY_IS_SUCCESS))
                        + ", result=" + describeBundle(result));
                dispatchCallback(callback, reqCode, result);
            }
        }, "MyFiles-WebDAV-" + reqCode);
        worker.setDaemon(true);
        worker.start();
        return requestId;
    }

    static Bundle handleSync(int reqCode, Bundle args) {
        return handleSync(reqCode, args, null);
    }

    static Bundle handleSync(int reqCode, Bundle args, final Object progressCallback) {
        try {
            args = normalizeArgs(args);
            DiagnosticLogger.log("handleSync enter reqCode=" + reqName(reqCode));
            switch (reqCode) {
                case REQ_GET_SERVER_LIST:
                    return getServerList();
                case REQ_ADD_SERVER:
                    return addServer(args);
                case REQ_UPDATE_SERVER:
                    return updateServer(args);
                case REQ_DELETE_SERVER:
                    return deleteServer(args);
                case REQ_GET_FILE_LIST:
                    return getFileList(args);
                case REQ_GET_FILE_OBJECT:
                    return getFileObject(args);
                case REQ_VERIFY_SERVER_INFO:
                    return verifyServer(args);
                case REQ_REMOVE_CACHED_FILE_LIST:
                    return success();
                case REQ_OPEN_STREAM_VIDEO:
                    return softFalse();
                case REQ_GET_SERVER_INFO_BY_ID:
                    return getServerInfo(args);
                case REQ_CREATE_FOLDER:
                    return createFolder(args);
                case REQ_RENAME:
                    return rename(args);
                case REQ_UPLOAD:
                    return upload(args, progressCallback);
                case REQ_GET_FILE_DESCRIPTOR:
                    return getFileDescriptor(args);
                case REQ_DELETE:
                    return deleteFile(args);
                case REQ_INTERNAL_COPY:
                    return copyOrMove(args, false);
                case REQ_INTERNAL_MOVE:
                    return copyOrMove(args, true);
                case REQ_EXIST:
                    return exists(args);
                default:
                    DiagnosticLogger.log("unsupported reqCode " + reqCode);
                    return failure(7, "Unsupported WebDAV request: " + reqCode);
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("request failed, reqCode=" + reqName(reqCode)
                    + ", errorType=" + t.getClass().getName()
                    + ", message=" + t.getMessage());
            DiagnosticLogger.log(t);
            return failure(errorCodeFor(t), t.getMessage());
        }
    }

    static ArrayList<Bundle> getServerBundles() {
        return store().listBundles();
    }

    private static Bundle getServerList() {
        Bundle bundle = success();
        bundle.putParcelableArrayList(KEY_SERVER_LIST, store().listBundles());
        return bundle;
    }

    private static Bundle getServerInfo(Bundle args) {
        WebDavStore.ServerConfig config = store().get(serverId(args));
        if (config == null) {
            return failure(7, "WebDAV server not found");
        }
        Bundle result = success();
        result.putAll(config.toBundle());
        return result;
    }

    private static Bundle verifyServer(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = store().get(serverId(args));
        if (config == null) {
            config = WebDavStore.ServerConfig.fromBundle(args);
        }
        DiagnosticLogger.log("verify config " + describeConfig(config));
        WebDavClient client = new WebDavClient(config);
        client.stat("/");
        return success();
    }

    private static Bundle addServer(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = WebDavStore.ServerConfig.fromBundle(args);
        DiagnosticLogger.log("addServer config " + describeConfig(config));
        if (config.id <= 0) {
            config.id = store().nextId();
        }
        if (config.addedTime <= 0) {
            config.addedTime = System.currentTimeMillis();
        }
        if (isEmpty(config.name)) {
            config.name = config.host;
        }
        store().save(config);

        Bundle result = success();
        result.putLong(KEY_SERVER_ID, config.id);
        result.putAll(config.toBundle());
        return result;
    }

    private static Bundle updateServer(Bundle args) throws Exception {
        long id = serverId(args);
        WebDavStore.ServerConfig old = store().get(id);
        if (old == null) {
            return failure(7, "WebDAV server not found");
        }

        if (args != null && args.containsKey(KEY_SERVER_ADDRESS)) {
            WebDavStore.ServerConfig next = WebDavStore.ServerConfig.fromBundle(args);
            next.id = id;
            if (next.addedTime <= 0) {
                next.addedTime = old.addedTime;
            }
            store().save(next);
        } else if (args != null && args.containsKey(KEY_SERVER_NAME)) {
            old.name = args.getString(KEY_SERVER_NAME, old.name);
            store().save(old);
        }
        return success();
    }

    private static Bundle deleteServer(Bundle args) {
        store().delete(serverId(args));
        return success();
    }

    private static Bundle getFileList(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        WebDavClient client = new WebDavClient(config);
        String path = stringArg(args, "/", KEY_FILE_PATH, "path", KEY_SOURCE_PATH);
        ArrayList<WebDavClient.Item> items = client.list(path);

        ArrayList<Bundle> fileBundles = new ArrayList<>();
        for (WebDavClient.Item item : items) {
            fileBundles.add(toFileBundle(config.id, item));
        }

        Bundle result = success();
        result.putParcelableArrayList(KEY_FILE_LIST, fileBundles);
        result.putInt(KEY_TOTAL_PAGE_COUNT, 0);
        result.putInt(KEY_RESPONSE_PAGE_NUMBER, args != null ? args.getInt(KEY_REQUEST_PAGE_NUMBER, 0) : 0);
        return result;
    }

    private static Bundle getFileObject(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        WebDavClient.Item item = new WebDavClient(config).stat(
                stringArg(args, "/", KEY_FILE_PATH, KEY_SOURCE_PATH, "path"));
        Bundle result = success();
        result.putBundle(KEY_FILE_OBJECT, toFileBundle(config.id, item));
        result.putAll(toFileBundle(config.id, item));
        return result;
    }

    private static Bundle createFolder(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String parent = stringArg(args, "/", KEY_PARENT_PATH,
                KEY_DESTINATION_FOLDER_PATH, KEY_FILE_PATH, KEY_SOURCE_PATH, "path");
        String name = stringArg(args, "", KEY_NEW_NAME, KEY_FILE_NAME);
        if (isEmpty(name)) {
            return failure(7, "Folder name is empty");
        }
        String target = WebDavClient.childPath(parent, name);
        DiagnosticLogger.log("MKCOL path=" + target);
        new WebDavClient(config).mkcol(target);
        return success();
    }

    private static Bundle rename(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String source = stringArg(args, "/", KEY_SOURCE_PATH, KEY_FILE_PATH, "path");
        String newName = stringArg(args, "", KEY_NEW_NAME, KEY_FILE_NAME);
        if (isEmpty(newName)) {
            return failure(7, "New name is empty");
        }
        String destination = WebDavClient.childPath(WebDavClient.parentPath(source), newName);
        DiagnosticLogger.log("RENAME source=" + source + ", destination=" + destination);
        new WebDavClient(config).move(source, destination);
        return success();
    }

    private static Bundle upload(Bundle args, final Object progressCallback) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String folder = stringArg(args, "/", KEY_DESTINATION_FOLDER_PATH, KEY_PARENT_PATH, "path");
        String name = stringArg(args, "", KEY_DESTINATION_FILE_NAME, KEY_FILE_NAME, KEY_NEW_NAME);
        if (isEmpty(name)) {
            return failure(7, "Destination name is empty");
        }
        ParcelFileDescriptor pfd = args.getParcelable(KEY_FILE_DESCRIPTOR);
        if (pfd == null) {
            return failure(7, "Missing upload descriptor");
        }
        InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        long size = args.getLong(KEY_OPERATION_FILE_SIZE, -1L);
        String target = WebDavClient.childPath(folder, name);
        DiagnosticLogger.log("UPLOAD target=" + target + ", size=" + size);
        new WebDavClient(config).put(target, input, size, new WebDavClient.ProgressListener() {
            @Override
            public void onProgress(long transferredBytes, long totalBytes) {
                dispatchUploadProgress(progressCallback, transferredBytes);
            }
        });
        return success();
    }

    private static void dispatchUploadProgress(Object progressCallback, long transferredBytes) {
        if (progressCallback == null) {
            return;
        }
        try {
            ProgressBridge bridge = progressCallback == cachedUploadProgressCallback
                    ? cachedUploadProgressBridge
                    : null;
            if (bridge == null) {
                bridge = resolveUploadProgressBridge(progressCallback);
                cachedUploadProgressCallback = progressCallback;
                cachedUploadProgressBridge = bridge;
            }
            if (bridge == null) {
                if (!uploadProgressFailureLogged) {
                    uploadProgressFailureLogged = true;
                    DiagnosticLogger.log("UPLOAD progress bridge unresolved, callback="
                            + describeObject(progressCallback));
                    DiagnosticLogger.log("UPLOAD progress callback fields="
                            + describeObjectFields(progressCallback));
                }
                return;
            }
            bridge.dispatch(transferredBytes);
            if (!uploadProgressSuccessLogged) {
                uploadProgressSuccessLogged = true;
                DiagnosticLogger.log("UPLOAD progress bridge active, listener="
                        + describeObject(bridge.listener)
                        + ", source=" + describeObject(bridge.sourceFileInfo)
                        + ", sourcePath=" + readPathForLog(bridge.sourceFileInfo)
                        + ", method=" + bridge.method.getName());
            }
        } catch (Throwable t) {
            if (!uploadProgressFailureLogged) {
                uploadProgressFailureLogged = true;
                DiagnosticLogger.log("UPLOAD progress dispatch failed, transferred="
                        + transferredBytes + ", callback=" + describeObject(progressCallback));
                DiagnosticLogger.log(t);
            }
        }
    }

    private static ProgressBridge resolveUploadProgressBridge(Object callback) {
        ProgressBridge directBridge = resolveUploadProgressBridgeFromCallbackFields(callback);
        if (directBridge != null) {
            ArrayList<Object> candidates = new ArrayList<>();
            candidates.add(directBridge.sourceFileInfo);
            logUploadProgressProbe(callback, candidates, directBridge.listener, directBridge.sourceFileInfo);
            return directBridge;
        }

        ArrayList<Object> fileInfoCandidates = new ArrayList<>();
        collectFileInfoCandidates(callback, fileInfoCandidates, 2);
        Object sourceFileInfo = chooseUploadSourceFileInfo(fileInfoCandidates);
        if (sourceFileInfo == null) {
            logUploadProgressProbe(callback, fileInfoCandidates, null, null);
            return null;
        }

        ArrayList<Object> listenerCandidates = collectObjectFieldValues(callback);
        Object listener = null;
        Method method = null;
        for (int i = 0; i < listenerCandidates.size(); i++) {
            Object candidate = listenerCandidates.get(i);
            if (candidate == null || candidate == sourceFileInfo || isPrimitiveLike(candidate)) {
                continue;
            }
            method = findProgressMethod(candidate.getClass(), sourceFileInfo.getClass());
            if (method != null) {
                listener = candidate;
                break;
            }
        }
        if (listener == null) {
            ArrayList<Object> nested = collectNestedObjectFieldValues(callback, 2);
            for (int i = 0; i < nested.size(); i++) {
                Object candidate = nested.get(i);
                if (candidate == null || candidate == sourceFileInfo || isPrimitiveLike(candidate)) {
                    continue;
                }
                method = findProgressMethod(candidate.getClass(), sourceFileInfo.getClass());
                if (method != null) {
                    listener = candidate;
                    break;
                }
            }
        }

        logUploadProgressProbe(callback, fileInfoCandidates, listener, sourceFileInfo);
        if (listener == null || method == null) {
            return null;
        }
        return new ProgressBridge(listener, sourceFileInfo, method);
    }

    private static ProgressBridge resolveUploadProgressBridgeFromCallbackFields(Object callback) {
        if (callback == null) {
            return null;
        }
        try {
            Object mode = getFieldByNames(callback, "f441d", "d");
            if (mode instanceof Number && ((Number) mode).intValue() != 20) {
                return null;
            }
            Object listener = getFieldByNames(callback, "f442e", "e");
            Object param = getFieldByNames(callback, "f443k", "k");
            Object sourceFileInfo = getFieldByNames(param, "f969a", "a");
            if (listener == null || sourceFileInfo == null) {
                return null;
            }
            Method method = findProgressMethod(listener.getClass(), sourceFileInfo.getClass());
            if (method == null) {
                return null;
            }
            return new ProgressBridge(listener, sourceFileInfo, method);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logUploadProgressProbe(
            Object callback,
            ArrayList<Object> fileInfoCandidates,
            Object listener,
            Object sourceFileInfo
    ) {
        if (uploadProgressProbeLogged) {
            return;
        }
        uploadProgressProbeLogged = true;
        StringBuilder builder = new StringBuilder();
        builder.append("UPLOAD progress probe callback=").append(describeObject(callback));
        builder.append(", listener=").append(describeObject(listener));
        builder.append(", source=").append(describeObject(sourceFileInfo));
        builder.append(", candidates=");
        for (int i = 0; i < fileInfoCandidates.size(); i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            Object candidate = fileInfoCandidates.get(i);
            builder.append(describeObject(candidate))
                    .append(", path=")
                    .append(readPathForLog(candidate));
        }
        DiagnosticLogger.log(builder.toString());
        DiagnosticLogger.log("UPLOAD progress callback fields=" + describeObjectFields(callback));
    }

    private static Object chooseUploadSourceFileInfo(ArrayList<Object> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (int i = 0; i < candidates.size(); i++) {
            Object candidate = candidates.get(i);
            String path = readPathForLog(candidate);
            if (!isEmpty(path) && !path.contains("/Network Storage/WebDAV")) {
                return candidate;
            }
        }
        return candidates.get(0);
    }

    private static void collectFileInfoCandidates(
            Object owner,
            ArrayList<Object> out,
            int depth
    ) {
        if (owner == null || out == null || depth < 0 || isPrimitiveLike(owner)) {
            return;
        }
        if (isLikelyFileInfo(owner)) {
            addUnique(out, owner);
            return;
        }
        for (Field field : allFields(owner)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value == null || isPrimitiveLike(value)) {
                    continue;
                }
                if (isLikelyFileInfo(value)) {
                    addUnique(out, value);
                } else if (depth > 0 && isSmallObfuscatedObject(value)) {
                    collectFileInfoCandidates(value, out, depth - 1);
                }
            } catch (Throwable ignored) {
                // Keep scanning runtime fields.
            }
        }
    }

    private static boolean isLikelyFileInfo(Object object) {
        if (object == null) {
            return false;
        }
        String className = object.getClass().getName();
        if (className.startsWith("Y5.") || className.startsWith("V5.")) {
            return true;
        }
        String path = readPathForLog(object);
        if (isEmpty(path)) {
            return false;
        }
        try {
            Object domain = XposedHelpers.callMethod(object, "b0");
            if (domain instanceof Number) {
                return true;
            }
        } catch (Throwable ignored) {
            // Path-bearing file info objects may not expose b0 on every build.
        }
        return className.contains("FileInfo") || className.contains("File");
    }

    private static ArrayList<Object> collectObjectFieldValues(Object owner) {
        ArrayList<Object> values = new ArrayList<>();
        if (owner == null) {
            return values;
        }
        for (Field field : allFields(owner)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) {
                    addUnique(values, value);
                }
            } catch (Throwable ignored) {
                // Keep scanning runtime fields.
            }
        }
        return values;
    }

    private static ArrayList<Object> collectNestedObjectFieldValues(Object owner, int depth) {
        ArrayList<Object> values = new ArrayList<>();
        collectNestedObjectFieldValues(owner, depth, values);
        return values;
    }

    private static void collectNestedObjectFieldValues(Object owner, int depth, ArrayList<Object> out) {
        if (owner == null || out == null || depth < 0 || isPrimitiveLike(owner)) {
            return;
        }
        ArrayList<Object> direct = collectObjectFieldValues(owner);
        for (int i = 0; i < direct.size(); i++) {
            Object value = direct.get(i);
            if (value == null || isPrimitiveLike(value)) {
                continue;
            }
            addUnique(out, value);
            if (depth > 0 && isSmallObfuscatedObject(value)) {
                collectNestedObjectFieldValues(value, depth - 1, out);
            }
        }
    }

    private static Method findProgressMethod(Class<?> listenerClass, Class<?> fileInfoClass) {
        Class<?> current = listenerClass;
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (!isProgressMethod(method, fileInfoClass, true)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        current = listenerClass;
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (int i = 0; i < methods.length; i++) {
                Method method = methods[i];
                if (!isProgressMethod(method, fileInfoClass, false)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isProgressMethod(Method method, Class<?> fileInfoClass, boolean requireKnownName) {
        if (method == null) {
            return false;
        }
        if (requireKnownName && !"c".equals(method.getName()) && !"f".equals(method.getName())) {
            return false;
        }
        Class<?>[] types = method.getParameterTypes();
        if (types.length != 2) {
            return false;
        }
        if (types[1] != long.class && types[1] != Long.class) {
            return false;
        }
        return types[0].isAssignableFrom(fileInfoClass) || types[0] == Object.class;
    }

    private static void addUnique(ArrayList<Object> list, Object value) {
        if (list == null || value == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == value) {
                return;
            }
        }
        list.add(value);
    }

    private static boolean isPrimitiveLike(Object object) {
        if (object == null) {
            return true;
        }
        Class<?> cls = object.getClass();
        return cls.isPrimitive()
                || object instanceof String
                || object instanceof CharSequence
                || object instanceof Number
                || object instanceof Boolean
                || object instanceof Character
                || cls.isEnum()
                || cls.isArray()
                || cls.getName().startsWith("java.")
                || cls.getName().startsWith("android.");
    }

    private static boolean isSmallObfuscatedObject(Object object) {
        if (object == null) {
            return false;
        }
        String name = object.getClass().getName();
        if (name.startsWith("java.") || name.startsWith("android.")) {
            return false;
        }
        return allFields(object).size() <= 12;
    }

    private static String readPathForLog(Object object) {
        if (object == null) {
            return "";
        }
        try {
            Object value = XposedHelpers.callMethod(object, "getPath");
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
            // Fall through to string field scan.
        }
        for (Field field : allFields(object)) {
            try {
                if (field.getType() == String.class || CharSequence.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value instanceof CharSequence) {
                        String text = String.valueOf(value);
                        if (text.indexOf('/') >= 0 || text.indexOf('\\') >= 0) {
                            return text;
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning string fields.
            }
        }
        return "";
    }

    private static String describeObject(Object object) {
        return object == null ? "null" : object.getClass().getName();
    }

    private static String describeObjectFields(Object object) {
        if (object == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Field field : allFields(object)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (count > 0) {
                builder.append("; ");
            }
            count++;
            builder.append(field.getName()).append(':').append(field.getType().getName());
            try {
                field.setAccessible(true);
                Object value = field.get(object);
                builder.append('=').append(describeObject(value));
                String path = readPathForLog(value);
                if (!isEmpty(path)) {
                    builder.append("[path=").append(path).append(']');
                }
            } catch (Throwable t) {
                builder.append("=<").append(t.getClass().getSimpleName()).append('>');
            }
            if (count >= 20) {
                builder.append("; ...");
                break;
            }
        }
        return builder.toString();
    }

    private static final class ProgressBridge {
        final Object listener;
        final Object sourceFileInfo;
        final Method method;

        ProgressBridge(Object listener, Object sourceFileInfo, Method method) {
            this.listener = listener;
            this.sourceFileInfo = sourceFileInfo;
            this.method = method;
        }

        void dispatch(long transferredBytes) throws Exception {
            method.invoke(listener, sourceFileInfo, Long.valueOf(transferredBytes));
        }
    }

    private static Bundle getFileDescriptor(Bundle args) throws Exception {
        final WebDavStore.ServerConfig config = requireConfig(args);
        final String path = stringArg(args, "/", KEY_SOURCE_PATH, KEY_FILE_PATH, "path");
        DiagnosticLogger.log("GET descriptor path=" + path);
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        final ParcelFileDescriptor read = pipe[0];
        final ParcelFileDescriptor write = pipe[1];

        Thread streamer = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream input = null;
                OutputStream output = null;
                try {
                    input = new WebDavClient(config).get(path);
                    output = new ParcelFileDescriptor.AutoCloseOutputStream(write);
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = input.read(buffer)) != -1) {
                        output.write(buffer, 0, n);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("MyFilesWebDav: stream GET failed");
                    XposedBridge.log(t);
                } finally {
                    WebDavClient.closeQuietly(input);
                    WebDavClient.closeQuietly(output);
                }
            }
        }, "MyFiles-WebDAV-GET");
        streamer.setDaemon(true);
        streamer.start();

        Bundle result = success();
        result.putParcelable(KEY_FILE_DESCRIPTOR, read);
        return result;
    }

    private static Bundle deleteFile(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String path = stringArg(args, "/", KEY_SOURCE_PATH, KEY_FILE_PATH, "path");
        DiagnosticLogger.log("DELETE path=" + path);
        new WebDavClient(config).delete(path);
        return success();
    }

    private static Bundle copyOrMove(Bundle args, boolean move) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String source = stringArg(args, "/", KEY_SOURCE_PATH, KEY_FILE_PATH, "path");
        String folder = stringArg(args, WebDavClient.parentPath(source),
                KEY_DESTINATION_FOLDER_PATH, KEY_PARENT_PATH);
        String name = stringArg(args, WebDavClient.nameOf(source),
                KEY_DESTINATION_FILE_NAME, KEY_FILE_NAME, KEY_NEW_NAME);
        String destination = WebDavClient.childPath(folder, name);
        DiagnosticLogger.log((move ? "MOVE" : "COPY")
                + " source=" + source + ", destination=" + destination);
        WebDavClient client = new WebDavClient(config);
        if (move) {
            client.move(source, destination);
        } else {
            client.copy(source, destination);
        }
        return success();
    }

    private static Bundle exists(Bundle args) throws Exception {
        WebDavStore.ServerConfig config = requireConfig(args);
        String path = stringArg(args, "/", KEY_SOURCE_PATH, KEY_FILE_PATH, "path");
        DiagnosticLogger.log("EXIST path=" + path);
        boolean exists = new WebDavClient(config).exists(path);
        Bundle result = success();
        result.putBoolean(KEY_RESULT, exists);
        return result;
    }

    private static Bundle toFileBundle(long serverId, WebDavClient.Item item) {
        Bundle bundle = new Bundle();
        bundle.putLong(KEY_SERVER_ID, serverId);
        bundle.putString(KEY_FILE_PATH, item.path);
        bundle.putString(KEY_FILE_NAME, item.name);
        bundle.putBoolean(KEY_IS_DIRECTORY, item.directory);
        bundle.putLong(KEY_FILE_SIZE, item.size);
        bundle.putLong(KEY_FILE_DATE, item.modified);
        bundle.putString(KEY_MIME_TYPE, item.mimeType);
        return bundle;
    }

    private static WebDavStore.ServerConfig requireConfig(Bundle args) {
        WebDavStore.ServerConfig config = store().get(serverId(args));
        if (config == null) {
            throw new IllegalStateException("WebDAV server not found");
        }
        return config;
    }

    private static long serverId(Bundle args) {
        if (args == null) {
            return -1L;
        }
        long value = args.getLong(KEY_SERVER_ID, -1L);
        if (value > 0) {
            return value;
        }
        value = args.getLong(KEY_SRC_SERVER_ID, -1L);
        if (value > 0) {
            return value;
        }
        value = args.getLong(KEY_DST_SERVER_ID, -1L);
        if (value > 0) {
            return value;
        }
        String[] pathKeys = {
                KEY_FILE_PATH,
                KEY_SOURCE_PATH,
                KEY_PARENT_PATH,
                KEY_DESTINATION_FOLDER_PATH,
                "path"
        };
        for (String key : pathKeys) {
            String path = args.getString(key);
            long parsed = parseServerIdFromSyntheticPath(path);
            if (parsed > 0) {
                return parsed;
            }
        }
        return -1L;
    }

    private static String stringArg(Bundle args, String key, String fallback) {
        if (args == null) {
            return fallback;
        }
        String value = args.getString(key);
        return isEmpty(value) ? fallback : value;
    }

    private static String stringArg(Bundle args, String fallback, String... keys) {
        if (args == null || keys == null) {
            return fallback;
        }
        for (String key : keys) {
            String value = args.getString(key);
            if (!isEmpty(value)) {
                return value;
            }
        }
        return fallback;
    }

    private static Bundle normalizeArgs(Bundle args) {
        if (args == null) {
            return null;
        }
        Bundle normalized = new Bundle(args);
        long id = serverId(normalized);
        if (id > 0 && !normalized.containsKey(KEY_SERVER_ID)) {
            normalized.putLong(KEY_SERVER_ID, id);
        }
        normalizePathKey(normalized, id, KEY_FILE_PATH);
        normalizePathKey(normalized, id, KEY_SOURCE_PATH);
        normalizePathKey(normalized, id, KEY_PARENT_PATH);
        normalizePathKey(normalized, id, KEY_DESTINATION_FOLDER_PATH);
        normalizePathKey(normalized, id, "path");
        return normalized;
    }

    private static void normalizePathKey(Bundle bundle, long serverId, String key) {
        if (bundle == null || !bundle.containsKey(key)) {
            return;
        }
        String value = bundle.getString(key);
        if (value == null) {
            return;
        }
        String normalized = normalizeWebDavPathValue(value, serverId);
        if (!value.equals(normalized)) {
            DiagnosticLogger.log("backend normalized path key=" + key
                    + ", from=" + value + ", to=" + normalized);
            bundle.putString(key, normalized);
        }
    }

    private static String normalizeWebDavPathValue(String value, long serverId) {
        if (isEmpty(value)) {
            return "/";
        }
        String trimmed = value.trim();
        long effectiveServerId = serverId > 0 ? serverId : parseServerIdFromSyntheticPath(trimmed);
        if (effectiveServerId > 0) {
            String webDavBase = "/Network Storage/WebDAV/" + effectiveServerId;
            if ("/Network Storage".equals(trimmed) || "/Network Storage/WebDAV".equals(trimmed)) {
                return "/";
            }
            if (trimmed.equals(webDavBase) || trimmed.equals(webDavBase + "/")) {
                return "/";
            }
            if (trimmed.startsWith(webDavBase + "/")) {
                return normalizeRelativePath(trimmed.substring(webDavBase.length()));
            }

            String networkPrefix = "/Network Storage/";
            if (trimmed.startsWith(networkPrefix)) {
                int typeStart = networkPrefix.length();
                int typeEnd = trimmed.indexOf('/', typeStart);
                if (typeEnd > typeStart && typeEnd + 1 < trimmed.length()) {
                    int idStart = typeEnd + 1;
                    int idEnd = idStart;
                    while (idEnd < trimmed.length() && Character.isDigit(trimmed.charAt(idEnd))) {
                        idEnd++;
                    }
                    if (idEnd > idStart) {
                        try {
                            long parsedId = Long.parseLong(trimmed.substring(idStart, idEnd));
                            if (parsedId == effectiveServerId) {
                                return idEnd < trimmed.length()
                                        ? normalizeRelativePath(trimmed.substring(idEnd))
                                        : "/";
                            }
                        } catch (NumberFormatException ignored) {
                            // Fall through to plain relative normalization.
                        }
                    }
                }
            }
        }
        return normalizeRelativePath(trimmed);
    }

    private static long parseServerIdFromSyntheticPath(String path) {
        if (isEmpty(path)) {
            return -1L;
        }
        String marker = "/WebDAV/";
        int markerIndex = path.indexOf(marker);
        if (markerIndex >= 0) {
            int start = markerIndex + marker.length();
            int end = start;
            while (end < path.length() && Character.isDigit(path.charAt(end))) {
                end++;
            }
            if (end > start) {
                try {
                    return Long.parseLong(path.substring(start, end));
                } catch (NumberFormatException ignored) {
                    return -1L;
                }
            }
        }
        String networkPrefix = "/Network Storage/";
        if (path.startsWith(networkPrefix)) {
            int typeStart = networkPrefix.length();
            int typeEnd = path.indexOf('/', typeStart);
            if (typeEnd > typeStart && typeEnd + 1 < path.length()) {
                int idStart = typeEnd + 1;
                int idEnd = idStart;
                while (idEnd < path.length() && Character.isDigit(path.charAt(idEnd))) {
                    idEnd++;
                }
                if (idEnd > idStart) {
                    try {
                        return Long.parseLong(path.substring(idStart, idEnd));
                    } catch (NumberFormatException ignored) {
                        return -1L;
                    }
                }
            }
        }
        return -1L;
    }

    private static String normalizeRelativePath(String path) {
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
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Bundle success() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_IS_SUCCESS, true);
        bundle.putBoolean(KEY_IS_VALID_REQUEST, true);
        bundle.putBoolean(KEY_RESULT, true);
        return bundle;
    }

    private static Bundle softFalse() {
        Bundle bundle = success();
        bundle.putBoolean(KEY_RESULT, false);
        return bundle;
    }

    private static Bundle failure(int code, String message) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_IS_SUCCESS, false);
        bundle.putBoolean(KEY_IS_VALID_REQUEST, true);
        bundle.putBoolean(KEY_RESULT, false);
        Bundle err = new Bundle();
        err.putInt(KEY_ERR_CODE, code);
        err.putString(KEY_ERR_MSG, message != null ? message : "WebDAV request failed");
        bundle.putBundle(KEY_ERR_INFO, err);
        return bundle;
    }

    private static void dispatchCallback(final Object callback, final int reqCode, final Bundle result) {
        if (callback == null) {
            return;
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    dispatchCallback(callback, reqCode, result);
                }
            });
            return;
        }
        try {
            if (result != null && result.getBoolean(KEY_IS_SUCCESS)) {
                if (reqCode == REQ_ADD_SERVER) {
                    completeAddServer(callback, result);
                    return;
                }
                XposedHelpers.callMethod(callback, "onSuccess", reqCode, result);
            } else {
                toastFailureLogPath(result);
                Bundle err = result != null ? result.getBundle(KEY_ERR_INFO) : null;
                int code = err != null ? err.getInt(KEY_ERR_CODE, 7) : 7;
                XposedHelpers.callMethod(callback, "o", code, result);
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("dispatch callback failed");
            DiagnosticLogger.log(t);
            forceCompleteCallback(callback, reqCode, result);
        }
    }

    private static void completeAddServer(Object callback, Bundle result) {
        DiagnosticLogger.log("ADD_SERVER success handled by WebDAV fallback UI");
        stopLoading(callback);
        MyFilesWebDavHook.notifyServerListChanged();

        Activity activity = currentManageActivity;
        Context context = activity != null ? activity : appContext;
        if (context != null) {
            Toast.makeText(context, "WebDAV added", Toast.LENGTH_SHORT).show();
        }
        if (activity != null && !activity.isFinishing()) {
            try {
                activity.setResult(Activity.RESULT_OK);
            } catch (Throwable ignored) {
                // Some Samsung builds ignore setResult here; finishing is enough.
            }
            activity.finish();
        }
    }

    private static Object stopLoading(Object callback) {
        Object controller = findController(callback);
        try {
            if (controller == null && currentManageActivity != null) {
                controller = callAny(currentManageActivity, "getController", "getNsmController");
            }
            Object loading = findLoadingObservable(controller);
            setObservableBoolean(loading, false);
            DiagnosticLogger.log("loading stopped, controller="
                    + (controller != null ? controller.getClass().getName() : "null"));
        } catch (Throwable t) {
            DiagnosticLogger.log("force stop loading failed");
            DiagnosticLogger.log(t);
        }
        return controller;
    }

    private static Object findController(Object callback) {
        Object controller = getFieldByNames(callback, "f2248e", "e");
        if (controller != null) {
            return controller;
        }
        for (Field field : allFields(callback)) {
            try {
                field.setAccessible(true);
                Object value = field.get(callback);
                if (value != null && "F7.c".equals(value.getClass().getName())) {
                    return value;
                }
            } catch (Throwable ignored) {
                // Keep scanning callback fields.
            }
        }
        return null;
    }

    private static Object findLoadingObservable(Object controller) {
        Object loading = getFieldByNames(controller, "f2253w", "w");
        if (loading != null) {
            return loading;
        }
        for (Field field : allFields(controller)) {
            try {
                field.setAccessible(true);
                Object value = field.get(controller);
                if (value != null) {
                    String name = value.getClass().getName();
                    if ("androidx.lifecycle.D".equals(name) || name.contains("LiveData")) {
                        return value;
                    }
                }
            } catch (Throwable ignored) {
                // Keep scanning controller fields.
            }
        }
        return null;
    }

    private static Object getFieldByNames(Object object, String... names) {
        if (object == null) {
            return null;
        }
        for (String name : names) {
            Class<?> cls = object.getClass();
            while (cls != null) {
                try {
                    Field field = cls.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(object);
                } catch (Throwable ignored) {
                    cls = cls.getSuperclass();
                }
            }
        }
        return null;
    }

    private static Object callAny(Object object, String... methodNames) {
        if (object == null) {
            return null;
        }
        for (String methodName : methodNames) {
            try {
                return XposedHelpers.callMethod(object, methodName);
            } catch (Throwable ignored) {
                // Try the next method name.
            }
        }
        return null;
    }

    private static ArrayList<Field> allFields(Object object) {
        ArrayList<Field> fields = new ArrayList<>();
        if (object == null) {
            return fields;
        }
        Class<?> cls = object.getClass();
        while (cls != null) {
            Field[] declared = cls.getDeclaredFields();
            for (Field field : declared) {
                fields.add(field);
            }
            cls = cls.getSuperclass();
        }
        return fields;
    }

    private static void forceCompleteCallback(Object callback, int reqCode, Bundle result) {
        Object controller = null;
        try {
            controller = XposedHelpers.getObjectField(callback, "f2248e");
            Object loading = XposedHelpers.getObjectField(controller, "f2253w");
            setObservableBoolean(loading, false);
        } catch (Throwable t) {
            DiagnosticLogger.log("force stop loading failed");
            DiagnosticLogger.log(t);
        }

        if (reqCode != REQ_ADD_SERVER || result == null || !result.getBoolean(KEY_IS_SUCCESS)) {
            return;
        }

        try {
            Context context = (Context) XposedHelpers.getObjectField(controller, "f23388n");
            Toast.makeText(context, "WebDAV 已添加", Toast.LENGTH_SHORT).show();
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        } catch (Throwable t) {
            DiagnosticLogger.log("force add completion UI failed");
            DiagnosticLogger.log(t);
        }
    }

    private static void toastFailureLogPath(Bundle result) {
        Context context = appContext;
        if (context == null) {
            return;
        }
        String message = "WebDAV failed. Log: " + DiagnosticLogger.path();
        Bundle err = result != null ? result.getBundle(KEY_ERR_INFO) : null;
        if (err != null) {
            String errMsg = err.getString(KEY_ERR_MSG, "");
            if (!isEmpty(errMsg)) {
                message = "WebDAV failed: " + errMsg + "\nLog: " + DiagnosticLogger.path();
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    private static void setObservableBoolean(Object observable, boolean value) {
        if (observable == null) {
            return;
        }
        Boolean boxed = Boolean.valueOf(value);
        try {
            XposedHelpers.callMethod(observable, "k", boxed);
            return;
        } catch (Throwable ignored) {
            // Try AndroidX ObservableBoolean / LiveData variants below.
        }
        try {
            XposedHelpers.callMethod(observable, "P", boxed);
            return;
        } catch (Throwable ignored) {
            // Try the next common setter.
        }
        try {
            XposedHelpers.callMethod(observable, "setValue", boxed);
            return;
        } catch (Throwable ignored) {
            // Try asynchronous LiveData update.
        }
        XposedHelpers.callMethod(observable, "postValue", boxed);
    }

    private static int errorCodeFor(Throwable t) {
        String msg = t != null ? t.getMessage() : "";
        if (msg != null && (msg.contains("401") || msg.contains("403"))) {
            return 6;
        }
        if (msg != null && msg.contains("port")) {
            return 9;
        }
        return 10;
    }

    private static String describeConfig(WebDavStore.ServerConfig config) {
        if (config == null) {
            return "null";
        }
        return "{scheme=" + config.scheme
                + ", host=" + config.host
                + ", port=" + config.port
                + ", basePath=" + config.basePath
                + ", rootUrl=" + config.rootUrl()
                + ", anonymous=" + config.anonymous
                + ", username=" + DiagnosticLogger.mask(config.username)
                + ", hasPassword=" + (!isEmpty(config.password))
                + "}";
    }

    private static String describeBundle(Bundle bundle) {
        if (bundle == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder("{");
        for (String key : bundle.keySet()) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(key).append('=');
            if (KEY_ACCOUNT_PASSWORD.equals(key)) {
                String value = bundle.getString(key);
                builder.append(value != null && value.length() > 0 ? "***" : "");
            } else {
                Object value = bundle.get(key);
                builder.append(String.valueOf(value));
            }
        }
        builder.append('}');
        return builder.toString();
    }

    private static String reqName(int reqCode) {
        switch (reqCode) {
            case REQ_GET_SERVER_LIST:
                return "GET_SERVER_LIST(1)";
            case REQ_ADD_SERVER:
                return "ADD_SERVER(2)";
            case REQ_UPDATE_SERVER:
                return "UPDATE_SERVER(4)";
            case REQ_DELETE_SERVER:
                return "DELETE_SERVER(6)";
            case REQ_GET_FILE_LIST:
                return "GET_FILE_LIST(9)";
            case REQ_GET_FILE_OBJECT:
                return "GET_FILE_OBJECT(10)";
            case REQ_VERIFY_SERVER_INFO:
                return "VERIFY_SERVER_INFO(13)";
            case REQ_REMOVE_CACHED_FILE_LIST:
                return "REMOVE_CACHED_FILE_LIST(17)";
            case REQ_OPEN_STREAM_VIDEO:
                return "OPEN_STREAM_VIDEO(18)";
            case REQ_GET_SERVER_INFO_BY_ID:
                return "GET_SERVER_INFO_BY_ID(19)";
            case REQ_CREATE_FOLDER:
                return "CREATE_FOLDER(121)";
            case REQ_RENAME:
                return "RENAME(122)";
            case REQ_UPLOAD:
                return "UPLOAD(123)";
            case REQ_GET_FILE_DESCRIPTOR:
                return "GET_FILE_DESCRIPTOR(124)";
            case REQ_DELETE:
                return "DELETE(125)";
            case REQ_INTERNAL_COPY:
                return "INTERNAL_COPY(126)";
            case REQ_INTERNAL_MOVE:
                return "INTERNAL_MOVE(127)";
            case REQ_EXIST:
                return "EXIST(130)";
            default:
                return "UNKNOWN(" + reqCode + ")";
        }
    }

    private static WebDavStore store() {
        Context context = appContext;
        if (context == null) {
            throw new IllegalStateException("Context is not ready");
        }
        return WebDavStore.get(context);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.length() == 0;
    }
}
