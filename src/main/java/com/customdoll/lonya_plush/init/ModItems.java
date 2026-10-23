package com.customdoll.lonya_plush.init;
import com.customdoll.lonya_plush.DollMod;
public final class ModItems {
public static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS = net.neoforged.neoforge.registries.DeferredRegister.createItems(DollMod.MODID);
 public static final net.neoforged.neoforge.registries.DeferredItem<com.customdoll.lonya_plush.content.item.DollItem> TWO_I_DOLL = ITEMS.register("lonya_plush_doll", com.customdoll.lonya_plush.content.item.DollItem::new);
}