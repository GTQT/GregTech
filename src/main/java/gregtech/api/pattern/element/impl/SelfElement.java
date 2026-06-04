package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PieceTemplateCompiler;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Element that matches the controller's own MetaTileEntity type (self predicate).
 *
 * <p>Depth-optimized: the {@link TraceabilityPredicate} is built once in
 * the constructor (each call to {@code MultiblockControllerBase.selfPredicate}
 * allocates a fresh predicate), and {@link #applyTo} bypasses
 * {@link #toPredicate} to skip the per-call method-indirection cost.
 */
public class SelfElement implements IStructureElement {

    private final Class<? extends MultiblockControllerBase> controllerClass;
    private final TraceabilityPredicate cachedPredicate;

    public SelfElement(Class<? extends MultiblockControllerBase> controllerClass) {
        this.controllerClass = controllerClass;
        this.cachedPredicate = MultiblockControllerBase.selfPredicate(controllerClass);
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        TileEntity te = world.getTileEntity(pos);
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
    public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                              EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Override
    public void spawnHint(World world, BlockPos pos) {
        // No hint for controller
    }

    @Override
    public boolean isCenter() {
        return true;
    }

    @Override
    public void applyTo(@NotNull String symbol, @NotNull PieceTemplateCompiler compiler) {
        // Depth-optimized: register the cached predicate directly, skipping
        // the default-method indirection through toPredicate().
        compiler.where(symbol, cachedPredicate);
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        return cachedPredicate;
    }
}
