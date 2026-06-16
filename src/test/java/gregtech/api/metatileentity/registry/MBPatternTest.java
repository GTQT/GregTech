package gregtech.api.metatileentity.registry;

import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.pattern.element.StructureElementPreview;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MBPatternTest {

    @Test
    void typedPreviewEntryIsReturnedDirectly() {
        BlockPos pos = BlockPos.ORIGIN;
        TraceabilityPredicate mapPredicate = new TraceabilityPredicate(state -> true);
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        predicateMap.put(pos, mapPredicate);
        StructureElementPreviewEntry entry = StructureElementPreviewEntry.of(
                StructureElementPreview.empty(),
                Collections.singletonList("typed tooltip"));
        Map<BlockPos, StructureElementPreviewEntry> previewEntries = new HashMap<>();
        previewEntries.put(pos, entry);

        MBPattern pattern = new MBPattern(null, Collections.emptyList(), predicateMap, previewEntries);

        assertSame(entry, pattern.getPreviewEntry(pos));
    }

    @Test
    void missingTypedPreviewEntryReturnsNull() {
        BlockPos pos = BlockPos.ORIGIN;
        TraceabilityPredicate predicate = new TraceabilityPredicate(state -> true);
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        predicateMap.put(pos, predicate);

        MBPattern pattern = new MBPattern(null, Collections.emptyList(), predicateMap);

        assertNull(pattern.getPreviewEntry(pos));
    }

    @Test
    void legacyPredicateMapRemainsOnlyAsCompatibilityAccessor() {
        BlockPos pos = BlockPos.ORIGIN;
        TraceabilityPredicate mapPredicate = new TraceabilityPredicate(state -> false);
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        predicateMap.put(pos, mapPredicate);
        StructureElementPreviewEntry entry = StructureElementPreviewEntry.of(
                StructureElementPreview.empty(),
                Collections.singletonList("typed tooltip"));
        Map<BlockPos, StructureElementPreviewEntry> previewEntries = new HashMap<>();
        previewEntries.put(pos, entry);

        MBPattern pattern = new MBPattern(null, Collections.emptyList(), predicateMap, previewEntries);

        assertSame(mapPredicate, pattern.getPredicateMap().get(pos));
        assertSame(entry, pattern.getPreviewEntry(pos));
    }

    @Test
    void registryMetadataUsesDefensiveCopies() {
        BlockPos pos = BlockPos.ORIGIN;
        TraceabilityPredicate predicate = new TraceabilityPredicate(state -> true);
        Map<BlockPos, TraceabilityPredicate> predicateMap = new HashMap<>();
        predicateMap.put(pos, predicate);
        StructureElementPreviewEntry entry = StructureElementPreviewEntry.of(
                StructureElementPreview.empty(),
                Collections.singletonList("typed tooltip"));
        Map<BlockPos, StructureElementPreviewEntry> previewEntries = new HashMap<>();
        previewEntries.put(pos, entry);

        MBPattern pattern = new MBPattern(null, Collections.emptyList(), predicateMap, previewEntries);

        predicateMap.clear();
        previewEntries.clear();

        assertSame(entry, pattern.getPreviewEntry(pos));
        assertSame(predicate, pattern.getPredicateMap().get(pos));
    }
}
