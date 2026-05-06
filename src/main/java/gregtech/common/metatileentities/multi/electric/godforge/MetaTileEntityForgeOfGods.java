package gregtech.common.metatileentities.multi.electric.godforge;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockWithDisplayBase;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.LazyTemplate;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.OffsetMode;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.blocks.BlockGodforgeGlass;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.metatileentities.multi.electric.godforge.util.ForgeOfGodsData;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static gregtech.api.util.RelativeDirection.FRONT;
import static gregtech.api.util.RelativeDirection.RIGHT;
import static gregtech.api.util.RelativeDirection.UP;

/**
 * Forge of the Gods — the largest multiblock structure in the mod.
 * <p>
 * Structure overview:
 * <ul>
 *   <li>beam_shaft (60 layers) — contains the controller, hatches, modules</li>
 *   <li>first_ring (127 layers) — always required (replaced with air when renderer active)</li>
 *   <li>second_ring (111 layers) — conditional, unlocked by CD upgrade</li>
 *   <li>third_ring (94 layers) — conditional, unlocked by END upgrade</li>
 * </ul>
 * <p>
 * Architecture:
 * <ul>
 *   <li>Initial formation uses a standard BlockPattern (beam_shaft + first_ring merged).</li>
 *   <li>After formation, a MultiPiecePattern will provide event-driven partial re-validation
 *       (pending multiblock structure system refactoring — see docs/multiblock-structure-refactoring-plan.md).</li>
 * </ul>
 */
public class MetaTileEntityForgeOfGods extends MultiblockWithDisplayBase {

    private final ForgeOfGodsData data = new ForgeOfGodsData();

    public MetaTileEntityForgeOfGods(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityForgeOfGods(metaTileEntityId);
    }

