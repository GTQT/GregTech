package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
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
