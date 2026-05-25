package cn.silentcrane.jksviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.silentcrane.jksviewer.AppMetadata;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CrashReporterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesCrashReportWithMetadataAndStackTrace() throws Exception {
        AppMetadata metadata = metadata("2.3.4");
        RuntimeException crash = new RuntimeException("boom");
        crash.addSuppressed(new IllegalArgumentException("retry failed"));
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T13:30:45.123Z"), ZoneId.of("UTC"));
        Thread crashThread = new Thread("main");

        Path report = CrashReporter.writeCrashReport(
                crashThread,
                crash,
                metadata,
                tempDir,
                clock
        );

        assertTrue(Files.isRegularFile(report));
        assertEquals("crash-20260525-133045-123-main.log", report.getFileName().toString());

        String content = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(content.contains("App: JKS Viewer"));
        assertTrue(content.contains("Version: 2.3.4"));
        assertTrue(content.contains("Thread: main"));
        assertTrue(content.contains("java.lang.RuntimeException: boom"));
        assertTrue(content.contains("Suppressed: java.lang.IllegalArgumentException: retry failed"));
    }

    @Test
    void keepsExistingCrashReportWhenTimestampCollides() throws Exception {
        AppMetadata metadata = metadata("1.0.0");
        Clock clock = Clock.fixed(Instant.parse("2026-05-25T13:30:45.123Z"), ZoneId.of("UTC"));
        Thread crashThread = new Thread("main");

        Path first = CrashReporter.writeCrashReport(
                crashThread,
                new RuntimeException("first"),
                metadata,
                tempDir,
                clock
        );
        Path second = CrashReporter.writeCrashReport(
                crashThread,
                new RuntimeException("second"),
                metadata,
                tempDir,
                clock
        );

        assertEquals("crash-20260525-133045-123-main.log", first.getFileName().toString());
        assertEquals("crash-20260525-133045-123-main-1.log", second.getFileName().toString());
        assertTrue(Files.readString(first, StandardCharsets.UTF_8).contains("first"));
        assertTrue(Files.readString(second, StandardCharsets.UTF_8).contains("second"));
    }

    private AppMetadata metadata(String version) {
        return new AppMetadata(
                "JKS Viewer",
                version,
                "Silent Crane",
                "https://example.com/repo",
                "https://example.com",
                "Unspecified",
                "https://example.com/releases/latest"
        );
    }
}
