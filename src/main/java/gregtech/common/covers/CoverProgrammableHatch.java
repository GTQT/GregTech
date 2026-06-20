package gregtech.common.covers;

import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.gui.ModularUI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockNotifiablePart;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import gregtech.common.items.behaviors.ProgrammableCircuit;

import java.util.Optional;

/**
 * 可编程覆盖板。
 * 检测输入总线中的可编程电路，从 NBT 中读取被包裹的物品，
 * 并将其设置到对应机器的虚拟电路槽位中。
 */
public class CoverProgrammableHatch extends CoverBase implements CoverWithUI, ITickable {

    public CoverProgrammableHatch(CoverDefinition definition, CoverableView coverableView, EnumFacing attachedSide) {
        super(definition, coverableView, attachedSide);
    }

    @Override
    public ModularUI createUI(EntityPlayer entityPlayer) {
        return null;
    }

    @Override
    public void update() {
        if (getCoverableView().getWorld().isRemote) return;

        TileEntity tileEntity = getCoverableView().getWorld().getTileEntity(getCoverableView().getPos());
        if (tileEntity instanceof IGregTechTileEntity igtte) {
            MetaTileEntity mte = igtte.getMetaTileEntity();
            // 处理单方块机器
            if (mte instanceof SimpleMachineMetaTileEntity machineMetaTile) {
                processImportItems(machineMetaTile.getImportItems(), machineMetaTile);
            }
            // 处理多方块仓室
            if (mte instanceof MetaTileEntityMultiblockNotifiablePart hatch
                    && mte instanceof IGhostSlotConfigurable configurable) {
                processImportItems(hatch.getImportItems(), configurable);
            }
        }
    }

    /**
     * 处理输入物品，将可编程电路中的被包裹物品设置到虚拟电路槽。
     */
    private void processImportItems(IItemHandlerModifiable importItems,
                                    IGhostSlotConfigurable configurable) {
        if (!configurable.hasGhostCircuitInventory()) return;

        for (int i = 0; i < importItems.getSlots(); i++) {
            ItemStack itemStack = importItems.getStackInSlot(i);
            if (itemStack.isEmpty()) continue;
            if (ProgrammableCircuit.getInstanceFor(itemStack) == null) continue;

            if (ProgrammableCircuit.hasWrappedItem(itemStack)) {
                // 有包裹物品：解包并设置到虚拟电路槽
                Optional<ItemStack> wrappedItem = ProgrammableCircuit.getWrappedItem(itemStack);
                wrappedItem.ifPresent(configurable::setGhostCustomStack);
            } else {
                // 空白可编程电路：重置虚拟电路槽为空
                configurable.setGhostCustomStack(ItemStack.EMPTY);
            }
            // 消耗可编程电路（不回收）
            importItems.extractItem(i, 1, false);
        }
    }

    @Override
    public boolean canAttach(CoverableView coverable, EnumFacing side) {
        return coverable.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, getAttachedSide()) != null;
    }

    @Override
    public void renderCover(CCRenderState renderState, Matrix4 translation,
                            IVertexOperation[] pipeline, Cuboid6 plateBox,
                            BlockRenderLayer layer) {
        Textures.FUSION_REACTOR_OVERLAY.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }
}
