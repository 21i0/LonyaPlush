package com.customdoll.lonya_plush.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Protocol v1: public registry tags + deterministic mutual-nearest pairing. No foreign Java classes. */
public final class DollUmbrellaProtocol {
    public static final String ROOT = "doll_protocol", KIND = "kind", UMBRELLA = "umbrella";
    public static final String ENTITY_TAG = "doll_protocol:umbrella";
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("doll_protocol", "umbrella_participants");
    public static final TagKey<Item> ITEMS = TagKey.create(Registries.ITEM, ID);
    public static final TagKey<Block> BLOCKS = TagKey.create(Registries.BLOCK, ID);
    public static final TagKey<EntityType<?>> ENTITIES = TagKey.create(Registries.ENTITY_TYPE, ID);
    public static final double RANGE = 1.65;
    public record Anchor(String id, Vec3 feet) { }
    public record Pair(Anchor self, Anchor partner, Vec3 canopy, boolean leader) {
        public boolean shared() { return partner != null; }
    }
    private DollUmbrellaProtocol() { }
    public static boolean isUmbrellaParticipant(ItemStack stack) {
        if (stack.is(ITEMS)) return true;
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && UMBRELLA.equals(data.copyTag().getCompound(ROOT).getString(KIND));
    }
    public static void markUmbrellaParticipant(ItemStack stack) {
        if (isUmbrellaParticipant(stack)) return;
        CompoundTag all = stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        CompoundTag protocol = all.getCompound(ROOT).copy();
        protocol.putString(KIND, UMBRELLA);
        protocol.putInt("version", 1);
        all.put(ROOT, protocol);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(all));
    }
    public static boolean compatible(ItemStack a, ItemStack b) { return isUmbrellaParticipant(a) && isUmbrellaParticipant(b); }
    public static Anchor block(BlockPos pos) { return new Anchor("block:" + pos.asLong(), new Vec3(pos.getX() + .5, pos.getY(), pos.getZ() + .5)); }
    public static Anchor entity(Entity entity) { return new Anchor("entity:" + entity.getUUID(), entity.position()); }
    public static Pair sample(Level level, Anchor self) {
        List<Anchor> nearby = new ArrayList<>();
        nearby.add(self);
        BlockPos origin = BlockPos.containing(self.feet());
        // Bounded, loaded-only search, including a partner's competing neighbors.
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3,-1,-3), origin.offset(3,1,3))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).is(BLOCKS)) {
                Anchor anchor = block(pos);
                if (!anchor.id().equals(self.id())) nearby.add(anchor);
            }
        }
        for (Entity entity : level.getEntities((Entity)null, new AABB(self.feet(), self.feet()).inflate(RANGE * 2),
                e -> e.isAlive() && e.getType().is(ENTITIES))) {
            Anchor anchor = entity(entity);
            if (!anchor.id().equals(self.id())) nearby.add(anchor);
        }
        Anchor partner = nearest(self, nearby);
        if (partner != null && !self.equals(nearest(partner, nearby))) partner = null;
        Vec3 center = partner == null ? self.feet() : self.feet().add(partner.feet()).scale(.5);
        double ground = partner == null ? self.feet().y : Math.max(self.feet().y, partner.feet().y);
        return new Pair(self, partner, new Vec3(center.x, ground + 1.25, center.z),
                partner == null || self.id().compareTo(partner.id()) < 0);
    }
    private static Anchor nearest(Anchor anchor, List<Anchor> candidates) {
        return candidates.stream().filter(other -> !anchor.id().equals(other.id())
                && Math.abs(other.feet().y - anchor.feet().y) <= .55
                && other.feet().distanceToSqr(anchor.feet()) <= RANGE * RANGE)
                .min(Comparator.<Anchor>comparingDouble(other -> other.feet().distanceToSqr(anchor.feet())).thenComparing(Anchor::id)).orElse(null);
    }
    public static boolean raining(Level level, Pair pair) {
        return level.isRainingAt(BlockPos.containing(pair.self().feet().add(0,.8,0)))
                || pair.partner() != null && level.isRainingAt(BlockPos.containing(pair.partner().feet().add(0,.8,0)));
    }
}

