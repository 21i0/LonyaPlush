package com.customdoll.lonya_plush.checks;
import com.customdoll.lonya_plush.content.*;
import com.customdoll.lonya_plush.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import static com.customdoll.lonya_plush.checks.RegressionChecks.*;

public final class OwnershipChecks {
    public static void run(ServerLevel level, ServerPlayer owner, BlockPos pos, DollDisplayBlockEntity display) {
        var guest = player(level,"DollGuest");
        guest.setShiftKeyDown(true);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        level.getBlockState(pos).useWithoutItem(level,guest,hit);
        check(level.getBlockEntity(pos) == display,"nonowner cannot pick up display");
        guest.setShiftKeyDown(false);
        level.getBlockState(pos).useWithoutItem(level,guest,hit);
        check(!display.isSpinning(),"nonowner cannot rotate display");
        var event = new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,display.getBlockState(),guest);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        check(event.isCanceled(),"nonowner break rejected");
        for(int i=0;i<36;i++)owner.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        owner.setShiftKeyDown(true);
        level.getBlockState(pos).useWithoutItem(level,owner,hit);
        check(level.getBlockEntity(pos)==display,"full inventory retains display");
        owner.setShiftKeyDown(false);owner.getInventory().clearContent();
        ItemStack stack=display.copyDoll();
        check(DollOwnership.get(stack).equals(owner.getUUID()),"placed doll binds owner");
        check(!ModItems.TWO_I_DOLL.get().canEquip(stack,net.minecraft.world.entity.EquipmentSlot.HEAD,guest),"nonowner helmet denied");
        check(DollUmbrellaProtocol.isUmbrellaParticipant(stack),"public item protocol tag loaded");
        com.mojang.logging.LogUtils.getLogger().info("OWNERSHIP CHECKS PASSED");
    }
}

