package gregtech.integration.jei.basic;

import gregtech.api.GTValues;
import gregtech.api.gui.GuiTextures;
import gregtech.api.util.GTStringUtils;
import gregtech.api.worldgen.config.WorldGenRegistry;
import gregtech.common.items.OrbItems;
import gregtech.integration.jei.utils.JEIResourceDepositCategoryUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GTFluidVeinCategory extends BasicRecipeCategory<GTFluidVeinInfo, GTFluidVeinInfo> {

    public static final String UID = String.format("%s.fluid_spawn_location", GTValues.MODID);

    private static final int SLOT_CENTER = 79;
    private static final int LEFT_PADDING = 3;
    private static final int DIM_HEADER_OFFSET = 72;
    private static final int START_POS_Y = 40;
    private static final int SLOT_WIDTH = 18;
    private static final int SLOT_HEIGHT = 18;
    private static final int DIM_DISPLAY_PER_ROW = 7;
    private static final int LINE_KEY_COLOR = 0x404040;
    private static final int LINE_VALUE_COLOR = 0x303030;

    protected final IDrawable slot;
    private String veinName;
    private int weight;
    private int[] yields;
    private int depletionAmount;
    private int depletionChance;
    private int depletedYield;
    private int[] dimensionIDs;
    private int weightLength;
    private int minYieldLength;
    private int maxYieldLength;
    private int depletionChanceLength;
    private int depletionAmountLength;
    private int depletedYieldLength;
    private int dimDisplayCount;
    private int dimDisplayBaseYPos;
    private int dimHeaderYPos;

    public GTFluidVeinCategory(IGuiHelper guiHelper) {
        super("fluid_spawn_location",
                "fluid.spawnlocation.name",
                guiHelper.createBlankDrawable(176, 166),
                guiHelper);

        this.slot = guiHelper.drawableBuilder(GuiTextures.SLOT.imageLocation, 0, 0, 18, 18).setTextureSize(18, 18)
                .build();
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayout recipeLayout, GTFluidVeinInfo gtFluidVeinInfo,
                          @NotNull IIngredients ingredients) {
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();

        fluidStackGroup.init(0, true, SLOT_CENTER, 19, 16, 16, 1, false, null);

        fluidStackGroup.addTooltipCallback(gtFluidVeinInfo::addTooltip);
        fluidStackGroup.set(ingredients);

        this.veinName = gtFluidVeinInfo.getName();
        this.weight = gtFluidVeinInfo.getWeight();
        this.yields = gtFluidVeinInfo.getYields();
        this.depletionAmount = gtFluidVeinInfo.getDepletionAmount();
        this.depletionChance = gtFluidVeinInfo.getDepletionChance();
        this.depletedYield = gtFluidVeinInfo.getDepletedYield();

        this.dimensionIDs = JEIResourceDepositCategoryUtils.getAllRegisteredDimensions(
                gtFluidVeinInfo.getDefinition().getDimensionFilter());

        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        int dimSlotStartIndex = 1;
        this.dimHeaderYPos = START_POS_Y + 6 * FONT_HEIGHT + 1;
        this.dimDisplayBaseYPos = dimHeaderYPos + FONT_HEIGHT + 2;

        int j = 0;
        for (int dimId : dimensionIDs) {
            ItemStack displayStack = OrbItems.getDisplayItem(dimId);
            if (displayStack.isEmpty()) continue;

            itemStackGroup.init(dimSlotStartIndex + j, true,
                    LEFT_PADDING + (j % DIM_DISPLAY_PER_ROW) * SLOT_WIDTH,
                    dimDisplayBaseYPos + (j / DIM_DISPLAY_PER_ROW) * SLOT_HEIGHT);
            itemStackGroup.set(dimSlotStartIndex + j, displayStack);
            j++;
        }
        this.dimDisplayCount = j;
    }

    @NotNull
    @Override
    public IRecipeWrapper getRecipeWrapper(@NotNull GTFluidVeinInfo gtFluidVeinInfo) {
        return gtFluidVeinInfo;
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        GTStringUtils.drawCenteredStringWithCutoff(veinName, minecraft.fontRenderer, 176);

        this.slot.draw(minecraft, SLOT_CENTER - 1, 18);

        // Vein Weight information
        String veinWeight = I18n.format("gregtech.jei.fluid.vein_weight", weight);
        weightLength = minecraft.fontRenderer.getStringWidth(veinWeight);
        minecraft.fontRenderer.drawString(veinWeight, LEFT_PADDING, START_POS_Y, LINE_KEY_COLOR);

        String veinMinYield = I18n.format("gregtech.jei.fluid.min_yield", yields[0]);
        minYieldLength = minecraft.fontRenderer.getStringWidth(veinMinYield);
        minecraft.fontRenderer.drawString(veinMinYield, LEFT_PADDING, START_POS_Y + FONT_HEIGHT + 1, LINE_KEY_COLOR);

        String veinMaxYield = I18n.format("gregtech.jei.fluid.max_yield", yields[1]);
        maxYieldLength = minecraft.fontRenderer.getStringWidth(veinMaxYield);
        minecraft.fontRenderer.drawString(veinMaxYield, LEFT_PADDING, START_POS_Y + 2 * FONT_HEIGHT + 1, LINE_KEY_COLOR);

        String veinDepletionChance = I18n.format("gregtech.jei.fluid.depletion_chance", depletionChance);
        depletionChanceLength = minecraft.fontRenderer.getStringWidth(veinDepletionChance);
        minecraft.fontRenderer.drawString(veinDepletionChance, LEFT_PADDING, START_POS_Y + 3 * FONT_HEIGHT + 1,
                LINE_KEY_COLOR);

        String veinDepletionAmount = I18n.format("gregtech.jei.fluid.depletion_amount", depletionAmount);
        depletionAmountLength = minecraft.fontRenderer.getStringWidth(veinDepletionAmount);
        minecraft.fontRenderer.drawString(veinDepletionAmount, LEFT_PADDING, START_POS_Y + 4 * FONT_HEIGHT + 1,
                LINE_KEY_COLOR);

        String veinDepletedYield = I18n.format("gregtech.jei.fluid.depleted_rate", depletedYield);
        depletedYieldLength = minecraft.fontRenderer.getStringWidth(veinDepletedYield);
        minecraft.fontRenderer.drawString(veinDepletedYield, LEFT_PADDING, START_POS_Y + 5 * FONT_HEIGHT + 1,
                LINE_KEY_COLOR);

        String dimHeader = net.minecraft.util.text.TextFormatting.UNDERLINE +
                I18n.format("gregtech.jei.fluid.dimension");
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

    @NotNull
    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        if (isPointWithinRange(LEFT_PADDING, START_POS_Y, weightLength, FONT_HEIGHT, mouseX, mouseY)) {
            return Collections.singletonList(I18n.format("gregtech.jei.fluid.weight_hover"));
        } else if (isPointWithinRange(LEFT_PADDING, START_POS_Y + FONT_HEIGHT + 1, minYieldLength, FONT_HEIGHT + 1,
                mouseX, mouseY)) {
                    return Collections.singletonList(I18n.format("gregtech.jei.fluid.min_hover"));
                } else
            if (isPointWithinRange(LEFT_PADDING, START_POS_Y + 2 * FONT_HEIGHT + 1, maxYieldLength, FONT_HEIGHT + 1,
                    mouseX, mouseY)) {
                        return Collections.singletonList(I18n.format("gregtech.jei.fluid.max_hover"));
                    } else
                if (isPointWithinRange(LEFT_PADDING, START_POS_Y + 3 * FONT_HEIGHT + 1, depletionChanceLength,
                        FONT_HEIGHT + 1, mouseX, mouseY)) {
                            return Collections.singletonList(I18n.format("gregtech.jei.fluid.dep_chance_hover"));
                        } else
                    if (isPointWithinRange(LEFT_PADDING, START_POS_Y + 4 * FONT_HEIGHT + 1, depletionAmountLength,
                            FONT_HEIGHT + 1, mouseX, mouseY)) {
                                return Collections.singletonList(I18n.format("gregtech.jei.fluid.dep_amount_hover"));
                            } else
                        if (isPointWithinRange(LEFT_PADDING, START_POS_Y + 5 * FONT_HEIGHT + 1, depletedYieldLength,
                                FONT_HEIGHT + 1, mouseX, mouseY)) {
                                    return Collections.singletonList(
                                            I18n.format("gregtech.jei.fluid.dep_yield_hover"));
                                }

        return Collections.emptyList();
    }

    private static boolean isPointWithinRange(int initialX, int initialY, int width, int height, int pointX,
                                              int pointY) {
        return initialX <= pointX && pointX <= initialX + width && initialY <= pointY && pointY <= initialY + height;
    }
}
