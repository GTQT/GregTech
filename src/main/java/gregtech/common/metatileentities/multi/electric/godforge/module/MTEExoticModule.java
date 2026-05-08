package gregtech.common.metatileentities.multi.electric.godforge.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.GTRecipeHandler;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;
import gregtech.common.mui.multiblock.godforge.MTEExoticModuleGui;
import gregtech.loaders.recipe.GodforgeRecipeLoader;

/**
 * Godforge Exotic Module.
 * Generates random recipes at runtime:
 * - Normal mode: random plasma inputs → Quark-Gluon Plasma
 * - Magmatter mode: random plasma inputs + MHDCSM → MagMatter
 * Recipe inputs change periodically (every 1000 ticks / 50 seconds).
 */
public class MTEExoticModule extends MTEBaseModule {

    // Recipe refresh interval in ticks
    private static final int RECIPE_REFRESH_INTERVAL = 1000;
    // Number of plasma inputs for exotic recipe
    private static final int QUARK_GLUON_INPUT_COUNT = 3;
    // Number of plasma inputs for magmatter recipe
    private static final int MAGMATTER_INPUT_COUNT = 4;
    // Output amount in mB
    private static final int QUARK_GLUON_OUTPUT_MB = 1000;
    private static final int MAGMATTER_OUTPUT_MB = 144;

    private boolean magmatterMode;
    private long ticker;
    private final List<ItemStack> exoticInputs = new ArrayList<>();
    private final List<ItemStack> possibleInputs = new ArrayList<>();
    private final Random random = new Random();

    public MTEExoticModule(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GodforgeRecipeMaps.GODFORGE_EXOTIC_MATTER_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MTEExoticModule(metaTileEntityId);
    }

    @Override
    protected MTEBaseModuleGui<?> createModuleGui() {
        return new MTEExoticModuleGui(this);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    // ==================== Dynamic Recipe Generation (C4) ====================

    /**
     * Refreshes the current recipe by randomly selecting plasma inputs from all available plasma materials.
     * In normal mode: produces Quark-Gluon Plasma.
     * In magmatter mode: produces MagMatter (requires additional MHDCSM fluid input).
     */
    public void refreshRecipe() {
        exoticInputs.clear();

        List<Material> available = GodforgeRecipeLoader.plasmaMaterials;
        if (available.isEmpty()) return;

        int inputCount = magmatterMode ? MAGMATTER_INPUT_COUNT : QUARK_GLUON_INPUT_COUNT;
        inputCount = Math.min(inputCount, available.size());

        // Select random unique plasma materials as dust inputs
        List<Material> shuffled = new ArrayList<>(available);
        Collections.shuffle(shuffled, random);
        List<Material> selected = shuffled.subList(0, inputCount);

        for (Material mat : selected) {
            exoticInputs.add(OrePrefix.dust.getItemForm(mat, 1));
        }

        // Build the recipe dynamically
        GTRecipeHandler.removeAllRecipes(GodforgeRecipeMaps.GODFORGE_EXOTIC_MATTER_RECIPES);

        var builder = GodforgeRecipeMaps.GODFORGE_EXOTIC_MATTER_RECIPES.recipeBuilder();
        for (Material mat : selected) {
            builder.input(OrePrefix.dust, mat);
        }

        if (magmatterMode) {
            // Magmatter mode requires MHDCSM fluid input
            FluidStack mhdcsm = Materials.MagnetoHydrodynamicallyConstrainedStarMatter.getFluid(144);
            if (mhdcsm != null) {
                builder.fluidInputs(mhdcsm);
            }
            builder.fluidOutputs(Materials.MagMatter.getFluid(MAGMATTER_OUTPUT_MB));
        } else {
            builder.fluidOutputs(Materials.QuarkGluonPlasma.getPlasma(QUARK_GLUON_OUTPUT_MB));
        }

        builder.duration(10);
        builder.EUt(Integer.MAX_VALUE);
        builder.buildAndRegister();

        // Update possible inputs for GUI display
        updatePossibleInputs();
    }

    /**
     * Updates the list of all possible inputs for GUI display purposes.
     */
    private void updatePossibleInputs() {
        possibleInputs.clear();
        for (Material mat : GodforgeRecipeLoader.plasmaMaterials) {
            ItemStack dust = OrePrefix.dust.getItemForm(mat, 1);
            if (!dust.isEmpty()) {
                possibleInputs.add(dust);
            }
        }
    }

    @Override
    protected void updateFormedValid() {
        super.updateFormedValid();
        if (!getWorld().isRemote) {
            ticker++;
            if (ticker % RECIPE_REFRESH_INTERVAL == 0) {
                refreshRecipe();
            }
        }
    }

    // ==================== Mode ====================

    public boolean isMagmatterModeOn() {
        return magmatterMode;
    }

    public void setMagmatterMode(boolean magmatterMode) {
        this.magmatterMode = magmatterMode;
        refreshRecipe();
    }

    public long getTicker() {
        return ticker;
    }

    public void setTicker(long ticker) {
        this.ticker = ticker;
    }

    // ==================== GUI Sync ====================

    public GenericListSyncHandler<ItemStack> getInputsSyncer() {
        return createItemStackListSyncer(exoticInputs);
    }

    public GenericListSyncHandler<ItemStack> getPossibleInputsSyncer() {
        return createItemStackListSyncer(possibleInputs);
    }

    private static GenericListSyncHandler<ItemStack> createItemStackListSyncer(List<ItemStack> source) {
        return GenericListSyncHandler.<ItemStack>builder()
            .getter(() -> source)
            .adapter(ByteBufAdapters.ITEM_STACK)
            .copy(stack -> stack == null ? ItemStack.EMPTY : stack.copy())
            .build();
    }

    public static class ExoticInputSlot extends ModularSlot {

        public ExoticInputSlot(int index, ItemStack stack) {
            super(new DisplayItemStackHandler(stack), 0);
            accessibility(false, false);
        }
    }

    public static class ExoticPossibleInputSlot extends ModularSlot {

        public ExoticPossibleInputSlot(int index, ItemStack stack) {
            super(new DisplayItemStackHandler(stack), 0);
            accessibility(false, false);
        }
    }

    private static final class DisplayItemStackHandler extends ItemStackHandler {

        private DisplayItemStackHandler(ItemStack stack) {
            super(1);
            setStackInSlot(0, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }
}
