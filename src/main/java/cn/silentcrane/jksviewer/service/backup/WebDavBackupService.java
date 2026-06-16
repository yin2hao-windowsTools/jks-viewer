package cn.silentcrane.jksviewer.service.backup;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class WebDavBackupService {
    private final HttpClient httpClient;

    public WebDavBackupService() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    WebDavBackupService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public URI backup(WebDavBackupRequest request) throws IOException, InterruptedException {
        validateRequest(request);
        URI baseUri = normalizeBaseUri(request.serverUri());
        List<String> segments = directorySegments(request.remoteDirectory());
        createRemoteDirectories(baseUri, segments, request);
        URI targetUri = backupTargetUri(baseUri, segments, request.sourceFile().getFileName().toString());
        HttpRequest.Builder builder = requestBuilder(targetUri, request)
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofFile(request.sourceFile()));
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response, "上传");
        return targetUri;
    }

    private void validateRequest(WebDavBackupRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("备份请求不能为空。");
        }
        Path sourceFile = request.sourceFile();
        if (sourceFile == null) {
            throw new IllegalArgumentException("请选择要备份的密钥文件。");
        }
        if (!Files.exists(sourceFile)) {
            throw new IOException("密钥文件不存在: " + sourceFile);
        }
        if (!Files.isRegularFile(sourceFile)) {
            throw new IOException("请选择一个密钥文件，而不是文件夹。");
        }
    }

    private void createRemoteDirectories(
            URI baseUri,
            List<String> segments,
            WebDavBackupRequest request
    ) throws IOException, InterruptedException {
        if (segments.isEmpty()) {
            return;
        }

        List<String> current = new ArrayList<>();
        for (String segment : segments) {
            current.add(segment);
            URI directoryUri = directoryUri(baseUri, current);
            HttpRequest.Builder builder = requestBuilder(directoryUri, request)
                    .timeout(Duration.ofSeconds(30))
                    .method("MKCOL", HttpRequest.BodyPublishers.noBody());
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 405) {
                continue;
            }
            ensureSuccess(response, "创建远程目录");
        }
    }

    private HttpRequest.Builder requestBuilder(URI uri, WebDavBackupRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri);
        if (!request.username().isBlank()) {
            builder.header("Authorization", basicAuthorization(request.username(), request.password()));
        }
        return builder;
    }

    private String basicAuthorization(String username, char[] password) {
        String token = username + ":" + new String(password);
        return "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private void ensureSuccess(HttpResponse<String> response, String action) throws IOException {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        if (status == 401 || status == 403) {
            throw new IOException("WebDAV 认证失败，请检查用户名和密码。");
        }
        throw new IOException(action + "失败，HTTP " + status + responseBodyHint(response.body()));
    }

    private String responseBodyHint(String body) {
        if (body == null || body.isBlank()) {
            return "。";
        }
        String trimmed = body.strip();
        if (trimmed.length() > 160) {
            trimmed = trimmed.substring(0, 160) + "...";
        }
        return ": " + trimmed;
    }

    private URI normalizeBaseUri(URI serverUri) {
        if (serverUri == null) {
            throw new IllegalArgumentException("WebDAV 地址不能为空。");
        }
        String scheme = serverUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("WebDAV 地址必须以 http:// 或 https:// 开头。");
        }
        if (serverUri.getRawQuery() != null || serverUri.getRawFragment() != null) {
            throw new IllegalArgumentException("WebDAV 地址不能包含查询参数或锚点。");
        }

        String text = serverUri.toString();
        if (!text.endsWith("/")) {
            text += "/";
        }
        return URI.create(text);
    }

    private List<String> directorySegments(String remoteDirectory) {
        List<String> segments = new ArrayList<>();
        if (remoteDirectory == null || remoteDirectory.isBlank()) {
            return segments;
        }
        for (String raw : remoteDirectory.trim().split("[/\\\\]+")) {
            if (raw.isBlank() || ".".equals(raw)) {
                continue;
            }
            if ("..".equals(raw)) {
                throw new IllegalArgumentException("远程目录不能包含上级目录引用。");
            }
            segments.add(raw);
        }
        return segments;
    }

    private URI directoryUri(URI baseUri, List<String> segments) {
        return resolveRelative(baseUri, encodedRelativePath(segments, true));
    }

    private URI backupTargetUri(URI baseUri, List<String> directorySegments, String fileName) {
        List<String> segments = new ArrayList<>(directorySegments);
        segments.add(fileName);
        return resolveRelative(baseUri, encodedRelativePath(segments, false));
    }

    private URI resolveRelative(URI baseUri, String relativePath) {
        return baseUri.resolve(relativePath);
    }

    private String encodedRelativePath(List<String> segments, boolean trailingSlash) {
        StringBuilder path = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) {
                path.append('/');
            }
            path.append(encodeSegment(segments.get(index)));
        }
        if (trailingSlash) {
            path.append('/');
        }
        return path.toString();
    }

    private String encodeSegment(String segment) {
        try {
            return new URI(null, null, segment, null).toASCIIString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("远程路径包含非法字符: " + segment, ex);
        }
    }
}
