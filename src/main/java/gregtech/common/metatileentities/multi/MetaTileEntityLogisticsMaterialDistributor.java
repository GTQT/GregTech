package gregtech.common.metatileentities.multi;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.unification.material.Materials;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

public class MetaTileEntityLogisticsMaterialDistributor extends MultiblockWithDisplayBase {

    protected IItemHandlerModifiable inputInventory;
    protected IMultipleTankHandler inputFluidInventory;

    List<IFluidTank> outputFluidTanks;
    List<IItemHandlerModifiable> outputItemHandlers;

    public MetaTileEntityLogisticsMaterialDistributor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    protected IMultipleTankHandler getInputTank(IItemHandler items) {
        List<IMultipleTankHandler.ITankEntry> tanks = new ArrayList<>();
        if (items instanceof IMultipleTankHandler tankHandler) {
            tanks.addAll(tankHandler.getFluidTanks());
        }
        return new FluidTankList(false, tanks);
    }

    @Override
    protected void updateFormedValid() {
        if (!isStructureFormed()) return;

        // 处理物品转移
        if (inputInventory != null) {
            for (int i = 0; i < inputInventory.getSlots(); i++) {
                ItemStack sourceStack = inputInventory.getStackInSlot(i);
                if (!sourceStack.isEmpty()) {
                    // 尝试插入到对应的输出槽
                    ItemStack remainder = insertItem(outputItemHandlers.get(i), sourceStack.copy(), false);

                    // 计算实际转移的数量
                    int amountTransferred = sourceStack.getCount() - remainder.getCount();
                    if (amountTransferred > 0) {
                        // 实际从输入槽中移除物品
                        inputInventory.extractItem(i, amountTransferred, false);
                    }
                }
            }
        }

        // 处理流体转移
        if (inputFluidInventory != null) {
            for (int i = 0; i < inputFluidInventory.getTanks(); i++) {
                IMultipleTankHandler.ITankEntry inputTank = inputFluidInventory.getTankAt(i);
                FluidStack sourceStack = inputTank.getFluid();

                if (sourceStack != null && sourceStack.getFluid() != null && i < outputFluidTanks.size()) {
                    IFluidTank outputTank = outputFluidTanks.get(i);

                    // 计算可以转移的流体量
                    int amountToTransfer = Math.min(sourceStack.amount,
                            outputTank.getCapacity() - outputTank.getFluidAmount());

                    if (amountToTransfer > 0) {
                        // 创建要转移的流体栈
                        FluidStack fluidToTransfer = new FluidStack(sourceStack.getFluid(), amountToTransfer);

                        // 实际填充到输出储罐
                        int filled = outputTank.fill(fluidToTransfer, true);

                        if (filled > 0) {
                            // 实际从输入储罐中排出
                            inputTank.drain(filled, true);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formStructureWithDisplay(formed);
        outputFluidTanks = getAbilities(MultiblockAbility.EXPORT_FLUIDS);
        outputItemHandlers = getAbilities(MultiblockAbility.EXPORT_ITEMS);

        List<IItemHandlerModifiable> importItemHandlers = getAbilities(MultiblockAbility.IMPORT_ITEMS);
        for (IItemHandlerModifiable bus : importItemHandlers) {
            if (getInputTank(bus) != null) {
                this.inputFluidInventory = getInputTank(bus);
            }
        }

        this.inputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        if (inputFluidInventory == null)
            this.inputFluidInventory = new FluidTankList(false, getAbilities(MultiblockAbility.IMPORT_FLUIDS));
    }

    private ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) return stack;

        ItemStack remaining = stack.copy();

        for (int i = 0; i < handler.getSlots() && !remaining.isEmpty(); i++) {
            remaining = handler.insertItem(i, remaining, simulate);
        }

        return remaining;
    }

    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return DeclarativePatternBuilder.start(RIGHT, BACK, UP)
                .piece("header")
                .aisle("ISI", "OEO")
                .aisle("XXX", "OEO")
                .aisle("XXX", "OEO")
                .aisle("XXX", "OEO")
                .repeatablePiece("body", 0, 12)
                .aisle(" F ", "XEX")
                .withAisleChannel(GTStructureChannels.STRUCTURE_HEIGHT.getName())
                .self('S', MetaTileEntityLogisticsMaterialDistributor.class)
                .where('I', Elements.chain(
                        Elements.block(getCasingState()),
                        Elements.abilities(MultiblockAbility.IMPORT_ITEMS, MultiblockAbility.IMPORT_FLUIDS)))
                .where('O', Elements.chain(
                        Elements.block(getCasingState()),
                        Elements.abilities(MultiblockAbility.EXPORT_FLUIDS)))
                .where('E', Elements.chain(
                        Elements.block(getCasingState()),
                        Elements.abilities(MultiblockAbility.EXPORT_ITEMS)))
                .frames('F', Materials.StainlessSteel)
                .any(' ')
                .casing('X', getCasingState())
                .buildStructureDefinition();
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLogisticsMaterialDistributor(metaTileEntityId);
    }

    @SideOnly(Side.CLIENT)
    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.HPCA_OVERLAY;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), this.isActive(),
                false);
    }
}
