package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.content.*;
import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;
public final class CuriosChecks {
 public static void run(ServerLevel level) throws Exception {
  var owner=player(level,"CuriosOwner");var guest=player(level,"CuriosGuest");
  var inventory=CuriosApi.getCuriosInventory(owner).orElseThrow();
  check(inventory.getCurios().keySet().containsAll(java.util.List.of("head","ring","back","doll_custom")),"Curios native slots loaded");
  ItemStack doll=specimen();
  for(var entry:inventory.getCurios().entrySet()) {
   var handler=entry.getValue();
   for(int i=0;i<handler.getStacks().getSlots();i++) {
    var menu=new CurioSlot(owner,handler.getStacks(),i,entry.getKey(),0,0,handler.getRenders(),handler.getActiveStates(),true,true,false);
    check(menu.mayPlace(doll)&&handler.getStacks().isItemValid(i,doll),"actual Curios menu and handler validation "+entry.getKey());
    check(DollOwnership.get(doll)==null,"simulation does not claim stack");
   }
  }
  var custom=inventory.getStacksHandler("doll_custom").orElseThrow();
  check(!custom.getStacks().isItemValid(0,new ItemStack(Items.DIAMOND)),"ordinary item still rejected");
  var context=new SlotContext("doll_custom",owner,0,false,true);
  check(!CuriosApi.isStackValid(context,doll),"raw restrictive validator rejects doll before compat event");
  check(custom.getStacks().insertItem(0,doll,false).isEmpty(),"actual Curios insert");
  new top.theillusivec4.curios.common.event.CuriosEventHandler().tick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(owner));
  ItemStack equipped=custom.getStacks().getStackInSlot(0);
  check(owner.getUUID().equals(DollOwnership.get(equipped)),"actual Curios tick binds owner");
  check(DollAccessoryBridge.isEquipped(owner,equipped),"optional equipped bridge");
  var foreign=CuriosApi.getCuriosInventory(guest).orElseThrow().getStacksHandler("doll_custom").orElseThrow();
  check(!foreign.getStacks().isItemValid(0,equipped)&&!foreign.getCosmeticStacks().isItemValid(0,equipped),"nonowner accessory and cosmetic equip denied");
  var saved=inventory.saveInventory(false);
  ItemStack before=equipped.copy();
  custom.getStacks().setStackInSlot(0,ItemStack.EMPTY);
  inventory.loadInventory(saved);
  check(ItemStack.isSameItemSameComponents(before,inventory.getStacksHandler("doll_custom").orElseThrow().getStacks().getStackInSlot(0)),"Curios NBT roundtrip full components");
  var path=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("lonya_plush-curios.nbt");
  if(java.nio.file.Files.exists(path)) {
   inventory.loadInventory(net.minecraft.nbt.NbtIo.read(path).getList("Inventory",10));
   check(ItemStack.isSameItemSameComponents(before,inventory.getStacksHandler("doll_custom").orElseThrow().getStacks().getStackInSlot(0)),"prior process Curios components restored");
   com.mojang.logging.LogUtils.getLogger().info("CURIOS RESTART CHECKS PASSED");
  }
  var nbt=new net.minecraft.nbt.CompoundTag();nbt.put("Inventory",saved);net.minecraft.nbt.NbtIo.write(nbt,path);
  inventory.getCurios().values().forEach(h->{for(int i=0;i<h.getStacks().getSlots();i++){h.getStacks().setStackInSlot(i,ItemStack.EMPTY);h.getCosmeticStacks().setStackInSlot(i,ItemStack.EMPTY);}});
  com.mojang.logging.LogUtils.getLogger().info("CURIOS CHECKS PASSED: native slots, restrictive validation, owner, equip tick, full components");
 }
}

