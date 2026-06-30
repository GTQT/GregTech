package gregtech.api.pattern;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable request shape for V3 structure operations.
 *
 * <p>This is intentionally small for now: it carries only the fields needed by
 * the operations already routed through {@link StructureOperationEvaluator}.
 */
public final class StructureOperationRequest {

    public enum Kind {
        CHECK(StructureEvaluationContext.Operation.MATCH_WORLD),
        SNAPSHOT_CHECK(StructureEvaluationContext.Operation.MATCH_SNAPSHOT),
        PREVIEW(StructureEvaluationContext.Operation.PREVIEW),
        HINT(StructureEvaluationContext.Operation.HINT),
        CREATIVE_BUILD(StructureEvaluationContext.Operation.CREATIVE_BUILD),
        SURVIVAL_BUILD(StructureEvaluationContext.Operation.SURVIVAL_BUILD),
        ITERATE(StructureEvaluationContext.Operation.ITERATE);

        @NotNull
        private final StructureEvaluationContext.Operation evaluationOperation;

        Kind(@NotNull StructureEvaluationContext.Operation evaluationOperation) {
            this.evaluationOperation = evaluationOperation;
        }

        @NotNull
        public StructureEvaluationContext.Operation getEvaluationOperation() {
            return evaluationOperation;
        }
    }

    @NotNull
    private final Kind kind;
    @Nullable
    private final World world;
    @Nullable
    private final IBlockAccess snapshot;
    @Nullable
    private final BlockPos controllerPos;
    @Nullable
    private final StructureOrientation orientation;
    @Nullable
    private final MultiblockControllerBase controller;
    @Nullable
    private final EntityPlayer player;
    @Nullable
    private final Map<String, Integer> channelValues;
    @Nullable
    private final int[] repetitions;
    @Nullable
    private final AbilityPlacementTracker abilityTracker;
    @NotNull
    private final ItemStack triggerStack;
    private final boolean doRandomCheck;
    private final boolean skipHatches;
    private final int pieceIndex;

