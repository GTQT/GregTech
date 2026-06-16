package gregtech.api.pattern;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

class StructureInternalLegacyBoundaryScanTest {

    private static final Path SOURCE_ROOT = Paths.get("src/main/java");
    private static final Path COMMON_CONTROLLER_ROOT = SOURCE_ROOT.resolve("gregtech/common/metatileentities");
    private static final Path GTQT_CONTROLLER_ROOT = SOURCE_ROOT.resolve("gtqt");

    private static final List<String> INTERNAL_MAIN_PATHS = Arrays.asList(
            "gregtech/api/pattern/StructureRuntime.java",
            "gregtech/api/pattern/StructureBuildOperationService.java",
            "gregtech/api/pattern/StructureCheckOperationService.java",
            "gregtech/api/pattern/StructureHintOperationService.java",
            "gregtech/api/pattern/StructureIterateOperationService.java",
            "gregtech/api/pattern/StructurePreviewOperationService.java",
            "gregtech/api/pattern/StructureSnapshotOperationService.java",
            "gregtech/api/metatileentity/multiblock/MultiblockStructureCheckScheduler.java",
            "gregtech/api/metatileentity/multiblock/MultiblockStructureOperations.java",
            "gregtech/api/metatileentity/multiblock/MultiblockStructureCommitter.java",
            "gregtech/api/metatileentity/multiblock/AsyncStructureChecker.java",
            "gregtech/api/metatileentity/multiblock/MultiblockStructurePreviews.java",
            "gregtech/api/metatileentity/multiblock/MultiblockStructureRegistration.java",
            "gregtech/api/metatileentity/registry/MBPattern.java",
            "gregtech/api/util/tooltips/StructureComponent.java",
            "gregtech/client/renderer/handler/MultiblockPreviewRenderer.java",
            "gregtech/client/renderer/handler/GhostBlockRenderer.java",
            "gregtech/integration/jei/multiblock/MultiblockInfoRecipeWrapper.java",
            "gregtech/common/items/behaviors/MultiblockBuilderBehavior.java",
            "gregtech/common/items/behaviors/MultiblockRemovalBehavior.java",
            "gregtech/common/items/behaviors/StructureProjectorBehavior.java");

    private static final List<ForbiddenToken> FORBIDDEN_TOKENS = Arrays.asList(
            new ForbiddenToken("legacy predicate cube traversal",
                    Pattern.compile("TraceabilityPredicate\\s*\\[\\s*]\\s*\\[\\s*]\\s*\\[")),
            new ForbiddenToken("materialized legacy predicate map",
                    Pattern.compile("\\.getBlockMatches\\s*\\(")),
            new ForbiddenToken("legacy block-pattern owner construction",
                    Pattern.compile("new\\s+BlockPattern\\s*\\(")),
            new ForbiddenToken("legacy multiblock-state owner construction",
                    Pattern.compile("new\\s+MultiblockState\\s*\\(")),
            new ForbiddenToken("legacy multiblock-state tooling traversal",
                    Pattern.compile("\\.getMultiblockState\\s*\\(")),
            new ForbiddenToken("direct preview-cell traversal outside typed result",
                    Pattern.compile("\\.createPreviewCells\\s*\\(")),
            new ForbiddenToken("deprecated predicate map tooling accessor",
                    Pattern.compile("\\.getPredicateMap\\s*\\(")),
            new ForbiddenToken("direct legacy fast check",
                    Pattern.compile("\\.checkPatternFastAt\\s*\\(")),
            new ForbiddenToken("legacy-view traversal",
                    Pattern.compile("PieceTemplateLegacyView|\\.getLegacyView\\s*\\(")));

