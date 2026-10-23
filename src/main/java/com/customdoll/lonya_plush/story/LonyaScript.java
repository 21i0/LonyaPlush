package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.DollMod;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared, immutable story: the server validates the same steps the client presents. */
public final class LonyaScript {
    /** minimumMillis keeps the 1.1.0 resource format; it is advisory autoplay timing only. */
    public record Line(String speaker, String expression, String text, int minimumMillis) { }
    public record Chapter(String title, String summary, List<Line> lines) { }
    public static final List<Chapter> CHAPTERS = load();
    public static Chapter chapter(int index) { return CHAPTERS.get(index); }
    private static List<Chapter> load() {
        try (var input = LonyaScript.class.getResourceAsStream("/assets/" + DollMod.MODID + "/story/chapters.json")) {
            if (input == null) throw new IllegalStateException("Missing Lonya story");
            var json = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
            List<Chapter> result = new ArrayList<>();
            for (var entry : json) {
                var chapter = entry.getAsJsonObject();
                List<Line> lines = new ArrayList<>();
                for (var value : chapter.getAsJsonArray("lines")) {
                    var line = value.getAsJsonObject();
                    lines.add(new Line(line.get("speaker").getAsString(), line.get("expression").getAsString(),
                            line.get("text").getAsString(), line.get("minimum_ms").getAsInt()));
                }
                if (lines.stream().mapToInt(Line::minimumMillis).sum() != 300_000 || lines.size() < 30)
                    throw new IllegalStateException("Every chapter must contain five minutes of suggested autoplay timing");
                result.add(new Chapter(chapter.get("title").getAsString(), chapter.get("summary").getAsString(), List.copyOf(lines)));
            }
            if (result.size() != 5) throw new IllegalStateException("Expected five chapters");
            return List.copyOf(result);
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private LonyaScript() { }
}
