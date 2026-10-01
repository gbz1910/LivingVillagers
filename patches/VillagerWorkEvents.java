package com.gb.livingvillagers.event;

import com.gb.livingvillagers.profession.WorkerProfession;
import com.gb.livingvillagers.task.TaskState;
import com.gb.livingvillagers.task.runtime.WorkerRuntime;
import com.gb.livingvillagers.task.runtime.WorkerRuntimeManager;
import com.gb.livingvillagers.util.VillagerDataAccess;
import com.gb.livingvillagers.util.VillagerNameManager;
import com.gb.livingvillagers.util.VillagerProfessionMapper;
import com.gb.livingvillagers.work.WorkerTaskManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;

public final class VillagerWorkEvents {
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)) {
            return;
        }

        // During a physical work interaction, vanilla villager AI must not walk away.
        // This runs every tick, while the heavier task logic below still runs only every 10 ticks.
        VillagerDataAccess.get(villager).ifPresent(data -> {
            WorkerRuntime runtime = WorkerRuntimeManager.get(villager.getUUID());
            TaskState state = data.getTaskState();
            if (state == TaskState.PERFORM_TASK || state == TaskState.STORE_ITEMS) {
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

                Vec3 motion = villager.getDeltaMovement();
                villager.setDeltaMovement(0.0D, motion.y, 0.0D);

                BlockPos focus = state == TaskState.PERFORM_TASK
                        ? runtime.getTargetPos()
                        : runtime.getStoragePos();
                if (focus != null) {
                    villager.getLookControl().setLookAt(
                            focus.getX() + 0.5D,
                            focus.getY() + 0.5D,
                            focus.getZ() + 0.5D,
                            30.0F,
                            30.0F
                    );
                }
            }
        });

        if (villager.tickCount % 10 != Math.floorMod(villager.getId(), 10)) {
            return;
        }

        VillagerDataAccess.get(villager).ifPresent(data -> {
            WorkerProfession minecraftProfession = VillagerProfessionMapper.fromVanilla(villager);

            if (minecraftProfession != WorkerProfession.UNASSIGNED
                    && minecraftProfession != data.getProfession()) {
                data.setProfession(minecraftProfession);
                data.setTaskState(TaskState.IDLE);
                data.setCurrentTaskId("profession_changed");
                WorkerRuntimeManager.get(villager.getUUID()).clearTaskTarget();
                VillagerNameManager.refreshDisplayName(villager, data);
            } else if (villager.getVillagerData().getProfession() == VillagerProfession.NONE
                    && villager.getVillagerXp() == 0
                    && data.getProfession() != WorkerProfession.UNASSIGNED) {
                data.setProfession(WorkerProfession.UNASSIGNED);
                data.setTaskState(TaskState.IDLE);
                data.setCurrentTaskId("unemployed");
                WorkerRuntimeManager.get(villager.getUUID()).clearTaskTarget();
                VillagerNameManager.refreshDisplayName(villager, data);
            }

            WorkerRuntime runtime = WorkerRuntimeManager.get(villager.getUUID());
            WorkerTaskManager.tick(level, villager, data, runtime);
        });
    }

    private VillagerWorkEvents() {
    }
}