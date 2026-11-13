package dev.kizuna.meridian.villager.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 木人桩：桩身居中、木臂朝放置的人伸出，结构旋转和镜像时一起转。
 * 轮廓与碰撞都是桩身外框，高 24 像素即 1.5 格，和栅栏一样跳不上去。
 */
public final class WoodenDummyBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // 桩身按方块内像素给出，水平居中；24 像素高即 1.5 格。
    private static final double POST_INSET = 4;
    private static final double POST_HEIGHT = 24;
    private static final double BLOCK_PIXELS = 16;
    private static final VoxelShape POST = Block.box(
            POST_INSET, 0, POST_INSET, BLOCK_PIXELS - POST_INSET, POST_HEIGHT, BLOCK_PIXELS - POST_INSET);

    public WoodenDummyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        // 默认朝北，放置时改成朝向放置者。
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 木臂朝向放置的人。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        // 结构旋转时朝向跟着转。
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        // 结构镜像按朝向换算成旋转。
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 桩身外框水平对称，朝向不改变形状。
        return POST;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // 唯一的方块状态是水平朝向。
        builder.add(FACING);
    }
}
