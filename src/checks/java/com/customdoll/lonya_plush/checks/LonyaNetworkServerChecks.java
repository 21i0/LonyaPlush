package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.init.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;

@EventBusSubscriber(modid=DollMod.MODID)
public final class LonyaNetworkServerChecks {
 private static int ticks,stage,delay;
 private static DollEntity doll;
 private static net.minecraft.server.level.ServerPlayer owner;
 @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
  if(!Boolean.getBoolean("lonya.network.server"))return;
  try {
   if(++ticks>3600)throw new AssertionError("network client timeout");
   if(stage==0) {
    var players=event.getServer().getPlayerList().getPlayers();if(players.isEmpty())return;
    owner=players.getFirst();if(++delay<60)return;
    var level=event.getServer().overworld();level.getChunkAt(new net.minecraft.core.BlockPos(0,100,0));
    for(int x=-3;x<4;x++)for(int z=-3;z<4;z++) {
     level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,100,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
     for(int y=101;y<=105;y++)level.setBlockAndUpdate(new net.minecraft.core.BlockPos(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }
    owner.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
    owner.teleportTo(level,.5,101,.5,java.util.Set.of(),0,0);owner.getInventory().clearContent();
    com.customdoll.lonya_plush.story.LonyaProgress.get(event.getServer()).forPlayer(owner.getUUID()).affection=100;
    doll=ModEntities.TWO_I_DOLL.get().create(level);doll.initialize(owner.getUUID(),specimen());doll.setPos(.5,101,2);
    doll.setNoGravity(true);check(level.addFreshEntity(doll),"wire test doll spawn");stage=1;delay=0;
   } else if(stage==1&&++delay>=20) {
    // Teleporting into a new chunk can postpone its entity ticks until the client acknowledges loading.
    if(com.customdoll.lonya_plush.story.LonyaProgress.get(event.getServer()).forPlayer(owner.getUUID()).companionTicks<20) {
     check(delay<200,"nearby entity ticks accrue companionship without interaction; entity_ticks="+doll.tickCount+", player="+owner.position());return;
    }
    owner.setShiftKeyDown(true);doll.interact(owner,net.minecraft.world.InteractionHand.MAIN_HAND);owner.setShiftKeyDown(false);stage=2;
   } else if(stage==2&&doll.isRemoved()) {
    check(owner.getInventory().countItem(ModItems.TWO_I_DOLL.get())==1,"wire UI reclaim returns exactly one");
    var returned=owner.getInventory().items.stream().filter(s->s.is(ModItems.TWO_I_DOLL.get())).findFirst().orElse(net.minecraft.world.item.ItemStack.EMPTY);
    var custom=returned.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
    check(returned.getCount()==1
            && net.minecraft.network.chat.Component.literal("Roundtrip specimen").equals(returned.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
            && Boolean.TRUE.equals(returned.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
            && custom!=null && custom.copyTag().getString("payload").equals("custom data preserved"),
            "wire UI components preserved returned="+returned+" slots="+owner.getInventory().items);
    var progress=com.customdoll.lonya_plush.story.LonyaProgress.get(event.getServer()).forPlayer(owner.getUUID());
    check(progress.chapter==1&&progress.line==0&&progress.completed==1,"wire completion stays at next chapter start");
    check(progress.affection==103&&progress.rewardedChoices==1&&progress.lastDailyChatAtMillis>0,"wire choice and daily chat rewards stored per player");
    long remaining=com.customdoll.lonya_plush.story.LonyaStoryConfig.remainingMillis(progress,System.currentTimeMillis());
    check(remaining>1_100_000,"server still enforces cooldown after forged entry, review and reclaim");
    com.mojang.logging.LogUtils.getLogger().info("LONYA NETWORK SERVER CHECKS PASSED: real client, Shift interaction, immediate next, chapter completion, cooldown rejection, UI reclaim and components, remaining_ms={}",remaining);
    stage=3;delay=0;
   } else if(stage==3&&++delay>60)event.getServer().halt(false);
  }catch(Throwable failure) {com.mojang.logging.LogUtils.getLogger().error("LONYA NETWORK SERVER CHECKS FAILED",failure);event.getServer().halt(false);}
 }
}
