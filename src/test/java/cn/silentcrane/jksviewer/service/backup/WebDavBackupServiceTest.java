package cn.silentcrane.jksviewer.service.backup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class WebDavBackupServiceTest {
    @TempDir
    Path tempDir;

    private HttpServer server;
    private final List<String> methods = new ArrayList<>();
    private final List<String> paths = new ArrayList<>();
    private byte[] uploadedBytes = new byte[0];
    private String expectedAuthorization;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void createsDirectoriesAndUploadsFileWithBasicAuthorization() throws Exception {
        expectedAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString("user:secret".getBytes(StandardCharsets.UTF_8));
        startServer(201);
        Path source = tempDir.resolve("release 密钥.jks");
        Files.writeString(source, "keystore-bytes", StandardCharsets.UTF_8);

        URI target = new WebDavBackupService().backup(new WebDavBackupRequest(
                source,
                URI.create(serverBaseUri() + "/dav%20root"),
                "android/release",
                "user",
                "secret".toCharArray()
        ));

        assertEquals(serverBaseUri() + "/dav%20root/android/release/release%20%E5%AF%86%E9%92%A5.jks", target.toString());
        assertEquals(List.of("MKCOL", "MKCOL", "PUT"), methods);
        assertEquals(List.of("/dav root/android/", "/dav root/android/release/", "/dav root/android/release/release 密钥.jks"), paths);
        assertEquals("keystore-bytes", new String(uploadedBytes, StandardCharsets.UTF_8));
    }

    @Test
    void reportsAuthenticationFailure() throws Exception {
        expectedAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString("user:secret".getBytes(StandardCharsets.UTF_8));
        startServer(201);
        Path source = tempDir.resolve("release.jks");
        Files.writeString(source, "keystore-bytes", StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> new WebDavBackupService().backup(new WebDavBackupRequest(
                source,
                URI.create(serverBaseUri() + "/dav"),
                "android",
                "user",
                "wrong".toCharArray()
        )));

        assertTrue(ex.getMessage().contains("认证失败"));
    }

    private void startServer(int successStatus) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getPath());
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (expectedAuthorization != null && !expectedAuthorization.equals(authorization)) {
                send(exchange, 401, "Unauthorized");
                return;
            }
            if ("PUT".equals(exchange.getRequestMethod())) {
                uploadedBytes = exchange.getRequestBody().readAllBytes();
            }
            send(exchange, successStatus, "");
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    private String serverBaseUri() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
