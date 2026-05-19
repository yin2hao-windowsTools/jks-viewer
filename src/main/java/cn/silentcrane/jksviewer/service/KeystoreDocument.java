package cn.silentcrane.jksviewer.service;

import cn.silentcrane.jksviewer.model.AliasInfo;
import cn.silentcrane.jksviewer.model.GeneratedAliasRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.KeyStore;
import java.security.Security;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class KeystoreDocument {
    private static final String DEFAULT_STORE_TYPE = "JKS";
    private static final List<String> FALLBACK_STORE_TYPES = List.of(
            "JKS",
            "PKCS12",
            "JCEKS",
            "CaseExactJKS",
            "BKS",
            "BKS-V1",
            "UBER",
            "BCFKS"
    );

    private final Path path;
    private final char[] storePassword;
    private final KeyStore keyStore;
    private final String storeType;

    private KeystoreDocument(Path path, char[] storePassword, KeyStore keyStore, String storeType) {
        this.path = path;
        this.storePassword = storePassword.clone();
        this.keyStore = keyStore;
        this.storeType = storeType;
    }

    public static KeystoreDocument load(Path path, char[] storePassword) throws Exception {
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("请选择一个密钥库文件。");
        }
        if (Files.size(path) == 0L) {
            throw new IOException("密钥库文件为空。");
        }

        ensureOptionalProviders();
        char[] password = normalizePassword(storePassword);
        List<String> candidates = candidateStoreTypes(path);
        Exception lastFailure = null;
        List<String> attempted = new ArrayList<>();
        for (String type : candidates) {
            attempted.add(type);
            try (InputStream input = Files.newInputStream(path)) {
                KeyStore keyStore = KeyStore.getInstance(type);
                keyStore.load(input, password);
                KeystoreDocument document = new KeystoreDocument(path, password, keyStore, keyStore.getType());
                Arrays.fill(password, '\0');
                return document;
            } catch (IOException | GeneralSecurityException | RuntimeException ex) {
                lastFailure = ex;
            }
        }
        Arrays.fill(password, '\0');
        throw new IOException(
                "无法打开密钥库。请确认库密码正确，且文件格式为 JKS、PKCS12、JCEKS、BKS、UBER 或 BCFKS。已尝试: "
                        + String.join(", ", attempted),
                lastFailure
        );
    }

    public static KeystoreDocument create(Path path, char[] storePassword) throws Exception {
        return create(path, storePassword, DEFAULT_STORE_TYPE);
    }

    public static KeystoreDocument create(Path path, char[] storePassword, String storeType) throws Exception {
        ensureOptionalProviders();
        char[] password = normalizePassword(storePassword);
        KeyStore keyStore = KeyStore.getInstance(normalizeStoreType(storeType));
        keyStore.load(null, password);
        KeystoreDocument document = new KeystoreDocument(path, password, keyStore, keyStore.getType());
        Arrays.fill(password, '\0');
        return document;
    }

    public Path path() {
        return path;
    }

    public String storeType() {
        return storeType;
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
        char[] normalizedPassword = normalizePassword(password);
        try {
            Key key = keyStore.getKey(alias, normalizedPassword);
            return key != null;
        } catch (UnrecoverableKeyException ex) {
            return false;
        } finally {
            Arrays.fill(normalizedPassword, '\0');
        }
    }

    public void addGeneratedAlias(GeneratedAliasRequest request) throws Exception {
        validateAliasName(request.alias());
        if (keyStore.containsAlias(request.alias())) {
            throw new IllegalArgumentException("alias 已存在: " + request.alias());
        }
        char[] password = request.aliasPassword();
        validateEntryPassword(password);

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

        try {
            keyStore.setKeyEntry(request.alias(), keyPair.getPrivate(), password, new Certificate[]{certificate});
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
        Path absolutePath = path.toAbsolutePath();
        Path tempDirectory = absolutePath.getParent();
        Path temp = Files.createTempFile(tempDirectory, absolutePath.getFileName().toString(), ".tmp");
        try (OutputStream output = Files.newOutputStream(temp)) {
            keyStore.store(output, storePassword);
        }
        boolean moved = false;
        try {
            Files.move(temp, absolutePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } catch (IOException ex) {
            Files.move(temp, absolutePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    public void reload() throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            keyStore.load(input, storePassword);
        }
    }

    public void clearPassword() {
        Arrays.fill(storePassword, '\0');
    }

    private AliasInfo describeAlias(String alias) throws Exception {
        String kind = aliasKind(alias);
        LocalDate createdAt = toLocalDate(keyStore.getCreationDate(alias));
        Certificate certificate = primaryCertificate(alias);
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

    private Certificate primaryCertificate(String alias) throws KeyStoreException {
        Certificate[] chain = keyStore.getCertificateChain(alias);
        if (chain != null && chain.length > 0) {
            return chain[0];
        }
        return keyStore.getCertificate(alias);
    }

    private String aliasKind(String alias) throws KeyStoreException {
        if (keyStore.isCertificateEntry(alias)) {
            return "证书";
        }
        if (keyStore.isKeyEntry(alias)) {
            if (keyStore.entryInstanceOf(alias, KeyStore.PrivateKeyEntry.class)) {
                return "私钥";
            }
            if (keyStore.entryInstanceOf(alias, KeyStore.SecretKeyEntry.class)) {
                return "密钥";
            }
            return "密钥";
        }
        return "未知";
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

    private void validateEntryPassword(char[] password) {
        if (password == null || password.length < 6) {
            throw new IllegalArgumentException("alias 密码至少需要 6 位。");
        }
    }

    private LocalDate toLocalDate(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static List<String> candidateStoreTypes(Path path) throws IOException {
        Set<String> candidates = new LinkedHashSet<>();
        byte[] header = readHeader(path);
        if (header.length >= 4) {
            int magic = ((header[0] & 0xFF) << 24)
                    | ((header[1] & 0xFF) << 16)
                    | ((header[2] & 0xFF) << 8)
                    | (header[3] & 0xFF);
            if (magic == 0xFEEDFEED) {
                candidates.add("CaseExactJKS");
                candidates.add("JKS");
            } else if (magic == 0xCECECECE) {
                candidates.add("JCEKS");
            } else if ((header[0] & 0xFF) == 0x30) {
                candidates.add("PKCS12");
            }
        }
        candidates.addAll(FALLBACK_STORE_TYPES);
        return new ArrayList<>(candidates);
    }

    private static byte[] readHeader(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(4);
        }
    }

    private static char[] normalizePassword(char[] password) {
        return password == null ? new char[0] : password.clone();
    }

    private static String normalizeStoreType(String storeType) {
        if (storeType == null || storeType.isBlank()) {
            return DEFAULT_STORE_TYPE;
        }
        return storeType.trim();
    }

    private static void ensureOptionalProviders() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
