package com.customdoll.lonya_plush.content.block;

import com.customdoll.lonya_plush.content.blockentity.DollDisplayBlockEntity;
import com.customdoll.lonya_plush.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import com.customdoll.lonya_plush.content.DollOwnership;

/** Animated client-side decoration. The complete doll item survives placement and pickup. */
public final class DollDisplayBlock extends Block implements EntityBlock {
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 15, 13);

    public DollDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        int rotation = Math.floorMod(Math.round(context.getRotation() / 22.5F) + 8, 16);
        return defaultBlockState().setValue(ROTATION, rotation);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ROTATION, rotation.rotate(state.getValue(ROTATION), 16));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(ROTATION, mirror.mirror(state.getValue(ROTATION), 16));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DollDisplayBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof DollDisplayBlockEntity display) {
            ItemStack placed = stack.copyWithCount(1);
            if (!level.isClientSide && placer instanceof Player player) DollOwnership.claim(placed, player);
            display.setDoll(placed);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof DollDisplayBlockEntity display
                ? display.copyDoll() : ItemStack.EMPTY;
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof DollDisplayBlockEntity display ? display.copyDoll()
                : ItemStack.EMPTY);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        interact(state, level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        return interact(state, level, pos, player);
    }

    private InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof DollDisplayBlockEntity display && !DollOwnership.mayUse(display.copyDoll(), player)) {
            DollOwnership.deny(player); return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                ItemStack doll = getCloneItemStack(level, pos, state);
                if (player.getInventory().getFreeSlot() < 0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.lonya_plush.full"), true);
                    return InteractionResult.CONSUME;
                }
                DollOwnership.claim(doll, player);
                // Remove first, then hand back exactly one stack. No loot path runs twice.
                if (level.removeBlock(pos, false)) {
                    if (!player.getInventory().add(doll) && !doll.isEmpty()) popResource(level, pos, doll);
                    player.getInventory().setChanged();
                }
            } else if (level.getBlockEntity(pos) instanceof DollDisplayBlockEntity display) {
                display.toggleSpinning();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
