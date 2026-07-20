package gregtech.integration.chisel;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.GTValues.MODID;

public class ModUtility {

    public static @NotNull ItemStack getModItem(String modID, String itemName) {
        return GameRegistry.makeItemStack(modID + ":" + itemName, 0, 1, null);
    }

    public static @NotNull ItemStack getModItem(String modID, String itemName, int amount) {
        return GameRegistry.makeItemStack(modID + ":" + itemName, 0, amount, null);
    }

    public static @NotNull ItemStack getModItem(String modID, String itemName, int amount, int meta) {
        return GameRegistry.makeItemStack(modID + ":" + itemName, meta, amount, null);
    }

    public static @NotNull ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public static void registerOre(String dictName, ItemStack... itemStacks) {
        for (ItemStack stack : itemStacks) {
            OreDictionary.registerOre(dictName, stack);
        }
    }
}
