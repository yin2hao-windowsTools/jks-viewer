package cn.silentcrane.jksviewer.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class RuntimePaths {
    private RuntimePaths() {
    }

    public static Path locateAppHome(Class<?> anchorClass) throws Exception {
        URI codeSource = anchorClass.getProtectionDomain().getCodeSource().getLocation().toURI();
        Path location = Path.of(codeSource).toAbsolutePath().normalize();
        Path parent = Files.isRegularFile(location) ? location.getParent() : location;
        if (parent != null && ("app".equalsIgnoreCase(parent.getFileName().toString())
                || "lib".equalsIgnoreCase(parent.getFileName().toString()))) {
            return parent.getParent();
        }
        return parent == null ? Path.of(".").toAbsolutePath().normalize() : parent;
    }

    public static Optional<Path> appExecutable(Path appHome, String appDisplayName) {
        Path executable = appHome.resolve(appDisplayName + ".exe");
        return Files.isRegularFile(executable) ? Optional.of(executable) : Optional.empty();
    }
}
