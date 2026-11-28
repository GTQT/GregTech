package gregtech.api.recipes.map;

import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.util.GTUtility;

import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Special {@link AbstractMapIngredient} for {@link GTRecipeInput} fluid ingredients carrying an
 * {@code NBTMatcher} / {@code NBTCondition}. Hashes by fluid registry name only and delegates matching to
 * {@link GTRecipeInput#acceptsFluid(FluidStack)}, mirroring {@link MapItemStackNBTIngredient} on the item side.
 */
public class MapFluidNBTIngredient extends MapFluidIngredient {

    @Nullable
    protected final GTRecipeInput gtRecipeInput;
    @Nullable
    protected final FluidStack fluidStack;

    public MapFluidNBTIngredient(@NotNull GTRecipeInput fluidInput) {
        super(fluidInput);
        this.gtRecipeInput = fluidInput;
        this.fluidStack = null;
    }

    public MapFluidNBTIngredient(@NotNull FluidStack stack) {
        super(stack);
        this.gtRecipeInput = null;
        this.fluidStack = GTUtility.copy(Math.max(1, stack.amount), stack);
    }

    @Override
    protected int hash() {
        return 31 + fluid.getName().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MapFluidNBTIngredient other)) {
            return false;
        }
        if (!this.fluid.getName().equals(other.fluid.getName())) {
            return false;
        }
        if (this.gtRecipeInput != null && other.gtRecipeInput != null) {
            return this.gtRecipeInput.equalIgnoreAmount(other.gtRecipeInput);
        }
        if (this.gtRecipeInput == null && other.gtRecipeInput != null) {
            return other.gtRecipeInput.acceptsFluid(this.fluidStack);
        }
        if (this.gtRecipeInput != null) {
            return this.gtRecipeInput.acceptsFluid(other.fluidStack);
        }
        return Objects.equals(this.tag, other.tag);
    }

    @Override
    public String toString() {
        return "MapFluidNBTIngredient{fluid=" + fluid.getName() + "}";
    }

    @Override
    public boolean isSpecialIngredient() {
        return true;
    }
}
