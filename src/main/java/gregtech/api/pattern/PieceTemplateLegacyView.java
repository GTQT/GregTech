package gregtech.api.pattern;

import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.util.RelativeDirection;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Explicit compatibility adapter for APIs that still need the legacy
 * {@link TraceabilityPredicate} shape.
 *
 * <p>{@link PieceTemplate} stores and executes compiled structure elements as
 * its canonical representation. This view materializes predicate arrays and
 * predicate metadata only for older addon/tooling surfaces such as
 * {@link BlockPatternTemplate#getBlockMatches()}, JEI predicate maps and
 * channel discovery.
 */
@ApiStatus.Obsolete
public final class PieceTemplateLegacyView {

    @NotNull
    private final IStructureElement<?>[][][] elements;
    @NotNull
    private final RelativeDirection[] structureDir;
    @Nullable
    private final TraceabilityPredicate[][][] seedPredicates;
    @Nullable
    private TraceabilityPredicate[][][] blockMatches;

    private PieceTemplateLegacyView(@NotNull IStructureElement<?>[][][] elements,
                                    @NotNull RelativeDirection[] structureDir,
                                    @Nullable TraceabilityPredicate[][][] seedPredicates) {
        this.elements = elements;
        this.structureDir = structureDir;
        this.seedPredicates = seedPredicates;
    }

    @NotNull
    static PieceTemplateLegacyView fromElements(@NotNull IStructureElement<?>[][][] elements,
                                                @NotNull RelativeDirection[] structureDir) {
        return new PieceTemplateLegacyView(elements, structureDir, null);
    }

    @NotNull
    static PieceTemplateLegacyView fromLegacyPredicates(@NotNull TraceabilityPredicate[][][] predicates,
                                                        @NotNull IStructureElement<?>[][][] elements,
                                                        @NotNull RelativeDirection[] structureDir) {
        return new PieceTemplateLegacyView(elements, structureDir, predicates);
    }

    @NotNull
    public TraceabilityPredicate[][][] getBlockMatches() {
        TraceabilityPredicate[][][] result = blockMatches;
        if (result == null) {
            result = materializeBlockMatches();
            blockMatches = result;
        }
        return result;
    }

    @NotNull
    public TraceabilityPredicate predicateAt(int z, int y, int x) {
        return getBlockMatches()[z][y][x];
    }

    public void forEachPredicate(@NotNull StructureOrientation orientation,
                                 @NotNull BiConsumer<BlockPos, TraceabilityPredicate> consumer) {
        TraceabilityPredicate[][][] matches = getBlockMatches();
        for (int iz = 0; iz < matches.length; iz++) {
            TraceabilityPredicate[][] layer = matches[iz];
            for (int iy = 0; iy < layer.length; iy++) {
                TraceabilityPredicate[] row = layer[iy];
                for (int ix = 0; ix < row.length; ix++) {
                    TraceabilityPredicate pred = row[ix];
                    if (pred == null || pred == TraceabilityPredicate.ANY) continue;
                    BlockPos localPos = RelativeDirection.setActualRelativeOffset(
                            ix, iy, iz,
                            orientation.getStructureFront(), orientation.getUp(),
                            orientation.isFlipped(), structureDir);
                    consumer.accept(localPos, pred);
                }
            }
        }
    }

    @NotNull
    private TraceabilityPredicate[][][] materializeBlockMatches() {
        TraceabilityPredicate[][][] result = new TraceabilityPredicate[elements.length][][];
        Map<IStructureElement<?>, TraceabilityPredicate> fallbackCache = new HashMap<>();
        for (int z = 0; z < elements.length; z++) {
            result[z] = new TraceabilityPredicate[elements[z].length][];
            for (int y = 0; y < elements[z].length; y++) {
                result[z][y] = new TraceabilityPredicate[elements[z][y].length];
                for (int x = 0; x < elements[z][y].length; x++) {
                    TraceabilityPredicate seeded = seededPredicateAt(z, y, x);
                    result[z][y][x] = seeded == null
                            ? predicateViewFor(elements[z][y][x], fallbackCache)
                            : seeded;
                }
            }
        }
        return result;
    }

    @Nullable
    private TraceabilityPredicate seededPredicateAt(int z, int y, int x) {
        if (seedPredicates == null
                || z >= seedPredicates.length
                || y >= seedPredicates[z].length
                || x >= seedPredicates[z][y].length) {
            return null;
        }
        return seedPredicates[z][y][x];
    }

    @NotNull
    public static TraceabilityPredicate predicateViewFor(@Nullable IStructureElement<?> element) {
        return predicateViewFor(element, new HashMap<>());
    }

    @NotNull
    public static TraceabilityPredicate previewPredicateViewFor(@Nullable IStructureElement<?> element) {
        if (element == null) {
            return TraceabilityPredicate.ANY;
        }
        TraceabilityPredicate result = predicateFromPreview(element);
        if (element.isCenter()) {
            result.setCenter();
        }
        return result.sort();
    }

    @NotNull
    private static TraceabilityPredicate predicateViewFor(
            @Nullable IStructureElement<?> element,
            @NotNull Map<IStructureElement<?>, TraceabilityPredicate> cache) {
        if (element == null) {
            return TraceabilityPredicate.ANY;
        }
        TraceabilityPredicate cached = cache.get(element);
        if (cached != null) {
            return cached;
        }
        TraceabilityPredicate predicate = element.toPredicate();
        if (predicate == TraceabilityPredicate.ANY) {
            return TraceabilityPredicate.ANY;
        }
        TraceabilityPredicate result = predicate == null
                ? predicateFromPreview(element)
                : new TraceabilityPredicate(predicate);
        if (element.isCenter()) {
            result.setCenter();
        }
        result.sort();
        cache.put(element, result);
        return result;
    }

    @NotNull
    private static TraceabilityPredicate predicateFromPreview(@NotNull IStructureElement<?> element) {
        StructureElementPreview preview = element.getPreview();
        TraceabilityPredicate result = new TraceabilityPredicate();
        for (StructureElementPreview.CandidateGroup group : preview.getLimited()) {
            result.limited.add(simplePredicateFrom(group));
        }
        for (StructureElementPreview.CandidateGroup group : preview.getCommon()) {
            result.common.add(simplePredicateFrom(group));
        }
        if (result.common.isEmpty() && result.limited.isEmpty()) {
            result.common.add(new TraceabilityPredicate.SimplePredicate(state -> true, element::getCandidates));
        }
        return result;
    }

    @NotNull
    private static TraceabilityPredicate.SimplePredicate simplePredicateFrom(
            @NotNull StructureElementPreview.CandidateGroup group) {
        TraceabilityPredicate.SimplePredicate predicate =
                new TraceabilityPredicate.SimplePredicate(state -> true, group::getCandidates);
        predicate.minGlobalCount = group.getMinGlobalCount();
        predicate.maxGlobalCount = group.getMaxGlobalCount();
        predicate.minLayerCount = group.getMinLayerCount();
        predicate.maxLayerCount = group.getMaxLayerCount();
        predicate.previewCount = group.getPreviewCount();
        predicate.channelName = group.getChannelName();
        predicate.defaultCandidate = group.getDefaultCandidate();
        return predicate;
    }
}