    private static final Pattern LEGACY_FORM_CALLBACK =
            Pattern.compile("\\bformStructure\\s*\\(\\s*PatternMatchContext\\b");
    private static final Pattern LEGACY_STRUCTURE_DECLARATION =
            Pattern.compile("\\bcreateStructurePattern\\s*\\(");
    private static final Pattern LEGACY_CALLBACK_CONTEXT_COPY =
            Pattern.compile("\\.copyLegacyCallbackContext\\s*\\(");
    private static final Pattern FORMED_STRUCTURE_ESCAPE_ACCESSOR =
            Pattern.compile("\\bformed\\s*\\.\\s*(?:getMetadata"
                    + "|copyChannelValues"
                    + "|copyOperationState"
                    + "|getAggregateValues)\\s*\\("
                    + "|\\bformed\\s*\\.\\s*getAggregate\\s*\\(\\s*\"");
    private static final Pattern LEGACY_DYNAMIC_TOOLING =
            Pattern.compile("\\b(?:FactoryBlockPattern|BlockPattern|BlockPatternTemplate|MultiblockState)\\b"
                    + "|\\b(?:buildFactoryPattern|buildStructurePattern(?:ForChannelValues|ForLogSize)?)\\s*\\("
                    + "|\\.(?:getTemplate|previewDynamicStructure|autoBuildDynamicStructure|hintDynamicStructure)"
                    + "\\s*\\(");
    private static final Pattern LEGACY_JEI_PREVIEW_FALLBACK =
            Pattern.compile("\\bgetLegacyPredicateFallback\\s*\\("
                    + "|\\bBlockWorldState\\b"
                    + "|\\bPatternMatchContext\\b"
                    + "|\\bStructureElementPreview(?:Entry)?\\.fromPredicate\\s*\\(");
    private static final Pattern LEGACY_DECLARATION_BUILD =
            Pattern.compile("\\bFactoryBlockPattern\\.start\\s*\\("
                    + "|\\.buildTemplate\\s*\\(");
    private static final Pattern LEGACY_ELEMENT_WORLD_SIGNATURE =
            Pattern.compile("\\b(?:check|placeBlock|spawnHint|couldBeValid|getBlocksToPlace|survivalPlaceBlock"
                    + "|spawnHintWithResult)\\s*\\(\\s*World\\b");
    private static final Pattern STABLE_DEFINITION_RETURN =
            Pattern.compile(
                    "createStructureDefinition\\s*\\(\\s*\\)\\s*\\{\\s*"
                            + "return\\s+STRUCTURE_DEFINITION\\s*;",
                    Pattern.DOTALL);

