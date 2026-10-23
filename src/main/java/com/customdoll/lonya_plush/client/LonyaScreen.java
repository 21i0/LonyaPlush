package com.customdoll.lonya_plush.client;

import com.customdoll.lonya_plush.DollMod;
import com.customdoll.lonya_plush.story.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Full-bleed CG reading, a passive backlog, and an independent AE-inspired doll menu. */
public final class LonyaScreen extends Screen {
    private static final int PANEL=0xFFCBCCD4,LIGHT=0xFFF2F2F2,SLOT=0xFFADB0C4,
            SHADOW=0xFF9A9FB4,INK=0xFF413F54,MUTED=0xFF6D7287,BLUE=0xFF517497;
    private JsonObject state;
    // 0: interaction menu; 1: story; 2: chapter library / CG; 3: backlog overlay.
    private int page,returnPage,ticks,scroll,galleryChapter,textSpeed=2,revealTicks;
    private boolean automatic,fastForward,waiting,fullText,gallery,hidden,draggingScroll;
    private String message="";
    private Button next,talkButton,replayButton;
    private long cooldownSnapshotNanos=System.nanoTime();
    private float unit=1;
    private int x,y,w,h,dialogueY,dialogueH,historyLimit,historyMax;
    private final List<Button> choiceButtons=new ArrayList<>();
    private final List<HistoryEntry> history=new ArrayList<>();
    private record HistoryEntry(int chapter,int line,String speaker,String text) { }
    private record HistoryRow(FormattedCharSequence text,int color) { }

