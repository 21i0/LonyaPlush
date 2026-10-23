package com.customdoll.lonya_plush.content.entity;
import com.customdoll.lonya_plush.init.*;
import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.content.DollUmbrellaProtocol;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** One authoritative server entity stores the complete deployed stack. No client inventory mutation. */
public final class DollEntity extends PathfinderMob {
 public static final int ACTION_GREETING=1, ACTION_CURIOUS=2, ACTION_PATTED=3, ACTION_SWING_FEET=6;
 private static final EntityDataAccessor<Integer> ACTION=SynchedEntityData.defineId(DollEntity.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> ACTION_TICKS=SynchedEntityData.defineId(DollEntity.class,EntityDataSerializers.INT);
 private UUID owner;
 private ItemStack stored=ItemStack.EMPTY;
 private boolean reclaimed;
 private boolean sharedUmbrella;
 public DollEntity(EntityType<? extends DollEntity> type,Level level) { super(type,level); setPersistenceRequired(); }
 public static AttributeSupplier.Builder createAttributes() { return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.30).add(Attributes.FOLLOW_RANGE,32); }
 @Override protected void defineSynchedData(SynchedEntityData.Builder b) { super.defineSynchedData(b);b.define(ACTION,0);b.define(ACTION_TICKS,0); }
 @Override protected void registerGoals() {
  goalSelector.addGoal(0,new FloatGoal(this));
  goalSelector.addGoal(1,new Goal() {
   { setFlags(java.util.EnumSet.of(Goal.Flag.MOVE,Goal.Flag.LOOK)); }
   private Player target() { return owner==null?null:level().getPlayerByUUID(owner); }
   @Override public boolean canUse() { var p=target();return !com.customdoll.lonya_plush.story.LonyaStoryService.busy(DollEntity.this)&&p!=null&&p.isAlive()&&!p.isSpectator()&&getPoseAction()!=ACTION_PATTED&&distanceToSqr(p)>100; }
   @Override public boolean canContinueToUse() { var p=target();return !com.customdoll.lonya_plush.story.LonyaStoryService.busy(DollEntity.this)&&p!=null&&p.isAlive()&&!p.isSpectator()&&getPoseAction()!=ACTION_PATTED&&distanceToSqr(p)>16; }
   @Override public void tick() { var p=target();if(p!=null) { getLookControl().setLookAt(p,30,30);if(tickCount%10==0)getNavigation().moveTo(p,1.2); } }
   @Override public void stop() { getNavigation().stop(); }
  });
  goalSelector.addGoal(3,new RandomLookAroundGoal(this));
 }
 public void initialize(UUID id,ItemStack stack) { owner=id;stored=stack.copyWithCount(1); addTag(DollUmbrellaProtocol.ENTITY_TAG); }
 public UUID ownerId() { return owner; }
 public ItemStack storedCopy() { return stored.copy(); }
 public boolean isSharedUmbrella() { return sharedUmbrella; }
 public void setSharedUmbrella(boolean value) { sharedUmbrella = value; }
 public int getPoseAction() { return entityData.get(ACTION); }
 public float getPoseActionProgress(float partial) { return net.minecraft.util.Mth.clamp((30-entityData.get(ACTION_TICKS)+partial)/30F,0,1); }
 public boolean pat(Player player) {
  if(level().isClientSide||!isAlive()||reclaimed||owner==null||player.isSpectator()||!player.isAlive()||player.level()!=level()||distanceToSqr(player)>36||entityData.get(ACTION_TICKS)>0)return false;
  entityData.set(ACTION,ACTION_PATTED);entityData.set(ACTION_TICKS,30);getNavigation().stop();
  playSound(ModEntities.PAT.get(),.65F,.95F+random.nextFloat()*.10F);return true;
 }
 public boolean reclaim(Player player) {
  if(level().isClientSide||!isAlive()||reclaimed||stored.isEmpty()||!player.isAlive()||player.isSpectator()||player.level()!=level()||distanceToSqr(player)>36)return false;
  if(!player.getUUID().equals(owner)) { player.displayClientMessage(Component.translatable("message.lonya_plush.owner"),true);return false; }
  // The item is unstackable: reserve an empty slot before changing either form.
  int slot=player.getInventory().getFreeSlot();
  if(slot<0) {player.displayClientMessage(Component.translatable("message.lonya_plush.full"),true);return false;}
  reclaimed=true;
  ItemStack result=stored;stored=ItemStack.EMPTY;
  player.getInventory().setItem(slot,result);player.getInventory().setChanged();
  discard();return true;
 }
 @Override protected InteractionResult mobInteract(Player p,InteractionHand hand) {
  if(!level().isClientSide) {
   if(p.isShiftKeyDown()&&p instanceof net.minecraft.server.level.ServerPlayer player)
    com.customdoll.lonya_plush.story.LonyaStoryService.open(player,this);
   else pat(p);
  }
  return InteractionResult.sidedSuccess(level().isClientSide);
 }
 @Override public void tick() {
  super.tick();
  if(!level().isClientSide) {
   if(owner!=null&&level().getPlayerByUUID(owner) instanceof net.minecraft.server.level.ServerPlayer player)
    com.customdoll.lonya_plush.story.LonyaStoryService.accompany(player,this);
   if (tickCount % 10 == 0) sharedUmbrella = DollUmbrellaProtocol.sample(level(), DollUmbrellaProtocol.entity(this)).shared();
   int left=entityData.get(ACTION_TICKS);
   if(left>0) {entityData.set(ACTION_TICKS,left-1);if(left==1)entityData.set(ACTION,0);}
   else if(tickCount%200==0) {entityData.set(ACTION,random.nextBoolean()?ACTION_GREETING:ACTION_CURIOUS);entityData.set(ACTION_TICKS,30);}
  }
 }
 @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount) { return false; }
 @Override public boolean removeWhenFarAway(double distance) { return false; }
 @Override protected void checkFallDamage(double y,boolean onGround,net.minecraft.world.level.block.state.BlockState state,net.minecraft.core.BlockPos pos) { }
 @Override public ItemStack getPickResult() { return storedCopy(); }
 @Override public void addAdditionalSaveData(CompoundTag tag) {
  super.addAdditionalSaveData(tag);if(owner!=null)tag.putUUID("Owner",owner);
  if(!stored.isEmpty())tag.put("Doll",stored.save(registryAccess()));
  tag.putBoolean("SharedUmbrella", sharedUmbrella);
 }
 @Override public void readAdditionalSaveData(CompoundTag tag) {
  super.readAdditionalSaveData(tag);owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;
  ItemStack decoded=ItemStack.parseOptional(registryAccess(),tag.getCompound("Doll"));
  stored=decoded.is(ModItems.TWO_I_DOLL.get())?decoded.copyWithCount(1):ItemStack.EMPTY;
  addTag(DollUmbrellaProtocol.ENTITY_TAG);
  sharedUmbrella=tag.getBoolean("SharedUmbrella");
 }
}
