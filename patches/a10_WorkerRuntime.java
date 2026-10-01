package com.gb.livingvillagers.task.runtime;

import net.minecraft.core.BlockPos;

public final class WorkerRuntime {
    private BlockPos targetPos;
    private BlockPos storagePos;
    private int stuckTicks;
    private int idleCooldown;
    private int actionCooldown;
    private int completedWorkActions;
    private boolean collectingDrops;

    public BlockPos getTargetPos() {
        return targetPos;
    }

    public void setTargetPos(BlockPos targetPos) {
        this.targetPos = targetPos;
        this.stuckTicks = 0;
    }

    public BlockPos getStoragePos() {
        return storagePos;
    }

    public void setStoragePos(BlockPos storagePos) {
        this.storagePos = storagePos;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }

    public void addStuckTicks(int amount) {
        stuckTicks += Math.max(0, amount);
    }

    public void resetStuckTicks() {
        stuckTicks = 0;
    }

    public int getIdleCooldown() {
        return idleCooldown;
    }

    public void setIdleCooldown(int idleCooldown) {
        this.idleCooldown = Math.max(0, idleCooldown);
    }

    public void tickCooldown(int amount) {
        idleCooldown = Math.max(0, idleCooldown - Math.max(0, amount));
        actionCooldown = Math.max(0, actionCooldown - Math.max(0, amount));
    }

    public int getActionCooldown() {
        return actionCooldown;
    }

    public void setActionCooldown(int actionCooldown) {
        this.actionCooldown = Math.max(0, actionCooldown);
    }

    public int getCompletedWorkActions() {
        return completedWorkActions;
    }

    public void incrementCompletedWorkActions() {
        completedWorkActions++;
    }

    public void resetCompletedWorkActions() {
        completedWorkActions = 0;
    }

    public boolean isCollectingDrops() {
        return collectingDrops;
    }

    public void setCollectingDrops(boolean collectingDrops) {
        this.collectingDrops = collectingDrops;
    }

    public void clearTaskTarget() {
        targetPos = null;
        stuckTicks = 0;
        collectingDrops = false;
    }
}