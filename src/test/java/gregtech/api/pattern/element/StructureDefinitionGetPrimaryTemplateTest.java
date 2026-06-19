package gregtech.api.pattern.element;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.RepeatGroupPiece;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StructureDefinition#getPrimaryTemplate()} and the related
 * single-template / multi-piece detection logic used by the
 * {@code DeclarativePatternBuilder.buildTemplate()} delegation.
 *
 * <p>Covers: single-piece detection, repeatable-piece exclusion, multi-piece
 * behaviour, and incomplete repeatable declarations.
 */
class StructureDefinitionGetPrimaryTemplateTest {

    @Nested
    @DisplayName("supportsSingleTemplatePath()")
    class SupportsSingleTemplatePathTests {

        @Test
        @DisplayName("Single non-repeatable piece is a single-piece definition")
        void singlePieceIsDetected() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .piece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                    .end()
                    .build();
            assertTrue(def.supportsSingleTemplatePath(),
                    "One non-repeatable piece should support the single-template path");
        }

        @Test
        @DisplayName("Multiple pieces form a multi-piece definition")
        void multiPieceIsDetected() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .piece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                    .end()
                    .piece("aux", Vec3i.NULL_VECTOR, "YYY", "YSY", "YYY")
                    .end()
                    .build();
            assertFalse(def.supportsSingleTemplatePath(),
                    "Two pieces should require the multi-piece path");
        }
    }

    @Nested
    @DisplayName("getPrimaryTemplate()")
    class GetPrimaryTemplateTests {

        @Test
        @DisplayName("Single-piece definition returns the primary piece's template")
        void singlePieceReturnsTemplate() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .piece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                    .where('X', Elements.any())
                    .where('S', Elements.any())
                    .end()
                    .build();
            BlockPatternTemplate template = def.getPrimaryTemplate();
            assertNotNull(template, "Single-piece definition should expose its primary template");
            assertEquals(3, template.getXLength(), "Template should reflect the aisle's x size");
            assertEquals(3, template.getYLength(), "Template should reflect the aisle's y size");
            assertEquals(1, template.getZLength(), "Template should reflect the aisle count");
        }

        @Test
        @DisplayName("Multi-piece definition returns null (callers must use buildStructureDefinition)")
        void multiPieceReturnsNull() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .piece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                    .end()
                    .piece("aux", Vec3i.NULL_VECTOR, "YYY", "YSY", "YYY")
                    .end()
                    .build();
            BlockPatternTemplate template = def.getPrimaryTemplate();
            assertNull(template, "Multi-piece definition should NOT expose a primary template; "
                    + "callers must use buildStructureDefinition() and iterate the compiled pattern.");
        }

        @Test
        @DisplayName("Repeatable declaration without an axis is rejected")
        void repeatablePieceWithoutAxisIsRejected() {
            IllegalStateException error = assertThrows(IllegalStateException.class, () ->
                    StructureDefinition.builder(
                                    RelativeDirection.RIGHT,
                                    RelativeDirection.UP,
                                    RelativeDirection.FRONT)
                            .repeatablePiece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                            .end()
                            .build());
            assertTrue(error.getMessage().contains("requires at least one repeat axis"));
        }

        @Test
        @DisplayName("Single-axis repeatable piece requires the multi-piece runtime")
        void singleAxisRepeatablePieceRequiresMultiPieceRuntime() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .repeatablePiece("main", Vec3i.NULL_VECTOR, "XXX", "XSX", "XXX")
                    .where('X', Elements.any())
                    .where('S', Elements.any())
                    .repeatAxes(2)
                    .repeatRange(1, 3)
                    .stepSizes(1)
                    .end()
                    .build();

            assertFalse(def.supportsSingleTemplatePath(),
                    "A RepeatGroupPiece cannot use the single-template runtime");
            assertNull(def.getPrimaryTemplate(),
                    "A repeatable piece must not expose a fixed primary template");
            assertInstanceOf(RepeatGroupPiece.class, def.getCompiledPattern().getPrimaryPiece());
        }

        @Test
        @DisplayName("Three-axis repeatable piece remains native RepeatGroupPiece")
        void threeAxisRepeatablePieceKeepsNativeRepeatAxes() {
            StructureDefinition def = StructureDefinition.builder(
                            RelativeDirection.RIGHT,
                            RelativeDirection.UP,
                            RelativeDirection.FRONT)
                    .repeatablePiece("main", Vec3i.NULL_VECTOR, "X")
                    .where('X', Elements.any())
                    .repeatAxes(0, 1, 2)
                    .repeatRange(1, 3, 2, 4, 1, 2)
                    .stepSizes(1, 2, 3)
                    .end()
                    .build();

            List<StructurePiece> pieces = def.getCompiledPattern().getPieceList();
            assertEquals(1, pieces.size(), "One repeatable definition should compile to one piece");
            assertInstanceOf(RepeatGroupPiece.class, pieces.get(0),
                    "Multi-axis repeatable pieces must keep the native RepeatGroupPiece path");
            RepeatGroupPiece repeat = (RepeatGroupPiece) pieces.get(0);
            assertArrayEquals(new int[] { 0, 1, 2 }, repeat.getRepeatAxes(),
                    "All three repeat axes must be preserved");
            assertArrayEquals(new int[] { 1, 2, 3 }, repeat.getStepSizes(),
                    "Per-axis step sizes must be preserved");
            assertArrayEquals(new int[][] { { 1, 3 }, { 2, 4 }, { 1, 2 } }, repeat.getRepeatRanges(),
                    "Per-axis repeat ranges must be preserved");
        }
    }
}
