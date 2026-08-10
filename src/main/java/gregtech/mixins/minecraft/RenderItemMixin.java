package gregtech.mixins.minecraft;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.handler.LampItemOverlayRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.client.utils.ToolChargeBarRenderer;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeChest;
import gregtech.common.metatileentities.storage.MetaTileEntityCreativeTank;
import gregtech.common.metatileentities.storage.MetaTileEntityDrum;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumChest;
import gregtech.common.metatileentities.storage.MetaTileEntityQuantumTank;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.text.DecimalFormat;

@Mixin(RenderItem.class)
public class RenderItemMixin {

    @Unique
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("0.#");

    // ===== Fluid bars =====

    @Unique
    private static void gregtech$renderFluidBar(@NotNull ItemStack stack, int xPosition, int yPosition, int tankSize) {
        if (stack.isEmpty() || stack.getCount() > 1) return; // ignore stacked items

        FluidStack fluid = FluidUtil.getFluidContained(stack);
        if (fluid == null || fluid.amount <= 0) return;

        double fillRate = MathHelper.clamp(fluid.amount / (double) tankSize, 0.0, 1.0);
        Color color = new Color(GTUtility.convertRGBtoOpaqueRGBA_MC(RenderUtil.getFluidColor(fluid)));
        ToolChargeBarRenderer.render(fillRate, xPosition, yPosition, 0, true, color, color, false);
    }

    @Unique
    private static void gregtech$renderDrumBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (GTUtility.getMetaTileEntity(stack) instanceof MetaTileEntityDrum drum) {
            gregtech$renderFluidBar(stack, xPosition, yPosition, drum.getTankSize());
        }
    }

    @Unique
    private static void gregtech$renderQuantumTankBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (GTUtility.getMetaTileEntity(stack) instanceof MetaTileEntityQuantumTank tank
                && !(tank instanceof MetaTileEntityCreativeTank)) {
            gregtech$renderFluidBar(stack, xPosition, yPosition, tank.getTankSize());
        }
    }

    @Unique
    private static void gregtech$renderQuantumChestBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (GTUtility.getMetaTileEntity(stack) instanceof MetaTileEntityQuantumChest chest
                && !(chest instanceof MetaTileEntityCreativeChest)) {
            long itemCapacity = chest.getMaxStoredItems();
            long itemStore = chest.getStoredItemCountFromNBT(stack) + chest.getExportItemCountFromNBT(stack);
            double fillRate = itemStore / (double) itemCapacity;
            ToolChargeBarRenderer.render(fillRate, xPosition, yPosition, 0, true, Color.BLUE, Color.BLUE, false);
        }
    }

    // ===== Electric bars =====

    @Unique
    private static void gregtech$renderElectricBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (stack.getItem() instanceof IGTTool tool) {
            ToolChargeBarRenderer.renderBarsTool(tool, stack, xPosition, yPosition);
        } else if (stack.getItem() instanceof MetaItem<?> metaItem) {
            ToolChargeBarRenderer.renderBarsItem(metaItem, stack, xPosition, yPosition);
        }
    }

    // ===== Lamp overlay =====

    @Unique
    private static void gregtech$renderLampOverlay(@NotNull ItemStack stack, int xPosition, int yPosition) {
        LampItemOverlayRenderer.OverlayType overlayType = LampItemOverlayRenderer.getOverlayType(stack);
        if (overlayType != LampItemOverlayRenderer.OverlayType.NONE) {
            LampItemOverlayRenderer.renderOverlay(overlayType, xPosition, yPosition);
        }
    }

    // ===== Injections =====

    @Inject(method = "renderItemOverlayIntoGUI", at = @At(value = "HEAD"))
    private void renderItemOverlayIntoGUILamp(FontRenderer fr, ItemStack stack, int xPosition, int yPosition,
                                              String text, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            gregtech$renderLampOverlay(stack, xPosition, yPosition);
        }
    }

    // The easy part of translating the item render stuff
    @Inject(method = "renderItemOverlayIntoGUI",
            at = @At(value = "INVOKE_ASSIGN",
                     target = "Lnet/minecraft/client/Minecraft;getMinecraft()Lnet/minecraft/client/Minecraft;",
                     shift = At.Shift.BEFORE,
                     ordinal = 0))
    private void renderItemOverlayIntoGUIBars(FontRenderer fr, ItemStack stack, int xPosition, int yPosition,
                                              String text, CallbackInfo ci) {
        gregtech$renderElectricBar(stack, xPosition, yPosition);
        gregtech$renderDrumBar(stack, xPosition, yPosition);
        gregtech$renderQuantumTankBar(stack, xPosition, yPosition);
        gregtech$renderQuantumChestBar(stack, xPosition, yPosition);
    }

    @Inject(at = @At("RETURN"),
            method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
    private void renderItemOverlayIntoGUIDurability(FontRenderer fr, ItemStack stack, int xPosition, int yPosition,
                                                    String text, CallbackInfo ci) {
        gregtech$renderDurabilityText(fr, stack, xPosition, yPosition);
    }

    @Unique
    private void gregtech$renderDurabilityText(FontRenderer fr, ItemStack stack, int xPosition, int yPosition) {
        if (stack.isEmpty() || !stack.isItemDamaged()) return;

        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.scale(0.5F, 0.5F, 0.5F);

        // ItemStack information
        int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantments.UNBREAKING, stack);
        int maxDamage = stack.getMaxDamage();
        int damage = stack.getItemDamage();

        // Create string, position, and color
        String string = gregtech$format(((long) maxDamage - damage) * (unbreaking + 1));
        int stringWidth = fr.getStringWidth(string);
        int x = ((xPosition + 8) * 2 + 1 + stringWidth / 2 - stringWidth);

        // Raise the text a bit for electric items
        boolean isElectricItem = stack.getItem() instanceof IGTTool tool && tool.isElectric();

        int yBase = (yPosition * 2) + 18;
        int y = isElectricItem ? (yBase - 4) : yBase;

        int color = stack.getItem().getRGBDurabilityForDisplay(stack);

        // Draw string
        fr.drawStringWithShadow(string, x, y, color);

        GlStateManager.scale(2.0F, 2.0F, 2.0F);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
    }

    @Unique
    private static String gregtech$format(long number) {
        if (number >= 1_000_000_000) return DECIMAL_FORMAT.format(number / 1_000_000_000d) + "b";
        if (number >= 1_000_000) return DECIMAL_FORMAT.format(number / 1_000_000d) + "m";
        if (number >= 1_000) return DECIMAL_FORMAT.format(number / 1_000d) + "k";
        return DECIMAL_FORMAT.format(number);
    }
}
