package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A parallel hatch that asks the controller for cross-recipe parallel: the whole parallel budget is shared
 * elastically between however many recipes the inputs can feed, instead of every slot being capped at
 * {@link #getCurrentParallel()}.
 * <p>
 * It deliberately reuses the ordinary parallel hatch for everything user visible — same
 * {@link gregtech.api.metatileentity.multiblock.MultiblockAbility#PARALLEL_HATCH ability}, same overlay,
 * same adjustment GUI — so the two are interchangeable in a structure and only the mode flag differs.
 */
public class MetaTileEntityCrossParallelHatch extends MetaTileEntityParallelHatch {

    public MetaTileEntityCrossParallelHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    public MetaTileEntityCrossParallelHatch(ResourceLocation metaTileEntityId, int tier, int maxParallel) {
        super(metaTileEntityId, tier, maxParallel);
    }

    /**
     * Unlike the plain parallel hatch this keeps the explicit ceiling, so an unlimited creative hatch stays
     * unlimited after being reloaded instead of falling back to what the tier alone would give.
     */
    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityCrossParallelHatch(this.metaTileEntityId, this.getTier(), this.getMaxParallel());
    }

    @Override
    public boolean isCrossParallel() {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.cross_parallel_hatch.tooltip"));
        tooltip.add(I18n.format("gregtech.machine.cross_parallel_hatch.incompatible"));
    }
}
