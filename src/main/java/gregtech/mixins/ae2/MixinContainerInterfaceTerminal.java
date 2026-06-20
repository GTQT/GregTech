package gregtech.mixins.ae2;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.IMEPatternProviderPart;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.implementations.ContainerInterfaceTerminal.ProviderTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = ContainerInterfaceTerminal.class, remap = false)
public abstract class MixinContainerInterfaceTerminal {

    @Shadow
    private IGrid grid;

    @Shadow
    private List<ProviderTracker> provider;

    /**
     * @author GregTech
     * @reason Inject GT pattern provider change detection logic
     */
    @Overwrite
    protected int[] checkGTProviderChanges() {
        int total = 0;
        boolean missing = false;

        for (final IGridNode gn : grid.getMachineNodes(IMEPatternProviderPart.class)) {
            if (gn.isActive()) {
                BlockPos pos = gn.getGridBlock().getLocation().getPos();
                World world = gn.getGridBlock().getLocation().getWorld();
                TileEntity te = world.getTileEntity(pos);

                if (te instanceof IGregTechTileEntity igtte) {
                    MetaTileEntity mte = igtte.getMetaTileEntity();
                    if (mte instanceof IMEPatternProviderPart providerPart
                            && providerPart.getPatternSlot() != null) {
                        ProviderTracker t = null;
                        for (ProviderTracker pt : provider) {
                            if (pt.pos.equals(pos) && pt.dim == mte.getWorld().provider.getDimension()) {
                                t = pt;
                                break;
                            }
                        }

                        if (t == null) {
                            missing = true;
                        } else {
                            String currentName = getProviderDisplayName(mte, providerPart);
                            if (!t.unlocalizedName.equals(currentName)) {
                                missing = true;
                            }
                        }
                        total++;
                    }
                }
            }
        }

        return new int[]{total, missing ? 1 : 0};
    }

    /**
     * @author GregTech
     * @reason Inject GT pattern provider collection logic
     */
    @Overwrite
    protected void collectGTProviders() {
        for (final IGridNode gn : grid.getMachineNodes(IMEPatternProviderPart.class)) {
            BlockPos pos = gn.getGridBlock().getLocation().getPos();
            TileEntity te = gn.getGridBlock().getLocation().getWorld().getTileEntity(pos);
            if (te instanceof IGregTechTileEntity igtte) {
                MetaTileEntity mte = igtte.getMetaTileEntity();
                if (mte instanceof IMEPatternProviderPart patternProvider
                        && patternProvider.getPatternSlot() != null) {
                    String displayName = getProviderDisplayName(mte, patternProvider);
                    provider.add(new ProviderTracker(
                            mte.getPos(),
                            mte.getWorld().provider.getDimension(),
                            patternProvider.getTier(),
                            patternProvider.getPatternSlot(),
                            displayName
                    ));
                }
            }
        }
    }

    private static String getProviderDisplayName(MetaTileEntity controlBase, IMEPatternProviderPart providerPart) {
        if (providerPart.getController() != null
                && providerPart.getShowName().equals(controlBase.getMetaFullName())) {
            return providerPart.getController().getMetaFullName();
        }
        return providerPart.getShowName();
    }
}
