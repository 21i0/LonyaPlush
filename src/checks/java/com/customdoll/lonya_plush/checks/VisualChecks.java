package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.DollMod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.logging.LogUtils;
@EventBusSubscriber(modid=DollMod.MODID,value=Dist.CLIENT)
public final class VisualChecks {
 private static int ticks,frames,shots;
 private static boolean initialized,finished;
 private static long captured;
 private static int[] pixels;
 private static int storyScene=-1;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
  if(!Boolean.getBoolean("doll.visual")||finished)return;
  var c=Minecraft.getInstance();
  try {
   if(++ticks>1800)throw new AssertionError("visual timeout");
   if(c.getOverlay()!=null||c.screen==null)return;
   if(!initialized) {
    c.options.guiScale().set(1);c.resizeDisplay();
    DollRenderChecks.validate(c);
    java.nio.file.Files.createDirectories(c.gameDirectory.toPath().resolve("screenshots"));
    c.setScreen(DollRenderChecks.preview());initialized=true;frames=20;return;
   }
   if(frames-->0)return;
   if(storyScene>=0) {
    try(var shot=Screenshot.takeScreenshot(c.getMainRenderTarget())) {
     shot.writeToFile(c.gameDirectory.toPath().resolve("screenshots/lonya-"+LonyaVisualChecks.NAMES[storyScene]+".png"));
    }
    if(++storyScene<LonyaVisualChecks.NAMES.length) {LonyaVisualChecks.show(c,storyScene);frames=10;return;}
    finished=true;LogUtils.getLogger().info("LONYA UI VISUAL CHECKS PASSED: {} scenes, real viewport sizes, backlog pause/restore, zero-time choices, fifth hidden/revealed by branch, compact dialogue, full-screen CG, cooldown countdown/expiry/config sync and review/reclaim availability",LonyaVisualChecks.NAMES.length);LogUtils.getLogger().info("DOLL VISUAL CHECKS PASSED");c.stop();return;
   }
   if(shots==1&&System.nanoTime()-captured<1_200_000_000L)return;
   try(var image=Screenshot.takeScreenshot(c.getMainRenderTarget())) {
    image.writeToFile(c.gameDirectory.toPath().resolve("screenshots/doll-"+(shots+1)+".png"));
    int[] next=new int[image.getWidth()*220];
    for(int y=0;y<220;y++)for(int x=0;x<image.getWidth();x++)next[y*image.getWidth()+x]=image.getPixelRGBA(x,image.getHeight()-220+y);
    if(shots==0){pixels=next;captured=System.nanoTime();}
    else {int changed=0;for(int i=0;i<next.length;i++)if(next[i]!=pixels[i])changed++;if(changed<10)throw new AssertionError("item animation frozen");LogUtils.getLogger().info("ANIMATION PIXELS CHANGED: {}",changed);}
   }
   if(++shots==2){storyScene=0;LonyaVisualChecks.show(c,0);frames=10;}
  }catch(Throwable t){finished=true;LogUtils.getLogger().error("DOLL VISUAL CHECKS FAILED",t);c.stop();}
 }
}
