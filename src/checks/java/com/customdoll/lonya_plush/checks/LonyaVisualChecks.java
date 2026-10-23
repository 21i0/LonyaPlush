package com.customdoll.lonya_plush.checks;

import com.customdoll.lonya_plush.client.LonyaScreen;
import com.customdoll.lonya_plush.story.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import org.lwjgl.glfw.GLFW;

/** Real framebuffer sizes and production widgets; no fixtures modify a player's unlocks. */
public final class LonyaVisualChecks {
    public static final String[] NAMES={"hub","chapter-1","chapter-2","chapter-3","chapter-4-choice","chapter-5",
            "library-before-branch","backlog-latest","backlog-earliest","chapter-recap","hidden-cg",
            "narrow-reading","narrow-choice","narrow-library","narrow-backlog","widescreen-reading","rainbow-umbrella","library-after-branch",
            "cooldown-hub","cooldown-library","cooldown-narrow","cooldown-expired"};
    private static void field(Object o,String name,Object value) throws Exception {
        var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    private static Object get(Object o,String name) throws Exception {
        var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    public static void show(Minecraft client,int scene) throws Exception {
        boolean narrow=scene>=11&&scene<=14||scene==20,wide=scene==15;
        GLFW.glfwSetWindowSize(client.getWindow().getWindow(),narrow?960:wide?1600:1440,narrow?720:wide?900:960);
        GLFW.glfwPollEvents();client.options.guiScale().set(narrow?3:2);client.resizeDisplay();
        if(scene==16) {client.setScreen(umbrella());return;}
        int chapter=scene>=1&&scene<=5?scene-1:scene==12?3:scene==9?2:scene==17?4:0;
        int line=chapter==3?LonyaScript.chapter(chapter).lines().size()-1:scene==7||scene==8||scene==14?14:0;
        var state=new JsonObject();state.addProperty("entity",1);state.addProperty("token",9L);state.addProperty("revision",1);
        state.addProperty("chapter",chapter);state.addProperty("line",line);state.addProperty("read_ms",0);
        state.addProperty("completed",15);state.addProperty("fifth_unlocked",chapter==4);state.addProperty("ended",false);
        state.addProperty("mode",scene==0||scene>=18?0:1);state.addProperty("open",true);state.addProperty("message","");
        state.addProperty("cooldown_remaining_ms",scene>=18?1_200_000:0);
        var constructor=LonyaScreen.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        var screen=constructor.newInstance(state);
        field(screen,"automatic",chapter==3);field(screen,"fastForward",chapter==3);
        field(screen,"fullText",true);client.setScreen(screen);
        if(scene>=18) {
            var talk=(Button)get(screen,"talkButton");
            check(!talk.active&&talk.getMessage().getString().contains("20:00"),"cooldown disables talk and shows synchronized countdown");
            check(screen.children().stream().anyMatch(c->c instanceof Button b&&b.active&&b.getMessage().getString().equals("回收玩偶")),"reclaim stays active during cooldown");
            if(scene==19) {
                screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("历史章节")).map(c->(Button)c).findFirst().orElseThrow().onPress();
                var replay=(Button)get(screen,"replayButton");
                check(!replay.active&&replay.getMessage().getString().contains("冷却"),"chapter replay respects same cooldown");
                check(screen.children().stream().anyMatch(c->c instanceof Button b&&b.active&&b.getMessage().getString().equals("查看 CG")),"read-only CG review remains available");
            }
            if(scene==21) {
                state.addProperty("cooldown_remaining_ms",1);field(screen,"cooldownSnapshotNanos",System.nanoTime()-2_000_000L);screen.tick();
                check(talk.active&&talk.getMessage().getString().equals("聊聊天"),"countdown expiry automatically restores talk without reopening screen");
                state.addProperty("cooldown_remaining_ms",1_200_000);state.addProperty("open",false);
                LonyaScreen.accept(state.toString());check(!talk.active,"periodic server update can reapply cooldown without revision change");
                state.addProperty("cooldown_remaining_ms",0);LonyaScreen.accept(state.toString());
                check(talk.active,"configuration change removes cooldown on same-revision snapshot");
                field(screen,"waiting",true);LonyaScreen.accept(state.toString());
                check((boolean)get(screen,"waiting")&&!talk.active,"periodic snapshots never unlock an in-flight action");
                field(screen,"waiting",false);screen.tick();
            }
        }
        if(scene==6||scene==9||scene==13||scene==17) {
            var b=screen.children().stream().filter(c->c instanceof Button bt&&bt.getMessage().getString().equals("章节回顾")).map(c->(Button)c).findFirst().orElseThrow();
            b.onPress();
            if(scene==9) {
                field(screen,"galleryChapter",2);
                screen.children().stream().filter(c->c instanceof Button bt&&bt.getMessage().getString().equals("查看 CG")).map(c->(Button)c).findFirst().orElseThrow().onPress();
            }
        }
        if(scene==7||scene==8||scene==14) {
            field(screen,"fullText",false);field(screen,"revealTicks",12);
            field(screen,"automatic",true);field(screen,"fastForward",true);
            screen.mouseScrolled(10,10,0,1);
            check((int)get(screen,"page")==3,"scroll opens backlog");
            check(get(screen,"scroll").equals(get(screen,"historyMax")),"backlog opens at most recent entry");
            for(int i=0;i<20;i++)screen.tick();
            check((int)get(screen,"revealTicks")==12,"backlog pauses typing/autoplay/fast forward");
            screen.mouseClicked(10,10,1);
            check((int)get(screen,"page")==1&&(int)get(screen,"revealTicks")==12,"backlog returns to exact reading position");
            field(screen,"automatic",false);field(screen,"fastForward",false);
            screen.mouseScrolled(10,10,0,1);
            if(scene==8)screen.keyPressed(GLFW.GLFW_KEY_HOME,0,0);
        }
        if(scene==10) {
            screen.mouseClicked(10,10,1);
            check((boolean)get(screen,"hidden")&&screen.children().isEmpty(),"right click hides all UI");
            for(int i=0;i<5;i++)screen.tick();
        }
        if(chapter==3) {
            check(!(boolean)get(screen,"automatic")&&!(boolean)get(screen,"fastForward"),"choice pauses autoplay and fast forward");
            check(screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().contains("· 开")),"choice controls display playback as off");
            var choices=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().contains("不耐烦")).map(c->(Button)c).toList();
            check(choices.size()==1&&choices.getFirst().active,"branch available with zero reading time");
        }
        for(var child:screen.children())if(child instanceof Button b) {
            check(b.getX()>=0&&b.getY()>=0&&b.getX()+b.getWidth()<=screen.width&&b.getY()+b.getHeight()<=screen.height,
                    "button outside screen: "+b.getMessage().getString()+" at "+screen.width+"x"+screen.height);
        }
        if(scene==6||scene==13) {
            check(screen.children().stream().noneMatch(c->c instanceof Button b&&b.getMessage().getString().startsWith("05")),"fifth chapter has no visible row before correct answer");
            check(screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().matches("^0[1-5]  .*" )).count()==4,"exactly four chapter cards before correct answer");
        }
        if(scene==17)check(screen.children().stream().anyMatch(c->c instanceof Button b&&b.getMessage().getString().startsWith("05")&&b.active),"fifth chapter appears after correct answer");
        if(scene==11) {
            check(screen.width==320&&screen.height==240,"real narrow viewport 320x240, got "+screen.width+"x"+screen.height);
            int available=(int)get(screen,"dialogueH")-56;
            for(var ch:LonyaScript.CHAPTERS)for(var l:ch.lines()) {
                int rows=client.font.split(net.minecraft.network.chat.Component.literal(l.text()),screen.width-50).size();
                check((rows-1)*10+9<=available,"narrow dialogue clipping: "+l.text());
            }
        }
        if(scene==1||scene==15)check((int)get(screen,"dialogueH")<=screen.height*.29,"compact dialogue height");
        com.mojang.logging.LogUtils.getLogger().info("LONYA UI SCENE {}: framebuffer {}x{}, GUI {}x{}",NAMES[scene],client.getWindow().getWidth(),client.getWindow().getHeight(),screen.width,screen.height);
    }
    private static Screen umbrella() {
        return new Screen(net.minecraft.network.chat.Component.literal("Lonya rainbow canopy")) {
            @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial) {
                g.fill(0,0,width,height,0xFFD1E0E8);
                var pose=g.pose();pose.pushPose();pose.translate(width/2.0,height*.42,100);
                pose.scale(120,120,120);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(35));
                var buffers=minecraft.renderBuffers().bufferSource();
                com.customdoll.lonya_plush.client.render.DollUmbrellaCanopy.render(pose,buffers,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                buffers.endBatch();pose.popPose();
                g.drawCenteredString(font,"Lonya rainbow canopy",width/2,height-35,0xFF27384A);
            }
        };
    }
}
