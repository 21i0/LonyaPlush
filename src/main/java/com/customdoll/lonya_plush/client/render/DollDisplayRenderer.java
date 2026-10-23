package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.customdoll.lonya_plush.content.block.DollDisplayBlock;
import com.customdoll.lonya_plush.content.blockentity.DollDisplayBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** One cached model per renderer; placed dolls do not load chunks or tick on the server. */
public final class DollDisplayRenderer implements BlockEntityRenderer<DollDisplayBlockEntity> {
    private final DollRenderer.Model model;

    public DollDisplayRenderer(BlockEntityRendererProvider.Context context) {
        model = new DollRenderer.Model(context.bakeLayer(DollRenderer.LAYER));
    }

    @Override
    public void render(DollDisplayBlockEntity doll, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        // A stable position offset makes a shelf of dolls use different gestures.
        float offset = Math.floorMod(doll.getBlockPos().hashCode(), 1600);
        model.setupDecoration(DollItemRenderer.animationTime() + offset);
        var level=doll.getLevel();
        var pair=level==null?null:com.customdoll.lonya_plush.content.DollUmbrellaProtocol.sample(level,
                com.customdoll.lonya_plush.content.DollUmbrellaProtocol.block(doll.getBlockPos()));
        float open=pair==null?0:DollUmbrellaRenderer.opening(pair.self().id(),pair.leader()
                && com.customdoll.lonya_plush.content.DollUmbrellaProtocol.raining(level,pair),level.getGameTime()+partialTick);
        var grip=open>.01F?model.umbrellaGrip(open):net.minecraft.world.phys.Vec3.ZERO;
        poseStack.pushPose();
        poseStack.translate(0.5D, DollRenderer.FEET_Y / 16.0F * DollRenderer.RENDER_SCALE, 0.5D);
        double now = doll.getLevel() == null ? 0 : doll.getLevel().getGameTime() + partialTick;
        float yaw=-22.5F*doll.getBlockState().getValue(DollDisplayBlock.ROTATION)+doll.rotationAt(now);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.scale(DollRenderer.RENDER_SCALE, -DollRenderer.RENDER_SCALE, -DollRenderer.RENDER_SCALE);
        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(DollSkin.texture(model.blink()))),
                light, overlay);
        poseStack.popPose();
        if (pair!=null && open>.01F) {
            var hand=new org.joml.Vector3f((float)grip.x*DollRenderer.RENDER_SCALE,0,(float)-grip.z*DollRenderer.RENDER_SCALE).rotateY(yaw*net.minecraft.util.Mth.DEG_TO_RAD);
            var localGrip=new net.minecraft.world.phys.Vec3(.5+hand.x,(DollRenderer.FEET_Y/16F-grip.y)*DollRenderer.RENDER_SCALE,.5+hand.z);
            DollUmbrellaRenderer.renderPair(pair,net.minecraft.world.phys.Vec3.atLowerCornerOf(doll.getBlockPos()),localGrip,open,poseStack,buffer,light,overlay);
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
