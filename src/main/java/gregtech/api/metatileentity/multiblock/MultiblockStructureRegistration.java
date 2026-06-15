package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.PieceRuntimes;
import gregtech.api.pattern.CommittedStructureGraph;
import gregtech.api.pattern.StructureRuntime;
import gregtech.api.util.world.DummyWorld;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MultiblockStructureRegistration {

    private MultiblockStructureRegistration() {}

    static void registerFormedDefinition(@NotNull MultiblockControllerBase controller,
                                         @Nullable MultiPiecePattern multiPiecePattern,
                                         @Nullable PieceRuntimes pieceRuntimes) {
        if (controller.getWorld() instanceof DummyWorld || multiPiecePattern == null) {
            return;
        }
        if (registerCommittedGraph(controller, multiPiecePattern)) {
            return;
        }
        registerMultiPiecePattern(controller, multiPiecePattern, pieceRuntimes);
    }

    static void refreshMultiPieceRegistrationFromRuntime(@NotNull MultiblockControllerBase controller,
                                                         @Nullable MultiPiecePattern multiPiecePattern,
                                                         @Nullable PieceRuntimes pieceRuntimes) {
        if (multiPiecePattern == null || pieceRuntimes == null || !controller.isStructureFormed()
                || controller.getWorld() instanceof DummyWorld) {
            return;
        }

        MultiblockWorldData worldData = MultiblockWorldData.get(controller.getWorld());
        worldData.unregisterMultiblock(controller);
        if (registerCommittedGraph(controller, multiPiecePattern)) {
            return;
        }
        registerMultiPiecePattern(controller, multiPiecePattern, pieceRuntimes);
    }

    static void registerMultiPiecePattern(@NotNull MultiblockControllerBase controller,
                                          @Nullable MultiPiecePattern multiPiecePattern,
                                          @Nullable PieceRuntimes pieceRuntimes) {
        if (multiPiecePattern == null || controller.getWorld() == null || controller.getWorld() instanceof DummyWorld) {
            return;
        }
        if (registerCommittedGraph(controller, multiPiecePattern)) {
            return;
        }

        LongSet allPositions = multiPiecePattern.getAllPositions(pieceRuntimes, controller);
        if (!allPositions.isEmpty()) {
            MultiblockWorldData.get(controller.getWorld()).registerMultiblock(controller, allPositions,
                    multiPiecePattern);
        }
    }

    static boolean registerCommittedGraph(@NotNull MultiblockControllerBase controller,
                                          @Nullable MultiPiecePattern multiPiecePattern) {
        if (multiPiecePattern == null || controller.getWorld() == null
                || controller.getWorld() instanceof DummyWorld) {
            return false;
        }
        StructureRuntime runtime = controller.getStructureRuntime();
        if (runtime == null) {
            return false;
        }
        CommittedStructureGraph graph = runtime.getCommittedGraph();
        if (graph == null || graph.getPositionIndex().getAllWatchedPositions().isEmpty()) {
            return false;
        }
        MultiblockWorldData.get(controller.getWorld()).registerMultiblock(
                controller, graph.getPositionIndex(), multiPiecePattern);
        return true;
    }

}
