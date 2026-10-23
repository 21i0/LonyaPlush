package com.customdoll.lonya_plush;
import com.customdoll.lonya_plush.init.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
@Mod(DollMod.MODID)
public final class DollMod {
 public static final String MODID = "lonya_plush";
 public DollMod(IEventBus bus, net.neoforged.fml.ModContainer container) {
  container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,
    com.customdoll.lonya_plush.story.LonyaStoryConfig.SPEC, "lonya_plush-common.toml");
  bus.addListener(com.customdoll.lonya_plush.story.LonyaNetwork::register);
  bus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> {
   if (net.neoforged.fml.ModList.get().isLoaded("curios"))
    event.enqueueWork(com.customdoll.lonya_plush.compat.DollCuriosCompat::register);
  });
  ModBlocks.BLOCKS.register(bus); ModItems.ITEMS.register(bus); ModBlockEntities.BLOCK_ENTITIES.register(bus);
  ModEntities.ENTITIES.register(bus); ModEntities.SOUNDS.register(bus); bus.addListener(ModEntities::attributes);
  bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
   if (event.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.TOOLS_AND_UTILITIES)) event.accept(ModItems.TWO_I_DOLL.get());
  });
 }
}
