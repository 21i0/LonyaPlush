package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.init.*;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import com.customdoll.lonya_plush.content.item.DollItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;
public final class CompanionChecks {
 public static void run(ServerLevel l) throws Exception {
  var p=player(l,"DollOwner");var guest=player(l,"DollGuest");
  p.getInventory().clearContent();guest.getInventory().clearContent();p.setPos(4,101,0);guest.setPos(4,101,1);p.setYRot(0);p.setXRot(0);
  var original=specimen(); var expected=original.copy(); com.customdoll.lonya_plush.content.DollOwnership.claim(expected,p); com.customdoll.lonya_plush.content.DollUmbrellaProtocol.markUmbrellaParticipant(expected); p.setItemInHand(InteractionHand.MAIN_HAND,original.copy());
  check(DollItem.deploy(p,InteractionHand.MAIN_HAND),"deploy succeeds");check(p.getMainHandItem().isEmpty(),"deploy consumes item");
  check(!DollItem.deploy(p,InteractionHand.MAIN_HAND),"repeat deploy rejected");
  var dolls=l.getEntitiesOfClass(DollEntity.class,new AABB(0,100,-4,14,106,4));check(dolls.size()==1,"exactly one companion");var d=dolls.getFirst();
  check(d.ownerId().equals(p.getUUID())&&ItemStack.isSameItemSameComponents(expected,d.storedCopy()),"owner and full stack");
  original = expected;
  check(!d.reclaim(guest)&&!d.isRemoved(),"nonowner cannot reclaim");
  check(d.pat(guest)&&d.getPoseAction()==DollEntity.ACTION_PATTED,"guest may pat");check(!d.pat(p),"simultaneous pat rate limited");
  for(int i=0;i<31;i++)d.tick();check(d.getPoseAction()!=DollEntity.ACTION_PATTED,"pat animation expires");
  CompoundTag tag=new CompoundTag();d.save(tag);var loaded=ModEntities.TWO_I_DOLL.get().create(l);loaded.load(tag);
  check(loaded.ownerId().equals(p.getUUID())&&ItemStack.isSameItemSameComponents(d.storedCopy(),loaded.storedCopy()),"entity NBT roundtrip");
  for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
  check(!d.reclaim(p)&&!d.isRemoved()&&!d.storedCopy().isEmpty(),"full inventory retains entity");
  p.getInventory().setItem(8,ItemStack.EMPTY);check(d.reclaim(p)&&d.isRemoved(),"owner recovers once");
  check(!d.reclaim(p)&&!d.reclaim(guest),"duplicate and stale interactions rejected");
  check(ItemStack.isSameItemSameComponents(original,p.getInventory().getItem(8)),"recovered full components");
  p.getInventory().clearContent();p.getAbilities().instabuild=true;p.setItemInHand(InteractionHand.OFF_HAND,original.copy());
  check(DollItem.deploy(p,InteractionHand.OFF_HAND)&&p.getOffhandItem().isEmpty(),"creative offhand deployment still moves one");
  p.getAbilities().instabuild=false;
  d=l.getEntitiesOfClass(DollEntity.class,new AABB(0,100,-4,14,106,4)).getFirst();check(d.reclaim(p),"creative deployed doll reclaimed");
  p.getInventory().clearContent();
  java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> cancel=event->{if(event.getEntity() instanceof DollEntity)event.setCanceled(true);};
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(cancel);
  try { p.setItemInHand(InteractionHand.MAIN_HAND,original.copy());check(!DollItem.deploy(p,InteractionHand.MAIN_HAND)&&p.getMainHandItem().getCount()==1,"cancelled spawn retains item"); }
  finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(cancel);}
  p.setPos(4,101,0);p.setItemInHand(InteractionHand.MAIN_HAND,original.copy());
  l.setBlockAndUpdate(new net.minecraft.core.BlockPos(4,101,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
  check(!DollItem.deploy(p,InteractionHand.MAIN_HAND)&&p.getMainHandItem().getCount()==1,"blocked deploy retains item");
  l.setBlockAndUpdate(new net.minecraft.core.BlockPos(4,101,0),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
  p.getInventory().clearContent();
  // Real navigation against a loaded platform, using an indexed server-side test player.
  l.addNewPlayer(p);p.setPos(12,101,0);
  var follow=ModEntities.TWO_I_DOLL.get().create(l);follow.initialize(p.getUUID(),original);follow.setPos(1,101,0);l.addFreshEntity(follow);
  double start=follow.distanceToSqr(p);
  for(int i=0;i<120;i++)follow.tick();
  check(follow.distanceToSqr(p)<start-10,"actual AI navigation advances toward owner");
  l.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER);
  for(int i=0;i<12;i++)follow.tick();check(follow.getNavigation().isDone(),"offline owner releases navigation");follow.discard();
  // Save a real live entity; the next process verifies world entity persistence.
  var marker=l.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("companion-uuid.txt");
  var pos=new net.minecraft.core.BlockPos(22,101,0);l.getChunkAt(pos);
  if(java.nio.file.Files.exists(marker)) {
   l.getChunkAt(new net.minecraft.core.BlockPos(22,101,0));
   var id=java.util.UUID.fromString(java.nio.file.Files.readString(marker));var old=l.getEntity(id);
   com.mojang.logging.LogUtils.getLogger().info("DOLL RESTART DEBUG old={} expected={}", old instanceof DollEntity ? ((DollEntity)old).ownerId() : old, p.getUUID());
   check(old instanceof DollEntity&&((DollEntity)old).ownerId().equals(p.getUUID()),"entity owner survived restart");
   check(ItemStack.isSameItemSameComponents(original,((DollEntity)old).storedCopy()),"entity item survived restart");old.discard();
   com.mojang.logging.LogUtils.getLogger().info("COMPANION RESTART CHECKS PASSED");
  }
  var persistent=ModEntities.TWO_I_DOLL.get().create(l);persistent.initialize(p.getUUID(),original);persistent.setPos(22,101,0);persistent.setNoGravity(true);l.addFreshEntity(persistent);
  java.nio.file.Files.writeString(marker,persistent.getUUID().toString());
  com.mojang.logging.LogUtils.getLogger().info("COMPANION CHECKS PASSED: ownership, full inventory, components, creative/offhand, cancellation, duplicate reclaim, pat, follow, disconnect");
 }
}
