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
        scanJavaFiles(COMMON_CONTROLLER_ROOT, (source, lines) -> {
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
            }
        });

        if (!violations.isEmpty()) {
            fail("GregTech-owned controllers must use StructureDefinition and FormedStructureView:\n"
                    + String.join("\n", violations));
        }
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
