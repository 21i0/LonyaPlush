package com.customdoll.lonya_plush.checks;

import com.customdoll.lonya_plush.story.*;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.init.*;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;
import java.util.concurrent.atomic.AtomicLong;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;

/** Tests real block/entity registries and authoritative story commands with an injected monotonic clock. */
public final class LonyaStoryChecks {
    private static LonyaNetwork.Request packet(DollEntity doll,LonyaStoryService.Session session,int action,int value) {
        return new LonyaNetwork.Request(doll.getId(),session.token,session.revision,action,value);
    }
    private static void command(ServerPlayer player,DollEntity doll,LonyaStoryService.Session session,int action,int value) {
        LonyaStoryService.handle(player,packet(doll,session,action,value));
    }
    private static void finishChapter(ServerPlayer player,DollEntity doll,LonyaStoryService.Session session,AtomicLong clock,int choice) {
        var progress=LonyaProgress.get(player.server).forPlayer(player.getUUID());int chapter=progress.chapter;
        command(player,doll,session,LonyaStoryService.CONTINUE,0);
        int count=LonyaScript.chapter(chapter).lines().size();
        while(!progress.ended&&progress.chapter==chapter) {
            // Manual advance and both branch choices must work without advancing the clock.
            boolean branch=chapter==3&&progress.line==count-1;
            command(player,doll,session,branch?LonyaStoryService.CHOICE:LonyaStoryService.NEXT,choice);
            if(branch&&choice==1)break;
        }
    }
    public static void run(ServerLevel level) throws Exception {
        boolean enabled=LonyaStoryConfig.COOLDOWN_ENABLED.get();
        try {
            // Preserve the immediate-reading regression independently of chapter-entry cooldowns.
            LonyaStoryConfig.COOLDOWN_ENABLED.set(false);
            runFreeStory(level);
        } finally {LonyaStoryConfig.COOLDOWN_ENABLED.set(enabled);}
    }
    private static void runFreeStory(ServerLevel level) throws Exception {
        for(var chapter:LonyaScript.CHAPTERS)for(var line:chapter.lines()) {
            check(java.util.Set.of("Lonya","你","旁白").contains(line.speaker()),"two-person story has no third dialogue speaker");
            check(!line.text().contains("阿澄")&&!line.text().contains("三个人")&&!line.text().contains("吉她"),"rewritten story has no old cast or accidental text replacements");
        }
        check(LonyaScript.chapter(3).lines().getFirst().text().contains("来电")&&LonyaScript.chapter(4).lines().getFirst().text().contains("来电"),"chapters four and five begin as phone calls");
        var owner=player(level,"LonyaStoryOwner");var guest=player(level,"LonyaStoryGuest");
        owner.getInventory().clearContent();owner.setPos(5,101,1);guest.setPos(5,101,1);
        var data=LonyaProgress.get(level.getServer());
        var marker=level.getServer().getWorldPath(LevelResource.ROOT).resolve("lonya-story-restart.txt");
        if(java.nio.file.Files.exists(marker)) {
            var restored=data.forPlayer(owner.getUUID());
            check(restored.completed==31&&restored.ended&&restored.fifthUnlocked,"all five chapters survived real restart");
            var resume=data.forPlayer(player(level,"LonyaResume").getUUID());
            check(resume.chapter==0&&resume.line==3&&resume.readMillis==1000,"mid-line reading and cursor survived restart");
            com.mojang.logging.LogUtils.getLogger().info("LONYA STORY RESTART CHECKS PASSED");
        }
        var progress=data.forPlayer(owner.getUUID());progress.chapter=0;progress.line=0;progress.readMillis=0;progress.completed=0;progress.ended=false;progress.fifthUnlocked=false;progress.lastChapterCompletedAtMillis=0;
        var doll=ModEntities.TWO_I_DOLL.get().create(level);var original=specimen();doll.initialize(owner.getUUID(),original);doll.setPos(5,101,2);
        check(level.addFreshEntity(doll),"real doll entity inserted");
        check(LonyaStoryService.open(guest,doll)==null,"guest cannot open owner UI");
        owner.setShiftKeyDown(true);doll.interact(owner,InteractionHand.MAIN_HAND);owner.setShiftKeyDown(false);
        check(LonyaStoryService.busy(doll)&&!doll.isRemoved()&&owner.getInventory().countItem(ModItems.TWO_I_DOLL.get())==0,"real Shift interaction opens UI without reclaim");
        var clock=new AtomicLong(1_000_000_000L);var session=LonyaStoryService.open(owner,doll,clock::get);
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);
        var early=packet(doll,session,LonyaStoryService.NEXT,0);LonyaStoryService.handle(owner,early);
        check(progress.line==1&&progress.readMillis==0,"manual next advances immediately with zero reading time");
        command(owner,doll,session,LonyaStoryService.REPLAY,4);check(progress.chapter==0,"locked fifth rejects replay");
        LonyaStoryService.handle(owner,new LonyaNetwork.Request(doll.getId(),session.token+1,session.revision,LonyaStoryService.RECLAIM,0));
        check(!doll.isRemoved(),"wrong token rejected");
        command(owner,doll,session,LonyaStoryService.HEARTBEAT,0);clock.addAndGet(60_000_000_000L);command(owner,doll,session,LonyaStoryService.HEARTBEAT,0);
        check(progress.readMillis==0,"menu and disconnected time do not count");
        var once=packet(doll,session,LonyaStoryService.NEXT,0);LonyaStoryService.handle(owner,once);LonyaStoryService.handle(owner,once);
        check(progress.line==2,"duplicate advance changes cursor once");
        command(owner,doll,session,LonyaStoryService.REPLAY,0);
        long start=clock.get();for(int i=0;i<4;i++)finishChapter(owner,doll,session,clock,1);
        check(progress.ended&&progress.chapter==3&&!progress.fifthUnlocked&&progress.completed==15,"choice two ends at fourth and locks fifth");
        check(clock.get()==start,"all four chapters and second choice allow immediate skip with no time gate");
        command(owner,doll,session,LonyaStoryService.CONTINUE,0);check(session.mode==0&&progress.ended,"ended route cannot continue directly");
        command(owner,doll,session,LonyaStoryService.REPLAY,3);check(!progress.ended&&progress.chapter==3&&progress.line==0,"history replay reopens fourth from start");
        finishChapter(owner,doll,session,clock,0);
        check(progress.fifthUnlocked&&progress.chapter==4&&!progress.ended,"choice one unlocks fifth after fourth replay");
        finishChapter(owner,doll,session,clock,0);check(progress.completed==31&&progress.ended,"full route ends after fifth");
        check(data.forPlayer(guest.getUUID()).completed==0,"multiplayer story progress isolated");
        var roundtrip=LonyaProgress.load(data.save(new net.minecraft.nbt.CompoundTag(),level.registryAccess()),level.registryAccess());
        check(roundtrip.forPlayer(owner.getUUID()).completed==31,"SavedData codec preserves progress");
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        command(owner,doll,session,LonyaStoryService.RECLAIM,0);check(!doll.isRemoved(),"UI reclaim full inventory retains entity");
        owner.getInventory().setItem(8,ItemStack.EMPTY);var reclaim=packet(doll,session,LonyaStoryService.RECLAIM,0);
        LonyaStoryService.handle(owner,reclaim);LonyaStoryService.handle(owner,reclaim);
        check(doll.isRemoved()&&owner.getInventory().countItem(ModItems.TWO_I_DOLL.get())==1,"UI reclaim once only");
        check(ItemStack.isSameItemSameComponents(original,owner.getInventory().getItem(8)),"UI reclaim preserves all components");
        var resume=data.forPlayer(player(level,"LonyaResume").getUUID());resume.chapter=0;resume.line=3;resume.readMillis=1000;data.setDirty();
        java.nio.file.Files.writeString(marker,"completed route and mid-line progress");
        check(LonyaScript.CHAPTERS.stream().mapToInt(c->c.lines().stream().mapToInt(LonyaScript.Line::minimumMillis).sum()).sum()==1_500_000,"five chapters retain twenty-five minutes of suggested autoplay timing");
        com.mojang.logging.LogUtils.getLogger().info("LONYA STORY CHECKS PASSED: immediate next/choices, no mandatory reading time, locks, fourth replay, per-player persistence, duplicate packets, full inventory and components");
    }
}
