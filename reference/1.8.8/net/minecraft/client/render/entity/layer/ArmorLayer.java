package net.minecraft.client.render.entity.layer;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.entity.HumanoidModel;

public class ArmorLayer extends AbstractArmorLayer<HumanoidModel> {
    public ArmorLayer(LivingEntityRenderer<?> livingEntityRenderer) {
        super(livingEntityRenderer);
    }

    @Override
    protected void hideAll() {
        this.innerModel = new HumanoidModel(0.5F);
        this.outerModel = new HumanoidModel(1.0F);
    }

    protected void setVisible(HumanoidModel humanoidModel, int i) {
        this.setInvisible(humanoidModel);
        switch (i) {
            case 1:
                humanoidModel.rightLeg.visible = true;
                humanoidModel.leftLeg.visible = true;
                break;
            case 2:
                humanoidModel.body.visible = true;
                humanoidModel.rightLeg.visible = true;
                humanoidModel.leftLeg.visible = true;
                break;
            case 3:
                humanoidModel.body.visible = true;
                humanoidModel.rightArm.visible = true;
                humanoidModel.leftArm.visible = true;
                break;
            case 4:
                humanoidModel.head.visible = true;
                humanoidModel.hat.visible = true;
        }
    }

    protected void setInvisible(HumanoidModel model) {
        model.setVisible(false);
    }
}
