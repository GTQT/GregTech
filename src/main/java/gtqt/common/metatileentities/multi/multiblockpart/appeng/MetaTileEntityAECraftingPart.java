package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;

import net.minecraft.util.ResourceLocation;

import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.GridAccessException;
import lombok.Getter;

public abstract class MetaTileEntityAECraftingPart extends MetaTileEntityAEHostablePart implements ICraftingProvider {

    @Getter
    IGridNode node;

    public MetaTileEntityAECraftingPart(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
    }

    public void pushToGridCache() {
        try {
            if (getProxy() != null) {
                getProxy().getGrid().getCache(ICraftingGrid.class).addNode(getProxy().getNode(), (IGridHost) this);
                node = getProxy().getNode();
            }
        } catch (GridAccessException ignored) {}
    }

    public void removeFromGridCache() {
        try {
            if (getProxy() != null) {
                getProxy().getGrid().getCache(ICraftingGrid.class).removeNode(getProxy().getNode(), (IGridHost) this);
            }
        } catch (GridAccessException ignored) {}
    }
}
