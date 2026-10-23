package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.client.LonyaScreen;
import com.customdoll.lonya_plush.story.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid=DollMod.MODID,value=Dist.CLIENT)
public final class LonyaNetworkClientChecks {
 private static int ticks,stage,delay,pausedReveal,pausedLine;
 private static boolean connecting,finished;
 private static Object get(LonyaScreen screen,String name) throws Exception {var f=LonyaScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
 private static JsonObject state(LonyaScreen screen) throws Exception {return (JsonObject)get(screen,"state");}
 private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
 private static void click(LonyaScreen screen,String label) {
  var button=screen.children().stream().filter(x->x instanceof Button b&&b.getMessage().getString().contains(label)).map(x->(Button)x).findFirst().orElseThrow();
  check(button.active,"inactive wire button: "+label);
  double x=button.getX()+button.getWidth()/2.0,y=button.getY()+button.getHeight()/2.0;
  screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);
 }
 private static void shot(Minecraft client,String name) throws Exception {
  var dir=client.gameDirectory.toPath().resolve("screenshots");java.nio.file.Files.createDirectories(dir);
  try(var image=Screenshot.takeScreenshot(client.getMainRenderTarget())) {image.writeToFile(dir.resolve(name+".png"));}
 }
 @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
  if(!Boolean.getBoolean("lonya.network.client")||finished)return;var client=Minecraft.getInstance();
  try {
   if(++ticks>2400)throw new AssertionError("network UI timeout");
   if(!connecting&&ticks>100&&client.getOverlay()==null&&client.player==null) {
    connecting=true;String address="127.0.0.1:25595";
    com.mojang.logging.LogUtils.getLogger().info("LONYA NETWORK CLIENT CONNECTING {}",address);
    client.options.guiScale().set(2);client.resizeDisplay();
    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(client.screen,client,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("Lonya fixture",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);return;
   }
   if(stage==7&&!(client.screen instanceof LonyaScreen)&&client.player!=null) {
    if(client.player.getInventory().countItem(com.customdoll.lonya_plush.init.ModItems.TWO_I_DOLL.get())!=1)return;
    finished=true;com.mojang.logging.LogUtils.getLogger().info("LONYA NETWORK CLIENT CHECKS PASSED: actual mouse/keyboard, instant reveal/next, paused backlog, fast forward through chapter, synchronized twenty-minute cooldown, forged continue rejected, read-only CG and owner reclaim");client.stop();return;
   }
   if(!(client.screen instanceof LonyaScreen screen))return;
   var state=state(screen);
   if(stage==0&&++delay>15) {shot(client,"wire-hub");click(screen,"聊聊天");stage=1;delay=0;}
   else if(stage==1&&state.get("mode").getAsInt()==1&&++delay>2) {
    check(state.get("line").getAsInt()==0,"fresh story starts at zero");
    screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
    check((boolean)get(screen,"fullText")&&state.get("line").getAsInt()==0,"first enter reveals text");
    screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);stage=2;delay=0;
   } else if(stage==2&&state.get("line").getAsInt()==1&&++delay>3) {
    check(state.get("read_ms").getAsLong()<LonyaScript.chapter(0).lines().getFirst().minimumMillis(),"actual next needs no mandatory wait");
    shot(client,"wire-instant-next");click(screen,"自动");
    screen.mouseScrolled(50,50,0,1);
    check((int)get(screen,"page")==3,"wheel opens actual backlog");
    pausedReveal=(int)get(screen,"revealTicks");pausedLine=state.get("line").getAsInt();stage=3;delay=0;
   } else if(stage==3&&++delay>25) {
    check(state.get("line").getAsInt()==pausedLine&&(int)get(screen,"revealTicks")==pausedReveal,"backlog pauses and preserves exact position");
    shot(client,"wire-backlog");screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
    check((int)get(screen,"page")==1,"esc returns to story");
    click(screen,"自动");click(screen,"快进");stage=4;delay=0;
   } else if(stage==4&&state.get("line").getAsInt()>=4) {
    click(screen,"快进");shot(client,"wire-fast-forward");stage=5;delay=0;
   } else if(stage==5&&++delay>12) {
    check(!(boolean)get(screen,"fastForward"),"fast forward toggles off");
    click(screen,"快进");stage=8;delay=0;
   } else if(stage==8&&state.get("chapter").getAsInt()==1&&state.get("mode").getAsInt()==0&&++delay>10) {
    check(state.get("cooldown_remaining_ms").getAsLong()>1_100_000,"server sent twenty-minute chapter cooldown");
    var talk=(Button)get(screen,"talkButton");check(!talk.active&&talk.getMessage().getString().contains("休息中"),"real talk widget disabled during cooldown");
    shot(client,"wire-chapter-cooldown");
    net.neoforged.neoforge.network.PacketDistributor.sendToServer(new LonyaNetwork.Request(state.get("entity").getAsInt(),state.get("token").getAsLong(),state.get("revision").getAsInt(),LonyaStoryService.CONTINUE,0));
    stage=9;delay=0;
   } else if(stage==9&&++delay>20) {
    check(state.get("mode").getAsInt()==0&&state.get("line").getAsInt()==0&&((String)get(screen,"message")).contains("正在休息"),"server rejects forged continue during cooldown");
    click(screen,"历史章节");
    click(screen,"01  ");
    check(!((Button)get(screen,"replayButton")).active,"real replay widget disabled during cooldown");
    click(screen,"查看 CG");shot(client,"wire-review-during-cooldown");screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
    stage=6;delay=0;
   } else if(stage==6&&state.get("mode").getAsInt()==0&&++delay>10) {
    shot(client,"wire-before-reclaim");click(screen,"回收玩偶");stage=7;delay=0;
   }
  }catch(Throwable failure){finished=true;com.mojang.logging.LogUtils.getLogger().error("LONYA NETWORK CLIENT CHECKS FAILED",failure);client.stop();}
 }
}
