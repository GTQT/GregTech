package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.SoftTemplate;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.element.Elements;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockBoilerCasing;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.BlockWireCoil;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MetaTileEntityLargeChemicalReactor extends RecipeMapMultiblockController {

    private static final SoftTemplate TEMPLATE = TemplatePool.getInstance().register("gregtech:large_chemical_reactor", () ->
            DeclarativePatternBuilder.start()
                    .aisle("XXX", "XCX", "XXX")
                    .aisle("XCX", "CPC", "XCX")
                    .aisle("XXX", "XSX", "XXX")
                    .where('S', selfPredicate(MetaTileEntityLargeChemicalReactor.class))
                    .where('P', Elements.block(getPipeCasingState()))
                    .where('C', Elements.chain(
                            Elements.tieredCasing(
                                    GTCasingGroups.heatingCoils().group(),
                                    GTCasingGroups.heatingCoils().channel().getName(),
                                    1, 1),
                            Elements.hatch(MultiblockAbility.IMPORT_ITEMS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.EXPORT_ITEMS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.IMPORT_FLUIDS, 0, -1, 0),
                            Elements.hatch(MultiblockAbility.EXPORT_FLUIDS, 0, -1, 0),
                            Elements.block(getCasingState())))
                    .casing('X', CasingDefinition.simple(getCasingState()))
                        .maintenance().preset(HatchPresets.STANDARD_IO).optionalEnergyInput(2)
                    .buildTemplate()
    );

    public MetaTileEntityLargeChemicalReactor(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.LARGE_CHEMICAL_RECIPES);
        this.recipeMapWorkable = new MultiblockRecipeLogic(this, true);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeChemicalReactor(metaTileEntityId);
    }

    @Override
    protected @NotNull BlockPatternTemplate createStructureTemplate() {
        return TEMPLATE.get();
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        ArrayList<MultiblockShapeInfo> shapeInfo = new ArrayList<>();
        MultiblockShapeInfo.Builder baseBuilder = MultiblockShapeInfo.builder()
                .where('S', MetaTileEntities.LARGE_CHEMICAL_REACTOR, EnumFacing.SOUTH)
                .where('X', MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING))
                .where('P',
                        MetaBlocks.BOILER_CASING
                                .getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE))
                .where('C', MetaBlocks.WIRE_COIL.getState(BlockWireCoil.CoilType.CUPRONICKEL))
                .where('I', MetaTileEntities.ITEM_IMPORT_BUS[3], EnumFacing.SOUTH)
                .where('E', MetaTileEntities.ENERGY_INPUT_HATCH[3], EnumFacing.NORTH)
                .where('O', MetaTileEntities.ITEM_EXPORT_BUS[3], EnumFacing.SOUTH)
                .where('F', MetaTileEntities.FLUID_IMPORT_HATCH[3], EnumFacing.SOUTH)
                .where('H', MetaTileEntities.FLUID_EXPORT_HATCH[3], EnumFacing.SOUTH)
                .where('M',
                        () -> ConfigHolder.machines.enableMaintenance ? MetaTileEntities.MAINTENANCE_HATCH :
                                MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING),
                        EnumFacing.SOUTH);
        shapeInfo.add(baseBuilder.shallowCopy()
                .aisle("XEX", "XCX", "XXX")
                .aisle("XXX", "XPX", "XXX")
                .aisle("IMO", "FSH", "XXX")
                .build());
        shapeInfo.add(baseBuilder.shallowCopy()
                .aisle("XEX", "XXX", "XXX")
                .aisle("XXX", "XPX", "XCX")
                .aisle("IMO", "FSH", "XXX")
                .build());
        shapeInfo.add(baseBuilder.shallowCopy()
                .aisle("XEX", "XXX", "XXX")
                .aisle("XCX", "XPX", "XXX")
                .aisle("IMO", "FSH", "XXX")
                .build());
        shapeInfo.add(baseBuilder.shallowCopy()
                .aisle("XEX", "XXX", "XXX")
                .aisle("XXX", "CPX", "XXX")
                .aisle("IMO", "FSH", "XXX")
                .build());
        shapeInfo.add(baseBuilder.shallowCopy()
                .aisle("XEX", "XXX", "XXX")
                .aisle("XXX", "XPC", "XXX")
                .aisle("IMO", "FSH", "XXX")
                .build());
        return shapeInfo;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.INERT_PTFE_CASING;
    }

    protected static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.PTFE_INERT_CASING);
    }

    protected static IBlockState getPipeCasingState() {
        return MetaBlocks.BOILER_CASING.getState(BlockBoilerCasing.BoilerCasingType.POLYTETRAFLUOROETHYLENE_PIPE);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        TooltipBuilder.create().addPerfectOC().build(this, tooltip);
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.LARGE_CHEMICAL_REACTOR_OVERLAY;
    }
}
