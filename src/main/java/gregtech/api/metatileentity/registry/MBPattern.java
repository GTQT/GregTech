package gregtech.api.metatileentity.registry;

import gregtech.api.pattern.StructureElementPreviewEntry;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.client.renderer.scene.WorldSceneRenderer;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MBPattern {

    final WorldSceneRenderer sceneRenderer;
    final List<ItemStack> parts;
    final Map<BlockPos, TraceabilityPredicate> predicateMap;
    final Map<BlockPos, StructureElementPreviewEntry> previewEntries;

    public MBPattern(final WorldSceneRenderer sceneRenderer, final List<ItemStack> parts,
                     Map<BlockPos, TraceabilityPredicate> predicateMap) {
        this(sceneRenderer, parts, predicateMap, Collections.emptyMap());
    }

    public MBPattern(final WorldSceneRenderer sceneRenderer, final List<ItemStack> parts,
                     Map<BlockPos, TraceabilityPredicate> predicateMap,
                     Map<BlockPos, StructureElementPreviewEntry> previewEntries) {
        this.sceneRenderer = sceneRenderer;
        this.parts = parts;
        this.predicateMap = Collections.unmodifiableMap(new HashMap<>(predicateMap));
        this.previewEntries = Collections.unmodifiableMap(new HashMap<>(previewEntries));
    }

    public List<ItemStack> getParts() {
        return parts;
    }

    public WorldSceneRenderer getSceneRenderer() {
        return sceneRenderer;
    }

    @Nullable
    public StructureElementPreviewEntry getPreviewEntry(@NotNull BlockPos pos) {
        return previewEntries.get(pos);
    }

    /**
     * Return the legacy predicate only for cells that have no typed preview
     * metadata.
     *
     * <p>When a typed preview entry exists for the position, it suppresses the
     * broader predicate map fallback. Legacy predicates adapted into typed
     * preview entries expose candidates/tooltips through the entry itself.
     */
    @Nullable
    public TraceabilityPredicate getLegacyPredicateFallback(@NotNull BlockPos pos) {
        return previewEntries.containsKey(pos) ? null : predicateMap.get(pos);
    }

    /**
     * @deprecated Compatibility accessor for old tooling/addons. New internal
     *             tooling should use {@link #getPreviewEntry(BlockPos)} first
     *             and {@link #getLegacyPredicateFallback(BlockPos)} only as a
     *             migration fallback.
     */
    @Deprecated
    @ApiStatus.Obsolete
    public Map<BlockPos, TraceabilityPredicate> getPredicateMap() {
        return predicateMap;
    }

    /**
     * @deprecated Compatibility accessor for old tooling. New internal tooling
     *             should use {@link #getPreviewEntry(BlockPos)}.
     */
    @Deprecated
    @ApiStatus.Obsolete
    public Map<BlockPos, StructureElementPreviewEntry> getPreviewEntries() {
        return previewEntries;
    }
}
