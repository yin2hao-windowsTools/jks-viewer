package cn.silentcrane.jksviewer;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Launcher {
    private Launcher() {
    }

    public static void main(String[] args) {
        configurePortableRuntime();
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
        URI codeSource = Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path location = Path.of(codeSource).toAbsolutePath().normalize();
        Path parent = Files.isRegularFile(location) ? location.getParent() : location;
        if (parent != null && ("app".equalsIgnoreCase(parent.getFileName().toString())
                || "lib".equalsIgnoreCase(parent.getFileName().toString()))) {
            return parent.getParent();
        }
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }
}
