package cn.silentcrane.jksviewer.service;

import cn.silentcrane.jksviewer.model.AliasInfo;
import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public final class KeystoreDocument {
    private static final String STORE_TYPE = "JKS";

    private final Path path;
    private final char[] storePassword;
    private final KeyStore keyStore;

    private KeystoreDocument(Path path, char[] storePassword, KeyStore keyStore) {
        this.path = path;
        this.storePassword = storePassword.clone();
        this.keyStore = keyStore;
    }

    public static KeystoreDocument load(Path path, char[] storePassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(STORE_TYPE);
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, storePassword);
        }
        return new KeystoreDocument(path, storePassword, keyStore);
    }

    public static KeystoreDocument create(Path path, char[] storePassword) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(STORE_TYPE);
        keyStore.load(null, storePassword);
        return new KeystoreDocument(path, storePassword, keyStore);
    }

    public Path path() {
        return path;
    }

    public List<AliasInfo> listAliases() throws Exception {
        List<String> names = new ArrayList<>();
        Enumeration<String> enumeration = keyStore.aliases();
        while (enumeration.hasMoreElements()) {
            names.add(enumeration.nextElement());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);

        List<AliasInfo> items = new ArrayList<>();
        for (String alias : names) {
            items.add(describeAlias(alias));
        }
        return items;
    }

    public boolean verifyAliasPassword(String alias, char[] password) throws Exception {
        ensureAliasExists(alias);
        if (keyStore.isCertificateEntry(alias)) {
            return true;
        }
        try {
            Key key = keyStore.getKey(alias, password);
            return key != null;
        } catch (UnrecoverableKeyException ex) {
            return false;
        }
    }

    public void addGeneratedAlias(GeneratedAliasRequest request) throws Exception {
        validateAliasName(request.alias());
        if (keyStore.containsAlias(request.alias())) {
            throw new IllegalArgumentException("alias 已存在: " + request.alias());
        }
        if (request.aliasPassword().length < 6) {
            throw new IllegalArgumentException("alias 密码至少需要 6 位。");
        }

        KeyPair keyPair = SelfSignedCertificateFactory.generateRsaKeyPair(request.rsaKeySize());
        X509Certificate certificate = SelfSignedCertificateFactory.createCertificate(
                keyPair,
                request.commonName(),
                request.organization(),
                request.organizationUnit(),
                request.locality(),
                request.state(),
                request.country(),
                request.validityYears()
        );

        KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(
                keyPair.getPrivate(),
                new Certificate[]{certificate}
        );
        char[] password = request.aliasPassword();
        try {
            keyStore.setEntry(request.alias(), entry, new KeyStore.PasswordProtection(password));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void deleteAlias(String alias) throws Exception {
        ensureAliasExists(alias);
        keyStore.deleteEntry(alias);
    }

    public void save() throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            keyStore.store(output, storePassword);
        }
        try {
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void clearPassword() {
        Arrays.fill(storePassword, '\0');
    }

    private AliasInfo describeAlias(String alias) throws Exception {
        String kind = keyStore.isKeyEntry(alias) ? "私钥" : keyStore.isCertificateEntry(alias) ? "证书" : "未知";
        LocalDate createdAt = toLocalDate(keyStore.getCreationDate(alias));
        Certificate certificate = keyStore.getCertificate(alias);
        if (certificate instanceof X509Certificate x509) {
            return new AliasInfo(
                    alias,
                    kind,
                    createdAt,
                    x509.getSubjectX500Principal().getName(),
                    x509.getIssuerX500Principal().getName(),
                    toLocalDate(x509.getNotBefore()),
                    toLocalDate(x509.getNotAfter()),
                    x509.getSerialNumber().toString(16).toUpperCase(Locale.ROOT),
                    x509.getSigAlgName(),
                    x509.getPublicKey().getAlgorithm()
            );
        }
        return new AliasInfo(alias, kind, createdAt, "-", "-", null, null, "-", "-", "-");
    }

    private void ensureAliasExists(String alias) throws Exception {
        if (!keyStore.containsAlias(alias)) {
            throw new IllegalArgumentException("alias 不存在: " + alias);
        }
    }

    private void validateAliasName(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias 不能为空。");
        }
        if (!alias.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("alias 只能包含字母、数字、点、下划线和短横线，长度不超过 64。");
        }
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
