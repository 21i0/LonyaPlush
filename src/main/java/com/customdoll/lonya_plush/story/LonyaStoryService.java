package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.content.DollOwnership;
import com.google.gson.JsonObject;
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
    public static final int CONTINUE=1, NEXT=2, CHOICE=3, REPLAY=4, RECLAIM=5, CLOSE=6, HEARTBEAT=7, HUB=8;
    private static final Map<UUID, Session> SESSIONS=new HashMap<>();
    private static final LongSupplier CLOCK=System::nanoTime;
    public static final class Session {
        public final DollEntity doll;
        public final long token=new java.security.SecureRandom().nextLong();
        public int revision, mode;
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
        if(packet.action()==CONTINUE||packet.action()==REPLAY||packet.action()==NEXT||packet.action()==CHOICE) {
            long remaining=LonyaStoryConfig.remainingMillis(progress,session.wallClock.getAsLong());
            if(remaining>0) {send(player,session,false,"Lonya 正在休息，距离下次聊天还有 "+LonyaStoryConfig.countdown(remaining)+"。");return;}
        }
        switch(packet.action()) {
            case CONTINUE -> { if(!progress.ended)session.mode=1; }
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
                var chapter=LonyaScript.chapter(progress.chapter);
                boolean last=progress.line==chapter.lines().size()-1;
                // Reading duration is an autoplay suggestion, never an authorization gate.
                boolean branch=last&&progress.chapter==3;
                if(branch ? packet.action()!=CHOICE||packet.value()<0||packet.value()>1 : packet.action()!=NEXT)return;
                if(last) {
                    int previous=progress.chapter;data.complete(progress,packet.value(),session.wallClock.getAsLong());session.mode=0;
                    String message=previous==3&&packet.value()==1
                            ? "听筒那边停了一下。‘嗯，那我先挂了。你也早点休息。’Lonya 没再接着讲。\n本次通话结束；可在历史章节重玩第四章，重新作出选择。"
                            : previous==4 ? "音乐播完，你们又闲扯了几句才挂电话。那份素材留给明天，今晚先好好歇着。"
                            : previous==3 ? "‘那我可接着说了啊。’电话那头的 Lonya 笑起来，声音又轻快了。第五章已解锁。"
                            : "本章已读完，下一章已解锁。";
                    session.revision++;session.visible=false;send(player,session,false,message);return;
                }
                progress.line++;progress.readMillis=0;data.setDirty();
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
