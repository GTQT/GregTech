package gregtech.api.pattern.element;

import gregtech.api.pattern.StructureSizeDescriptor;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureDefinitionSizeDescriptorTest {

    @Test
    void includesEverySequentialRepeatablePieceInLengthRange() {
        StructureDefinition definition = StructureDefinition.builder(
                        RelativeDirection.BACK,
                        RelativeDirection.UP,
                        RelativeDirection.RIGHT)
                .piece("cap1", Vec3i.NULL_VECTOR, "XXX", "XXX", "XXX")
                    .where('X', Elements.any())
                    .end()
                .repeatablePiece("body1", Vec3i.NULL_VECTOR, "XXX", "XXX", "XXX")
                    .where('X', Elements.any())
                    .repeatAxes(2)
                    .repeatRange(1, 3)
                    .stepSizes(1)
                    .end()
                .piece("middle", Vec3i.NULL_VECTOR, "XXX", "XXX", "XXX")
                    .where('X', Elements.any())
                    .end()
                .repeatablePiece("body2", Vec3i.NULL_VECTOR, "XXX", "XXX", "XXX")
                    .where('X', Elements.any())
                    .repeatAxes(2)
                    .repeatRange(1, 3)
                    .stepSizes(1)
                    .end()
                .piece("cap2", Vec3i.NULL_VECTOR, "XXX", "XXX", "XXX")
                    .where('X', Elements.any())
                    .end()
                .build();

        StructureSizeDescriptor size = definition.getStructureSizeDescriptor();
        assertEquals("3", size.getFormattedPalm());
        assertEquals("3", size.getFormattedThumb());
        assertEquals("5..9", size.getFormattedFinger());
    }

    @Test
    void includesMultiAxisRepeatRanges() {
        StructureDefinition definition = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK)
                .repeatablePiece("volume", new String[][] {
                        { "XXX", "XXX" },
                        { "XXX", "XXX" }
                }, Vec3i.NULL_VECTOR)
                    .where('X', Elements.any())
                    .repeatAxes(0, 1, 2)
                    .repeatRange(1, 5, 1, 7, 1, 4)
                    .stepSizes(3, 2, 2)
                    .end()
                .build();

        StructureSizeDescriptor size = definition.getStructureSizeDescriptor();
        assertEquals("3..15", size.getFormattedPalm());
        assertEquals("2..14", size.getFormattedThumb());
        assertEquals("2..8", size.getFormattedFinger());
    }
}
