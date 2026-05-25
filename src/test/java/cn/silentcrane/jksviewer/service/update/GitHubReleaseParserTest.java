package cn.silentcrane.jksviewer.service.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

final class GitHubReleaseParserTest {
    @Test
    void parsesLatestReleaseAndAssets() {
        String json = """
                {
                  "tag_name": "v1.2.3",
                  "html_url": "https://github.com/yin2hao-windowsTools/jks-viewer/releases/tag/v1.2.3",
                  "prerelease": false,
                  "assets": [
                    {
                      "name": "jks-viewer-1.2.3-windows-x64.exe",
                      "size": 1024,
                      "browser_download_url": "https://github.com/yin2hao-windowsTools/jks-viewer/releases/download/v1.2.3/jks-viewer.exe"
                    },
                    {
                      "name": "jks-viewer-1.2.3-windows-portable.zip",
                      "size": 2048,
                      "browser_download_url": "https://github.com/yin2hao-windowsTools/jks-viewer/releases/download/v1.2.3/jks-viewer.zip"
                    }
                  ]
                }
                """;

        ReleaseInfo release = GitHubReleaseParser.parse(json);

        assertEquals("v1.2.3", release.tagName());
        assertEquals("1.2.3", release.version());
        assertFalse(release.prerelease());
        assertEquals(2, release.assets().size());
        assertEquals("jks-viewer-1.2.3-windows-portable.zip", release.assets().get(1).name());
        assertEquals(2048, release.assets().get(1).size());
    }

    @Test
    void unescapesReleaseFields() {
        String json = """
                {
                  "tag_name": "v1.2.3-beta",
                  "html_url": "https://example.com/releases/tag/v1.2.3-beta",
                  "prerelease": true,
                  "assets": [
                    {
                      "name": "jks-viewer-1.2.3-\\u0062eta-windows-portable.zip",
                      "browser_download_url": "https://example.com/download.zip"
                    }
                  ]
                }
                """;

        ReleaseInfo release = GitHubReleaseParser.parse(json);

        assertEquals("1.2.3-beta", release.version());
        assertEquals("jks-viewer-1.2.3-beta-windows-portable.zip", release.assets().get(0).name());
    }
}
