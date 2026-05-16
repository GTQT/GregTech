package gregtech.mixins.minecraft;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.metatileentity.MetaTileEntity;
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
    private static void gregTechCEu$renderDrumBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (stack.getCount() > 1) return; //忽视堆叠项目

        MetaTileEntity mte = GTUtility.getMetaTileEntity(stack);
        if (!(mte instanceof MetaTileEntityDrum drum)) return;

        FluidStack fluid = FluidUtil.getFluidContained(stack);
        if (fluid == null || fluid.amount <= 0) return;

        int tankCapacity = drum.getTankSize(stack);
        double fillRate = fluid.amount / (double) tankCapacity;

        Color color = new Color(GTUtility.convertRGBtoOpaqueRGBA_MC(RenderUtil.getFluidColor(fluid)));
        ToolChargeBarRenderer.render(fillRate, xPosition, yPosition, 0, true, color, color, false);
    }

    @Unique
    private static void gregTechCEu$renderQuantumTankBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (stack.getCount() > 1) return; //忽视堆叠项目

        MetaTileEntity mte = GTUtility.getMetaTileEntity(stack);
        if (!(mte instanceof MetaTileEntityQuantumTank tank)) return;
        if (mte instanceof MetaTileEntityCreativeTank) return;

        FluidStack fluid = FluidUtil.getFluidContained(stack);
        if (fluid == null || fluid.amount <= 0) return;

        int tankCapacity = tank.getTankSize();
        double fillRate = fluid.amount / (double) tankCapacity;

        Color color = new Color(GTUtility.convertRGBtoOpaqueRGBA_MC(RenderUtil.getFluidColor(fluid)));
        ToolChargeBarRenderer.render(fillRate, xPosition, yPosition, 0, true, color, color, false);
    }

    @Unique
    private static void gregTechCEu$renderQuantumChestBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (stack.getCount() > 1) return; //忽视堆叠项目

        MetaTileEntity mte = GTUtility.getMetaTileEntity(stack);
        if (!(mte instanceof MetaTileEntityQuantumChest chest)) return;
        if (mte instanceof MetaTileEntityCreativeChest) return;

        long itemCapacity = chest.getMaxStoredItems();
        long itemStore = chest.getStoredItemCountFromNBT(stack) + chest.getExportItemCountFromNBT(stack);
        double fillRate = itemStore / (double) itemCapacity;

        Color color = Color.BLUE;
        ToolChargeBarRenderer.render(fillRate, xPosition, yPosition, 0, true, color, color, false);
    }

    @Unique
    private static void gregTechCEu$renderElectricBar(@NotNull ItemStack stack, int xPosition, int yPosition) {
        if (stack.getItem() instanceof IGTTool) {
            ToolChargeBarRenderer.renderBarsTool((IGTTool) stack.getItem(), stack, xPosition, yPosition);
        } else if (stack.getItem() instanceof MetaItem) {
            ToolChargeBarRenderer.renderBarsItem((MetaItem<?>) stack.getItem(), stack, xPosition, yPosition);
        }
    }

    @Unique
    private static void gregTechCEu$renderLampOverlay(@NotNull ItemStack stack, int xPosition, int yPosition) {
        LampItemOverlayRenderer.OverlayType overlayType = LampItemOverlayRenderer.getOverlayType(stack);
        if (overlayType != LampItemOverlayRenderer.OverlayType.NONE) {
            LampItemOverlayRenderer.renderOverlay(overlayType, xPosition, yPosition);
        }
    }

    // The easy part of translating the item render stuff
    @Inject(method = "renderItemOverlayIntoGUI", at = @At(value = "HEAD"))
    private void renderItemOverlayIntoGUIInject(FontRenderer fr, ItemStack stack, int xPosition, int yPosition,
                                                String text, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            gregTechCEu$renderLampOverlay(stack, xPosition, yPosition);
        }
    }

    @Inject(method = "renderItemOverlayIntoGUI",
            at = @At(value = "INVOKE_ASSIGN",
                     target = "Lnet/minecraft/client/Minecraft;getMinecraft()Lnet/minecraft/client/Minecraft;",
                     shift = At.Shift.BEFORE,
                     ordinal = 0))
    public void showDurabilityBarMixin(FontRenderer fr, ItemStack stack, int xPosition, int yPosition, String text,
                                       CallbackInfo ci) {
        gregTechCEu$renderElectricBar(stack, xPosition, yPosition);
        gregTechCEu$renderDrumBar(stack, xPosition, yPosition);
        gregTechCEu$renderQuantumTankBar(stack, xPosition, yPosition);
        gregTechCEu$renderQuantumChestBar(stack, xPosition, yPosition);
    }

    @Inject(at = @At("RETURN"),
            method = "renderItemOverlayIntoGUI(Lnet/minecraft/client/gui/FontRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V")
    public void renderItemOverlayIntoGUI(FontRenderer fontRenderer, ItemStack itemStack, int x, int y, String count,
                                         CallbackInfo ci) {
        gregTech$renderDurabilityRender(fontRenderer, itemStack, x, y);
    }

    @Unique
    public void gregTech$renderDurabilityRender(FontRenderer fr, ItemStack stack, int xPosition, int yPosition) {
        if (!stack.isEmpty() && stack.isItemDamaged()) {
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
            String string = gregTech$format(((maxDamage - damage) * (unbreaking + 1)));
            int stringWidth = fr.getStringWidth(string);
            int x = ((xPosition + 8) * 2 + 1 + stringWidth / 2 - stringWidth);

            // 检测是否为电动物品
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
    }

    @Unique
    public String gregTech$format(float number) {
        DecimalFormat decimalFormat = new DecimalFormat("0.#");
        if (number >= 1000000000) return decimalFormat.format(number / 1000000000) + "b";
        if (number >= 1000000) return decimalFormat.format(number / 1000000) + "m";
        if (number >= 1000) return decimalFormat.format(number / 1000) + "k";
        return Float.toString(number).replaceAll("\\.?0*$", "");
    }
}
