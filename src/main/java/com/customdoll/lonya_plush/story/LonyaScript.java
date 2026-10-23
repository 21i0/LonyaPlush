package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.DollMod;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Shared immutable dialogue graph. Nested option sequences return to their parent's continuation. */
public final class LonyaScript {
    public record Line(String id, String speaker, String text, int next, List<Choice> choices) {
        /** Suggested autoplay timing, derived from text rather than authored in JSON. */
        public int minimumMillis() { return Math.clamp(text.codePointCount(0, text.length()) * 100 + 1200, 1800, 15000); }
    }
    public record Choice(String text, int affection, int next, boolean unlockFifth, boolean endRoute) { }
    public record Chapter(String title, String summary, List<Line> lines, int mainLines) {
        // Kept for older optional development utilities; runtime uses each line's choices.
        @Deprecated public int choiceLine() {
            for (int i=0;i<lines.size();i++) if (!lines.get(i).choices().isEmpty()) return i;
            return -1;
        }
        @Deprecated public List<Choice> choices() { return choiceLine()<0 ? List.of() : lines.get(choiceLine()).choices(); }
    }
    public record CasualLine(String text, String expression, int minAffection, int maxAffection) {
        public boolean eligible(int affection) { return affection>=minAffection && affection<=maxAffection; }
    }
    public static final Set<String> EXPRESSIONS = Set.of("hello", "calm", "angry", "idea", "puzzled", "shy", "sorry", "surprised");
    public static final List<Chapter> CHAPTERS = loadStory();
    public static final List<CasualLine> CASUAL = loadCasual();
    public static Chapter chapter(int index) { return CHAPTERS.get(index); }
    private static JsonArray resource(String name) {
        try (var input = LonyaScript.class.getResourceAsStream("/assets/"+DollMod.MODID+"/story/"+name)) {
            if (input==null) throw new IllegalStateException("Missing "+name);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception failure) { throw new IllegalStateException("Cannot load "+name, failure); }
    }
    private static boolean flag(JsonObject object, String key) { return object.has(key)&&object.get(key).getAsBoolean(); }
    private static List<Chapter> loadStory() {
        var result = new ArrayList<Chapter>();
        for (int index=1;index<=5;index++) {
            var chapter = chapterResource("chapter"+index+".json");
            var source = chapter.getAsJsonArray("lines");
            if (source.isEmpty()) throw new IllegalStateException("Chapter needs dialogue");
            var lines = new ArrayList<Line>();
            compile(source, -1, "line", lines, new HashSet<>());
            if (lines.size()>=100000) throw new IllegalStateException("Chapter is too large");
            result.add(new Chapter(chapter.get("title").getAsString(), chapter.get("summary").getAsString(), List.copyOf(lines), source.size()));
        }
        return List.copyOf(result);
    }
    private static JsonObject chapterResource(String name) {
        try (var input=LonyaScript.class.getResourceAsStream("/assets/"+DollMod.MODID+"/story/"+name)) {
            if(input==null)throw new IllegalStateException("Missing "+name);
            return JsonParser.parseString(new String(input.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(Exception failure) { throw new IllegalStateException("Cannot load "+name,failure); }
    }
    /** Reserve a sequence before its branches so existing main-line save positions remain valid. */
    private static int compile(JsonArray sequence, int continuation, String path, List<Line> lines, Set<String> ids) {
        if (sequence.isEmpty()) return continuation;
        int start=lines.size();
        for (int i=0;i<sequence.size();i++) lines.add(null);
        for (int i=0;i<sequence.size();i++) {
            var source=sequence.get(i).getAsJsonObject();
            String id=source.has("id")?source.get("id").getAsString():path+"/"+i;
            if (!ids.add(id)) throw new IllegalStateException("Duplicate dialogue id: "+id);
            int next=i+1<sequence.size()?start+i+1:continuation;
            var choices=new ArrayList<Choice>();
            if (source.has("choices")) for (var entry:source.getAsJsonArray("choices")) {
                var choice=entry.getAsJsonObject();
                int target=choice.has("lines")?compile(choice.getAsJsonArray("lines"),next,id+"/choice/"+choices.size(),lines,ids):next;
                choices.add(new Choice(choice.get("text").getAsString(),choice.has("affection")?choice.get("affection").getAsInt():0,
                        target,flag(choice,"unlock_fifth"),flag(choice,"end_route")));
            }
            lines.set(start+i,new Line(id,source.get("speaker").getAsString(),source.get("text").getAsString(),next,List.copyOf(choices)));
        }
        return start;
    }
    private static List<CasualLine> loadCasual() {
        var result=new ArrayList<CasualLine>();
        for (var entry:resource("causal.json")) {
            var source=entry.getAsJsonObject();
            String expression=source.has("expression")?source.get("expression").getAsString():"hello";
            int min=source.has("min_affection")?source.get("min_affection").getAsInt():0;
            int max=source.has("max_affection")?source.get("max_affection").getAsInt():Integer.MAX_VALUE;
            if (min<0||max<min||!EXPRESSIONS.contains(expression)) throw new IllegalStateException("Invalid casual expression or affection range: "+source);
            result.add(new CasualLine(source.get("text").getAsString(),expression,min,max));
        }
        if (result.isEmpty()) throw new IllegalStateException("causal.json needs dialogue");
        return List.copyOf(result);
    }
    private LonyaScript() { }
}
