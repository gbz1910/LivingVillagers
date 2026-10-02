package com.gb.livingvillagers.client.render;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.Villager;

public final class MinerAwareVillagerModel extends VillagerModel<Villager> {
    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart hatRim;
    private boolean forceNoHat;

    public MinerAwareVillagerModel(ModelPart root) {
        super(root);
        this.head = root.getChild("head");
        this.hat = this.head.getChild("hat");
        this.hatRim = this.hat.getChild("hat_rim");
    }

    public void setForceNoHat(boolean forceNoHat) {
        this.forceNoHat = forceNoHat;
        applyHatState(true);
    }

    private void applyHatState(boolean requestedVisible) {
        this.head.visible = true;

        if (forceNoHat) {
            this.hat.visible = false;
            this.hatRim.visible = false;
        } else {
            this.hat.visible = requestedVisible;
            this.hatRim.visible = requestedVisible;
        }
    }

    @Override
    public void hatVisible(boolean visible) {
        applyHatState(visible);
    }
}
