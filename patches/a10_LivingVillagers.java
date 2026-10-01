package com.gb.livingvillagers;

import com.gb.livingvillagers.capability.ModCapabilities;
import com.gb.livingvillagers.command.LivingVillagersCommands;
import com.gb.livingvillagers.config.LivingVillagersConfig;
import com.gb.livingvillagers.event.VillagerCapabilityEvents;
import com.gb.livingvillagers.event.VillagerLifecycleEvents;
import com.gb.livingvillagers.event.MinerTradeEvents;
import com.gb.livingvillagers.event.VillagerWorkEvents;
import com.gb.livingvillagers.registry.ModBlocks;
import com.gb.livingvillagers.registry.ModCreativeTabs;
import com.gb.livingvillagers.registry.ModItems;
import com.gb.livingvillagers.registry.ModVillagerProfessions;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(LivingVillagers.MOD_ID)
public final class LivingVillagers {
    public static final String MOD_ID = "livingvillagers";

    public LivingVillagers() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ModCapabilities::registerCapabilities);
        modBus.addListener(ModCreativeTabs::addToTabs);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModVillagerProfessions.register(modBus);

        ModLoadingContext.get().registerConfig(
                ModConfig.Type.COMMON,
                LivingVillagersConfig.SPEC,
                "livingvillagers-common.toml"
        );

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addGenericListener(Entity.class, VillagerCapabilityEvents::attachCapabilities);
        forgeBus.addListener(VillagerLifecycleEvents::onEntityJoinLevel);
        forgeBus.addListener(VillagerWorkEvents::onLivingTick);
        forgeBus.addListener(MinerTradeEvents::onVillagerTrades);
        forgeBus.addListener(LivingVillagersCommands::onRegisterCommands);
    }
}