package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.LazyTemplate;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

public class MetaTileEntityVacuumFreezer extends RecipeMapMultiblockController {

    private static final LazyTemplate TEMPLATE = LazyTemplate.of(() ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "XXX", "XXX")
                    .aisle("XXX", "X#X", "XXX")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(GTUtility.gregtechId("vacuum_freezer")))
                    .where('#', air())
                    .casing('X', CasingDefinition.simple(
                            MetaBlocks.METAL_CASING.getState(MetalCasingType.ALUMINIUM_FROSTPROOF),
                            "gregtech.machine.casing.aluminium_frostproof"))
                        .withHatches(MultiblockAbility.INPUT_ENERGY, 1, 2)
                        .withOptionalHatches(MultiblockAbility.MAINTENANCE_HATCH, 1)
                        .withOptionalHatches(MultiblockAbility.IMPORT_ITEMS, 4)
                        .withOptionalHatches(MultiblockAbility.EXPORT_ITEMS, 4)
                        .withOptionalHatches(MultiblockAbility.IMPORT_FLUIDS, 4)
                        .withOptionalHatches(MultiblockAbility.EXPORT_FLUIDS, 4)
                    .buildTemplate()
    );

    public MetaTileEntityVacuumFreezer(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.VACUUM_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityVacuumFreezer(metaTileEntityId);
    }

    @Override
    protected BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.FROST_PROOF_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.ALUMINIUM_FROSTPROOF);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.VACUUM_FREEZER_OVERLAY;
    }
}
