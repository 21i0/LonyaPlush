package com.customdoll.lonya_plush.checks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.math.Axis;
import com.customdoll.lonya_plush.client.render.DollRenderer;
import com.customdoll.lonya_plush.client.render.DollSkin;
import com.customdoll.lonya_plush.client.render.DollHandRenderer;
import com.customdoll.lonya_plush.client.render.DollHeadLayer;
import com.customdoll.lonya_plush.client.render.DollItemRenderer;

import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Standalone fixture helpers for the opt-in isolated story client. */
public final class DollRenderChecks {
    private DollRenderChecks() { }

    public static void validate(Minecraft client) throws Exception {
        try (var stream = client.getResourceManager().open(DollRenderer.TEXTURE);
             NativeImage source = NativeImage.read(stream);
             NativeImage normalized = DollSkin.normalizedCopy(source)) {
            try(var half=DollSkin.blinkCopy(normalized,false);var closed=DollSkin.blinkCopy(normalized,true)) {
                int changed=0;
                for(int y=0;y<64;y++)for(int x=0;x<64;x++) {
                    if(normalized.getPixelRGBA(x,y)!=closed.getPixelRGBA(x,y)) changed++;
                    if((normalized.getPixelRGBA(x,y)>>>24)==0)
                        check((closed.getPixelRGBA(x,y)>>>24)==0,"blink preserves transparent overlay");
                    if(y!=14 || !(x>=9&&x<11 || x>=13&&x<15 || x>=41&&x<43 || x>=45&&x<47))
                        check(normalized.getPixelRGBA(x,y)==closed.getPixelRGBA(x,y),"blink edits eye pixels only");
                }
                check(changed>0,"blink changes actual eye pixels");
                var folder=client.gameDirectory.toPath().resolve("screenshots");
                java.nio.file.Files.createDirectories(folder);
                normalized.writeToFile(folder.resolve("skin-open.png"));half.writeToFile(folder.resolve("skin-half.png"));closed.writeToFile(folder.resolve("skin-closed.png"));
                check(!DollSkin.texture(0).equals(DollSkin.texture(1)),"dynamic blink textures registered");
                com.mojang.logging.LogUtils.getLogger().info("BLINK PIXEL CHECKS PASSED: changed={}",changed);
            }
            boolean artistAlex=DollSkin.usesSlimArms(source);
            com.mojang.logging.LogUtils.getLogger().info("Bundled Lonya artist skin layout detected as {}",
                    artistAlex?"Alex/slim":"Steve/wide");
            for (int[] palm : new int[][]{{48, 16}, {40, 48}}) {
                for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                    check((normalized.getPixelRGBA(palm[0] + x, palm[1] + y) >>> 24) == 255,
                            "normalized full-width palm is opaque");
                }
            }
            for (int y = 0; y < 16; y++) for (int x = 0; x < 64; x++) {
                check(source.getPixelRGBA(x, y) == normalized.getPixelRGBA(x, y),
                        "normalization preserves face and hair pixels");
            }
            check((normalized.getPixelRGBA(56, 48) >>> 24) == 0,
                    "transparent sleeve overlay stays transparent");
        }
        try (NativeImage steve = new NativeImage(64, 64, false)) {
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) steve.setPixelRGBA(x, y, 0xFF6F8DA5);
            check(!DollSkin.usesSlimArms(steve), "opaque wide-arm replacement is Steve");
            try (NativeImage copy = DollSkin.normalizedCopy(steve)) {
                check(copy.getPixelRGBA(54, 20) == steve.getPixelRGBA(54, 20), "Steve UVs are unchanged");
            }
        }
        try (NativeImage alex = new NativeImage(64, 64, false)) {
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) alex.setPixelRGBA(x, y, 0xFF6F8DA5);
            for (int y = 20; y < 32; y++) for (int x = 54; x < 56; x++) alex.setPixelRGBA(x, y, 0);
            for (int y = 52; y < 64; y++) for (int x = 46; x < 48; x++) alex.setPixelRGBA(x, y, 0);
            check(DollSkin.usesSlimArms(alex), "Alex reserved columns are detected independently of the bundled skin");
            try (NativeImage copy = DollSkin.normalizedCopy(alex)) {
                check((copy.getPixelRGBA(48, 16) >>> 24) == 255 && (copy.getPixelRGBA(40, 48) >>> 24) == 255,
                        "Alex normalization fills both palm UV regions");
            }
        }
        var model = new DollRenderer.Model(client.getEntityModels().bakeLayer(DollRenderer.LAYER));
        for (int gesture = 0; gesture < 12; gesture++) {
            float time = gesture * 200 + 60;
            model.setupDecoration(time);
            check(Float.isFinite(model.head.xRot) && Float.isFinite(model.head.yRot), "finite idle transform");
            sameTransform(model.head, model.hat);
            sameTransform(model.body, model.jacket);
            sameTransform(model.leftArm, model.leftSleeve);
            sameTransform(model.rightArm, model.rightSleeve);
            sameTransform(model.leftLeg, model.leftPants);
            sameTransform(model.rightLeg, model.rightPants);
            float headY = model.head.y;
            model.setupDecoration(time);
            check(model.head.y == headY, "render pose must not accumulate between frames");
            check(model.footBottom(model.leftLeg) <= DollRenderer.FEET_Y + 0.001F
                    && model.footBottom(model.rightLeg) <= DollRenderer.FEET_Y + 0.001F,
                    "seated feet never sink into the shelf");
            check(model.leftLeg.x > 2.3F && model.rightLeg.x < -2.3F
                            && model.leftLeg.yRot < 0 && model.rightLeg.yRot > 0,
                    "seated knees and feet open outward instead of folding together");
            check(innerPantsEdge(model.leftPants, true) > innerPantsEdge(model.rightPants, false),
                    "even the outer pants cubes stay apart throughout seated poses");
            var grip=model.umbrellaGrip();
            var gripPose=new PoseStack();model.rightArm.translateAndRotate(gripPose);
            var expectedGrip=new org.joml.Vector3f(-1/16F,10/16F,0).mulPosition(gripPose.last().pose());
            check(grip.distanceTo(new net.minecraft.world.phys.Vec3(expectedGrip.x,expectedGrip.y,expectedGrip.z))<.00001,"umbrella rod starts at actual palm");
            sameTransform(model.rightArm,model.rightSleeve);
        }
        for (int gesture = 0; gesture < 12; gesture++) {
            float boundary = gesture * 200;
            model.setupDecoration(boundary - .001F);
            float[] before = {model.head.xRot,model.head.yRot,model.head.zRot,
                    model.leftArm.xRot,model.rightArm.xRot,model.leftLeg.y,model.rightLeg.y};
            model.setupDecoration(boundary + .001F);
            float[] after = {model.head.xRot,model.head.yRot,model.head.zRot,
                    model.leftArm.xRot,model.rightArm.xRot,model.leftLeg.y,model.rightLeg.y};
            for (int i=0;i<before.length;i++) check(Math.abs(before[i]-after[i])<.001F,
                    "idle gesture boundary is continuous");
            for (float open : new float[]{0,.05F,.5F,1}) {
                model.setupDecoration(boundary+60);
                var grip=model.umbrellaGrip(open);
                check(grip.distanceTo(model.umbrellaGrip(open))<.000001,
                        "repeated entity render sampling does not blend the hand twice");
                var palmPose=new PoseStack();model.rightArm.translateAndRotate(palmPose);
                var palm=new org.joml.Vector3f(-1/16F,10/16F,0).mulPosition(palmPose.last().pose());
                check(grip.distanceTo(new net.minecraft.world.phys.Vec3(palm.x,palm.y,palm.z))<.000001,
                        "partially opened umbrella stays in the actual palm");
                sameTransform(model.rightArm,model.rightSleeve);
            }
        }
        for (int rotation=0;rotation<16;rotation++) {
            var hand=new net.minecraft.world.phys.Vec3(.45, .4, .15).yRot(rotation*net.minecraft.util.Mth.PI/8);
            var canopy=new net.minecraft.world.phys.Vec3(0,1.25,0);
            var tip=com.customdoll.lonya_plush.client.render.DollUmbrellaRenderer.shaftTip(canopy,hand,1.03F,1);
            check(Math.abs(tip.x-hand.x)<.000001 && Math.abs(tip.z-hand.z)<.000001 && tip.y>hand.y,
                    "fully open shared umbrella has one straight upright shaft at every rotation");
        }
        com.mojang.logging.LogUtils.getLogger().info("UMBRELLA MOTION CHECKS PASSED: 12 boundaries, partial grip, 16 rotations");
        float seatedHeadScale = model.head.yScale;
        float seatedArmScale = model.leftArm.yScale;
        float seatedLegScale = model.leftLeg.yScale;
        for (int frame = 0; frame < 80; frame++) {
            model.setupStanding(frame * 0.2F, 0.4F, frame, 0, 0, 0, 0);
            check(model.head.yScale == seatedHeadScale && model.leftArm.yScale == seatedArmScale
                    && model.leftLeg.yScale == seatedLegScale, "standing and sitting use identical proportions");
            sameTransform(model.head, model.hat);
            sameTransform(model.body, model.jacket);
            sameTransform(model.leftArm, model.leftSleeve);
            sameTransform(model.rightArm, model.rightSleeve);
            sameTransform(model.leftLeg, model.leftPants);
            sameTransform(model.rightLeg, model.rightPants);
            float left = model.footBottom(model.leftLeg), right = model.footBottom(model.rightLeg);
            check(left <= DollRenderer.FEET_Y + 0.001F && right <= DollRenderer.FEET_Y + 0.001F,
                    "walking soles never sink into the ground");
            check(Math.abs(Math.max(left, right) - DollRenderer.FEET_Y) < 0.001F,
                    "walking keeps one supporting foot on the ground");
        }
        model.setupStanding(0, 0, 0, 0, 0, 3, 0.5F);
        check(model.head.yScale < seatedHeadScale && model.head.xScale > seatedHeadScale,
                "pat produces a soft head squash");
        sameTransform(model.head, model.hat);
        model.setupStanding(0, 0, 0, 0, 0, 0, 0);
        check(model.head.yScale == seatedHeadScale, "head squash resets on the next idle pose");
        check(Math.abs(model.footBottom(model.leftLeg) - DollRenderer.FEET_Y) < 0.001F,
                "standing soles align to the same item/block ground plane");
        ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());
        check(client.getItemRenderer().getModel(stack, null, null, 0).isCustomRenderer(),
                "all item contexts route to the animated custom renderer");
        check(client.getResourceManager().getResource(DollRenderer.TEXTURE).isPresent(), "shared skin is present");
        check(!client.getItemRenderer().getModel(stack, null, null, 0).getParticleIcon().contents().name()
                        .equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()),
                "particle atlas also resolves the shared skin");
        for (boolean slim : new boolean[]{false, true}) {
            var playerModel = new PlayerModel<LivingEntity>(client.getEntityModels().bakeLayer(
                    slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            for (float crouch : new float[]{0, 4.2F}) for (float pitch : new float[]{-0.7F, 0, 0.7F}) {
                playerModel.head.y = crouch;
                playerModel.head.xRot = pitch;
                playerModel.head.yRot = 0.6F;
                PoseStack headPose = new PoseStack();
                playerModel.head.translateAndRotate(headPose);
                var expected = new org.joml.Vector3f(0, -DollHeadLayer.HEAD_CLEARANCE, 0)
                        .mulPosition(headPose.last().pose());
                PoseStack dollPose = new PoseStack();
                DollHeadLayer.transformToHead(playerModel.head, dollPose);
                var actual = new org.joml.Vector3f().mulPosition(dollPose.last().pose());
                check(actual.distance(expected) < 0.00001F, "head-mounted doll follows pitch, yaw and crouch pivot");
                var above = new org.joml.Vector3f(0, 0.1F, 0).mulPosition(dollPose.last().pose());
                var localAbove = above.mulPosition(new org.joml.Matrix4f(headPose.last().pose()).invert());
                check(localAbove.y < -8.5F / 16F, "entire upright doll stays above outer hat layer");
            }
        }
        net.minecraft.client.renderer.MultiBufferSource forbidden = renderType -> {
            throw new AssertionError("2i HEAD item path must not double-render vanilla custom head layer");
        };
        DollItemRenderer.get().renderByItem(stack, ItemDisplayContext.HEAD, new PoseStack(), forbidden,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    private static void sameTransform(ModelPart base, ModelPart layer) {
        check(base.x == layer.x && base.y == layer.y && base.z == layer.z
                        && base.xRot == layer.xRot && base.yRot == layer.yRot && base.zRot == layer.zRot
                        && base.xScale == layer.xScale && base.yScale == layer.yScale && base.zScale == layer.zScale,
                "outer skin layer follows the complete base transform");
    }

    private static float innerPantsEdge(ModelPart part, boolean left) {
        PoseStack pose = new PoseStack();
        part.translateAndRotate(pose);
        float edge = left ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        for (float x : new float[]{-2.25F, 2.25F}) for (float y : new float[]{-0.25F, 12.25F}) {
            for (float z : new float[]{-2.25F, 2.25F}) {
                float value = new org.joml.Vector3f(x / 16, y / 16, z / 16).mulPosition(pose.last().pose()).x;
                edge = left ? Math.min(edge, value) : Math.max(edge, value);
            }
        }
        return edge;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError("2i visual: " + name);
    }

    /** Same-rig sit/stand/walk/pat comparison, idle variants, and real item contexts. */
    public static Screen preview() {
        return new Screen(Component.literal("lonya_plush / NeoForge-only render verification")) {
            private DollRenderer.Model model;
            private final ItemStack stack = new ItemStack(ModItems.TWO_I_DOLL.get());

            @Override
            public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                if (model == null) model = new DollRenderer.Model(minecraft.getEntityModels().bakeLayer(DollRenderer.LAYER));
                graphics.fill(0, 0, width, height, 0xFFBDD0E0);
                graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);
                String[] names = {"seated doll", "standing doll", "short step L", "short step R", "head pat", "wave",
                        "look around", "swing feet", "stretch", "sleepy", "dust off", "shy"};
                int[] idle = {0, 1, 3, 4, 5, 7};
                for (int variant = 0; variant < 12; variant++) {
                    int x = width * (variant % 6 + 1) / 7;
                    int row = variant / 6;
                    int baseY = row == 0 ? 220 : height - 245;
                    switch (variant) {
                        case 0 -> model.setupDecoration(60);
                        case 1 -> model.setupStanding(0, 0, 0, 0, 0, 0, 0);
                        case 2 -> model.setupStanding(1.7F, 0.4F, 20, 0, 0, 0, 0);
                        case 3 -> model.setupStanding(5.1F, 0.4F, 40, 0, 0, 0, 0);
                        case 4 -> model.setupStanding(0, 0, 20, 0, 0, 3, 0.5F);
                        case 5 -> model.setupStanding(0, 0, 20, 0, 0, 1, 0.5F);
                        default -> model.setupDecoration(idle[variant - 6] * 200 + 60);
                    }
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    float scale = Math.min(70, width / 15.0F);
                    pose.translate(x, baseY - DollRenderer.FEET_Y / 16.0F * scale, 100);
                    pose.scale(scale, scale, scale);
                    pose.mulPose(Axis.YP.rotationDegrees(165));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(DollSkin.texture(model.blink()))),
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.fill(x - 30, baseY + 1, x + 30, baseY + 2, 0xFF71899B);
                    graphics.drawCenteredString(font, names[variant], x, baseY + 8, 0xFFFFFFFF);
                }
                PlayerSkin fixtureSkin = new PlayerSkin(ResourceLocation.withDefaultNamespace(
                        "textures/entity/player/wide/steve.png"), null, null, null, PlayerSkin.Model.WIDE, true);
                for (HumanoidArm arm : HumanoidArm.values()) {
                    int x = width * (arm == HumanoidArm.LEFT ? 2 : 5) / 7;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, 380, 150);
                    pose.scale(105, -105, -105);
                    pose.mulPose(Axis.XP.rotationDegrees(-16));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    PlayerSkin palmSkin=arm==HumanoidArm.LEFT?new PlayerSkin(ResourceLocation.withDefaultNamespace(
                            "textures/entity/player/slim/alex.png"),null,null,null,PlayerSkin.Model.SLIM,true):fixtureSkin;
                    DollHandRenderer.renderOnPalm(stack, arm, palmSkin, true, true, pose, buffers, LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, "actual palm: " + (arm==HumanoidArm.LEFT?"Alex left":"Steve right"), x, 410, 0xFFFFFFFF);
                }
                for (boolean slim : new boolean[]{false, true}) {
                    int x = width * (slim ? 4 : 3) / 7;
                    var playerModel = new PlayerModel<LivingEntity>(minecraft.getEntityModels().bakeLayer(
                            slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                    playerModel.head.y = slim ? 4.2F : 0;
                    playerModel.head.yRot = slim ? 0.4F : -0.3F;
                    playerModel.head.xRot = slim ? 0.2F : -0.1F;
                    playerModel.hat.copyFrom(playerModel.head);
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, 360, 150);
                    pose.scale(80, 80, 80);
                    pose.mulPose(Axis.YP.rotationDegrees(170));
                    var buffers = minecraft.renderBuffers().bufferSource();
                    ResourceLocation skinTexture = slim ? ResourceLocation.withDefaultNamespace(
                            "textures/entity/player/slim/alex.png") : fixtureSkin.texture();
                    var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(skinTexture));
                    playerModel.head.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    playerModel.hat.render(pose, vertices, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    DollHeadLayer.renderOnHead(stack, playerModel.head, pose, buffers, LightTexture.FULL_BRIGHT);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, slim ? "head: Alex crouch" : "head: Steve", x, 410, 0xFFFFFFFF);
                }
                ItemDisplayContext[] contexts = {ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.GROUND, ItemDisplayContext.FIXED};
                for (int i = 0; i < contexts.length; i++) {
                    int x = width * (i + 1) / 8;
                    PoseStack pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x, height - 115, 150);
                    pose.scale(90, -90, 90);
                    var buffers = minecraft.renderBuffers().bufferSource();
                    minecraft.getItemRenderer().renderStatic(stack, contexts[i], LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY, pose, buffers, null, 0);
                    buffers.endBatch();
                    pose.popPose();
                    graphics.drawCenteredString(font, contexts[i].name().replace("PERSON", "P"), x, height - 40, 0xFFFFFFFF);
                }
            }
        };
    }
}
