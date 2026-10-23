package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.customdoll.lonya_plush.content.DollUmbrellaProtocol;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Geometry is cached; rain and partner positions are derived from the public v1 protocol. */
public final class DollUmbrellaRenderer {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath("lonya_plush", "textures/entity/umbrella.png");
    private static final ModelPart HANDLE = handle();
    private static ModelPart handle() {
        var mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0,0).addBox(-.2F,0,-.2F,.4F,16,.4F), PartPose.ZERO);
        return LayerDefinition.create(mesh,1,1).bakeRoot();
    }
    private record Opening(double time,float value) { }
    private static final java.util.Map<String,Opening> OPENINGS=new java.util.LinkedHashMap<>();
    public static float opening(String key,boolean wet,double time) {
        Opening previous=OPENINGS.get(key);
        float value=previous==null ? 0 : previous.value();
        double elapsed=previous==null ? 0 : Math.max(0,Math.min(10,time-previous.time()));
        value=net.minecraft.util.Mth.clamp(value+(float)elapsed*(wet&&DollUmbrellaPalette.enabled()?.12F:-.12F),0,1);
        if(OPENINGS.size()>512) OPENINGS.remove(OPENINGS.keySet().iterator().next());
        OPENINGS.put(key,new Opening(time,value));
        return value*value*(3-2*value);
    }
    public static void renderPair(DollUmbrellaProtocol.Pair pair, Vec3 origin, Vec3 grip,float open,PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!DollUmbrellaPalette.enabled()) return;
        if (!pair.leader() || open<=.01F) return;
        Vec3 delta = pair.canopy().subtract(origin);
        if (!pair.shared()) delta = new Vec3(grip.x, delta.y, grip.z);
        float radius = pair.shared() ? 1.03F : .68F;
        // An offset shared canopy is supported at its underside, never by a bent mast.
        Vec3 tip = shaftTip(delta, grip, radius, open);
        renderRod(grip,tip,pose,buffers,light,overlay);
        pose.pushPose();
        pose.translate(delta.x, delta.y, delta.z);
        // Canopy mesh uses Minecraft model coordinates (downward Y).
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(radius, .68F, radius);
        pose.scale(open,.65F+open*.35F,open);
        DollUmbrellaCanopy.render(pose, buffers, light, overlay);
        pose.popPose();
    }
    public static Vec3 shaftTip(Vec3 canopy, Vec3 grip, float radius, float open) {
        double x = grip.x - canopy.x, z = grip.z - canopy.z;
        double angle = Math.atan2(z, x), sector = Math.PI / 4;
        double fraction = angle / sector - Math.floor(angle / sector);
        double edge = DollUmbrellaCanopy.CANOPY_RADIUS / 16 * radius * open
                * Math.cos(sector / 2) / Math.cos((fraction - .5) * sector);
        double distance = Math.sqrt(x*x + z*z);
        double r = Math.min(.98, distance / Math.max(.001, edge));
        double fit = distance > edge * .98 ? edge * .98 / distance : 1;
        double surface = (-.45 + 4.9 * Math.pow(r, 1.65)
                + .38 * Math.sin(fraction * Math.PI) * r*r + .08) / 16;
        return new Vec3(canopy.x + x*fit, canopy.y - surface*.68*(.65+open*.35), canopy.z + z*fit);
    }
    private static void renderRod(Vec3 grip,Vec3 canopy,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        Vec3 direction=canopy.subtract(grip);
        if(direction.lengthSqr()<.00001) return;
        pose.pushPose();pose.translate(grip.x,grip.y,grip.z);
        pose.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),direction.normalize().toVector3f()));
        pose.scale(1,(float)direction.length(),1);
        HANDLE.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)),light,overlay,0xFF686781);
        pose.popPose();
    }
    public static void renderCarried(Vec3 grip,float open,PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!DollUmbrellaPalette.enabled() || open<=.01F) return;
        Vec3 canopy=grip.add(.15,.85,0);
        renderRod(grip,canopy,pose,buffers,light,overlay);
        pose.pushPose();
        pose.translate(canopy.x,canopy.y,canopy.z);
        pose.mulPose(Axis.XP.rotationDegrees(180));
        pose.scale(.62F,.62F,.62F);
        pose.scale(open,.65F+open*.35F,open);
        DollUmbrellaCanopy.render(pose,buffers,light,overlay);
        pose.popPose();
    }
}

