package ru.russianiptv.player;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class M3uParser {
    private static final Pattern ATTR = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    public static List<Channel> parse(String text) throws Exception {
        List<Channel> result = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new StringReader(text));
        String line, ext = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF")) { ext = line; continue; }
            if (ext != null && !line.isEmpty() && !line.startsWith("#")) {
                String[] parts = ext.split(",", 2);
                String name = parts.length > 1 ? parts[1].trim() : "Без названия";
                result.add(new Channel(attr(ext, "tvg-id"), name, line,
                        attr(ext, "tvg-logo"), attr(ext, "group-title", "Общие")));
                ext = null;
            }
        }
        return result;
    }

    private static String attr(String line, String name) { return attr(line, name, ""); }
    private static String attr(String line, String name, String fallback) {
        Matcher m = ATTR.matcher(line);
        while (m.find()) if (m.group(1).equals(name)) return m.group(2);
        return fallback;
    }
}
