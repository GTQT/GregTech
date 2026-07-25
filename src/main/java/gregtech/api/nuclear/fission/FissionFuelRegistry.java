package gregtech.api.nuclear.fission;

import gregtech.api.unification.material.properties.IFissionFuelStats;
import gregtech.api.util.ItemStackHashStrategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

public class FissionFuelRegistry {

    private static final Map<String, IFissionFuelStats> IDENTIFIED_FUELS = new Object2ObjectOpenHashMap<>();
    private static final Map<ItemStack, IFissionFuelStats> FUELS = new Object2ObjectOpenCustomHashMap<>(
            ItemStackHashStrategy.comparingAllButCount());

    public static void registerFuel(@NotNull ItemStack item, @NotNull IFissionFuelStats fuel) {
        IDENTIFIED_FUELS.put(fuel.getId(), fuel);
        FUELS.put(item, fuel);
    }

    @Nullable
    public static IFissionFuelStats getFissionFuel(ItemStack stack) {
        return FUELS.get(stack);
    }

    @NotNull
    public static Collection<ItemStack> getAllFissionableRods() {
        return FUELS.keySet();
    }

    @Nullable
    public static IFissionFuelStats getFissionFuel(String name) {
        return IDENTIFIED_FUELS.get(name);
    }
}