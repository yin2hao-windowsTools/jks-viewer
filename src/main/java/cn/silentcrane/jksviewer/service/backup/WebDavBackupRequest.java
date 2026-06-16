package cn.silentcrane.jksviewer.service.backup;

import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;

public final class WebDavBackupRequest {
    private final Path sourceFile;
    private final URI serverUri;
    private final String remoteDirectory;
    private final String username;
    private final char[] password;

    public WebDavBackupRequest(
            Path sourceFile,
            URI serverUri,
            String remoteDirectory,
            String username,
            char[] password
    ) {
        this.sourceFile = sourceFile;
        this.serverUri = serverUri;
        this.remoteDirectory = remoteDirectory == null ? "" : remoteDirectory;
        this.username = username == null ? "" : username;
        this.password = password == null ? new char[0] : password.clone();
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public URI serverUri() {
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

    public void clearPassword() {
        Arrays.fill(password, '\0');
    }
}
