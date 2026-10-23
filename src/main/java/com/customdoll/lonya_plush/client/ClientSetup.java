package com.customdoll.lonya_plush.client;
import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.client.render.*;
import com.customdoll.lonya_plush.init.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
@EventBusSubscriber(modid=DollMod.MODID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
 @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
  event.enqueueWork(() -> com.customdoll.lonya_plush.story.LonyaNetwork.CLIENT = LonyaScreen::accept);
  if (net.neoforged.fml.ModList.get().isLoaded("curios"))
   event.enqueueWork(DollCurioRenderer::register);
 }
 @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) { e.registerLayerDefinition(DollRenderer.LAYER, DollRenderer.Model::createLayer); }
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
  e.registerBlockEntityRenderer(ModBlockEntities.TWO_I_DOLL_DISPLAY.get(), DollDisplayRenderer::new);
  e.registerEntityRenderer(ModEntities.TWO_I_DOLL.get(), DollRenderer::new);
 }
 @SubscribeEvent public static void items(RegisterClientExtensionsEvent e) { e.registerItem(new IClientItemExtensions() {
  @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return DollItemRenderer.get(); }
 },ModItems.TWO_I_DOLL.get()); }
 @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e) { e.registerReloadListener(DollItemRenderer.get()); }
}
