package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.customdoll.lonya_plush.content.entity.DollEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** One plush-doll rig, standing as a companion or sitting as an animated item. */
@OnlyIn(Dist.CLIENT)
public final class DollRenderer extends MobRenderer<DollEntity, DollRenderer.Model> {
    /** Standard 64x64 Steve skin UVs are deliberately identical in every form. */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("lonya_plush", "textures/entity/skin.png");
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("lonya_plush", "lonya_plush_doll"), "main");
    public static final float RENDER_SCALE = 0.62F;
    public static final float FEET_Y = 24.0F;

    public DollRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(LAYER)), 0.18F);
    }

    @Override
    protected void scale(DollEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(RENDER_SCALE, RENDER_SCALE, RENDER_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(DollEntity entity) {
        return DollSkin.texture(model.blink());
    }

    @Override public void render(DollEntity entity, float yaw, float partial, PoseStack pose,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        super.render(entity,yaw,partial,pose,buffers,light);
        var protocol = com.customdoll.lonya_plush.content.DollUmbrellaProtocol.sample(entity.level(),
                com.customdoll.lonya_plush.content.DollUmbrellaProtocol.entity(entity));
        float open=DollUmbrellaRenderer.opening(protocol.self().id(),protocol.leader()
                && com.customdoll.lonya_plush.content.DollUmbrellaProtocol.raining(entity.level(),protocol),entity.level().getGameTime()+partial);
        if(open>.01F) {
            var grip=model.umbrellaGrip(open);
            float body=net.minecraft.util.Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot);
            var hand=new org.joml.Vector3f((float)-grip.x*RENDER_SCALE,0,(float)grip.z*RENDER_SCALE)
                    .rotateY((180-body)*net.minecraft.util.Mth.DEG_TO_RAD);
            var localGrip=new net.minecraft.world.phys.Vec3(hand.x,(FEET_Y/16F-grip.y)*RENDER_SCALE,hand.z);
            DollUmbrellaRenderer.renderPair(protocol,entity.position(),localGrip,open,pose,buffers,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
        }
    }

    /**
     * Keep Steve's UV-sized cubes, not Steve's proportions or locomotion. Short
     * plush limbs are scaled at the bones so future standard skins still fit;
     * sitting and standing share these exact bones and skin-layer transforms.
     */
    public static final class Model extends PlayerModel<DollEntity> {
        private float blink;
        private float animationTime;
        private boolean umbrellaPosed;
        private static final float HEAD_SIZE = 1.18F;
        private static final float BODY_HEIGHT = 0.75F;
        private static final float LEG_HEIGHT = 0.50F;
        private static final float LEG_DEPTH = 0.75F;
        private static final float HIP_Y = FEET_Y - 12.0F * LEG_HEIGHT;
        private static final float NECK_Y = HIP_Y - 12.0F * BODY_HEIGHT;
        private final ModelPart[] resetParts;

        public static LayerDefinition createLayer() {
            return LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
        }

        public Model(ModelPart root) {
            super(root, false);
            // Dedicated doll pivots; only UV geometry is shared with Steve.
            initialPivot(head, 0, NECK_Y, 0);
            initialPivot(body, 0, NECK_Y, 0);
            initialPivot(leftArm, 4.15F, NECK_Y + 1.4F, 0);
            initialPivot(rightArm, -4.15F, NECK_Y + 1.4F, 0);
            initialPivot(leftLeg, 1.8F, HIP_Y, 0);
            initialPivot(rightLeg, -1.8F, HIP_Y, 0);
            resetParts = root.getAllParts().toArray(ModelPart[]::new);
        }

        private static void initialPivot(ModelPart part, float x, float y, float z) {
            PartPose pose = PartPose.offset(x, y, z);
            part.setInitialPose(pose);
            part.loadPose(pose);
        }

        @Override
        public void setupAnim(DollEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            setupStanding(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch,
                    entity.getPoseAction(), entity.getPoseActionProgress(ageInTicks - entity.tickCount));
            var pair=com.customdoll.lonya_plush.content.DollUmbrellaProtocol.sample(entity.level(),
                    com.customdoll.lonya_plush.content.DollUmbrellaProtocol.entity(entity));
            float open=DollUmbrellaRenderer.opening(pair.self().id(),pair.leader()
                    && com.customdoll.lonya_plush.content.DollUmbrellaProtocol.raining(entity.level(),pair),
                    entity.level().getGameTime()+ageInTicks-entity.tickCount);
            if(open>.01F) umbrellaGrip(open);
        }

        /** Also used by the isolated client fixture: no fake entity/world needed. */
        public void setupStanding(float limbSwing, float limbSwingAmount, float time,
                                  float headYaw, float headPitch, int action, float progress) {
            resetDoll();
            animationTime = time;
            blink = DollSkin.blinkAt(time);
            float moving = Mth.clamp(limbSwingAmount * 2.4F, 0, 1);
            float still = 1 - moving;
            float phase = limbSwing * 0.92F;
            float step = Mth.sin(phase);
            float bounce = Mth.abs(step) * moving * 0.16F;
            float breath = Mth.sin(time * 0.09F) * still;
            moveTorso(-bounce + breath * 0.045F);
            head.yRot = Mth.clamp(headYaw * Mth.DEG_TO_RAD, -0.72F, 0.72F);
            head.xRot = Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.38F, 0.42F);
            head.zRot = Mth.sin(time * 0.033F) * 0.035F * still;
            body.zRot = step * moving * 0.022F;
            head.x += step * moving * 0.07F;
            leftArm.xRot = -step * moving * 0.38F;
            rightArm.xRot = step * moving * 0.38F;
            leftArm.zRot = -0.07F - breath * 0.015F;
            rightArm.zRot = 0.07F + breath * 0.015F;
            leftLeg.xRot = step * moving * 0.57F;
            rightLeg.xRot = -step * moving * 0.57F;
            // Plant the supporting foot exactly at y=24; only the swinging foot
            // lifts. A vanilla 12-pixel stride made the old tiny model float.
            plantFoot(leftLeg, Mth.clamp(-step, 0, 1) * moving * 0.65F);
            plantFoot(rightLeg, Mth.clamp(step, 0, 1) * moving * 0.65F);

            float gesture = gestureEnvelope(Mth.clamp(progress, 0, 1));
            switch (action) {
                case 1 -> {
                    rightArm.xRot = Mth.lerp(gesture, rightArm.xRot, -2.45F);
                    rightArm.zRot += (0.45F + Mth.sin(time * 0.32F) * 0.20F) * gesture;
                    head.zRot -= 0.12F * gesture;
                }
                case 2 -> head.zRot += 0.25F * gesture;
                case 3 -> {
                    leftArm.xRot = Mth.lerp(gesture, leftArm.xRot, -2.65F);
                    leftArm.yRot = 0.24F * gesture;
                    leftArm.zRot -= 0.28F * gesture;
                    head.xRot -= 0.12F * gesture;
                    head.zRot += 0.10F * gesture;
                    head.xScale = head.zScale = HEAD_SIZE * (1 + 0.055F * gesture);
                    head.yScale = HEAD_SIZE * (1 - 0.10F * gesture);
                    moveTorso(0.30F * gesture);
                }
                case 6 -> {
                    // A standing companion rocks onto one foot, never hangs both
                    // feet in mid-air like the seated decoration's foot-swing.
                    leftLeg.xRot += Mth.sin(time * 0.19F) * 0.18F * still * gesture;
                    plantFoot(leftLeg, 0.15F * still * gesture);
                }
                default -> { }
            }
            copySkinLayers();
        }

        private static void plantFoot(ModelPart leg, float lift) {
            leg.y = FEET_Y - soleDepth(leg.xRot) - lift;
        }

        private static float soleDepth(float angle) {
            return 12.0F * LEG_HEIGHT * Mth.cos(angle) + 2.0F * LEG_DEPTH * Mth.abs(Mth.sin(angle));
        }

        /** Bottom of the bare skin's foot, in model coordinates, for regression checks. */
        public float footBottom(ModelPart leg) {
            return leg.y + soleDepth(leg.xRot);
        }

        private void resetDoll() {
            umbrellaPosed = false;
            for (ModelPart part : resetParts) part.resetPose();
            young = riding = crouching = false;
            attackTime = swimAmount = 0;
            head.xScale = head.yScale = head.zScale = HEAD_SIZE;
            body.xScale = 0.875F;
            body.yScale = BODY_HEIGHT;
            body.zScale = 1.125F;
            leftArm.xScale = rightArm.xScale = 0.75F;
            leftArm.yScale = rightArm.yScale = 0.50F;
            leftArm.zScale = rightArm.zScale = 0.75F;
            leftLeg.xScale = rightLeg.xScale = 0.75F;
            leftLeg.yScale = rightLeg.yScale = LEG_HEIGHT;
            leftLeg.zScale = rightLeg.zScale = LEG_DEPTH;
        }

        private void moveTorso(float dy) {
            head.y += dy;
            body.y += dy;
            leftArm.y += dy;
            rightArm.y += dy;
        }

        /** Twelve gestures over a continuous breathing pose, exclusively client-side. */
        public void setupDecoration(float time) {
            resetDoll();
            animationTime = time;
            blink = DollSkin.blinkAt(time);
            // Fold the very same short legs forward and lower the torso. The
            // seated bottom stays on the block top, without a second doll mesh.
            float seatedHip = FEET_Y - 2.25F * LEG_DEPTH;
            moveTorso(FEET_Y - 0.25F * BODY_HEIGHT - HIP_Y);
            leftLeg.y = rightLeg.y = seatedHip;
            leftLeg.z = rightLeg.z = -1.6F;
            leftLeg.x = 2.60F;
            rightLeg.x = -2.60F;
            leftLeg.xRot = rightLeg.xRot = -Mth.HALF_PI;
            // The foot points toward -Z. Negative yaw opens the left leg toward
            // +X; the old opposite signs folded both feet into each other.
            leftLeg.yRot = -0.24F;
            rightLeg.yRot = 0.24F;
            float breath = Mth.sin(time * 0.08F);
            moveTorso(breath * .045F);
            head.zRot = Mth.sin(time * 0.029F) * 0.04F;
            head.yRot = Mth.sin(time * .021F) * .10F + Mth.sin(time * .047F) * .04F;
            head.xRot = Mth.sin(time * .035F) * .025F;
            head.y += breath * 0.04F;
            leftArm.xRot = -.13F + Mth.sin(time * .063F) * .035F;
            rightArm.xRot = -.13F - Mth.sin(time * .063F) * .035F;
            leftArm.zRot = -.10F - breath * .015F;
            rightArm.zRot = .10F + breath * .015F;

            int slot = Math.floorDiv((int)time, 200);
            float progress = (time - slot * 200.0F) / 120.0F;
            float envelope = progress < 1 ? gestureEnvelope(progress) : 0;
            switch (Math.floorMod(slot, 12)) {
                case 0 -> {
                    head.yRot += Mth.sin(progress * Mth.TWO_PI) * 0.4F * envelope;
                    head.xRot -= 0.10F * envelope;
                }
                case 1 -> {
                    leftLeg.xRot += Mth.sin(time * 0.20F) * 0.17F * envelope;
                    rightLeg.xRot -= Mth.sin(time * 0.20F) * 0.17F * envelope;
                }
                case 2 -> {
                    rightArm.xRot = Mth.lerp(envelope, rightArm.xRot, -2.40F);
                    rightArm.zRot += (0.36F + Mth.sin(time * 0.28F) * 0.20F) * envelope;
                    head.zRot += 0.12F * envelope;
                }
                case 3 -> {
                    leftArm.zRot -= 2.25F * envelope;
                    rightArm.zRot += 2.25F * envelope;
                    head.xRot -= 0.20F * envelope;
                }
                case 4 -> {
                    head.xRot += (0.28F + Mth.sin(time * 0.08F) * 0.09F) * envelope;
                    head.zRot += 0.13F * envelope;
                    head.y += 0.13F * envelope;
                }
                case 5 -> {
                    rightArm.xRot = Mth.lerp(envelope, rightArm.xRot, -0.80F);
                    rightArm.yRot -= 0.65F * envelope;
                    rightArm.zRot += Mth.sin(time * 0.19F) * 0.17F * envelope;
                    head.xRot += 0.16F * envelope;
                }
                case 6 -> {
                    head.zRot += 0.27F * envelope;
                    leftLeg.xRot += Mth.sin(time * 0.16F) * 0.08F * envelope;
                }
                case 7 -> {
                    leftArm.xRot = Mth.lerp(envelope, leftArm.xRot, -2.55F);
                    rightArm.xRot = Mth.lerp(envelope, rightArm.xRot, -2.55F);
                    leftArm.yRot = 0.20F * envelope;
                    rightArm.yRot = -0.20F * envelope;
                    head.xRot += 0.13F * envelope;
                }
                case 8 -> { head.yRot -= .28F*envelope;leftArm.xRot -= .22F*envelope; }
                case 9 -> { head.yRot += Mth.sin(time*.21F)*.2F*envelope;leftArm.zRot -= .35F*envelope; }
                case 10 -> { leftArm.xRot=Mth.lerp(envelope,leftArm.xRot,-.72F);rightArm.xRot=Mth.lerp(envelope,rightArm.xRot,-.42F);head.xRot -= .16F*envelope; }
                case 11 -> { leftLeg.xRot += .22F*envelope;rightLeg.xRot -= .22F*envelope;head.zRot += .18F*envelope; }
            }
            // Seated swinging feet may lift, but never clip through the shelf.
            seatLeg(leftLeg);
            seatLeg(rightLeg);
            copySkinLayers();
        }

        private static void seatLeg(ModelPart leg) {
            float cosine=Mth.cos(leg.xRot);
            float bottom=(cosine>=0 ? 12.25F : -0.25F)*LEG_HEIGHT*cosine
                    + 2.25F*LEG_DEPTH*Math.abs(Mth.sin(leg.xRot));
            leg.y=FEET_Y-bottom;
        }

        public float blink() { return blink; }

        public net.minecraft.world.phys.Vec3 umbrellaGrip() {
            return umbrellaGrip(1);
        }

        public net.minecraft.world.phys.Vec3 umbrellaGrip(float open) {
            if (!umbrellaPosed) {
                float blend = Mth.clamp(open, 0, 1);
                rightArm.xRot=Mth.lerp(blend,rightArm.xRot,-.50F+Mth.sin(animationTime*.08F)*.02F);
                rightArm.yRot=Mth.lerp(blend,rightArm.yRot,0);
                rightArm.zRot=Mth.lerp(blend,rightArm.zRot,1.04F+Mth.sin(animationTime*.057F)*.015F);
                head.yRot=Mth.lerp(blend,head.yRot,Mth.clamp(head.yRot,-.40F,.40F));
                head.zRot=Mth.lerp(blend,head.zRot,Mth.clamp(head.zRot,-.07F,.07F));
                head.xRot=Mth.lerp(blend,head.xRot,Mth.clamp(head.xRot,-.30F,.40F));
                copySkinLayers();
                umbrellaPosed = true;
            }
            var pose = new PoseStack();
            rightArm.translateAndRotate(pose);
            var palm = new org.joml.Vector3f(-1/16F,10/16F,0).mulPosition(pose.last().pose());
            return new net.minecraft.world.phys.Vec3(palm.x,palm.y,palm.z);
        }

        private static float gestureEnvelope(float progress) {
            float wave = Mth.sin(progress * Mth.PI);
            return wave * wave;
        }

        private void copySkinLayers() {
            hat.copyFrom(head);
            jacket.copyFrom(body);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
        }
    }
}
