package cn.silentcrane.jksviewer.model;

import java.util.Arrays;

public final class GeneratedAliasRequest {
    private final String alias;
    private final char[] aliasPassword;
    private final String commonName;
    private final String organization;
    private final String organizationUnit;
    private final String locality;
    private final String state;
    private final String country;
    private final int rsaKeySize;
    private final int validityYears;

    public GeneratedAliasRequest(
            String alias,
            char[] aliasPassword,
            String commonName,
            String organization,
            String organizationUnit,
            String locality,
            String state,
            String country,
            int rsaKeySize,
            int validityYears
    ) {
        this.alias = alias;
        this.aliasPassword = aliasPassword.clone();
        this.commonName = commonName;
        this.organization = organization;
        this.organizationUnit = organizationUnit;
        this.locality = locality;
        this.state = state;
        this.country = country;
        this.rsaKeySize = rsaKeySize;
        this.validityYears = validityYears;
    }

    public String alias() {
        return alias;
    }

    public char[] aliasPassword() {
        return aliasPassword.clone();
    }

    public String commonName() {
        return commonName;
    }

    public String organization() {
        return organization;
    }

    public String organizationUnit() {
        return organizationUnit;
    }

    public String locality() {
        return locality;
    }

    public String state() {
        return state;
    }

    public String country() {
        return country;
    }

    public int rsaKeySize() {
        return rsaKeySize;
    }

    public int validityYears() {
        return validityYears;
    }

    public void clearPasswords() {
        Arrays.fill(aliasPassword, '\0');
    }
}
