package cn.silentcrane.jksviewer.service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class SelfSignedCertificateFactory {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SelfSignedCertificateFactory() {
    }

    public static KeyPair generateRsaKeyPair(int keySize) throws Exception {
        if (keySize < 2048) {
            throw new IllegalArgumentException("RSA 位数不能小于 2048。");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize, RANDOM);
        return generator.generateKeyPair();
    }

    public static X509Certificate createCertificate(
            KeyPair keyPair,
            String commonName,
            String organization,
            String organizationUnit,
            String locality,
            String state,
            String country,
            int validityYears
    ) throws Exception {
        ensureProvider();
        X500Name subject = buildName(commonName, organization, organizationUnit, locality, state, country);
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));
        Date notAfter = Date.from(now.plus(Math.max(1, validityYears) * 365L, ChronoUnit.DAYS));
        BigInteger serial = new BigInteger(160, RANDOM).abs();

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(signer));
    }

    private static X500Name buildName(
            String commonName,
            String organization,
            String organizationUnit,
            String locality,
            String state,
            String country
    ) {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);
        builder.addRDN(BCStyle.CN, fallback(commonName, "Android App Signing"));
        addOptional(builder, BCStyle.O, organization);
        addOptional(builder, BCStyle.OU, organizationUnit);
        addOptional(builder, BCStyle.L, locality);
        addOptional(builder, BCStyle.ST, state);
        addOptional(builder, BCStyle.C, normalizeCountry(country));
        return builder.build();
    }

    private static void addOptional(X500NameBuilder builder, org.bouncycastle.asn1.ASN1ObjectIdentifier oid, String value) {
        if (value != null && !value.isBlank()) {
            builder.addRDN(oid, value.trim());
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "CN";
        }
        return country.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
