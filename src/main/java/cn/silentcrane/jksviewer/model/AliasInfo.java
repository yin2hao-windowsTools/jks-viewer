package cn.silentcrane.jksviewer.model;

import java.time.LocalDate;
import javafx.css.PseudoClass;

public final class AliasInfo {
    public static final PseudoClass EXPIRED_PSEUDO_CLASS = PseudoClass.getPseudoClass("expired");
    public static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    private final String alias;
    private final String kind;
    private final LocalDate createdAt;
    private final String subject;
    private final String issuer;
    private final LocalDate notBefore;
    private final LocalDate notAfter;
    private final String serialNumber;
    private final String signatureAlgorithm;
    private final String publicKeyAlgorithm;

    public AliasInfo(
            String alias,
            String kind,
            LocalDate createdAt,
            String subject,
            String issuer,
            LocalDate notBefore,
            LocalDate notAfter,
            String serialNumber,
            String signatureAlgorithm,
            String publicKeyAlgorithm
    ) {
        this.alias = alias;
        this.kind = kind;
        this.createdAt = createdAt;
        this.subject = subject;
        this.issuer = issuer;
        this.notBefore = notBefore;
        this.notAfter = notAfter;
        this.serialNumber = serialNumber;
        this.signatureAlgorithm = signatureAlgorithm;
        this.publicKeyAlgorithm = publicKeyAlgorithm;
    }

    public String getAlias() {
        return alias;
    }

    public String getKind() {
        return kind;
    }

    public LocalDate getCreatedAt() {
        return createdAt;
    }

    public String getSubject() {
        return subject;
    }

    public String getIssuer() {
        return issuer;
    }

    public LocalDate getNotBefore() {
        return notBefore;
    }

    public LocalDate getNotAfter() {
        return notAfter;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public String getPublicKeyAlgorithm() {
        return publicKeyAlgorithm;
    }

    public String getValidityText() {
        if (notBefore == null || notAfter == null) {
            return "-";
        }
        return notBefore + " 至 " + notAfter;
    }

    public boolean isExpired() {
        return notAfter != null && notAfter.isBefore(LocalDate.now());
    }
}
