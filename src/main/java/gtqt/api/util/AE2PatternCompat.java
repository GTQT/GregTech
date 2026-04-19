package gtqt.api.util;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.Optional;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;

public final class AE2PatternCompat {

    private AE2PatternCompat() {}

    public static boolean isFluidDrop(ItemStack stack) {
        return ItemFluidDrop.isFluidDrop(stack);
    }

    public static FluidStack getFluidStack(ItemStack stack) {
        return ItemFluidDrop.getFluidStack(stack);
    }

    public static ItemStack toFluidDrop(FluidStack fluidStack) {
        return ItemFluidDrop.newStack(fluidStack);
    }

    public static NBTBase createPatternIngredientTag(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();

        if (stack.isEmpty()) {
            return tag;
        }

        if (isFluidDrop(stack)) {
            IAEFluidStack fluidStack = ItemFluidDrop.getAeFluidStack(AEItemStack.fromItemStack(stack));
            if (fluidStack != null) {
                return fluidStack.toNBTGeneric();
            }
        }

        FluidStack containedFluid = FluidUtil.getFluidContained(stack);
        if (containedFluid != null && containedFluid.amount > 0) {
            IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(containedFluid);
            if (aeFluid != null) {
                aeFluid.setStackSize((long) containedFluid.amount * stack.getCount());
                return aeFluid.toNBTGeneric();
            }
        }

        stackWriteToNBT(stack, tag);
        return tag;
    }

    public static boolean containsFluid(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }

        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (isFluidDrop(stack)) {
                return true;
            }
            FluidStack containedFluid = FluidUtil.getFluidContained(stack);
            if (containedFluid != null && containedFluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack createProcessingPattern(ItemStack[] inputs, ItemStack[] outputs, boolean substitute,
                                                    boolean fluidPattern) {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inTag = new NBTTagList();
        NBTTagList outTag = new NBTTagList();

        for (ItemStack input : inputs) {
            inTag.appendTag(createPatternIngredientTag(input));
        }
        for (ItemStack output : outputs) {
            outTag.appendTag(createPatternIngredientTag(output));
        }

        tag.setTag("in", inTag);
        tag.setTag("out", outTag);
        tag.setBoolean("crafting", false);
        tag.setBoolean("substitute", substitute);
        if (fluidPattern || containsFluid(inputs) || containsFluid(outputs)) {
            tag.setBoolean("fluidPattern", true);
        }

        Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        if (!maybePattern.isPresent()) {
            return ItemStack.EMPTY;
        }

        ItemStack patternStack = maybePattern.get();
        patternStack.setTagCompound(tag);
        return patternStack;
    }
}
