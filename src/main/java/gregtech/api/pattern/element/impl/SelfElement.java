package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.tileentity.TileEntity;

/**
 * Element that matches the controller's own MetaTileEntity type (self predicate).
 */
public class SelfElement implements ITypedStructureElement<Object> {

    private final Class<? extends MultiblockControllerBase> controllerClass;
    private final TraceabilityPredicate cachedPredicate;

    public SelfElement(Class<? extends MultiblockControllerBase> controllerClass) {
        this.controllerClass = controllerClass;
        this.cachedPredicate = MultiblockControllerBase.selfPredicate(controllerClass);
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        TileEntity te = context.getTileEntity();
        if (te instanceof IGregTechTileEntity) {
            MetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            return controllerClass.isInstance(mte);
        }
        return false;
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[0];
    }

    @Override
    public boolean isCenter() {
        return true;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }
}
