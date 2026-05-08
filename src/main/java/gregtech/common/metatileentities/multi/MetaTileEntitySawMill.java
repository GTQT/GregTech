package gregtech.common.metatileentities.multi;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.NoEnergyMultiblockController;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.LazyTemplate;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockSteamCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.wood.BlockGregPlanks;

import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;

import static gregtech.api.recipes.RecipeMaps.SAWMILL_RECIPES;

public class MetaTileEntitySawMill extends NoEnergyMultiblockController {

    public MetaTileEntitySawMill(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, SAWMILL_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntitySawMill(metaTileEntityId);
    }

    @Override
    private static final LazyTemplate TEMPLATE = LazyTemplate.of(() ->
            DeclarativePatternBuilder.start()
                    .aisle("PPPPP", "    F", "    F")
                    .aisle("PXXXP", "XX XF", "FFFFF")
                    .aisle("PXXXP", "XX XF", " F  F")
                    .aisle("PXXXP", "XX XF", "FFFFF")
                    .aisle("PSPPP", "    F", "    F")
                    .where('S', selfPredicate(GTUtility.gregtechId("saw_mill")))
                    .where('F', states(MetaBlocks.FRAMES.get(Materials.TreatedWood).getBlock(Materials.TreatedWood)))
                    .where('X', states(MetaBlocks.PLANKS.getState(BlockGregPlanks.BlockType.TREATED_PLANK)))
                    .where(' ', any())
                    .casing('P', CasingDefinition.simple(
                            MetaBlocks.STEAM_CASING.getState(BlockSteamCasing.SteamCasingType.WOOD_WALL),
                            "gregtech.machine.casing.wood_wall"))
                        .withOptionalHatches(MultiblockAbility.IMPORT_ITEMS, 2)
                        .withOptionalHatches(MultiblockAbility.EXPORT_ITEMS, 2)
                        .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 2)
                    .buildTemplate()
    );

    protected @NotNull BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.PRIMITIVE;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.WOOD_WALL;
    }

    @Override
    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.BLOWER_OVERLAY;
    }

    public boolean hasMaintenanceMechanics() {
        return false;
    }
}
