package gregtech.common.metatileentities.multi;

import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.ParametricMultiblockPart;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.MetaTileEntityMultiblockTank.TankMaterial;

import net.minecraft.block.SoundType;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Single-ID tank valve supporting multiple material variants via NBT.
 * Extends {@link ParametricMultiblockPart} for automatic variant management.
 */
public class MetaTileEntityTankValve extends ParametricMultiblockPart<TankMaterial>
                                     implements IMultiblockAbilityPart<IFluidHandler> {

    public MetaTileEntityTankValve(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, TankMaterial.class, TankMaterial.WOOD);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        MetaTileEntityTankValve valve = new MetaTileEntityTankValve(metaTileEntityId);
        valve.setVariant(getVariant());
        return valve;
    }

    @Override
    @NotNull
    protected String getVariantTranslationPrefix() {
        return "gregtech.machine.tank_valve";
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.PIPE_IN_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @Override
    public ICubeRenderer getBaseTexture() {
        if (getController() == null) {
            return getVariant().getTexture();
        }
        return super.getBaseTexture();
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0L && isAttachedToMultiBlock() &&
                getFrontFacing() == EnumFacing.DOWN) {
            TileEntity tileEntity = getNeighbor(getFrontFacing());
            IFluidHandler fluidHandler = tileEntity == null ? null : tileEntity
                    .getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, getFrontFacing().getOpposite());
            if (fluidHandler != null) {
                GTTransferUtils.transferFluids(fluidInventory, fluidHandler);
            }
        }
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        initializeDummyInventory();
    }

    private void initializeDummyInventory() {
        this.fluidInventory = new FluidHandlerProxy(new FluidTankList(false), new FluidTankList(false));
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        super.addToMultiBlock(controllerBase);
        this.fluidInventory = controllerBase.getFluidInventory();
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        super.removeFromMultiBlock(controllerBase);
        initializeDummyInventory();
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return false;
    }

    @Override
    public boolean canPartShare() {
        return false;
    }

    @Override
    public MultiblockAbility<IFluidHandler> getAbility() {
        return MultiblockAbility.TANK_VALVE;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this.getImportFluids());
    }

    @Override
    protected boolean shouldSerializeInventories() {
        return false;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.tank_valve.tooltip"));
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @NotNull
    @Override
    public SoundType getSoundType() {
        return getVariant().isWood() ? SoundType.WOOD : SoundType.METAL;
    }
}
