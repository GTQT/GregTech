package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.electric.MetaTileEntityFusionReactor;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.apiimpl.elements.ElementProgress;
import org.jetbrains.annotations.NotNull;

public class FusionReactorProvider extends ElectricContainerInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":fusion";
    }
    @Override
    protected void addProbeInfo(@NotNull IEnergyContainer capability, @NotNull IProbeInfo probeInfo,
                                EntityPlayer player, @NotNull TileEntity tileEntity, @NotNull IProbeHitData data) {
        if (tileEntity instanceof IGregTechTileEntity gregTechTileEntity) {
            MetaTileEntity metaTileEntity = gregTechTileEntity.getMetaTileEntity();

            if (metaTileEntity instanceof MetaTileEntityFusionReactor fusionReactor) {

                if (fusionReactor.isStructureFormed()) {
                    long heat = fusionReactor.getHeat();
                    long capacity = capability.getEnergyCapacity();

                    probeInfo.text(TextFormatting.RED + "{*gregtech.top.fusion_reactor.heat*}");
                    probeInfo.progress(heat, capacity, probeInfo.defaultProgressStyle()
                            .numberFormat(player.isSneaking() || heat < 10000 ? NumberFormat.FULL : NumberFormat.COMPACT)
                            .suffix(" / " + (player.isSneaking() || capacity < 10000 ? capacity :
                                    ElementProgress.format(capacity, NumberFormat.COMPACT, "")))
                            .filledColor(0xFFEEE600)
                            .alternateFilledColor(0xFFEEE600)
                            .borderColor(0xFF555555));
                }
            }
        }
    }
}
