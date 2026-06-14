package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureCheckState;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StructureFailureDiagnosticsTest {

    private static final MultiblockAbility<Object> TEST_ABILITY =
            new MultiblockAbility<>("test_failure_diagnostics_ability", Object.class);
    private static World world;

    @BeforeAll
    static void bootstrapMinecraft() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
        world = bareWorld();
    }

    @Test
    void failureSelectionPrefersMissingAbilityOverLaterBlockMismatch() {
        StructureFailureTrace missingAbility = trace(StructureFailureTrace.Kind.MISSING_ABILITY, false, 1);
        StructureFailureTrace blockMismatch = trace(StructureFailureTrace.Kind.BLOCK_MISMATCH, true, 2);

        assertSame(missingAbility, StructureFailureSelection.select(missingAbility, blockMismatch));
    }

    @Test
    void failureSelectionUsesStableTieBreakBeforeSequence() {
        StructureFailureTrace unflipped = trace(StructureFailureTrace.Kind.BLOCK_MISMATCH, false, 1);
        StructureFailureTrace flipped = trace(StructureFailureTrace.Kind.BLOCK_MISMATCH, true, 1);

        assertSame(unflipped, StructureFailureSelection.select(unflipped, flipped));
    }

    @Test
    void commitRejectionDoesNotReplaceMoreUsefulCurrentMismatch() {
        StructureRuntime runtime = new StructureRuntime(null, null, null, null, null);
        StructureFailureTrace mismatch = trace(StructureFailureTrace.Kind.MISSING_ABILITY, false, 3);
        StructureFailureTrace rejection = trace(StructureFailureTrace.Kind.COMMIT_REJECTION, false, 4);

        runtime.recordLifecycleFailure(mismatch);
        runtime.recordLifecycleFailure(rejection);

        assertSame(mismatch, runtime.getLastFailure());
    }

    @Test
    void newCheckFailureReplacesOlderFailureEvenWhenLowerPriority() {
        StructureRuntime runtime = new StructureRuntime(null, null, null, null, null);
        StructureFailureTrace oldMissingAbility = trace(StructureFailureTrace.Kind.MISSING_ABILITY, false, 3);
        StructureFailureTrace currentMismatch = trace(StructureFailureTrace.Kind.BLOCK_MISMATCH, false, 1);

        runtime.recordCheckFailure(oldMissingAbility, Collections.emptyMap());
        runtime.recordCheckFailure(currentMismatch, Collections.emptyMap());

        assertSame(currentMismatch, runtime.getLastFailure());
    }

    @Test
    void flippedBlockMismatchDoesNotCoverUnflippedMissingAbility() {
        MissingAbilityElement missingAbilityElement = new MissingAbilityElement(new BlockPos(1, 0, 0));
        StructurePiece piece = new StructurePiece(
                "diagnostic", template(missingAbilityElement), Vec3i.NULL_VECTOR, OffsetMode.RELATIVE, null);
        Map<MultiblockAbility<?>, int[]> abilityLimits = new HashMap<>();
        abilityLimits.put(TEST_ABILITY, new int[] { 1, -1 });
        StructureDefinition<?> definition = StructureDefinition.fromMultiPiecePattern(
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new MultiPiecePattern(Collections.singletonList(piece), abilityLimits));

        StructureCheckState.Result result = definition.createState().check(
                world,
                BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, true),
                null,
                null);

        assertNotNull(result.failureTrace);
        assertEquals(StructureFailureTrace.Kind.MISSING_ABILITY, result.failureTrace.getKind());
        assertEquals("diagnostic", result.failureTrace.getPiece());
        assertEquals("{test_failure_diagnostics_ability=1}", result.failureTrace.getMissingAbilities());
        assertEquals("{test_failure_diagnostics_ability=0}", result.failureTrace.getAbilityCounts());
    }

    @NotNull
    private static StructureFailureTrace trace(StructureFailureTrace.Kind kind, boolean flipped, int progressDepth) {
        return new StructureFailureTrace.Builder("test", BlockPos.ORIGIN)
                .orientation(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, flipped)
                .path("test")
                .operation("CHECK")
                .kind(kind)
                .result(kind.getTraceName())
                .piece("piece")
                .cell("cell")
                .progressDepth(progressDepth)
                .build();
    }

    @NotNull
    private static PieceTemplate template(@NotNull IStructureElement<?> element) {
        TraceabilityPredicate center = new TraceabilityPredicate().setCenter();
        TraceabilityPredicate other = TraceabilityPredicate.ANY;
        return new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { center, other }
                        }
                },
                new IStructureElement<?>[][][] {
                        {
                                { new AlwaysElement(), element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
    }

    @NotNull
    private static World bareWorld() {
        try {
            return (World) unsafe().allocateInstance(BareWorld.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate bare test world", e);
        }
    }

    @NotNull
    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access Unsafe", e);
        }
    }

    private static final class MissingAbilityElement implements IStructureElement<Object> {

        private final BlockPos allowedPos;

        private MissingAbilityElement(@NotNull BlockPos allowedPos) {
            this.allowedPos = allowedPos;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return allowedPos.equals(context.getPos());
        }

        @Override
        public void collectRequirements(@NotNull StructureEvaluationContext<Object> context) {
            context.getCollector().declareAbility(this, TEST_ABILITY, 1, -1);
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] { new BlockInfo(Blocks.STONE.getDefaultState(), null) };
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}
    }

    private static final class AlwaysElement implements IStructureElement<Object> {

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return true;
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}
    }

    private static final class BareWorld extends World {

        private BareWorld() {
            super(null, null, null, null, false);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockPos getSpawnPoint() {
            return BlockPos.ORIGIN;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }
}
