package cn.silentcrane.jksviewer.service.update;

import java.net.URI;

public record ReleaseAsset(String name, URI downloadUri, long size) {
}
