package gregicality.multiblocks.api.recipes.alloyblast;

import gregicality.multiblocks.api.fluids.GCYMFluidStorageKeys;
import gregicality.multiblocks.api.recipes.GCYMRecipeMaps;
import gregicality.multiblocks.api.unification.GCYMMaterialFlags;
import gregtech.api.GTValues;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.properties.BlastProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.common.items.MetaItems;
import gregtech.loaders.recipe.CraftingComponent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static gregtech.api.GTValues.MV;
import static gregtech.api.GTValues.VA;

public class AlloyBlastRecipeProducer {

    public static final AlloyBlastRecipeProducer DEFAULT_PRODUCER = new AlloyBlastRecipeProducer();
    /**
     * Add the freezer recipes for the material
     *
     * @param material the material to generate for
     * @param molten   the molten fluid
     * @param property the blast property of the material
     */
    @SuppressWarnings("MethodMayBeStatic")
    private static final int FLUID_AMOUNT_MOLTEN = GTValues.L;
    private static final int HELIUM_INPUT = 500;
    private static final int HELIUM_OUTPUT = 250;
    private static final int NITROGEN_INPUT = 1000;
    private static final int NITROGEN_OUTPUT = 500;

    /**
     * Generates alloy blast recipes for a material
     *
     * @param material      the material to generate for
     * @param blastProperty the blast property of the material
     */
    public void produce(@NotNull Material material, @NotNull BlastProperty blastProperty) {
        // do not generate for disabled materials
        if (material.hasFlag(GCYMMaterialFlags.NO_ALLOY_BLAST_RECIPES)) return;

        final int componentAmount = material.getMaterialComponents().size();

        // ignore non-alloys
        if (componentAmount < 2) return;

        // get the output fluid
        Fluid output = material.getFluid(GCYMFluidStorageKeys.MOLTEN);
        if (output == null) {
            output = material.getFluid(FluidStorageKeys.LIQUID);
            if (output == null) return;
        }

        RecipeBuilder<BlastRecipeBuilder> builder = createBuilder(blastProperty, material);

        int outputAmount = addInputs(material, builder);
        if (outputAmount <= 0) return;

        buildRecipes(blastProperty, output, outputAmount, componentAmount, builder);

        // if the material does not need a vacuum freezer, exit
        if (!OrePrefix.ingotHot.doGenerateItem(material)) return;

        addFreezerRecipes(material, output, blastProperty);
    }

    /**
     * Creates the recipeBuilder with duration and EUt
     *
     * @param property the blast property of the material
     * @param material the material
     * @return the builder
     */
    @SuppressWarnings("MethodMayBeStatic")
    protected @NotNull BlastRecipeBuilder createBuilder(@NotNull BlastProperty property, @NotNull Material material) {
        BlastRecipeBuilder builder = GCYMRecipeMaps.ALLOY_BLAST_RECIPES.recipeBuilder();
        // apply the duration override
        int duration = property.getDurationOverride();
        if (duration < 0) duration = Math.max(1, (int) (material.getMass() * property.getBlastTemperature() / 100L));
        builder.duration(duration);

        // apply the EUt override
        int EUt = property.getEUtOverride();
        if (EUt < 0) EUt = GTValues.VA[GTValues.MV];
        builder.EUt(EUt);

        return builder.blastFurnaceTemp(property.getBlastTemperature());
    }

    /**
     * @param material the material to start recipes for
     * @param builder  the recipe builder to append to
     * @return the outputAmount if the recipe is valid, otherwise -1
     */
    protected int addInputs(@NotNull Material material, @NotNull RecipeBuilder<BlastRecipeBuilder> builder) {
        // calculate the output amount and add inputs
        int outputAmount = 0;
        int fluidAmount = 0;
        int dustAmount = 0;
        for (MaterialStack materialStack : material.getMaterialComponents()) {
            final Material msMat = materialStack.material;
            final int msAmount = (int) materialStack.amount;

            if (msMat.hasProperty(PropertyKey.DUST)) {
                if (dustAmount >= 9) return -1; // more than 9 dusts won't fit in the machine
                dustAmount++;
                builder.input(OrePrefix.dust, msMat, msAmount);
            } else if (msMat.hasProperty(PropertyKey.FLUID)) {
                if (fluidAmount >= 2) return -1; // more than 2 fluids won't fit in the machine
                fluidAmount++;
                // assume all fluids have 1000mB/mol, since other quantities should be as an item input
                builder.fluidInputs(msMat.getFluid(1000 * msAmount));
            } else return -1; // no fluid or item prop means no valid recipe
            outputAmount += msAmount;
        }
        return outputAmount;
    }

