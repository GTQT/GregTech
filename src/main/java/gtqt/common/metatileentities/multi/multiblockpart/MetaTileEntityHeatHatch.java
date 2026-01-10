package gtqt.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IHeatable;
import gregtech.api.capability.impl.HeatContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.client.utils.PipelineUtil;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;

import static gregtech.api.capability.GregtechCapabilities.CAPABILITY_HEAT_CONTAINER;

public class MetaTileEntityHeatHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IHeatable> {

    protected final IHeatable heatable;
    boolean isExportHatch;

    public MetaTileEntityHeatHatch(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier);
        this.isExportHatch = isExportHatch;

        if (isExportHatch) {
            this.heatable = HeatContainerHandler.emitterContainer(this, GTValues.V[tier] * 64L, (tier+1) * 200 + 273,
                    GTValues.V[tier]);
            ((HeatContainerHandler) this.heatable).setSideOutputCondition(s -> s == getFrontFacing());
        } else {
            this.heatable = HeatContainerHandler.receiverContainer(this, GTValues.V[tier] * 64L, (tier+1) * 200 + 273,
                    GTValues.V[tier]);
        }
    }

    public void update() {
        super.update();
        if (getWorld().isRemote) {
            return;
        }

        if (isExportHatch) {
            heatable.setTemperature(400);
            heatable.changeHeat(114);

        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation,
                    PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
        }
    }

    @NotNull
    private SimpleOverlayRenderer getOverlay() {
        if (isExportHatch) {
            return Textures.HEAT_OUT;
        } else {
            return Textures.HEAT_IN;
        }
    }

    @Override
    public MultiblockAbility<IHeatable> getAbility() {
        return isExportHatch ? MultiblockAbility.OUTPUT_HEAT : MultiblockAbility.INPUT_HEAT;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(heatable);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHeatHatch(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability.equals(CAPABILITY_HEAT_CONTAINER)) {
            return CAPABILITY_HEAT_CONTAINER.cast(heatable);
        }
        return super.getCapability(capability, side);
    }
}
