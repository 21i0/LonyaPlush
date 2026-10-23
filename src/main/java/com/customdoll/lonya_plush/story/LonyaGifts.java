package com.customdoll.lonya_plush.story;

import com.customdoll.lonya_plush.content.entity.DollEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.Map;

/** Called on the server for an alive, nearby owner. Gifts are consumed, never stored or returned. */
public final class LonyaGifts {
    private static final Map<Item,Integer> VALUES=Map.of(
            Items.EMERALD,1,Items.DIAMOND,1,Items.NETHERITE_INGOT,2,
            Items.GOLD_BLOCK,1,Items.EMERALD_BLOCK,5,Items.DIAMOND_BLOCK,5,Items.NETHERITE_BLOCK,10);

    public static void collect(ServerPlayer player,DollEntity doll,LonyaProgress data) {
        for(var drop:doll.level().getEntitiesOfClass(ItemEntity.class,doll.getBoundingBox().inflate(2),
                item->item.isAlive()&&!item.hasPickUpDelay()&&doll.distanceToSqr(item)<=4)) {
            var thrower=drop.getOwner();
            if(thrower==null||!player.getUUID().equals(thrower.getUUID()))continue;
            if(drop.getTarget()!=null&&!player.getUUID().equals(drop.getTarget()))continue;
            if(!doll.hasLineOfSight(drop))continue;
            var stack=drop.getItem();int value=VALUES.getOrDefault(stack.getItem(),0);
            if(value==0||stack.isEmpty())continue;
            var progress=data.forPlayer(player.getUUID());
            if(progress.affection==Integer.MAX_VALUE)continue;
            int count=stack.getCount(),before=progress.affection;
            int reward=(int)Math.min(Integer.MAX_VALUE,(long)value*count);
            var name=stack.getHoverName();
            // Entity ticks are sequential: discarding the drop prevents another doll awarding it again.
            doll.take(drop,count);drop.discard();data.addAffection(progress,reward);
            player.displayClientMessage(Component.translatable("message.lonya_plush.gift",name,count,progress.affection-before),true);
        }
    }
    private LonyaGifts() { }
}
