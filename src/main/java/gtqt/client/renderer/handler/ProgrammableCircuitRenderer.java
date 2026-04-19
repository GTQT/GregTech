package gtqt.client.renderer.handler;

import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.item.IItemRenderer;
import codechicken.lib.util.TransformUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@SideOnly(Side.CLIENT)
public class ProgrammableCircuitRenderer implements IItemRenderer {

    private final IBakedModel baseModel;

    public ProgrammableCircuitRenderer(@NotNull IBakedModel baseModel) {
        this.baseModel = baseModel;
    }

    @Override
    public void renderItem(@NotNull ItemStack stack, @NotNull ItemCameraTransforms.TransformType transformType) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.5F, 0.5F, 0.5F);
        renderWrappedItem(stack, renderItem);
        renderItem.renderItem(stack, baseModel);
        GlStateManager.popMatrix();
    }

    private static void renderWrappedItem(@NotNull ItemStack stack, @NotNull RenderItem renderItem) {
        Optional<ItemStack> wrappedOpt = ProgrammableCircuit.getWrappedItem(stack);
        if (!wrappedOpt.isPresent()) return;

        ItemStack wrapped = wrappedOpt.get();
        if (wrapped.isEmpty()) return;
        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null && GTQTMetaItems.PROGRAMMABLE_CIRCUIT.isItemEqual(wrapped)) {
            return;
        }

        IBakedModel wrappedModel = renderItem.getItemModelWithOverrides(wrapped, null, null);
        GlStateManager.pushMatrix();
        GlStateManager.scale(0.82F, 0.82F, 0.82F);
        GlStateManager.translate(0.0F, 0.0F, -0.001F);
        renderItem.renderItem(wrapped, wrappedModel);
        GlStateManager.popMatrix();
    }

    @Override
    public IModelState getTransforms() {
        return TransformUtils.DEFAULT_ITEM;
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }
}
