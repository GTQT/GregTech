package gregtech.mixins.ae2fc;

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.integration.modules.gregtech.CircuitHelper;
import com.glodblock.github.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin for AE2FC's Util class to preserve programmable circuit stack count
 * during pattern multiply/divide/increase/decrease operations.
 * Programmable circuits should always keep count=1 since they are notConsumed.
 */
@Mixin(value = Util.class, remap = false)
public abstract class MixinUtil {

    // Restore programmable circuit count to 1 after multiply/divide/increase/decrease

    @Inject(method = "multiplySlot", at = @At("TAIL"), remap = false)
    private static void gregtech$restoreAfterMultiply(Slot[] slots, int multiple, CallbackInfo ci) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    @Inject(method = "divideSlot", at = @At("TAIL"), remap = false)
    private static void gregtech$restoreAfterDivide(Slot[] slots, int divide, CallbackInfo ci) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    @Inject(method = "increaseSlot", at = @At("TAIL"), remap = false)
    private static void gregtech$restoreAfterIncrease(Slot[] slots, int increase, CallbackInfo ci) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    @Inject(method = "decreaseSlot", at = @At("TAIL"), remap = false)
    private static void gregtech$restoreAfterDecrease(Slot[] slots, int decrease, CallbackInfo ci) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    // Make divideSlotCheck and decreaseSlotCheck skip programmable circuits
    // by temporarily setting their count to a safe value before check

    @Inject(method = "divideSlotCheck", at = @At("HEAD"), remap = false)
    private static void gregtech$prepareForDivideCheck(Slot[] slots, int divide, CallbackInfoReturnable<Boolean> cir) {
        gregtech$setProgrammableCircuitCountForDivideCheck(slots, divide);
    }

    @Inject(method = "divideSlotCheck", at = @At("RETURN"), remap = false)
    private static void gregtech$restoreAfterDivideCheck(Slot[] slots, int divide,
                                                         CallbackInfoReturnable<Boolean> cir) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    @Inject(method = "decreaseSlotCheck", at = @At("HEAD"), remap = false)
    private static void gregtech$prepareForDecreaseCheck(Slot[] slots, int decrease,
                                                         CallbackInfoReturnable<Boolean> cir) {
        gregtech$setProgrammableCircuitCountForDecreaseCheck(slots, decrease);
    }

    @Inject(method = "decreaseSlotCheck", at = @At("RETURN"), remap = false)
    private static void gregtech$restoreAfterDecreaseCheck(Slot[] slots, int decrease,
                                                           CallbackInfoReturnable<Boolean> cir) {
        gregtech$restoreProgrammableCircuitCount(slots);
    }

    @Unique
    private static void gregtech$restoreProgrammableCircuitCount(Slot[] slots) {
        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && circuitHelper.isProgrammableCircuit(stack)) {
                stack.setCount(1);
            }
        }
    }

    /**
     * Set programmable circuit count so that divideSlotCheck passes:
     * count % divide == 0, so set count = divide.
     */
    @Unique
    private static void gregtech$setProgrammableCircuitCountForDivideCheck(Slot[] slots, int divide) {
        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && circuitHelper.isProgrammableCircuit(stack)) {
                stack.setCount(divide);
            }
        }
    }

    /**
     * Set programmable circuit count so that decreaseSlotCheck passes:
     * count - decrease >= 1, so set count = decrease + 1.
     */
    @Unique
    private static void gregtech$setProgrammableCircuitCountForDecreaseCheck(Slot[] slots, int decrease) {
        CircuitHelper circuitHelper = CircuitHelper.getInstance();
        for (Slot slot : slots) {
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && circuitHelper.isProgrammableCircuit(stack)) {
                stack.setCount(decrease + 1);
            }
        }
    }
}
