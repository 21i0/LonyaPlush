package com.customdoll.lonya_plush.content;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

/** Ownership is carried by the item component, so every form can round-trip it. */
public final class DollOwnership {
    private static final String KEY = "doll_owner";
    private DollOwnership() { }
    public static UUID get(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().hasUUID(KEY) ? data.copyTag().getUUID(KEY) : null;
    }
    public static boolean mayUse(ItemStack stack, Player player) {
        UUID owner = get(stack);
        return owner == null || owner.equals(player.getUUID());
    }
    public static void claim(ItemStack stack, Player player) {
        if (get(stack) != null) return;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(KEY, player.getUUID());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static void deny(Player player) {
        player.displayClientMessage(Component.translatable("message.lonya_plush.owner"), true);
    }
}
