package cn.silentcrane.jksviewer.service.library;

import cn.silentcrane.jksviewer.service.RuntimePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class KeystoreLibraryService {
    private static final String LIBRARY_DIRECTORY_NAME = "keystore-library";
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".jks",
            ".keystore",
            ".p12",
            ".pfx",
            ".bks",
            ".bcfks"
    );

    public Path createDefaultLibrary(Class<?> anchorClass) throws Exception {
        return createDefaultLibrary(RuntimePaths.locateAppHome(anchorClass));
    }

    public Path createDefaultLibrary(Path appHome) throws IOException {
        if (appHome == null) {
            throw new IllegalArgumentException("安装路径不能为空。");
        }
        return createLibrary(appHome.resolve(LIBRARY_DIRECTORY_NAME));
    }

    public Path createLibrary(Path directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("库目录不能为空。");
        }
        Path absoluteDirectory = directory.toAbsolutePath().normalize();
        Files.createDirectories(absoluteDirectory);
        if (!Files.isDirectory(absoluteDirectory)) {
            throw new IOException("无法创建库目录: " + absoluteDirectory);
        }
        return absoluteDirectory;
    }

    public List<Path> scan(Path directory) throws IOException {
        Path libraryDirectory = validateLibraryDirectory(directory);
        try (Stream<Path> paths = Files.walk(libraryDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedKeystoreFile)
                    .sorted(Comparator.comparing(path -> libraryDirectory.relativize(path).toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
    }

    public boolean isLibraryFile(Path libraryDirectory, Path file) {
        if (libraryDirectory == null || file == null || !isSupportedKeystoreFile(file)) {
            return false;
        }
        Path library = libraryDirectory.toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        return target.startsWith(library);
    }

    public boolean isSupportedKeystoreFile(Path file) {
        if (file == null || file.getFileName() == null) {
            return false;
        }
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private Path validateLibraryDirectory(Path directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("库目录不能为空。");
        }
        Path libraryDirectory = directory.toAbsolutePath().normalize();
        if (!Files.exists(libraryDirectory)) {
            throw new IOException("库目录不存在: " + libraryDirectory);
        }
        if (!Files.isDirectory(libraryDirectory)) {
            throw new IOException("请选择一个文件夹作为库。");
        }
        return libraryDirectory;
    }
}
