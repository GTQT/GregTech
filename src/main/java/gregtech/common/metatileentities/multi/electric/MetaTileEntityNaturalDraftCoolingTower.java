package gregtech.common.metatileentities.multi.electric;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.CachedPatternRecipeMapMultiblock;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.TextComponentUtil;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockBoilerCasing.BoilerCasingType;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.StoneVariantBlock;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static gregtech.api.pattern.FluidStructureElements.*;

/**
 * @author SUSYCore
 * @since <a href="https://github.com/SymmetricDevs/Susy-Core/blob/main/src/main/java/supersymmetry/common/metatileentities/multi/electric/MetaTileEntityNaturalDraftCoolingTower.java">...</a>
 */
public class MetaTileEntityNaturalDraftCoolingTower extends CachedPatternRecipeMapMultiblock {

    private static final String[][] VAPOR_PATTERN = {{
            "  VVV",
            " VVVVV",
            "VVVVVVV",
            "VVVVVVV",
            "VVVVVVV",
            " VVVVV",
            "  VVV"
    }};

    private static final Vec3i PATTERN_OFFSET = new Vec3i(-8, 14, 4);
    @NotNull
    private static final StructureDefinition<?> STRUCTURE_DEFINITION = StructureDefinition.getOrBuild(
            "gregtech:natural_draft_cooling_tower", () -> DeclarativePatternBuilder.start()
                    .aisle("     CCCCC     ", "     CCCCC     ", "     CCCCC     ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .aisle("   CCCCCCCCC   ", "   CCWWWWWCC   ", "   CCCCCCCCC   ", "     FFFFF     ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .aisle("  CCCCCCCCCCC  ", "  CWWWWWWWWWC  ", "  CC#######CC  ", "   FF#####FF   ", "     CCCCC     ",
                            "     CCICC     ", "      CCC      ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .aisle(" CCCCCCCCCCCCC ", " CWWWWWWWWWWWC ", " CC#########CC ", "  F#########F  ", "   CCAAAAACC   ",
                            "    C#P#P#C    ", "     C###C     ", "     CCCCC     ", "      CCC      ", "               ",
                            "               ", "               ", "               ", "      CCC      ", "     CCCCC     ",
                            "     CCCCC     ")
                    .aisle(" CCCCCCCCCCCCC ", " CWWWWWWWWWWWC ", " CC#########CC ", "  F#########F  ", "   CAAAAAAAC   ",
                            "   CPPPPPPPC   ", "    C#####C    ", "    CCCCCCC    ", "     CCCCC     ", "      CCC      ",
                            "      CCC      ", "      CCC      ", "     CCCCC     ", "     CCCCC     ", "    CC###CC    ",
                            "    CC###CC    ")
                    .aisle("CCCCCCCCCCCCCCC", "CWWWWWWWWWWWWWC", "CC###########CC", " F###########F ", "  CAAAAAAAAAC  ",
                            "  C#P#P#P#P#C  ", "   C#######C   ", "   CCFFFFFCC   ", "    CC###CC    ", "     C###C     ",
                            "     C###C     ", "     C###C     ", "    CC###CC    ", "    CC###CC    ", "   CC#####CC   ",
                            "   CC#####CC   ")
                    .aisle("CCCCCCCCCCCCCCC", "CWWWWWWWWWWWWWC", "CC###########CC", " F###########F ", "  CAAAAAAAAAC  ",
                            "  CPPPPPPPPPC  ", "  C#########C  ", "   CCFFFFFCC   ", "   CC#####CC   ", "    C#####C    ",
                            "    C#####C    ", "    C#####C    ", "    C#####C    ", "   CC#####CC   ", "   C#######C   ",
                            "   C#######C   ")
                    .aisle("CCCCCCCCCCCCCCC", "CWWWWWWWWWWWWWC", "CC###########CC", " F###########F ", "  CAAAAAAAAAC  ",
                            "  I#P#P#P#P#I  ", "  C#########C  ", "   CCFFFFFCC   ", "   CC#####CC   ", "    C#####C    ",
                            "    C#####C    ", "    C#####C    ", "    C#####C    ", "   CC#####CC   ", "   C#######C   ",
                            "   C#######C   ")
                    .aisle("CCCCCCCCCCCCCCC", "CWWWWWWWWWWWWWC", "CC###########CC", " F###########F ", "  CAAAAAAAAAC  ",
                            "  CPPPPPPPPPC  ", "  C#########C  ", "   CCFFFFFCC   ", "   CC#####CC   ", "    C#####C    ",
                            "    C#####C    ", "    C#####C    ", "    C#####C    ", "   CC#####CC   ", "   C#######C   ",
                            "   C#######C   ")
                    .aisle("CCCCCCCCCCCCCCC", "CWWWWWWWWWWWWWC", "CC###########CC", " F###########F ", "  CAAAAAAAAAC  ",
                            "  C#P#P#P#P#C  ", "   C#######C   ", "   CCFFFFFCC   ", "    CC###CC    ", "     C###C     ",
                            "     C###C     ", "     C###C     ", "    CC###CC    ", "    CC###CC    ", "   CC#####CC   ",
                            "   CC#####CC   ")
                    .aisle(" CCCCCCCCCCCCC ", " CWWWWWWWWWWWC ", " CC#########CC ", "  F#########F  ", "   CAAAAAAAC   ",
                            "   CPPPPPPPC   ", "    C#####C    ", "    CCCCCCC    ", "     CCCCC     ", "      CCC      ",
                            "      CCC      ", "      CCC      ", "     CCCCC     ", "     CCCCC     ", "    CC###CC    ",
                            "    CC###CC    ")
                    .aisle(" CCCCCCCCCCCCC ", " CWWWWWWWWWWWC ", " CC#########CC ", "  F#########F  ", "   CCAAAAACC   ",
                            "    C#P#P#C    ", "     C###C     ", "     CCCCC     ", "      CCC      ", "               ",
                            "               ", "               ", "               ", "      CCC      ", "     CCCCC     ",
                            "     CCCCC     ")
                    .aisle("  CCCCCCCCCCP  ", "  CWWWWWWWWWC  ", "  CC#######CC  ", "   FF#####FF   ", "     CCCCC     ",
                            "     CCICC     ", "      CCC      ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .aisle("   CCCCCCCCCP  ", "   CCWWWWWCC   ", "   CCCCCCCCC   ", "     FFFFF     ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .aisle("     CCCCC OOO ", "     CCCCC OSO ", "     CCCCC     ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ", "               ", "               ", "               ", "               ",
                            "               ")
                    .self('S', MetaTileEntityNaturalDraftCoolingTower.class)
                    .where('O', Elements.chain(
                            Elements.block(getCasingState()),
                            Elements.hatch(MultiblockAbility.INPUT_ENERGY),
                            Elements.hatch(MultiblockAbility.MAINTENANCE_HATCH),
                            Elements.hatch(MultiblockAbility.EXPORT_FLUIDS)))
                    .where('I', Elements.chain(
                            Elements.block(MetaBlocks.STONE_BLOCKS.get(StoneVariantBlock.StoneVariant.SMOOTH)
                                    .getState(StoneVariantBlock.StoneType.CONCRETE_LIGHT)),
                            Elements.hatch(MultiblockAbility.IMPORT_FLUIDS)))
                    .block('C', MetaBlocks.STONE_BLOCKS.get(StoneVariantBlock.StoneVariant.SMOOTH)
                            .getState(StoneVariantBlock.StoneType.CONCRETE_LIGHT))
                    .where('W', fluidElement(FluidRegistry.WATER))
                    .frames('F', Materials.Steel)
                    .block('A', MetaBlocks.METAL_CASING
                            .getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID))
                    .block('P', MetaBlocks.BOILER_CASING.getState(BoilerCasingType.STEEL_PIPE))
                    .any(' ')
                    .air('#')
                    .buildStructureDefinition()
    );
    private boolean waterFilled;
    private List<BlockPos> waterPositions;

    public MetaTileEntityNaturalDraftCoolingTower(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.NATURAL_DRAFT_COOLING_TOWER);
    }

