package cn.silentcrane.jksviewer.service.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionComparator {
    private VersionComparator() {
    }

    public static boolean isNewer(String candidateVersion, String currentVersion) {
        return compare(candidateVersion, currentVersion) > 0;
    }

    public static int compare(String left, String right) {
        Version leftVersion = Version.parse(left);
        Version rightVersion = Version.parse(right);
        int numericSize = Math.max(leftVersion.numbers().size(), rightVersion.numbers().size());
        for (int index = 0; index < numericSize; index++) {
            int leftPart = index < leftVersion.numbers().size() ? leftVersion.numbers().get(index) : 0;
            int rightPart = index < rightVersion.numbers().size() ? rightVersion.numbers().get(index) : 0;
            int compared = Integer.compare(leftPart, rightPart);
            if (compared != 0) {
                return compared;
            }
        }
        if (leftVersion.suffix().equals(rightVersion.suffix())) {
            return 0;
        }
        if (leftVersion.suffix().isBlank()) {
            return 1;
        }
        if (rightVersion.suffix().isBlank()) {
            return -1;
        }
        return leftVersion.suffix().compareTo(rightVersion.suffix());
    }

    static String normalizeVersionText(String version) {
        String text = version == null ? "" : version.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int buildMetadataIndex = text.indexOf('+');
        if (buildMetadataIndex >= 0) {
            text = text.substring(0, buildMetadataIndex);
        }
        return text;
    }

    private record Version(List<Integer> numbers, String suffix) {
        private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+)(.*)$");

        static Version parse(String rawVersion) {
            String text = normalizeVersionText(rawVersion);
            String suffix = "";
            int suffixIndex = text.indexOf('-');
            if (suffixIndex >= 0) {
                suffix = text.substring(suffixIndex + 1).toLowerCase(Locale.ROOT);
                text = text.substring(0, suffixIndex);
            }

            List<Integer> numbers = new ArrayList<>();
            for (String part : text.split("[._]")) {
                if (part.isBlank()) {
                    continue;
                }
                Matcher matcher = LEADING_NUMBER.matcher(part);
                if (matcher.matches()) {
                    numbers.add(Integer.parseInt(matcher.group(1)));
                    if (suffix.isBlank() && !matcher.group(2).isBlank()) {
                        suffix = matcher.group(2).toLowerCase(Locale.ROOT);
                    }
                }
            }
            if (numbers.isEmpty()) {
                numbers.add(0);
            }
            return new Version(List.copyOf(numbers), suffix);
        }
    }
}
