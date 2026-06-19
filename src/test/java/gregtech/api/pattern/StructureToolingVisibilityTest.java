package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StructureToolingVisibilityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
    }

    @Test
    void runtimeOnlyPiecesStayCompiledButAreHiddenFromToolingIndexes() {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("main", Vec3i.NULL_VECTOR, "S")
                .where('S', block(Blocks.STONE.getDefaultState()))
                .end()
                .piece("rendered_air", new Vec3i(10, 0, 0), "A")
                .where('A', block(Blocks.DIRT.getDefaultState()))
                .runtimeOnly()
                .end()
                .piece("extra", new Vec3i(20, 0, 0), "E")
                .where('E', block(Blocks.COBBLESTONE.getDefaultState()))
                .end()
                .build();

        MultiPiecePattern pattern = definition.getCompiledPattern();

        assertEquals(3, pattern.getPieceCount());
        assertEquals(2, pattern.getToolingPieceCount());
        assertEquals(1, pattern.resolveToolingPieceIndex(1));
        assertEquals(3, pattern.resolveToolingPieceIndex(2));
        assertEquals(-1, pattern.resolveToolingPieceIndex(3));
        assertSame(pattern.getPiece("main"), pattern.getToolingPiece(1));
        assertSame(pattern.getPiece("extra"), pattern.getToolingPiece(2));
        assertNull(pattern.getToolingPiece(3));
    }

    @Test
    void multiPiecePreviewIndexesOnlyVisiblePiecesAndKeepsHiddenMetadataForLaterPieces() {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("main", Vec3i.NULL_VECTOR, "S")
                .where('S', block(Blocks.STONE.getDefaultState()))
                .end()
                .piece("rendered_air", new Vec3i(10, 0, 0), "A")
                .where('A', block(Blocks.DIRT.getDefaultState()))
                .runtimeOnly()
                .end()
                .piece("extra", new Vec3i(20, 0, 0), "E")
                .where('E', block(Blocks.COBBLESTONE.getDefaultState()))
                .end()
                .build();
        MultiPiecePattern pattern = definition.getCompiledPattern();

        MultiPiecePreviewAssembler.Result preview = MultiPiecePreviewAssembler.assemble(
                pattern, new PieceRuntimes(pattern), Collections.emptyMap(), null);
        MultiPiecePreviewAssembler.PieceResult extraPreview = preview.getPiece(2);

        assertNotNull(extraPreview.getPrior().getPieceCenter("rendered_air"));
        StructureOrientation orientation = StructureOrientation.of(
                net.minecraft.util.EnumFacing.SOUTH,
                net.minecraft.util.EnumFacing.SOUTH,
                net.minecraft.util.EnumFacing.NORTH,
                false, false);
        assertEquals(pattern.getPiece("extra").getCenterPos(
                        BlockPos.ORIGIN, orientation, extraPreview.getPrior()),
                MultiPiecePreviewAssembler.resolveWorldPieceCenter(
                        pattern, 2, extraPreview.getPrior(), BlockPos.ORIGIN, orientation, null));
        assertEquals(2, countVisibleBlocks(preview.getShape().getBlocks()));
    }

    @Test
    void forcedToolingPiecePreviewIgnoresRuntimeActivationCondition() {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("main", Vec3i.NULL_VECTOR, "M")
                .where('M', block(Blocks.STONE.getDefaultState()))
                .end()
                .conditionalPiece("locked", new String[][]{{"L"}}, new Vec3i(0, 0, -4), () -> false)
                .where('L', block(Blocks.DIRT.getDefaultState()))
                .end()
                .piece("extra", new Vec3i(0, 0, -8), "E")
                .where('E', block(Blocks.COBBLESTONE.getDefaultState()))
                .end()
                .build();
        MultiPiecePattern pattern = definition.getCompiledPattern();
        PieceRuntimes runtimes = new PieceRuntimes(pattern);

        MultiPiecePreviewAssembler.Result defaultPreview = MultiPiecePreviewAssembler.assemble(
                pattern, runtimes, Collections.emptyMap(), null, false, 0);
        MultiPiecePreviewAssembler.Result forcedPreview = MultiPiecePreviewAssembler.assemble(
                pattern, runtimes, Collections.emptyMap(), null, false, 2);
        MultiPiecePreviewAssembler.Result projectorDefault = MultiPiecePreviewAssembler.assemble(
                pattern, runtimes, Collections.emptyMap(), null, false,
                MultiPiecePreviewAssembler.DEFAULT_TOOLING_PIECES);

        assertEquals(0, countVisibleBlocks(defaultPreview.getPiece(2).getShape().getBlocks()));
        assertEquals(1, countVisibleBlocks(forcedPreview.getPiece(2).getShape().getBlocks()));
        assertEquals(2, countVisibleBlocks(projectorDefault.getShape().getBlocks()));
        assertEquals(0, countVisibleBlocks(projectorDefault.getPiece(3).getShape().getBlocks()));
    }

    @Test
    void combinedPreviewReportsNormalizedControllerOrigin() {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("left", new Vec3i(4, 0, 0), "L")
                .where('L', block(Blocks.DIRT.getDefaultState()))
                .end()
                .piece("main", Vec3i.NULL_VECTOR, "S")
                .where('S', block(Blocks.STONE.getDefaultState()))
                .end()
                .build();
        MultiPiecePattern pattern = definition.getCompiledPattern();

        MultiPiecePreviewAssembler.Result preview = MultiPiecePreviewAssembler.assemble(
                pattern, new PieceRuntimes(pattern), Collections.emptyMap(), null);

        assertEquals(new BlockPos(4, 0, 0), preview.getCenter());
        assertEquals(2, countVisibleBlocks(preview.getShape().getBlocks()));
    }

    @Test
    void laterPieceAirDoesNotEraseEarlierSolidPreviewBlock() {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("solid", Vec3i.NULL_VECTOR, "S")
                .where('S', block(Blocks.STONE.getDefaultState()))
                .end()
                .piece("air", Vec3i.NULL_VECTOR, "A")
                .where('A', block(Blocks.AIR.getDefaultState()))
                .end()
                .build();
        MultiPiecePattern pattern = definition.getCompiledPattern();

        MultiPiecePreviewAssembler.Result preview = MultiPiecePreviewAssembler.assemble(
                pattern, new PieceRuntimes(pattern), Collections.emptyMap(), null);

        assertEquals(1, countVisibleBlocks(preview.getShape().getBlocks()));
    }

    @Test
    void relativeBackOffsetCanReproduceConcatenatedFrontTemplatePosition() {
        assertSplitOffsetMatchesConcatenatedTemplate(StructureOrientation.of(
                EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.NORTH, false, false));
        assertSplitOffsetMatchesConcatenatedTemplate(StructureOrientation.of(
                EnumFacing.DOWN, EnumFacing.UP, EnumFacing.WEST, false, false));
    }

    private static void assertSplitOffsetMatchesConcatenatedTemplate(StructureOrientation orientation) {
        int frontDistance = 59;
        BlockPos concatenatedPosition = RelativeDirection.setActualRelativeOffset(
                0, 0, frontDistance,
                orientation.getStructureFront(), orientation.getUp(), orientation.isFlipped(),
                new RelativeDirection[] {
                        RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT
                });
        BlockPos splitPiecePosition = OffsetMode.RELATIVE.apply(
                BlockPos.ORIGIN, new int[] { 0, 0, -frontDistance }, orientation);
        BlockPos physicalBack = new BlockPos(orientation.getFront().getOpposite().getDirectionVec());
        BlockPos templateForward = RelativeDirection.setActualRelativeOffset(
                0, 0, 1,
                orientation.getStructureFront(), orientation.getUp(), orientation.isFlipped(),
                new RelativeDirection[] {
                        RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT
                });

        assertEquals(concatenatedPosition, splitPiecePosition);
        assertEquals(physicalBack, templateForward);
    }

    private static IStructureElement<Object> block(net.minecraft.block.state.IBlockState state) {
        return new IStructureElement<Object>() {
            @Override
            public boolean check(StructureEvaluationContext<Object> context) {
                return true;
            }

            @Override
            public BlockInfo[] getCandidates() {
                return new BlockInfo[] { new BlockInfo(state, null) };
            }
        };
    }

    private static int countVisibleBlocks(BlockInfo[][][] blocks) {
        int count = 0;
        for (BlockInfo[][] aisle : blocks) {
            for (BlockInfo[] row : aisle) {
                for (BlockInfo info : row) {
                    if (info != null
                            && info != BlockInfo.EMPTY
                            && info.getBlockState() != null
                            && info.getBlockState().getBlock() != Blocks.AIR) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
