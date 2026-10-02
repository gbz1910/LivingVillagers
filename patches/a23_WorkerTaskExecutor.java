package com.gb.livingvillagers.work;

import com.gb.livingvillagers.capability.ILivingVillagerData;
import com.gb.livingvillagers.profession.WorkerProfession;
import com.gb.livingvillagers.storage.VillageStorage;
import com.gb.livingvillagers.task.runtime.WorkerRuntime;
import com.gb.livingvillagers.village.VillageSettingsData;
import com.gb.livingvillagers.village.VillageSettingsSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class WorkerTaskExecutor {
    private static final double TOOL_PICKUP_RADIUS = 5.0D;

    public static boolean ensureRequiredItems(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            WorkerRuntime runtime
    ) {
        if (data.getProfession() != WorkerProfession.MINER && data.getProfession() != WorkerProfession.LUMBERJACK) {
            return true;
        }

        boolean needsPickaxe = data.getProfession() == WorkerProfession.MINER;
        VillageSettingsSnapshot settings = VillageSettingsData.get(level).snapshot();

        if (settings.autoUpgradeTools()) {
            if (needsPickaxe) upgradePickaxeFromGround(level, villager, data);
            else upgradeAxeFromGround(level, villager, data);
        }

        BlockPos storagePos = runtime.getStoragePos();
        Container storage = VillageStorage.getContainer(level, storagePos);
        if (storage == null) {
            storagePos = VillageStorage.findNearest(level, villager.blockPosition(), settings.storageSearchRadius(), 4);
            runtime.setStoragePos(storagePos);
            storage = VillageStorage.getContainer(level, storagePos);
        }

        if (settings.autoUpgradeTools() && storage != null) {
            if (needsPickaxe) upgradePickaxeFromStorage(level, villager, data, storage);
            else upgradeAxeFromStorage(level, villager, data, storage);
        }

        if (hasRequiredTool(data, needsPickaxe)) {
            syncHeldTool(villager, data, needsPickaxe);
            return true;
        }

        if (pickupNearbyTool(level, villager, data, needsPickaxe)) {
            syncHeldTool(villager, data, needsPickaxe);
            return true;
        }

        if (storage == null) {
            data.setCurrentTaskId(needsPickaxe ? "waiting_for_pickaxe" : "waiting_for_axe");
            return false;
        }

        boolean withdrawn = needsPickaxe
                ? withdrawBestPickaxe(storage, data)
                : withdrawBestAxe(storage, data);

        if (!withdrawn) {
            data.setCurrentTaskId(needsPickaxe ? "waiting_for_pickaxe" : "waiting_for_axe");
        } else {
            syncHeldTool(villager, data, needsPickaxe);
        }
        return withdrawn;
    }

    public static void syncDisplayedWorkTool(Villager villager, ILivingVillagerData data) {
        if (data.getProfession() == WorkerProfession.MINER) {
            syncHeldTool(villager, data, true);
        } else if (data.getProfession() == WorkerProfession.LUMBERJACK) {
            syncHeldTool(villager, data, false);
        }
    }

    public static BlockPos selectTarget(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            WorkerRuntime runtime,
            int searchRadius
    ) {
        return switch (data.getProfession()) {
            case FARMER -> WorkerWorldSearch.findMatureCrop(level, villager.blockPosition(), searchRadius);
            case LUMBERJACK -> selectLumberjackTarget(level, villager, data, runtime, searchRadius);
            case MINER -> selectMinerTarget(level, villager, data, runtime);
            default -> null;
        };
    }

    private static BlockPos selectLumberjackTarget(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            WorkerRuntime runtime,
            int searchRadius
    ) {
        if (WorkerTools.getAxe(data.getWorkInventory()).isEmpty()) return null;

        BlockPos root = runtime.getLumberjackTreeRoot();
        if (root != null) {
            BlockPos remaining = WorkerWorldSearch.findLogInTree(level, root);
            if (remaining != null) return remaining;
            return null;
        }

        BlockPos newRoot = WorkerWorldSearch.findTreeRoot(level, villager.blockPosition(), searchRadius);
        if (newRoot == null) return null;

        runtime.setLumberjackTreeRoot(newRoot);
        runtime.setLumberjackSapling(WorkerWorldSearch.saplingForLog(level.getBlockState(newRoot)));
        return WorkerWorldSearch.findLogInTree(level, newRoot);
    }

    private static BlockPos selectMinerTarget(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            WorkerRuntime runtime
    ) {
        ItemStack pickaxe = WorkerTools.getPickaxe(data.getWorkInventory());
        if (pickaxe.isEmpty()) return null;

        VillageSettingsSnapshot settings = VillageSettingsData.get(level).snapshot();
        Container storage = VillageStorage.getContainer(level, runtime.getStoragePos());
        if (storage == null) {
            BlockPos storagePos = VillageStorage.findNearest(level, villager.blockPosition(), settings.storageSearchRadius(), 4);
            runtime.setStoragePos(storagePos);
            storage = VillageStorage.getContainer(level, storagePos);
        }

        Container finalStorage = storage;
        int maxStock = settings.minerMaxStock();
        return WorkerWorldSearch.findExposedOre(
                level,
                villager.blockPosition(),
                settings.minerSearchRadius(),
                pickaxe,
                state -> minerStockBelowLimit(finalStorage, data, state, maxStock)
        );
    }

    private static boolean minerStockBelowLimit(
            Container storage,
            ILivingVillagerData data,
            BlockState state,
            int maxStock
    ) {
        Item expected = expectedOreDrop(state);
        if (expected == null) return true;

        int stored = VillageStorage.countMatching(storage, stack -> stack.is(expected));
        int carried = 0;
        for (int slot = 0; slot < data.getWorkInventory().getSlots(); slot++) {
            ItemStack stack = data.getWorkInventory().getStackInSlot(slot);
            if (stack.is(expected)) carried += stack.getCount();
        }
        return stored + carried < maxStock;
    }

    private static Item expectedOreDrop(BlockState state) {
        if (state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE)) return Items.COAL;
        if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)) return Items.RAW_IRON;
        if (state.is(Blocks.COPPER_ORE) || state.is(Blocks.DEEPSLATE_COPPER_ORE)) return Items.RAW_COPPER;
        if (state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)) return Items.RAW_GOLD;
        if (state.is(Blocks.REDSTONE_ORE) || state.is(Blocks.DEEPSLATE_REDSTONE_ORE)) return Items.REDSTONE;
        if (state.is(Blocks.LAPIS_ORE) || state.is(Blocks.DEEPSLATE_LAPIS_ORE)) return Items.LAPIS_LAZULI;
        if (state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return Items.DIAMOND;
        if (state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE)) return Items.EMERALD;
        return null;
    }

    public static boolean perform(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos targetPos
    ) {
        if (targetPos == null) return false;
        return switch (data.getProfession()) {
            case FARMER -> harvestCrop(level, villager, data, targetPos);
            case MINER -> breakResource(level, villager, data, targetPos, WorkerTools.getPickaxe(data.getWorkInventory()), true);
            case LUMBERJACK -> breakResource(level, villager, data, targetPos, WorkerTools.getAxe(data.getWorkInventory()), true);
            default -> false;
        };
    }

    private static boolean harvestCrop(ServerLevel level, Villager villager, ILivingVillagerData data, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) return false;

        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY));
        Item replantItem = replantItem(state);
        if (replantItem != null) consumeOne(drops, replantItem);

        insertDrops(level, villager, data, drops);
        level.setBlock(pos, crop.getStateForAge(0), 3);
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    private static Item replantItem(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (block == Blocks.CARROTS) return Items.CARROT;
        if (block == Blocks.POTATOES) return Items.POTATO;
        if (block == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        return null;
    }

    private static void consumeOne(List<ItemStack> drops, Item item) {
        for (ItemStack drop : drops) {
            if (drop.is(item) && !drop.isEmpty()) {
                drop.shrink(1);
                return;
            }
        }
    }

    private static boolean breakResource(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos pos,
            ItemStack tool,
            boolean dropIntoWorld
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || tool.isEmpty()) return false;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, villager, tool);
        if (drops.isEmpty() && state.requiresCorrectToolForDrops()) return false;

        if (dropIntoWorld) spawnDrops(level, pos, drops);
        else insertDrops(level, villager, data, drops);

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.levelEvent(2001, pos, Block.getId(state));

        boolean wasPickaxe = tool.getItem() instanceof PickaxeItem;
        boolean wasAxe = tool.getItem() instanceof AxeItem;
        tool.hurtAndBreak(1, villager, ignored -> {});

        if (WorkerTools.hasMending(tool) && !tool.isEmpty() && tool.getDamageValue() > 0 && level.random.nextFloat() < 0.35F) {
            tool.setDamageValue(Math.max(0, tool.getDamageValue() - 1));
        }

        if (tool.isEmpty()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            notifyBrokenTool(level, villager, data, wasPickaxe ? "pickaxe" : (wasAxe ? "axe" : "tool"));
        } else {
            syncHeldTool(villager, data, wasPickaxe);
        }
        return true;
    }

    private static void notifyBrokenTool(ServerLevel level, Villager villager, ILivingVillagerData data, String toolType) {
        data.setCurrentTaskId(toolType + "_broken");
        if (!VillageSettingsData.get(level).snapshot().notifyBrokenTool()) return;

        String key = "axe".equals(toolType)
                ? "message.livingvillagers.axe_broken"
                : "message.livingvillagers.pickaxe_broken";
        Component message = Component.translatable(key, villager.getDisplayName());
        level.players().stream()
                .filter(player -> player.distanceToSqr(villager) <= 64.0D * 64.0D)
                .forEach(player -> player.sendSystemMessage(message));
        level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.55D, pos.getZ() + 0.5D, drop.copy());
            itemEntity.setDeltaMovement((level.random.nextDouble() - 0.5D) * 0.10D, 0.16D, (level.random.nextDouble() - 0.5D) * 0.10D);
            level.addFreshEntity(itemEntity);
        }
    }

    public static boolean collectNearbyDrops(ServerLevel level, Villager villager, ILivingVillagerData data, BlockPos dropPos) {
        if (dropPos == null) return false;
        boolean collectedAny = false;
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(dropPos).inflate(2.75D, 1.75D, 2.75D),
                entity -> entity.isAlive() && !entity.getItem().isEmpty()
        );
        drops.sort(Comparator.comparingDouble(villager::distanceToSqr));
        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted <= 0) continue;
            collectedAny = true;
            if (remainder.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(remainder);
        }
        return collectedAny;
    }

    private static void insertDrops(ServerLevel level, Villager villager, ILivingVillagerData data, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), drop.copy(), false);
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level, villager.getX(), villager.getY() + 0.5D, villager.getZ(), remainder);
                level.addFreshEntity(itemEntity);
            }
        }
    }

    private static boolean hasRequiredTool(ILivingVillagerData data, boolean needsPickaxe) {
        return needsPickaxe ? WorkerTools.hasPickaxe(data.getWorkInventory()) : WorkerTools.hasAxe(data.getWorkInventory());
    }

    private static boolean pickupNearbyTool(ServerLevel level, Villager villager, ILivingVillagerData data, boolean needsPickaxe) {
        Predicate<ItemStack> predicate = stack -> needsPickaxe
                ? stack.getItem() instanceof PickaxeItem
                : stack.getItem() instanceof AxeItem;

        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(TOOL_PICKUP_RADIUS),
                entity -> entity.isAlive() && predicate.test(entity.getItem())
        );

        nearbyItems.sort((a, b) -> Double.compare(
                needsPickaxe ? WorkerTools.pickaxeScore(b.getItem()) : WorkerTools.axeScore(b.getItem()),
                needsPickaxe ? WorkerTools.pickaxeScore(a.getItem()) : WorkerTools.axeScore(a.getItem())
        ));

        for (ItemEntity itemEntity : nearbyItems) {
            ItemStack stack = itemEntity.getItem();
            ItemStack singleTool = stack.copy();
            singleTool.setCount(1);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), singleTool, false);
            if (remainder.isEmpty()) {
                stack.shrink(1);
                if (stack.isEmpty()) itemEntity.discard();
                else itemEntity.setItem(stack);
                return true;
            }
        }
        return false;
    }

    private static boolean withdrawBestPickaxe(Container storage, ILivingVillagerData data) {
        int bestSlot = -1;
        double bestScore = -1.0D;
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!(stack.getItem() instanceof PickaxeItem)) continue;
            double score = WorkerTools.pickaxeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return false;
        ItemStack source = storage.getItem(bestSlot);
        ItemStack one = source.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (!remainder.isEmpty()) return false;
        source.shrink(1);
        storage.setItem(bestSlot, source);
        storage.setChanged();
        return true;
    }

    private static void upgradePickaxeFromGround(ServerLevel level, Villager villager, ILivingVillagerData data) {
        ItemStack current = WorkerTools.getPickaxe(data.getWorkInventory());
        List<ItemEntity> candidates = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(TOOL_PICKUP_RADIUS),
                entity -> entity.isAlive() && entity.getItem().getItem() instanceof PickaxeItem
        );
        ItemEntity bestEntity = candidates.stream()
                .filter(entity -> WorkerTools.isBetterPickaxe(entity.getItem(), current))
                .max(Comparator.comparingDouble(entity -> WorkerTools.pickaxeScore(entity.getItem())))
                .orElse(null);
        if (bestEntity == null) return;

        int currentSlot = WorkerTools.getPickaxeSlot(data.getWorkInventory());
        if (currentSlot >= 0) {
            ItemStack old = data.getWorkInventory().extractItem(currentSlot, 1, false);
            if (!old.isEmpty()) {
                ItemEntity dropped = new ItemEntity(level, villager.getX(), villager.getY() + 0.4D, villager.getZ(), old);
                level.addFreshEntity(dropped);
            }
        }

        ItemStack candidate = bestEntity.getItem();
        ItemStack one = candidate.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (remainder.isEmpty()) {
            candidate.shrink(1);
            if (candidate.isEmpty()) bestEntity.discard();
            else bestEntity.setItem(candidate);
            syncHeldTool(villager, data, true);
        }
    }

    private static void upgradePickaxeFromStorage(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            Container storage
    ) {
        ItemStack current = WorkerTools.getPickaxe(data.getWorkInventory());
        int bestSlot = -1;
        double bestScore = WorkerTools.pickaxeScore(current);
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!WorkerTools.isBetterPickaxe(stack, current)) continue;
            double score = WorkerTools.pickaxeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return;

        int currentSlot = WorkerTools.getPickaxeSlot(data.getWorkInventory());
        ItemStack old = currentSlot >= 0 ? data.getWorkInventory().getStackInSlot(currentSlot).copy() : ItemStack.EMPTY;
        if (!old.isEmpty()) {
            ItemStack leftover = VillageStorage.insertIntoContainer(storage, old);
            if (!leftover.isEmpty()) return;
            data.getWorkInventory().setStackInSlot(currentSlot, ItemStack.EMPTY);
        }

        ItemStack candidate = storage.getItem(bestSlot);
        ItemStack one = candidate.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (remainder.isEmpty()) {
            candidate.shrink(1);
            storage.setItem(bestSlot, candidate);
            storage.setChanged();
            syncHeldTool(villager, data, true);
        }
    }

    private static boolean withdrawBestAxe(Container storage, ILivingVillagerData data) {
        int bestSlot = -1;
        double bestScore = -1.0D;
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!(stack.getItem() instanceof AxeItem)) continue;
            double score = WorkerTools.axeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return false;

        ItemStack source = storage.getItem(bestSlot);
        ItemStack one = source.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (!remainder.isEmpty()) return false;

        source.shrink(1);
        storage.setItem(bestSlot, source);
        storage.setChanged();
        return true;
    }

    private static void upgradeAxeFromGround(ServerLevel level, Villager villager, ILivingVillagerData data) {
        ItemStack current = WorkerTools.getAxe(data.getWorkInventory());
        List<ItemEntity> candidates = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(TOOL_PICKUP_RADIUS),
                entity -> entity.isAlive() && entity.getItem().getItem() instanceof AxeItem
        );
        ItemEntity bestEntity = candidates.stream()
                .filter(entity -> WorkerTools.isBetterAxe(entity.getItem(), current))
                .max(Comparator.comparingDouble(entity -> WorkerTools.axeScore(entity.getItem())))
                .orElse(null);
        if (bestEntity == null) return;

        int currentSlot = WorkerTools.getAxeSlot(data.getWorkInventory());
        if (currentSlot >= 0) {
            ItemStack old = data.getWorkInventory().extractItem(currentSlot, 1, false);
            if (!old.isEmpty()) {
                ItemEntity dropped = new ItemEntity(level, villager.getX(), villager.getY() + 0.4D, villager.getZ(), old);
                level.addFreshEntity(dropped);
            }
        }

        ItemStack candidate = bestEntity.getItem();
        ItemStack one = candidate.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (remainder.isEmpty()) {
            candidate.shrink(1);
            if (candidate.isEmpty()) bestEntity.discard();
            else bestEntity.setItem(candidate);
            syncHeldTool(villager, data, false);
        }
    }

    private static void upgradeAxeFromStorage(ServerLevel level, Villager villager, ILivingVillagerData data, Container storage) {
        ItemStack current = WorkerTools.getAxe(data.getWorkInventory());
        int bestSlot = -1;
        double bestScore = WorkerTools.axeScore(current);
        for (int slot = 0; slot < storage.getContainerSize(); slot++) {
            ItemStack stack = storage.getItem(slot);
            if (!WorkerTools.isBetterAxe(stack, current)) continue;
            double score = WorkerTools.axeScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return;

        int currentSlot = WorkerTools.getAxeSlot(data.getWorkInventory());
        ItemStack old = currentSlot >= 0 ? data.getWorkInventory().getStackInSlot(currentSlot).copy() : ItemStack.EMPTY;
        if (!old.isEmpty()) {
            ItemStack leftover = VillageStorage.insertIntoContainer(storage, old);
            if (!leftover.isEmpty()) return;
            data.getWorkInventory().setStackInSlot(currentSlot, ItemStack.EMPTY);
        }

        ItemStack candidate = storage.getItem(bestSlot);
        ItemStack one = candidate.copy();
        one.setCount(1);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), one, false);
        if (remainder.isEmpty()) {
            candidate.shrink(1);
            storage.setItem(bestSlot, candidate);
            storage.setChanged();
            syncHeldTool(villager, data, false);
        }
    }

    public static BlockPos lumberjackPillarBase(WorkerRuntime runtime) {
        return runtime.getLumberjackScaffoldBase();
    }

    public static BlockPos prepareLumberjackPillarBase(WorkerRuntime runtime, BlockPos target) {
        BlockPos existing = runtime.getLumberjackScaffoldBase();
        if (existing != null) return existing;

        BlockPos root = runtime.getLumberjackTreeRoot();
        if (root == null || target == null) return null;

        // Build directly under the high trunk we are trying to reach, but at ground/tree-root height.
        BlockPos base = new BlockPos(target.getX(), root.getY(), target.getZ());
        runtime.setLumberjackScaffoldBase(base);
        return base;
    }

    public static boolean isAtLumberjackPillarBase(Villager villager, WorkerRuntime runtime) {
        BlockPos base = lumberjackPillarBase(runtime);
        if (base == null) return false;
        double dx = villager.getX() - (base.getX() + 0.5D);
        double dz = villager.getZ() - (base.getZ() + 0.5D);
        return dx * dx + dz * dz <= 0.35D;
    }

    public static boolean stabilizeLumberjackOnPillar(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        if (!runtime.hasScaffoldBlocks()) {
            if (villager.isNoGravity()) villager.setNoGravity(false);
            return false;
        }

        BlockPos base = lumberjackPillarBase(runtime);
        if (base == null) return false;

        int topY = runtime.getScaffoldTopY();
        if (topY == Integer.MIN_VALUE) return false;

        double expectedX = base.getX() + 0.5D;
        double expectedY = topY + 1.0D;
        double expectedZ = base.getZ() + 0.5D;

        villager.getNavigation().stop();
        villager.setNoGravity(true);
        villager.fallDistance = 0.0F;
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);

        double dx = villager.getX() - expectedX;
        double dy = villager.getY() - expectedY;
        double dz = villager.getZ() - expectedZ;
        if (dx * dx + dz * dz > 0.04D || Math.abs(dy) > 0.20D) {
            villager.setPos(expectedX, expectedY, expectedZ);
        }
        return true;
    }

    public static boolean canReachLumberjackTarget(Villager villager, WorkerRuntime runtime, BlockPos target) {
        if (target == null) return false;
        double dx = villager.getX() - (target.getX() + 0.5D);
        double dz = villager.getZ() - (target.getZ() + 0.5D);
        double horizontal = dx * dx + dz * dz;
        double vertical = (target.getY() + 0.5D) - villager.getEyeY();
        double maxHorizontal = runtime.getScaffoldBlocks().isEmpty() ? 7.0D : 12.25D;
        return horizontal <= maxHorizontal && vertical <= 2.5D && vertical >= -3.0D;
    }

    public static boolean raiseLumberjackWithDirt(
            ServerLevel level,
            Villager villager,
            WorkerRuntime runtime,
            BlockPos target,
            int maxHeight
    ) {
        if (target == null) return false;
        if (runtime.getScaffoldBlocks().size() >= Math.max(1, maxHeight)) return false;

        BlockPos base = lumberjackPillarBase(runtime);
        if (base == null) return false;

        // If a pillar already exists, forcibly restore the villager to the top before extending it.
        if (runtime.hasScaffoldBlocks()) {
            stabilizeLumberjackOnPillar(level, villager, runtime);
        } else if (!isAtLumberjackPillarBase(villager, runtime)) {
            return false;
        }

        if (target.getY() - villager.getEyeY() <= 2.5D) return false;

        int nextY = runtime.hasScaffoldBlocks()
                ? runtime.getScaffoldTopY() + 1
                : villager.blockPosition().getY();
        BlockPos next = new BlockPos(base.getX(), nextY, base.getZ());
        BlockState nextState = level.getBlockState(next);
        if (!nextState.isAir() && !nextState.canBeReplaced()) return false;

        level.setBlock(next, Blocks.DIRT.defaultBlockState(), 3);
        runtime.addScaffoldBlock(next);

        villager.getNavigation().stop();
        villager.setNoGravity(true);
        villager.fallDistance = 0.0F;
        villager.setPos(base.getX() + 0.5D, next.getY() + 1.0D, base.getZ() + 0.5D);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
        return true;
    }

    public static void finishLumberjackTree(
            ServerLevel level,
            Villager villager,
            WorkerRuntime runtime,
            boolean replant
    ) {
        BlockPos root = runtime.getLumberjackTreeRoot();
        Block sapling = runtime.getLumberjackSapling();

        moveLumberjackOffScaffold(level, villager, runtime);
        cleanupLumberjackScaffold(level, runtime);

        if (replant && root != null && sapling != null) {
            if (sapling == Blocks.DARK_OAK_SAPLING) {
                plantIfPossible(level, root, sapling);
                plantIfPossible(level, root.east(), sapling);
                plantIfPossible(level, root.south(), sapling);
                plantIfPossible(level, root.east().south(), sapling);
            } else {
                plantIfPossible(level, root, sapling);
            }
        }

        runtime.clearLumberjackTree();
    }

    private static void moveLumberjackOffScaffold(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        BlockPos base = lumberjackPillarBase(runtime);
        villager.getNavigation().stop();
        villager.setNoGravity(false);
        villager.fallDistance = 0.0F;

        if (base == null || runtime.getScaffoldBlocks().isEmpty()) {
            villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
            return;
        }

        int[][] offsets = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1},{2,0},{-2,0},{0,2},{0,-2}};
        for (int[] off : offsets) {
            BlockPos feet = new BlockPos(base.getX() + off[0], base.getY(), base.getZ() + off[1]);
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(feet.above());
            BlockState floorState = level.getBlockState(feet.below());
            if ((feetState.isAir() || feetState.canBeReplaced())
                    && (headState.isAir() || headState.canBeReplaced())
                    && !floorState.isAir()) {
                villager.setPos(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
                return;
            }
        }

        // Last-resort recovery: put him next to the original tree base instead of leaving him floating/spinning.
        BlockPos fallback = base.east();
        villager.setPos(fallback.getX() + 0.5D, base.getY(), fallback.getZ() + 0.5D);
        villager.setDeltaMovement(0.0D, 0.0D, 0.0D);
    }

    private static void plantIfPossible(ServerLevel level, BlockPos pos, Block sapling) {
        BlockState current = level.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) return;
        BlockState planted = sapling.defaultBlockState();
        if (planted.canSurvive(level, pos)) level.setBlock(pos, planted, 3);
    }

    public static void finishCurrentLumberjackPillar(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        moveLumberjackOffScaffold(level, villager, runtime);
        cleanupLumberjackScaffold(level, runtime);
    }

    public static void cleanupLumberjackScaffold(ServerLevel level, Villager villager, WorkerRuntime runtime) {
        finishCurrentLumberjackPillar(level, villager, runtime);
    }

    public static void cleanupLumberjackScaffold(ServerLevel level, WorkerRuntime runtime) {
        List<BlockPos> placed = new ArrayList<>(runtime.getScaffoldBlocks());
        placed.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        for (BlockPos pos : placed) {
            if (level.getBlockState(pos).is(Blocks.DIRT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        runtime.clearScaffoldBlocks();
        runtime.setLumberjackScaffoldBase(null);
    }

    public static boolean collectLumberjackTreeDrops(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos treeRoot
    ) {
        if (treeRoot == null) return false;
        boolean collectedAny = false;
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(treeRoot).inflate(7.0D, 10.0D, 7.0D),
                entity -> entity.isAlive() && !entity.getItem().isEmpty()
        );
        drops.sort(Comparator.comparingDouble(villager::distanceToSqr));
        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted <= 0) continue;
            collectedAny = true;
            if (remainder.isEmpty()) itemEntity.discard();
            else itemEntity.setItem(remainder);
        }
        return collectedAny;
    }

    private static void syncHeldTool(Villager villager, ILivingVillagerData data, boolean needsPickaxe) {
        ItemStack tool = needsPickaxe ? WorkerTools.getPickaxe(data.getWorkInventory()) : WorkerTools.getAxe(data.getWorkInventory());
        ItemStack current = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (tool.isEmpty()) {
            if (!current.isEmpty()) villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            return;
        }

        ItemStack held = tool.copy();
        held.setCount(1);
        boolean sameVisual = ItemStack.isSameItemSameTags(current, held)
                && current.getDamageValue() == held.getDamageValue()
                && current.getCount() == 1;
        if (!sameVisual) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, held);
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    private WorkerTaskExecutor() {
    }
}