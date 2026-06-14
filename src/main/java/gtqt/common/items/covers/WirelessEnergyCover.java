package gtqt.common.items.covers;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverableView;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.wireless.TransferContext;
import gregtech.api.wireless.TransferResult;
import gregtech.api.wireless.WirelessEnergyService;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleSidedCubeRenderer;
import gregtech.common.wireless.WirelessEnergyServiceImpl;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static gregtech.api.GTValues.V;

public class WirelessEnergyCover extends CoverBase implements ITickable {

    private final long EUt;
    private final boolean isTransparent;

    public WirelessEnergyCover(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                           @NotNull EnumFacing attachedSide, int tier, boolean isTransparent) {
        super(definition, coverableView, attachedSide);
        this.EUt = V[tier];
        this.isTransparent = isTransparent; //true 从无线电网拉，按照每tick EUt 插入机器
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return getAttachedSide() == EnumFacing.UP &&
                coverable.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null) != null;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.WIRELESS_ENERGY.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }
    public static @Nullable MetaTileEntity getMetaTileEntity(@Nullable TileEntity te) {
        return te instanceof IGregTechTileEntity gtte ? gtte.getMetaTileEntity() : null;
    }
    @Override
    public void update() {
        CoverableView coverable = getCoverableView();
        MetaTileEntity metaTileEntity = getMetaTileEntity(getTileEntityHere());
        if (metaTileEntity == null) return;
    
        UUID ownerId = metaTileEntity.getOwnerGT();
        if (ownerId == null) return;
    
        WirelessEnergyService service = WirelessEnergyServiceImpl.getService();
        if (service == null) return;
    
        IEnergyContainer energyContainer = coverable.getCapability(
                GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, getAttachedSide());
        if (energyContainer == null) return;
    
        if (isTransparent) {
            // Pull from wireless network → inject into machine
            long energyNeeded = energyContainer.getEnergyCanBeInserted();
            if (energyNeeded > 0) {
                long toExtract = Math.min(energyNeeded, EUt);
                TransferResult result = service.extractUpTo(ownerId, toExtract, TransferContext.HATCH);
                if (result.isSuccess() && result.getAmountLong() > 0) {
                    energyContainer.addEnergy(result.getAmountLong());
                }
            }
        } else {
            // Extract from machine → push to wireless network
            long stored = energyContainer.getEnergyStored();
            if (stored > 0) {
                long toPush = Math.min(stored, EUt);
                TransferResult result = service.insert(ownerId, toPush, TransferContext.HATCH);
                if (result.isSuccess() && result.getAmountLong() > 0) {
                    energyContainer.removeEnergy(result.getAmountLong());
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected @NotNull TextureAtlasSprite getPlateSprite() {
        return Textures.VOLTAGE_CASINGS[GTUtility.getTierByVoltage(this.EUt)]
                .getSpriteOnSide(SimpleSidedCubeRenderer.RenderSide.SIDE);
    }
}
