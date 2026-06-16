package cn.silentcrane.jksviewer.service.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AppSettingsServiceTest {
    @TempDir
    Path tempDir;

    private final AppSettingsService service = new AppSettingsService();

    @Test
    void buildsDefaultSettingsFileUnderAppHome() {
        Path appHome = tempDir.resolve("app-home");

        Path settingsFile = service.defaultSettingsFile(appHome);

        assertEquals(appHome.resolve("settings").resolve("jks-viewer-settings.properties"), settingsFile);
    }

    @Test
    void savesAndLoadsWebDavSettings() throws Exception {
        Path settingsFile = tempDir.resolve("settings.properties");
        AppSettings settings = new AppSettings(new WebDavSettings(
                " https://example.com/dav/ ",
                " android/release ",
                " user ",
                "secret".toCharArray()
        ));

        service.save(settingsFile, settings);
        AppSettings loaded = service.load(settingsFile);

        assertEquals("https://example.com/dav/", loaded.webDav().serverUri());
        assertEquals("android/release", loaded.webDav().remoteDirectory());
        assertEquals("user", loaded.webDav().username());
        assertArrayEquals("secret".toCharArray(), loaded.webDav().password());
    }

    @Test
    void exportCopiesCurrentSettingsToTargetFile() throws Exception {
        Path settingsFile = tempDir.resolve("settings.properties");
        Path exportFile = tempDir.resolve("backup").resolve("exported.properties");
        service.save(settingsFile, new AppSettings(new WebDavSettings(
                "https://example.com/dav/",
                "jks-backup",
                "admin",
                "pass".toCharArray()
        )));

        service.exportSettings(settingsFile, exportFile);
        AppSettings exported = service.load(exportFile);

        assertEquals("https://example.com/dav/", exported.webDav().serverUri());
        assertEquals("pass", new String(exported.webDav().password()));
    }

    @Test
    void importLoadsSourceAndReplacesCurrentSettings() throws Exception {
        Path settingsFile = tempDir.resolve("settings.properties");
        Path importFile = tempDir.resolve("import.properties");
        service.save(settingsFile, new AppSettings(new WebDavSettings(
                "https://old.example.com/dav/",
                "old",
                "old-user",
                "old-pass".toCharArray()
        )));
        service.save(importFile, new AppSettings(new WebDavSettings(
                "https://new.example.com/dav/",
                "new",
                "new-user",
                "new-pass".toCharArray()
        )));

        AppSettings imported = service.importSettings(importFile, settingsFile);
        AppSettings current = service.load(settingsFile);

        assertEquals("https://new.example.com/dav/", imported.webDav().serverUri());
        assertEquals("new", current.webDav().remoteDirectory());
        assertEquals("new-user", current.webDav().username());
        assertEquals("new-pass", new String(current.webDav().password()));
    }

    @Test
    void returnsEmptySettingsWhenFileDoesNotExist() throws Exception {
        AppSettings settings = service.load(tempDir.resolve("missing.properties"));

        assertEquals("", settings.webDav().serverUri());
        assertEquals("", settings.webDav().remoteDirectory());
        assertEquals("", settings.webDav().username());
        assertArrayEquals(new char[0], settings.webDav().password());
    }
}
