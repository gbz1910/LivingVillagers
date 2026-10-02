package com.gb.livingvillagers.client.render;

import com.gb.livingvillagers.LivingVillagers;
import com.gb.livingvillagers.registry.ModVillagerProfessions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public final class MinerIronHelmetLayer extends RenderLayer<Villager, VillagerModel<Villager>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            LivingVillagers.MOD_ID,
            "textures/entity/villager/miner_iron_helmet.png"
    );

    private final MinerIronHelmetModel helmetModel;

    public MinerIronHelmetLayer(RenderLayerParent<Villager, VillagerModel<Villager>> parent,
                                EntityRendererProvider.Context context) {
        super(parent);
        this.helmetModel = new MinerIronHelmetModel(context.bakeLayer(MinerIronHelmetModel.LAYER_LOCATION));
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource buffer,
                       int packedLight,
                       Villager villager,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTick,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        if (villager.getVillagerData().getProfession() != ModVillagerProfessions.MINER.get()) {
            return;
        }

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);
        helmetModel.renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
        poseStack.popPose();
    }
}
