package gregtech.api.pattern.element;

import gregtech.api.pattern.PatternMatchContext;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

public interface IStructureElementCheckOnly<T> extends IStructureElement<T> {

    @Override
    default boolean couldBeValid(World world, BlockPos pos, PatternMatchContext context,
                                 @NotNull ItemStack trigger) {
        return true;
    }

    @Override
    default boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                               EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Override
    default void spawnHint(World world, BlockPos pos) {
    }

    @NotNull
    @Override
    default PlaceResult survivalPlaceBlock(World world, BlockPos pos, PatternMatchContext context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env,
                                           boolean skipHatches) {
        return PlaceResult.REJECT_CONTINUE;
    }
}
