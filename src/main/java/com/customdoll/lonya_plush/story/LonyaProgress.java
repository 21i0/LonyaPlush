package com.customdoll.lonya_plush.story;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Per-player, per-world story and affection, shared by all of a player's dolls. */
public final class LonyaProgress extends SavedData {
    public static final int HISTORY_LIMIT=512, HISTORY_STRIDE=100000;
    public static final int COMPANION_INTERVAL_TICKS=20*60;
    public static final class Progress {
        public int chapter, line, completed;
        public long readMillis, lastChapterCompletedAtMillis;
        public int affection, companionTicks, rewardedChoices;
        public long lastDailyChatAtMillis;
        private long lastCompanionTick=Long.MIN_VALUE;
        public boolean ended, fifthUnlocked, endAfterChapter;
        public final Set<String> resolvedChoices=new HashSet<>();
        public final List<Integer> history=new ArrayList<>();
        public boolean available(int chapter) {
            return storyAvailable(chapter)&&affection>=requiredAffection(chapter);
        }
        public boolean storyAvailable(int chapter) {
            return chapter>=0&&chapter<5&&(chapter==0||(chapter==4?fifthUnlocked&&(completed&8)!=0:(completed&(1<<(chapter-1)))!=0));
        }
    }
    public static int requiredAffection(int chapter) { return (chapter+1)*20; }
    private final Map<UUID, Progress> players=new HashMap<>();
    public static LonyaProgress get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(LonyaProgress::new,LonyaProgress::load),"lonya_plush_story");
    }
    public Progress forPlayer(UUID id) { return players.computeIfAbsent(id,key->new Progress()); }
    public void addAffection(Progress progress,int amount) {
        progress.affection=(int)Math.clamp((long)progress.affection+amount,0,Integer.MAX_VALUE);setDirty();
    }
    /** One eligible server tick per player, even with several nearby dolls. No offline accrual. */
    public void accompany(Progress progress,long serverTick) {
        if(progress.lastCompanionTick==serverTick)return;
        progress.lastCompanionTick=serverTick;
        if(++progress.companionTicks>=COMPANION_INTERVAL_TICKS) {progress.companionTicks=0;addAffection(progress,1);}
        setDirty();
    }
    /** Each choice node applies its signed affection change once, including across replays. */
    public void rewardChoice(Progress progress,int amount) {
        String key=progress.chapter+":"+LonyaScript.chapter(progress.chapter).lines().get(progress.line).id();
        if(progress.resolvedChoices.add(key)) {addAffection(progress,amount);setDirty();}
    }
    public void recordLine(Progress progress) {
        int entry=progress.chapter*HISTORY_STRIDE+progress.line;
        if(progress.history.isEmpty()||progress.history.getLast()!=entry)progress.history.add(entry);
        while(progress.history.size()>HISTORY_LIMIT)progress.history.removeFirst();
        setDirty();
    }
    public void replay(Progress progress,int chapter) {
        if(!progress.available(chapter))return;
        progress.chapter=chapter;progress.line=0;progress.readMillis=0;progress.ended=false;progress.endAfterChapter=false;
        recordLine(progress);setDirty();
    }
    public void complete(Progress progress,int ignoredChoice,long nowMillis) {
        progress.lastChapterCompletedAtMillis=Math.max(1,nowMillis);
        progress.completed|=1<<progress.chapter;progress.readMillis=0;
        if(progress.endAfterChapter||progress.chapter==4||(progress.chapter==3&&!progress.fifthUnlocked))progress.ended=true;
        else {progress.chapter++;progress.line=0;}
        progress.endAfterChapter=false;setDirty();
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup) {
        var entries=new ListTag();
        players.forEach((id,progress)->{
            var tag=new CompoundTag();tag.putUUID("player",id);
            tag.putInt("chapter",progress.chapter);tag.putInt("line",progress.line);
            tag.putString("line_id",LonyaScript.chapter(progress.chapter).lines().get(progress.line).id());
            tag.putInt("completed",progress.completed);tag.putLong("read_ms",progress.readMillis);
            tag.putLong("last_chapter_completed_at_ms",progress.lastChapterCompletedAtMillis);
            tag.putInt("affection",progress.affection);tag.putInt("companion_ticks",progress.companionTicks);
            tag.putLong("last_daily_chat_at_ms",progress.lastDailyChatAtMillis);
            var choices=new ListTag();progress.resolvedChoices.stream().sorted().forEach(key->choices.add(StringTag.valueOf(key)));
            tag.put("resolved_choices",choices);
            var history=new ListTag();
            for(int entry:progress.history) {
                int c=entry/HISTORY_STRIDE,i=entry%HISTORY_STRIDE;var item=new CompoundTag();
                item.putInt("chapter",c);item.putString("id",LonyaScript.chapter(c).lines().get(i).id());history.add(item);
            }
            tag.put("history",history);tag.putBoolean("end_after_chapter",progress.endAfterChapter);
            tag.putBoolean("ended",progress.ended);tag.putBoolean("fifth_unlocked",progress.fifthUnlocked);entries.add(tag);
        });
        root.put("players",entries);return root;
    }
    private static int findLine(int chapter,String id,int fallback) {
        var lines=LonyaScript.chapter(chapter).lines();
        for(int i=0;i<lines.size();i++)if(lines.get(i).id().equals(id))return i;
        return fallback;
    }
    public static LonyaProgress load(CompoundTag root,HolderLookup.Provider lookup) {
        var data=new LonyaProgress();var entries=root.getList("players",Tag.TAG_COMPOUND);
        for(var value:entries) {
            var tag=(CompoundTag)value;if(!tag.hasUUID("player"))continue;
            var progress=new Progress();progress.completed=tag.getInt("completed")&31;
            progress.fifthUnlocked=tag.getBoolean("fifth_unlocked");progress.chapter=Math.clamp(tag.getInt("chapter"),0,4);
            if(!progress.storyAvailable(progress.chapter))progress.chapter=0;
            var lines=LonyaScript.chapter(progress.chapter).lines();
            int fallback=tag.contains("line_id",Tag.TAG_STRING)?0:Math.clamp(tag.getInt("line"),0,lines.size()-1);
            progress.line=findLine(progress.chapter,tag.getString("line_id"),fallback);
            progress.readMillis=Math.clamp(tag.getLong("read_ms"),0,lines.get(progress.line).minimumMillis());
            progress.lastChapterCompletedAtMillis=Math.max(0,tag.getLong("last_chapter_completed_at_ms"));
            progress.affection=Math.max(0,tag.getInt("affection"));
            progress.companionTicks=Math.clamp(tag.getInt("companion_ticks"),0,COMPANION_INTERVAL_TICKS-1);
            progress.lastDailyChatAtMillis=Math.max(0,tag.getLong("last_daily_chat_at_ms"));
            for(var choice:tag.getList("resolved_choices",Tag.TAG_STRING))progress.resolvedChoices.add(choice.getAsString());
            // Migrate rewards from the previous one-choice-per-chapter format.
            for(int c=0;c<5;c++)if((tag.getInt("rewarded_choices")&(1<<c))!=0) {
                for(var line:LonyaScript.chapter(c).lines())if(!line.choices().isEmpty())progress.resolvedChoices.add(c+":"+line.id());
            }
            if(tag.contains("history",Tag.TAG_LIST)) {
                for(var item:tag.getList("history",Tag.TAG_COMPOUND)) {
                    var visited=(CompoundTag)item;int c=visited.getInt("chapter");if(c<0||c>=5)continue;
                    int i=findLine(c,visited.getString("id"),-1);if(i>=0)progress.history.add(c*HISTORY_STRIDE+i);
                }
            } else {
                // Old saves only know the linear reading position; never invent branch history.
                for(int c=0;c<=progress.chapter;c++) {
                    if(c<progress.chapter&&(progress.completed&(1<<c))==0)continue;
                    int end=c==progress.chapter?Math.min(progress.line+1,LonyaScript.chapter(c).mainLines()):LonyaScript.chapter(c).mainLines();
                    for(int i=0;i<end;i++)progress.history.add(c*HISTORY_STRIDE+i);
                }
            }
            while(progress.history.size()>HISTORY_LIMIT)progress.history.removeFirst();
            progress.endAfterChapter=tag.getBoolean("end_after_chapter");progress.ended=tag.getBoolean("ended");
            data.players.put(tag.getUUID("player"),progress);
        }
        return data;
    }
}