    public static IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STEEL_SOLID);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityNaturalDraftCoolingTower(metaTileEntityId);
    }

    @Override
    protected void formStructure(FormedStructureView formed) {
        super.formStructure(formed);

        List<BlockPos> positions = formed.getAggregate(FLUID_BLOCK_POSITIONS);
        this.waterPositions = positions == null ? new ArrayList<>() : new ArrayList<>(positions);
        this.waterFilled = waterPositions.isEmpty();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.waterPositions = null; // Clear water fill data when the structure is invalidated
        this.waterFilled = false;
    }

    @Override
    protected void updateFormedValid() {
        super.updateFormedValid();
        if (!waterFilled && getOffsetTimer() % 5 == 0) {
            fillFluid(this, this.waterPositions, FluidRegistry.WATER);
            if (this.waterPositions.isEmpty()) {
                this.waterFilled = true;
            }
        }
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !waterFilled;
    }

    @Override
    protected @NotNull StructureDefinition<?> createStructureDefinition() {
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected void addErrorText(List<ITextComponent> textList) {
        super.addErrorText(textList);
        if (isStructureFormed() && !waterFilled) {
            textList.add(TextComponentUtil.translationWithColor(TextFormatting.RED,
                    "susy.multiblock.natural_draft_cooling_tower.obstructed"));
            textList.add(TextComponentUtil.translationWithColor(TextFormatting.GRAY,
                    "susy.multiblock.natural_draft_cooling_tower.obstructed.desc"));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.fluid_auto_fill.tooltip"));
        TooltipBuilder.create().addParallel(128).build(this, tooltip);
    }

    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart iMultiblockPart) {
        return Textures.SOLID_STEEL_CASING;
    }

    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.PRIMITIVE_PUMP_OVERLAY;
    }

    @Override
    protected String[][] getPattern() {
        return VAPOR_PATTERN;
    }

    @Override
    protected Vec3i getPatternOffset() {
        return PATTERN_OFFSET;
    }

    @Override
    public void update() {
        super.update();
        if (this.isActive() && getWorld().isRemote)
            createParticles();
    }

    @SideOnly(Side.CLIENT)
    private void createParticles() {
        Random rand = getWorld().rand;
        if (cachedPattern == null || cachedPattern.length == 0)
            generateCachedPattern(getPattern(), getPatternOffset(), this.frontFacing, isFlipped());
        for (Vec3i offset : cachedPattern) {

            BlockPos pos = this.getPos().add(offset);

            this.getWorld().spawnParticle(EnumParticleTypes.CLOUD,
                    pos.getX() + rand.nextDouble(),
                    pos.getY() + .5F,
                    pos.getZ() + rand.nextDouble(), .1F, .3F, .1F);
        }
    }

    @Override
    public boolean isMultiblockPartWeatherResistant(@NotNull IMultiblockPart part) {
        return true;
    }

    @Override
    public boolean getIsWeatherOrTerrainResistant() {
        return true;
    }
}
