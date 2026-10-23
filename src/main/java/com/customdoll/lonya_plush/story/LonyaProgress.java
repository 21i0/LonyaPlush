package com.customdoll.lonya_plush.story;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-player, per-world progress. No inventory or friendship rewards are involved. */
public final class LonyaProgress extends SavedData {
    public static final class Progress {
        public int chapter, line, completed;
        public long readMillis, lastChapterCompletedAtMillis;
        public boolean ended, fifthUnlocked;
        public boolean available(int chapter) {
            return chapter >= 0 && chapter < 5 && (chapter == 0 || (chapter == 4 ? fifthUnlocked : (completed & (1 << (chapter - 1))) != 0));
        }
    }
    private final Map<UUID, Progress> players = new HashMap<>();
    public static LonyaProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(LonyaProgress::new, LonyaProgress::load), "lonya_plush_story");
    }
    public Progress forPlayer(UUID id) { return players.computeIfAbsent(id, key -> new Progress()); }
    public void replay(Progress progress, int chapter) {
        if (!progress.available(chapter)) return;
        progress.chapter = chapter; progress.line = 0; progress.readMillis = 0; progress.ended = false; setDirty();
    }
    public void complete(Progress progress, int choice, long nowMillis) {
        progress.lastChapterCompletedAtMillis = Math.max(1, nowMillis);
        progress.completed |= 1 << progress.chapter;
        progress.readMillis = 0;
        if (progress.chapter == 3) {
            if (choice == 0) { progress.fifthUnlocked = true; progress.chapter = 4; progress.line = 0; }
            else { progress.ended = true; }
        } else if (progress.chapter == 4) progress.ended = true;
        else { progress.chapter++; progress.line = 0; }
        setDirty();
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        var entries = new ListTag();
        players.forEach((id, progress) -> {
            var tag = new CompoundTag(); tag.putUUID("player", id);
            tag.putInt("chapter", progress.chapter); tag.putInt("line", progress.line);
            tag.putInt("completed", progress.completed); tag.putLong("read_ms", progress.readMillis);
            tag.putLong("last_chapter_completed_at_ms", progress.lastChapterCompletedAtMillis);
            tag.putBoolean("ended", progress.ended); tag.putBoolean("fifth_unlocked", progress.fifthUnlocked); entries.add(tag);
        });
        root.put("players", entries); return root;
    }
    public static LonyaProgress load(CompoundTag root, HolderLookup.Provider lookup) {
        var data = new LonyaProgress(); var entries = root.getList("players", Tag.TAG_COMPOUND);
        for (var value : entries) {
            var tag = (CompoundTag)value; if (!tag.hasUUID("player")) continue;
            var progress = new Progress(); progress.completed = tag.getInt("completed") & 31;
            progress.fifthUnlocked = tag.getBoolean("fifth_unlocked");
            progress.chapter = Math.clamp(tag.getInt("chapter"), 0, 4);
            if (!progress.available(progress.chapter)) progress.chapter = 0;
            progress.line = Math.clamp(tag.getInt("line"), 0, LonyaScript.chapter(progress.chapter).lines().size() - 1);
            progress.readMillis = Math.clamp(tag.getLong("read_ms"), 0, LonyaScript.chapter(progress.chapter).lines().get(progress.line).minimumMillis());
            progress.lastChapterCompletedAtMillis = Math.max(0, tag.getLong("last_chapter_completed_at_ms"));
            progress.ended = tag.getBoolean("ended"); data.players.put(tag.getUUID("player"), progress);
        }
        return data;
    }
}
