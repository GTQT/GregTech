package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.GCYMRecipeMapMultiblockController;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.CasingDefinition;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.casing.GTCasingGroups;
import gregtech.api.pattern.casing.HatchPresets;
import gregtech.api.pattern.casing.ICasing;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.tooltips.InformationHandler;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.BlockLargeMultiblockCasing;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

//这是与GCYM的转底炉 巨冰箱同一系列的设备
//此系列设备不给多线程
public class MetaTileEntityMegaCrackingUnit extends GCYMRecipeMapMultiblockController {

    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gcym:mega_cracking_unit", () ->
                    DeclarativePatternBuilder.start()
                            .aisle("CCCCCCCCCCCCC", " C         C ", " C         C ", " C         C ", " C         C ",
                                    " C         C ", " C         C ")
                            .aisle("CCCCCCCCCCCCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC",
                                    "CCGGGGGGGGGCC", "CCGGGGGGGGGCC")
                            .aisle("CCCCCCCCCCCCC", " GALALALALAG ", " GALALALALAG ", " GALALALALAG ", " GALALALALAG ",
                                    " GALALALALAG ", " CGGGGGGGGGC ")
                            .aisle("CCCCCCCCCCCCC", " GALALALALAG ", " EAAAAAAAAAD ", " EALALALALAD ", " EAAAAAAAAAD ",
                                    " GALALALALAG ", " CGGGEEEGGGC ")
                            .aisle("CCCCCCCCCCCCC", " GALALALALAG ", " EALALALALAD ", " EALALALALAD ", " EALALALALAD ",
                                    " GALALALALAG ", " CGGGEEEGGGC ")
                            .aisle("CCCCCCCCCCCCC", " GALALALALAG ", " EAAAAAAAAAD ", " EALALALALAD ", " EAAAAAAAAAD ",
                                    " GALALALALAG ", " CGGGEEEGGGC ")
                            .aisle("CCCCCCCCCCCCC", " GALALALALAG ", " GALALALALAG ", " GALALALALAG ", " GALALALALAG ",
                                    " GALALALALAG ", " CGGGGGGGGGC ")
                            .aisle("CCCCCCCCCCCCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC", "CCGGGGGGGGGCC",
                                    "CCGGGGGGGGGCC", "CCGGGGGGGGGCC")
                            .aisle("CCCCCCSCCCCCC", " C         C ", " C         C ", " C         C ", " C         C ",
                                    " C         C ", " C         C ")
                            .self('S', MetaTileEntityMegaCrackingUnit.class)
                            .casing('C', CasingDefinition.simple(getCasingState()))
                            .optionalEnergyInput(8)
                            .optionalLaserInput(1)
                            .preset(HatchPresets.MUFFLER_IO)
                            .tieredHatch()
                            .parallelHatch()
                            .where('G', states(getGlassState()))
                            .tieredCasing('L', GTCasingGroups.heatingCoils().group())
                            .withChannel(GTCasingGroups.heatingCoils().channel())
                            .where('D', states(getCasingState())
                                    .or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1))
                                    .or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1))
                            )
                            .where('E', states(getCasingState())
                                    .or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1))
                                    .or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1))
                            )
                            .where(' ', any())
                            .where('A', air())
                            .buildStructureDefinition()
    );

    private int coilTier;

    public MetaTileEntityMegaCrackingUnit(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, new RecipeMap[] {
                RecipeMaps.CRACKING_RECIPES
        });
        this.recipeMapWorkable = new CrackingUnitWorkableHandler(this);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.LARGE_MULTIBLOCK_CASING.getState(BlockLargeMultiblockCasing.CasingType.WATERTIGHT_CASING);
    }

    protected static IBlockState getGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS);
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.WATERTIGHT_CASING;
    }

    @Override
    public void addCustomCapacity(KeyManager keyManager, UISyncer syncer) {
        if (isStructureFormed()) {
            // Coil energy discount line
            IKey energyDiscount = KeyUtil.number(TextFormatting.AQUA,
                    syncer.syncLong(100 - 10L * getCoilTier()), "%");

            IKey base = KeyUtil.lang(TextFormatting.GRAY,
                    "gregtech.multiblock.cracking_unit.energy",
                    energyDiscount);

            IKey hover = KeyUtil.lang(TextFormatting.GRAY,
                    "gregtech.multiblock.cracking_unit.energy_hover");

            keyManager.add(KeyUtil.setHover(base, hover));
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        InformationHandler.topTooltips("最强裂化机", tooltip);
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addCoilLogic().build(this, tooltip);
        tooltip.add(I18n.format("gregtech.machine.cracker.tooltip.1"));
        TooltipBuilder.create().addLaser().build(this, tooltip);
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityMegaCrackingUnit(this.metaTileEntityId);
    }

    @Override
    protected @NotNull OrientedOverlayRenderer getFrontOverlay() {
        return Textures.MEGA_CHEMICAL_REACTOR;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        ICasing matchedCoil = GTCasingGroups.heatingCoils().channel().getMatchedCasing(formed);
        IHeatingCoilBlockStats stats = matchedCoil != null ?
                matchedCoil.getPayloadAs(IHeatingCoilBlockStats.class) : null;
        if (stats != null) {
            this.coilTier = stats.getTier();
        } else {
            this.coilTier = 0;
        }
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.coilTier = -1;
    }

    protected int getCoilTier() {
        return this.coilTier;
    }

    @SuppressWarnings("InnerClassMayBeStatic")
    private class CrackingUnitWorkableHandler extends GCYMMultiblockRecipeLogic {

        public CrackingUnitWorkableHandler(GCYMRecipeMapMultiblockController tileEntity) {
            super(tileEntity);
        }

        @Override
        protected void modifyOverclockPost(@NotNull OCResult ocResult, @NotNull RecipePropertyStorage storage) {
            super.modifyOverclockPost(ocResult, storage);

            int coilTier = ((MetaTileEntityMegaCrackingUnit) metaTileEntity).getCoilTier();
            if (coilTier <= 0)
                return;

            // each coil above cupronickel (coilTier = 0) uses 10% less energy
            ocResult.setEut(Math.max(1, (long) (ocResult.eut() * (1.0 - coilTier * 0.1))));
        }
    }
}
