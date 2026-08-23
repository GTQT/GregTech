package gregtech.api.worldgen.config;

import gregtech.api.unification.material.Material;
import gregtech.api.util.WorldBlockPredicate;
import gregtech.api.worldgen.filler.BlockFiller;
import gregtech.api.worldgen.filler.FillerEntry;
import gregtech.api.worldgen.filler.LayeredBlockFiller;
import gregtech.api.worldgen.filler.SimpleBlockFiller;
import gregtech.api.worldgen.populator.FluidBallPopulator;
import gregtech.api.worldgen.populator.FluidSpringPopulator;
import gregtech.api.worldgen.populator.IVeinPopulator;
import gregtech.api.worldgen.populator.SurfaceBlockPopulator;
import gregtech.api.worldgen.populator.SurfaceRockPopulator;
import gregtech.api.worldgen.shape.EllipsoidGenerator;
import gregtech.api.worldgen.shape.LayeredGenerator;
import gregtech.api.worldgen.shape.PlateGenerator;
import gregtech.api.worldgen.shape.ShapeGenerator;
import gregtech.api.worldgen.shape.SingleBlockGenerator;
import gregtech.api.worldgen.shape.SphereGenerator;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.blocks.StoneVariantBlock;
import gregtech.common.blocks.StoneVariantBlock.StoneVariant;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraftforge.fluids.Fluid;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * 普通矿脉定义 builder：纯代码注册
 */
public class OreDepositBuilder extends DepositBuilder<OreDepositBuilder, OreDepositDefinition> {

    private int minHeight = Integer.MIN_VALUE;
    private int maxHeight = Integer.MAX_VALUE;
    private float density;
    private WorldBlockPredicate generationPredicate = OreDepositDefinition.PREDICATE_STONE_TYPE;
    private IVeinPopulator veinPopulator;
    private BlockFiller blockFiller;
    private ShapeGenerator shapeGenerator;

    public static OreDepositBuilder definitionBuilder(String depositName) {
        return new OreDepositBuilder(depositName);
    }

    private OreDepositBuilder(String depositName) {
        super(depositName);
    }

    @Override
    public OreDepositBuilder getThis() {
        return this;
    }

    public OreDepositBuilder density(float density) {
        this.density = density;
        return getThis();
    }

    public OreDepositBuilder minHeight(int minHeight) {
        this.minHeight = minHeight;
        return getThis();
    }

