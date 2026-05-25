package cn.silentcrane.jksviewer.service.update;

public record UpdateCheckResult(
        String currentVersion,
        ReleaseInfo latestRelease,
        boolean updateAvailable
) {
}
