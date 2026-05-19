package cn.silentcrane.jksviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KeystoreDocumentTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAddsVerifiesDeletesAndReloadsJksAlias() throws Exception {
        Path keystorePath = tempDir.resolve("release.jks");
        char[] storePassword = "storepass".toCharArray();
        char[] aliasPassword = "aliaspass".toCharArray();

        KeystoreDocument document = KeystoreDocument.create(keystorePath, storePassword);
        document.addGeneratedAlias(new GeneratedAliasRequest(
                "release",
                aliasPassword,
                "Android Release",
                "Test Org",
                "Mobile",
                "Shanghai",
                "Shanghai",
                "CN",
                2048,
                25
        ));
        document.save();

        KeystoreDocument reloaded = KeystoreDocument.load(keystorePath, storePassword);
        assertEquals(1, reloaded.listAliases().size());
        assertTrue(reloaded.verifyAliasPassword("release", "aliaspass".toCharArray()));
        assertFalse(reloaded.verifyAliasPassword("release", "wrongpass".toCharArray()));

        reloaded.deleteAlias("release");
        reloaded.save();

        KeystoreDocument empty = KeystoreDocument.load(keystorePath, storePassword);
        assertEquals(0, empty.listAliases().size());
    }
}
