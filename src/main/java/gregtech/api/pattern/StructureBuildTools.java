package gregtech.api.pattern;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Extension point for held items that may drive the structure builder.
 *
 * <p>The built-in multiblock builder is always accepted. Addons that ship their own builder tool (for example
 * an AE2-backed one) register a predicate here, so the client build packet accepts their item without GT
 * having to know about the addon.
 */
public final class StructureBuildTools {

    private static final List<Predicate<ItemStack>> EXTRA_TOOLS = new CopyOnWriteArrayList<>();

    private StructureBuildTools() {}

    /**
     * Registers an additional builder tool.
     *
     * @param tool predicate matching the addon's builder item stacks
     */
    public static void register(@NotNull Predicate<ItemStack> tool) {
        EXTRA_TOOLS.add(tool);
    }

    /**
     * @param stack held stack to test
     * @return whether an addon registered this stack as a valid builder tool
     */
    public static boolean isRegistered(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Predicate<ItemStack> tool : EXTRA_TOOLS) {
            if (tool.test(stack)) {
                return true;
            }
        }
        return false;
    }
}
