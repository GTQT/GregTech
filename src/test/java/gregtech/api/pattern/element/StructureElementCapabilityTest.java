package gregtech.api.pattern.element;

import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.impl.AnyElement;
import gregtech.api.pattern.element.impl.ChainElement;
import gregtech.api.pattern.element.impl.LegacyElement;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.Vec3i;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StructureElementCapabilityTest {

    @Test
    void directAndLegacyElementsAdvertiseSnapshotSupportExplicitly() {
        assertTrue(AnyElement.INSTANCE.supports(
                StructureElementCapability.SNAPSHOT_MATCH));
        assertFalse(new LegacyElement(TraceabilityPredicate.ANY).supports(
                StructureElementCapability.SNAPSHOT_MATCH));
    }

    @Test
    void chainUsesTheConservativeCapabilityIntersection() {
        ChainElement direct = new ChainElement(
                AnyElement.INSTANCE, AnyElement.INSTANCE);
        ChainElement mixed = new ChainElement(
                AnyElement.INSTANCE, new LegacyElement(TraceabilityPredicate.ANY));

        assertTrue(direct.supports(StructureElementCapability.SNAPSHOT_MATCH));
        assertFalse(mixed.supports(StructureElementCapability.SNAPSHOT_MATCH));
    }

    @Test
    void noPlacementPreservesMatchingButRemovesPlacementCapabilities() {
        IStructureElementNoPlacement<Object> element =
                AnyElement.INSTANCE.noPlacement();

        assertTrue(element.supports(StructureElementCapability.SNAPSHOT_MATCH));
        assertFalse(element.supports(
                StructureElementCapability.CREATIVE_PLACEMENT));
        assertFalse(element.supports(
                StructureElementCapability.SURVIVAL_PLACEMENT));
    }

    @Test
    void conditionalPiecesRequireLiveWorldFallback() {
        StructureDefinition<?> direct = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .piece("main", Vec3i.NULL_VECTOR, "S")
                .where('S', Elements.any())
                .end()
                .build();
        StructureDefinition<?> conditional = StructureDefinition.builder(
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.FRONT)
                .conditionalPiece("main", new String[][]{{"S"}},
                        Vec3i.NULL_VECTOR, () -> true)
                .where('S', Elements.any())
                .end()
                .build();

        assertTrue(direct.supportsElementCapability(
                StructureElementCapability.SNAPSHOT_MATCH));
        assertFalse(conditional.supportsElementCapability(
                StructureElementCapability.SNAPSHOT_MATCH));
    }
}
