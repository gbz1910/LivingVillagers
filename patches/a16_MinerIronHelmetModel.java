package com.gb.livingvillagers.client.render;

import com.gb.livingvillagers.LivingVillagers;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;

public final class MinerIronHelmetModel extends EntityModel<Villager> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(LivingVillagers.MOD_ID, "miner_iron_helmet"),
            "main"
    );

    private final ModelPart helmet;

    public MinerIronHelmetModel(ModelPart root) {
        this.helmet = root.getChild("helmet");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild(
                "helmet",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-6.0F, -11.0F, -6.0F, 12.0F, 2.0F, 12.0F, new CubeDeformation(0.15F))
                        .texOffs(0, 16)
                        .addBox(-5.0F, -10.0F, -5.0F, 10.0F, 6.0F, 10.0F, new CubeDeformation(0.25F)),
                PartPose.ZERO
        );

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(Villager entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack,
                               com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
                               int packedLight,
                               int packedOverlay,
                               float red,
                               float green,
                               float blue,
                               float alpha) {
        helmet.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
