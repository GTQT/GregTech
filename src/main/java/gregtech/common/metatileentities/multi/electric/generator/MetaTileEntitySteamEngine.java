package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
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
                            .block('G', getCasingState2())
                            .where('E', energyOutputElement())
                            .hatch('M', MultiblockAbility.MUFFLER_HATCH)
                            .any('#')
                            .casing('X', getCasingState())
                            .hatch(MultiblockAbility.MAINTENANCE_HATCH,
                                    ConfigHolder.machines.enableMaintenance ? 1 : 0, 1)
                            .preset(HatchPresets.STANDARD_FLUID_IO)
                            .buildStructureDefinition());

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

    // Energy output restricted to MV dynamos, using typed Elements API.
    private static IStructureElement energyOutputElement() {
        return Elements.withTooltips(
                Elements.metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_ENERGY).stream().filter(mte -> {
                    IEnergyContainer container = mte.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
                    return container != null && container.getOutputVoltage() <= GTValues.V[GTValues.MV];
                }).toArray(MetaTileEntity[]::new)),
                "gregtech.multiblock.pattern.error.limited.1 " + GTValues.VN[GTValues.LV],
                "gregtech.multiblock.pattern.error.limited.0 " + GTValues.VN[GTValues.MV]);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.STEAM_CASING);
    }

    public static IBlockState getCasingState2() {
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
        return Textures.STEAM_CASING;
    }

    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.STEAM_ENGINE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }
}
