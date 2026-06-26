package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.wireless.EnergyContainerWireless;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class MetaTileEntityWirelessEnergyHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IEnergyContainer> {

    private final int amperage;
    private final boolean isExport;
    private final EnergyContainerWireless energyContainer;

    public MetaTileEntityWirelessEnergyHatch(ResourceLocation metaTileEntityId, int tier, int amperage,
                                             boolean isExport) {
        super(metaTileEntityId, tier);
        this.isExport = isExport;
        this.amperage = amperage;
        energyContainer = new EnergyContainerWireless(this, isExport, GTValues.V[tier], this.amperage);

    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityWirelessEnergyHatch(this.metaTileEntityId, this.getTier(), this.amperage,
                this.isExport);
    }

    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
        }

    }

    @SideOnly(Side.CLIENT)
    private SimpleOverlayRenderer getOverlay() {
        if (amperage == 2) {
            return Textures.MULTIPART_WIRELESS_ENERGY;
        } else if (amperage == 4) {
            return Textures.MULTIPART_WIRELESS_ENERGY_4x;
        } else if (amperage == 16) {
            return Textures.MULTIPART_WIRELESS_ENERGY_16x;
        } else if (amperage == 64) {
            return Textures.MULTIPART_WIRELESS_ENERGY_64x;
        } else if (amperage == 256) {
            return Textures.MULTIPART_WIRELESS_ENERGY_256x;
        } else if (amperage == 1024) {
            return Textures.MULTIPART_WIRELESS_ENERGY_1024x;
        } else if (amperage == 4096) {
            return Textures.MULTIPART_WIRELESS_ENERGY_4096x;
        } else if (amperage == 16384) {
            return Textures.MULTIPART_WIRELESS_ENERGY_16384x;
        } else if (amperage == 65536) {
            return Textures.MULTIPART_WIRELESS_ENERGY_65536x;
        } else if (amperage == 262144) {
            return Textures.MULTIPART_WIRELESS_ENERGY_262144x;
        } else if (amperage == 1048576) {
            return Textures.MULTIPART_WIRELESS_ENERGY_1048576x;
        } else return Textures.MULTIPART_WIRELESS_ENERGY;

    }

    @Override
    public MultiblockAbility<IEnergyContainer> getAbility() {
        return isExport ? MultiblockAbility.OUTPUT_ENERGY : MultiblockAbility.INPUT_ENERGY;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(energyContainer);
    }

    @Override
    public void addInformation(ItemStack stack,
                               World player,
                               @NotNull List<String> tooltip,
                               boolean advanced) {
        String tierName = GTValues.VNF[this.getTier()];
        tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.2"));

        if (this.isExport) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", this.energyContainer.getOutputVoltage(),
                    tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_out_till",
                    this.energyContainer.getOutputAmperage()));
        } else {
            tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", this.energyContainer.getInputVoltage(),
                    tierName));
            tooltip.add(I18n.format("gregtech.universal.tooltip.amperage_in_till",
                    this.energyContainer.getInputAmperage()));
        }

        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                this.energyContainer.getEnergyCapacity()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
            tooltip.add(I18n.format("gregtech.machine.wireless_energy_hatch.tooltip.shift"));
        } else {
            tooltip.add(I18n.format("gregtech.tooltip.hold_shift"));
        }
    }
}
