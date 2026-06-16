package gregtech.api.pattern.element;

import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureCompilerStrategyTest {

    @Test
    void solidRepeatablePieceUsesIndependent1D() {
        String[][] pattern = new String[][] {
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        },
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        },
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        }
                };

        assertTrue(StructureCompiler.isTensorProduct(testPiece(pattern)));
        assertTrue(StructureCompiler.isAxisSeparable(testPiece(pattern)));
        RepeatGroupPiece piece = compileRepeat(pattern);
        assertEquals(StructureCompiler.SearchStrategy.INDEPENDENT_1D, piece.getSearchStrategy());
    }

    @Test
    void hollowShellUsesIndependent1DWithoutBeingTensor() {
        String[][] pattern = new String[][] {
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        },
                        {
                                "XXX",
                                "X X",
                                "XXX"
                        },
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        }
                };

        TestPiece definition = testPiece(pattern);
        assertFalse(StructureCompiler.isTensorProduct(definition));
        assertTrue(StructureCompiler.isAxisSeparable(definition));
        RepeatGroupPiece piece = compileRepeat(pattern);
        assertEquals(StructureCompiler.SearchStrategy.INDEPENDENT_1D, piece.getSearchStrategy());
    }

    @Test
    void rectangularFrameUsesIndependent1DWithoutBeingTensor() {
        String[][] pattern = new String[][] {
                        {
                                "XXX",
                                "X X",
                                "XXX"
                        }
                };

        TestPiece definition = testPiece(pattern);
        assertFalse(StructureCompiler.isTensorProduct(definition));
        assertTrue(StructureCompiler.isAxisSeparable(definition));
        RepeatGroupPiece piece = compileRepeat(pattern);
        assertEquals(StructureCompiler.SearchStrategy.INDEPENDENT_1D, piece.getSearchStrategy());
    }

    @Test
    void layeredShapeUsesIndependent1DWithoutBeingTensor() {
        String[][] pattern = new String[][] {
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        },
                        {
                                "   ",
                                "   ",
                                "   "
                        },
                        {
                                "XXX",
                                "XXX",
                                "XXX"
                        }
                };

        TestPiece definition = testPiece(pattern);
        assertFalse(StructureCompiler.isTensorProduct(definition));
        assertTrue(StructureCompiler.isAxisSeparable(definition));
        RepeatGroupPiece piece = compileRepeat(pattern);
        assertEquals(StructureCompiler.SearchStrategy.INDEPENDENT_1D, piece.getSearchStrategy());
    }

    @Test
    void coupledSparseShapeFallsBackToNestedBacktracking() {
        String[][] pattern = new String[][] {
                        {
                                "X  ",
                                "   ",
                                "  X"
                        },
                        {
                                "   ",
                                " X ",
                                "   "
                        },
                        {
                                "  X",
                                "   ",
                                "X  "
                        }
                };

        TestPiece definition = testPiece(pattern);
        assertFalse(StructureCompiler.isTensorProduct(definition));
        assertFalse(StructureCompiler.isAxisSeparable(definition));
        RepeatGroupPiece piece = compileRepeat(pattern);
        assertEquals(StructureCompiler.SearchStrategy.NESTED_BACKTRACKING, piece.getSearchStrategy());
    }

    private static RepeatGroupPiece compileRepeat(String[][] pattern) {
        StructureDefinition<?> definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .repeatablePiece("repeat", pattern, Vec3i.NULL_VECTOR)
                .where('X', Elements.any())
                .repeatAxes(0, 1, 2)
                .repeatRange(1, 3, 1, 3, 1, 3)
                .stepSizes(1, 1, 1)
                .end()
                .build();
        StructurePiece piece = definition.getCompiledPattern().getPrimaryPiece();
        assertInstanceOf(RepeatGroupPiece.class, piece);
        return (RepeatGroupPiece) piece;
    }

    private static TestPiece testPiece(String[][] pattern) {
        Map<Character, IStructureElement> symbols = new HashMap<>();
        symbols.put('X', Elements.any());
        return new TestPiece(pattern, symbols);
    }

    private static final class TestPiece implements IStructurePiece {

        private final String[][] pattern;
        private final Map<Character, IStructureElement> symbolMap;

        private TestPiece(String[][] pattern, Map<Character, IStructureElement> symbolMap) {
            this.pattern = pattern;
            this.symbolMap = symbolMap;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public String[][] getPattern() {
            return pattern;
        }

        @Override
        public Map<Character, IStructureElement> getSymbolMap() {
            return symbolMap;
        }

        @Override
        public int[] getRepeatAxes() {
            return new int[] { 0, 1, 2 };
        }

        @Override
        public int[][] getRepeatRanges() {
            return new int[][] { { 1, 3 }, { 1, 3 }, { 1, 3 } };
        }

        @Override
        public int[] getStepSizes() {
            return new int[] { 1, 1, 1 };
        }

        @Override
        public String[] getRepeatChannelNames() {
            return null;
        }

        @Override
        public int[] getCenterOffset() {
            return new int[] { 0, 0, 0 };
        }
    }
}
