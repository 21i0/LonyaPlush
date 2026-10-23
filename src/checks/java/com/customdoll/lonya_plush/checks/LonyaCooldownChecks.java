package com.customdoll.lonya_plush.checks;

import com.customdoll.lonya_plush.story.*;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.init.ModEntities;
import com.google.gson.JsonParser;
import net.minecraft.server.level.*;
import net.minecraft.world.level.storage.LevelResource;
import java.util.concurrent.atomic.AtomicLong;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;

/** Authoritative chapter boundaries, real SavedData restarts, and configuration changes. */
public final class LonyaCooldownChecks {
    private static LonyaNetwork.Request packet(DollEntity doll,LonyaStoryService.Session session,int action,int value) {
        return new LonyaNetwork.Request(doll.getId(),session.token,session.revision,action,value);
    }
    private static void command(ServerPlayer player,DollEntity doll,LonyaStoryService.Session session,int action,int value) {
        LonyaStoryService.handle(player,packet(doll,session,action,value));
    }
    private static DollEntity doll(ServerLevel level,ServerPlayer owner) {
        var doll=ModEntities.TWO_I_DOLL.get().create(level);doll.initialize(owner.getUUID(),specimen());doll.setPos(5,101,2);
        check(level.addFreshEntity(doll),"cooldown specimen spawned");return doll;
    }
    public static void run(ServerLevel level) throws Exception {
        check(LonyaStoryConfig.COOLDOWN_ENABLED.get()&&LonyaStoryConfig.COOLDOWN_SECONDS.get()==1200,"default configuration enables twenty minute cooldown");
        int configured=LonyaStoryConfig.COOLDOWN_SECONDS.get();boolean enabled=LonyaStoryConfig.COOLDOWN_ENABLED.get();
        try {verify(level);} finally {
            LonyaStoryConfig.COOLDOWN_ENABLED.set(enabled);LonyaStoryConfig.COOLDOWN_SECONDS.set(configured);
        }
    }
    private static void verify(ServerLevel level) throws Exception {
        var owner=player(level,"LonyaCooldownOwner");owner.setPos(5,101,1);owner.getInventory().clearContent();
        var data=LonyaProgress.get(level.getServer());var progress=data.forPlayer(owner.getUUID());
        var marker=level.getServer().getWorldPath(LevelResource.ROOT).resolve("lonya-cooldown-restart.txt");
        if(java.nio.file.Files.exists(marker)) {
            long persisted=Long.parseLong(java.nio.file.Files.readString(marker).trim());
            check(progress.lastChapterCompletedAtMillis==persisted&&progress.chapter==1&&progress.line==0,"chapter completion wall time survived real server process restart");
            var restoredDoll=doll(level,owner);var restoredSession=LonyaStoryService.open(owner,restoredDoll);
            long remaining=JsonParser.parseString(LonyaStoryService.snapshot(owner,restoredSession,false,"")).getAsJsonObject().get("cooldown_remaining_ms").getAsLong();
            long expected=LonyaStoryConfig.remainingMillis(progress,System.currentTimeMillis());
            check(Math.abs(remaining-expected)<100,"reopened session computes remaining cooldown from persisted wall time");
            if(remaining>0) {command(owner,restoredDoll,restoredSession,LonyaStoryService.CONTINUE,0);check(restoredSession.mode==0,"restart cannot bypass an unexpired cooldown");}
            LonyaStoryService.close(owner);restoredDoll.discard();
            com.mojang.logging.LogUtils.getLogger().info("LONYA COOLDOWN RESTART CHECKS PASSED: timestamp={}, remaining_ms={}",persisted,remaining);
        }
        progress.chapter=0;progress.line=0;progress.completed=0;progress.readMillis=0;progress.ended=false;progress.fifthUnlocked=false;progress.lastChapterCompletedAtMillis=0;
        var wall=new AtomicLong(System.currentTimeMillis());var nano=new AtomicLong(1_000_000_000L);
        var doll=doll(level,owner);var session=LonyaStoryService.open(owner,doll,nano::get,wall::get);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);
        check(session.mode==1,"new and legacy players have no retroactive cooldown");
        int count=LonyaScript.chapter(0).lines().size();
        for(int i=0;i<count-1;i++)command(owner,doll,session,LonyaStoryService.NEXT,0);
        check(progress.chapter==0&&progress.line==count-1&&progress.lastChapterCompletedAtMillis==0,"all dialogue advances without a within-chapter wait");
        var finalPacket=packet(doll,session,LonyaStoryService.NEXT,0);LonyaStoryService.handle(owner,finalPacket);
        long completedAt=wall.get();check(progress.chapter==1&&progress.line==0&&session.mode==0,"completion enters hub and unlocks next chapter");
        check(progress.lastChapterCompletedAtMillis==completedAt&&LonyaStoryConfig.remainingMillis(progress,wall.get())==1_200_000,"chapter completion starts default twenty minutes");
        LonyaStoryService.handle(owner,finalPacket);check(progress.lastChapterCompletedAtMillis==completedAt,"duplicate completion cannot extend cooldown");
        int revision=session.revision;
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);command(owner,doll,session,LonyaStoryService.REPLAY,0);command(owner,doll,session,LonyaStoryService.NEXT,0);
        check(session.mode==0&&session.revision==revision&&progress.chapter==1&&progress.line==0,"continue/replay/forged next rejected on server during cooldown");
        command(owner,doll,session,LonyaStoryService.HUB,0);check(session.mode==0,"hub remains available");
        LonyaStoryService.close(owner);doll.discard();doll=doll(level,owner);session=LonyaStoryService.open(owner,doll,nano::get,wall::get);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==0,"reopening UI with another doll cannot bypass player's cooldown");
        var peer=player(level,"LonyaCooldownPeer");peer.setPos(5,101,1);
        var peerDoll=doll(level,peer);var peerSession=LonyaStoryService.open(peer,peerDoll,nano::get,wall::get);
        command(peer,peerDoll,peerSession,LonyaStoryService.CONTINUE,0);check(peerSession.mode==1,"cooldown isolated by player UUID");
        LonyaStoryService.close(peer);peerDoll.discard();
        var saved=data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess());
        var roundtrip=LonyaProgress.load(saved,level.registryAccess());
        check(roundtrip.forPlayer(owner.getUUID()).lastChapterCompletedAtMillis==completedAt,"SavedData preserves cooldown timestamp");
        for(var entry:saved.getList("players",10))((net.minecraft.nbt.CompoundTag)entry).remove("last_chapter_completed_at_ms");
        check(LonyaProgress.load(saved,level.registryAccess()).forPlayer(owner.getUUID()).lastChapterCompletedAtMillis==0,"old saves without timestamp stay immediately playable");
        LonyaStoryConfig.COOLDOWN_SECONDS.set(60);wall.set(completedAt+59_999);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==0&&LonyaStoryConfig.remainingMillis(progress,wall.get())==1,"custom duration rejects one millisecond before expiry");
        wall.incrementAndGet();command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==1,"custom duration permits entry exactly at expiry");
        LonyaStoryConfig.COOLDOWN_SECONDS.set(1200);check(LonyaStoryConfig.remainingMillis(progress,wall.get())==1_140_000,"duration edits recalculate an existing cooldown");
        command(owner,doll,session,LonyaStoryService.HUB,0);LonyaStoryConfig.COOLDOWN_ENABLED.set(false);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==1,"configuration switch disables server entry restriction");
        command(owner,doll,session,LonyaStoryService.REPLAY,0);check(progress.chapter==0&&progress.line==0,"disabled cooldown also allows replay");
        check(progress.lastChapterCompletedAtMillis==completedAt,"replay does not clear persisted cooldown");
        LonyaStoryConfig.COOLDOWN_ENABLED.set(true);check(LonyaStoryConfig.remainingMillis(progress,wall.get())>0,"re-enabling applies remaining configured cooldown");
        LonyaStoryConfig.COOLDOWN_SECONDS.set(0);check(LonyaStoryConfig.remainingMillis(progress,wall.get())==0,"zero seconds disables waiting");
        LonyaStoryConfig.COOLDOWN_SECONDS.set(1200);wall.set(completedAt-1);
        check(LonyaStoryConfig.remainingMillis(progress,wall.get())==1_200_000,"wall clock correction never lengthens wait beyond configured duration");
        wall.set(completedAt+1_200_000);command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==1,"default duration expires at twenty minutes");
        // Keep an active, real-time cooldown in world data for the next independent server process.
        wall.set(System.currentTimeMillis());progress.lastChapterCompletedAtMillis=wall.get()-1_200_000;
        for(int i=0;i<count;i++)command(owner,doll,session,LonyaStoryService.NEXT,0);
        check(progress.chapter==1&&progress.lastChapterCompletedAtMillis==wall.get(),"new completion creates a new cooldown");
        java.nio.file.Files.writeString(marker,Long.toString(wall.get()));data.setDirty();
        command(owner,doll,session,LonyaStoryService.RECLAIM,0);check(doll.isRemoved(),"reclaim remains available during cooldown");
        com.mojang.logging.LogUtils.getLogger().info("LONYA COOLDOWN CHECKS PASSED: default1200s, config enable/disable/custom/zero, expiry boundaries, no per-line gate, UUID isolation, session/doll bypass rejected, duplicate completion, replay, legacy save migration and reclaim");
    }
}
