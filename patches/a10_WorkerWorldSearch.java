package com.gb.livingvillagers.work;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public final class WorkerWorldSearch {
    public static BlockPos findMatureCrop(ServerLevel level, BlockPos origin, int radius) {
        return findNearest(level, origin, radius, 3, state -> {
            if (!(state.getBlock() instanceof CropBlock crop)) {
                return false;
            }
            return crop.isMaxAge(state);
        });
    }

    public static BlockPos findNaturalLog(ServerLevel level, BlockPos origin, int radius) {
        return findNearest(level, origin, radius, 5, state -> state.is(BlockTags.LOGS), pos -> hasLeavesNearby(level, pos));
    }

    public static BlockPos findExposedIronOre(ServerLevel level, BlockPos origin, int radius) {
        return findNearest(level, origin, radius, 12, WorkerWorldSearch::isIronOre, pos -> isExposed(level, pos));
    }

    public static BlockPos findExposedCoalOre(ServerLevel level, BlockPos origin, int radius) {
        return findNearest(level, origin, radius, 12, WorkerWorldSearch::isCoalOre, pos -> isExposed(level, pos));
    }

    public static BlockPos findExposedCopperOre(ServerLevel level, BlockPos origin, int radius) {
        return findNearest(level, origin, radius, 12, WorkerWorldSearch::isCopperOre, pos -> isExposed(level, pos));
    }

    public static BlockPos findExposedOre(ServerLevel level, BlockPos origin, int radius, ItemStack tool) {
        if (tool.isEmpty()) {
            return null;
        }
        return findNearest(
                level,
                origin,
                radius,
                12,
                state -> isSupportedOre(state) && tool.isCorrectToolForDrops(state),
                pos -> isExposed(level, pos)
        );
    }

    private static boolean isIronOre(BlockState state) {
        return state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE);
    }

    private static boolean isCoalOre(BlockState state) {
        return state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE);
    }

    private static boolean isCopperOre(BlockState state) {
        return state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE);
    }

    private static boolean isSupportedOre(BlockState state) {
        return isIronOre(state)
                || isCoalOre(state)
                || isCopperOre(state)
                || state.is(Blocks.GOLD_ORE)
                || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                || state.is(Blocks.REDSTONE_ORE)
                || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)
                || state.is(Blocks.LAPIS_ORE)
                || state.is(Blocks.DEEPSLATE_LAPIS_ORE)
                || state.is(Blocks.DIAMOND_ORE)
                || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                || state.is(Blocks.EMERALD_ORE)
                || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
    }

    private static boolean isExposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(direction));
            if (neighbor.isAir() || !neighbor.getFluidState().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLeavesNearby(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.getBlockState(cursor).is(BlockTags.LEAVES)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static BlockPos findNearest(
            ServerLevel level,
            BlockPos origin,
            int horizontalRadius,
            int verticalRadius,
            Predicate<BlockState> statePredicate
    ) {
        return findNearest(level, origin, horizontalRadius, verticalRadius, statePredicate, ignored -> true);
    }

    private static BlockPos findNearest(
            ServerLevel level,
            BlockPos origin,
            int horizontalRadius,
            int verticalRadius,
            Predicate<BlockState> statePredicate,
            Predicate<BlockPos> positionPredicate
    ) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!statePredicate.test(state) || !positionPredicate.test(cursor)) {
                        continue;
                    }
                    double distance = cursor.distSqr(origin);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    private WorkerWorldSearch() {
    }
}