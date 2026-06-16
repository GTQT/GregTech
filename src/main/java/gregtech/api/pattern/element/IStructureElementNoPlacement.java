package gregtech.api.pattern.element;

import gregtech.api.pattern.StructureEvaluationContext;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public interface IStructureElementNoPlacement<T> extends IStructureElement<T> {

    @NotNull
    @Override
    default Set<StructureElementCapability> getCapabilities() {
        return StructureElementCapability.withoutPlacement(IStructureElement.super.getCapabilities());
    }

    @Override
    default boolean placeBlock(@NotNull StructureEvaluationContext<T> context,
                               @NotNull EntityPlayer player, boolean skipHatches) {
        return false;
    }

    @Nullable
    @Override
    default BlocksToPlace getBlocksToPlace(@NotNull StructureEvaluationContext<T> context,
                                           @NotNull ItemStack trigger,
                                           @NotNull AutoPlaceEnvironment env) {
        return null;
    }

    @NotNull
    @Override
    default PlaceResult survivalPlaceBlock(@NotNull StructureEvaluationContext<T> context,
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
