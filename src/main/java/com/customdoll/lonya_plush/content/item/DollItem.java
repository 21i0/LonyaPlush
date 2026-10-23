package com.customdoll.lonya_plush.content.item;
import com.customdoll.lonya_plush.init.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import com.customdoll.lonya_plush.content.DollOwnership;
import com.customdoll.lonya_plush.content.DollUmbrellaProtocol;
public final class DollItem extends BlockItem {
 public DollItem() { super(ModBlocks.TWO_I_DOLL_DISPLAY.get(), new Properties().stacksTo(1)); }
 @Override public EquipmentSlot getEquipmentSlot(ItemStack s) { return EquipmentSlot.HEAD; }
 @Override public boolean canEquip(ItemStack s, EquipmentSlot slot, LivingEntity e) { return slot == EquipmentSlot.HEAD && (!(e instanceof Player p) || DollOwnership.mayUse(s, p)); }
 @Override public InteractionResult place(net.minecraft.world.item.context.BlockPlaceContext context) {
  Player p = context.getPlayer();
  if (p != null && !DollOwnership.mayUse(context.getItemInHand(), p)) { DollOwnership.deny(p); return InteractionResult.FAIL; }
  return super.place(context);
 }
 @Override public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
  if (!level.isClientSide && entity instanceof Player p && p.getItemBySlot(EquipmentSlot.HEAD) == stack && DollOwnership.mayUse(stack, p)) DollOwnership.claim(stack, p);
 }
 @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
  if (!DollOwnership.mayUse(player.getItemInHand(hand), player)) { DollOwnership.deny(player); return InteractionResultHolder.fail(player.getItemInHand(hand)); }
  if (!level.isClientSide) { DollOwnership.claim(player.getItemInHand(hand), player); DollUmbrellaProtocol.markUmbrellaParticipant(player.getItemInHand(hand)); }
  if (player.isShiftKeyDown()) {
   if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) deploy(sp,hand);
   return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
  }
  return InteractionResultHolder.pass(player.getItemInHand(hand));
 }
 @Override public void appendHoverText(ItemStack s, TooltipContext ctx, java.util.List<Component> lines, TooltipFlag flags) {
  lines.add(Component.translatable("tooltip.lonya_plush.lonya_plush_doll").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
  lines.add(Component.translatable("tooltip.lonya_plush.controls").withStyle(net.minecraft.ChatFormatting.GRAY));
 }
 public static boolean deploy(net.minecraft.server.level.ServerPlayer player, InteractionHand hand) {
  ItemStack stack=player.getItemInHand(hand);
  if (!player.isAlive() || player.isSpectator() || !stack.is(ModItems.TWO_I_DOLL.get())) return false;
  if (!DollOwnership.mayUse(stack, player)) { DollOwnership.deny(player); return false; }
  DollOwnership.claim(stack, player); DollUmbrellaProtocol.markUmbrellaParticipant(stack);
  var level=player.serverLevel();
  var doll=ModEntities.TWO_I_DOLL.get().create(level);
  if (doll==null) return false;
  var look=player.getLookAngle();
  doll.moveTo(player.getX()+look.x*0.9,player.getY(),player.getZ()+look.z*0.9,player.getYRot(),0);
  if (!level.getWorldBorder().isWithinBounds(doll.getBoundingBox()) || !level.noCollision(doll) || level.containsAnyLiquid(doll.getBoundingBox())) return false;
  doll.initialize(player.getUUID(),stack.copyWithCount(1));
  if (!level.addFreshEntity(doll)) return false;
  // Even creative deployment moves this exact doll. Pick-block remains vanilla creative cloning.
  stack.shrink(1);
  player.getInventory().setChanged();
  return true;
 }
}
