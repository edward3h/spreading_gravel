package net.ethelred.spreading_gravel;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

public class SpreadingGravelBlock extends FallingBlock {

    private static final List<Direction> SPREAD_ORDER =
        List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);

    public SpreadingGravelBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FallingBlock> codec() {
        return simpleCodec(SpreadingGravelBlock::new);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockState(pos.below()).canBeReplaced()) {
            super.tick(state, level, pos, random);
            return;
        }
        findBestAdjacentPos(level, pos).ifPresent(targetPos -> {
            if (!level.getBlockState(targetPos).canBeReplaced()) return;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            FallingBlockEntity.fall(level, targetPos, state);
        });
    }

    static Optional<BlockPos> findBestAdjacentPos(Level level, BlockPos pos) {
        BlockPos bestPos = null;
        int bestDepth = 0;
        for (Direction dir : SPREAD_ORDER) {
            BlockPos adj = pos.relative(dir);
            if (!level.isLoaded(adj)) continue;
            if (!level.getBlockState(adj).canBeReplaced()) continue;
            int depth = countFallDepth(level, adj);
            if (depth > bestDepth) {
                bestDepth = depth;
                bestPos = adj;
            }
        }
        return Optional.ofNullable(bestPos);
    }

    static int countFallDepth(Level level, BlockPos from) {
        int depth = 0;
        BlockPos check = from.below();
        while (level.getBlockState(check).canBeReplaced()) {
            depth++;
            check = check.below();
        }
        return depth;
    }
}