    private StructureOperationRequest(@NotNull Kind kind,
                                      @Nullable World world,
                                      @Nullable IBlockAccess snapshot,
                                      @Nullable BlockPos controllerPos,
                                      @Nullable StructureOrientation orientation,
                                      @Nullable MultiblockControllerBase controller,
                                      @Nullable EntityPlayer player,
                                      @Nullable Map<String, Integer> channelValues,
                                      @Nullable int[] repetitions,
                                      @Nullable AbilityPlacementTracker abilityTracker,
                                      @NotNull ItemStack triggerStack,
                                      boolean doRandomCheck,
                                      boolean skipHatches,
                                      int pieceIndex) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.world = world;
        this.snapshot = snapshot;
        this.controllerPos = controllerPos;
        this.orientation = orientation;
        this.controller = controller;
        this.player = player;
        this.channelValues = channelValues == null
                ? null
                : Collections.unmodifiableMap(new HashMap<>(channelValues));
        this.repetitions = repetitions == null ? null : repetitions.clone();
        this.abilityTracker = abilityTracker;
        this.triggerStack = triggerStack.isEmpty() ? ItemStack.EMPTY : triggerStack.copy();
        this.doRandomCheck = doRandomCheck;
        this.skipHatches = skipHatches;
        this.pieceIndex = pieceIndex;
    }

    @NotNull
    public static StructureOperationRequest check(@NotNull World world,
                                                  @NotNull BlockPos controllerPos,
                                                  @NotNull StructureOrientation orientation,
                                                  boolean doRandomCheck,
                                                  @Nullable MultiblockControllerBase controller) {
        return new StructureOperationRequest(
                Kind.CHECK, world, null, controllerPos, orientation, controller,
                null, null, null, null, ItemStack.EMPTY, doRandomCheck, false, 0);
    }

    @NotNull
    public static StructureOperationRequest snapshotCheck(
            @NotNull IBlockAccess snapshot,
            @NotNull BlockPos controllerPos,
            @NotNull StructureOrientation orientation,
            @Nullable MultiblockControllerBase controller) {
        return new StructureOperationRequest(
                Kind.SNAPSHOT_CHECK, null, snapshot, controllerPos, orientation,
                controller, null, null, null, null,
                ItemStack.EMPTY, false, false, 0);
    }

    @NotNull
    public static StructureOperationRequest preview(@NotNull int[] repetitions,
                                                    @Nullable Map<String, Integer> channelValues) {
        return preview(repetitions, channelValues, null);
    }

    @NotNull
    public static StructureOperationRequest preview(@NotNull int[] repetitions,
                                                    @Nullable Map<String, Integer> channelValues,
                                                    @Nullable AbilityPlacementTracker abilityTracker) {
        return preview(repetitions, channelValues, abilityTracker, false);
    }

    @NotNull
    public static StructureOperationRequest preview(@NotNull int[] repetitions,
                                                    @Nullable Map<String, Integer> channelValues,
                                                    @Nullable AbilityPlacementTracker abilityTracker,
                                                    boolean skipHatches) {
        return new StructureOperationRequest(
                Kind.PREVIEW, null, null, null, null, null, null,
                channelValues, repetitions, abilityTracker, ItemStack.EMPTY, false, skipHatches, 0);
    }

    @NotNull
    public static StructureOperationRequest previewMultiPiece(@Nullable Map<String, Integer> channelValues,
                                                              @Nullable MultiblockControllerBase controller) {
        return previewMultiPiece(channelValues, controller, false);
    }

    @NotNull
    public static StructureOperationRequest previewMultiPiece(@Nullable Map<String, Integer> channelValues,
                                                              @Nullable MultiblockControllerBase controller,
                                                              boolean skipHatches) {
        return previewMultiPiece(channelValues, controller, skipHatches, 0);
    }

    @NotNull
    public static StructureOperationRequest previewMultiPiece(@Nullable Map<String, Integer> channelValues,
                                                              @Nullable MultiblockControllerBase controller,
                                                              boolean skipHatches,
                                                              int toolingPieceIndex) {
        return new StructureOperationRequest(
                Kind.PREVIEW, null, null, null, null, controller, null,
                channelValues, null, null, ItemStack.EMPTY, false, skipHatches, toolingPieceIndex);
    }

    @NotNull
    public static StructureOperationRequest hint(@NotNull EntityPlayer player,
                                                 @NotNull MultiblockControllerBase controller,
                                                 @NotNull StructureOrientation orientation,
                                                 @Nullable Map<String, Integer> channelValues,
                                                 @NotNull ItemStack triggerStack) {
        return new StructureOperationRequest(
                Kind.HINT, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, null, triggerStack, false, false, 0);
    }

    @NotNull
    public static StructureOperationRequest creativeBuild(@NotNull EntityPlayer player,
                                                          @NotNull MultiblockControllerBase controller,
                                                          @NotNull StructureOrientation orientation,
                                                          @Nullable Map<String, Integer> channelValues,
                                                          boolean skipHatches) {
        return new StructureOperationRequest(
                Kind.CREATIVE_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, null, ItemStack.EMPTY, false, skipHatches, 0);
    }

    @NotNull
    public static StructureOperationRequest build(@NotNull EntityPlayer player,
                                                  @NotNull MultiblockControllerBase controller,
                                                  @NotNull StructureOrientation orientation,
                                                  @Nullable Map<String, Integer> channelValues,
                                                  boolean skipHatches,
                                                  @NotNull ItemStack triggerStack) {
        return player.isCreative()
                ? creativeBuild(player, controller, orientation, channelValues, skipHatches)
                : survivalBuild(player, controller, orientation, channelValues, skipHatches, triggerStack);
    }

    @NotNull
    public static StructureOperationRequest creativeBuildPiece(int pieceIndex,
                                                               @NotNull EntityPlayer player,
                                                               @NotNull MultiblockControllerBase controller,
                                                               @NotNull StructureOrientation orientation,
                                                               @Nullable Map<String, Integer> channelValues,
                                                               boolean skipHatches) {
        return new StructureOperationRequest(
                Kind.CREATIVE_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, null, ItemStack.EMPTY,
                false, skipHatches, pieceIndex);
    }

    @NotNull
    public static StructureOperationRequest creativeBuildPiece(int pieceIndex,
                                                               @NotNull EntityPlayer player,
                                                               @NotNull MultiblockControllerBase controller,
                                                               @NotNull StructureOrientation orientation,
                                                               @Nullable Map<String, Integer> channelValues,
                                                               boolean skipHatches,
                                                               @NotNull AbilityPlacementTracker abilityTracker) {
        return new StructureOperationRequest(
                Kind.CREATIVE_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, abilityTracker, ItemStack.EMPTY,
                false, skipHatches, pieceIndex);
    }

    @NotNull
    public static StructureOperationRequest buildPiece(int pieceIndex,
                                                       @NotNull EntityPlayer player,
                                                       @NotNull MultiblockControllerBase controller,
                                                       @NotNull StructureOrientation orientation,
                                                       @Nullable Map<String, Integer> channelValues,
                                                       boolean skipHatches,
                                                       @NotNull ItemStack triggerStack) {
        return player.isCreative()
                ? creativeBuildPiece(pieceIndex, player, controller, orientation, channelValues, skipHatches)
                : survivalBuildPiece(pieceIndex, player, controller, orientation, channelValues,
                        skipHatches, triggerStack);
    }

    @NotNull
    public static StructureOperationRequest survivalBuild(@NotNull EntityPlayer player,
                                                          @NotNull MultiblockControllerBase controller,
                                                          @NotNull StructureOrientation orientation,
                                                          @Nullable Map<String, Integer> channelValues,
                                                          boolean skipHatches,
                                                          @NotNull ItemStack triggerStack) {
        return new StructureOperationRequest(
                Kind.SURVIVAL_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, null, triggerStack, false, skipHatches, 0);
    }

    @NotNull
    public static StructureOperationRequest survivalBuildPiece(int pieceIndex,
                                                               @NotNull EntityPlayer player,
                                                               @NotNull MultiblockControllerBase controller,
                                                               @NotNull StructureOrientation orientation,
                                                               @Nullable Map<String, Integer> channelValues,
                                                               boolean skipHatches,
                                                               @NotNull ItemStack triggerStack) {
        return new StructureOperationRequest(
                Kind.SURVIVAL_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, null, triggerStack,
                false, skipHatches, pieceIndex);
    }

    @NotNull
    public static StructureOperationRequest survivalBuildPiece(int pieceIndex,
                                                               @NotNull EntityPlayer player,
                                                               @NotNull MultiblockControllerBase controller,
                                                               @NotNull StructureOrientation orientation,
                                                               @Nullable Map<String, Integer> channelValues,
                                                               boolean skipHatches,
                                                               @NotNull AbilityPlacementTracker abilityTracker,
                                                               @NotNull ItemStack triggerStack) {
        return new StructureOperationRequest(
                Kind.SURVIVAL_BUILD, player.world, null, controller.getPos(), orientation,
                controller, player, channelValues, null, abilityTracker, triggerStack,
                false, skipHatches, pieceIndex);
    }

    @NotNull
    public StructureOperationRequest withChannelValues(@Nullable Map<String, Integer> channelValues) {
        return new StructureOperationRequest(
                kind, world, snapshot, controllerPos, orientation, controller, player,
                channelValues, repetitions, abilityTracker, triggerStack, doRandomCheck, skipHatches, pieceIndex);
    }

    @NotNull
    public static StructureOperationRequest iterate(@NotNull World world,
                                                    @NotNull BlockPos controllerPos,
                                                    @NotNull StructureOrientation orientation) {
        return iterate(world, controllerPos, orientation, null);
    }

    @NotNull
    public static StructureOperationRequest iterate(@NotNull World world,
                                                    @NotNull BlockPos controllerPos,
                                                    @NotNull StructureOrientation orientation,
                                                    @Nullable MultiblockControllerBase controller) {
        return new StructureOperationRequest(
                Kind.ITERATE, world, null, controllerPos, orientation, controller,
                null, null, null, null, ItemStack.EMPTY, false, false, 0);
    }

    @NotNull
    public Kind getKind() {
        return kind;
    }

    @NotNull
    public StructureEvaluationContext.Operation getEvaluationOperation() {
        return kind.getEvaluationOperation();
    }

    public void requireKind(@NotNull Kind expected) {
        if (kind != expected) {
            throw new IllegalArgumentException("Expected " + expected + " request, got " + kind);
        }
    }

    public boolean isBuildKind() {
        return kind.getEvaluationOperation().isBuild();
    }

    public void requireBuildKind() {
        if (!isBuildKind()) {
            throw new IllegalArgumentException("Expected build request, got " + kind);
        }
    }

    @NotNull
    public World requireWorld() {
        if (world == null) {
            throw new IllegalStateException(kind + " request has no world");
        }
        return world;
    }

    @NotNull
    public IBlockAccess requireSnapshot() {
        if (snapshot == null) {
            throw new IllegalStateException(kind + " request has no snapshot");
        }
        return snapshot;
    }

    @NotNull
    public BlockPos requireControllerPos() {
        if (controllerPos == null) {
            throw new IllegalStateException(kind + " request has no controller position");
        }
        return controllerPos;
    }

    @NotNull
    public StructureOrientation requireOrientation() {
        if (orientation == null) {
            throw new IllegalStateException(kind + " request has no orientation");
        }
        return orientation;
    }

    @Nullable
    public MultiblockControllerBase getController() {
        return controller;
    }

    @NotNull
    public MultiblockControllerBase requireController() {
        if (controller == null) {
            throw new IllegalStateException(kind + " request has no controller");
        }
        return controller;
    }

    @NotNull
    public EntityPlayer requirePlayer() {
        if (player == null) {
            throw new IllegalStateException(kind + " request has no player");
        }
        return player;
    }

    @Nullable
    public Map<String, Integer> getChannelValues() {
        return channelValues;
    }

    @NotNull
    public int[] requireRepetitions() {
        if (repetitions == null) {
            throw new IllegalStateException(kind + " request has no repetitions");
        }
        return repetitions.clone();
    }

    @NotNull
    public AbilityPlacementTracker requireAbilityTracker() {
        if (abilityTracker == null) {
            throw new IllegalStateException(kind + " request has no ability tracker");
        }
        return abilityTracker;
    }

    @Nullable
    public AbilityPlacementTracker getAbilityTracker() {
        return abilityTracker;
    }

    @NotNull
    public ItemStack requireTriggerStack() {
        return triggerStack.isEmpty() ? ItemStack.EMPTY : triggerStack.copy();
    }

    public boolean doRandomCheck() {
        return doRandomCheck;
    }

    public boolean skipHatches() {
        return skipHatches;
    }

    public int getPieceIndex() {
        return pieceIndex;
    }
}
