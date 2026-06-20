package gregtech.common.metatileentities.heat;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IHeat;
import gregtech.api.capability.IHeatMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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

import java.util.List;

public class MetaTileEntityHeatSensor extends MetaTileEntityMultiblockPart implements IMultiblockAbilityPart<IHeat> {

    // 反转
    boolean reverse = false;

    public MetaTileEntityHeatSensor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 0);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityHeatSensor(metaTileEntityId);
    }

    @Override
    public void update() {
        super.update();
        if (getController() != null && getController() instanceof IHeatMachine heatMachine) {
            int temp = heatMachine.getTemperature();
            // 温度每超过373k 200k则增加一级红石信号（最高15）
            int tier = (temp - 373) / 200;
            setOutputRedstoneSignal(getFrontFacing(), reverse ? tier : 15 - tier);
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            getOverlay().renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @NotNull
    private SimpleOverlayRenderer getOverlay() {
        return Textures.ENERGY_OUT;
    }

    @Override
    public MultiblockAbility<IHeat> getAbility() {
        return MultiblockAbility.HEAT_SENSOR;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setReverse(!reverse);
        return true;
    }

    @SuppressWarnings("DuplicatedCode")
    public void setReverse(boolean inverted) {
        reverse = inverted;
        if (!getWorld().isRemote) {

            writeCustomData(GregtechDataCodes.TOGGLE_REVERSE,
                    packetBuffer -> packetBuffer.writeBoolean(reverse));
            notifyBlockUpdate();
            markDirty();
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.TOGGLE_REVERSE) {
            reverse = buf.readBoolean();
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.heat_sensor.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.heat_sensor.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.heat_sensor.tooltip.3"));
    }
}
