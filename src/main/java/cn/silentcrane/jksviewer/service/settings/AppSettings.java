package cn.silentcrane.jksviewer.service.settings;

public final class AppSettings {
    private final WebDavSettings webDav;

    public AppSettings(WebDavSettings webDav) {
        this.webDav = webDav == null ? WebDavSettings.empty() : webDav;
    }

    public static AppSettings empty() {
        return new AppSettings(WebDavSettings.empty());
    }

    public WebDavSettings webDav() {
        return webDav;
    }
}
