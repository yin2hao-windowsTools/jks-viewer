package cn.silentcrane.jksviewer.service.settings;

import cn.silentcrane.jksviewer.service.RuntimePaths;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppSettingsService {
    private static final String SETTINGS_DIRECTORY_NAME = "settings";
    private static final String SETTINGS_FILE_NAME = "jks-viewer-settings.properties";
    private static final String FORMAT_VERSION_KEY = "format.version";
    private static final String FORMAT_VERSION = "1";
    private static final String WEB_DAV_SERVER_URI_KEY = "webdav.serverUri";
    private static final String WEB_DAV_REMOTE_DIRECTORY_KEY = "webdav.remoteDirectory";
    private static final String WEB_DAV_USERNAME_KEY = "webdav.username";
    private static final String WEB_DAV_PASSWORD_KEY = "webdav.password";

    public Path defaultSettingsFile(Class<?> anchorClass) throws Exception {
        return defaultSettingsFile(RuntimePaths.locateAppHome(anchorClass));
    }

    public Path defaultSettingsFile(Path appHome) {
        if (appHome == null) {
            throw new IllegalArgumentException("安装路径不能为空。");
        }
        return appHome.resolve(SETTINGS_DIRECTORY_NAME).resolve(SETTINGS_FILE_NAME).toAbsolutePath().normalize();
    }

    public AppSettings load(Path file) throws IOException {
        Path settingsFile = requireFile(file);
        if (!Files.exists(settingsFile)) {
            return AppSettings.empty();
        }
        if (!Files.isRegularFile(settingsFile)) {
            throw new IOException("设置路径不是文件: " + settingsFile);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            properties.load(input);
        }
        return fromProperties(properties);
    }

    public void save(Path file, AppSettings settings) throws IOException {
        Path settingsFile = requireFile(file);
        Path parent = settingsFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream output = Files.newOutputStream(settingsFile)) {
            toProperties(settings).store(output, "JKS Viewer settings");
        }
    }

    public AppSettings importSettings(Path importFile, Path settingsFile) throws IOException {
        AppSettings imported = load(importFile);
        save(settingsFile, imported);
        return imported;
    }

    public void exportSettings(Path settingsFile, Path exportFile) throws IOException {
        AppSettings current = load(settingsFile);
        save(exportFile, current);
    }

    private AppSettings fromProperties(Properties properties) {
        return new AppSettings(new WebDavSettings(
                properties.getProperty(WEB_DAV_SERVER_URI_KEY, ""),
                properties.getProperty(WEB_DAV_REMOTE_DIRECTORY_KEY, ""),
                properties.getProperty(WEB_DAV_USERNAME_KEY, ""),
                properties.getProperty(WEB_DAV_PASSWORD_KEY, "").toCharArray()
        ));
    }

    private Properties toProperties(AppSettings settings) {
        AppSettings normalized = settings == null ? AppSettings.empty() : settings;
        WebDavSettings webDav = normalized.webDav();
        Properties properties = new Properties();
        properties.setProperty(FORMAT_VERSION_KEY, FORMAT_VERSION);
        properties.setProperty(WEB_DAV_SERVER_URI_KEY, webDav.serverUri());
        properties.setProperty(WEB_DAV_REMOTE_DIRECTORY_KEY, webDav.remoteDirectory());
        properties.setProperty(WEB_DAV_USERNAME_KEY, webDav.username());
        properties.setProperty(WEB_DAV_PASSWORD_KEY, new String(webDav.password()));
        return properties;
    }

    private Path requireFile(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("设置文件路径不能为空。");
        }
        return file.toAbsolutePath().normalize();
    }
}