    private LonyaScreen(JsonObject state) {
        super(Component.literal("lonya plush · 晴色日常"));
        this.state=state;page=number("mode");returnPage=page;galleryChapter=number("chapter");seedHistory();
    }
    public static void accept(String raw) {
        var state=JsonParser.parseString(raw).getAsJsonObject();var client=Minecraft.getInstance();
        if(state.has("closed")) {if(client.screen instanceof LonyaScreen)client.setScreen(null);return;}
        if(state.get("open").getAsBoolean()) {client.setScreen(new LonyaScreen(state));return;}
        if(!(client.screen instanceof LonyaScreen screen)||screen.number("entity")!=state.get("entity").getAsInt())return;
        boolean changed=screen.number("revision")!=state.get("revision").getAsInt();
        int oldChapter=screen.number("chapter"),oldLine=screen.number("line");
        screen.state=state;screen.cooldownSnapshotNanos=System.nanoTime();String note=state.get("message").getAsString();
        if(!note.isBlank()) {screen.message=note;screen.waiting=false;}
        // A periodic snapshot cannot release an in-flight action before its acknowledgement.
        if(changed) {
            screen.page=state.get("mode").getAsInt();screen.returnPage=screen.page;
            screen.gallery=false;screen.hidden=false;screen.waiting=false;screen.fullText=false;screen.revealTicks=0;
            if(screen.page==1) {
                if(oldChapter!=screen.number("chapter")||oldLine!=screen.number("line")||screen.history.isEmpty())screen.recordLine();
                if(screen.branch()) {screen.automatic=false;screen.fastForward=false;}
                client.getNarrator().sayNow(Component.literal(screen.line().speaker()+"："+screen.line().text()));
            } else {screen.automatic=false;screen.fastForward=false;}
            screen.rebuildWidgets();
        }
        screen.updateEntryButtons();
    }
    private int number(String key) {return state.get(key).getAsInt();}
    private long cooldownMillis() {
        long received=state.has("cooldown_remaining_ms")?state.get("cooldown_remaining_ms").getAsLong():0;
        return Math.max(0,received-Math.max(0,(System.nanoTime()-cooldownSnapshotNanos)/1_000_000));
    }
    private void updateEntryButtons() {
        long remaining=cooldownMillis();boolean cooling=remaining>0;
        if(talkButton!=null) {
            talkButton.active=!flag("ended")&&!waiting&&!cooling;
            talkButton.setMessage(Component.literal(cooling?"休息中 · "+LonyaStoryConfig.countdown(remaining):flag("ended")?"路线已结束":"聊聊天"));
        }
        if(replayButton!=null) {
            replayButton.active=unlocked(galleryChapter)&&!waiting&&!cooling;
            replayButton.setMessage(Component.literal(cooling?"冷却 "+LonyaStoryConfig.countdown(remaining):"从头重玩"));
        }
    }
    private boolean flag(String key) {return state.get(key).getAsBoolean();}
    private LonyaScript.Line line() {return LonyaScript.chapter(number("chapter")).lines().get(number("line"));}
    private boolean unlocked(int i) {return i==0||(i==4?flag("fifth_unlocked"):(number("completed")&(1<<(i-1)))!=0);}
    private boolean completed(int i) {return (number("completed")&(1<<i))!=0;}
    private int visibleChapters() {return flag("fifth_unlocked")?5:4;}
    private boolean last() {return number("line")==LonyaScript.chapter(number("chapter")).lines().size()-1;}
    private boolean branch() {return number("chapter")==3&&last();}
    private int s(float n) {return Math.round(n*unit);}
    private void seedHistory() {
        for(int c=0;c<=number("chapter");c++) {
            if(c<number("chapter")&&!completed(c))continue;
            var lines=LonyaScript.chapter(c).lines();int end=c==number("chapter")?number("line"):lines.size()-1;
            for(int i=0;i<=end;i++) {var l=lines.get(i);history.add(new HistoryEntry(c,i,l.speaker(),l.text()));}
        }
        while(history.size()>512)history.removeFirst();
    }
    private void recordLine() {
        int c=number("chapter"),i=number("line");
        if(!history.isEmpty()) {var tail=history.getLast();if(tail.chapter==c&&tail.line==i)return;}
        history.add(new HistoryEntry(c,i,line().speaker(),line().text()));
        while(history.size()>512)history.removeFirst();
    }
    private void request(int action,int value) {
        boolean passive=action==LonyaStoryService.HEARTBEAT||action==LonyaStoryService.CLOSE;
        if(waiting&&!passive)return;
        PacketDistributor.sendToServer(new LonyaNetwork.Request(number("entity"),state.get("token").getAsLong(),number("revision"),action,value));
        if(!passive)waiting=true;
    }
    private void pauseReading() {if(minecraft.player!=null)request(LonyaStoryService.HEARTBEAT,0);}
    private Button button(String label,int px,int py,int bw,Button.OnPress press,boolean ae) {
        return button(label,px,py,bw,s(22),press,ae,()->false);
    }
    private Button button(String label,int px,int py,int bw,int bh,Button.OnPress press,boolean ae,BooleanSupplier selected) {
        return addRenderableWidget(new StyledButton(label,px,py,bw,bh,press,ae,selected));
    }
    private void openLibrary() {
        if(waiting)return;
        returnPage=page;page=2;gallery=false;galleryChapter=number("chapter");pauseReading();rebuildWidgets();
    }
    private void openHistory() {
        if(page!=1||waiting)return;
        returnPage=1;page=3;pauseReading();rebuildWidgets();scroll=historyMax;
    }
    private void back() {
        if(page==2&&gallery)gallery=false;else page=returnPage;
        draggingScroll=false;rebuildWidgets();
    }
    @Override protected void init() {
        unit=Math.max(1,Math.min(width/800f,height/450f));
        dialogueH=Math.min(s(108),Math.max(s(width<s(480)?96:88),Math.round(height*.24f)));
        dialogueY=height-dialogueH-s(10);
        choiceButtons.clear();next=null;talkButton=null;replayButton=null;
        if(page==1&&branch()) {automatic=false;fastForward=false;}
        if(page==0) {
            w=Math.min(width-s(24),s(430));h=Math.min(height-s(20),s(250));x=(width-w)/2;y=(height-h)/2;
            button("关闭",x+w-s(48),y+s(5),s(39),s(18),b->onClose(),true,()->false);
            int artW=Math.min(s(126),w/3),bx=x+artW+s(24),bw=w-artW-s(36),by=y+h-s(93);
            talkButton=button("聊聊天",bx,by,bw,b->request(LonyaStoryService.CONTINUE,0),true);
            button("历史章节",bx,by+s(27),bw,b->openLibrary(),true);
            button("回收玩偶",bx,by+s(54),bw,b->request(LonyaStoryService.RECLAIM,0),true).active=!waiting;
        } else if(page==1) {
            if(hidden)return;
            int margin=s(12),gap=s(3),count=width<s(480)?5:7;
            int bw=(width-2*margin-(count-1)*gap)/count,by=height-s(35),col=0;
            button(automatic?"自动 · 开":"自动",margin+col++*(bw+gap),by,bw,s(21),b->{automatic=!automatic;fastForward=false;rebuildWidgets();},false,()->automatic);
            button("历史",margin+col++*(bw+gap),by,bw,b->openHistory(),false);
            button(fastForward?"快进 · 开":"快进",margin+col++*(bw+gap),by,bw,s(21),b->{fastForward=!fastForward;automatic=false;if(branch())fastForward=false;rebuildWidgets();},false,()->fastForward);
            if(count==7) {
                button("章节",margin+col++*(bw+gap),by,bw,b->openLibrary(),false);
                button(textSpeed==0?"文字 · 即时":textSpeed==4?"文字 · 快":"文字 · 标准",margin+col++*(bw+gap),by,bw,b->{textSpeed=textSpeed==2?4:textSpeed==4?0:2;rebuildWidgets();},false);
            }
            button("菜单",margin+col++*(bw+gap),by,bw,b->request(LonyaStoryService.HUB,0),false);
            next=button("继续",margin+col*(bw+gap),by,bw,b->advance(),false);
            button("章节回顾",width-s(122),s(9),s(76),s(19),b->openLibrary(),false,()->false);
            button("×",width-s(39),s(9),s(27),s(19),b->onClose(),false,()->false);
            if(branch()) {
                automatic=false;fastForward=false;
                int cbw=Math.min(width-s(32),s(440)),cx=(width-cbw)/2,cy=dialogueY-s(61);
                choiceButtons.add(button("没有不耐烦啊，我挺喜欢听你说的",cx,cy,cbw,s(25),b->request(LonyaStoryService.CHOICE,0),false,()->false));
                choiceButtons.add(button("有点吧，毕竟我也不是很懂你说的",cx,cy+s(30),cbw,s(25),b->request(LonyaStoryService.CHOICE,1),false,()->false));
            }
            updateReadingButtons();
        } else if(page==3) {
            w=Math.min(width-s(24),s(650));h=height-s(32);x=(width-w)/2;y=(height-h)/2;updateHistoryMetrics();
            button("返回阅读",x+w-s(94),y+s(9),s(82),b->back(),false);
            button("最早",x+s(14),y+h-s(32),s(60),b->scroll=0,false);
            button("最新",x+s(80),y+h-s(32),s(60),b->scroll=historyMax,false);
            button("上一页",x+w-s(144),y+h-s(32),s(60),b->scroll=Math.max(0,scroll-historyLimit+1),false);
            button("下一页",x+w-s(78),y+h-s(32),s(64),b->scroll=Math.min(historyMax,scroll+historyLimit-1),false);
        } else if(page==2) {
            if(gallery) {
                button("返回章节",s(12),s(10),s(86),b->back(),false);
                button("关闭",width-s(60),s(10),s(48),b->onClose(),false);return;
            }
            w=Math.min(width-s(24),s(760));h=Math.min(height-s(24),s(410));x=(width-w)/2;y=(height-h)/2;
            boolean wide=w>=s(530);
            int listW=wide?(w-s(40))/2:w-s(28),rowH=(h-s(wide?92:100))/visibleChapters();
            for(int i=0;i<visibleChapters();i++) {
                final int chapter=i;
                var b=button(String.format("%02d  ",i+1)+LonyaScript.chapter(i).title().replaceFirst("第\\d章 · ",""),
                        x+s(14),y+s(43)+i*rowH,listW,rowH-s(4),ignored->{galleryChapter=chapter;rebuildWidgets();},true,()->galleryChapter==chapter);
                b.active=unlocked(i);
            }
            int ax=x+s(14),ay=y+h-s(34),bw=Math.min(s(116),(w-s(42))/3);
            button(returnPage==1?"返回阅读":"返回菜单",ax,ay,bw,b->back(),true);
            replayButton=button("从头重玩",ax+bw+s(7),ay,bw,b->request(LonyaStoryService.REPLAY,galleryChapter),true);
            button("查看 CG",ax+2*(bw+s(7)),ay,bw,b->{gallery=true;rebuildWidgets();},true).active=completed(galleryChapter);
        }
        updateEntryButtons();
    }
    private int visibleCount() {
        return fullText||textSpeed==0?line().text().length():Math.min(line().text().length(),(int)(revealTicks*50.0/(textSpeed==4?18:36)));
    }
    private void advance() {
        if(page!=1||waiting||hidden)return;
        if(visibleCount()<line().text().length()) {fullText=true;updateReadingButtons();return;}
        if(!branch())request(LonyaStoryService.NEXT,0);
    }
    private void updateReadingButtons() {
        if(next!=null) {
            next.active=!waiting&&!branch();
            next.setMessage(Component.literal(branch()?"请选择":visibleCount()<line().text().length()?"显示全文":last()?"本章结束":"继续"));
        }
        for(var button:choiceButtons)button.active=!waiting;
    }
    @Override public void tick() {
        if(++ticks%10==0&&minecraft.player!=null)request(LonyaStoryService.HEARTBEAT,page==1&&!hidden&&!waiting?1:0);
        updateEntryButtons();
        if(page!=1||hidden)return;
        if(!waiting)revealTicks++;
        updateReadingButtons();
        if(branch()||waiting)return;
        if((fastForward||hasControlDown())&&ticks%4==0) {fullText=true;request(LonyaStoryService.NEXT,0);}
        else if(automatic&&visibleCount()>=line().text().length()&&revealTicks*50L>=Math.max(1800,line().minimumMillis()))advance();
    }
    private static ResourceLocation texture(String name) {return ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"textures/story/"+name+".png");}
    private void image(GuiGraphics g,String name,int px,int py,int width,int height,int sw,int sh) {
        g.blit(texture(name),px,py,width,height,0,0,sw,sh,sw,sh);
    }
    /** Aspect-cover preserves proportions and fills every screen edge, including at 4:3 sizes. */
    private void cover(GuiGraphics g,int chapter,int px,int py,int bw,int bh) {
        int sw=chapter==4?1536:1672,sh=chapter==4?1024:941;
        float scale=Math.max(bw/(float)sw,bh/(float)sh);int dw=(int)Math.ceil(sw*scale),dh=(int)Math.ceil(sh*scale);
        g.enableScissor(px,py,px+bw,py+bh);
        image(g,"chapter_"+(chapter+1),px+(bw-dw)/2,py+(bh-dh)/2,dw,dh,sw,sh);g.disableScissor();
    }
    private void text(GuiGraphics g,String text,int px,int py,int color) {
        g.pose().pushPose();g.pose().translate(px,py,0);g.pose().scale(unit,unit,1);
        g.drawString(font,text,0,0,color,false);g.pose().popPose();
    }
    private void row(GuiGraphics g,FormattedCharSequence text,int px,int py,int color) {
        g.pose().pushPose();g.pose().translate(px,py,0);g.pose().scale(unit,unit,1);
        g.drawString(font,text,0,0,color,false);g.pose().popPose();
    }
    private void wrapped(GuiGraphics g,String text,int px,int py,int maxWidth,int color,int maxHeight) {
        wrapped(g,text,px,py,maxWidth,color,maxHeight,s(12));
    }
    private void wrapped(GuiGraphics g,String text,int px,int py,int maxWidth,int color,int maxHeight,int lineStep) {
        int rowY=py;
        for(var line:font.split(Component.literal(text),Math.max(20,(int)(maxWidth/unit)))) {
            if(rowY+s(9)>py+maxHeight)break;
            row(g,line,px,rowY,color);rowY+=lineStep;
        }
    }
    private void panel(GuiGraphics g,int px,int py,int pw,int ph) {
        g.fill(px,py,px+pw,py+ph,0xFF55566B);g.fill(px+1,py+1,px+pw-1,py+ph-1,LIGHT);
        g.fill(px+2,py+2,px+pw-2,py+ph-2,SHADOW);g.fill(px+2,py+2,px+pw-3,py+ph-3,PANEL);
        g.fill(px+s(5),py+s(5),px+pw-s(5),py+s(28),SLOT);
        for(int cx:new int[]{px+s(3),px+pw-s(5)})for(int cy:new int[]{py+s(3),py+ph-s(5)})
            g.fill(cx,cy,cx+s(2),cy+s(2),0xFF6E7186);
    }
    private void inset(GuiGraphics g,int px,int py,int pw,int ph) {
        g.fill(px,py,px+pw,py+ph,SHADOW);g.fill(px+1,py+1,px+pw-1,py+ph-1,0xFFBDC0CE);
        g.fill(px,py+ph-1,px+pw,py+ph,LIGHT);
    }
    private void renderReading(GuiGraphics g) {
        cover(g,number("chapter"),0,0,width,height);if(hidden)return;
        g.fillGradient(0,0,width,s(54),0xC0161824,0x00161824);
        text(g,LonyaScript.chapter(number("chapter")).title(),s(15),s(14),0xFFF5E6DA);
        if(number("chapter")>=3)text(g,"语音通话 · Lonya",s(15),s(31),0xFFDED2CA);
        // CGs already contain Lonya: do not obscure them with a duplicate full-body portrait.
        int margin=s(12),dy=dialogueY;
        g.fillGradient(margin,dy,width-margin,height-s(10),0xC5202231,0xEF171A28);
        g.fill(margin,dy,width-margin,dy+1,0xD7DBB5A8);g.fill(margin,dy,margin+s(3),height-s(10),0xFFD3AC9E);
        text(g,line().speaker(),margin+s(13),dy+s(10),0xFFF3C6BA);
        String counter=(number("line")+1)+" / "+LonyaScript.chapter(number("chapter")).lines().size();
        text(g,counter,width-margin-s(14)-s(font.width(counter)),dy+s(11),0xFFBDB4B4);
        wrapped(g,line().text().substring(0,visibleCount()),margin+s(13),dy+s(27),width-2*margin-s(26),0xFFFFF5EC,dialogueH-s(56),s(width<s(480)?10:12));
        g.fill(margin+s(10),height-s(40),width-margin-s(10),height-s(40)+1,0x506E6878);
        if(branch())text(g,"Lonya 等着你的回答",Math.max(s(16),(width-s(font.width("Lonya 等着你的回答")))/2),dialogueY-s(78),0xFFFFEFE2);
    }
    private void renderHub(GuiGraphics g) {
        g.fill(0,0,width,height,0x38000000);panel(g,x,y,w,h);
        text(g,"lonya plush",x+s(12),y+s(12),INK);
        int artW=Math.min(s(126),w/3),ax=x+s(10),ay=y+s(35),ah=h-s(47);inset(g,ax,ay,artW,ah);
        int ph=ah-s(4),pw=ph*2/3;g.enableScissor(ax+1,ay+1,ax+artW-1,ay+ah-1);
        image(g,"hello",ax+(artW-pw)/2,ay+s(4),pw,ph,1024,1536);g.disableScissor();
        int tx=x+artW+s(24),tw=w-artW-s(36);
        text(g,"晴色日常",tx,y+s(41),BLUE);g.fill(tx,y+s(58),x+w-s(12),y+s(58)+1,SHADOW);
        String intro=message.isBlank()?"“回来啦。先坐一会儿？”\n\n继续上次的话题，或翻翻以前的章节。":message;
        wrapped(g,intro,tx,y+s(68),tw,INK,Math.max(s(25),h-s(167)));
        if(h>=s(245))text(g,cooldownMillis()>0?"下次聊天："+LonyaStoryConfig.countdown(cooldownMillis()):"进度已保存 · 随时可以离开",tx,y+h-s(111),MUTED);
    }
    private List<HistoryRow> historyRows() {
        var rows=new ArrayList<HistoryRow>();int previous=-1,wrapWidth=Math.max(20,(int)((w-s(54))/unit));
        for(var entry:history) {
            if(previous!=entry.chapter) {
                rows.add(new HistoryRow(Component.literal(LonyaScript.chapter(entry.chapter).title()).getVisualOrderText(),0xFFD9B2A4));
                rows.add(new HistoryRow(FormattedCharSequence.EMPTY,0));previous=entry.chapter;
            }
            rows.add(new HistoryRow(Component.literal(entry.speaker).getVisualOrderText(),0xFFE4BCAD));
            for(var text:font.split(Component.literal(entry.text),wrapWidth))rows.add(new HistoryRow(text,0xFFF3EBE1));
            rows.add(new HistoryRow(FormattedCharSequence.EMPTY,0));
        }
        return rows;
    }
    private void updateHistoryMetrics() {
        historyLimit=Math.max(1,(h-s(91))/s(12));
        historyMax=Math.max(0,historyRows().size()-historyLimit);scroll=Math.clamp(scroll,0,historyMax);
    }
    private void renderHistory(GuiGraphics g) {
        renderReading(g);g.fill(0,0,width,height,0x9C11131D);
        g.fill(x,y,x+w,y+h,0xF21D2231);g.fill(x,y,x+w,y+1,0xFFD3AC9E);
        text(g,"对话历史",x+s(15),y+s(13),0xFFF5D2C2);
        text(g,"滚轮翻阅 · Esc / 右键返回",x+s(15),y+s(33),0xFFA5ADBD);
        updateHistoryMetrics();var rows=historyRows();int top=y+s(52),bottom=top+historyLimit*s(12);
        g.enableScissor(x+s(12),top,x+w-s(23),bottom);
        for(int i=0;i<historyLimit&&scroll+i<rows.size();i++) {var r=rows.get(scroll+i);row(g,r.text,x+s(19),top+i*s(12),r.color);}
        g.disableScissor();
        int barX=x+w-s(14),barH=bottom-top;g.fill(barX,top,barX+s(3),bottom,0xFF3F4556);
        int thumbH=Math.max(s(13),barH*historyLimit/Math.max(historyLimit,rows.size()));
        int thumbY=top+(historyMax==0?0:(barH-thumbH)*scroll/historyMax);
        g.fill(barX,thumbY,barX+s(3),thumbY+thumbH,0xFFD0B2A4);
    }
    private void renderLibrary(GuiGraphics g) {
        if(gallery) {
            cover(g,galleryChapter,0,0,width,height);
            g.fillGradient(0,height-s(90),width,height,0x00161824,0xE5171A28);
            wrapped(g,LonyaScript.chapter(galleryChapter).summary(),s(20),height-s(55),width-s(40),0xFFFFF2E8,s(45));return;
        }
        g.fill(0,0,width,height,0x60000000);panel(g,x,y,w,h);text(g,"历史章节 / 章节回顾",x+s(14),y+s(12),INK);
        if(w>=s(530)) {
            int rx=x+(w-s(40))/2+s(28),rw=w-(rx-x)-s(14),ph=(h-s(110))*48/100;inset(g,rx,y+s(43),rw,ph);
            if(completed(galleryChapter))cover(g,galleryChapter,rx+1,y+s(43)+1,rw-2,ph-2);
            else {
                text(g,"尚未收录 CG",rx+s(12),y+s(55),MUTED);text(g,"读完本章后可在这里回顾",rx+s(12),y+s(72),MUTED);
            }
            int ty=y+s(43)+ph+s(12);
            wrapped(g,completed(galleryChapter)?LonyaScript.chapter(galleryChapter).summary():"选择章节后可以从头重玩；读完后可查看 CG 和回顾。",rx,ty,rw,INK,h-(ty-y)-s(68));
            text(g,completed(galleryChapter)?"本章已读完":"本章尚未读完",rx,y+h-s(62),BLUE);
            text(g,"重玩会替换当前阅读位置，保留章节解锁。",x+s(14),y+h-s(53),MUTED);
        } else text(g,"重玩替换阅读位置；章节解锁保留。",x+s(14),y+h-s(52),MUTED);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) { }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        if(page==0)renderHub(g);else if(page==1)renderReading(g);else if(page==2)renderLibrary(g);else if(page==3)renderHistory(g);
        super.render(g,mx,my,partial);
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE) {
            if(page>=2)back();
            else if(page==1) {if(hidden){hidden=false;rebuildWidgets();}else request(LonyaStoryService.HUB,0);}
            else onClose();return true;
        }
        if(page==3) {
            if(key==GLFW.GLFW_KEY_PAGE_UP)scroll=Math.max(0,scroll-historyLimit+1);
            else if(key==GLFW.GLFW_KEY_PAGE_DOWN)scroll=Math.min(historyMax,scroll+historyLimit-1);
            else if(key==GLFW.GLFW_KEY_HOME)scroll=0;
            else if(key==GLFW.GLFW_KEY_END)scroll=historyMax;
            else return super.keyPressed(key,scan,mods);
            return true;
        }
        if(page==1) {
            if(key==GLFW.GLFW_KEY_SPACE||key==GLFW.GLFW_KEY_ENTER) {
                if(hidden){hidden=false;rebuildWidgets();}else advance();return true;
            }
            if(key==GLFW.GLFW_KEY_H) {hidden=!hidden;rebuildWidgets();return true;}
            if(key==GLFW.GLFW_KEY_L) {openHistory();return true;}
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public boolean mouseClicked(double mx,double my,int button) {
        if(button==1) {
            if(page>=2)back();else if(page==1) {hidden=!hidden;pauseReading();rebuildWidgets();}
            return true;
        }
        if(page==1&&hidden) {hidden=false;rebuildWidgets();return true;}
        if(page==3&&button==0&&mx>=x+w-s(22)&&mx<=x+w-s(7)&&my>=y+s(52)&&my<y+h-s(39)) {
            draggingScroll=true;dragHistory(my);return true;
        }
        if(super.mouseClicked(mx,my,button))return true;
        if(page==1&&button==0) {advance();return true;}return false;
    }
    private void dragHistory(double my) {
        double track=Math.max(1,historyLimit*s(12));
        scroll=Math.clamp((int)Math.round((my-y-s(52))/track*historyRows().size()-historyLimit/2.0),0,historyMax);
    }
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy) {
        if(page==3&&draggingScroll) {dragHistory(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);
    }
    @Override public boolean mouseReleased(double mx,double my,int button) {draggingScroll=false;return super.mouseReleased(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        if(page==3) {scroll=Math.clamp(scroll-(int)(vertical*3),0,historyMax);return true;}
        if(page==1&&vertical>0) {openHistory();return true;}
        if(page==1&&vertical<0) {advance();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void onClose() {if(minecraft.player!=null)request(LonyaStoryService.CLOSE,0);minecraft.setScreen(null);}
    @Override public void removed() {if(minecraft.player!=null)request(LonyaStoryService.CLOSE,0);}
    @Override public boolean isPauseScreen() {return false;}

    private final class StyledButton extends Button {
        private final boolean ae,chapterCard;
        private final BooleanSupplier selected;
        StyledButton(String label,int x,int y,int w,int h,OnPress press,boolean ae,BooleanSupplier selected) {
            super(x,y,w,h,Component.literal(label),press,DEFAULT_NARRATION);
            this.ae=ae;this.selected=selected;this.chapterCard=label.matches("^0[1-5]  .*");
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            boolean hover=active&&(isHovered||isFocused()),chosen=selected.getAsBoolean();
            int bx=getX(),by=getY(),bw=getWidth(),bh=getHeight();
            if(ae) {
                g.fill(bx,by,bx+bw,by+bh,0xFF777D92);g.fill(bx+1,by+1,bx+bw-1,by+bh-1,LIGHT);
                g.fill(bx+2,by+2,bx+bw-1,by+bh-1,chosen?BLUE:hover?0xFFD8DFEA:active?SLOT:0xFFBCC0CC);
                g.fill(bx+2,by+bh-2,bx+bw-1,by+bh-1,SHADOW);
            } else {
                g.fill(bx,by,bx+bw,by+bh,chosen?0xCC715B66:hover?0xD84C4355:0x9B1D2232);
                g.fill(bx,by+bh-1,bx+bw,by+bh,active?(hover||chosen?0xFFF0C4B3:0x776E6878):0x444E5260);
            }
            int reserve=chapterCard?s(38):0;
            String label=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,(int)((bw-s(10)-reserve)/unit)));
            int tw=s(font.width(label)),tx=chapterCard?bx+s(8):bx+(bw-tw)/2;
            text(g,label,tx,by+(bh-s(8))/2,ae?active?(chosen?LIGHT:INK):0xFF858A9F:active?0xFFF7E4D9:0xFF838796);
            if(chapterCard) {
                int i=Integer.parseInt(getMessage().getString().substring(0,2))-1;
                text(g,unlocked(i)?completed(i)?"已读":"可读":"锁定",bx+bw-s(29),by+(bh-s(8))/2,chosen?LIGHT:MUTED);
            }
        }
    }
}
