package gregtech.mixins.mui2;

import gregtech.common.ConfigHolder;

import net.minecraft.client.Minecraft;

import com.cleanroommc.modularui.ModularUI;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives every MUI2 panel a short jelly-like pop animation when it gets opened.
 * <p>
 * MUI2 ships its own opening animation, but it is dead code unless NeverEnoughAnimations is installed, since
 * {@link ModularPanel#getScale()} bails out when NEA is absent. This mixin drives the very same
 * {@code getScale()}/{@code transform()} hook with its own damped oscillation, so the animation works without NEA
 * and keeps the drawing, the hover detection and the slot positions in sync (all of them go through
 * {@link ModularPanel#transform(com.cleanroommc.modularui.screen.viewport.IViewportStack)}).
 * <p>
 * The scale follows {@code 1 - (1 - start) * e^(-damping * t) * cos(frequency * t)}: it starts small, overshoots
 * slightly past 1, dips below it once more and settles exactly at 1. When NEA is installed this mixin does nothing
 * and lets NEA handle the animation, as it also animates the screen itself.
 */
@Mixin(value = ModularPanel.class, remap = false)
public class ModularPanelMixin {

    @Unique
    private static final float GREGTECH_JELLY_START_SCALE = 0.6f;
    @Unique
    private static final float GREGTECH_JELLY_DAMPING = 3.6f;
    @Unique
    private static final float GREGTECH_JELLY_FREQUENCY = 7.0f;
    @Unique
    private static final float GREGTECH_JELLY_DURATION = 400f;

    @Unique
    private boolean gregtech$jellyAnimating = false;
    @Unique
    private long gregtech$jellyStartTime = 0L;

    @Inject(method = "onOpen", at = @At("TAIL"))
    private void gregtech$startJellyAnimation(ModularScreen screen, CallbackInfo ci) {
        if (!gregtech$shouldJellyAnimate()) return;
        this.gregtech$jellyStartTime = Minecraft.getSystemTime();
        this.gregtech$jellyAnimating = true;
    }

    @Inject(method = "getScale", at = @At("HEAD"), cancellable = true)
    private void gregtech$jellyScale(CallbackInfoReturnable<Float> cir) {
        if (!this.gregtech$jellyAnimating) return;

        float progress = (Minecraft.getSystemTime() - this.gregtech$jellyStartTime) / GREGTECH_JELLY_DURATION;
        if (progress >= 1f) {
            // let the vanilla implementation take over again, it resolves to 1f without NEA
            this.gregtech$jellyAnimating = false;
            return;
        }
        if (progress < 0f) progress = 0f;

        float damping = (float) (Math.exp(-GREGTECH_JELLY_DAMPING * progress) * Math.cos(GREGTECH_JELLY_FREQUENCY * progress));
        float interpolated = 1f - damping;
        cir.setReturnValue(GREGTECH_JELLY_START_SCALE + (1f - GREGTECH_JELLY_START_SCALE) * interpolated);
    }

    @Unique
    private static boolean gregtech$shouldJellyAnimate() {
        if (ModularUI.Mods.NEA.isLoaded()) return false;
        return ConfigHolder.client.guiConfig.jellyOpeningAnimation;
    }
}