    /**
     * Builds the alloy blast recipes
     *
     * @param property        the blast property to utilize
     * @param molten          the molten fluid
     * @param outputAmount    the amount of material to output
     * @param componentAmount the amount of different components in the material
     * @param builder         the builder to continue
     */
    protected void buildRecipes(@NotNull BlastProperty property, @NotNull Fluid molten, int outputAmount,
                                int componentAmount,
                                @NotNull RecipeBuilder<BlastRecipeBuilder> builder) {
        builder.fluidOutputs(new FluidStack(molten, GTValues.L * outputAmount));

        int duration = builder.getDuration() * outputAmount * 3 / 4;

        BlastProperty.GasTier requiredTier = property.getGasTier();
        if (requiredTier != null) {
            int baseGasDuration = (int) (duration * 0.75);

            for (BlastProperty.GasTier tier : BlastProperty.GasTier.values()) {
                if (tier.ordinal() >= requiredTier.ordinal()) {
                    List<FluidStack> gases = CraftingComponent.EBF_GASES.get(tier);
                    if (gases != null && !gases.isEmpty()) {
                        int tierDiff = tier.ordinal() - requiredTier.ordinal();
                        int newDuration = (int) (baseGasDuration * (1 - 0.1 * tierDiff));
                        if (newDuration < 1) newDuration = 1;

                        for (FluidStack gas : gases) {
                            RecipeBuilder<BlastRecipeBuilder> builderGas = builder.copy();
                            builderGas.circuitMeta(getGasCircuitNum(componentAmount))
                                    .fluidInputs(new FluidStack(gas.getFluid(), gas.amount * outputAmount))
                                    .duration(newDuration)
                                    .buildAndRegister();
                        }
                    }
                }
            }
        }

        // 非气体配方（始终存在）
        builder.circuitMeta(getCircuitNum(componentAmount))
                .duration(duration)
                .buildAndRegister();
    }

    /**
     * @param componentAmount the amount of different components in the material
     * @return the circuit number for the regular recipe
     */
    protected int getCircuitNum(int componentAmount) {
        return componentAmount;
    }

    /**
     * @param componentAmount the amount of different components in the material
     * @return the circuit number for the gas-boosted recipe
     */
    protected int getGasCircuitNum(int componentAmount) {
        return componentAmount + 10;
    }

    protected void addFreezerRecipes(@NotNull Material material, @NotNull Fluid molten,
                                     @NotNull BlastProperty property) {
        // 参数合法性校验
        if (material.getMass() <= 0) {
            throw new IllegalArgumentException("Material mass must be positive");
        }

        final int vacuumDuration = property.getVacuumDurationOverride() == -1 ?
                (int) material.getMass() * 3 : property.getVacuumDurationOverride();
        final int vacuumEUt = property.getVacuumEUtOverride() == -1 ?
                VA[MV] : property.getVacuumEUtOverride();

        // 构建基础冷冻配方
        RecipeBuilder<?> freezerBuilder = createBaseRecipeBuilder(molten, material, vacuumDuration, vacuumEUt)
                .notConsumable(MetaItems.SHAPE_MOLD_INGOT.getStackForm())
                .output(OrePrefix.ingot, material);

        if (property.getBlastTemperature() >= 2000) {
            addNitrogenToRecipe(freezerBuilder);
        } else if (property.getBlastTemperature() >= 5000) {
            addHeliumToRecipe(freezerBuilder);
        }
        freezerBuilder.buildAndRegister();

        // 构建流体输出版本配方
        if (material.hasFluid()) {
            RecipeBuilder<?> freezerFluidBuilder = createBaseRecipeBuilder(molten, material,
                    vacuumDuration * 4 / 5, vacuumEUt)
                    .fluidOutputs(material.getFluid(GTValues.L))
                    .circuitMeta(1);

            if (property.getBlastTemperature() >= 2000) {
                addNitrogenToRecipe(freezerFluidBuilder);
            } else if (property.getBlastTemperature() >= 5000) {
                addHeliumToRecipe(freezerFluidBuilder);
            }
            freezerFluidBuilder.buildAndRegister();
        }
    }

    // 公共配方构建逻辑
    private RecipeBuilder<?> createBaseRecipeBuilder(Fluid molten, Material material,
                                                     int duration, int EUt) {
        return RecipeMaps.VACUUM_RECIPES.recipeBuilder()
                .fluidInputs(new FluidStack(molten, FLUID_AMOUNT_MOLTEN))
                .duration(duration)
                .EUt(EUt);
    }

    // 添加氦气输入输出
    private void addHeliumToRecipe(RecipeBuilder<?> builder) {
        builder.fluidInputs(Materials.Helium.getFluid(FluidStorageKeys.LIQUID, HELIUM_INPUT))
                .fluidOutputs(Materials.Helium.getFluid(HELIUM_OUTPUT));
    }

    private void addNitrogenToRecipe(RecipeBuilder<?> builder) {
        builder.fluidInputs(Materials.Nitrogen.getFluid(FluidStorageKeys.LIQUID, NITROGEN_INPUT))
                .fluidOutputs(Materials.Nitrogen.getFluid(NITROGEN_OUTPUT));
    }
}
