package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.init.*;
import com.customdoll.lonya_plush.content.blockentity.DollDisplayBlockEntity;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.*;
import net.minecraft.server.level.*;
import com.mojang.logging.LogUtils;
@EventBusSubscriber(modid=DollMod.MODID)
public final class RegressionChecks {
 public static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static ItemStack specimen(){var s=new ItemStack(ModItems.TWO_I_DOLL.get());s.set(DataComponents.CUSTOM_NAME,Component.literal("Roundtrip specimen"));s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,true);var t=new net.minecraft.nbt.CompoundTag();t.putString("payload","custom data preserved");s.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(t));return s;}
 public static net.neoforged.neoforge.common.util.FakePlayer player(ServerLevel l,String name) {return net.neoforged.neoforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
 @SubscribeEvent public static void start(ServerStartedEvent e) {
  if(!Boolean.getBoolean("doll.checks"))return;
  e.getServer().overworld().getChunkAt(new BlockPos(22,101,0));
 }
 private static int warmup;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) {
  if (!Boolean.getBoolean("doll.checks") || ++warmup != 40) return;
  try{
   var l=e.getServer().overworld();
   DollHeadwearChecks.run(l);
   if(net.neoforged.fml.ModList.get().isLoaded("curios")) CuriosChecks.run(l);
   var p=player(l,"DollOwner");p.getInventory().clearContent();p.setPos(0,101,0);
   for(int x=-2;x<14;x++)for(int z=-2;z<3;z++){l.setBlockAndUpdate(new BlockPos(x,100,z),Blocks.STONE.defaultBlockState());for(int y=101;y<105;y++)l.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
   var pos=new BlockPos(0,101,0);var state=ModBlocks.TWO_I_DOLL_DISPLAY.get().defaultBlockState();
   var emptyDisplay=new DollDisplayBlockEntity(pos,state);
   check(emptyDisplay.copyDoll().isEmpty(),"missing block data must never synthesize a doll");
   var emptyCopy=(DollDisplayBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,state,emptyDisplay.saveWithFullMetadata(l.registryAccess()),l.registryAccess());
   check(emptyCopy!=null&&emptyCopy.copyDoll().isEmpty(),"empty display stays empty after decode");
   var saved=specimen();p.setItemInHand(InteractionHand.MAIN_HAND,saved.copy());
   com.customdoll.lonya_plush.content.DollOwnership.claim(saved,p);
   var hit=new BlockHitResult(new Vec3(.5,101,.5),Direction.UP,pos.below(),false);
   var ctx=new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,hit);
   check(p.getMainHandItem().useOn(ctx).consumesAction(),"real BlockItem placement");
   check(p.getMainHandItem().isEmpty(),"placement consumes exactly one");
   var display=(DollDisplayBlockEntity)l.getBlockEntity(pos);
   check(display!=null&&ItemStack.isSameItemSameComponents(saved,display.copyDoll()),"placed components preserved");
   OwnershipChecks.run(l, p, pos, display);
   display.toggleSpinning();long now=l.getGameTime();check(Math.abs(display.rotationAt(now+50)-90)<.001,"continuous rotation");
   var clone=(DollDisplayBlockEntity)net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos,state,display.saveWithFullMetadata(l.registryAccess()),l.registryAccess());
   check(clone!=null&&clone.isSpinning()&&ItemStack.isSameItemSameComponents(saved,clone.copyDoll()),"block persistence");
   clone.setLevel(l);clone.toggleSpinning();check(clone.rotationAt(now+500)==clone.rotationAt(now),"pause rotation");
   var click=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);p.setShiftKeyDown(true);
   l.getBlockState(pos).useWithoutItem(l,p,click);
   check(l.getBlockState(pos).isAir()&&p.getInventory().countItem(ModItems.TWO_I_DOLL.get())==1,"shift pickup exactly one");
   p.setShiftKeyDown(false);p.getInventory().clearContent();
   l.setBlockAndUpdate(pos,state);display=(DollDisplayBlockEntity)l.getBlockEntity(pos);display.setDoll(saved);
   boolean broken=l.destroyBlock(pos,true,p);
   var drops=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(pos).inflate(2));
   int dollDropCount=drops.stream().filter(d->d.getItem().is(ModItems.TWO_I_DOLL.get())).mapToInt(d->d.getItem().getCount()).sum();
   check(broken&&dollDropCount==1,"break drops exactly one (destroyed="+broken+", count="+dollDropCount+", block="+l.getBlockState(pos)+", nearby="+drops.stream().map(d->d.getItem().toString()).toList()+")");
   check(drops.stream().anyMatch(d->ItemStack.isSameItemSameComponents(saved,d.getItem())),"break components preserved");
   drops.forEach(net.minecraft.world.entity.Entity::discard);
   CompanionChecks.run(l);
   var persist=new BlockPos(20,101,0);l.getChunkAt(persist);
   var marker=e.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("doll-check-marker.txt");
   if(java.nio.file.Files.exists(marker)) {
    var be=l.getBlockEntity(persist);check(be instanceof DollDisplayBlockEntity,"display survived actual server restart");
    check(ItemStack.isSameItemSameComponents(saved,((DollDisplayBlockEntity)be).copyDoll()),"restart components intact");
    LogUtils.getLogger().info("DOLL RESTART CHECKS PASSED");
   }
   l.setBlockAndUpdate(persist,state);((DollDisplayBlockEntity)l.getBlockEntity(persist)).setDoll(saved);
   java.nio.file.Files.writeString(marker,"restart fixture");
   check(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(k->k.getNamespace().equals(DollMod.MODID)).count()==1,"exact entity registry count");
   LonyaStoryChecks.run(l);
   LonyaCooldownChecks.run(l);
   LogUtils.getLogger().info("DOLL SERVER CHECKS PASSED");
  }catch(Throwable t){LogUtils.getLogger().error("DOLL SERVER CHECKS FAILED",t);}
  finally{e.getServer().halt(false);}
 }
}
