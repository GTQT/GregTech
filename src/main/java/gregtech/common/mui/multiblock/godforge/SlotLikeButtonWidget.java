package gregtech.common.mui.multiblock.godforge;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.SlotTheme;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import net.minecraft.client.renderer.GlStateManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;

public class SlotLikeButtonWidget extends ButtonWidget<SlotLikeButtonWidget> implements RecipeViewerIngredientProvider {

    private final Supplier<ItemStack> itemSupplier;
    private final IDrawable itemDrawable;

    public SlotLikeButtonWidget(ItemStack itemStack) {
        this.itemSupplier = () -> itemStack;
        this.itemDrawable = new ItemDrawable(itemStack).asIcon();
        disableHoverBackground();
    }

    public SlotLikeButtonWidget(Supplier<ItemStack> itemSupplier) {
        this.itemSupplier = itemSupplier;
        this.itemDrawable = new DynamicDrawable(() -> new ItemDrawable(itemSupplier.get()).asIcon());
        disableHoverBackground();
    }

    @Override
    public WidgetThemeEntry<?> getWidgetThemeInternal(ITheme theme) {
        return theme.getItemSlotTheme();
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetThemeEntry) {
        super.draw(context, widgetThemeEntry);
        itemDrawable.drawAtZero(context, getArea(), widgetThemeEntry.getTheme());
        if (isHovering()) {
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.colorMask(true, true, true, false);
            GuiDraw.drawRect(1, 1, 16, 16, getHoverColor(widgetThemeEntry.getTheme()));
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.disableBlend();
        }
    }

    private int getHoverColor(WidgetTheme widgetTheme) {
        if (widgetTheme instanceof SlotTheme slotTheme) {
            return slotTheme.getSlotHoverColor();
        }
        return ITheme.getDefault()
            .getItemSlotTheme()
            .getTheme()
            .getSlotHoverColor();
    }

    @Override
    public @Nullable Object getIngredient() {
        return itemSupplier.get();
    }
}
