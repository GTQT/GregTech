package gregtech.common.metatileentities.multiblock.generator;

import gregtech.client.renderer.texture.GCYMTextures;
import gregtech.common.blocks.GCYMMetaBlocks;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockTurbineCasing;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntitySteamEngine extends FuelMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:steam_engine", () ->
                    DeclarativePatternBuilder.start()
                            .piece("main")
                                .aisle("#XX", "XEX", "#XX")
                                .aisle("XXX", "XGX", "XMX")
                                .aisle("#XX", "XGX", "#XX")
                                .aisle("#XX", "#SX", "#XX")
                            .self('S', MetaTileEntitySteamEngine.class)
                            .where('X', states(getCasingState()).setMinGlobalLimited(18)
                                    .or(standardAbilities()))
                            .where('G', states(getCasingState2()))
                            .where('E', energyOutputPredicate())
                            .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                            .where('#', any())
                            .buildStructureDefinition());

    private static TraceabilityPredicate standardAbilities() {
        TraceabilityPredicate predicate = abilities(MultiblockAbility.MAINTENANCE_HATCH)
                .setMinGlobalLimited(ConfigHolder.machines.enableMaintenance ? 1 : 0)
                .setMaxGlobalLimited(1);
        RecipeMap<?> recipeMap = RecipeMaps.STEAM_TURBINE_FUELS;
        if (recipeMap.getMaxInputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1));
        }
        if (recipeMap.getMaxOutputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1));
        }
        if (recipeMap.getMaxFluidInputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1));
        }
        if (recipeMap.getMaxFluidOutputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1));
        }
        return predicate;
    }

    public MetaTileEntitySteamEngine(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.STEAM_TURBINE_FUELS, GTValues.MV);
        this.recipeMapWorkable = new MultiblockFuelRecipeLogic(this) {
            @Override
            public long getMaxVoltage() {
                return GTValues.V[GTValues.MV];
            }
        };
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[GTValues.MV]);
    }

    private static TraceabilityPredicate energyOutputPredicate() {
        return metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_ENERGY).stream().filter(mte -> {
            IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
            return container != null && container.getOutputVoltage() <= GTValues.V[GTValues.MV];
        }).toArray(MetaTileEntity[]::new))
                .addTooltip("gregtech.multiblock.pattern.error.limited.1", GTValues.VN[GTValues.LV])
                .addTooltip("gregtech.multiblock.pattern.error.limited.0", GTValues.VN[GTValues.MV]);
    }

    private static IBlockState getCasingState() {
        return GCYMMetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.STEAM_CASING);
    }

    private static IBlockState getCasingState2() {
        return MetaBlocks.TURBINE_CASING.getState(BlockTurbineCasing.TurbineCasingType.BRONZE_GEARBOX);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntitySteamEngine(metaTileEntityId);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gcym.machine.steam_engine.tooltip.1", GTValues.VNF[GTValues.MV]));
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return GCYMTextures.STEAM_CASING;
    }

    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return GCYMTextures.STEAM_ENGINE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }
}
