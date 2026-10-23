package com.customdoll.lonya_plush.compat;

import com.customdoll.lonya_plush.content.DollOwnership;
import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** This class is only loaded behind ModList's Curios presence check. */
public final class DollCuriosCompat {
    private DollCuriosCompat() { }
    public static void register() {
        CuriosApi.registerCurio(ModItems.TWO_I_DOLL.get(), new ICurioItem() {
            @Override public boolean canEquip(SlotContext context, ItemStack stack) { return mayWear(context, stack); }
            @Override public boolean canEquipFromUse(SlotContext context, ItemStack stack) { return false; }
            @Override public void onEquip(SlotContext context, ItemStack previous, ItemStack stack) { bind(context, stack); }
            @Override public void curioTick(SlotContext context, ItemStack stack) { bind(context, stack); }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DollCuriosCompat::allowAnySlot);
    }
    public static boolean mayWear(SlotContext context, ItemStack stack) {
        return context.entity() instanceof Player p && DollOwnership.mayUse(stack, p);
    }
    private static void bind(SlotContext context, ItemStack stack) {
        if (!context.entity().level().isClientSide && context.entity() instanceof Player p && mayWear(context, stack))
            DollOwnership.claim(stack, p);
    }
    private static void allowAnySlot(CurioCanEquipEvent event) {
        if (event.getStack().is(ModItems.TWO_I_DOLL.get()))
            event.setEquipResult(mayWear(event.getSlotContext(), event.getStack()) ? TriState.TRUE : TriState.FALSE);
    }
    public static boolean isEquipped(LivingEntity entity, ItemStack stack) {
        return CuriosApi.getCuriosInventory(entity).map(inventory -> inventory.getCurios().entrySet().stream().anyMatch(entry -> {
            var stacks = entry.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++)
                if (inventory.isSlotActive(entry.getKey(), i) && ItemStack.isSameItemSameComponents(stack, stacks.getStackInSlot(i))) return true;
            return false;
        })).orElse(false);
    }
}

