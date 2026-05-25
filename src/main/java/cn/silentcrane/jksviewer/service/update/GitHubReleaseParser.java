package cn.silentcrane.jksviewer.service.update;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubReleaseParser {
    private GitHubReleaseParser() {
    }

    static ReleaseInfo parse(String json) {
        String topLevel = topLevelSection(json);
        String tagName = stringField(topLevel, "tag_name")
                .orElseThrow(() -> new IllegalArgumentException("GitHub Release response is missing tag_name."));
        String htmlUrl = stringField(topLevel, "html_url")
                .orElseThrow(() -> new IllegalArgumentException("GitHub Release response is missing html_url."));
        boolean prerelease = booleanField(topLevel, "prerelease").orElse(false);
        return new ReleaseInfo(
                tagName,
                VersionComparator.normalizeVersionText(tagName),
                URI.create(htmlUrl),
                prerelease,
                parseAssets(json)
        );
    }

    private static String topLevelSection(String json) {
        int assetsIndex = json.indexOf("\"assets\"");
        return assetsIndex >= 0 ? json.substring(0, assetsIndex) : json;
    }

    private static List<ReleaseAsset> parseAssets(String json) {
        Optional<String> assetsArray = arrayField(json, "assets");
        if (assetsArray.isEmpty()) {
            return List.of();
        }
        List<ReleaseAsset> assets = new ArrayList<>();
        for (String object : splitObjects(assetsArray.get())) {
            Optional<String> name = stringField(object, "name");
            Optional<String> downloadUrl = stringField(object, "browser_download_url");
            if (name.isPresent() && downloadUrl.isPresent()) {
                assets.add(new ReleaseAsset(
                        name.get(),
                        URI.create(downloadUrl.get()),
                        longField(object, "size").orElse(0L)
                ));
            }
        }
        return List.copyOf(assets);
    }

    private static Optional<String> stringField(String json, String name) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(unescapeJsonString(matcher.group(1)));
    }

    private static Optional<Boolean> booleanField(String json, String name) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(true|false)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(Boolean.parseBoolean(matcher.group(1))) : Optional.empty();
    }

    private static Optional<Long> longField(String json, String name) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? Optional.of(Long.parseLong(matcher.group(1))) : Optional.empty();
    }

    private static Optional<String> arrayField(String json, String name) {
        int keyIndex = json.indexOf("\"" + name + "\"");
        if (keyIndex < 0) {
            return Optional.empty();
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int openIndex = json.indexOf('[', colonIndex);
        if (colonIndex < 0 || openIndex < 0) {
            return Optional.empty();
        }
        int closeIndex = matchingIndex(json, openIndex, '[', ']');
        if (closeIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(json.substring(openIndex + 1, closeIndex));
    }

    private static int matchingIndex(String text, int openIndex, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = openIndex; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static List<String> splitObjects(String arrayContent) {
        List<String> objects = new ArrayList<>();
        int objectStart = -1;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < arrayContent.length(); index++) {
            char ch = arrayContent.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                if (depth == 0) {
                    objectStart = index;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    objects.add(arrayContent.substring(objectStart, index + 1));
                    objectStart = -1;
                }
            }
        }
        return objects;
    }

    private static String unescapeJsonString(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch != '\\' || index + 1 >= text.length()) {
                result.append(ch);
                continue;
            }
            char escaped = text.charAt(++index);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (index + 4 >= text.length()) {
                        result.append("\\u");
                    } else {
                        String hex = text.substring(index + 1, index + 5);
                        result.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }
}
