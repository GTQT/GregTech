package gregtech.integration.jei.recipe;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.gui.BlankUIHolder;
import gregtech.api.gui.IRenderContext;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.Widget;
import gregtech.api.gui.widgets.ProgressWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.gui.widgets.TankWidget;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTUtility;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.ConfigHolder;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.utils.render.FluidStackTextRenderer;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RecipeMapCategory implements IRecipeCategory<GTRecipeWrapper> {

    private final RecipeMap<?> recipeMap;
    private final GTRecipeCategory category;
    private final ModularUI modularUI;
    private final ItemStackHandler importItems, exportItems;
    private final FluidTankList importFluids, exportFluids;
    private final IDrawable backgroundDrawable;
    private Object iconIngredient;
    private IDrawable icon;

    private static final Map<GTRecipeCategory, RecipeMapCategory> gtCategories = new Object2ObjectOpenHashMap<>();
    /**
     * Voltage tier of the recipe page currently being laid out, captured in `setRecipe` because
     * `drawExtras` only receives the client and cannot resolve the recipe itself.
     */
    private int pageVoltageTier = -1;
    private static final Map<RecipeMap<?>, List<RecipeMapCategory>> recipeMapCategories = new Object2ObjectOpenHashMap<>();

    public RecipeMapCategory(@NotNull RecipeMap<?> recipeMap, @NotNull GTRecipeCategory category,
                             IGuiHelper guiHelper) {
        this.recipeMap = recipeMap;
        this.category = category;
        FluidTank[] importFluidTanks = new FluidTank[recipeMap.getMaxFluidInputs()];
        for (int i = 0; i < importFluidTanks.length; i++)
            importFluidTanks[i] = new FluidTank(16000);
        FluidTank[] exportFluidTanks = new FluidTank[recipeMap.getMaxFluidOutputs()];
        for (int i = 0; i < exportFluidTanks.length; i++)
            exportFluidTanks[i] = new FluidTank(16000);
        this.modularUI = recipeMap.getRecipeMapUI().createJeiUITemplate(
                (importItems = new ItemStackHandler(
                        recipeMap.getMaxInputs() + recipeMap.getExtraInput())),
                (exportItems = new ItemStackHandler(recipeMap.getMaxOutputs())),
                (importFluids = new FluidTankList(false, importFluidTanks)),
                (exportFluids = new FluidTankList(false, exportFluidTanks)), 0)
                .build(new BlankUIHolder(), Minecraft.getMinecraft().player);
        this.modularUI.initWidgets();
        this.backgroundDrawable = guiHelper.createBlankDrawable(modularUI.getWidth(),
                modularUI.getHeight() * 2 / 3 + recipeMap.getRecipeMapUI().getPropertyHeightShift());
        gtCategories.put(category, this);
        recipeMapCategories.compute(recipeMap, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(this);
            return v;
        });
    }

    @Override
    @NotNull
    public String getUid() {
        return category.getUniqueID();
    }

    @Override
    @NotNull
    public String getTitle() {
        return LocalizationUtils.format(category.getTranslationKey());
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        if (icon != null) {
            return icon;
        } else if (iconIngredient instanceof IDrawable drawable) {
            return icon = drawable;
        } else if (iconIngredient != null) {
            // cache the icon drawable for less gc pressure
            return icon = JustEnoughItemsModule.guiHelper.createDrawableIngredient(iconIngredient);
        }
        // JEI will automatically populate the icon as the first registered catalyst if null
        return null;
    }

    public void setIcon(Object icon) {
        if (iconIngredient == null) {
            iconIngredient = icon;
        }
    }

    @Override
    @NotNull
    public String getModName() {
        return GTValues.MODID;
    }

    @Override
    @NotNull
    public IDrawable getBackground() {
        return backgroundDrawable;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @NotNull GTRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        pageVoltageTier = voltageTierOf(recipeWrapper);
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();
        for (Widget uiWidget : modularUI.guiWidgets.values()) {

            if (uiWidget instanceof SlotWidget) {
                SlotWidget slotWidget = (SlotWidget) uiWidget;
                if (!(slotWidget.getHandle() instanceof SlotItemHandler)) {
                    continue;
                }
                SlotItemHandler handle = (SlotItemHandler) slotWidget.getHandle();
                if (handle.getItemHandler() == importItems) {
                    // this is input item stack slot widget, so add it to item group
                    itemStackGroup.init(handle.getSlotIndex(), true,
                            new ItemStackTextRenderer(recipeWrapper.isNotConsumedItem(handle.getSlotIndex())),
                            slotWidget.getPosition().x + 1,
                            slotWidget.getPosition().y + 1,
                            slotWidget.getSize().width - 2,
                            slotWidget.getSize().height - 2, 0, 0);
                } else if (handle.getItemHandler() == exportItems) {
                    // this is output item stack slot widget, so add it to item group
                    itemStackGroup.init(importItems.getSlots() + handle.getSlotIndex(), false,
                            new ItemStackTextRenderer(
                                    recipeWrapper.getOutputChance(
                                            handle.getSlotIndex() - recipeWrapper.getRecipe().getOutputs().size()),
                                    recipeWrapper.getChancedOutputLogic()),
                            slotWidget.getPosition().x + 1,
                            slotWidget.getPosition().y + 1,
                            slotWidget.getSize().width - 2,
                            slotWidget.getSize().height - 2, 0, 0);
                }
            } else if (uiWidget instanceof TankWidget) {
                TankWidget tankWidget = (TankWidget) uiWidget;
                if (importFluids.getFluidTanks().contains(tankWidget.fluidTank)) {
                    int importIndex = importFluids.getFluidTanks().indexOf(tankWidget.fluidTank);
                    List<List<FluidStack>> inputsList = ingredients.getInputs(VanillaTypes.FLUID);
                    int fluidAmount = 0;
                    if (inputsList.size() > importIndex && !inputsList.get(importIndex).isEmpty())
                        fluidAmount = inputsList.get(importIndex).get(0).amount;
                    // this is input tank widget, so add it to fluid group
                    fluidStackGroup.init(importIndex, true,
                            new FluidStackTextRenderer(fluidAmount, false,
                                    tankWidget.getSize().width - (2 * tankWidget.fluidRenderOffset),
                                    tankWidget.getSize().height - (2 * tankWidget.fluidRenderOffset), null)
                                            .setNotConsumed(recipeWrapper.isNotConsumedFluid(importIndex)),
                            tankWidget.getPosition().x + tankWidget.fluidRenderOffset,
                            tankWidget.getPosition().y + tankWidget.fluidRenderOffset,
                            tankWidget.getSize().width - (2 * tankWidget.fluidRenderOffset),
                            tankWidget.getSize().height - (2 * tankWidget.fluidRenderOffset), 0, 0);

                } else if (exportFluids.getFluidTanks().contains(tankWidget.fluidTank)) {
                    int exportIndex = exportFluids.getFluidTanks().indexOf(tankWidget.fluidTank);
                    List<List<FluidStack>> inputsList = ingredients.getOutputs(VanillaTypes.FLUID);
                    int fluidAmount = 0;
                    if (inputsList.size() > exportIndex && !inputsList.get(exportIndex).isEmpty())
                        fluidAmount = inputsList.get(exportIndex).get(0).amount;
                    // this is output tank widget, so add it to fluid group
                    fluidStackGroup.init(importFluids.getFluidTanks().size() + exportIndex, false,
                            new FluidStackTextRenderer(fluidAmount, false,
                                    tankWidget.getSize().width - (2 * tankWidget.fluidRenderOffset),
                                    tankWidget.getSize().height - (2 * tankWidget.fluidRenderOffset), null,
                                    recipeWrapper.getFluidOutputChance(
                                            exportIndex - recipeWrapper.getRecipe().getFluidOutputs().size()),
                                    recipeWrapper.getChancedFluidOutputLogic()),
                            tankWidget.getPosition().x + tankWidget.fluidRenderOffset,
                            tankWidget.getPosition().y + tankWidget.fluidRenderOffset,
                            tankWidget.getSize().width - (2 * tankWidget.fluidRenderOffset),
                            tankWidget.getSize().height - (2 * tankWidget.fluidRenderOffset), 0, 0);

                }
            }
        }

        if (ConfigHolder.machines.enableResearch && this.recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES) {
            ResearchPropertyData data = recipeWrapper.getRecipe().getProperty(ResearchProperty.getInstance(), null);
            if (data != null) {
                List<ItemStack> dataItems = new ArrayList<>();
                for (ResearchPropertyData.ResearchEntry entry : data) {
                    ItemStack dataStick = entry.dataItem().copy();
                    AssemblyLineManager.writeResearchToNBT(GTUtility.getOrCreateNbtCompound(dataStick),
                            entry.researchId());
                    dataItems.add(dataStick);
                }
                itemStackGroup.set(16, dataItems);
            }
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addItemTooltip);
        fluidStackGroup.addTooltipCallback(recipeWrapper::addFluidTooltip);
        itemStackGroup.set(ingredients);
        fluidStackGroup.set(ingredients);
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        for (Widget widget : modularUI.guiWidgets.values()) {
            if (widget instanceof ProgressWidget) widget.detectAndSendChanges();
            widget.drawInBackground(0, 0, minecraft.getRenderPartialTicks(), new IRenderContext() {});
            widget.drawInForeground(0, 0);
        }
        drawVoltageFrame();
    }

    /**
     * Redraws the visible outline of JEI's recipe frame in the recipe's voltage tier colour.
     *
     * <p>
     * JEI's frame is a nine slice texture: besides its outline it also tiles its middle area across the whole
     * rectangle, and that fill <em>is</em> the recipe page background, because this category's background is a
     * blank drawable ({@code guiHelper.createBlankDrawable(...)}) which paints nothing. Tinting the whole nine
     * slice would therefore tint the page itself, so only the outline is repainted here and the corner notches
     * are deliberately left alone.
     *
     * <p>
     * The visible line is two pixels wide (a one pixel outline followed by a one pixel highlight, {@code #D8D8D8}
     * and {@code #B3B3B3} on the bottom), offset three pixels outwards from the page edge so it floats inside
     * JEI's four pixel padding band (JEI's {@code RECIPE_BORDER_PADDING}). Vertex colours are used because
     * {@code Gui.drawRect} assigns its own colour through the same state and would override any tint set
     * beforehand.
     */
    private void drawVoltageFrame() {
        int tier = pageVoltageTier;
        if (tier < 0 || tier >= GTValues.VC.length) {
            return;
        }
        int width = backgroundDrawable.getWidth();
        int height = backgroundDrawable.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int baseColor = GTValues.TIER_COLORS[tier];
        int outline = shade(baseColor, 0x99);
        int highlight = shade(baseColor, 0xD8);
        int highlightBottom = shade(baseColor, 0xB3);
        // Outline: one pixel, three pixels out from the page. Corner notches stay untouched.
        drawSolidRect(-4, -4, width + 4, -3, outline);
        drawSolidRect(-4, height + 3, width + 4, height + 4, outline);
        drawSolidRect(-4, -3, -3, height + 3, outline);
        drawSolidRect(width + 3, -3, width + 4, height + 3, outline);
        // Highlight: one pixel, just inside the outline.
        drawSolidRect(-3, -3, width + 3, -2, highlight);
        drawSolidRect(-3, height + 2, width + 3, height + 3, highlightBottom);
        drawSolidRect(-3, -2, -2, height + 2, highlight);
        drawSolidRect(width + 2, -2, width + 3, height + 2, highlight);
    }

    /**
     * @return the tier colour multiplied by one channel of a grey sample, which is how JEI's frame shading and
     *         the tier colour combine
     */
    private static int shade(int base, int grey) {
        int red = ((base >> 16) & 0xFF) * grey / 0xFF;
        int green = ((base >> 8) & 0xFF) * grey / 0xFF;
        int blue = (base & 0xFF) * grey / 0xFF;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * Fills a rectangle with a solid ARGB colour. Vertex colours are used because {@code Gui.drawRect}
     * assigns the colour through {@code GlStateManager.color}, which would override any tint set beforehand.
     */
    private static void drawSolidRect(int left, int top, int right, int bottom, int color) {
        if (right <= left || bottom <= top) {
            return;
        }
        float alpha = (color >>> 24) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        GlStateManager.disableLighting();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(right, top, 0.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(left, top, 0.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(left, bottom, 0.0D).color(red, green, blue, alpha).endVertex();
        buffer.pos(right, bottom, 0.0D).color(red, green, blue, alpha).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * @return the voltage tier index of the recipe, matching {@link GTValues#VC}
     */
    private static int voltageTierOf(@NotNull GTRecipeWrapper recipeWrapper) {
        return GTUtility.getTierByVoltage(recipeWrapper.getRecipe().getEUt());
    }

    @Nullable
    public static RecipeMapCategory getCategoryFor(@NotNull GTRecipeCategory category) {
        return gtCategories.get(category);
    }

    @Nullable
    public static Collection<RecipeMapCategory> getCategoriesFor(@NotNull RecipeMap<?> recipeMap) {
        return recipeMapCategories.get(recipeMap);
    }
}
