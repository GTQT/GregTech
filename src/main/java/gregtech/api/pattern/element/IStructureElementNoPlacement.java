package gregtech.api.pattern.element;

import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.util.BlockInfo;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IStructureElementNoPlacement<T> extends IStructureElement<T> {

    @Override
    default boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                               EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Nullable
    @Override
    default BlocksToPlace getBlocksToPlace(World world, BlockPos pos, PatternMatchContext context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env) {
        return null;
    }

    @NotNull
    @Override
    default PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        return PlaceResult.REJECT;
    }

    @Override
    default IStructureElementNoPlacement<T> noPlacement() {
        return this;
    }
}
