package com.customdoll.lonya_plush.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
public final class DollThirdPerson {
 public static void poseArm(PlayerModel<?> model,HumanoidArm side) {
  ModelPart arm=side==HumanoidArm.RIGHT?model.rightArm:model.leftArm;
  arm.xRot=-.85F;arm.yRot=side==HumanoidArm.RIGHT?-.08F:.08F;arm.zRot=0;
  (side==HumanoidArm.RIGHT?model.rightSleeve:model.leftSleeve).copyFrom(arm);
 }
 public static void transformToPalm(ModelPart arm,HumanoidArm side,boolean slim,PoseStack pose) {
  arm.translateAndRotate(pose);
  pose.translate((side==HumanoidArm.RIGHT?-1:1)*(slim?.5F:1F)/16,10F/16,-2.2F/16);
  pose.mulPose(new Quaternionf().rotationZYX(arm.zRot,arm.yRot,arm.xRot).conjugate());
  pose.scale(.8F,-.8F,-.8F);
 }
 public static void render(ItemStack stack,PlayerModel<?> model,HumanoidArm side,boolean slim,
     PoseStack pose,MultiBufferSource buffers,int light,LivingEntity wearer) {
  pose.pushPose();transformToPalm(side==HumanoidArm.RIGHT?model.rightArm:model.leftArm,side,slim,pose);
  DollItemRenderer.get().renderCarried(stack,pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,wearer);pose.popPose();
 }
}
