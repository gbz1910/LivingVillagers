package com.gb.livingvillagers.task.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WorkerRuntime {
    private BlockPos targetPos;
    private BlockPos storagePos;
    private int stuckTicks;
    private int idleCooldown;
    private int actionCooldown;
    private int completedWorkActions;
    private boolean collectingDrops;
    private boolean patrolling;
    private int patrolStep;

    private BlockPos lumberjackTreeRoot;
    private BlockPos lumberjackScaffoldBase;
    private Block lumberjackSapling;
    private final List<BlockPos> scaffoldBlocks = new ArrayList<>();

    public BlockPos getTargetPos() { return targetPos; }

    public void setTargetPos(BlockPos targetPos) {
        this.targetPos = targetPos;
        this.stuckTicks = 0;
    }

    public BlockPos getStoragePos() { return storagePos; }
    public void setStoragePos(BlockPos storagePos) { this.storagePos = storagePos; }

    public int getStuckTicks() { return stuckTicks; }
    public void addStuckTicks(int amount) { stuckTicks += Math.max(0, amount); }
    public void resetStuckTicks() { stuckTicks = 0; }

    public int getIdleCooldown() { return idleCooldown; }
    public void setIdleCooldown(int idleCooldown) { this.idleCooldown = Math.max(0, idleCooldown); }

    public void tickCooldown(int amount) {
        idleCooldown = Math.max(0, idleCooldown - Math.max(0, amount));
        actionCooldown = Math.max(0, actionCooldown - Math.max(0, amount));
    }

    public int getActionCooldown() { return actionCooldown; }
    public void setActionCooldown(int actionCooldown) { this.actionCooldown = Math.max(0, actionCooldown); }

    public int getCompletedWorkActions() { return completedWorkActions; }
    public void incrementCompletedWorkActions() { completedWorkActions++; }
    public void resetCompletedWorkActions() { completedWorkActions = 0; }

    public boolean isCollectingDrops() { return collectingDrops; }
    public void setCollectingDrops(boolean collectingDrops) { this.collectingDrops = collectingDrops; }

    public boolean isPatrolling() { return patrolling; }
    public void setPatrolling(boolean patrolling) { this.patrolling = patrolling; }

    public int nextPatrolStep() {
        int current = patrolStep;
        patrolStep = (patrolStep + 1) % 8;
        return current;
    }

    public BlockPos getLumberjackTreeRoot() { return lumberjackTreeRoot; }
    public void setLumberjackTreeRoot(BlockPos lumberjackTreeRoot) { this.lumberjackTreeRoot = lumberjackTreeRoot; }

    public BlockPos getLumberjackScaffoldBase() { return lumberjackScaffoldBase; }
    public void setLumberjackScaffoldBase(BlockPos lumberjackScaffoldBase) { this.lumberjackScaffoldBase = lumberjackScaffoldBase; }

    public Block getLumberjackSapling() { return lumberjackSapling; }
    public void setLumberjackSapling(Block lumberjackSapling) { this.lumberjackSapling = lumberjackSapling; }

    public void addScaffoldBlock(BlockPos pos) {
        if (!scaffoldBlocks.contains(pos)) scaffoldBlocks.add(pos.immutable());
    }

    public List<BlockPos> getScaffoldBlocks() {
        return Collections.unmodifiableList(scaffoldBlocks);
    }

    public boolean hasScaffoldBlocks() { return !scaffoldBlocks.isEmpty(); }

    public int getScaffoldTopY() {
        int top = Integer.MIN_VALUE;
        for (BlockPos pos : scaffoldBlocks) top = Math.max(top, pos.getY());
        return top;
    }

    public void clearScaffoldBlocks() { scaffoldBlocks.clear(); }

    public void clearLumberjackTree() {
        lumberjackTreeRoot = null;
        lumberjackScaffoldBase = null;
        lumberjackSapling = null;
    }

    public void clearTaskTarget() {
        targetPos = null;
        stuckTicks = 0;
        collectingDrops = false;
        patrolling = false;
    }
}