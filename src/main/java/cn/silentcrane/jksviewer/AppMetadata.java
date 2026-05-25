package cn.silentcrane.jksviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class AppMetadata {
    private static final String RESOURCE_PATH = "/jksviewer.properties";

    private final String name;
    private final String version;
    private final String vendor;
    private final String repositoryUrl;
    private final String developerHomepageUrl;
    private final String license;
    private final String releaseApiUrl;

    public AppMetadata(
            String name,
            String version,
            String vendor,
            String repositoryUrl,
            String developerHomepageUrl,
            String license,
            String releaseApiUrl
    ) {
        this.name = name;
        this.version = version;
        this.vendor = vendor;
        this.repositoryUrl = repositoryUrl;
        this.developerHomepageUrl = developerHomepageUrl;
        this.license = license;
        this.releaseApiUrl = releaseApiUrl;
    }

    public static AppMetadata load() {
        Properties properties = new Properties();
        try (InputStream input = AppMetadata.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input != null) {
                properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // Fall back to defaults when metadata is unavailable in a development run.
        }
        return new AppMetadata(
                value(properties, "app.name", "JKS Viewer"),
                value(properties, "app.version", "1.0.0"),
                value(properties, "app.vendor", "Silent Crane"),
                value(properties, "app.repository", "https://github.com/yin2hao-windowsTools/jks-viewer"),
                value(properties, "app.developerHomepage", "https://github.com/yin2hao-windowsTools"),
                value(properties, "app.license", "Unspecified"),
                value(properties, "app.releaseApi", "https://api.github.com/repos/yin2hao-windowsTools/jks-viewer/releases/latest")
        );
    }

    public String name() {
        return name;
    }

    public String version() {
        return version;
    }

    public String vendor() {
        return vendor;
    }

    public String repositoryUrl() {
        return repositoryUrl;
    }

    public String developerHomepageUrl() {
        return developerHomepageUrl;
    }

    public String license() {
        return license;
    }

    public String releaseApiUrl() {
        return releaseApiUrl;
    }

    public boolean isPortableRuntime() {
        return Boolean.getBoolean("jksviewer.portable");
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
