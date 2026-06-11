package gregtech.api.pattern.element.impl;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Element that matches hatch positions for multiblock abilities.
 */
public class HatchElement implements IStructureElement<Object> {

    private final MultiblockAbility<?> ability;
    private final int minCount;
    private final int maxCount;

    public HatchElement(MultiblockAbility<?> ability) {
        this(ability, 0, -1);
    }

    public HatchElement(MultiblockAbility<?> ability, int minCount, int maxCount) {
        this.ability = ability;
        this.minCount = minCount;
        this.maxCount = maxCount;
    }

    @Override
    public boolean check(World world, BlockPos pos, PatternMatchContext context) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof IGregTechTileEntity) {
            MetaTileEntity mte = ((IGregTechTileEntity) te).getMetaTileEntity();
            if (mte instanceof IMultiblockAbilityPart) {
                for (MultiblockAbility<?> a : ((IMultiblockAbilityPart<?>) mte).getAbilities()) {
                    if (a == ability) {
                        return true;
                    }
                }
            }
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
        // Hints are handled at a higher level
    }

    @Override
    public int getMinGlobalCount() {
        return minCount;
    }

    @Override
    public int getMaxGlobalCount() {
        return maxCount;
    }

    @Override
    public boolean usesLegacyPredicateRuntime() {
        return true;
    }

    @Override
    public TraceabilityPredicate toPredicate() {
        TraceabilityPredicate pred = MultiblockControllerBase.abilities(ability);
        if (minCount > 0) {
            pred = pred.setMinGlobalLimited(minCount);
        }
        if (maxCount > 0) {
            pred = pred.setMaxGlobalLimited(maxCount);
        }
        return pred;
    }
}
