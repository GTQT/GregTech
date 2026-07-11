package gregtech.api.pattern.element.impl;

import gregtech.api.pattern.StructureEvaluationContext;
import gregtech.api.pattern.element.ITypedStructureElement;
import gregtech.api.pattern.element.StructureElementCapability;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import java.util.Set;

/**
 * Element that matches air blocks.
 */
public class AirElement implements ITypedStructureElement<Object> {

    public static final AirElement INSTANCE = new AirElement();

    private AirElement() {}

    @Override
    public Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.snapshotSafe();
    }

    @Override
    public boolean check(StructureEvaluationContext<Object> context) {
        return context.getBlockState().getBlock().isAir(context.getBlockState(),
                context.getBlockAccess(), context.getPos());
    }

    @Override
    public BlockInfo[] getCandidates() {
        return new BlockInfo[]{new BlockInfo(Blocks.AIR.getDefaultState(), null)};
    }

    @Override
    public boolean placeBlock(StructureEvaluationContext<Object> context,
                              EntityPlayer player) {
        World world = context.getWorld();
        if (world == null) {
            return false;
        }
        world.setBlockToAir(context.getPos());
        return true;
    }
}
