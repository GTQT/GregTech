package gregtech.api.worldgen.shape;

import net.minecraft.util.math.Vec3i;

import java.util.Random;

/**
 * 大平板矿脉：把矿脉球拉伸成正方形再压扁——X/Z 用<strong>同一个</strong>半径（正方形 footprint，不再是球面判定），
 * Y 只有 {@code yRadius} 那么厚。半径掷法与 {@link SphereGenerator} 完全一致：{@code radiusMin == radiusMax}
 * 时取 {@code radiusMin}，否则取 {@code radiusMin + gridRandom.nextInt(radiusMax - radiusMin)}。
 *
 * <p>
 * 逐个方块走默认的 {@code generateBlock(x, y, z)} 通道，所以矿脉定义的 density 会在板上打出常规孔洞；
 * 想要实心板就在定义里把 density 设成 1.0。
 *
 * <p>
 * {@link #getMaxSize()} 返回真实包围盒 {@code (radiusMax * 2, yRadius * 2, radiusMax * 2)}（即各轴最大偏移的两倍）：
 * 生成引擎用 {@code getMaxSize().getY() / 2 + 4 = yRadius + 4} 收缩高度上限、用 X/Z 算贫矿壳，
 * 低估会把矿生成到高度上限之外。{@link PlateGenerator} 在这里返回了以 Z 深度充当的 Y，会导致矿脉根本不生成，
 * 所以本类不复用它。本类也不做旋转，正方形 footprint 天然不需要。
 *
 * <p>
 * 硬约束：生成引擎只在矿脉中心所在的 3×3 chunk 网格（中心 ±48 格）内落块，越界的方块会被静默丢弃，
 * 因此 radiusMax 不得大于 48。
 */
public class SlabGenerator extends ShapeGenerator {

    private static final int MAX_RADIUS = 48;

    private final int radiusMin;
    private final int radiusMax;
    private final int yRadius;

    /** 超薄平板：Y 半径默认 1（3 格厚） */
    public SlabGenerator(int radiusMin, int radiusMax) {
        this(radiusMin, radiusMax, 1);
    }

    /**
     * @param radiusMin X/Z 半径下限（格），> 0
     * @param radiusMax X/Z 半径上限（格），≤ 48
     * @param yRadius   Y 半径（格）：1 → 3 格厚，3 → 7 格厚
     */
    public SlabGenerator(int radiusMin, int radiusMax, int yRadius) {
        if (radiusMin <= 0 || radiusMax < radiusMin) {
            throw new IllegalArgumentException("Invalid slab radius range: " + radiusMin + ".." + radiusMax);
        }
        if (radiusMax > MAX_RADIUS) {
            throw new IllegalArgumentException("Slab radius " + radiusMax + " exceeds the supported maximum of " +
                    MAX_RADIUS + " blocks");
        }
        if (yRadius <= 0) {
            throw new IllegalArgumentException("Invalid slab y radius: " + yRadius);
        }
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.yRadius = yRadius;
    }

    public int getYRadius() {
        return yRadius;
    }

    @Override
    public Vec3i getMaxSize() {
        return new Vec3i(radiusMax * 2, yRadius * 2, radiusMax * 2);
    }

    @Override
    public void generate(Random gridRandom, IBlockGeneratorAccess relativeBlockAccess) {
        int radius = radiusMin >= radiusMax ? radiusMin : radiusMin + gridRandom.nextInt(radiusMax - radiusMin);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -yRadius; y <= yRadius; y++) {
                    relativeBlockAccess.generateBlock(x, y, z);
                }
            }
        }
    }
}
