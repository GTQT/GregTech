package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.IHeatable;
import gregtech.api.capability.impl.HeatContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.api.GTValues.V;
import static gregtech.api.capability.GregtechCapabilities.CAPABILITY_HEAT_CONTAINER;

public class MetaTileEntityElectricHeater extends TieredMetaTileEntity {

    protected final IHeatable heatable;

    public MetaTileEntityElectricHeater(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.heatable = HeatContainerHandler.emitterContainer(this, V[tier] * 64L, (tier + 1) * 200 + 273,
                V[tier] * 20);
        ((HeatContainerHandler) this.heatable).setSideOutputCondition(s -> s == getFrontFacing());
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityElectricHeater(metaTileEntityId, getTier());
    }

    public void update() {
        super.update();
        if (energyContainer.getEnergyStored() > V[getTier()] && heatable.getHeatStored() < heatable.getHeatCapacity()) {
            energyContainer.changeEnergy(-V[getTier()]);
            heatable.changeHeat(V[getTier()]);
        }
        if (getOffsetTimer() % 20 == 0) {
            if (heatable.getHeatStored() > 0) {
                if (heatable.getTemperature() < heatable.getMaxTemperature()) {
                    heatable.setTemperature(heatable.getTemperature() + 10 * getTier());
                }
            } else {
                if (heatable.getTemperature() > 293) {
                    heatable.setTemperature(heatable.getTemperature() - 5);
                }
            }
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.HEATER_OVERLAY.renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                true, true);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability.equals(CAPABILITY_HEAT_CONTAINER)) {
            return CAPABILITY_HEAT_CONTAINER.cast(heatable);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.electric_heater.tooltip"));
        tooltip.add(I18n.format("gregtech.universal.tooltip.heat_out_till", GTValues.V[getTier()] * 20));
        tooltip.add(I18n.format("gregtech.universal.tooltip.max_temperature", heatable.getMaxTemperature()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.heat_storage_capacity", heatable.getHeatCapacity()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }
}
