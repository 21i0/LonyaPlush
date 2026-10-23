package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.customdoll.lonya_plush.DollMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** One client texture for every doll form, rebuilt only when resources reload. */
@EventBusSubscriber(modid = DollMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class DollSkin {
    private static final ResourceLocation NORMALIZED = ResourceLocation.fromNamespaceAndPath(
            DollMod.MODID, "dynamic/lonya_plush_normalized_skin");
    private static final ResourceLocation HALF = ResourceLocation.fromNamespaceAndPath(
            DollMod.MODID, "dynamic/lonya_plush_half_blink");
    private static final ResourceLocation CLOSED = ResourceLocation.fromNamespaceAndPath(
            DollMod.MODID, "dynamic/lonya_plush_closed_eyes");
    private static ResourceLocation texture = DollRenderer.TEXTURE;
    private static boolean blinkReady;
    private record Prepared(NativeImage normal, NativeImage half, NativeImage closed, boolean enabled) { }

    private DollSkin() { }

    public static ResourceLocation texture() {
        return texture;
    }

    public static ResourceLocation texture(float blink) {
        return !blinkReady || blink < 0.25F ? texture : blink < 0.65F ? HALF : CLOSED;
    }

    @SubscribeEvent
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Prepared>() {
            @Override
            protected Prepared prepare(ResourceManager resources, ProfilerFiller profiler) {
                try (var input = resources.open(DollRenderer.TEXTURE);
                     NativeImage source = NativeImage.read(input)) {
                    if (source.getWidth()<64 || source.getWidth()%64!=0 || source.getHeight()!=source.getWidth())
                        throw new IllegalArgumentException("Expected 64x64 or an integer-scale HD skin");
                    NativeImage normal=normalizedCopy(source);
                    NativeImage half=null, closed=null;
                    boolean enabled=true;
                    var config=resources.getResource(ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"textures/entity/skin.json"));
                    if(config.isPresent()) try(var reader=config.get().openAsReader()) {
                        var json=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if(json.has("blink_enabled")) enabled=json.get("blink_enabled").getAsBoolean();
                        if(json.has("preview_variants")) {
                            var variants=json.getAsJsonObject("preview_variants");
                            half=loadVariant(resources,variants.get("half").getAsString(),normal);
                            closed=loadVariant(resources,variants.get("closed").getAsString(),normal);
                        }
                    } catch(Exception failure) {
                        LogUtils.getLogger().warn("Invalid blink configuration; using default eye positions",failure);
                    }
                    if(half==null) half=blinkCopy(normal,false);
                    if(closed==null) closed=blinkCopy(normal,true);
                    return new Prepared(normal,half,closed,enabled);
                } catch (Exception failure) {
                    LogUtils.getLogger().warn("Could not prepare the 2i skin; using the resource directly", failure);
                    return null;
                }
            }

            @Override
            protected void apply(Prepared image, ResourceManager resources, ProfilerFiller profiler) {
                DollHandRenderer.reload();
                var textures = Minecraft.getInstance().getTextureManager();
                textures.release(NORMALIZED);
                textures.release(HALF);
                textures.release(CLOSED);
                blinkReady = false;
                texture = DollRenderer.TEXTURE;
                if (image != null) {
                    textures.register(HALF, new DynamicTexture(image.half()));
                    textures.register(CLOSED, new DynamicTexture(image.closed()));
                    textures.register(NORMALIZED, new DynamicTexture(image.normal()));
                    texture = NORMALIZED;
                    blinkReady = image.enabled();
                }
            }
        });
    }

    private static NativeImage loadVariant(ResourceManager resources, String name, NativeImage normal) throws Exception {
        if(!name.matches("[a-zA-Z0-9_.-]+[.]png")) throw new IllegalArgumentException("Expected a PNG filename");
        var location=ResourceLocation.fromNamespaceAndPath(DollMod.MODID,"textures/entity/"+name.toLowerCase(java.util.Locale.ROOT));
        if(resources.getResource(location).isEmpty()) return null;
        try(var stream=resources.open(location);var source=NativeImage.read(stream)) {
            if(source.getWidth()!=normal.getWidth() || source.getHeight()!=normal.getHeight())
                throw new IllegalArgumentException("Blink frame size differs from skin.png");
            return normalizedCopy(source);
        }
    }

    /** Original PNG is untouched. Standard and integer-scale HD Steve/Alex skins are accepted. */
    public static NativeImage normalizedCopy(NativeImage source) {
        NativeImage copy = new NativeImage(source.getWidth(), source.getHeight(), false);
        copy.copyFrom(source);
        if (usesSlimArms(source)) {
            int scale = source.getWidth() / 64;
            remapArm(source, copy, 40, 16, scale, true);
            remapArm(source, copy, 32, 48, scale, true);
            remapArm(source, copy, 40, 32, scale, false);
            remapArm(source, copy, 48, 48, scale, false);
        }
        return copy;
    }

    public static NativeImage blinkCopy(NativeImage skin, boolean closed) {
        NativeImage copy = new NativeImage(skin.getWidth(), skin.getHeight(), false);
        copy.copyFrom(skin);
        if (skin.getWidth() % 64 != 0 || skin.getHeight() != skin.getWidth()) return copy;
        int scale = skin.getWidth() / 64;
        for (int eye : new int[]{9, 13}) for (int px = 0; px < 2 * scale; px++) {
            int x = eye * scale + px;
            int complexion = skin.getPixelRGBA(x, 15 * scale);
            int lash = skin.getPixelRGBA(x, 13 * scale);
            for (int py = 0; py < scale; py++) {
                int y = 14 * scale + py;
                int original=skin.getPixelRGBA(x,y);
                int color = closed ? (py == scale / 2 ? lash : complexion)
                        : (py < Math.max(1, scale / 2) ? complexion : original);
                if(!closed && scale==1) color=((original&0x00FEFEFE)>>>1)+((complexion&0x00FEFEFE)>>>1);
                if((original>>>24)!=0) copy.setPixelRGBA(x,y,(color&0x00FFFFFF)|(original&0xFF000000));
                int outer=skin.getPixelRGBA(x+32*scale,y);
                if ((outer>>>24)!=0)
                    copy.setPixelRGBA(x+32*scale,y,(color&0x00FFFFFF)|(outer&0xFF000000));
            }
        }
        return copy;
    }

    public static float blinkAt(float time) {
        int cycle = Math.floorDiv((int)Math.floor(time), 83);
        float phase = time - cycle * 83.0F;
        float first = net.minecraft.util.Mth.clamp(1.0F - Math.abs(phase - 42.0F) / 3.0F, 0, 1);
        float second = cycle % 7 == 0 ? net.minecraft.util.Mth.clamp(1.0F - Math.abs(phase - 49.0F) / 2.0F, 0, 1) : 0;
        return Math.max(first, second);
    }

    public static boolean usesSlimArms(NativeImage skin) {
        if (skin.getWidth() < 64 || skin.getWidth() % 64 != 0 || skin.getHeight() != skin.getWidth()) return false;
        int scale = skin.getWidth() / 64;
        // Alex reserves the last two arm columns. Check both base arms and
        // confirm their three-pixel palms exist, not an entirely missing skin.
        return allTransparent(skin, 54, 20, 2, 12, scale)
                && allTransparent(skin, 46, 52, 2, 12, scale)
                && !allTransparent(skin, 47, 16, 3, 4, scale)
                && !allTransparent(skin, 39, 48, 3, 4, scale);
    }

    private static boolean allTransparent(NativeImage image, int x, int y, int width, int height, int scale) {
        for (int py = y * scale; py < (y + height) * scale; py++) {
            for (int px = x * scale; px < (x + width) * scale; px++) {
                if ((image.getPixelRGBA(px, py) >>> 24) != 0) return false;
            }
        }
        return true;
    }

    private static void remapArm(NativeImage source, NativeImage target, int x, int y, int scale, boolean base) {
        // Unfolded vanilla cube faces: top, palm, side, front, side, back.
        face(source, target, x + 4, y, 3, 4, x + 4, y, 4, 4, scale, base);
        face(source, target, x + 7, y, 3, 4, x + 8, y, 4, 4, scale, base);
        face(source, target, x, y + 4, 4, 12, x, y + 4, 4, 12, scale, base);
        face(source, target, x + 4, y + 4, 3, 12, x + 4, y + 4, 4, 12, scale, base);
        face(source, target, x + 7, y + 4, 4, 12, x + 8, y + 4, 4, 12, scale, base);
        face(source, target, x + 11, y + 4, 3, 12, x + 12, y + 4, 4, 12, scale, base);
    }

    private static void face(NativeImage source, NativeImage target, int sx, int sy, int sw, int sh,
                             int dx, int dy, int dw, int dh, int scale, boolean opaqueBase) {
        for (int y = 0; y < dh * scale; y++) for (int x = 0; x < dw * scale; x++) {
            int color = source.getPixelRGBA(sx * scale + x * sw / dw, sy * scale + y * sh / dh);
            target.setPixelRGBA(dx * scale + x, dy * scale + y, opaqueBase ? color | 0xFF000000 : color);
        }
    }
}
