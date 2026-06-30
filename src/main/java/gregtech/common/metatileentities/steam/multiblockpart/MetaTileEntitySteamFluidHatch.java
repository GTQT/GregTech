package gregtech.common.metatileentities.steam.multiblockpart;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTheme;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.client.utils.TooltipHelper;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityFluidHatch;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntitySteamFluidHatch extends MetaTileEntityFluidHatch {

    public MetaTileEntitySteamFluidHatch(ResourceLocation metaTileEntityId, boolean isExportHatch) {
        super(metaTileEntityId, 1, isExportHatch);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntitySteamFluidHatch(metaTileEntityId, isExportHatch);
    }

    @Override
    public MultiblockAbility<IFluidTank> getAbility() {
        return isExportHatch ? MultiblockAbility.STEAM_EXPORT_FLUID : MultiblockAbility.STEAM_IMPORT_FLUID;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(isExportHatch ? this.exportFluids : this.importFluids);
    }

    @Override
    public ICubeRenderer getBaseTexture() {
        if(getController()==null)
            return Textures.STEAM_CASING_BRONZE;
        return super.getBaseTexture();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(TooltipHelper.BLINKING_ORANGE + I18n.format("gregtech.machine.steam_bus.tooltip"));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.shouldRenderOverlay()) {
            SimpleOverlayRenderer renderer = this.isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
            renderer.renderSided(this.getFrontFacing(), renderState, translation, pipeline);
            Textures.PIPE_FLUID_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
            SimpleOverlayRenderer overlay = isExportHatch ? Textures.FLUID_HATCH_OUTPUT_OVERLAY :
                    Textures.FLUID_HATCH_INPUT_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.BRONZE;
    }
}
