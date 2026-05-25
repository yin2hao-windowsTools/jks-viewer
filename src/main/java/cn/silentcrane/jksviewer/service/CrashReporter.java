package cn.silentcrane.jksviewer.service;

import cn.silentcrane.jksviewer.AppMetadata;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CrashReporter {
    public static final String CRASH_DIR_PROPERTY = "jksviewer.crash.dir";

    private static final DateTimeFormatter FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static volatile Path crashDirectory;
    private static volatile AppMetadata metadata;

    private CrashReporter() {
    }

    public static void install(Class<?> anchorClass, AppMetadata appMetadata) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        metadata = appMetadata;
        crashDirectory = defaultCrashDirectory(anchorClass, appMetadata);
        Thread.UncaughtExceptionHandler handler = CrashReporter::handleUncaughtException;
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }

    public static void attach(Thread thread) {
        thread.setUncaughtExceptionHandler(CrashReporter::handleUncaughtException);
    }

    public static void attachCurrentThread() {
        attach(Thread.currentThread());
    }

    public static Optional<Path> report(Thread thread, Throwable throwable) {
        Path directory = crashDirectory;
        AppMetadata currentMetadata = metadata;
        if (directory == null || currentMetadata == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(writeCrashReport(
                    thread,
                    throwable,
                    currentMetadata,
                    directory,
                    Clock.systemDefaultZone()
            ));
        } catch (Exception ex) {
            System.err.println("Failed to write crash report: " + ex.getMessage());
            ex.printStackTrace(System.err);
            return Optional.empty();
        }
    }

    private static void handleUncaughtException(Thread thread, Throwable throwable) {
        report(thread, throwable)
                .ifPresent(path -> System.err.println("Crash report written to: " + path.toAbsolutePath()));
    }

    private static Path defaultCrashDirectory(Class<?> anchorClass, AppMetadata appMetadata) {
        String override = System.getProperty(CRASH_DIR_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        if (appMetadata.isPortableRuntime()) {
            try {
                return RuntimePaths.locateAppHome(anchorClass)
                        .resolve(".portable-data")
                        .resolve("crash")
                        .toAbsolutePath()
                        .normalize();
            } catch (Exception ignored) {
                // Fall back to the user-scoped location when the packaged app home cannot be resolved.
            }
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, appMetadata.name(), "crash").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home", "."), ".jks-viewer", "crash")
                .toAbsolutePath()
                .normalize();
    }

    static Path writeCrashReport(
            Thread thread,
            Throwable throwable,
            AppMetadata appMetadata,
            Path directory,
            Clock clock
    ) throws Exception {
        Files.createDirectories(directory);
        ZonedDateTime now = ZonedDateTime.now(clock);
        String baseName = "crash-" + FILE_TIMESTAMP_FORMATTER.format(now)
                + "-" + safeFilePart(thread.getName());
        Path reportPath = uniqueReportPath(directory, baseName);
        Files.writeString(reportPath, renderReport(thread, throwable, appMetadata, now), StandardCharsets.UTF_8);
        return reportPath;
    }

    private static Path uniqueReportPath(Path directory, String baseName) {
        Path reportPath = directory.resolve(baseName + ".log");
        int index = 1;
        while (Files.exists(reportPath)) {
            reportPath = directory.resolve(baseName + "-" + index + ".log");
            index++;
        }
        return reportPath;
    }

    private static String renderReport(
            Thread thread,
            Throwable throwable,
            AppMetadata appMetadata,
            ZonedDateTime timestamp
    ) {
        StringWriter stackTrace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackTrace));

        return "App: " + appMetadata.name() + System.lineSeparator()
                + "Version: " + appMetadata.version() + System.lineSeparator()
                + "Time: " + timestamp + System.lineSeparator()
                + "Thread: " + thread.getName() + " (" + thread.getState() + ")" + System.lineSeparator()
                + "Java: " + System.getProperty("java.version") + " "
                + System.getProperty("java.vendor") + System.lineSeparator()
                + "OS: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " "
                + System.getProperty("os.arch") + System.lineSeparator()
                + "Working Directory: " + Path.of(System.getProperty("user.dir", ".")).toAbsolutePath()
                + System.lineSeparator()
                + System.lineSeparator()
                + "Exception:" + System.lineSeparator()
                + stackTrace;
    }

    private static String safeFilePart(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isBlank() ? "thread" : safe;
    }
}
