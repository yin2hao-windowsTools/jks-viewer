package cn.silentcrane.jksviewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.silentcrane.jksviewer.model.AliasInfo;
import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class KeystoreDocumentTest {
    @TempDir
    Path tempDir;

    @Test
    void createsAddsVerifiesDeletesAndReloadsJksAlias() throws Exception {
        Path keystorePath = tempDir.resolve("release.jks");
        char[] storePassword = "storepass".toCharArray();

        KeystoreDocument document = KeystoreDocument.create(keystorePath, storePassword);
        document.addGeneratedAlias(request("release", "aliaspass"));
        document.save();

        KeystoreDocument reloaded = KeystoreDocument.load(keystorePath, storePassword);
        assertTrue(List.of("JKS", "CaseExactJKS").contains(reloaded.storeType()));
        assertEquals(1, reloaded.listAliases().size());
        assertTrue(reloaded.verifyAliasPassword("release", "aliaspass".toCharArray()));
        assertFalse(reloaded.verifyAliasPassword("release", "wrongpass".toCharArray()));

        reloaded.deleteAlias("release");
        reloaded.save();

        KeystoreDocument empty = KeystoreDocument.load(keystorePath, storePassword);
        assertEquals(0, empty.listAliases().size());
    }

    @Test
    void loadsPkcs12AndPreservesItsStoreTypeWhenSaving() throws Exception {
        Path path = tempDir.resolve("release.p12");
        char[] storePassword = "storepass".toCharArray();
        writeKeystoreWithPrivateKey(path, "PKCS12", storePassword, "release", "aliaspass".toCharArray());

        KeystoreDocument document = KeystoreDocument.load(path, storePassword);
        assertEquals("PKCS12", document.storeType());
        assertEquals(1, document.listAliases().size());
        assertTrue(document.verifyAliasPassword("release", "aliaspass".toCharArray()));

        document.addGeneratedAlias(request("upload", "uploadpass"));
        document.save();

        KeystoreDocument reloaded = KeystoreDocument.load(path, storePassword);
        assertEquals("PKCS12", reloaded.storeType());
        assertEquals(2, reloaded.listAliases().size());
        assertTrue(reloaded.verifyAliasPassword("upload", "uploadpass".toCharArray()));
    }

    @Test
    void loadsJceksCertificateEntries() throws Exception {
        Path path = tempDir.resolve("certs.jceks");
        char[] storePassword = "storepass".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, storePassword);
        X509Certificate certificate = SelfSignedCertificateFactory.createCertificate(
                SelfSignedCertificateFactory.generateRsaKeyPair(2048),
                "Certificate Only",
                "Test Org",
                "Mobile",
                "Shanghai",
                "Shanghai",
                "CN",
                10
        );
        keyStore.setCertificateEntry("certificate", certificate);
        try (OutputStream output = java.nio.file.Files.newOutputStream(path)) {
            keyStore.store(output, storePassword);
        }

        KeystoreDocument document = KeystoreDocument.load(path, storePassword);
        assertEquals("JCEKS", document.storeType());
        List<AliasInfo> aliases = document.listAliases();
        assertEquals(1, aliases.size());
        assertEquals("证书", aliases.get(0).getKind());
        assertTrue(document.verifyAliasPassword("certificate", new char[0]));
    }

    @Test
    void reportsWrongStorePasswordAsLoadFailure() throws Exception {
        Path path = tempDir.resolve("release.jks");
        char[] storePassword = "storepass".toCharArray();
        KeystoreDocument document = KeystoreDocument.create(path, storePassword);
        document.addGeneratedAlias(request("release", "aliaspass"));
        document.save();

        Exception ex = assertThrows(Exception.class, () -> KeystoreDocument.load(path, "wrongpass".toCharArray()));
        assertNotNull(ex.getMessage());
    }

    @Test
    void supportsEmptyStorePasswordWhenExistingKeystoreUsesIt() throws Exception {
        Path path = tempDir.resolve("empty-pass.jks");
        KeystoreDocument document = KeystoreDocument.create(path, new char[0]);
        document.addGeneratedAlias(request("release", "aliaspass"));
        document.save();

        KeystoreDocument reloaded = KeystoreDocument.load(path, new char[0]);
        assertEquals(1, reloaded.listAliases().size());
        assertTrue(reloaded.verifyAliasPassword("release", "aliaspass".toCharArray()));
    }

    @Test
    void generatedAliasUsesAndroidStudioStyleCertificateIdentity() throws Exception {
        Path path = tempDir.resolve("android-studio-style.jks");
        KeystoreDocument document = KeystoreDocument.create(path, "storepass".toCharArray());

        document.addGeneratedAlias(new GeneratedAliasRequest(
                "myvideos",
                "aliaspass".toCharArray(),
                "yin2hao",
                "",
                "",
                "",
                "",
                "",
                2048,
                50
        ));

        AliasInfo alias = document.listAliases().get(0);
        assertEquals("myvideos", alias.getAlias());
        assertEquals("CN=yin2hao", alias.getSubject());
        assertEquals("CN=yin2hao", alias.getIssuer());
        assertEquals("1", alias.getSerialNumber());
        assertEquals(LocalDate.now(), alias.getNotBefore());
    }

    @Test
    void generatedAliasDoesNotAddDefaultCertificateFields() throws Exception {
        Path path = tempDir.resolve("minimal-dn.jks");
        KeystoreDocument document = KeystoreDocument.create(path, "storepass".toCharArray());

        document.addGeneratedAlias(new GeneratedAliasRequest(
                "release",
                "aliaspass".toCharArray(),
                "",
                "yin2hao",
                "",
                "",
                "",
                "",
                2048,
                50
        ));

        AliasInfo alias = document.listAliases().get(0);
        assertEquals("O=yin2hao", alias.getSubject());
        assertEquals("O=yin2hao", alias.getIssuer());
    }

    @Test
    void saveDoesNotOverwriteNeighborTmpFile() throws Exception {
        Path path = tempDir.resolve("release.jks");
        Path neighborTmp = tempDir.resolve("release.jks.tmp");
        java.nio.file.Files.writeString(neighborTmp, "keep me", StandardCharsets.UTF_8);

        KeystoreDocument document = KeystoreDocument.create(path, "storepass".toCharArray());
        document.addGeneratedAlias(request("release", "aliaspass"));
        document.save();

        assertEquals("keep me", java.nio.file.Files.readString(neighborTmp, StandardCharsets.UTF_8));
        assertEquals(1, KeystoreDocument.load(path, "storepass".toCharArray()).listAliases().size());
    }

    private GeneratedAliasRequest request(String alias, String password) {
        return new GeneratedAliasRequest(
                alias,
                password.toCharArray(),
                "Android Release",
                "Test Org",
                "Mobile",
                "Shanghai",
                "Shanghai",
                "CN",
                2048,
                25
        );
    }

    private void writeKeystoreWithPrivateKey(
            Path path,
            String storeType,
            char[] storePassword,
            String alias,
            char[] aliasPassword
    ) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(storeType);
        keyStore.load(null, storePassword);
        KeyPair keyPair = SelfSignedCertificateFactory.generateRsaKeyPair(2048);
        Certificate certificate = SelfSignedCertificateFactory.createCertificate(
                keyPair,
                "Android Release",
                "Test Org",
                "Mobile",
                "Shanghai",
                "Shanghai",
                "CN",
                25
        );
        keyStore.setKeyEntry(alias, keyPair.getPrivate(), aliasPassword, new Certificate[]{certificate});
        try (OutputStream output = java.nio.file.Files.newOutputStream(path)) {
            keyStore.store(output, storePassword);
        }
    }
}
