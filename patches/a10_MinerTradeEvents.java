package com.gb.livingvillagers.event;

import com.gb.livingvillagers.registry.ModVillagerProfessions;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.event.village.VillagerTradesEvent;

public final class MinerTradeEvents {
    private static final float PRICE_MULTIPLIER = 0.05F;

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != ModVillagerProfessions.MINER.get()) {
            return;
        }

        // Level 1 - novice
        event.getTrades().get(1).add(buyFromPlayer(Items.COAL, 15, 1, 16, 2));
        event.getTrades().get(1).add(sellToPlayer(Items.TORCH, 16, 1, 16, 1));

        // Level 2 - apprentice
        event.getTrades().get(2).add(buyFromPlayer(Items.RAW_COPPER, 10, 1, 12, 10));
        event.getTrades().get(2).add(sellToPlayer(Items.STONE_PICKAXE, 1, 2, 8, 5));

        // Level 3 - journeyman
        event.getTrades().get(3).add(buyFromPlayer(Items.RAW_IRON, 5, 1, 12, 20));
        event.getTrades().get(3).add(sellToPlayer(Items.IRON_PICKAXE, 1, 7, 5, 10));

        // Level 4 - expert
        event.getTrades().get(4).add(buyFromPlayer(Items.RAW_GOLD, 4, 1, 8, 30));
        event.getTrades().get(4).add(sellToPlayer(Items.RAIL, 16, 4, 8, 15));

        // Level 5 - master
        event.getTrades().get(5).add(buyFromPlayer(Items.DIAMOND, 1, 3, 4, 30));
        event.getTrades().get(5).add(sellToPlayer(Items.DIAMOND_PICKAXE, 1, 18, 2, 30));
    }

    private static VillagerTrades.ItemListing buyFromPlayer(
            Item item,
            int itemCount,
            int emeraldCount,
            int maxUses,
            int xp
    ) {
        return (trader, random) -> new MerchantOffer(
                new ItemStack(item, itemCount),
                new ItemStack(Items.EMERALD, emeraldCount),
                maxUses,
                xp,
                PRICE_MULTIPLIER
        );
    }

    private static VillagerTrades.ItemListing sellToPlayer(
            Item item,
            int itemCount,
            int emeraldCost,
            int maxUses,
            int xp
    ) {
        return (trader, random) -> new MerchantOffer(
                new ItemStack(Items.EMERALD, emeraldCost),
                new ItemStack(item, itemCount),
                maxUses,
                xp,
                PRICE_MULTIPLIER
        );
    }

    private MinerTradeEvents() {
    }
}