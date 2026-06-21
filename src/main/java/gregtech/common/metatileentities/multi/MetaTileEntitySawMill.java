package gregtech.common.metatileentities.multi;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.NoEnergyMultiblockController;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.unification.material.Materials;
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

    private static final StructureDefinition STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:saw_mill", () -> DeclarativePatternBuilder.start()
                    .aisle("PPP", "F F", "   ")
                    .aisle("PXP", "F F", "FFF")
                    .aisle("PXP", "F F", "   ")
                    .aisle("PXP", "F F", "FFF")
                    .aisle("PSP", "F F", "   ")
                    .self('S', MetaTileEntitySawMill.class)
                    .frames('F', Materials.TreatedWood)
                    .block('X', MetaBlocks.PLANKS.getState(BlockGregPlanks.BlockType.TREATED_PLANK))
                    .any(' ')
                    .casing('P',
                            MetaBlocks.STEAM_CASING.getState(BlockSteamCasing.SteamCasingType.WOOD_WALL))
                        .optionalHatch(MultiblockAbility.IMPORT_ITEMS, 2)
                        .optionalHatch(MultiblockAbility.EXPORT_ITEMS, 2)
                        .optionalHatch(MultiblockAbility.IMPORT_FLUIDS, 2)
                    .buildStructureDefinition());

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
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
