package com.gb.livingvillagers.work;

import com.gb.livingvillagers.capability.ILivingVillagerData;
import com.gb.livingvillagers.config.LivingVillagersConfig;
import com.gb.livingvillagers.profession.WorkerProfession;
import com.gb.livingvillagers.storage.VillageStorage;
import com.gb.livingvillagers.task.TaskState;
import com.gb.livingvillagers.task.runtime.WorkerRuntime;
import com.gb.livingvillagers.village.VillageSettingsData;
import com.gb.livingvillagers.village.VillageSettingsSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Heightmap;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public final class WorkerTaskManager {
    private static final double ARRIVAL_DISTANCE_SQR = 7.0D;
    private static final int LUMBERJACK_ACTIONS_BEFORE_DEPOSIT = 12;
    private static final int FARMER_ACTIONS_BEFORE_DEPOSIT = 14;
    private static final int DROP_PICKUP_DELAY_TICKS = 20;

    public static void tick(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        if (data.getProfession() == WorkerProfession.UNASSIGNED || villager.isBaby()) {
            clearBlockBreaking(level, villager, runtime.getTargetPos());
            setStorageOpen(level, runtime.getStoragePos(), false);
            return;
        }

        runtime.tickCooldown(10);
        WorkerTaskExecutor.syncDisplayedWorkTool(villager, data);
        if (!level.isDay() || villager.isSleeping()) {
            villager.getNavigation().stop();
            clearBlockBreaking(level, villager, runtime.getTargetPos());
            setStorageOpen(level, runtime.getStoragePos(), false);
            data.setTaskState(TaskState.IDLE);
            data.setCurrentTaskId("resting");
            return;
        }

        switch (data.getTaskState()) {
            case IDLE -> handleIdle(level, villager, data, runtime);
            case CHECK_NEEDS -> handleCheckNeeds(data);
            case CHECK_INVENTORY -> handleCheckInventory(level, villager, data, runtime);
            case GET_REQUIRED_ITEMS -> handleGetRequiredItems(level, villager, data, runtime);
            case SELECT_NEW_TASK -> handleSelectTask(level, villager, data, runtime);
            case TRAVEL_TO_TASK -> handleTravel(level, villager, data, runtime);
            case PERFORM_TASK -> handlePerform(level, villager, data, runtime);
            case RETURN -> handleReturn(level, villager, data, runtime);
            case STORE_ITEMS -> handleStore(level, villager, data, runtime);
        }
    }

    private static void handleIdle(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        clearBlockBreaking(level, villager, runtime.getTargetPos());
        setStorageOpen(level, runtime.getStoragePos(), false);
        if (runtime.getIdleCooldown() > 0) return;
        data.setCurrentTaskId("checking_village_needs");
        data.setTaskState(TaskState.CHECK_NEEDS);
    }

    private static void handleCheckNeeds(ILivingVillagerData data) {
        data.setCurrentTaskId("checking_inventory");
        data.setTaskState(TaskState.CHECK_INVENTORY);
    }

    private static void handleCheckInventory(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        if (inventoryItemCount(data) >= LivingVillagersConfig.DEPOSIT_AFTER_ITEMS.get()) {
            BlockPos storagePos = resolveStorage(level, villager, runtime);
            if (storagePos != null) {
                data.setCurrentTaskId("return_to_storage");
                data.setTaskState(TaskState.RETURN);
                return;
            }
        }
        data.setTaskState(TaskState.GET_REQUIRED_ITEMS);
    }

    private static void handleGetRequiredItems(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        if (WorkerTaskExecutor.ensureRequiredItems(level, villager, data, runtime)) {
            data.setTaskState(TaskState.SELECT_NEW_TASK);
            return;
        }
        runtime.setIdleCooldown(40);
        data.setTaskState(TaskState.IDLE);
    }

    private static void handleSelectTask(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        VillageSettingsSnapshot settings = VillageSettingsData.get(level).snapshot();
        int searchRadius = data.getProfession() == WorkerProfession.MINER
                ? settings.minerSearchRadius()
                : LivingVillagersConfig.WORK_SEARCH_RADIUS.get();

        BlockPos target = WorkerTaskExecutor.selectTarget(level, villager, data, runtime, searchRadius);
        if (target == null) {
            clearBlockBreaking(level, villager, runtime.getTargetPos());

            if (inventoryItemCount(data) > 0 && shouldReturnToStorage(data, runtime, settings)) {
                BlockPos storagePos = resolveStorage(level, villager, runtime);
                if (storagePos != null) {
                    runtime.setPatrolling(false);
                    data.setCurrentTaskId("return_to_storage");
                    data.setTaskState(TaskState.RETURN);
                    navigate(villager, storagePos);
                    return;
                }
            }

            if (data.getProfession() == WorkerProfession.MINER && WorkerTools.hasPickaxe(data.getWorkInventory())) {
                BlockPos patrol = createPatrolTarget(level, villager, runtime, settings.minerPatrolRadius());
                runtime.setPatrolling(true);
                runtime.setTargetPos(patrol);
                data.setCurrentTaskId("patrolling_for_ore");
                data.setTaskState(TaskState.TRAVEL_TO_TASK);
                navigate(villager, patrol);
                return;
            }

            data.setCurrentTaskId(switch (data.getProfession()) {
                case BLACKSMITH -> "waiting_stage_3_blacksmith";
                case BUILDER -> "waiting_stage_4_builder";
                case GUARD, ARCHER -> "waiting_stage_6_defense";
                case MINER -> "waiting_for_pickaxe";
                case LUMBERJACK -> "looking_for_tree";
                case FARMER -> "looking_for_crops";
                default -> "no_task_found";
            });
            runtime.setIdleCooldown(60);
            data.setTaskState(TaskState.IDLE);
            villager.getNavigation().stop();
            return;
        }

        runtime.setPatrolling(false);
        runtime.setTargetPos(target);
        data.setCurrentTaskId(taskIdFor(data.getProfession()));
        data.setTaskState(TaskState.TRAVEL_TO_TASK);
        navigate(villager, target);
    }

    private static void handleTravel(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        BlockPos target = runtime.getTargetPos();
        if (target == null || !level.isLoaded(target)) {
            cancelTask(level, villager, data, runtime);
            return;
        }

        if (runtime.isPatrolling() && data.getProfession() == WorkerProfession.MINER) {
            VillageSettingsSnapshot settings = VillageSettingsData.get(level).snapshot();
            BlockPos ore = WorkerTaskExecutor.selectTarget(level, villager, data, runtime, settings.minerSearchRadius());
            if (ore != null) {
                runtime.setPatrolling(false);
                runtime.setTargetPos(ore);
                data.setCurrentTaskId("mine_resource");
                navigate(villager, ore);
                return;
            }
        }

        if (villager.distanceToSqr(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQR) {
            villager.getNavigation().stop();
            runtime.resetStuckTicks();
            if (runtime.isPatrolling()) {
                runtime.clearTaskTarget();
                runtime.setIdleCooldown(20);
                data.setCurrentTaskId("patrol_checkpoint");
                data.setTaskState(TaskState.IDLE);
                return;
            }
            runtime.setActionCooldown(actionDelayFor(data, level, runtime.getTargetPos()));
            data.setTaskState(TaskState.PERFORM_TASK);
            return;
        }

        runtime.addStuckTicks(10);
        if (runtime.getStuckTicks() > LivingVillagersConfig.TASK_TIMEOUT_TICKS.get()) {
            cancelTask(level, villager, data, runtime);
            return;
        }

        if (runtime.getStuckTicks() % 40 == 0) navigate(villager, target);
    }

    private static void handlePerform(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        BlockPos target = runtime.getTargetPos();

        if (runtime.isCollectingDrops()) {
            villager.getNavigation().stop();
            data.setCurrentTaskId("collecting_drops");
            if (target != null) {
                villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.35D, target.getZ() + 0.5D, 30.0F, 30.0F);
            }
            if (runtime.getActionCooldown() > 0) return;
            WorkerTaskExecutor.collectNearbyDrops(level, villager, data, target);
            runtime.setCollectingDrops(false);
            runtime.clearTaskTarget();
            finishWorkCycle(level, villager, data, runtime);
            return;
        }

        if (runtime.getActionCooldown() > 0) {
            data.setCurrentTaskId(waitTaskIdFor(data.getProfession()));
            if (target != null && (data.getProfession() == WorkerProfession.MINER || data.getProfession() == WorkerProfession.LUMBERJACK)) {
                villager.getNavigation().stop();
                villager.getLookControl().setLookAt(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, 30.0F, 30.0F);
                if (runtime.getActionCooldown() % 20 == 0) villager.swing(InteractionHand.MAIN_HAND);
                updateBlockBreaking(level, villager, target, data, runtime);
            }
            return;
        }

        clearBlockBreaking(level, villager, target);
        boolean success = WorkerTaskExecutor.perform(level, villager, data, target);
        if (success) {
            data.addExperience(1);
            runtime.incrementCompletedWorkActions();
        }

        if (success && data.getProfession() == WorkerProfession.MINER) {
            runtime.setCollectingDrops(true);
            runtime.setActionCooldown(DROP_PICKUP_DELAY_TICKS);
            data.setCurrentTaskId("collecting_drops");
            return;
        }

        runtime.clearTaskTarget();
        finishWorkCycle(level, villager, data, runtime);
    }

    private static void finishWorkCycle(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        VillageSettingsSnapshot settings = VillageSettingsData.get(level).snapshot();
        BlockPos storagePos = resolveStorage(level, villager, runtime);
        if (storagePos != null && inventoryItemCount(data) > 0 && shouldReturnToStorage(data, runtime, settings)) {
            data.setCurrentTaskId("return_to_storage");
            data.setTaskState(TaskState.RETURN);
            navigate(villager, storagePos);
            return;
        }
        data.setTaskState(TaskState.GET_REQUIRED_ITEMS);
    }

    private static void handleReturn(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        clearBlockBreaking(level, villager, runtime.getTargetPos());
        BlockPos storagePos = resolveStorage(level, villager, runtime);
        if (storagePos == null) {
            data.setTaskState(TaskState.SELECT_NEW_TASK);
            return;
        }

        if (villager.distanceToSqr(storagePos.getX() + 0.5D, storagePos.getY() + 0.5D, storagePos.getZ() + 0.5D) <= ARRIVAL_DISTANCE_SQR) {
            villager.getNavigation().stop();
            runtime.setActionCooldown(storeDelayFor(data.getProfession()));
            data.setTaskState(TaskState.STORE_ITEMS);
            return;
        }
        navigate(villager, storagePos);
    }

    private static void handleStore(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        if (runtime.getActionCooldown() > 0) {
            data.setCurrentTaskId("storing_items");
            setStorageOpen(level, runtime.getStoragePos(), true);
            return;
        }

        Container storage = VillageStorage.getContainer(level, runtime.getStoragePos());
        if (storage != null) {
            int maxStock = VillageSettingsData.get(level).maxStockFor(data.getProfession());
            VillageStorage.depositResourcesWithLimit(data.getWorkInventory(), storage, maxStock);
        }
        setStorageOpen(level, runtime.getStoragePos(), false);
        runtime.resetCompletedWorkActions();
        data.setCurrentTaskId("checking_tools");
        data.setTaskState(TaskState.GET_REQUIRED_ITEMS);
    }

    private static BlockPos resolveStorage(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        if (VillageStorage.getContainer(level, runtime.getStoragePos()) != null) return runtime.getStoragePos();
        BlockPos storagePos = VillageStorage.findNearest(level, villager.blockPosition(), LivingVillagersConfig.STORAGE_SEARCH_RADIUS.get(), 4);
        runtime.setStoragePos(storagePos);
        return storagePos;
    }

    private static int inventoryItemCount(ILivingVillagerData data) {
        int total = 0;
        for (int slot = 0; slot < data.getWorkInventory().getSlots(); slot++) {
            ItemStack stack = data.getWorkInventory().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof net.minecraft.world.item.PickaxeItem || stack.getItem() instanceof net.minecraft.world.item.AxeItem) continue;
            total += stack.getCount();
        }
        return total;
    }

    private static boolean shouldReturnToStorage(ILivingVillagerData data, WorkerRuntime runtime, VillageSettingsSnapshot settings) {
        if (inventoryItemCount(data) >= LivingVillagersConfig.DEPOSIT_AFTER_ITEMS.get()) return true;
        int actionThreshold = switch (data.getProfession()) {
            case MINER -> settings.minerBlocksBeforeDeposit();
            case LUMBERJACK -> LUMBERJACK_ACTIONS_BEFORE_DEPOSIT;
            case FARMER -> FARMER_ACTIONS_BEFORE_DEPOSIT;
            default -> 1;
        };
        return runtime.getCompletedWorkActions() >= actionThreshold;
    }

    private static int actionDelayFor(ILivingVillagerData data, ServerLevel level, BlockPos target) {
        if (data.getProfession() != WorkerProfession.MINER) {
            return switch (data.getProfession()) {
                case LUMBERJACK -> 70;
                case FARMER -> 30;
                default -> 20;
            };
        }

        ItemStack pickaxe = WorkerTools.getPickaxe(data.getWorkInventory());
        if (pickaxe.isEmpty()) return 100;
        BlockState state = target == null ? Blocks.STONE.defaultBlockState() : level.getBlockState(target);
        float destroySpeed = Math.max(1.0F, pickaxe.getDestroySpeed(state));
        int efficiency = WorkerTools.efficiencyLevel(pickaxe);
        int delay = Math.round(145.0F - destroySpeed * 7.0F - efficiency * 12.0F);
        return Math.max(35, Math.min(120, delay));
    }

    private static int storeDelayFor(WorkerProfession profession) {
        return switch (profession) {
            case MINER -> 120;
            case LUMBERJACK -> 80;
            case FARMER -> 50;
            default -> 20;
        };
    }

    private static String taskIdFor(WorkerProfession profession) {
        return switch (profession) {
            case FARMER -> "harvest_crop";
            case MINER -> "mine_resource";
            case LUMBERJACK -> "cut_tree";
            default -> "work";
        };
    }

    private static String waitTaskIdFor(WorkerProfession profession) {
        return switch (profession) {
            case MINER -> "mining";
            case LUMBERJACK -> "chopping";
            case FARMER -> "harvesting";
            default -> "working";
        };
    }

    private static void updateBlockBreaking(ServerLevel level, Villager villager, BlockPos target, ILivingVillagerData data, WorkerRuntime runtime) {
        int total = Math.max(10, actionDelayFor(data, level, target));
        int elapsed = Math.max(0, total - runtime.getActionCooldown());
        int progress = Math.min(9, Math.max(0, (elapsed * 10) / total));
        level.destroyBlockProgress(villager.getId(), target, progress);
    }

    private static BlockPos createPatrolTarget(ServerLevel level, Villager villager, WorkerRuntime runtime, int radius) {
        BlockPos center = villageCenter(level, villager, runtime);
        int step = runtime.nextPatrolStep();
        int[][] dirs = {{1,0},{1,1},{0,1},{-1,1},{-1,0},{-1,-1},{0,-1},{1,-1}};
        int dx = dirs[step][0] * radius;
        int dz = dirs[step][1] * radius;
        BlockPos column = new BlockPos(center.getX() + dx, center.getY(), center.getZ() + dz);
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    private static BlockPos villageCenter(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isPresent() && jobSite.get().dimension() == level.dimension()) return jobSite.get().pos();
        if (runtime.getStoragePos() != null) return runtime.getStoragePos();
        return villager.blockPosition();
    }

    private static void clearBlockBreaking(ServerLevel level, Villager villager, BlockPos target) {
        if (target != null) level.destroyBlockProgress(villager.getId(), target, -1);
    }

    private static void setStorageOpen(ServerLevel level, BlockPos pos, boolean open) {
        if (pos == null) return;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.BARREL) && state.hasProperty(BarrelBlock.OPEN)) {
            if (state.getValue(BarrelBlock.OPEN) != open) level.setBlock(pos, state.setValue(BarrelBlock.OPEN, open), 3);
            return;
        }
        if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
            level.blockEvent(pos, state.getBlock(), 1, open ? 1 : 0);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static void navigate(Villager villager, BlockPos target) {
        villager.getNavigation().moveTo(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D, LivingVillagersConfig.WORK_MOVE_SPEED.get());
    }

    private static void cancelTask(ServerLevel level, Villager villager, ILivingVillagerData data, WorkerRuntime runtime) {
        villager.getNavigation().stop();
        clearBlockBreaking(level, villager, runtime.getTargetPos());
        setStorageOpen(level, runtime.getStoragePos(), false);
        runtime.clearTaskTarget();
        runtime.setIdleCooldown(40);
        data.setCurrentTaskId("task_cancelled");
        data.setTaskState(TaskState.IDLE);
    }

    private WorkerTaskManager() {
    }
}