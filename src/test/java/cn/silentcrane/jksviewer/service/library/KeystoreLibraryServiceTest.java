package cn.silentcrane.jksviewer.service.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KeystoreLibraryServiceTest {
    @TempDir
    Path tempDir;

    private final KeystoreLibraryService service = new KeystoreLibraryService();

    @Test
    void createsDefaultLibraryUnderAppHome() throws Exception {
        Path library = service.createDefaultLibrary(tempDir);

        assertEquals(tempDir.resolve("keystore-library").toAbsolutePath().normalize(), library);
        assertTrue(Files.isDirectory(library));
    }

    @Test
    void createsLibraryDirectoryAndScansSupportedKeystoresRecursively() throws Exception {
        Path library = tempDir.resolve("keys");
        Path nested = library.resolve("release");
        Files.createDirectories(nested);
        Path jks = library.resolve("upload.JKS");
        Path keystore = nested.resolve("app.keystore");
        Path ignored = nested.resolve("notes.txt");
        Files.writeString(jks, "jks");
        Files.writeString(keystore, "keystore");
        Files.writeString(ignored, "notes");

        Path created = service.createLibrary(library);
        List<Path> files = service.scan(created);

        assertEquals(library.toAbsolutePath().normalize(), created);
        assertEquals(List.of(nested.resolve("app.keystore"), library.resolve("upload.JKS")), files);
    }

    @Test
    void checksWhetherFileBelongsToLibraryAndUsesSupportedExtension() throws Exception {
        Path library = service.createLibrary(tempDir.resolve("keys"));
        Path file = library.resolve("release.p12");
        Path unsupported = library.resolve("release.txt");
        Path outside = tempDir.resolve("outside.jks");
        Files.writeString(file, "p12");
        Files.writeString(unsupported, "txt");
        Files.writeString(outside, "jks");

        assertTrue(service.isLibraryFile(library, file));
        assertFalse(service.isLibraryFile(library, unsupported));
        assertFalse(service.isLibraryFile(library, outside));
    }
}
