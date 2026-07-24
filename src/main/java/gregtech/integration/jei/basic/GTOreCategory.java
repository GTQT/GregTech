package gregtech.integration.jei.basic;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.util.GTStringUtils;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.common.items.OrbItems;
import gregtech.integration.jei.utils.JEIResourceDepositCategoryUtils;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import org.jetbrains.annotations.NotNull;

public class GTOreCategory extends BasicRecipeCategory<GTOreInfo, GTOreInfo> {

    public static final String UID = String.format("%s.ore_spawn_location", GTValues.MODID);
    private static final int NUM_OF_SLOTS = 5;
    private static final int SLOT_WIDTH = 18;
    private static final int SLOT_HEIGHT = 18;
    private static final int DIM_DISPLAY_PER_ROW = 7;

    protected final IDrawable slot;
    protected OreDepositDefinition definition;
    protected String veinName;
    protected int minHeight;
    protected int maxHeight;
    protected int outputCount;
    protected int weight;
    private int[] dimensionIDs;
    private int dimDisplayCount;
    private int dimDisplayBaseYPos;

    public GTOreCategory(IGuiHelper guiHelper) {
        super("ore_spawn_location",
                "ore.spawnlocation.name",
                guiHelper.createBlankDrawable(176, 166),
                guiHelper);

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, 18, 18).setTextureSize(18, 18)
                .build();
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, GTOreInfo recipeWrapper, @NotNull IIngredients ingredients) {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        int baseYPos = 19;

        // The ore selected from JEI
        itemStackGroup.init(0, true, 22, baseYPos);
        // The Surface Identifier
        itemStackGroup.init(1, true, 22, 73);

        for (int i = 0; i < recipeWrapper.getOutputCount(); i++) {
            int yPos = baseYPos + (i / NUM_OF_SLOTS) * SLOT_HEIGHT;
            int xPos = 70 + (i % NUM_OF_SLOTS) * SLOT_WIDTH;

            itemStackGroup.init(i + 2, false,
                    new ItemStackTextRenderer(recipeWrapper.getOreWeight(i) * 100, -1),
                    xPos + 1, yPos + 1, 16, 16, 0, 0);
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addTooltip);
        itemStackGroup.set(ingredients);
        this.veinName = recipeWrapper.getVeinName();
        this.minHeight = recipeWrapper.getMinHeight();
        this.maxHeight = recipeWrapper.getMaxHeight();
        this.outputCount = recipeWrapper.getOutputCount();
        this.weight = recipeWrapper.getWeight();
        this.definition = recipeWrapper.getDefinition();

        this.dimensionIDs = JEIResourceDepositCategoryUtils.getAllRegisteredDimensions(
                definition.getDimensionFilter());

        // ========== Dimension display item slots ==========
        this.dimDisplayCount = 0;
        int outputRows = outputCount == 0 ? 0 : ((outputCount - 1) / NUM_OF_SLOTS) + 1;
        int yAfterOres = 19 + outputRows * SLOT_HEIGHT;
        int textBaseY = Math.max(yAfterOres, SLOT_HEIGHT * (NUM_OF_SLOTS - 1) + 1); // matches drawExtras logic
        this.dimDisplayBaseYPos = textBaseY + 3 * FONT_HEIGHT;

        int j = 0;
        for (int dimId : dimensionIDs) {
            ItemStack displayStack = OrbItems.getDisplayItem(dimId);
            if (displayStack.isEmpty()) continue;

            int slotIndex = 2 + outputCount + j;
            itemStackGroup.init(slotIndex, true,
                    22 + (j % DIM_DISPLAY_PER_ROW) * SLOT_WIDTH,
                    dimDisplayBaseYPos + (j / DIM_DISPLAY_PER_ROW) * SLOT_HEIGHT);
            itemStackGroup.set(slotIndex, displayStack);
            j++;
        }
        this.dimDisplayCount = j;
    }

    @NotNull
    @Override
    public IRecipeWrapper getRecipeWrapper(@NotNull GTOreInfo recipe) {
        return recipe;
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        int baseXPos = 70;
        int baseYPos = 19;

        // Selected Ore
        this.slot.draw(minecraft, 22, baseYPos);
        // Surface Identifier
        this.slot.draw(minecraft, 22, SLOT_HEIGHT * (NUM_OF_SLOTS - 1) + 1);

        int yPos = 0;
        for (int i = 0; i < outputCount; i++) {
            yPos = baseYPos + (i / NUM_OF_SLOTS) * SLOT_HEIGHT;
            int xPos = baseXPos + (i % NUM_OF_SLOTS) * SLOT_WIDTH;

            this.slot.draw(minecraft, xPos, yPos);
        }

        // base positions set to position of last rendered slot for later use.
        baseYPos = yPos + SLOT_HEIGHT;

        GTStringUtils.drawCenteredStringWithCutoff(veinName, minecraft.fontRenderer, 176);

        // Begin Drawing information
        if (baseYPos >= SLOT_HEIGHT * NUM_OF_SLOTS) {
            minecraft.fontRenderer.drawString(I18n.format("gregtech.jei.ore.spawn_range", minHeight, maxHeight),
                    baseXPos, baseYPos + 1, 0x111111);
        } else {
            minecraft.fontRenderer.drawString(I18n.format("gregtech.jei.ore.spawn_range", minHeight, maxHeight),
                    baseXPos, SLOT_HEIGHT * (NUM_OF_SLOTS - 1) + 1, 0x111111);
            baseYPos = 73;
        }

        // Weight
        minecraft.fontRenderer.drawString(I18n.format("gregtech.jei.ore.vein_weight", weight), baseXPos,
                baseYPos + FONT_HEIGHT, 0x111111);

        // Dimensions label
        minecraft.fontRenderer.drawString(I18n.format("gregtech.jei.ore.dimension"), baseXPos,
                baseYPos + 2 * FONT_HEIGHT, 0x111111);

        // Fallback: text-based dimension list when no display icons are registered
        if (dimDisplayCount == 0) {
            JEIResourceDepositCategoryUtils.drawMultiLineCommaSeparatedDimensionList(
                    WorldGenRegistry.getNamedDimensions(),
                    dimensionIDs,
                    minecraft.fontRenderer,
                    baseXPos,
                    baseYPos + 3 * FONT_HEIGHT,
                    70);
        }

        // Dimension display item slot backgrounds
        for (int j = 0; j < dimDisplayCount; j++) {
            int slotX = 22 + (j % DIM_DISPLAY_PER_ROW) * SLOT_WIDTH;
            int slotY = dimDisplayBaseYPos + (j / DIM_DISPLAY_PER_ROW) * SLOT_HEIGHT;
            this.slot.draw(minecraft, slotX, slotY);
        }

        // Surface Identifier label
        minecraft.fontRenderer.drawSplitString(I18n.format("gregtech.jei.ore.surfacematerial"), 15, 92, 50, 0x111111);
    }
}
