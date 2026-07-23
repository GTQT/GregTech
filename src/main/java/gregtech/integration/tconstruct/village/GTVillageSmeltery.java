package gregtech.integration.tconstruct.village;

import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.StructureBoundingBox;
import net.minecraft.world.gen.structure.StructureComponent;
import net.minecraft.world.gen.structure.StructureVillagePieces;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.smeltery.TinkerSmeltery;
import slimeknights.tconstruct.smeltery.block.BlockFaucet;
import slimeknights.tconstruct.smeltery.block.BlockSmelteryController;

import java.util.List;
import java.util.Random;

/**
 * Village structure that places a small brick building containing a fully
 * assembled TiC Smeltery.
 *
 * <p>
 * The smeltery is 3×3 interior, 1 block wall thickness, 6 blocks tall.
 * It includes a controller, seared tank, IO block, faucet, casting table
 * and casting basin — everything needed to start melting ores.
 *
 * <p>
 * Weight is set to {@code (1, 1)} — rare but possible to encounter.
 * Registered via {@link GTVillageStructures#register()}.
 */
public class GTVillageSmeltery extends StructureVillagePieces.Village {

    private static final int WIDTH = 10;
    private static final int HEIGHT = 10;
    private static final int DEPTH = 10;

    private int averageGroundLevel = -1;

    /** Required by {@code MapGenStructureIO}. */
    public GTVillageSmeltery() {}

    private GTVillageSmeltery(StructureVillagePieces.Start start, int type, Random rand,
                              StructureBoundingBox boundingBox, EnumFacing facing) {
        super();
        this.setCoordBaseMode(facing);
        this.boundingBox = boundingBox;
    }

    @Override
    public boolean addComponentParts(World world, Random randomIn, StructureBoundingBox sbb) {
        if (this.averageGroundLevel < 0) {
            this.averageGroundLevel = this.getAverageGroundLevel(world, sbb);
            if (this.averageGroundLevel < 0) return true;
            // Shift the structure so its floor sits at ground level.
            this.boundingBox.offset(0, this.averageGroundLevel - this.boundingBox.maxY + 8, 0);
        }

        EnumFacing facing = this.getCoordBaseMode();
        if (facing == null) facing = EnumFacing.NORTH;

        // --- Floor (brick) ---
        this.fillWithBlocks(world, sbb, 1, 0, 0, 7, 0, 6,
                Blocks.BRICK_BLOCK.getDefaultState(), Blocks.BRICK_BLOCK.getDefaultState(), false);
        this.fillWithBlocks(world, sbb, 0, 0, 1, 0, 0, 5,
                Blocks.BRICK_BLOCK.getDefaultState(), Blocks.BRICK_BLOCK.getDefaultState(), false);
        this.fillWithBlocks(world, sbb, 8, 0, 1, 8, 0, 5,
                Blocks.BRICK_BLOCK.getDefaultState(), Blocks.BRICK_BLOCK.getDefaultState(), false);

        // --- Clear interior air ---
        this.fillWithBlocks(world, sbb, 0, 1, 0, 9, 3, 7,
                Blocks.AIR.getDefaultState(), Blocks.AIR.getDefaultState(), false);

        // --- Smeltery walls (seared brick, 3×3 interior × 6 high) ---
        this.fillWithBlocks(world, sbb, 2, 0, 1, 6, 6, 5,
                TinkerSmeltery.searedBlock.getStateFromMeta(3),
                TinkerSmeltery.searedBlock.getStateFromMeta(3), false);

        // --- Smeltery interior (hollow) ---
        this.fillWithBlocks(world, sbb, 3, 1, 2, 5, 6, 4,
                Blocks.AIR.getDefaultState(), Blocks.AIR.getDefaultState(), false);

        // --- Smeltery Controller ---
        this.fillWithBlocks(world, sbb, 4, 1, 1, 4, 1, 1,
                TinkerSmeltery.smelteryController.getDefaultState()
                        .withProperty(BlockSmelteryController.FACING, facing.getOpposite()),
                TinkerSmeltery.smelteryController.getDefaultState()
                        .withProperty(BlockSmelteryController.FACING, facing.getOpposite()), false);

        // --- Seared Tank (fuel input) ---
        this.fillWithBlocks(world, sbb, 5, 1, 1, 5, 1, 1,
                TinkerSmeltery.searedTank.getDefaultState(),
                TinkerSmeltery.searedTank.getDefaultState(), false);

        // --- Smeltery IO (drain/output) ---
        this.fillWithBlocks(world, sbb, 4, 2, 5, 5, 2, 5,
                TinkerSmeltery.smelteryIO.getDefaultState(),
                TinkerSmeltery.smelteryIO.getDefaultState(), false);

        // --- Faucets ---
        this.fillWithBlocks(world, sbb, 4, 2, 6, 5, 2, 6,
                TinkerSmeltery.faucet.getDefaultState()
                        .withProperty(BlockFaucet.FACING, facing.getOpposite()),
                TinkerSmeltery.faucet.getDefaultState()
                        .withProperty(BlockFaucet.FACING, facing.getOpposite()), false);

        // --- Casting Table ---
        this.fillWithBlocks(world, sbb, 4, 1, 6, 4, 1, 6,
                TinkerSmeltery.castingBlock.getStateFromMeta(0),
                TinkerSmeltery.castingBlock.getStateFromMeta(0), false);

        // --- Casting Basin ---
        this.fillWithBlocks(world, sbb, 5, 1, 6, 5, 1, 6,
                TinkerSmeltery.castingBlock.getStateFromMeta(1),
                TinkerSmeltery.castingBlock.getStateFromMeta(1), false);

        return true;
    }

    // ==================== Village Creation Handler ====================

    public static class CreationHandler implements VillagerRegistry.IVillageCreationHandler {

        @Override
        public StructureVillagePieces.PieceWeight getVillagePieceWeight(Random random, int size) {
            // Weight (1, 1) — rare, but possible to generate.
            return new StructureVillagePieces.PieceWeight(GTVillageSmeltery.class, 1, 1);
        }

        @Override
        public Class<?> getComponentClass() {
            return GTVillageSmeltery.class;
        }

        @Override
        public @Nullable StructureVillagePieces.Village buildComponent(
                StructureVillagePieces.PieceWeight pieceWeight,
                StructureVillagePieces.Start start,
                List<StructureComponent> components,
                Random random,
                int x, int y, int z,
                EnumFacing facing, int type) {
            StructureBoundingBox boundingBox = StructureBoundingBox
                    .getComponentToAddBoundingBox(x, y, z, 0, 0, 0,
                            WIDTH, HEIGHT, DEPTH, facing);
            return canVillageGoDeeper(boundingBox)
                    ? new GTVillageSmeltery(start, type, random, boundingBox, facing)
                    : null;
        }
    }
}
