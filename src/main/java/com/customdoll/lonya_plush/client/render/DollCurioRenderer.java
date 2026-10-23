package com.customdoll.lonya_plush.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.customdoll.lonya_plush.content.DollUmbrellaProtocol;
import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public final class DollCurioRenderer implements ICurioRenderer {
    public static void register() { CuriosRendererRegistry.register(ModItems.TWO_I_DOLL.get(), DollCurioRenderer::new); }
    @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTick, float age, float yaw, float pitch) {
        if (parent.getModel() instanceof HeadedModel model && isDisplayed(context)) {
            pose.pushPose();
            DollHeadLayer.transformToHead(model.getHead(), pose);
            DollItemRenderer.get().renderCarried(stack, pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, context.entity());
            pose.popPose();
        }
    }
    public static boolean isDisplayed(SlotContext context) {
        var wearer = context.entity();
        if (DollUmbrellaProtocol.isUmbrellaParticipant(wearer.getItemBySlot(EquipmentSlot.HEAD))) return false;
        return CuriosApi.getCuriosInventory(wearer).map(inventory -> {
            for (var entry : inventory.getCurios().entrySet()) {
                var handler = entry.getValue();
                for (int i = 0; i < handler.getStacks().getSlots(); i++) {
                    if (!inventory.isSlotActive(entry.getKey(), i)) continue;
                    ItemStack cosmetic = handler.getCosmeticStacks().getStackInSlot(i);
                    boolean useCosmetic = !cosmetic.isEmpty();
                    boolean visible = handler.getRenders().size() > i && handler.getRenders().get(i);
                    ItemStack displayed = useCosmetic ? cosmetic : visible ? handler.getStacks().getStackInSlot(i) : ItemStack.EMPTY;
                    if (DollUmbrellaProtocol.isUmbrellaParticipant(displayed))
                        return context.identifier().equals(entry.getKey()) && context.index() == i && context.cosmetic() == useCosmetic;
                }
            }
            return false;
        }).orElse(false);
    }
}

