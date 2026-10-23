package com.customdoll.lonya_plush.checks;

import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.init.ModEntities;
import com.customdoll.lonya_plush.story.*;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicLong;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;

/** Real authoritative requests, per-player saves and companion tick boundaries. */
public final class LonyaAffectionChecks {
    private static void command(ServerPlayer player, DollEntity doll, LonyaStoryService.Session session, int action, int value) {
        LonyaStoryService.handle(player,new LonyaNetwork.Request(doll.getId(),session.token,session.revision,action,value));
    }
    public static void run(ServerLevel level) throws Exception {
        var data=LonyaProgress.get(level.getServer());
        var owner=player(level,"AffectionOwner");owner.setPos(5,101,1);
        var peer=player(level,"AffectionPeer");peer.setPos(5,101,1);
        var progress=data.forPlayer(owner.getUUID());
        var marker=level.getServer().getWorldPath(LevelResource.ROOT).resolve("lonya-affection-restart.txt");
        if(Files.exists(marker)) {
            check(progress.affection==37&&progress.companionTicks==3599&&progress.rewardedChoices==1&&progress.lastDailyChatAtMillis==12345,
                    "affection, partial companionship, choice rewards and daily cooldown survived actual restart");
        }
        progress.affection=0;progress.companionTicks=0;progress.rewardedChoices=0;progress.lastDailyChatAtMillis=0;
        progress.chapter=0;progress.line=0;progress.completed=0;progress.ended=false;progress.fifthUnlocked=false;progress.lastChapterCompletedAtMillis=0;
        var doll=ModEntities.TWO_I_DOLL.get().create(level);doll.initialize(owner.getUUID(),specimen());doll.setPos(5,101,2);
        check(level.addFreshEntity(doll),"affection doll spawned");
        var wall=new AtomicLong(10_000_000);var session=LonyaStoryService.open(owner,doll,()->1_000_000_000L,wall::get);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);
        check(session.mode==0,"forged continue below 20 rejected");
        command(owner,doll,session,LonyaStoryService.REPLAY,0);
        check(session.mode==0,"forged replay below 20 rejected");
        var daily=new LonyaNetwork.Request(doll.getId(),session.token,session.revision,LonyaStoryService.DAILY_CHAT,0);
        LonyaStoryService.handle(owner,daily);LonyaStoryService.handle(owner,daily);
        check(progress.affection==1,"daily chat grants one point and duplicate packet cannot grant twice");
        command(owner,doll,session,LonyaStoryService.DAILY_CHAT,0);
        check(progress.affection==1,"fresh request cannot bypass daily cooldown");
        LonyaStoryService.close(owner);session=LonyaStoryService.open(owner,doll,()->1_000_000_000L,wall::get);
        command(owner,doll,session,LonyaStoryService.DAILY_CHAT,0);
        check(progress.affection==1,"reopening menu cannot bypass daily cooldown");
        wall.addAndGet(1_199_999);command(owner,doll,session,LonyaStoryService.DAILY_CHAT,0);
        check(progress.affection==1,"daily chat denied 1ms before expiry");
        wall.incrementAndGet();command(owner,doll,session,LonyaStoryService.DAILY_CHAT,0);
        check(progress.affection==2,"daily chat available exactly at configured expiry");
        check(data.forPlayer(peer.getUUID()).affection==0,"daily affection isolated by player");
        for(int chapter=0;chapter<5;chapter++) {
            progress.completed=31;progress.fifthUnlocked=true;progress.chapter=chapter;progress.line=0;
            progress.affection=LonyaProgress.requiredAffection(chapter)-1;
            command(owner,doll,session,LonyaStoryService.REPLAY,chapter);
            check(session.mode==0&&!progress.available(chapter),"chapter rejects affection immediately below threshold "+chapter);
            progress.affection++;
            command(owner,doll,session,LonyaStoryService.REPLAY,chapter);
            check(session.mode==1&&progress.available(chapter),"chapter opens exactly at threshold "+chapter);
            command(owner,doll,session,LonyaStoryService.HUB,0);
        }
        progress.chapter=0;progress.line=LonyaScript.chapter(0).choiceLine();progress.affection=20;
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);
        command(owner,doll,session,LonyaStoryService.NEXT,0);
        check(progress.line==LonyaScript.chapter(0).choiceLine(),"forged next cannot skip a choice");
        command(owner,doll,session,LonyaStoryService.CHOICE,2);
        check(progress.affection==20,"invalid choice does not earn affection");
        command(owner,doll,session,LonyaStoryService.CHOICE,0);
        check(progress.affection==22&&progress.rewardedChoices==1,"kind choice adds two points");
        progress.lastChapterCompletedAtMillis=0;
        command(owner,doll,session,LonyaStoryService.REPLAY,0);progress.line=LonyaScript.chapter(0).choiceLine();
        command(owner,doll,session,LonyaStoryService.CHOICE,0);
        check(progress.affection==22,"replaying a rewarded choice cannot farm affection");
        progress.affection=0;progress.companionTicks=0;
        for(int tick=1;tick<=3599;tick++) {data.accompany(progress,tick);data.accompany(progress,tick);}
        check(progress.affection==0&&progress.companionTicks==3599,"multiple dolls do not accelerate companionship");
        var saved=data.save(new CompoundTag(),level.registryAccess());
        var restored=LonyaProgress.load(saved,level.registryAccess());
        var resumed=restored.forPlayer(owner.getUUID());restored.accompany(resumed,4000);
        check(resumed.affection==1&&resumed.companionTicks==0,"partial three-minute interval survives save/load");
        data.accompany(progress,3600);
        check(progress.affection==1&&progress.companionTicks==0,"exactly 3600 eligible ticks earns one point");
        LonyaStoryService.accompany(peer,doll);
        check(data.forPlayer(peer.getUUID()).companionTicks==0,"other player's doll does not earn companion time");
        owner.setPos(50,101,1);LonyaStoryService.accompany(owner,doll);
        check(progress.companionTicks==0,"faraway doll does not count as companionship");
        owner.setPos(5,101,1);LonyaStoryService.accompany(owner,doll);LonyaStoryService.accompany(owner,doll);
        check(progress.companionTicks==1,"nearby owner's doll counts once per actual server tick");
        var empty=ModEntities.TWO_I_DOLL.get().create(level);empty.setPos(5,101,2);LonyaStoryService.accompany(owner,empty);
        check(progress.companionTicks==1,"empty doll cannot earn companion time");
        var snapshot=JsonParser.parseString(LonyaStoryService.snapshot(owner,session,false,"")).getAsJsonObject();
        check(snapshot.get("affection").getAsInt()==1&&snapshot.has("daily_cooldown_remaining_ms"),"server synchronizes affection and daily cooldown");
        progress.chapter=3;progress.line=8;progress.completed=7;progress.affection=0;
        saved=data.save(new CompoundTag(),level.registryAccess());
        for(var value:saved.getList("players",10)) {
            var tag=(CompoundTag)value;tag.remove("affection");tag.remove("companion_ticks");tag.remove("rewarded_choices");tag.remove("last_daily_chat_at_ms");
        }
        resumed=LonyaProgress.load(saved,level.registryAccess()).forPlayer(owner.getUUID());
        check(resumed.chapter==3&&resumed.line==8&&resumed.affection==0&&resumed.companionTicks==0,"legacy saves preserve chapter position and default new fields to zero");
        LonyaStoryService.close(owner);doll.discard();
        progress.affection=37;progress.companionTicks=3599;progress.rewardedChoices=1;progress.lastDailyChatAtMillis=12345;data.setDirty();
        Files.writeString(marker,"affection persistence");
        com.mojang.logging.LogUtils.getLogger().info("LONYA AFFECTION CHECKS PASSED: thresholds, daily cooldown, choice replay, player isolation, companion ticks, legacy saves and persistence");
    }
}