    public OreDepositBuilder maxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        return getThis();
    }

    public OreDepositBuilder generationPredicate(WorldBlockPredicate predicate) {
        this.generationPredicate = predicate;
        return getThis();
    }

    /** 任意方块可替换（raw_oil_sphere 用） */
    public OreDepositBuilder generationPredicateAny() {
        this.generationPredicate = PredicateConfigUtils.any();
        return getThis();
    }

    public OreDepositBuilder surfaceRock(Material material) {
        this.veinPopulator = new SurfaceRockPopulator(material);
        return getThis();
    }

    public OreDepositBuilder surfaceBlock(IBlockState blockState) {
        this.veinPopulator = new SurfaceBlockPopulator(blockState);
        return getThis();
    }

    public OreDepositBuilder fluidSpring(IBlockState fluidState, float chance) {
        this.veinPopulator = new FluidSpringPopulator(fluidState, chance);
        return getThis();
    }

    public OreDepositBuilder fluidBall(IBlockState fluidState, float chance) {
        this.veinPopulator = new FluidBallPopulator(fluidState, chance);
        return getThis();
    }

    public OreDepositBuilder layeredGeneration(int radiusMin, int radiusMax) {
        this.shapeGenerator = new LayeredGenerator(radiusMin, radiusMax);
        return getThis();
    }

    public OreDepositBuilder sphereGeneration(int radiusMin, int radiusMax) {
        this.shapeGenerator = new SphereGenerator(radiusMin, radiusMax);
        return getThis();
    }

    public OreDepositBuilder ellipsoidGeneration(int radiusMin, int radiusMax) {
        this.shapeGenerator = new EllipsoidGenerator(radiusMin, radiusMax);
        return getThis();
    }

    public OreDepositBuilder plateGeneration(int minLength, int maxLength, int minDepth, int maxDepth,
                                             int minHeight, int maxHeight) {
        this.shapeGenerator = new PlateGenerator(minLength, maxLength, minDepth, maxDepth, minHeight, maxHeight);
        return getThis();
    }

    public OreDepositBuilder singleBlockGeneration(int minBlocksCount, int maxBlocksCount) {
        this.shapeGenerator = new SingleBlockGenerator(minBlocksCount, maxBlocksCount);
        return getThis();
    }

    /** 标准 4 材料分层填充（primary/secondary/between/sporadic） */
    public OreDepositBuilder layeredFill(Material primary, Material secondary, Material between,
                                         Material sporadic) {
        this.blockFiller = new LayeredBlockFiller(new FillerConfigUtils.LayeredFillerEntry(
                new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(primary)),
                new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(secondary)),
                new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(between)),
                new FillerConfigUtils.OreFilterEntry(OreConfigUtils.getOreForMaterial(sporadic))));
        return getThis();
    }

    /** 通用分层填充（自定义层数） */
    public OreDepositBuilder layeredFill(FillerEntry primary, FillerEntry secondary, FillerEntry between,
                                         FillerEntry sporadic, int primaryLayers, int secondaryLayers,
                                         int betweenLayers) {
        this.blockFiller = new LayeredBlockFiller(new FillerConfigUtils.LayeredFillerEntry(
                primary, secondary, between, sporadic, primaryLayers, secondaryLayers, betweenLayers));
        return getThis();
    }

    public OreDepositBuilder simpleFill(IBlockState blockState) {
        this.blockFiller = new SimpleBlockFiller(FillerEntry.createSimpleFiller(blockState));
        return getThis();
    }

    /** 按权重随机填充 */
    public OreDepositBuilder weightRandomFill(List<Pair<Integer, FillerEntry>> entries) {
        this.blockFiller = new SimpleBlockFiller(new FillerConfigUtils.WeightRandomMatcherEntry(entries));
        return getThis();
    }

    /** 忽略基岩的填充（sphere 矿脉用） */
    public OreDepositBuilder ignoreBedrockFill(FillerEntry filler) {
        this.blockFiller = new gregtech.api.worldgen.filler.BlacklistedBlockFiller(
                ImmutableList.of(Blocks.BEDROCK.getDefaultState()), filler);
        return getThis();
    }

    /** 石材 sphere 填充（stone_smooth 变体） */
    public OreDepositBuilder stoneSmoothSphereFill(StoneVariantBlock.StoneType stoneType) {
        IBlockState state = MetaBlocks.STONE_BLOCKS.get(StoneVariant.SMOOTH).getState(stoneType);
        return ignoreBedrockFill(FillerEntry.createSimpleFiller(state));
    }

    /** 流体填充（raw_oil_sphere 用） */
    public OreDepositBuilder fluidFill(Fluid fluid) {
        return ignoreBedrockFill(FillerEntry.createSimpleFiller(fluid.getBlock().getDefaultState()));
    }

    /** 构建并注册到 WorldGenRegistry（addon 推荐入口） */
    public void buildAndRegister(WorldGenRegistry registry) {
        registry.addVeinDefinitions(build());
    }

    @Override
    public OreDepositDefinition createDefinition() {
        return new OreDepositDefinition(depositName);
    }

    @Override
    public void verifyProperties() {
        if (weight == 0) {
            throw new IllegalStateException("OreDepositBuilder " + depositName + " doesn't have a weight!");
        }
        if (density == 0.0f) {
            throw new IllegalStateException("OreDepositBuilder " + depositName + " doesn't have a density!");
        }
        if (blockFiller == null) {
            throw new IllegalStateException("OreDepositBuilder " + depositName + " doesn't have a filler!");
        }
        if (shapeGenerator == null) {
            throw new IllegalStateException("OreDepositBuilder " + depositName + " doesn't have a generator!");
        }
    }

    @Override
    public OreDepositDefinition build() {
        OreDepositDefinition definition = super.build();
        definition.setDensity(density);
        definition.setHeightLimit(minHeight, maxHeight);
        definition.setGenerationPredicate(generationPredicate);
        definition.setVeinPopulator(veinPopulator);
        definition.setBlockFiller(blockFiller);
        definition.setShapeGenerator(shapeGenerator);
        return definition;
    }
}
