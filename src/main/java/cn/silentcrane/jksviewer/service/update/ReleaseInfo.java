package cn.silentcrane.jksviewer.service.update;

import java.net.URI;
import java.util.List;

public record ReleaseInfo(
        String tagName,
        String version,
        URI htmlUri,
        boolean prerelease,
        List<ReleaseAsset> assets
) {
    public ReleaseInfo {
        assets = assets == null ? List.of() : List.copyOf(assets);
    }
}
