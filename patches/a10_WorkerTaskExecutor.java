package com.gb.livingvillagers.work;

import com.gb.livingvillagers.capability.ILivingVillagerData;
import com.gb.livingvillagers.profession.WorkerProfession;
import com.gb.livingvillagers.storage.VillageStorage;
import com.gb.livingvillagers.task.runtime.WorkerRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
    private static final double TOOL_PICKUP_RADIUS = 4.5D;

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
        if (hasRequiredTool(data, needsPickaxe)) {
            syncHeldTool(villager, data, needsPickaxe);
            return true;
        }

        if (pickupNearbyTool(level, villager, data, needsPickaxe)) {
            syncHeldTool(villager, data, needsPickaxe);
            return true;
        }

        BlockPos storagePos = runtime.getStoragePos();
        Container storage = VillageStorage.getContainer(level, storagePos);
        if (storage == null) {
            storagePos = VillageStorage.findNearest(level, villager.blockPosition(), 16, 4);
            runtime.setStoragePos(storagePos);
            storage = VillageStorage.getContainer(level, storagePos);
        }

        if (storage == null) {
            data.setCurrentTaskId(needsPickaxe ? "waiting_for_pickaxe" : "waiting_for_axe");
            return false;
        }

        boolean withdrawn = needsPickaxe
                ? VillageStorage.withdrawOne(storage, data.getWorkInventory(), stack -> stack.getItem() instanceof PickaxeItem)
                : VillageStorage.withdrawOne(storage, data.getWorkInventory(), stack -> stack.getItem() instanceof AxeItem);

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
            case LUMBERJACK -> WorkerWorldSearch.findNaturalLog(level, villager.blockPosition(), searchRadius);
            case MINER -> selectMinerTarget(level, villager, data, runtime, searchRadius);
            default -> null;
        };
    }

    private static BlockPos selectMinerTarget(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            WorkerRuntime runtime,
            int searchRadius
    ) {
        // Stage 2.3 behavior: mine the nearest exposed ore that the current pickaxe can actually harvest.
        // Stone is intentionally NOT a fallback anymore.
        ItemStack pickaxe = WorkerTools.getPickaxe(data.getWorkInventory());
        return WorkerWorldSearch.findExposedOre(level, villager.blockPosition(), searchRadius, pickaxe);
    }

    public static boolean perform(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos targetPos
    ) {
        if (targetPos == null) {
            return false;
        }
        return switch (data.getProfession()) {
            case FARMER -> harvestCrop(level, villager, data, targetPos);
            case MINER -> breakResource(level, villager, data, targetPos, WorkerTools.getPickaxe(data.getWorkInventory()), true);
            case LUMBERJACK -> breakResource(level, villager, data, targetPos, WorkerTools.getAxe(data.getWorkInventory()), false);
            default -> false;
        };
    }

    private static boolean harvestCrop(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos pos
    ) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
            return false;
        }

        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null, villager, ItemStack.EMPTY));
        Item replantItem = replantItem(state);
        if (replantItem != null) {
            consumeOne(drops, replantItem);
        }

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
        if (state.isAir() || tool.isEmpty()) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, blockEntity, villager, tool);
        if (drops.isEmpty() && state.requiresCorrectToolForDrops()) {
            return false;
        }

        if (dropIntoWorld) {
            spawnDrops(level, pos, drops);
        } else {
            insertDrops(level, villager, data, drops);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.levelEvent(2001, pos, Block.getId(state));
        tool.hurtAndBreak(1, villager, ignored -> {});
        if (tool.isEmpty()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            ItemStack held = tool.copy();
            held.setCount(1);
            villager.setItemSlot(EquipmentSlot.MAINHAND, held);
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        return true;
    }

    private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemEntity itemEntity = new ItemEntity(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.55D,
                    pos.getZ() + 0.5D,
                    drop.copy()
            );
            itemEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5D) * 0.10D,
                    0.16D,
                    (level.random.nextDouble() - 0.5D) * 0.10D
            );
            level.addFreshEntity(itemEntity);
        }
    }

    public static boolean collectNearbyDrops(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            BlockPos dropPos
    ) {
        if (dropPos == null) {
            return false;
        }

        boolean collectedAny = false;
        List<ItemEntity> drops = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(dropPos).inflate(2.75D, 1.75D, 2.75D),
                entity -> entity.isAlive() && !entity.getItem().isEmpty()
        );

        drops.sort(Comparator.comparingDouble(villager::distanceToSqr));
        for (ItemEntity itemEntity : drops) {
            ItemStack stack = itemEntity.getItem();
            if (stack.getItem() instanceof PickaxeItem || stack.getItem() instanceof AxeItem) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted <= 0) {
                continue;
            }

            collectedAny = true;
            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(remainder);
            }
        }
        return collectedAny;
    }

    private static void insertDrops(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            List<ItemStack> drops
    ) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), drop.copy(), false);
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(
                        level,
                        villager.getX(),
                        villager.getY() + 0.5D,
                        villager.getZ(),
                        remainder
                );
                level.addFreshEntity(itemEntity);
            }
        }
    }

    private static boolean hasRequiredTool(ILivingVillagerData data, boolean needsPickaxe) {
        return needsPickaxe
                ? WorkerTools.hasPickaxe(data.getWorkInventory())
                : WorkerTools.hasAxe(data.getWorkInventory());
    }

    private static boolean pickupNearbyTool(
            ServerLevel level,
            Villager villager,
            ILivingVillagerData data,
            boolean needsPickaxe
    ) {
        Predicate<ItemStack> predicate = stack -> needsPickaxe
                ? stack.getItem() instanceof PickaxeItem
                : stack.getItem() instanceof AxeItem;

        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(villager.blockPosition()).inflate(TOOL_PICKUP_RADIUS),
                entity -> entity.isAlive() && predicate.test(entity.getItem())
        );

        nearbyItems.sort(Comparator.comparingDouble(villager::distanceToSqr));
        for (ItemEntity itemEntity : nearbyItems) {
            ItemStack stack = itemEntity.getItem();
            ItemStack singleTool = stack.copy();
            singleTool.setCount(1);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(data.getWorkInventory(), singleTool, false);
            if (remainder.isEmpty()) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(stack);
                }
                return true;
            }
        }
        return false;
    }

    private static void syncHeldTool(
            Villager villager,
            ILivingVillagerData data,
            boolean needsPickaxe
    ) {
        ItemStack tool = needsPickaxe
                ? WorkerTools.getPickaxe(data.getWorkInventory())
                : WorkerTools.getAxe(data.getWorkInventory());
        ItemStack current = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (tool.isEmpty()) {
            if (!current.isEmpty()) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            return;
        }

        ItemStack held = tool.copy();
        held.setCount(1);
        if (!ItemStack.isSameItemSameTags(current, held) || current.getCount() != 1) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, held);
            villager.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
    }

    private WorkerTaskExecutor() {
    }
}