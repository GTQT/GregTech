package gregtech.client.renderer.handler;

import gregtech.common.entities.GTMiningLaserEntity;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.Nullable;

public class MiningLaserRenderer extends Render<GTMiningLaserEntity> {

    public MiningLaserRenderer(RenderManager renderManager) {
        super(renderManager);
    }

    @Override
    public void doRender(GTMiningLaserEntity entity, double x, double y, double z, float entityYaw, float partialTicks) {}

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(GTMiningLaserEntity entity) {
        return null;
    }
}
