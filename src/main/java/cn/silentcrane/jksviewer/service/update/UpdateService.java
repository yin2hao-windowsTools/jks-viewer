package cn.silentcrane.jksviewer.service.update;

import cn.silentcrane.jksviewer.AppMetadata;
import cn.silentcrane.jksviewer.service.RuntimePaths;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

public final class UpdateService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);

    private final AppMetadata metadata;
    private final HttpClient httpClient;

    public UpdateService(AppMetadata metadata) {
        this(metadata, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    UpdateService(AppMetadata metadata, HttpClient httpClient) {
        this.metadata = metadata;
        this.httpClient = httpClient;
    }

    public UpdateCheckResult checkLatest() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(metadata.releaseApiUrl()))
                .timeout(CONNECT_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", userAgent())
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 404) {
            throw new IOException("GitHub 仓库还没有可用的 Release。");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub Release 请求失败，状态码: " + response.statusCode());
        }
        ReleaseInfo latestRelease = GitHubReleaseParser.parse(response.body());
        boolean updateAvailable = VersionComparator.isNewer(latestRelease.version(), metadata.version());
        return new UpdateCheckResult(metadata.version(), latestRelease, updateAvailable);
    }

    public Optional<ReleaseAsset> preferredAsset(ReleaseInfo release) {
        if (metadata.isPortableRuntime()) {
            Optional<ReleaseAsset> portableAsset = release.assets().stream()
                    .filter(this::isPortableZip)
                    .findFirst();
            if (portableAsset.isPresent()) {
                return portableAsset;
            }
        }
        return release.assets().stream()
                .filter(asset -> lowerName(asset).endsWith(".exe"))
                .min(Comparator.comparingInt(this::installerPreference))
                .or(() -> release.assets().stream().filter(asset -> lowerName(asset).endsWith(".msi")).findFirst())
                .or(() -> release.assets().stream().filter(this::isPortableZip).findFirst());
    }

    public Path downloadAsset(ReleaseAsset asset) throws IOException, InterruptedException {
        Path downloadDir = Files.createTempDirectory("jksviewer-update-");
        Path target = downloadDir.resolve(safeFileName(asset.name()));
        HttpRequest request = HttpRequest.newBuilder(asset.downloadUri())
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", userAgent())
                .GET()
                .build();
        HttpResponse<Path> response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("下载更新包失败，状态码: " + response.statusCode());
        }
        return target;
    }

    public UpdateInstallAction installDownloadedAsset(ReleaseAsset asset, Path assetFile) throws IOException {
        String lowerName = lowerName(asset);
        if (lowerName.endsWith(".zip") && metadata.isPortableRuntime()) {
            launchPortableUpdater(assetFile);
            return UpdateInstallAction.PORTABLE_UPDATE_STARTED;
        }
        if (lowerName.endsWith(".msi")) {
            new ProcessBuilder("msiexec.exe", "/i", assetFile.toString()).start();
            return UpdateInstallAction.INSTALLER_STARTED;
        }
        if (lowerName.endsWith(".exe")) {
            new ProcessBuilder(assetFile.toString()).start();
            return UpdateInstallAction.INSTALLER_STARTED;
        }
        openFile(assetFile);
        return UpdateInstallAction.FILE_OPENED;
    }

    private void launchPortableUpdater(Path portableZip) throws IOException {
        try {
            Path appHome = RuntimePaths.locateAppHome(UpdateService.class);
            Path executable = RuntimePaths.appExecutable(appHome, metadata.name())
                    .orElseThrow(() -> new IOException("找不到当前 portable 程序入口: " + metadata.name() + ".exe"));
            Path script = Files.createTempFile("jksviewer-update-", ".ps1");
            Files.writeString(script, portableUpdateScript(), StandardCharsets.UTF_8);
            new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-WindowStyle",
                    "Hidden",
                    "-File",
                    script.toString(),
                    Long.toString(ProcessHandle.current().pid()),
                    portableZip.toString(),
                    appHome.toString(),
                    executable.toString(),
                    metadata.name()
            ).start();
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("启动自更新脚本失败。", ex);
        }
    }

    private String portableUpdateScript() {
        return """
                param(
                    [long]$TargetProcessId,
                    [string]$ZipPath,
                    [string]$AppHome,
                    [string]$ExePath,
                    [string]$AppName
                )

                $ErrorActionPreference = 'Stop'
                $updateRoot = Split-Path -LiteralPath $ZipPath -Parent
                $extractDir = Join-Path $updateRoot 'expanded'
                $logPath = Join-Path $updateRoot 'update.log'

                try {
                    "Starting update $(Get-Date -Format o)" | Out-File -LiteralPath $logPath -Encoding UTF8 -Append
                    try { Wait-Process -Id $TargetProcessId -Timeout 180 -ErrorAction SilentlyContinue } catch {}
                    Start-Sleep -Seconds 1

                    if (Test-Path -LiteralPath $extractDir) {
                        Remove-Item -LiteralPath $extractDir -Recurse -Force
                    }
                    Expand-Archive -LiteralPath $ZipPath -DestinationPath $extractDir -Force

                    $source = Join-Path $extractDir $AppName
                    if (-not (Test-Path -LiteralPath $source)) {
                        $firstDirectory = Get-ChildItem -LiteralPath $extractDir -Directory | Select-Object -First 1
                        if ($null -ne $firstDirectory) {
                            $source = $firstDirectory.FullName
                        }
                    }
                    if ([string]::IsNullOrWhiteSpace($source) -or -not (Test-Path -LiteralPath $source)) {
                        throw 'Could not locate expanded application directory.'
                    }

                    $robocopyArgs = @($source, $AppHome, '/MIR', '/R:5', '/W:1', '/XD', '.portable-data')
                    & robocopy @robocopyArgs | Out-File -LiteralPath $logPath -Encoding UTF8 -Append
                    $code = $LASTEXITCODE
                    if ($code -gt 7) {
                        throw "Robocopy failed with exit code $code."
                    }
                    Start-Process -FilePath $ExePath
                } catch {
                    $_ | Out-File -LiteralPath $logPath -Encoding UTF8 -Append
                }
                """;
    }

    private void openFile(Path file) throws IOException {
        if (!Desktop.isDesktopSupported()) {
            throw new IOException("当前系统不支持自动打开文件: " + file);
        }
        Desktop.getDesktop().open(file.toFile());
    }

    private boolean isPortableZip(ReleaseAsset asset) {
        String name = lowerName(asset);
        return name.endsWith(".zip") && name.contains("portable");
    }

    private int installerPreference(ReleaseAsset asset) {
        String name = lowerName(asset);
        if (name.contains("windows-x64")) {
            return 0;
        }
        if (name.contains("windows")) {
            return 1;
        }
        return 2;
    }

    private String lowerName(ReleaseAsset asset) {
        return asset.name().toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String name) {
        String safeName = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safeName.isBlank() ? "jksviewer-update.bin" : safeName;
    }

    private String userAgent() {
        return "JKS-Viewer/" + metadata.version();
    }
}
