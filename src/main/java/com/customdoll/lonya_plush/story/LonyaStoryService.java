package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.content.DollOwnership;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;
import java.util.function.LongSupplier;

/** Owner-bound leases, freely paced reading and replay-safe choices. */
@EventBusSubscriber(modid=DollMod.MODID)
public final class LonyaStoryService {
    public static final int CONTINUE=1, NEXT=2, CHOICE=3, REPLAY=4, RECLAIM=5, CLOSE=6, HEARTBEAT=7, HUB=8, DAILY_CHAT=9;
    private static final Map<UUID, Session> SESSIONS=new HashMap<>();
    private static final LongSupplier CLOCK=System::nanoTime;
    public static final class Session {
        public final DollEntity doll;
        public final long token=new java.security.SecureRandom().nextLong();
        public int revision, mode;
        private String casualText="", casualExpression="hello";
        private long heartbeat, sample;
        private boolean visible;
        private final LongSupplier clock, wallClock;
        private Session(DollEntity doll, LongSupplier clock, LongSupplier wallClock) {
            this.doll=doll;this.clock=clock;this.wallClock=wallClock;heartbeat=sample=clock.getAsLong();
        }
    }
    public static boolean busy(DollEntity doll) { return SESSIONS.values().stream().anyMatch(s->s.doll==doll); }
    private static boolean valid(ServerPlayer player, DollEntity doll) {
        return player.isAlive()&&!player.isSpectator()&&doll.isAlive()&&!doll.storedCopy().isEmpty()
                &&doll.level()==player.level()&&doll.distanceToSqr(player)<=36&&player.getUUID().equals(doll.ownerId());
    }
    public static Session open(ServerPlayer player, DollEntity doll) { return open(player,doll,CLOCK); }
    public static void accompany(ServerPlayer player, DollEntity doll) {
        if(!valid(player,doll))return;
        var data=LonyaProgress.get(player.server);
        data.accompany(data.forPlayer(player.getUUID()),player.server.getTickCount());
        if(doll.tickCount%10==0)LonyaGifts.collect(player,doll,data);
    }
    public static Session open(ServerPlayer player, DollEntity doll, LongSupplier clock) {
        return open(player,doll,clock,System::currentTimeMillis);
    }
    public static Session open(ServerPlayer player, DollEntity doll, LongSupplier clock, LongSupplier wallClock) {
        if(!valid(player,doll)) { DollOwnership.deny(player); return null; }
        close(player);var session=new Session(doll,clock,wallClock);SESSIONS.put(player.getUUID(),session);
        doll.getNavigation().stop();send(player,session,true,"");return session;
    }
    public static void close(ServerPlayer player) {
        var session=SESSIONS.remove(player.getUUID());
        if(session!=null) accrue(player,session);
    }
    public static void handle(ServerPlayer player,LonyaNetwork.Request packet) {
        var session=SESSIONS.get(player.getUUID());
        if(session==null||packet.token()!=session.token||packet.entity()!=session.doll.getId())return;
        if(!valid(player,session.doll)) { close(player);LonyaNetwork.send(player,"{\"closed\":true}");return; }
        accrue(player,session);
        long now=session.clock.getAsLong();
        if(packet.action()==CLOSE) { close(player);return; }
        if(packet.action()==HEARTBEAT) { session.heartbeat=now;session.visible=packet.value()==1;session.sample=now;return; }
        if(packet.revision()!=session.revision) { send(player,session,false,"操作已更新，请稍后再试。");return; }
        var data=LonyaProgress.get(player.server);var progress=data.forPlayer(player.getUUID());
        // The menu reports affection before the chapter cooldown, without changing screens.
        if(packet.action()==CONTINUE) {
            if(progress.ended) {send(player,session,false,"本次主线剧情已结束，可在历史回顾中重玩已解锁的章节。");return;}
            if(progress.affection<LonyaProgress.requiredAffection(progress.chapter)) {
                send(player,session,false,"目前好感度不足以继续推进剧情，需要好感度："+LonyaProgress.requiredAffection(progress.chapter)
                        +"；注：好感度可以通过闲聊、陪伴在玩偶附近、在玩偶附近丢钻石等贵重物品或章节中获得");return;
            }
            if(!progress.storyAvailable(progress.chapter)) {send(player,session,false,"请先完成前置剧情，可在历史回顾中重玩并选择其他回答。");return;}
        }
        if(packet.action()==CONTINUE||packet.action()==REPLAY||packet.action()==NEXT||packet.action()==CHOICE) {
            long remaining=LonyaStoryConfig.remainingMillis(progress,session.wallClock.getAsLong());
            if(remaining>0) {send(player,session,false,"Lonya 正在休息，距离下次拜访还有 "+LonyaStoryConfig.countdown(remaining)+"。");return;}
        }
        switch(packet.action()) {
            case DAILY_CHAT -> {
                if(session.mode!=0)return;
                var eligible=LonyaScript.CASUAL.stream().filter(line->line.eligible(progress.affection)).toList();
                if(eligible.isEmpty()) {send(player,session,false,"当前好感度下暂无可用的闲聊。");return;}
                var casual=eligible.get(player.getRandom().nextInt(eligible.size()));
                long wallNow=session.wallClock.getAsLong();
                if(LonyaStoryConfig.dailyRemainingMillis(progress,wallNow)==0)data.addAffection(progress,1);
                // Every chat restarts the one-minute inactivity interval, even without a reward.
                progress.lastDailyChatAtMillis=Math.max(1,wallNow);data.setDirty();
                session.casualText=casual.text();session.casualExpression=casual.expression();
                session.revision++;send(player,session,false,session.casualText);return;
            }
            case CONTINUE -> {
                session.mode=1;data.recordLine(progress);
            }
            case HUB -> session.mode=0;
            case REPLAY -> {
                if(!progress.available(packet.value())) {send(player,session,false,"这一章尚未解锁。");return;}
                data.replay(progress,packet.value());session.mode=1;
            }
            case RECLAIM -> {
                if(session.doll.reclaim(player)) { close(player);LonyaNetwork.send(player,"{\"closed\":true}");return; }
                send(player,session,false,"背包已满，请先空出一格。");return;
            }
            case NEXT, CHOICE -> {
                if(session.mode!=1||progress.ended)return;
                var line=LonyaScript.chapter(progress.chapter).lines().get(progress.line);
                // Reading duration is an autoplay suggestion, never an authorization gate.
                boolean branch=!line.choices().isEmpty();
                if(branch ? packet.action()!=CHOICE||packet.value()<0||packet.value()>=line.choices().size() : packet.action()!=NEXT)return;
                int target=line.next();
                if(branch) {
                    var choice=line.choices().get(packet.value());
                    data.rewardChoice(progress,choice.affection());
                    if(choice.unlockFifth())progress.fifthUnlocked=true;
                    if(choice.endRoute())progress.endAfterChapter=true;
                    target=choice.next();
                }
                if(target<0) {
                    data.complete(progress,0,session.wallClock.getAsLong());session.mode=0;
                    String message=progress.ended?"本次拜访已结束，可在历史回顾中重玩已解锁的章节。"
                            : "本章已读完，好感度达到 "+LonyaProgress.requiredAffection(progress.chapter)+" 后可拜访下一章。";
                    session.revision++;session.visible=false;send(player,session,false,message);return;
                }
                progress.line=target;progress.readMillis=0;data.recordLine(progress);data.setDirty();
            }
            default -> {return;}
        }
        session.revision++;session.visible=false;session.sample=now;send(player,session,false,"");
    }
    /** Only foreground reading with recent heartbeats earns time; offline/menu time never counts. */
    private static void accrue(ServerPlayer player,Session session) {
        long now=session.clock.getAsLong();long elapsed=Math.max(0,Math.min(250,(now-session.sample)/1_000_000));session.sample=now;
        if(session.mode!=1||!session.visible||now-session.heartbeat>3_000_000_000L)return;
        var data=LonyaProgress.get(player.server);var progress=data.forPlayer(player.getUUID());
        if(progress.ended)return;
        int required=LonyaScript.chapter(progress.chapter).lines().get(progress.line).minimumMillis();
        long updated=Math.min(required,progress.readMillis+elapsed);
        if(updated!=progress.readMillis) {progress.readMillis=updated;data.setDirty();}
    }
    public static String snapshot(ServerPlayer player,Session session,boolean open,String message) {
        var progress=LonyaProgress.get(player.server).forPlayer(player.getUUID());var json=new JsonObject();
        json.addProperty("entity",session.doll.getId());json.addProperty("token",session.token);json.addProperty("revision",session.revision);
        json.addProperty("chapter",progress.chapter);json.addProperty("line",progress.line);json.addProperty("read_ms",progress.readMillis);
        json.addProperty("completed",progress.completed);json.addProperty("fifth_unlocked",progress.fifthUnlocked);
        json.addProperty("affection",progress.affection);
        json.addProperty("casual_text",session.casualText);json.addProperty("casual_expression",session.casualExpression);
        var history=new JsonArray();progress.history.forEach(history::add);json.add("history",history);
        json.addProperty("cooldown_remaining_ms",LonyaStoryConfig.remainingMillis(progress,session.wallClock.getAsLong()));
        json.addProperty("ended",progress.ended);json.addProperty("mode",session.mode);json.addProperty("open",open);json.addProperty("message",message);
        return json.toString();
    }
    private static void send(ServerPlayer player,Session session,boolean open,String message) {LonyaNetwork.send(player,snapshot(player,session,open,message));}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for(var id:new ArrayList<>(SESSIONS.keySet())) {
            var player=event.getServer().getPlayerList().getPlayer(id);var session=SESSIONS.get(id);
            if(player==null||!valid(player,session.doll)||session.clock.getAsLong()-session.heartbeat>10_000_000_000L) {
                if(player!=null) {close(player);LonyaNetwork.send(player,"{\"closed\":true}");} else SESSIONS.remove(id);
                continue;
            }
            accrue(player,session);session.doll.getNavigation().stop();
            if(event.getServer().getTickCount()%10==0)send(player,session,false,"");
        }
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {SESSIONS.clear();}
}
