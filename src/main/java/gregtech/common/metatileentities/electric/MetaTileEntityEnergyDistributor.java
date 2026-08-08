package gregtech.common.metatileentities.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.PipelineUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Energy Distributor
 * <p>
 * Distributes energy from the front face to the 5 other faces, or combines
 * energy from the 5 other faces to the front face. Mode is toggled by soft mallet.
 */
public class MetaTileEntityEnergyDistributor extends TieredMetaTileEntity {

    private static final long MAX_AMPERAGE = 320;

    private boolean isDistributeMode = true;

    public MetaTileEntityEnergyDistributor(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    public boolean isDistributeMode() {
        return isDistributeMode;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity te) {
        return new MetaTileEntityEnergyDistributor(metaTileEntityId, getTier());
    }

    @Override
    public void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = new EnergyContainerHandler(this,
                tierVoltage * MAX_AMPERAGE, tierVoltage, MAX_AMPERAGE, tierVoltage, MAX_AMPERAGE);
        if (isDistributeMode) {
            ((EnergyContainerHandler) energyContainer).setSideInputCondition(s -> s == frontFacing);
            ((EnergyContainerHandler) energyContainer).setSideOutputCondition(s -> s != frontFacing);
        } else {
            ((EnergyContainerHandler) energyContainer).setSideInputCondition(s -> s != frontFacing);
            ((EnergyContainerHandler) energyContainer).setSideOutputCondition(s -> s == frontFacing);
        }
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return MAX_AMPERAGE;
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("DistributeMode", isDistributeMode);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isDistributeMode = data.getBoolean("DistributeMode");
        reinitializeEnergyContainer();
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isDistributeMode);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        isDistributeMode = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.SYNC_TILE_MODE) {
            isDistributeMode = buf.readBoolean();
            scheduleRenderUpdate();
        }
    }

    @Override
    public boolean onSoftMalletClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                     CuboidRayTraceResult hitResult) {
        if (getWorld().isRemote) {
            scheduleRenderUpdate();
            return true;
        }
        isDistributeMode = !isDistributeMode;
        reinitializeEnergyContainer();
        writeCustomData(GregtechDataCodes.SYNC_TILE_MODE, buf -> buf.writeBoolean(isDistributeMode));
        notifyBlockUpdate();
        markDirty();
        return true;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.toggle_mode"));
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (isDistributeMode) {
            Textures.ENERGY_IN.renderSided(frontFacing, renderState, translation,
                    PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
            Arrays.stream(EnumFacing.values()).filter(f -> f != frontFacing).forEach(f ->
                    Textures.ENERGY_OUT.renderSided(f, renderState, translation,
                            PipelineUtil.color(pipeline, GTValues.VC[getTier()])));
        } else {
            Textures.ENERGY_OUT.renderSided(frontFacing, renderState, translation,
                    PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
            Arrays.stream(EnumFacing.values()).filter(f -> f != frontFacing).forEach(f ->
                    Textures.ENERGY_IN.renderSided(f, renderState, translation,
                            PipelineUtil.color(pipeline, GTValues.VC[getTier()])));
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip, boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.max_voltage_in",
                energyContainer.getInputVoltage(), GTValues.VNF[getTier()]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                energyContainer.getEnergyCapacity()));
        tooltip.add(I18n.format("gregtech.machine.energy_distributor.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.energy_distributor.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.energy_distributor.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.energy_distributor.tooltip.4"));
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return true;
    }
}
