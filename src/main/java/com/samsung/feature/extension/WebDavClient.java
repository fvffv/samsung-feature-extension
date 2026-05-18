package com.samsung.feature.extension;

import android.util.Base64;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.xml.parsers.DocumentBuilderFactory;

final class WebDavClient {
    interface ProgressListener {
        void onProgress(long transferredBytes, long totalBytes);
    }

    private static final String PROPFIND_XML =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                    + "<D:propfind xmlns:D=\"DAV:\">"
                    + "<D:prop>"
                    + "<D:displayname/>"
                    + "<D:getcontentlength/>"
                    + "<D:getlastmodified/>"
                    + "<D:getcontenttype/>"
                    + "<D:resourcetype/>"
                    + "</D:prop>"
                    + "</D:propfind>";

    private final WebDavStore.ServerConfig config;

    WebDavClient(WebDavStore.ServerConfig config) {
        this.config = config;
    }

    Item stat(String path) throws Exception {
        ArrayList<Item> items = propfind(path, 0);
        if (items.isEmpty()) {
            throw new IOException("WebDAV path not found: " + path);
        }
        return items.get(0);
    }

    boolean exists(String path) throws Exception {
        try {
            stat(path);
            return true;
        } catch (IOException e) {
            String message = e.getMessage();
            if (message != null && message.contains("404")) {
                return false;
            }
            throw e;
        }
    }

    ArrayList<Item> list(String path) throws Exception {
        ArrayList<Item> items = propfind(path, 1);
        String requested = normalizePath(path);
        ArrayList<Item> children = new ArrayList<>();
        for (Item item : items) {
            if (!samePath(item.path, requested)) {
                children.add(item);
            }
        }
        return children;
    }

