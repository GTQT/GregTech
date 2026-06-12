package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.IMultiblockPart;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed, transactional state collected by direct structure elements.
 *
 * <p>Legacy predicates continue to use {@link PatternMatchContext}. At an
 * operation-result boundary their compatibility data is merged into this
 * state, and this state can materialize the legacy keys required by existing
 * controller callbacks.
 */
public final class StructureOperationState {

    static final String MULTIBLOCK_PARTS_KEY = "MultiblockParts";
    static final String VARIANT_ACTIVE_BLOCKS_KEY = "VABlock";

    final Map<Object, StructureMatchCollector.CountRequirement> requirements = new HashMap<>();
    final Map<Object, Integer> counts = new HashMap<>();
    final Set<IMultiblockPart> parts = new HashSet<>();
    final List<BlockPos> variantActiveBlocks = new ArrayList<>();

    public StructureOperationState() {}

    private StructureOperationState(@NotNull StructureOperationState source) {
        replaceWith(source);
    }

    @NotNull
    public StructureOperationState copy() {
        return new StructureOperationState(this);
    }

    void replaceWith(@NotNull StructureOperationState source) {
        requirements.clear();
        requirements.putAll(source.requirements);
        counts.clear();
        counts.putAll(source.counts);
        parts.clear();
        parts.addAll(source.parts);
        variantActiveBlocks.clear();
        variantActiveBlocks.addAll(source.variantActiveBlocks);
    }

    @NotNull
    StructureOperationState copyIncludingLegacy(@NotNull PatternMatchContext context) {
        StructureOperationState copy = copy();
        Set<IMultiblockPart> legacyParts =
                context.getOrDefault(MULTIBLOCK_PARTS_KEY, Collections.emptySet());
        copy.parts.addAll(legacyParts);
        List<BlockPos> legacyActiveBlocks =
                context.getOrDefault(VARIANT_ACTIVE_BLOCKS_KEY, Collections.emptyList());
        copy.variantActiveBlocks.addAll(legacyActiveBlocks);
        return copy;
    }

    @NotNull
    public static StructureOperationState fromLegacyContext(@NotNull PatternMatchContext context) {
        return new StructureOperationState().copyIncludingLegacy(context);
    }

    /**
     * Replace collector-owned legacy keys with a compatibility view of this
     * typed state. Other addon-owned context entries are preserved.
     */
    public void applyCompatibilityView(@NotNull PatternMatchContext context) {
        context.remove(MULTIBLOCK_PARTS_KEY);
        context.remove(VARIANT_ACTIVE_BLOCKS_KEY);
        if (!parts.isEmpty()) {
            context.set(MULTIBLOCK_PARTS_KEY, new HashSet<>(parts));
        }
        if (!variantActiveBlocks.isEmpty()) {
            context.set(VARIANT_ACTIVE_BLOCKS_KEY, new ArrayList<>(variantActiveBlocks));
        }
    }

    @NotNull
    public Set<IMultiblockPart> getParts() {
        return Collections.unmodifiableSet(parts);
    }

    @NotNull
    public List<BlockPos> getVariantActiveBlocks() {
        return Collections.unmodifiableList(variantActiveBlocks);
    }
}
