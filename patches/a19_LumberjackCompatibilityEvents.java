package com.gb.livingvillagers.event;

import com.gb.livingvillagers.LivingVillagers;
import com.gb.livingvillagers.profession.WorkerProfession;
import com.gb.livingvillagers.task.runtime.WorkerRuntime;
import com.gb.livingvillagers.task.runtime.WorkerRuntimeManager;
import com.gb.livingvillagers.util.VillagerDataAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = LivingVillagers.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LumberjackCompatibilityEvents {
    private static final double DROP_RADIUS = 7.0D;

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.tickCount % 10 != Math.floorMod(villager.getId(), 10)) return;

        VillagerDataAccess.get(villager).ifPresent(data -> {
            if (data.getProfession() != WorkerProfession.LUMBERJACK) return;

            WorkerRuntime runtime = WorkerRuntimeManager.get(villager.getUUID());

            rememberModdedSapling(level, runtime);
            clearBlockingLeaves(level, villager, runtime);
            collectForgottenTreeDrops(level, villager, data.getWorkInventory());
        });
    }

    private static void rememberModdedSapling(ServerLevel level, WorkerRuntime runtime) {
        if (runtime.getLumberjackSapling() != null) return;

        BlockPos target = runtime.getTargetPos();
        if (target == null) return;

        BlockState state = level.getBlockState(target);
        if (!state.is(BlockTags.LOGS)) return;

        ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (logId == null) return;

        String path = logId.getPath();
        String[] candidates = new String[] {
                path.replace("_log", "_sapling"),
                path.replace("_wood", "_sapling"),
                path.replace("_stem", "_fungus"),
                path.replace("_hyphae", "_fungus")
        };

        for (String candidate : candidates) {
            if (candidate.equals(path)) continue;
            ResourceLocation id = new ResourceLocation(logId.getNamespace(), candidate);
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                runtime.setLumberjackSapling(block);
                return;
            }
        }
    }

    private static void clearBlockingLeaves(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        if (level.isNight()) return;
        BlockPos target = runtime.getTargetPos();
        if (target == null) return;

        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1))) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES)) continue;

            double d = pos.distSqr(target);
            if (d < bestDistance) {
                bestDistance = d;
                best = pos.immutable();
            }
        }

        if (best != null) {
            level.destroyBlock(best, true, villager);
        }
    }

    private static void collectForgottenTreeDrops(ServerLevel level, Villager villager, ItemStackHandler inventory) {
        AABB box = villager.getBoundingBox().inflate(DROP_RADIUS, 4.0D, DROP_RADIUS);
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                box,
                item -> item.isAlive() && !item.getItem().isEmpty()
        );

        drops.sort(Comparator.comparingDouble(villager::distanceToSqr));

        for (ItemEntity entity : drops) {
            ItemStack stack = entity.getItem();
            if (!isTreeRelated(stack)) continue;

            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < inventory.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = inventory.insertItem(slot, remaining, false);
            }

            if (remaining.isEmpty()) {
                entity.discard();
            } else if (remaining.getCount() != stack.getCount()) {
                entity.setItem(remaining);
            }
        }
    }

    private static boolean isTreeRelated(ItemStack stack) {
        if (stack.is(net.minecraft.tags.ItemTags.LOGS)) return true;
        if (stack.is(net.minecraft.tags.ItemTags.LEAVES)) return true;
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            return state.is(BlockTags.SAPLINGS);
        }
        return false;
    }

    private LumberjackCompatibilityEvents() {
    }
}
