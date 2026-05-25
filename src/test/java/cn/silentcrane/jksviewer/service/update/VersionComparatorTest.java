package cn.silentcrane.jksviewer.service.update;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VersionComparatorTest {
    @Test
    void comparesSemanticVersionsWithTagPrefix() {
        assertTrue(VersionComparator.isNewer("v1.2.0", "1.1.9"));
        assertFalse(VersionComparator.isNewer("v1.2.0", "1.2.0"));
        assertFalse(VersionComparator.isNewer("v1.2", "1.2.0"));
    }

    @Test
    void treatsStableReleaseAsNewerThanPrereleaseWithSameNumbers() {
        assertTrue(VersionComparator.isNewer("1.0.0", "1.0.0-alpha"));
        assertFalse(VersionComparator.isNewer("1.0.0-alpha", "1.0.0"));
    }

    @Test
    void ignoresBuildMetadataWhenComparing() {
        assertFalse(VersionComparator.isNewer("1.0.0+5", "1.0.0"));
        assertTrue(VersionComparator.isNewer("1.0.1+5", "1.0.0"));
    }
}
