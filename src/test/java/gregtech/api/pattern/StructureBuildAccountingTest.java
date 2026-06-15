package gregtech.api.pattern;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureElementPreview;
import gregtech.api.pattern.element.impl.AnyElement;
import gregtech.api.util.BlockInfo;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static gregtech.api.pattern.StructureEvaluationContext.Operation.CREATIVE_BUILD;
import static gregtech.api.pattern.StructureEvaluationContext.Operation.SURVIVAL_BUILD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureBuildAccountingTest {

    private static World world;
    private static ItemStack stoneItem;
    private static ItemStack dirtItem;
    private static BlockInfo stoneInfo;
    private static BlockInfo dirtInfo;

    @BeforeAll
    static void bootstrapMinecraft() {
        if (!Bootstrap.isRegistered()) {
            Bootstrap.register();
        }
        world = bareWorld();
        stoneItem = new ItemStack(Blocks.STONE);
        dirtItem = new ItemStack(Blocks.DIRT);
        stoneInfo = new BlockInfo(Blocks.STONE.getDefaultState(), null);
        dirtInfo = new BlockInfo(Blocks.DIRT.getDefaultState(), null);
    }

    @Test
    void insufficientItemsAreReportedWithoutConsumingInventory() {
        TestPlayer player = player(false);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.STONE));

        StructurePlacementDecision.Selection selection = StructurePlacementDecision.select(
                player,
                new BlockInfo[] { dirtInfo },
                StructurePlacementDecision.toItemStacks(new BlockInfo[] { dirtInfo }),
                (TraceabilityPredicate.SimplePredicate) null, null, null, SURVIVAL_BUILD);
        StructureBuildResult result = StructureBuildResult.builder()
                .recordAttemptedTraversal()
                .recordVisitedCell()
                .recordPlacementBudget()
                .recordRequiredItem(dirtItem)
                .recordUnavailableItemCell()
                .recordMissingItem(dirtItem)
                .build();

        assertNull(selection);
        assertEquals(1, result.getPlacementBudget());
        assertEquals(1, result.getRemainingPlacementBudget());
        assertEquals(1, result.getUnavailableItemCells());
        assertEquals(1, result.getMissingItems().get(0).getCount());
        assertEquals(1, player.inventory.mainInventory.get(0).getCount());
        assertTrue(result.getConsumedItems().isEmpty());
    }

    @Test
    void survivalSelectionConsumesOnlyAfterCallerCommitsPlacement() {
        TestPlayer player = player(false);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.STONE, 2));
        StructurePlacementDecision.Selection selection = StructurePlacementDecision.select(
                player,
                new BlockInfo[] { stoneInfo },
                StructurePlacementDecision.toItemStacks(new BlockInfo[] { stoneInfo }),
                (TraceabilityPredicate.SimplePredicate) null, null, null, SURVIVAL_BUILD);

        assertNotNull(selection);
        assertEquals(2, player.inventory.mainInventory.get(0).getCount());

        assertTrue(selection.consume(player));
        assertEquals(1, player.inventory.mainInventory.get(0).getCount());
    }

    @Test
    void partialPlacementResultKeepsResumeBudgetAndItemSummary() {
        StructureBuildResult firstPass = StructureBuildResult.builder()
                .recordAttemptedTraversal()
                .recordPlacementBudget()
                .recordRequiredItem(stoneItem)
                .recordConsumedItem(stoneItem)
                .recordPlacedCell()
                .recordPlacementBudget()
                .recordRequiredItem(dirtItem)
                .recordUnavailableItemCell()
                .recordMissingItem(dirtItem)
                .build();

        assertTrue(firstPass.hasPartialPlacement());
        assertTrue(firstPass.requiresResume());
        assertEquals(2, firstPass.getPlacementBudget());
        assertEquals(1, firstPass.getRemainingPlacementBudget());
        assertEquals(1, firstPass.getConsumedItems().get(0).getCount());
        assertEquals(1, firstPass.getMissingItems().get(0).getCount());

        StructureBuildResult resumePass = StructureBuildResult.builder()
                .recordAttemptedTraversal()
                .recordExistingCell()
                .recordPlacementBudget()
                .recordRequiredItem(dirtItem)
                .recordConsumedItem(dirtItem)
                .recordPlacedCell()
                .build();

        assertFalse(resumePass.hasPartialPlacement());
        assertFalse(resumePass.requiresResume());
        assertEquals(1, resumePass.getPlacementBudget());
        assertEquals(0, resumePass.getRemainingPlacementBudget());
    }

    @Test
    void alreadyValidProbeDoesNotPolluteFormationCollector() {
        StructureMatchSession session = new StructureMatchSession();
        PatternMatchContext legacyContext = session.getContext();
        StructureEvaluationContext<Object> context = new StructureEvaluationContext<>();
        BlockWorldState worldState = new BlockWorldState();
        worldState.update(world, BlockPos.ORIGIN, legacyContext,
                session.getGlobalCount(), new HashMap<>(), TraceabilityPredicate.ANY);
        context.update(null, session, worldState, SURVIVAL_BUILD);
        RecordingElement element = new RecordingElement(true);

        assertTrue(context.probe(evaluation -> element.match(evaluation)));

        assertEquals(0, session.getOperationState().getParts().size());
        assertNull(legacyContext.get("build_probe"));
    }

    @Test
    void branchFallbackUsesInventoryCandidateWhenPreferredIsUnavailable() {
        TestPlayer player = player(false);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.DIRT));
        Map<String, Integer> channels = new HashMap<>();
        channels.put("tier", 1);
        TraceabilityPredicate.SimplePredicate predicate = new TraceabilityPredicate.SimplePredicate(
                state -> true,
                () -> new BlockInfo[] { stoneInfo, dirtInfo });
        predicate.channelName = "tier";

        StructurePlacementDecision.Selection selection = StructurePlacementDecision.select(
                player,
                new BlockInfo[] { stoneInfo, dirtInfo },
                StructurePlacementDecision.toItemStacks(new BlockInfo[] { stoneInfo, dirtInfo }),
                predicate, channels, null, SURVIVAL_BUILD);

        assertNotNull(selection);
        assertEquals(dirtItem.getItem(), selection.getRequiredStack().getItem());
        assertEquals(1, player.inventory.mainInventory.get(0).getCount());
    }

    @Test
    void creativeSelectionSharesPreferredCandidateLogic() {
        TestPlayer player = player(true);
        Map<String, Integer> channels = new HashMap<>();
        channels.put("tier", 2);
        TraceabilityPredicate.SimplePredicate predicate = new TraceabilityPredicate.SimplePredicate(
                state -> true,
                () -> new BlockInfo[] { stoneInfo, dirtInfo });
        predicate.channelName = "tier";

        StructurePlacementDecision.Selection selection = StructurePlacementDecision.select(
                player,
                new BlockInfo[] { stoneInfo, dirtInfo },
                StructurePlacementDecision.toItemStacks(new BlockInfo[] { stoneInfo, dirtInfo }),
                predicate, channels, null, CREATIVE_BUILD);

        assertNotNull(selection);
        assertEquals(dirtItem.getItem(), selection.getRequiredStack().getItem());
        assertFalse(selection.consumesItem());
    }

    @Test
    void directElementCandidatesAreUsedWhenLegacyPredicateHasNoCandidates() {
        MutableWorld mutableWorld = mutableWorld();
        TestPlayer player = player(false, mutableWorld);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.DIRT));
        MultiblockState state = new MultiblockState(singleCellTemplate(new DirectCandidateElement(dirtInfo)));

        StructureBuildResult result = state.autoBuildAtWithResult(
                player, controller(mutableWorld), BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                0, 0, 0, null, false, null, SURVIVAL_BUILD, ItemStack.EMPTY);

        assertEquals(1, result.getPlacementBudget());
        assertEquals(1, result.getPlacedCells());
        assertEquals(1, result.getConsumedItems().get(0).getCount());
        assertEquals(dirtItem.getItem(), result.getConsumedItems().get(0).getStack().getItem());
        assertEquals(Blocks.DIRT.getDefaultState(), mutableWorld.getBlockState(BlockPos.ORIGIN));
    }

    @Test
    void directPreviewChannelSelectsBuildCandidateWithoutLegacyPredicateMetadata() {
        MutableWorld mutableWorld = mutableWorld();
        TestPlayer player = player(false, mutableWorld);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.DIRT));
        MultiblockState state = new MultiblockState(singleCellTemplate(
                new DirectChannelElement(stoneInfo, dirtInfo, "tier")));
        Map<String, Integer> channels = new HashMap<>();
        channels.put("tier", 2);

        StructureBuildResult result = state.autoBuildAtWithResult(
                player, controller(mutableWorld), BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                0, 0, 0, channels, false, null, SURVIVAL_BUILD, ItemStack.EMPTY);

        assertEquals(1, result.getPlacementBudget());
        assertEquals(1, result.getPlacedCells());
        assertEquals(dirtItem.getItem(), result.getConsumedItems().get(0).getStack().getItem());
        assertEquals(Blocks.DIRT.getDefaultState(), mutableWorld.getBlockState(BlockPos.ORIGIN));
    }

    @Test
    void directPreviewChannelSelectsPreviewCandidateWithoutLegacyPredicateMetadata() {
        MultiblockState state = new MultiblockState(singleCellTemplate(
                new DirectChannelElement(stoneInfo, dirtInfo, "tier")));
        Map<String, Integer> channels = new HashMap<>();
        channels.put("tier", 2);

        MultiblockState.PreviewCells preview = state.createPreviewCells(new int[] { 1 }, channels);

        assertEquals(Blocks.DIRT.getDefaultState(),
                preview.getBlocks().get(BlockPos.ORIGIN).getBlockState());
    }

    @Test
    void previewCellsExposeDirectElementTooltipAndCandidates() {
        MultiblockState state = new MultiblockState(singleCellTemplate(
                new PreviewTooltipElement(dirtInfo, "direct.preview.tooltip")));

        MultiblockState.PreviewCells preview = state.createPreviewCells(new int[] {1}, null);
        StructureElementPreviewEntry entry = preview.getPreviewEntries().get(BlockPos.ORIGIN);

        assertNotNull(entry);
        assertEquals("direct.preview.tooltip", entry.getTooltip().get(0));
        assertArrayEquals(new BlockInfo[] {dirtInfo},
                entry.getPreview().getCommon().get(0).getCandidates());
    }

    @Test
    void previewCellsRetainEmptyTypedEntriesForFallbackSuppression() {
        MultiblockState state = new MultiblockState(singleCellTemplate(
                gregtech.api.pattern.element.impl.AnyElement.INSTANCE));

        MultiblockState.PreviewCells preview =
                state.createPreviewCells(new int[] {1}, null);

        assertTrue(preview.getPreviewEntries().containsKey(BlockPos.ORIGIN));
    }

    @Test
    void patternErrorCandidatesPreferDirectPreviewEntry() {
        MutableWorld mutableWorld = mutableWorld();
        MultiblockState state = new MultiblockState(singleCellTemplateWithoutLegacyCandidates(
                new DirectCandidateElement(dirtInfo)));

        PatternMatchContext result = state.checkPatternAtSnapshotExact(
                mutableWorld, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                0, 0, 0);

        assertNull(result);
        PatternError error = state.getError();
        assertNotNull(error);
        assertTrue(error.getWorldState().getPreviewEntry() != null);
        assertEquals(dirtItem.getItem(), error.getCandidates().get(0).get(0).getItem());
    }

    @Test
    void previewAndDiagnosticsDoNotRequireDirectLegacyPredicateView() {
        MutableWorld mutableWorld = mutableWorld();
        MultiblockState state = new MultiblockState(singleCellTemplateWithoutLegacyCandidates(
                new ThrowingLegacyPredicateDirectElement(dirtInfo)));

        MultiblockState.PreviewCells preview = state.createPreviewCells(new int[] {1}, null);

        assertEquals(Blocks.DIRT.getDefaultState(),
                preview.getBlocks().get(BlockPos.ORIGIN).getBlockState());

        PatternMatchContext result = state.checkPatternAtSnapshotExact(
                mutableWorld, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                0, 0, 0);

        assertNull(result);
        PatternError error = state.getError();
        assertNotNull(error);
        assertEquals(dirtItem.getItem(), error.getCandidates().get(0).get(0).getItem());
    }

    @Test
    void compiledOnlyTemplateDiscoversCenterFromElement() {
        PieceTemplate template = new PieceTemplate(
                new IStructureElement<?>[][][] {
                        {
                                { new RecordingElement(true), new CenterOnlyElement() }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 2, 4 }
                },
                null,
                null,
                null);

        assertEquals(new BlockPatternTemplate.CenterOffset(1, 0, 0, 0, 0), template.getCenterOffset());
    }

    @Test
    void legacyViewProjectsPredicateForDirectElementWithoutPredicate() {
        DirectChannelElement element = new DirectChannelElement(stoneInfo, dirtInfo, "tier");
        PieceTemplate template = new PieceTemplate(
                new IStructureElement<?>[][][] {
                        {
                                { element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);

        TraceabilityPredicate predicate = template.getBlockMatches()[0][0][0];

        assertNotNull(predicate);
        assertFalse(predicate.isCenter());
        assertEquals(1, predicate.common.size());
        assertArrayEquals(element.getCandidates(), predicate.common.get(0).candidates.get());
    }

    @Test
    void legacyViewKeepsAnyPredicateIdentityForCompiledWildcard() {
        PieceTemplate template = new PieceTemplate(
                new IStructureElement<?>[][][] {
                        {
                                { AnyElement.INSTANCE.compile() }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);

        assertTrue(template.getBlockMatches()[0][0][0] == TraceabilityPredicate.ANY);
    }

    @Test
    void typedPreviewResultWrapsSinglePieceCells() {
        MultiblockState state = new MultiblockState(singleCellTemplate(
                new DirectChannelElement(stoneInfo, dirtInfo, "tier")));
        StructureOperationEvaluator evaluator =
                new StructureOperationEvaluator(null, state.getBackingState(), null, null);

        StructurePreviewResult result = evaluator.previewSingleResult(
                StructureOperationRequest.preview(new int[] {1}, null));

        assertEquals(StructurePreviewResult.Outcome.GENERATED, result.getOutcome());
        assertNotNull(result.getSinglePieceCells());
        assertEquals("v3-typed-single", result.getDiagnostics().getPath());
        assertTrue(result.getDiagnostics().isSyntheticSinglePiece());
        assertEquals(Blocks.STONE.getDefaultState(),
                result.getSinglePieceCells().getBlocks().get(BlockPos.ORIGIN).getBlockState());
    }

    @Test
    void singleTemplateRuntimeOperationsReportTypedDiagnostics() {
        MutableWorld mutableWorld = mutableWorld();
        MultiblockState state = new MultiblockState(singleCellTemplate(new DirectCandidateElement(dirtInfo)));
        StructureOperationEvaluator evaluator =
                new StructureOperationEvaluator(null, state.getBackingState(), null, null);
        TestController controller = controller(mutableWorld);
        TestPlayer player = player(true, mutableWorld);
        StructureOrientation orientation =
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false);

        mutableWorld.setBlockState(BlockPos.ORIGIN, dirtInfo.getBlockState());
        StructureCheckResult check = evaluator.check(StructureOperationRequest.check(
                mutableWorld, BlockPos.ORIGIN, orientation, false, null, controller));
        mutableWorld.setBlockState(BlockPos.ORIGIN, Blocks.AIR.getDefaultState());
        StructureBuildResult build = evaluator.creativeBuildSingle(
                StructureOperationRequest.creativeBuild(player, controller, orientation, null, false));
        StructureHintResult hint = evaluator.hintSingle(
                StructureOperationRequest.hint(player, controller, orientation, null, ItemStack.EMPTY));
        StructureIterateResult iterate = evaluator.iterateSingleResult(
                StructureOperationRequest.iterate(world, BlockPos.ORIGIN, orientation, controller));

        assertEquals("v3-typed-single", check.getDiagnostics().getPath());
        assertEquals("MATCH_WORLD", check.getDiagnostics().getOperation());
        assertEquals(1, check.getDiagnostics().getPieceCount());
        assertTrue(check.getDiagnostics().isSyntheticSinglePiece());
        assertEquals("v3-typed-single", build.getDiagnostics().getPath());
        assertEquals("CREATIVE_BUILD", build.getDiagnostics().getOperation());
        assertEquals("v3-typed-single", hint.getDiagnostics().getPath());
        assertEquals("HINT", hint.getDiagnostics().getOperation());
        assertEquals("v3-typed-single", iterate.getDiagnostics().getPath());
        assertEquals("ITERATE", iterate.getDiagnostics().getOperation());
    }

    @Test
    void splitOperationServicesKeepRequestKindBoundaries() {
        MutableWorld mutableWorld = mutableWorld();
        MultiblockState state = new MultiblockState(singleCellTemplate(new DirectCandidateElement(dirtInfo)));
        StructureOperationEvaluator evaluator =
                new StructureOperationEvaluator(null, state.getBackingState(), null, null);
        TestController controller = controller(mutableWorld);
        TestPlayer player = player(true, mutableWorld);
        StructureOrientation orientation =
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false);

        StructureOperationRequest previewRequest =
                StructureOperationRequest.preview(new int[] {1}, null);
        StructureOperationRequest iterateRequest =
                StructureOperationRequest.iterate(mutableWorld, BlockPos.ORIGIN, orientation, controller);
        StructureOperationRequest buildRequest =
                StructureOperationRequest.creativeBuild(player, controller, orientation, null, false);
        StructureOperationRequest hintRequest =
                StructureOperationRequest.hint(player, controller, orientation, null, ItemStack.EMPTY);
        StructureOperationRequest snapshotRequest =
                StructureOperationRequest.snapshotCheck(mutableWorld, BlockPos.ORIGIN, orientation, controller);

        assertThrows(IllegalArgumentException.class, () -> evaluator.previewSingleResult(iterateRequest));
        assertThrows(IllegalArgumentException.class, () -> evaluator.iterateSingleResult(previewRequest));
        assertThrows(IllegalArgumentException.class, () -> evaluator.creativeBuildSingle(hintRequest));
        assertThrows(IllegalArgumentException.class, () -> evaluator.hintSingle(buildRequest));
        assertThrows(IllegalArgumentException.class, () -> evaluator.checkSnapshot(previewRequest));

        assertEquals("v3-typed-single", evaluator.previewSingleResult(previewRequest).getDiagnostics().getPath());
        assertEquals("v3-typed-single", evaluator.iterateSingleResult(iterateRequest).getDiagnostics().getPath());
        assertEquals(StructureSnapshotResult.Outcome.CAPABILITY_UNSUPPORTED,
                evaluator.checkSnapshot(snapshotRequest).getOutcome());
    }

    @Test
    void hintResultRecordsActualRenderingOutcome() {
        MultiblockState state = new MultiblockState(singleCellTemplate(new ContextHintElement()));
        TestController controller = controller(world);

        StructureHintResult result = state.spawnHintsAtWithResult(
                world, controller, BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                null, ItemStack.EMPTY);

        assertEquals(1, result.getVisitedCells());
        assertEquals(0, result.getTriggerHandledCells());
        assertEquals(1, result.getContextFallbackCells());
        assertEquals(1, result.getRenderedCells());
        assertEquals(0, result.getSkippedRenderCells());
        assertEquals(0, result.getFailedRenderCells());
    }

    @Test
    void consumeFailureAfterPlacementRollsBackWorldAndSummary() {
        MutableWorld mutableWorld = mutableWorld();
        TestPlayer player = player(false, mutableWorld);
        player.inventory.mainInventory.set(0, new ItemStack(Blocks.DIRT));
        mutableWorld.clearInventoryAfterNextPlacement(player);
        MultiblockState state = new MultiblockState(singleCellTemplate(new DirectCandidateElement(dirtInfo)));

        StructureBuildResult result = state.autoBuildAtWithResult(
                player, controller(mutableWorld), BlockPos.ORIGIN,
                StructureOrientation.of(EnumFacing.NORTH, EnumFacing.NORTH, EnumFacing.UP, false, false),
                0, 0, 0, null, false, null, SURVIVAL_BUILD, ItemStack.EMPTY);

        assertEquals(1, result.getPlacementBudget());
        assertEquals(0, result.getPlacedCells());
        assertEquals(1, result.getUnavailableItemCells());
        assertTrue(result.getConsumedItems().isEmpty());
        assertEquals(1, result.getMissingItems().get(0).getCount());
        assertEquals(Blocks.AIR.getDefaultState(), mutableWorld.getBlockState(BlockPos.ORIGIN));
    }

    private static TestPlayer player(boolean creative) {
        return player(creative, world);
    }

    private static TestPlayer player(boolean creative, @NotNull World playerWorld) {
        try {
            TestPlayer player = (TestPlayer) unsafe().allocateInstance(TestPlayer.class);
            player.world = playerWorld;
            player.inventory = new InventoryPlayer(player);
            player.creative = creative;
            return player;
        } catch (InstantiationException e) {
            throw new AssertionError("Unable to allocate test player", e);
        }
    }

    @NotNull
    private static MutableWorld mutableWorld() {
        try {
            MutableWorld mutableWorld = (MutableWorld) unsafe().allocateInstance(MutableWorld.class);
            mutableWorld.states = new HashMap<>();
            return mutableWorld;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate mutable test world", e);
        }
    }

    @NotNull
    private static TestController controller(@NotNull World controllerWorld) {
        try {
            TestController controller = (TestController) unsafe().allocateInstance(TestController.class);
            controller.world = controllerWorld;
            controller.pos = BlockPos.ORIGIN;
            return controller;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate test controller", e);
        }
    }

    @NotNull
    private static PieceTemplate singleCellTemplate(@NotNull IStructureElement<?> element) {
        TraceabilityPredicate predicate = element.toPredicate();
        if (predicate == null) {
            predicate = new TraceabilityPredicate(worldState -> true, element::getCandidates);
            if (element.isCenter()) {
                predicate.setCenter();
            }
        }
        return new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { predicate }
                        }
                },
                new IStructureElement<?>[][][] {
                        {
                                { element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
    }

    @NotNull
    private static PieceTemplate singleCellTemplateWithoutLegacyCandidates(@NotNull IStructureElement<?> element) {
        TraceabilityPredicate predicate = new TraceabilityPredicate(worldState -> false);
        if (element.isCenter()) {
            predicate.setCenter();
        }
        return new PieceTemplate(
                new TraceabilityPredicate[][][] {
                        {
                                { predicate }
                        }
                },
                new IStructureElement<?>[][][] {
                        {
                                { element }
                        }
                },
                new RelativeDirection[] {
                        RelativeDirection.RIGHT,
                        RelativeDirection.UP,
                        RelativeDirection.BACK
                },
                new int[][] {
                        { 1, 1 }
                },
                null,
                new int[] {0, 0, 0, 0, 0},
                null);
    }

    @NotNull
    private static World bareWorld() {
        try {
            return (World) unsafe().allocateInstance(BareWorld.class);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to allocate bare test world", e);
        }
    }

    @NotNull
    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access Unsafe", e);
        }
    }

    private static final class RecordingElement implements IStructureElement<Object> {

        private final boolean matches;

        private RecordingElement(boolean matches) {
            this.matches = matches;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            if (matches) {
                context.getCollector().addPart(new TestPart());
                context.getLegacyContext().set("build_probe", Boolean.TRUE);
            }
            return matches;
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return false;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}
    }

    private static final class DirectCandidateElement implements IStructureElement<Object> {

        @NotNull
        private final BlockInfo candidate;
        @NotNull
        private final TraceabilityPredicate predicate;

        private DirectCandidateElement(@NotNull BlockInfo candidate) {
            this.candidate = candidate;
            this.predicate = new TraceabilityPredicate(
                    worldState -> worldState.getBlockState() == candidate.getBlockState());
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return context.getBlockState() == candidate.getBlockState();
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return world.getBlockState(pos) == candidate.getBlockState();
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] { candidate };
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return world.setBlockState(pos, candidate.getBlockState());
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @Override
        public TraceabilityPredicate toPredicate() {
            return predicate;
        }
    }

    private static final class DirectChannelElement implements IStructureElement<Object> {

        @NotNull
        private final BlockInfo first;
        @NotNull
        private final BlockInfo second;
        @NotNull
        private final StructureElementPreview preview;

        private DirectChannelElement(@NotNull BlockInfo first,
                                     @NotNull BlockInfo second,
                                     @NotNull String channelName) {
            this.first = first;
            this.second = second;
            this.preview = StructureElementPreview.builder()
                    .common(StructureElementPreview.CandidateGroup.builder(this::getCandidates)
                            .channel(channelName)
                            .build())
                    .build();
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            IBlockState blockState = context.getBlockState();
            return blockState == first.getBlockState() || blockState == second.getBlockState();
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            IBlockState blockState = world.getBlockState(pos);
            return blockState == first.getBlockState() || blockState == second.getBlockState();
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] { first, second };
        }

        @NotNull
        @Override
        public StructureElementPreview getPreview() {
            return preview;
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return world.setBlockState(pos, first.getBlockState());
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}
    }

    private static final class ThrowingLegacyPredicateDirectElement implements IStructureElement<Object> {

        @NotNull
        private final BlockInfo candidate;

        private ThrowingLegacyPredicateDirectElement(@NotNull BlockInfo candidate) {
            this.candidate = candidate;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return context.getBlockState() == candidate.getBlockState();
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return world.getBlockState(pos) == candidate.getBlockState();
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] {candidate};
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @Override
        public TraceabilityPredicate toPredicate() {
            throw new AssertionError("typed preview/diagnostic path should not request legacy predicate view");
        }
    }

    private static final class ContextHintElement implements IStructureElement<Object> {

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return true;
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] { stoneInfo };
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @NotNull
        @Override
        public StructureHintRenderResult spawnHintWithResult(
                World world, BlockPos pos, @NotNull ItemStack trigger) {
            return StructureHintRenderResult.skipped(StructureHintRenderResult.Source.TRIGGER);
        }

        @NotNull
        @Override
        public StructureHintRenderResult spawnHintWithResult(
                @NotNull StructureEvaluationContext<Object> context) {
            return StructureHintRenderResult.rendered(StructureHintRenderResult.Source.CONTEXT);
        }
    }

    private static final class PreviewTooltipElement implements IStructureElement<Object> {

        @NotNull
        private final BlockInfo candidate;
        @NotNull
        private final String tooltip;

        private PreviewTooltipElement(@NotNull BlockInfo candidate, @NotNull String tooltip) {
            this.candidate = candidate;
            this.tooltip = tooltip;
        }

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return context.getBlockState() == candidate.getBlockState();
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return world.getBlockState(pos) == candidate.getBlockState();
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[] {candidate};
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @Override
        public void addPreviewTooltip(@NotNull java.util.List<String> tooltip) {
            tooltip.add(this.tooltip);
        }
    }

    private static final class CenterOnlyElement implements IStructureElement<Object> {

        @Override
        public boolean check(@NotNull StructureEvaluationContext<Object> context) {
            return true;
        }

        @Override
        public boolean check(World world, BlockPos pos, PatternMatchContext context) {
            return true;
        }

        @Override
        public BlockInfo[] getCandidates() {
            return new BlockInfo[0];
        }

        @Override
        public boolean placeBlock(World world, BlockPos pos, PatternMatchContext context,
                                  EntityPlayer player, boolean skipHatches) {
            return false;
        }

        @Override
        public void spawnHint(World world, BlockPos pos) {}

        @Override
        public boolean isCenter() {
            return true;
        }
    }

    private static final class TestPart implements IMultiblockPart {

        @Override
        public boolean isAttachedToMultiBlock() {
            return false;
        }

        @Override
        public void addToMultiBlock(MultiblockControllerBase controllerBase) {}

        @Override
        public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {}
    }

    private static final class TestController extends MultiblockControllerBase {

        private World world;
        private BlockPos pos;

        private TestController() {
            super(new ResourceLocation("gregtech", "test_controller"));
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return this;
        }

        @Override
        public World getWorld() {
            return world;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public @NotNull EnumFacing getFrontFacing() {
            return EnumFacing.NORTH;
        }

        @Override
        protected void updateFormedValid() {}

        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }

    private static final class TestPlayer extends EntityPlayer {

        private boolean creative;

        private TestPlayer(World world) {
            super(world, null);
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return creative;
        }
    }

    private static final class MutableWorld extends World {

        private Map<BlockPos, IBlockState> states;
        private TestPlayer playerToClear;

        private MutableWorld() {
            super(null, null, null, null, false);
        }

        private void clearInventoryAfterNextPlacement(@NotNull TestPlayer player) {
            this.playerToClear = player;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(pos.toImmutable(), Blocks.AIR.getDefaultState());
        }

        @Override
        public boolean setBlockState(BlockPos pos, IBlockState newState) {
            return setBlockState(pos, newState, 3);
        }

        @Override
        public boolean setBlockState(BlockPos pos, IBlockState newState, int flags) {
            states.put(pos.toImmutable(), newState);
            if (playerToClear != null) {
                playerToClear.inventory.mainInventory.set(0, ItemStack.EMPTY);
                playerToClear = null;
            }
            return true;
        }

        @Override
        public BlockPos getSpawnPoint() {
            return BlockPos.ORIGIN;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }

    private static final class BareWorld extends World {

        private BareWorld() {
            super(null, null, null, null, false);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.getDefaultState();
        }

        @Override
        public BlockPos getSpawnPoint() {
            return BlockPos.ORIGIN;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return null;
        }

        @Override
        protected boolean isChunkLoaded(int x, int z, boolean allowEmpty) {
            return false;
        }
    }
}
