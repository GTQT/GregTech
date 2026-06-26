package gregtech.api.pattern.element.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.BlockWorldState;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Element that matches the controller's own MetaTileEntity type (self predicate).
 */
public class SelfElement implements ITypedStructureElement<Object> {

    private final Class<? extends MultiblockControllerBase> controllerClass;
    private final TraceabilityPredicate cachedPredicate;
    private final StructureElementPreview preview;

    public SelfElement(Class<? extends MultiblockControllerBase> controllerClass) {
        this.controllerClass = controllerClass;
        // Inline legacy predicate construction (will be removed together with TraceabilityPredicate)
        this.cachedPredicate = new TraceabilityPredicate(blockWorldState -> {
            TileEntity tileEntity = blockWorldState.getTileEntity();
            if (!(tileEntity instanceof IGregTechTileEntity)) {
                return false;
            }
            MetaTileEntity metaTileEntity = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
            return controllerClass.isInstance(metaTileEntity);
        }, this::getCandidates).setCenter();
        this.preview = StructureElementPreview.of(this::getCandidates);
    }

    @Override
    public boolean check(@NotNull StructureEvaluationContext<Object> context) {
        TileEntity te = context.getTileEntity();
        if (te instanceof IGregTechTileEntity) {
            MetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            return controllerClass.isInstance(mte);
        }
        return false;
    }

    @Override
    @NotNull
    public BlockInfo[] getCandidates() {
        if (GregTechAPI.mteManager == null) {
            return new BlockInfo[] { BlockInfo.EMPTY };
        }

        List<BlockInfo> matches = new ArrayList<>();
        for (var registry : GregTechAPI.mteManager.getRegistries()) {
            for (MetaTileEntity metaTileEntity : registry) {
                if (controllerClass.isInstance(metaTileEntity)) {
                    matches.add(candidateInfo(metaTileEntity));
                }
            }
        }
        return matches.isEmpty() ? new BlockInfo[] { BlockInfo.EMPTY } : matches.toArray(new BlockInfo[0]);
    }

    @Override
    @NotNull
    public StructureElementPreview getPreview() {
        return preview;
    }

    @Override
    public boolean isCenter() {
        return true;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }

    @NotNull
    private static BlockInfo candidateInfo(@NotNull MetaTileEntity metaTileEntity) {
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        holder.setMetaTileEntity(metaTileEntity);
        holder.getMetaTileEntity().onPlacement();
        holder.getMetaTileEntity().setFrontFacing(EnumFacing.SOUTH);
        return new BlockInfo(metaTileEntity.getBlock().getDefaultState(), holder);
    }
}
