package gregtech.integration.jei.basic;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.util.GTStringUtils;
import gregtech.api.worldgen.config.OreDepositDefinition;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.api.worldgen.filler.LayeredBlockFiller;
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

import java.util.ArrayList;
import java.util.List;

public class GTOreCategory extends BasicRecipeCategory<GTOreInfo, GTOreInfo> {

    public static final String UID = String.format("%s.ore_spawn_location", GTValues.MODID);
    private static final int SLOT_WIDTH = 18;
    private static final int SLOT_HEIGHT = 18;
    private static final int DIM_DISPLAY_PER_ROW = 7;
    private static final int LEFT_PADDING = 3;
    private static final int LINE_KEY_COLOR = 0x404040;
    private static final int LINE_VALUE_COLOR = 0x303030;
    private static final int ORE_COLUMN_X = 3;

    protected final IDrawable slot;
    protected OreDepositDefinition definition;
    protected String veinName;
    protected int minHeight;
    protected int maxHeight;
    protected int outputCount;
    protected int weight;
    private boolean isLayeredVein;
    private List<String> oreDisplayNames;
    private int[] dimensionIDs;
    private int dimDisplayCount;
    private int dimDisplayBaseYPos;
    private int dimHeaderYPos;

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

        this.veinName = recipeWrapper.getVeinName();
        this.minHeight = recipeWrapper.getMinHeight();
        this.maxHeight = recipeWrapper.getMaxHeight();
        this.outputCount = recipeWrapper.getOutputCount();
        this.weight = recipeWrapper.getWeight();
        this.definition = recipeWrapper.getDefinition();
        this.isLayeredVein = definition.getBlockFiller() instanceof LayeredBlockFiller;

        this.oreDisplayNames = new ArrayList<>();
        List<List<ItemStack>> outputStacks = ingredients.getOutputs(mezz.jei.api.ingredients.VanillaTypes.ITEM);
        for (int i = 0; i < outputCount && i < outputStacks.size(); i++) {
            List<ItemStack> group = outputStacks.get(i);
            oreDisplayNames.add(group.isEmpty() ? "" : group.get(0).getDisplayName());
        }

        this.dimensionIDs = JEIResourceDepositCategoryUtils.getAllRegisteredDimensions(
                definition.getDimensionFilter());

        int oreListBottom = baseYPos + SLOT_HEIGHT + outputCount * SLOT_HEIGHT;
        int infoY = oreListBottom + 1;
        this.dimHeaderYPos = infoY + 2 * FONT_HEIGHT + 2;
        this.dimDisplayBaseYPos = dimHeaderYPos + FONT_HEIGHT + 1;

        int dimItemCount = 0;
        for (int dimId : dimensionIDs) {
            ItemStack displayStack = OrbItems.getDisplayItem(dimId);
            if (!displayStack.isEmpty()) dimItemCount++;
        }
        this.dimDisplayCount = dimItemCount;

        itemStackGroup.init(0, true, ORE_COLUMN_X, baseYPos);
        itemStackGroup.init(1, true, ORE_COLUMN_X + SLOT_WIDTH, baseYPos);

        for (int i = 0; i < outputCount; i++) {
            int yPos = baseYPos + SLOT_HEIGHT + i * SLOT_HEIGHT;
            itemStackGroup.init(i + 2, false,
                    new ItemStackTextRenderer(recipeWrapper.getOreWeight(i) * 100, -1),
                    ORE_COLUMN_X + 1, yPos + 1, 16, 16, 0, 0);
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addTooltip);
        itemStackGroup.set(ingredients);

        int j = 0;
        for (int dimId : dimensionIDs) {
            ItemStack displayStack = OrbItems.getDisplayItem(dimId);
            if (displayStack.isEmpty()) continue;

            int slotIndex = 2 + outputCount + j;
            itemStackGroup.init(slotIndex, true,
                    LEFT_PADDING + (j % DIM_DISPLAY_PER_ROW) * SLOT_WIDTH,
                    dimDisplayBaseYPos + (j / DIM_DISPLAY_PER_ROW) * SLOT_HEIGHT);
            itemStackGroup.set(slotIndex, displayStack);
            j++;
        }
    }

    @NotNull
    @Override
    public IRecipeWrapper getRecipeWrapper(@NotNull GTOreInfo recipe) {
        return recipe;
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        int baseYPos = 19;
        int textX = ORE_COLUMN_X + 18;

        GTStringUtils.drawCenteredStringWithCutoff(veinName, minecraft.fontRenderer, 176);

        String indicatorHeader = net.minecraft.util.text.TextFormatting.UNDERLINE +
                I18n.format("gregtech.jei.ore.vein_indicator");
        minecraft.fontRenderer.drawString(indicatorHeader, ORE_COLUMN_X,
                baseYPos - FONT_HEIGHT - 1, LINE_KEY_COLOR);

        for (int i = 0; i < outputCount; i++) {
            int yPos = baseYPos + SLOT_HEIGHT + i * SLOT_HEIGHT;

            if (isLayeredVein) {
                String layerName;
                switch (i) {
                    case 0:
                        layerName = I18n.format("gregtech.jei.ore.primary_1");
                        break;
                    case 1:
                        layerName = I18n.format("gregtech.jei.ore.secondary_1");
                        break;
                    case 2:
                        layerName = I18n.format("gregtech.jei.ore.between_1");
                        break;
                    case 3:
                        layerName = I18n.format("gregtech.jei.ore.sporadic_1");
                        break;
                    default:
                        layerName = "";
                }
                minecraft.fontRenderer.drawString(layerName, textX, yPos + 1, LINE_KEY_COLOR);
                if (i < oreDisplayNames.size()) {
                    minecraft.fontRenderer.drawString(
                            oreDisplayNames.get(i),
                            textX, yPos + 11, LINE_VALUE_COLOR);
                }
            }
        }

        int infoY = baseYPos + SLOT_HEIGHT + outputCount * SLOT_HEIGHT + 1;
        minecraft.fontRenderer.drawString(
                I18n.format("gregtech.jei.ore.spawn_range", minHeight, maxHeight),
                ORE_COLUMN_X, infoY + 1, LINE_KEY_COLOR);
        minecraft.fontRenderer.drawString(
                I18n.format("gregtech.jei.ore.vein_weight", weight),
                ORE_COLUMN_X, infoY + FONT_HEIGHT + 1, LINE_KEY_COLOR);

        String dimHeader = net.minecraft.util.text.TextFormatting.UNDERLINE +
                I18n.format("gregtech.jei.ore.dimension");
        minecraft.fontRenderer.drawString(dimHeader, LEFT_PADDING, dimHeaderYPos, LINE_KEY_COLOR);

        if (dimDisplayCount == 0) {
            JEIResourceDepositCategoryUtils.drawMultiLineCommaSeparatedDimensionList(
                    WorldGenRegistry.getNamedDimensions(),
                    dimensionIDs,
                    minecraft.fontRenderer,
                    LEFT_PADDING,
                    dimDisplayBaseYPos,
                    70);
        }
    }
}
