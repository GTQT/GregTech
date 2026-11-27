package gregtech.api.capability;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

public interface IMufflerHatch {

    void recoverItemsTable(List<ItemStack> recoveryItems,int parallel);

    /**
     * @param recoveryFluids the fluid to recover
     */
    void recoverFluidsTable(FluidStack recoveryFluids);

    /**
     * @return true if front face is free and contains only air blocks in 1x1 area
     */
    boolean isFrontFaceFree();

    boolean isMufflerFull();

    boolean mufflerWaste();

    /**
     * Fraction of pollution that passes through this hatch (0..1).
     * LV is 1.0, MV 0.875, ... UHV 0.0, matching GTNH's muffler table.
     */
    default double getPollutionReduction() {
        return 1.0D;
    }
}
