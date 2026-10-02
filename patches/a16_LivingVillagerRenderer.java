package com.gb.livingvillagers.client.render;

import com.gb.livingvillagers.registry.ModVillagerProfessions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.world.entity.npc.Villager;

public final class LivingVillagerRenderer extends VillagerRenderer {
    private final MinerAwareVillagerModel livingModel;

    public LivingVillagerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.livingModel = new MinerAwareVillagerModel(context.bakeLayer(ModelLayers.VILLAGER));
        this.model = livingModel;
        this.addLayer(new MinerIronHelmetLayer(this, context));
    }

    @Override
    public void render(
            Villager villager,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        boolean miner = villager.getVillagerData().getProfession() == ModVillagerProfessions.MINER.get();
        livingModel.setForceNoHat(miner);
        try {
            super.render(villager, entityYaw, partialTick, poseStack, buffer, packedLight);
        } finally {
            livingModel.setForceNoHat(false);
        }
    }
}
