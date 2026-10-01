package com.gb.livingvillagers.client.render;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.Villager;

public final class MinerAwareVillagerModel extends VillagerModel<Villager> {
    private boolean forceNoHat;

    public MinerAwareVillagerModel(ModelPart root) {
        super(root);
    }

    public void setForceNoHat(boolean forceNoHat) {
        this.forceNoHat = forceNoHat;
        super.hatVisible(!forceNoHat);
    }

    @Override
    public void hatVisible(boolean visible) {
        super.hatVisible(forceNoHat ? false : visible);
    }
}
