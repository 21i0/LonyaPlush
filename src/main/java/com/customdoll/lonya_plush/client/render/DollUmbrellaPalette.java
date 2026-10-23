package com.customdoll.lonya_plush.client.render;

import com.customdoll.lonya_plush.DollMod;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid=DollMod.MODID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class DollUmbrellaPalette {
    private static boolean enabled=true;
    public static boolean enabled() { return enabled; }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<int[]>() {
            @Override protected int[] prepare(ResourceManager resources, ProfilerFiller profiler) {
                int[] colors={0xAFA4E1,0x879DDA,0xF2E7FD,1,0,0,0,0,0,0,0,0,0};
                var file=resources.getResource(ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"textures/entity/umbrella.json"));
                if(file.isPresent()) try(var reader=file.get().openAsReader()) {
                    var json=JsonParser.parseReader(reader).getAsJsonObject();
                    String[] keys={"fabric_a","fabric_b","trim"};
                    for(int i=0;i<keys.length;i++) if(json.has(keys[i])) {
                        String color=json.get(keys[i]).getAsString();
                        if(!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Invalid RGB color");
                        colors[i]=Integer.parseInt(color.substring(1),16);
                    }
                    if(json.has("enabled")) colors[3]=json.get("enabled").getAsBoolean()?1:0;
                    if(json.has("panels")) {
                        var panels=json.getAsJsonArray("panels");
                        if(panels.size()!=8)throw new IllegalArgumentException("Eight umbrella panels required");
                        for(int i=0;i<8;i++) {
                            String value=panels.get(i).getAsString();
                            if(!value.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid panel RGB");
                            colors[4+i]=Integer.parseInt(value.substring(1),16);
                        }
                        colors[12]=1;
                    }
                } catch(Exception failure) {
                    com.mojang.logging.LogUtils.getLogger().warn("Invalid umbrella palette; using defaults",failure);
                    return new int[]{0xAFA4E1,0x879DDA,0xF2E7FD,1,0,0,0,0,0,0,0,0,0};
                }
                return colors;
            }
            @Override protected void apply(int[] colors,ResourceManager resources,ProfilerFiller profiler) {
                DollUmbrellaCanopy.colors(colors[0],colors[1],colors[2]);enabled=colors[3]!=0;
                DollUmbrellaCanopy.panels(colors[12]==1?java.util.Arrays.copyOfRange(colors,4,12):null);
            }
        });
    }
}

