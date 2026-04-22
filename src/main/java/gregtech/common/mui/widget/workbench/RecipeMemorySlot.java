package gregtech.common.mui.widget.workbench;

import gregtech.api.mui.GTGuiTextures;
import gregtech.client.utils.RenderUtil;
import gregtech.common.metatileentities.workbench.CraftingRecipeMemory;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerIngredientProvider;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public class RecipeMemorySlot extends Widget<RecipeMemorySlot> implements Interactable, RecipeViewerIngredientProvider {

    private final CraftingRecipeMemory memory;
    /** 静态 index（当 dynamicIndex 为 null 时使用） */
    private final int staticIndex;
    /** 动态 index 供给器（由滚动容器提供），优先于 staticIndex */
    private IntSupplier dynamicIndex;

    public RecipeMemorySlot(CraftingRecipeMemory memory, int index) {
        this.memory = memory;
        this.staticIndex = index;
        tooltipAutoUpdate(true);
        tooltipBuilder(tooltip -> {
            var recipe = memory.getRecipeAtIndex(getIndex());
            if (recipe == null) return;

            tooltip.addFromItem(recipe.getRecipeResult());

            tooltip.spaceLine(2);
            tooltip.addLine(IKey.lang("gregtech.recipe_memory_widget.tooltip.1"));
            tooltip.addLine(IKey.lang("gregtech.recipe_memory_widget.tooltip.2"));
            tooltip.addLine(IKey.lang("gregtech.recipe_memory_widget.tooltip.3"));
            tooltip.addLine(IKey.lang("gregtech.recipe_memory_widget.tooltip.0", recipe.timesUsed)
                    .style(TextFormatting.WHITE));
        });
    }

    /** 设置动态 index 供给器（由滚动容器调用） */
    public RecipeMemorySlot dynamicIndex(IntSupplier indexSupplier) {
        this.dynamicIndex = indexSupplier;
        return this;
    }

    /** 获取当前实际的 index */
    public int getIndex() {
        return dynamicIndex != null ? dynamicIndex.getAsInt() : staticIndex;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        int currentIndex = getIndex();
        ItemStack itemStack = this.memory.getRecipeOutputAtIndex(currentIndex);

        if (!itemStack.isEmpty()) {
            int cachedCount = itemStack.getCount();
            itemStack.setCount(1); // required to not render the amount overlay
            RenderUtil.drawItemStack(itemStack, 1, 1, true);
            itemStack.setCount(cachedCount);

            // noinspection DataFlowIssue
            if (this.memory.getRecipeAtIndex(currentIndex).isRecipeLocked()) {
                GlStateManager.disableDepth();
                GTGuiTextures.RECIPE_LOCK.draw(context, 10, 1, 8, 8, widgetTheme.getTheme());
                GlStateManager.enableDepth();
            }
        }

        RenderUtil.handleSlotOverlay(this, widgetTheme);
    }

    @Override
    public void drawForeground(ModularGuiContext context) {
        RichTooltip tooltip = getTooltip();
        if (tooltip != null && isHoveringFor(tooltip.getShowUpTimer())) {
            tooltip.draw(getContext(), this.memory.getRecipeOutputAtIndex(getIndex()));
        }
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        int currentIndex = getIndex();
        var recipe = memory.getRecipeAtIndex(currentIndex);
        if (recipe == null)
            return Result.IGNORE;

        var data = MouseData.create(mouseButton);
        this.memory.syncToServer(CraftingRecipeMemory.MOUSE_CLICK, buffer -> {
            buffer.writeVarInt(currentIndex);
            data.writeToPacket(buffer);
        });

        return Result.ACCEPT;
    }

    @Override
    public @Nullable Object getIngredient() {
        int currentIndex = getIndex();
        if (!this.memory.hasRecipe(currentIndex)) return null;
        return this.memory.getRecipeOutputAtIndex(currentIndex);
    }
}