    // ==================== Structure Pattern (Initial Formation + JEI) ====================

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        // The main BlockPattern handles initial structure formation and JEI preview.
        // It contains beam_shaft + first_ring merged into a single pattern.
        String[][] beamShaft = ForgeOfGodsStructureString.BEAM_SHAFT;
        String[][] firstRing = data.isRenderActive() ?
                ForgeOfGodsStructureString.FIRST_RING_AIR :
                ForgeOfGodsStructureString.FIRST_RING;

        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : beamShaft) {
            builder.aisle(layer);
        }
        for (String[] layer : firstRing) {
            builder.aisle(layer);
        }

        builder.where('S', selfPredicate());
        applySharedPredicates(builder);
        return builder.build();
    }

    // ==================== Multi-Piece Pattern (P3: Sharded Structure Check) ====================

    // Piece offsets in structure-relative coordinates (right, up, back from controller)
    // Derived from GT5 structurelib checkPiece offsets:
    //   beam_shaft: controller is at template [63, 14, 1] → offset (0, 0, 0)
    //   first_ring: controller maps to template [63, 14, 0] → 59 aisles behind controller
    //   second_ring: controller maps to template [55, 11, 0] → 67 aisles behind controller
    //   third_ring: controller maps to template [47, 13, 0] → 76 aisles behind controller
    private static final Vec3i BEAM_SHAFT_OFFSET = Vec3i.NULL_VECTOR;
    private static final Vec3i FIRST_RING_OFFSET = new Vec3i(0, 0, 59);
    private static final Vec3i SECOND_RING_OFFSET = new Vec3i(0, 0, 67);
    private static final Vec3i THIRD_RING_OFFSET = new Vec3i(0, 0, 76);

    // External center offsets [x, y, z, minZ, maxZ] for sub-piece templates without selfPredicate
    private static final int[] FIRST_RING_CENTER = { 63, 14, 0, 0, 0 };
    private static final int[] SECOND_RING_CENTER = { 55, 11, 0, 0, 0 };
    private static final int[] THIRD_RING_CENTER = { 47, 13, 0, 0, 0 };

    // Static template cache using LazyTemplate (thread-safe, zero-lock after init)
    private static final LazyTemplate BEAM_SHAFT_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildBeamShaftTemplate);
    private static final LazyTemplate FIRST_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildFirstRingTemplate);
    private static final LazyTemplate SECOND_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildSecondRingTemplate);
    private static final LazyTemplate THIRD_RING_TEMPLATE = LazyTemplate.of(
            MetaTileEntityForgeOfGods::buildThirdRingTemplate);

    @Nullable
    @Override
    protected MultiPiecePattern createMultiPiecePattern() {
        return MultiPiecePattern.builder()
                .piece("beam_shaft", BEAM_SHAFT_TEMPLATE.get(), BEAM_SHAFT_OFFSET, OffsetMode.RELATIVE)
                .piece("first_ring", FIRST_RING_TEMPLATE.get(), FIRST_RING_OFFSET, OffsetMode.RELATIVE)
                .conditionalPiece("second_ring", SECOND_RING_TEMPLATE.get(), SECOND_RING_OFFSET,
                        OffsetMode.RELATIVE, () -> data.isUpgradeActive(ForgeOfGodsUpgrade.CD))
                .conditionalPiece("third_ring", THIRD_RING_TEMPLATE.get(), THIRD_RING_OFFSET,
                        OffsetMode.RELATIVE, () -> data.isUpgradeActive(ForgeOfGodsUpgrade.END))
                .build();
    }

    private static BlockPatternTemplate buildBeamShaftTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.BEAM_SHAFT) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, true);
        return builder.buildTemplate();
    }

    private static BlockPatternTemplate buildFirstRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.FIRST_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(FIRST_RING_CENTER);
    }

    private static BlockPatternTemplate buildSecondRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.SECOND_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(SECOND_RING_CENTER);
    }

    private static BlockPatternTemplate buildThirdRingTemplate() {
        FactoryBlockPattern builder = FactoryBlockPattern.start(RIGHT, UP, FRONT);
        for (String[] layer : ForgeOfGodsStructureString.THIRD_RING) {
            builder.aisle(layer);
        }
        applyAllPredicates(builder, false);
        return builder.buildTemplate(THIRD_RING_CENTER);
    }

    /**
     * Apply all known character -> predicate mappings to a builder.
     * Includes all characters used across all pieces.
     *
     * @param builder          the factory block pattern builder
     * @param includeController true to include 'S' -> selfPredicate() (only for beam_shaft)
     */
    private static void applyAllPredicates(FactoryBlockPattern builder, boolean includeController) {
        if (includeController) {
            builder.where('S', new TraceabilityPredicate(
                    blockWorldState -> true,
                    () -> new gregtech.api.util.BlockInfo[] {
                            new gregtech.api.util.BlockInfo(
                                    getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING))
                    }).setCenter());
        }
        applySharedPredicates(builder);
    }

    // ==================== Block State Helpers ====================

    private static void applySharedPredicates(FactoryBlockPattern builder) {
        builder.where('A', hatches())
                .where('B', states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)))
                .where('C', states(getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING)))
                .where('D', states(getCasingState(BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING)))
                .where('E', states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)))
                .where('F', states(getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING)))
                .where('G', states(getCasingState(BlockGodforgeCasing.CasingType.REMOTE_GRAVITON_FLOW_MODULATOR)))
                .where('H', states(getGlassState()))
                .where('J', godforgeModules()
                        .or(states(getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING))))
                .where('I', states(getCasingState(BlockGodforgeCasing.CasingType.MEDIAL_GRAVITON_FLOW_MODULATOR)))
                .where('K', states(getCasingState(BlockGodforgeCasing.CasingType.CENTRAL_GRAVITON_FLOW_MODULATOR)))
                .where('L', air());
    }

    private static IBlockState getCasingState(BlockGodforgeCasing.CasingType type) {
        return MetaBlocks.GODFORGE_CASING.getState(type);
    }

    private static IBlockState getGlassState() {
        return MetaBlocks.GODFORGE_GLASS.getState(BlockGodforgeGlass.GlassType.SPATIALLY_TRANSCENDENT_GRAVITATIONAL_LENS);
    }

    private static TraceabilityPredicate hatches() {
        return abilities(MultiblockAbility.IMPORT_ITEMS)
                .or(abilities(MultiblockAbility.IMPORT_FLUIDS))
                .or(abilities(MultiblockAbility.EXPORT_ITEMS))
                .or(states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING)));
    }

    private static TraceabilityPredicate godforgeModules() {
        return metaTileEntities(
                MetaTileEntities.GODFORGE_SMELTING_MODULE,
                MetaTileEntities.GODFORGE_MOLTEN_MODULE,
                MetaTileEntities.GODFORGE_PLASMA_MODULE,
                MetaTileEntities.GODFORGE_EXOTIC_MODULE);
    }

    // ==================== Structure Lifecycle ====================

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        updateRingAmount();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
    }

    private void updateRingAmount() {
        int rings = 1;
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.CD)) {
            rings = 2;
        }
        if (data.isUpgradeActive(ForgeOfGodsUpgrade.END)) {
            rings = 3;
        }
        data.setRingAmount(rings);
    }

    // ==================== Rendering ====================

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.GODFORGE_INNER_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.GODFORGE_CONTROLLER_OVERLAY;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), true, true);
    }

    // ==================== Tick Logic ====================

    @Override
    protected void updateFormedValid() {
        // TODO: Implement Forge of Gods core logic (fuel, battery, modules, milestones)
    }

    // ==================== Facing ====================

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    public boolean isValidFrontFacing(EnumFacing facing) {
        return facing != null && (!hasFrontFacing() || getFrontFacing() != facing);
    }

    // ==================== Data Access ====================

    public ForgeOfGodsData getData() {
        return data;
    }

    public void updateRenderer() {}

    public void destroyRenderer() {}

    // ==================== NBT ====================

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        NBTTagCompound tag = super.writeToNBT(data);
        this.data.writeToNBT(tag, false);
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.data.readFromNBT(data);
        reinitializeStructurePattern();
    }
}
