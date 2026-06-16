package cn.silentcrane.jksviewer.service.settings;

import java.util.Arrays;

public final class WebDavSettings {
    private final String serverUri;
    private final String remoteDirectory;
    private final String username;
    private final char[] password;

    public WebDavSettings(String serverUri, String remoteDirectory, String username, char[] password) {
        this.serverUri = normalize(serverUri);
        this.remoteDirectory = normalize(remoteDirectory);
        this.username = normalize(username);
        this.password = password == null ? new char[0] : password.clone();
    }

    public static WebDavSettings empty() {
        return new WebDavSettings("", "", "", new char[0]);
    }

    public String serverUri() {
        return serverUri;
    }

    public String remoteDirectory() {
        return remoteDirectory;
    }

    public String username() {
        return username;
    }

    public char[] password() {
        return password.clone();
    }

    public boolean isEmpty() {
        return serverUri.isBlank()
                && remoteDirectory.isBlank()
                && username.isBlank()
                && password.length == 0;
    }

    public void clearPassword() {
        Arrays.fill(password, '\0');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