    @Test
    void internalStructureMainPathsDoNotReintroduceLegacyTraversalOwners() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String path : INTERNAL_MAIN_PATHS) {
            Path source = SOURCE_ROOT.resolve(path);
            if (!Files.isRegularFile(source)) {
                violations.add(path + ": file is missing from scan target");
                continue;
            }
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                for (ForbiddenToken token : FORBIDDEN_TOKENS) {
                    if (token.pattern.matcher(line).find() && !isAllowedBridge(path, i + 1, line)) {
                        violations.add(path + ":" + (i + 1) + " " + token.description
                                + " -> " + line.trim());
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Legacy structure traversal leaked into internal V3 main paths:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void gregTechControllersUseTypedFormationCallbacksAndDefinitions() throws IOException {
        List<String> violations = new ArrayList<>();
        scanJavaFiles(Arrays.asList(COMMON_CONTROLLER_ROOT, GTQT_CONTROLLER_ROOT), (source, lines) -> {
            String text = String.join("\n", lines);
            if (!text.contains("extends ") || !text.contains("Multiblock")) {
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (LEGACY_FORM_CALLBACK.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " legacy formation callback -> " + line.trim());
                }
                if (LEGACY_STRUCTURE_DECLARATION.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " legacy structure declaration -> " + line.trim());
                }
                if (LEGACY_CALLBACK_CONTEXT_COPY.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " legacy callback context read -> " + line.trim());
                }
            }
        });

        if (!violations.isEmpty()) {
            fail("GregTech-owned controllers must use StructureDefinition and FormedStructureView:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void structureElementsDoNotExposeLegacyWorldPatternMatchContextSignatures() throws IOException {
        List<String> violations = new ArrayList<>();
        scanJavaFiles(SOURCE_ROOT.resolve("gregtech/api/pattern/element"), (source, lines) -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (LEGACY_ELEMENT_WORLD_SIGNATURE.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " legacy element world/context signature -> " + line.trim());
                }
            }
        });

        if (!violations.isEmpty()) {
            fail("Structure elements must use StructureEvaluationContext; "
                    + "legacy World/PatternMatchContext element signatures are forbidden:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void gregTechOwnedCodeDoesNotUsePatternMatchContextInternally() throws IOException {
        List<String> violations = new ArrayList<>();
        scanJavaFiles(Arrays.asList(COMMON_CONTROLLER_ROOT, GTQT_CONTROLLER_ROOT), (source, lines) -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (line.contains("PatternMatchContext")) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " internal PatternMatchContext reference -> " + line.trim());
                }
            }
        });

        if (!violations.isEmpty()) {
            fail("GregTech-owned common/gtqt code must use typed structure contexts; "
                    + "PatternMatchContext is restricted to API compatibility adapters:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void gregTechControllersUseTypedFormedStructureViewAccessors() throws IOException {
        List<String> violations = new ArrayList<>();
        scanJavaFiles(Arrays.asList(COMMON_CONTROLLER_ROOT, GTQT_CONTROLLER_ROOT), (source, lines) -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (FORMED_STRUCTURE_ESCAPE_ACCESSOR.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " formed view escape accessor -> " + line.trim());
                }
            }
        });

        if (!violations.isEmpty()) {
            fail("GregTech-owned controllers should read FormedStructureView through typed accessors:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void runtimeShapedControllersKeepStableDefinitions() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> detectorControllers = Arrays.asList(
                "gregtech/common/metatileentities/multi/electric/MetaTileEntityCleanroom.java",
                "gregtech/common/metatileentities/multi/electric/centralmonitor/"
                        + "MetaTileEntityCentralMonitor.java",
                "gregtech/common/metatileentities/primitive/"
                        + "MetaTileEntityCharcoalPileIgniter.java");

        for (String path : detectorControllers) {
            String text = readSource(path);
            if (!text.contains(".runtimeDetector(")) {
                violations.add(path + ": missing stable runtime detector");
            }
            if (!STABLE_DEFINITION_RETURN.matcher(text).find()) {
                violations.add(path + ": createStructureDefinition must return STRUCTURE_DEFINITION");
            }
            rejectDynamicDefinitionLifecycle(path, text, violations);
        }

        String godforgePath =
                "gregtech/common/metatileentities/multi/electric/godforge/"
                        + "MetaTileEntityForgeOfGods.java";
        String godforge = readSource(godforgePath);
        if (!godforge.contains(".conditionalPieceContextual(")) {
            violations.add(godforgePath + ": missing stable contextual ring pieces");
        }
        for (String runtimeOnlyPiece : Arrays.asList(
                "first_ring_air",
                "second_ring_air",
                "third_ring_air")) {
            if (!containsRuntimeOnlyPiece(godforge, runtimeOnlyPiece)) {
                violations.add(godforgePath + ": " + runtimeOnlyPiece
                        + " must be marked runtimeOnly()/hideFromTooling()");
            }
        }
        if (!STABLE_DEFINITION_RETURN.matcher(godforge).find()) {
            violations.add(godforgePath
                    + ": createStructureDefinition must return STRUCTURE_DEFINITION");
        }
        rejectDynamicDefinitionLifecycle(godforgePath, godforge, violations);

        if (!violations.isEmpty()) {
            fail("Runtime-shaped controllers must keep stable StructureDefinitions:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void gregTechControllersDoNotBuildLegacyTemplatesInternally() throws IOException {
        List<String> violations = new ArrayList<>();
        scanJavaFiles(Arrays.asList(COMMON_CONTROLLER_ROOT, GTQT_CONTROLLER_ROOT), (source, lines) -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (LEGACY_DECLARATION_BUILD.matcher(line).find()) {
                    violations.add(relativeMainPath(source) + ":" + (i + 1)
                            + " legacy declaration build -> " + line.trim());
                }
            }
        });

        if (!violations.isEmpty()) {
            fail("GregTech-owned controllers must build StructureDefinition directly; "
                    + "DeclarativePatternBuilder.start() is allowed, but FactoryBlockPattern.start() "
                    + "and builder .buildTemplate() are not:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void migratedDynamicSizedControllerToolingUsesTypedTemplatePath() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> controllerPaths = Arrays.asList(
                "gregtech/common/metatileentities/multi/electric/MetaTileEntityCleanroom.java",
                "gregtech/common/metatileentities/primitive/MetaTileEntityCharcoalPileIgniter.java");
        for (String path : controllerPaths) {
            rejectLegacyDynamicTooling(path, violations);
        }

        if (!violations.isEmpty()) {
            fail("Migrated dynamic JEI/projector/hint tooling must stay on PieceTemplate/StructureDefinition:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void jeiTooltipAndCandidatePreviewDoesNotUseLegacyPredicateFallback() throws IOException {
        List<String> violations = new ArrayList<>();
        List<String> paths = Arrays.asList(
                "gregtech/api/metatileentity/registry/MBPattern.java",
                "gregtech/integration/jei/multiblock/MultiblockInfoRecipeWrapper.java");
        for (String path : paths) {
            List<String> lines = Files.readAllLines(SOURCE_ROOT.resolve(path), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isCommentOnlyLine(line)) {
                    continue;
                }
                if (LEGACY_JEI_PREVIEW_FALLBACK.matcher(line).find()) {
                    violations.add(path + ":" + (i + 1)
                            + " legacy JEI preview fallback -> " + line.trim());
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("JEI tooltip/candidate preview must consume typed preview entries only:\n"
                    + String.join("\n", violations));
        }
    }

    private static void rejectLegacyDynamicTooling(@NotNull String path,
                                                   @NotNull List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(SOURCE_ROOT.resolve(path), StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isCommentOnlyLine(line)) {
                continue;
            }
            if (LEGACY_DYNAMIC_TOOLING.matcher(line).find()) {
                violations.add(path + ":" + (i + 1)
                        + " legacy dynamic tooling API -> " + line.trim());
            }
        }
    }

    private static void rejectDynamicDefinitionLifecycle(
            @NotNull String path,
            @NotNull String text,
            @NotNull List<String> violations) {
        if (text.contains("reinitializeStructurePattern(")) {
            violations.add(path + ": runtime state must not rebuild the structure definition");
        }
        if (text.contains("System.identityHashCode(this)")) {
            violations.add(path + ": instance-keyed structure definitions are forbidden");
        }
        if (text.contains("TemplatePool.getInstance().evict(")) {
            violations.add(path + ": runtime structure checks must not evict templates");
        }
    }

    private static boolean containsRuntimeOnlyPiece(@NotNull String source,
                                                    @NotNull String pieceName) {
        int pieceStart = source.indexOf("\"" + pieceName + "\"");
        if (pieceStart < 0) {
            return false;
        }
        int pieceEnd = source.indexOf(".end();", pieceStart);
        if (pieceEnd < 0) {
            return false;
        }
        String pieceBlock = source.substring(pieceStart, pieceEnd);
        return pieceBlock.contains(".runtimeOnly()")
                || pieceBlock.contains(".hideFromTooling()");
    }

    @NotNull
    private static String readSource(@NotNull String path) throws IOException {
        return new String(
                Files.readAllBytes(SOURCE_ROOT.resolve(path)),
                StandardCharsets.UTF_8);
    }

    private static boolean isAllowedBridge(@NotNull String path, int lineNumber, @NotNull String line) {
        if (path.endsWith("StructureCheckOperationService.java")
                && line.contains("checkPatternAtExact")) {
            return true;
        }
        if (path.endsWith("StructurePreviewOperationService.java")
                && line.contains("createPreviewCells")) {
            return true;
        }
        return false;
    }

    private static void scanJavaFiles(@NotNull Path root, @NotNull SourceConsumer consumer) throws IOException {
        try (var stream = Files.walk(root)) {
            for (Path source : (Iterable<Path>) stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))::iterator) {
                consumer.accept(source, Files.readAllLines(source, StandardCharsets.UTF_8));
            }
        }
    }

    private static void scanJavaFiles(@NotNull List<Path> roots,
                                      @NotNull SourceConsumer consumer) throws IOException {
        for (Path root : roots) {
            scanJavaFiles(root, consumer);
        }
    }

    @NotNull
    private static String relativeMainPath(@NotNull Path source) {
        return SOURCE_ROOT.relativize(source).toString().replace('\\', '/');
    }

    private static boolean isCommentOnlyLine(@NotNull String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("//")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*")
                || trimmed.startsWith("*/");
    }

    @FunctionalInterface
    private interface SourceConsumer {

        void accept(@NotNull Path source, @NotNull List<String> lines) throws IOException;
    }

    private static final class ForbiddenToken {

        private final String description;
        private final Pattern pattern;

        private ForbiddenToken(@NotNull String description, @NotNull Pattern pattern) {
            this.description = description;
            this.pattern = pattern;
        }
    }
}