    InputStream get(String path) throws Exception {
        HttpURLConnection connection = open("GET", path);
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("GET failed: " + code);
        }
        return new BufferedInputStream(connection.getInputStream()) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    connection.disconnect();
                }
            }
        };
    }

    void put(String path, InputStream input, long size) throws Exception {
        put(path, input, size, null);
    }

    void put(String path, InputStream input, long size, ProgressListener progressListener) throws Exception {
        HttpURLConnection connection = open("PUT", path);
        connection.setDoOutput(true);
        setRequestMethodCompat(connection, "PUT");
        if (size >= 0 && size <= Integer.MAX_VALUE) {
            connection.setFixedLengthStreamingMode((int) size);
        } else if (size > Integer.MAX_VALUE) {
            connection.setFixedLengthStreamingMode(size);
        } else {
            connection.setChunkedStreamingMode(64 * 1024);
        }
        OutputStream output = null;
        try {
            output = connection.getOutputStream();
            copy(input, output, size, progressListener);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("PUT failed: " + code);
            }
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            connection.disconnect();
        }
    }

    void mkcol(String path) throws Exception {
        RawResponse response = rawRequest("MKCOL", path, null, null);
        if (response.code != 201 && response.code != 200 && response.code != 405) {
            throw new IOException("MKCOL failed: " + response.code + shortError(response.bodyAsString()));
        }
    }

    void delete(String path) throws Exception {
        RawResponse response = rawRequest("DELETE", path, null, null);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException("DELETE failed: " + response.code + shortError(response.bodyAsString()));
        }
    }

    void move(String source, String destination) throws Exception {
        copyMove("MOVE", source, destination);
    }

    void copy(String source, String destination) throws Exception {
        copyMove("COPY", source, destination);
    }

    private void copyMove(String method, String source, String destination) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("Destination", urlFor(destination).toString());
        headers.put("Overwrite", "T");
        RawResponse response = rawRequest(method, source, headers, null);
        if (response.code < 200 || response.code >= 300) {
            throw new IOException(method + " failed: " + response.code + shortError(response.bodyAsString()));
        }
    }

    private ArrayList<Item> propfind(String path, int depth) throws Exception {
        return propfind(path, depth, true);
    }

    private ArrayList<Item> propfind(String path, int depth, boolean allowHttpsRetry) throws Exception {
        return propfind(path, depth, allowHttpsRetry, true, false);
    }

    private ArrayList<Item> propfind(String path, int depth, boolean allowHttpsRetry,
                                     boolean allowNoSlashRetry, boolean omitRootTrailingSlash) throws Exception {
        long start = System.currentTimeMillis();
        byte[] body = PROPFIND_XML.getBytes("UTF-8");
        Map<String, String> headers = new HashMap<>();
        headers.put("Depth", String.valueOf(depth));
        headers.put("Content-Type", "text/xml; charset=utf-8");
        RawResponse response = rawRequest("PROPFIND", path, headers, body, omitRootTrailingSlash);
        DiagnosticLogger.log("RAW PROPFIND response code=" + response.code
                + ", depth=" + depth
                + ", elapsedMs=" + (System.currentTimeMillis() - start)
                + ", url=" + response.url
                + ", contentType=" + response.header("content-type")
                + ", authChallenge=" + response.header("www-authenticate"));
        if (response.code != 207 && response.code != 200) {
            String error = response.bodyAsString();
            DiagnosticLogger.log("RAW PROPFIND errorBody=" + shortError(error));
            if (allowHttpsRetry && shouldRetryAsHttps(response.code, error)) {
                DiagnosticLogger.log("retrying WebDAV over HTTPS for " + config.host + ":" + config.port);
                config.scheme = "https";
                return propfind(path, depth, false, allowNoSlashRetry, omitRootTrailingSlash);
            }
            if (allowNoSlashRetry && shouldRetryWithoutRootTrailingSlash(response.code, path, omitRootTrailingSlash)) {
                DiagnosticLogger.log("retrying WebDAV root without trailing slash, basePath=" + config.basePath);
                return propfind(path, depth, false, false, true);
            }
            throw new IOException("PROPFIND failed: " + response.code + shortError(error));
        }
        return parseMultiStatus(new ByteArrayInputStream(response.body), path);
    }

    private static boolean shouldRetryAsHttps(int code, String error) {
        return code == 400
                && error != null
                && error.indexOf("plain HTTP request was sent to HTTPS port") >= 0;
    }

    private boolean shouldRetryWithoutRootTrailingSlash(int code, String path, boolean omitRootTrailingSlash) {
        return code == 404
                && !omitRootTrailingSlash
                && samePath(path, "/")
                && !samePath(config.basePath, "/");
    }

    private static String shortError(String error) {
        if (error == null || error.length() == 0) {
            return "";
        }
        String compact = error.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 160) {
            compact = compact.substring(0, 160);
        }
        return ": " + compact;
    }

    private static String readErrorBody(HttpURLConnection connection) {
        InputStream error = null;
        try {
            error = connection.getErrorStream();
            if (error == null) {
                return "";
            }
            return new String(readAll(error), "UTF-8");
        } catch (Throwable ignored) {
            return "";
        } finally {
            closeQuietly(error);
        }
    }

    private HttpURLConnection open(String method, String path) throws Exception {
        return open(method, path, false);
    }

    private HttpURLConnection open(String method, String path, boolean omitRootTrailingSlash) throws Exception {
        URL url = urlFor(path, omitRootTrailingSlash);
        DiagnosticLogger.log("HTTP open method=" + method
                + ", url=" + url
                + ", anonymous=" + config.anonymous
                + ", username=" + DiagnosticLogger.mask(config.username)
                + ", hasPassword=" + (config.password != null && config.password.length() > 0));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "SamsungMyFiles-WebDAV-LSPosed");
        connection.setRequestProperty("Accept", "*/*");
        if (!config.anonymous && config.username != null && config.username.length() > 0) {
            String token = config.username + ":" + (config.password != null ? config.password : "");
            connection.setRequestProperty("Authorization",
                    "Basic " + Base64.encodeToString(token.getBytes("UTF-8"), Base64.NO_WRAP));
        }
        setRequestMethodCompat(connection, method);
        return connection;
    }

    private static String header(HttpURLConnection connection, String name) {
        try {
            String value = connection.getHeaderField(name);
            if (value != null) {
                return value;
            }
            Map<String, java.util.List<String>> fields = connection.getHeaderFields();
            if (fields == null) {
                return "";
            }
            for (String key : fields.keySet()) {
                if (key != null && key.equalsIgnoreCase(name)) {
                    java.util.List<String> values = fields.get(key);
                    return values != null ? values.toString() : "";
                }
            }
        } catch (Throwable ignored) {
            // Ignore header inspection failures.
        }
        return "";
    }

    private URL urlFor(String relativePath) throws Exception {
        return urlFor(relativePath, false);
    }

    private URL urlFor(String relativePath, boolean omitRootTrailingSlash) throws Exception {
        String path = joinPath(config.basePath, relativePath);
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (segment.length() == 0) {
                continue;
            }
            encoded.append('/').append(encodeSegment(segment));
        }
        if (path.endsWith("/") && encoded.length() > 1 && encoded.charAt(encoded.length() - 1) != '/') {
            encoded.append('/');
        }
        if (!omitRootTrailingSlash && samePath(relativePath, "/") && encoded.length() > 1
                && encoded.charAt(encoded.length() - 1) != '/') {
            encoded.append('/');
        }
        if (encoded.length() == 0) {
            encoded.append('/');
        }
        return new URL(config.rootUrl() + encoded);
    }

    private RawResponse rawRequest(String method, String path,
                                   Map<String, String> headers, byte[] body) throws Exception {
        return rawRequest(method, path, headers, body, false);
    }

    private RawResponse rawRequest(String method, String path,
                                   Map<String, String> headers, byte[] body,
                                   boolean omitRootTrailingSlash) throws Exception {
        return rawRequest(method, urlFor(path, omitRootTrailingSlash), headers, body, 0);
    }

    private RawResponse rawRequest(String method, URL url,
                                   Map<String, String> headers, byte[] body,
                                   int redirects) throws Exception {
        byte[] requestBody = body != null ? body : new byte[0];
        DiagnosticLogger.log("RAW open method=" + method
                + ", url=" + url
                + ", anonymous=" + config.anonymous
                + ", username=" + DiagnosticLogger.mask(config.username)
                + ", hasPassword=" + (config.password != null && config.password.length() > 0));

        Socket socket = null;
        try {
            socket = openSocket(url);
            OutputStream output = socket.getOutputStream();
            writeRawRequest(output, method, url, headers, requestBody);
            RawResponse response = readRawResponse((BufferedInputStream) null, socket, url.toString());
            if (isRedirect(response.code) && redirects < 3) {
                String location = response.header("location");
                closeQuietly(socket);
                socket = null;
                if (location != null && location.length() > 0) {
                    URL nextUrl = new URL(url, location);
                    DiagnosticLogger.log("RAW redirect " + response.code + " to " + nextUrl);
                    return rawRequest(method, nextUrl, headers, body, redirects + 1);
                }
            }
            return response;
        } finally {
            closeQuietly(socket);
        }
    }

    private Socket openSocket(URL url) throws Exception {
        boolean https = "https".equalsIgnoreCase(url.getProtocol());
        int port = url.getPort() > 0 ? url.getPort() : (https ? 443 : 80);
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(url.getHost(), port), 15000);
        plain.setSoTimeout(30000);
        if (!https) {
            return plain;
        }
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, url.getHost(), port, true);
        ssl.setSoTimeout(30000);
        ssl.startHandshake();
        return ssl;
    }

    private void writeRawRequest(OutputStream output, String method, URL url,
                                 Map<String, String> extraHeaders, byte[] body) throws Exception {
        String path = url.getFile();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        builder.append("Host: ").append(hostHeader(url)).append("\r\n");
        builder.append("User-Agent: SamsungMyFiles-WebDAV-LSPosed\r\n");
        builder.append("Accept: */*\r\n");
        builder.append("Connection: close\r\n");
        if (!config.anonymous && config.username != null && config.username.length() > 0) {
            String token = config.username + ":" + (config.password != null ? config.password : "");
            builder.append("Authorization: Basic ")
                    .append(Base64.encodeToString(token.getBytes("UTF-8"), Base64.NO_WRAP))
                    .append("\r\n");
        }
        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
                }
            }
        }
        builder.append("Content-Length: ").append(body != null ? body.length : 0).append("\r\n");
        builder.append("\r\n");
        output.write(builder.toString().getBytes("ISO-8859-1"));
        if (body != null && body.length > 0) {
            output.write(body);
        }
        output.flush();
    }

    private RawResponse readRawResponse(BufferedInputStream unused, Socket socket, String url) throws Exception {
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        String status = readAsciiLine(input);
        if (status == null || status.length() == 0) {
            throw new IOException("Empty HTTP response");
        }
        String[] parts = status.split(" ", 3);
        int code = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
        String reason = parts.length >= 3 ? parts[2] : "";
        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readAsciiLine(input)) != null) {
            if (line.length() == 0) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                        line.substring(colon + 1).trim());
            }
        }

        byte[] body;
        String transfer = headers.get("transfer-encoding");
        String length = headers.get("content-length");
        if (transfer != null && transfer.toLowerCase(Locale.US).contains("chunked")) {
            body = readChunked(input);
        } else if (length != null && length.length() > 0) {
            long contentLength = Long.parseLong(length);
            body = readFixed(input, contentLength);
        } else {
            body = readAll(input);
        }
        return new RawResponse(code, reason, headers, body, url);
    }

    private static String hostHeader(URL url) {
        boolean https = "https".equalsIgnoreCase(url.getProtocol());
        int port = url.getPort();
        if (port <= 0 || (https && port == 443) || (!https && port == 80)) {
            return url.getHost();
        }
        return url.getHost() + ":" + port;
    }

    private static boolean isRedirect(int code) {
        return code == 301 || code == 302 || code == 307 || code == 308;
    }

    private static String readAsciiLine(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int current;
        boolean seenAny = false;
        while ((current = input.read()) != -1) {
            seenAny = true;
            if (current == '\n') {
                break;
            }
            if (current != '\r') {
                output.write(current);
            }
        }
        if (!seenAny && output.size() == 0) {
            return null;
        }
        return output.toString("ISO-8859-1");
    }

    private static byte[] readChunked(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (true) {
            String line = readAsciiLine(input);
            if (line == null) {
                break;
            }
            int semicolon = line.indexOf(';');
            String rawSize = semicolon >= 0 ? line.substring(0, semicolon) : line;
            int size = Integer.parseInt(rawSize.trim(), 16);
            if (size == 0) {
                while ((line = readAsciiLine(input)) != null && line.length() > 0) {
                    // Consume trailing headers.
                }
                break;
            }
            byte[] chunk = readFixed(input, size);
            output.write(chunk);
            readAsciiLine(input);
        }
        return output.toByteArray();
    }

    private static byte[] readFixed(InputStream input, long length) throws IOException {
        if (length > Integer.MAX_VALUE) {
            throw new IOException("HTTP body too large: " + length);
        }
        byte[] result = new byte[(int) length];
        int offset = 0;
        while (offset < result.length) {
            int n = input.read(result, offset, result.length - offset);
            if (n == -1) {
                throw new IOException("Unexpected EOF in HTTP body");
            }
            offset += n;
        }
        return result;
    }

    private ArrayList<Item> parseMultiStatus(InputStream input, String requestedPath) throws Exception {
        byte[] xml = readAll(input);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        disableExternalEntities(factory);
        Document document = factory.newDocumentBuilder().parse(
                new InputSource(new ByteArrayInputStream(xml)));
        NodeList responses = document.getElementsByTagNameNS("*", "response");
        ArrayList<Item> items = new ArrayList<>();
        for (int i = 0; i < responses.getLength(); i++) {
            Node node = responses.item(i);
            if (!(node instanceof Element)) {
                continue;
            }
            Element response = (Element) node;
            String href = text(response, "href");
            if (href == null || href.length() == 0) {
                continue;
            }

            String relative = hrefToRelativePath(href);
            boolean directory = hasCollection(response);
            String displayName = text(response, "displayname");
            if (displayName == null || displayName.length() == 0) {
                displayName = nameOf(relative);
            }
            long size = parseLong(text(response, "getcontentlength"), 0L);
            long modified = parseHttpDate(text(response, "getlastmodified"));
            String mime = text(response, "getcontenttype");
            if (mime == null || mime.length() == 0) {
                mime = directory ? "resource/folder" : guessMime(displayName);
            }

            Item item = new Item();
            item.path = trimTrailingPathSlash(normalizePath(relative));
            item.name = displayName;
            item.directory = directory;
            item.size = directory ? 0L : size;
            item.modified = modified;
            item.mimeType = mime;
            items.add(item);
        }
        if (items.isEmpty()) {
            String path = normalizePath(requestedPath);
            Item fallback = new Item();
            fallback.path = trimTrailingPathSlash(path);
            fallback.name = nameOf(path);
            fallback.directory = true;
            fallback.mimeType = "resource/folder";
            items.add(fallback);
        }
        return items;
    }

    private String hrefToRelativePath(String href) throws Exception {
        String rawPath;
        if (href.startsWith("http://") || href.startsWith("https://")) {
            rawPath = new URL(href).getPath();
        } else {
            rawPath = href;
        }
        String decoded = URLDecoder.decode(rawPath, "UTF-8");
        String base = normalizePath(config.basePath);
        String relative;
        if (samePath(decoded, base)) {
            relative = "/";
        } else if (decoded.startsWith(base.endsWith("/") ? base : base + "/")) {
            relative = decoded.substring(base.length());
        } else {
            relative = decoded;
        }
        return normalizePath(relative);
    }

    private static void setRequestMethodCompat(HttpURLConnection connection, String method) throws Exception {
        try {
            connection.setRequestMethod(method);
            if (method.equals(connection.getRequestMethod())) {
                return;
            }
        } catch (ProtocolException ignored) {
            // Android's HttpURLConnection rejects WebDAV verbs on some releases.
        }

        if (forceRequestMethod(connection, method, 0)
                && method.equals(connection.getRequestMethod())) {
            return;
        }

        throw new ProtocolException("Cannot set HTTP method " + method
                + ", effective=" + connection.getRequestMethod()
                + ", class=" + connection.getClass().getName());
    }

    private static boolean forceRequestMethod(Object target, String method, int depth) {
        if (target == null || depth > 6) {
            return false;
        }

        boolean changed = false;
        Object delegate = getField(target, "delegate");
        if (delegate != null && delegate != target) {
            changed = forceRequestMethod(delegate, method, depth + 1);
        }

        changed = setMethodField(target, method) || changed;
        if (target instanceof HttpURLConnection) {
            try {
                if (method.equals(((HttpURLConnection) target).getRequestMethod())) {
                    DiagnosticLogger.log("HTTP method set on "
                            + target.getClass().getName()
                            + ", depth=" + depth
                            + ", method=" + method);
                    return true;
                }
            } catch (Throwable ignored) {
                // Keep trying other reflected targets.
            }
        }
        return changed;
    }

    private static boolean setMethodField(Object object, String method) {
        Class<?> cls = object.getClass();
        while (cls != null) {
            try {
                Field field = cls.getDeclaredField("method");
                field.setAccessible(true);
                field.set(object, method);
                return true;
            } catch (Throwable ignored) {
                cls = cls.getSuperclass();
            }
        }
        return false;
    }

    private static Object getField(Object object, String name) {
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
        return null;
    }

    static String childPath(String parent, String child) {
        String base = normalizePath(parent);
        String name = child != null ? child : "";
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        if ("/".equals(base)) {
            return "/" + name;
        }
        return base + "/" + name;
    }

    static String parentPath(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return "/";
        }
        int index = normalized.lastIndexOf('/');
        return index <= 0 ? "/" : normalized.substring(0, index);
    }

    static String nameOf(String path) {
        String normalized = normalizePath(path);
        if ("/".equals(normalized)) {
            return "";
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private static String trimTrailingPathSlash(String path) {
        if (path == null || path.length() <= 1) {
            return path;
        }
        String value = path;
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Ignore cleanup failures.
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        copy(input, output, -1L, null);
    }

    private static void copy(InputStream input, OutputStream output,
                             long totalBytes, ProgressListener progressListener) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long transferred = 0L;
        if (progressListener != null) {
            progressListener.onProgress(0L, totalBytes);
        }
        int n;
        while ((n = input.read(buffer)) != -1) {
            output.write(buffer, 0, n);
            transferred += n;
            if (progressListener != null) {
                progressListener.onProgress(transferred, totalBytes);
            }
        }
        if (progressListener != null) {
            progressListener.onProgress(transferred, totalBytes);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    private static void disableExternalEntities(DocumentBuilderFactory factory) {
        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Throwable ignored) {
            // Feature availability differs across Android releases.
        }
        try {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Throwable ignored) {
            // Feature availability differs across Android releases.
        }
    }

    private static String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes == null || nodes.getLength() == 0) {
            return "";
        }
        Node node = nodes.item(0);
        return node != null ? node.getTextContent() : "";
    }

    private static boolean hasCollection(Element response) {
        NodeList nodes = response.getElementsByTagNameNS("*", "collection");
        return nodes != null && nodes.getLength() > 0;
    }

    private static String guessMime(String name) {
        String mime = URLConnection.guessContentTypeFromName(name);
        return mime != null ? mime : "application/octet-stream";
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value != null && value.length() > 0 ? Long.parseLong(value.trim()) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseHttpDate(String value) {
        if (value == null || value.length() == 0) {
            return 0L;
        }
        String[] patterns = {
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEEE, dd-MMM-yy HH:mm:ss zzz",
                "EEE MMM d HH:mm:ss yyyy"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
                // Try the next common WebDAV timestamp format.
            }
        }
        return 0L;
    }

    private static String encodeSegment(String segment) throws Exception {
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20");
    }

    private static String joinPath(String basePath, String relativePath) {
        String base = normalizePath(basePath);
        String rel = normalizePath(relativePath);
        if ("/".equals(base)) {
            return rel;
        }
        if (samePath(base, rel) || rel.startsWith(base.endsWith("/") ? base : base + "/")) {
            return rel;
        }
        if ("/".equals(rel)) {
            return base;
        }
        return base + rel;
    }

    private static String normalizePath(String path) {
        if (path == null || path.length() == 0) {
            return "/";
        }
        String value = path.trim();
        if (value.length() == 0) {
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

    private static boolean samePath(String a, String b) {
        String left = normalizePath(a);
        String right = normalizePath(b);
        if (left.endsWith("/") && left.length() > 1) {
            left = left.substring(0, left.length() - 1);
        }
        if (right.endsWith("/") && right.length() > 1) {
            right = right.substring(0, right.length() - 1);
        }
        return left.equals(right);
    }

    static final class Item {
        String path;
        String name;
        boolean directory;
        long size;
        long modified;
        String mimeType;
    }

    private static final class RawResponse {
        final int code;
        final String reason;
        final Map<String, String> headers;
        final byte[] body;
        final String url;

        RawResponse(int code, String reason, Map<String, String> headers, byte[] body, String url) {
            this.code = code;
            this.reason = reason;
            this.headers = headers;
            this.body = body != null ? body : new byte[0];
            this.url = url;
        }

        String header(String name) {
            if (name == null || headers == null) {
                return "";
            }
            String value = headers.get(name.toLowerCase(Locale.US));
            return value != null ? value : "";
        }

        String bodyAsString() {
            try {
                return new String(body, "UTF-8");
            } catch (Throwable ignored) {
                return "";
            }
        }
    }
}
