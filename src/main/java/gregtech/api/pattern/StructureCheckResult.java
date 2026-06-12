package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.casing.StructureChannelValues;
import gregtech.api.pattern.element.FormedStructureMetadata;
import gregtech.api.pattern.element.StructureCheckState;

import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable operation-level result for a synchronous structure check.
 *
 * <p>This normalizes definition and legacy-template checks before controller
 * assembly begins. Mutable context/channel data is copied at the boundary so a
 * later traversal cannot change a result that is waiting to be committed.
 */
public final class StructureCheckResult {

    public enum Source {
        DEFINITION("definition"),
        LEGACY_TEMPLATE("legacy-template");

        @NotNull
        private final String tracePath;

        Source(@NotNull String tracePath) {
            this.tracePath = tracePath;
        }

        @NotNull
        public String getTracePath() {
            return tracePath;
        }
    }

    @NotNull
    private final Source source;
    private final boolean matched;
    @Nullable
    private final PatternMatchContext context;
    @NotNull
    private final StructureOperationState operationState;
    @Nullable
    private final FormedStructureMetadata metadata;
    @Nullable
    private final PatternError error;
    @Nullable
    private final BlockPos errorPos;
    @Nullable
    private final String errorMessage;
    @NotNull
    private final Map<MultiblockAbility<?>, Integer> missingAbilities;
    @NotNull
    private final StructureChannelValues channelValues;
    private final boolean flipped;

    private StructureCheckResult(@NotNull Source source,
                                 boolean matched,
                                 @Nullable PatternMatchContext context,
                                 @NotNull StructureOperationState operationState,
                                 @Nullable FormedStructureMetadata metadata,
                                 @Nullable PatternError error,
                                 @Nullable BlockPos errorPos,
                                 @Nullable String errorMessage,
                                 @NotNull Map<MultiblockAbility<?>, Integer> missingAbilities,
                                 @NotNull StructureChannelValues channelValues,
                                 boolean flipped) {
        this.source = source;
        this.matched = matched;
        this.context = context == null ? null : context.copy();
        this.operationState = operationState.copy();
        this.metadata = metadata;
        this.error = error;
        this.errorPos = errorPos;
        this.errorMessage = errorMessage;
        this.missingAbilities = Collections.unmodifiableMap(new LinkedHashMap<>(missingAbilities));
        this.channelValues = channelValues.copy();
        this.flipped = flipped;
    }

    @NotNull
    public static StructureCheckResult fromDefinition(@NotNull StructureCheckState.Result result) {
        PatternMatchContext context = result.context;
        return new StructureCheckResult(
                Source.DEFINITION,
                result.success,
                context,
                result.operationState == null ? new StructureOperationState() : result.operationState,
                result.metadata,
                result.error,
                result.errorPos,
                result.errorMessage,
                result.missingAbilities,
                context == null ? new StructureChannelValues() : StructureChannelValues.fromContext(context),
                result.flipped);
    }

    @NotNull
    public static StructureCheckResult fromLegacy(@Nullable PatternMatchContext context,
                                                  @NotNull MultiblockState state) {
        boolean matched = context != null;
        StructureOperationState operationState = matched
                ? StructureOperationState.fromLegacyContext(context)
                : new StructureOperationState();
        return new StructureCheckResult(
                Source.LEGACY_TEMPLATE,
                matched,
                context,
                operationState,
                null,
                matched ? null : state.getError(),
                null,
                matched ? null : "Legacy structure template did not match",
                matched ? Collections.emptyMap() : state.getMissingAbilities(),
                matched ? StructureChannelValues.fromContext(context) : new StructureChannelValues(),
                matched && context.neededFlip());
    }

    @NotNull
    public Source getSource() {
        return source;
    }

    @NotNull
    public String getTracePath() {
        return source.getTracePath();
    }

    public boolean isMatched() {
        return matched;
    }

    @Nullable
    public PatternMatchContext copyContext() {
        if (context == null) {
            return null;
        }
        PatternMatchContext copy = context.copy();
        operationState.applyCompatibilityView(copy);
        return copy;
    }

    @NotNull
    public StructureOperationState copyOperationState() {
        return operationState.copy();
    }

    @Nullable
    public FormedStructureMetadata getMetadata() {
        return metadata;
    }

    @NotNull
    public Map<MultiblockAbility<?>, Integer> getMissingAbilities() {
        return missingAbilities;
    }

    @NotNull
    public StructureChannelValues copyChannelValues() {
        return channelValues.copy();
    }

    public boolean isFlipped() {
        return flipped;
    }

    @NotNull
    public StructureFailureTrace createFailureTrace(@NotNull MultiblockControllerBase controller) {
        StructureFailureTrace.Builder builder =
                new StructureFailureTrace.Builder(controller.getMetaName(), controller.getPos())
                        .formed(controller.isStructureFormed())
                        .orientation(StructureOrientation.fromController(controller))
                        .path(getTracePath())
                        .operation("CHECK")
                        .result("failed")
                        .missingAbilities(missingAbilities);
        if (error != null) {
            builder.error(error);
        } else {
            builder.errorPosition(errorPos);
        }
        if (error == null && errorMessage != null) {
            builder.actual(errorMessage);
        }
        return builder.build();
    }
}
