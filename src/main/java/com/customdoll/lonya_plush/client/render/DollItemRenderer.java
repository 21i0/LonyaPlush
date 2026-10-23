package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Every item display context uses the same real 3-D model, not a flat icon. */
public final class DollItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static DollItemRenderer instance;
    private DollRenderer.Model model;

    private DollItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static DollItemRenderer get() {
        if (instance == null) instance = new DollItemRenderer();
        return instance;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        // Re-bake lazily after the model-set reload has finished.
        model = null;
    }

    /** Client clock keeps inventory/paused-menu decorations moving too. */
    public static float animationTime() {
        return (Util.getMillis() % 8_000_000L) / 50.0F;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffer, int light, int overlay) {
        // The player head layer anchors the doll on top of the actual head bone.
        // Vanilla CustomHeadLayer also calls the item renderer; suppress only that
        // duplicate path, never ordinary handheld, inventory or block rendering.
        if (context == ItemDisplayContext.HEAD) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        renderAtFeet(stack, poseStack, buffer, light, overlay);
        poseStack.popPose();
    }

    /** Local origin sits underneath the doll, for actual palm-supported rendering. */
    public void renderAtFeet(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        renderAtFeet(stack,poseStack,buffer,light,overlay,0);
    }

    private void renderAtFeet(ItemStack stack,PoseStack poseStack,MultiBufferSource buffer,int light,int overlay,float open) {
        if (model == null) model = new DollRenderer.Model(
                Minecraft.getInstance().getEntityModels().bakeLayer(DollRenderer.LAYER));
        model.setupDecoration(animationTime());
        var grip=open>.01F?model.umbrellaGrip(open):net.minecraft.world.phys.Vec3.ZERO;
        poseStack.pushPose();
        poseStack.translate(0.0D, DollRenderer.FEET_Y / 16.0F * DollRenderer.RENDER_SCALE, 0.0D);
        poseStack.scale(DollRenderer.RENDER_SCALE, -DollRenderer.RENDER_SCALE, -DollRenderer.RENDER_SCALE);
        var vertices = ItemRenderer.getFoilBufferDirect(buffer,
                RenderType.entityCutoutNoCull(DollSkin.texture(model.blink())), false, stack.hasFoil());
        model.renderToBuffer(poseStack, vertices, light, overlay);
        poseStack.popPose();
        if(open>.01F) {
            var hand=new net.minecraft.world.phys.Vec3(grip.x*DollRenderer.RENDER_SCALE,
                    (DollRenderer.FEET_Y/16F-grip.y)*DollRenderer.RENDER_SCALE,-grip.z*DollRenderer.RENDER_SCALE);
            DollUmbrellaRenderer.renderCarried(hand,open,poseStack,buffer,light,overlay);
        }
    }

    public void renderCarried(ItemStack stack, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay,
                              net.minecraft.world.entity.LivingEntity wearer) {
        float open=DollUmbrellaRenderer.opening("carried:"+wearer.getUUID(),wearer.level().isRainingAt(wearer.blockPosition().above()),
                wearer.level().getGameTime()+Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
        renderAtFeet(stack, poseStack, buffer, light, overlay,open);
    }
}
