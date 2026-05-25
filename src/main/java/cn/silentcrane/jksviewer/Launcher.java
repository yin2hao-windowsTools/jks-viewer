package cn.silentcrane.jksviewer;

import cn.silentcrane.jksviewer.service.CrashReporter;
import cn.silentcrane.jksviewer.service.RuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        configurePortableRuntime();
        CrashReporter.install(Launcher.class, AppMetadata.load());
        JksViewerApp.main(args);
    }

    private static void configurePortableRuntime() {
        if (!Boolean.getBoolean("jksviewer.portable")) {
            return;
        }
        try {
            Path appHome = locateAppHome();
            Path dataDir = appHome.resolve(".portable-data");
            Path tempDir = dataDir.resolve("tmp");
            Path prefsDir = dataDir.resolve("prefs");
            Path homeDir = dataDir.resolve("home");
            Files.createDirectories(tempDir);
            Files.createDirectories(prefsDir);
            Files.createDirectories(homeDir);
            System.setProperty("java.io.tmpdir", tempDir.toString());
            System.setProperty("java.util.prefs.userRoot", prefsDir.toString());
            System.setProperty("user.home", homeDir.toString());
        } catch (Exception ex) {
            System.err.println("Portable runtime setup failed: " + ex.getMessage());
        }
    }

    private static Path locateAppHome() throws Exception {
        return RuntimePaths.locateAppHome(Launcher.class);
    }
}
