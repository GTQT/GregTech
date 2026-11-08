package gregtech.integration.jei.multiblock;

import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.client.renderer.handler.GhostBlockRenderer;
import gregtech.client.renderer.scene.StructurePreviewRenderer;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper.CameraDragMode;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper.PreviewCandidate;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.vecmath.Vector3f;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fullscreen structure viewer laid out after GTO's structure preview screen.
 *
 * <p>
 * The whole screen is one window panel inset by 8px: a 14px title row runs along the top (title on the left,
 * zoom / settings / close on the right), the 3D scene fills the area below it, and the cards float on the right
 * edge of the scene. JEI mode uses GTO's read-only composition: minimap at the top, structure configuration
 * below it, and no world actions. Builder mode is a separate world-facing variant: it omits the minimap and
 * puts the adjustable hatch-count card above the structure configuration and the project/build footer.
 * Placement alternatives open as a card on the left edge. The cards are absolutely positioned at the same
 * insets GTO uses (8px window margin, 5px padding, 4px gap), so the composition matches the reference
 * screenshots instead of inventing a new chrome.
 *
 * <p>
 * Rendering and camera state stay owned by {@link MultiblockInfoRecipeWrapper}; this screen only draws the
 * chrome and forwards input.
 */
@SideOnly(Side.CLIENT)
public class MultiblockInfoFullscreenScreen extends GuiScreen {

    // ---------------------------------------------------------------------------------------------
    // GTO layout tokens (uipro UISizes / UITheme equivalents).
    // ---------------------------------------------------------------------------------------------
    private static final int MARGIN = 8;
    private static final int PADDING = 5;
    private static final int GAP = 4;
    private static final int CONTROL_HEIGHT = 14;
    private static final int CARD_WIDTH = 178;
    private static final int CARD_HEADER = 14;
    private static final int CARD_CONTENT_WIDTH = CARD_WIDTH - PADDING * 2;

    private static final int NODE_HEADER = 12;
    private static final int NODE_ROW = 14;
    private static final int ARROW_HEIGHT = 8;

    private static final int BUTTON_SIZE = 14;
    private static final int ZOOM_LABEL_WIDTH = 34;
    private static final int CARD_ACTION_HEIGHT = BUTTON_SIZE + GAP;

    private static final String PIECE_CHANNEL = "structure_piece";

    private static final int MINIMAP_MIN = 96;
    private static final int MINIMAP_MAX = 178;
    private static final int MINIMAP_BORDER = 2;

    // ---------------------------------------------------------------------------------------------
    // Palette: flat vanilla grey with dark ink.
    // ---------------------------------------------------------------------------------------------
    private static final int WINDOW_FILL = 0xFFC6C6C6;
    private static final int WINDOW_EDGE = 0xFF373737;
    private static final int EDGE_SOFT = 0xFF8A8A8A;
    private static final int CARD_FILL = 0xFFC6C6C6;
    private static final int HEADER_FILL = 0xFFBEBEBE;
    private static final int NODE_FILL = 0xFFE9E9E9;
    private static final int NODE_EDGE = 0xFF7FAF72;
    private static final int NODE_ACTIVE_FILL = 0xFFECF6EC;
    private static final int NODE_ACTIVE_EDGE = 0xFF4C9A4C;
    private static final int ACCENT = 0xFF2F6FA8;
    private static final int TEXT_PRIMARY = 0xFF202020;
    private static final int TEXT_SECONDARY = 0xFF404040;
    private static final int TEXT_DIM = 0xFF6A6A6A;
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SELECTION = 0xFFFFC83D;

    private static final ResourceLocation MINIMAP_TEXTURE = new ResourceLocation("gregtech",
            "structure_preview_minimap");

    // ---------------------------------------------------------------------------------------------
    // Cross-screen state: only one fullscreen viewer may exist at a time because it takes the shared
    // FBO/preview of a wrapper with it.
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static MultiblockInfoFullscreenScreen openScreen;

    @Nullable
    public static MultiblockInfoFullscreenScreen getOpenScreen() {
        return openScreen;
    }

    private final MultiblockInfoRecipeWrapper wrapper;
    private final boolean builderMode;

    @Nullable
    private static GuiScreen previousScreen;

    @Nullable
    private StructurePreviewRenderer syncedRenderer;

    // Layout computed for the current frame.
    private int frameX, frameY, frameWidth, frameHeight;
    private int sceneX, sceneY, sceneWidth, sceneHeight;
    private int mapX, mapY, mapSize;
    private int hatchX, hatchY, hatchWidth, hatchHeight;
    private int cfgX, cfgY, cfgWidth, cfgHeight;
    private int candX, candY, candWidth;
    private int closeX, settingsX, zoomPlusX, zoomLabelX, zoomMinusX;

    // Configuration card content.
    private int configScroll;
    private int configScrollMax;
    private final List<PieceNode> configNodes = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private boolean configVisible = true;

    // Minimap.
    @Nullable
    private DynamicTexture minimapTexture;
    @Nullable
    private StructurePreviewRenderer minimapSource;
    private int minimapTexWidth;
    private int minimapTexHeight;
    private int minimapMinX;
    private int minimapMinZ;
    private int minimapOriginX;
    private int minimapOriginY;
    private float minimapScale = 1.0F;

    // Interaction state.
    private boolean draggingLeft;
    private boolean dragRightCandidate;
    private boolean minimapDragging;
    private int rightPressX;
    private int rightPressY;

    private float fitZoom = 32.0F;

    @Nullable
    private List<String> hoveredTooltip;

    public MultiblockInfoFullscreenScreen(MultiblockInfoRecipeWrapper wrapper) {
        this(wrapper, false);
    }

    public MultiblockInfoFullscreenScreen(MultiblockInfoRecipeWrapper wrapper, boolean builderMode) {
        this.wrapper = wrapper;
        this.builderMode = builderMode;
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    public void initFullscreenView() {
        applyLayout(width, height);
        rebuildConfigTree();
        wrapper.setLayer(-1);
        wrapper.ensurePreviewLoading();
    }

    private void fitCameraToViewport() {
        StructurePreviewRenderer renderer = wrapper.getCurrentRenderer();
        if (!(renderer != null && renderer.world instanceof TrackedDummyWorld)) {
            return;
        }
        Vector3f size = ((TrackedDummyWorld) renderer.world).getSize();
        float largest = Math.max(Math.max(size.x, size.y), size.z);
        float zoom = Math.max(2.5F, 1.45F * Math.max(largest, 1.5F));
        fitZoom = zoom;
        wrapper.setZoom(zoom);
    }

    public void openFromCurrentScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen current = minecraft.currentScreen;
        if (current != null && current != this) {
            previousScreen = current;
        }
        openScreen = this;
        minecraft.displayGuiScreen(this);
    }

    @Override
    public void initGui() {
        applyLayout(width, height);
        buttonList.clear();
    }

    public void close() {
        wrapper.cancelChannelDrag();
        wrapper.resetCameraDrag();
        if (openScreen == this) {
            openScreen = null;
        }
        wrapper.restoreCompactLayout();
    }

    public void collapse() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen target = previousScreen;
        previousScreen = null;
        close();
        if (target != null && target != minecraft.currentScreen) {
            minecraft.displayGuiScreen(target);
        } else {
            minecraft.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        wrapper.cancelChannelDrag();
        close();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    private void applyLayout(int width, int height) {
        frameX = MARGIN;
        frameY = MARGIN;
        frameWidth = width - MARGIN * 2;
        frameHeight = height - MARGIN * 2;

        closeX = width - MARGIN - PADDING - BUTTON_SIZE;
        settingsX = closeX - GAP - BUTTON_SIZE;
        zoomPlusX = settingsX - GAP - BUTTON_SIZE;
        zoomLabelX = zoomPlusX - GAP - ZOOM_LABEL_WIDTH;
        zoomMinusX = zoomLabelX - GAP - BUTTON_SIZE;

        // Scene starts below the title row, inset by the window padding, exactly like GTO's `place()`.
        sceneX = MARGIN + PADDING;
        sceneY = MARGIN + PADDING + CONTROL_HEIGHT + GAP;
        sceneWidth = width - MARGIN * 2 - PADDING * 2;
        sceneHeight = height - MARGIN - PADDING - sceneY;

        int inset = MARGIN + PADDING + GAP;
        if (builderMode) {
            // The builder preview is a different screen from the JEI viewer: it has no minimap.
            // Hatch caps live in their own card at the top of the right column, above the structure config.
            mapSize = 0;
            mapX = 0;
            mapY = 0;
            hatchX = width - inset - CARD_WIDTH;
            hatchY = sceneY + GAP;
            hatchWidth = CARD_WIDTH;
            hatchHeight = builderHatchCardHeight();
            cfgX = hatchX;
            cfgY = hatchY + hatchHeight + GAP;
        } else {
            hatchHeight = 0;
            mapSize = Math.max(MINIMAP_MIN, Math.min(MINIMAP_MAX, Math.min(width / 5, height / 3)));
            mapX = width - inset - mapSize;
            mapY = sceneY + GAP;
            cfgX = width - inset - CARD_WIDTH;
            cfgY = mapY + mapSize + GAP;
        }
        cfgWidth = CARD_WIDTH;
        cfgHeight = Math.max(60, height - MARGIN - PADDING - GAP - cfgY);

        candX = MARGIN + PADDING + GAP;
        candY = sceneY + GAP;
        candWidth = CARD_WIDTH;
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        applyLayout(width, height);
        hoveredTooltip = null;
        hits.clear();

        wrapper.updateChannelDrag(mouseX, Mouse.isButtonDown(0));
        wrapper.advancePreviewLoadingStep();
        syncFullscreenRenderer();
        wrapper.advanceCandidateCycle();

        drawWindowPanel();

        if (wrapper.isPreviewReady()) {
            wrapper.renderScene(mc, sceneX, sceneY, sceneWidth, sceneHeight, mouseX, mouseY);
        } else {
            drawLoadingPlaceholder();
        }

        drawMinimapCard();
        drawBuilderHatchCard(mouseX, mouseY);
        drawConfigCard(mouseX, mouseY);
        drawCandidatesCard();
        drawTitleRow(mouseX, mouseY);
        drawSummaryLine();
        drawHintBar();

        wrapper.updateHoverState(mc, mouseX, mouseY, isSceneHovered(mouseX, mouseY));
        drawTooltips(mouseX, mouseY);
    }

    private void drawWindowPanel() {
        drawRect(frameX, frameY, frameX + frameWidth, frameY + frameHeight, WINDOW_FILL);
        drawBorder(frameX, frameY, frameWidth, frameHeight, WINDOW_EDGE);
        drawRect(frameX + 1, frameY + 1, frameX + frameWidth - 1, frameY + 2, 0x40FFFFFF);
    }

    private void drawLoadingPlaceholder() {
        if (wrapper.hasPreviewFailure()) {
            String failure = I18n.format("gregtech.multiblock.preview.fullscreen.loading_failed",
                    wrapper.getPreviewFailureMessage());
            fontRenderer.drawString(failure, sceneX + 8, sceneY + sceneHeight / 2 - 4, 0xFFB00020, false);
            return;
        }
        String text = I18n.format("gregtech.multiblock.preview.fullscreen.loading");
        fontRenderer.drawString(text, sceneX + (sceneWidth - fontRenderer.getStringWidth(text)) / 2,
                sceneY + sceneHeight / 2 - 12, TEXT_SECONDARY, false);
        int barWidth = 160;
        int barX = sceneX + (sceneWidth - barWidth) / 2;
        int barY = sceneY + sceneHeight / 2 + 4;
        drawRect(barX, barY, barX + barWidth, barY + 4, WINDOW_EDGE);
        drawRect(barX + 1, barY + 1, barX + barWidth - 1, barY + 3, 0xFFE4E4E4);
        int filled = (int) (Math.max(0.0F, Math.min(1.0F, wrapper.getPreviewProgress())) * (barWidth - 2));
        drawRect(barX + 1, barY + 1, barX + 1 + filled, barY + 3, ACCENT);
    }

    private void syncFullscreenRenderer() {
        StructurePreviewRenderer renderer = wrapper.getCurrentRenderer();
        if (renderer == null || renderer == syncedRenderer) {
            return;
        }
        syncedRenderer = renderer;
        wrapper.setPreviewFrameApplied(true);
        wrapper.applyDefaultCameraFraming();
        fitZoom = Math.max(2.5F, wrapper.getZoom());
        wrapper.applyCamera();
        wrapper.markRendererDirty();
    }

    private boolean isSceneHovered(int mouseX, int mouseY) {
        return inside(mouseX, mouseY, sceneX, sceneY, sceneWidth, sceneHeight) &&
                !inside(mouseX, mouseY, mapX, mapY, mapSize, mapSize) &&
                !(builderMode && inside(mouseX, mouseY, hatchX, hatchY, hatchWidth, hatchHeight)) &&
                !(configVisible && inside(mouseX, mouseY, cfgX, cfgY, cfgWidth, cfgHeight)) &&
                !inside(mouseX, mouseY, candX, candY, candWidth, CARD_HEADER + 24);
    }

    // ---------------------------------------------------------------------------------------------
    // Title row
    // ---------------------------------------------------------------------------------------------

    private void drawTitleRow(int mouseX, int mouseY) {
        String title = I18n.format("gregtech.multiblock.preview.fullscreen.title", wrapper.getMultiblockName());
        fontRenderer.drawString(title, frameX + PADDING, frameY + PADDING + 3, TEXT_PRIMARY, false);

        int y = frameY + PADDING;
        boolean closeHovered = inside(mouseX, mouseY, closeX, y, BUTTON_SIZE, BUTTON_SIZE);
        drawButton(closeX, y, BUTTON_SIZE, closeHovered, false);
        drawCross(closeX, y, closeHovered ? SELECTION : TEXT_SECONDARY);
        if (closeHovered) {
            hoveredTooltip = Collections.singletonList(
                    TextFormatting.GRAY + I18n.format("gregtech.multiblock.preview.fullscreen.esc"));
        }
        hit(closeX, y, BUTTON_SIZE, BUTTON_SIZE, this::collapse);

        boolean settingsHovered = inside(mouseX, mouseY, settingsX, y, BUTTON_SIZE, BUTTON_SIZE);
        drawButton(settingsX, y, BUTTON_SIZE, settingsHovered, configVisible);
        drawGear(settingsX, y, settingsHovered ? TEXT_PRIMARY : TEXT_SECONDARY);
        hit(settingsX, y, BUTTON_SIZE, BUTTON_SIZE, () -> configVisible = !configVisible);

        boolean minusHovered = inside(mouseX, mouseY, zoomMinusX, y, BUTTON_SIZE, BUTTON_SIZE);
        boolean plusHovered = inside(mouseX, mouseY, zoomPlusX, y, BUTTON_SIZE, BUTTON_SIZE);
        drawButton(zoomMinusX, y, BUTTON_SIZE, minusHovered, false);
        drawButton(zoomPlusX, y, BUTTON_SIZE, plusHovered, false);
        fontRenderer.drawString("-", zoomMinusX + (BUTTON_SIZE - 5) / 2, y + 4, TEXT_SECONDARY, false);
        fontRenderer.drawString("+", zoomPlusX + (BUTTON_SIZE - 5) / 2, y + 4, TEXT_SECONDARY, false);
        String zoomText = zoomPercent() + "%";
        drawRect(zoomLabelX, y, zoomLabelX + ZOOM_LABEL_WIDTH, y + BUTTON_SIZE, 0xFFBFBFBF);
        drawBorder(zoomLabelX, y, ZOOM_LABEL_WIDTH, BUTTON_SIZE, WINDOW_EDGE);
        fontRenderer.drawString(zoomText,
                zoomLabelX + (ZOOM_LABEL_WIDTH - fontRenderer.getStringWidth(zoomText)) / 2,
                y + (BUTTON_SIZE - fontRenderer.FONT_HEIGHT) / 2 + 1, TEXT_SECONDARY, false);
        hit(zoomMinusX, y, BUTTON_SIZE, BUTTON_SIZE, () -> {
            wrapper.setZoom(Math.max(3.0F, wrapper.getZoom() * 1.15F));
            wrapper.applyCamera();
        });
        hit(zoomPlusX, y, BUTTON_SIZE, BUTTON_SIZE, () -> {
            wrapper.setZoom(Math.min(999.0F, wrapper.getZoom() / 1.15F));
            wrapper.applyCamera();
        });
    }

    private int zoomPercent() {
        float zoom = Math.max(0.01F, wrapper.getZoom());
        return Math.max(5, Math.min(400, Math.round(fitZoom / zoom * 100.0F)));
    }

    // ---------------------------------------------------------------------------------------------
    // Cards
    // ---------------------------------------------------------------------------------------------

    private void drawCard(int x, int y, int w, int h, String title) {
        drawRect(x, y, x + w, y + h, CARD_FILL);
        drawBorder(x, y, w, h, WINDOW_EDGE);
        drawRect(x + 1, y + 1, x + w - 1, y + CARD_HEADER, HEADER_FILL);
        drawRect(x + 1, y + CARD_HEADER, x + w - 1, y + CARD_HEADER + 1, EDGE_SOFT);
        fontRenderer.drawString(title, x + PADDING, y + 3, TEXT_PRIMARY, false);
    }

    private void drawMinimapCard() {
        if (builderMode) {
            return;
        }
        refreshMinimap();
        drawRect(mapX, mapY, mapX + mapSize, mapY + mapSize, CARD_FILL);
        drawBorder(mapX, mapY, mapSize, mapSize, WINDOW_EDGE);
        if (minimapTexture == null) {
            drawRect(mapX + 1, mapY + 1, mapX + mapSize - 1, mapY + mapSize - 1, 0xFF9E9E9E);
            return;
        }
        int inner = mapSize - 2 * MINIMAP_BORDER;
        minimapScale = Math.min(inner / (float) minimapTexWidth, inner / (float) minimapTexHeight);
        int drawWidth = Math.max(1, Math.round(minimapTexWidth * minimapScale));
        int drawHeight = Math.max(1, Math.round(minimapTexHeight * minimapScale));
        minimapOriginX = mapX + MINIMAP_BORDER + (inner - drawWidth) / 2;
        minimapOriginY = mapY + MINIMAP_BORDER + (inner - drawHeight) / 2;

        // Scene/item rendering can leave the active texture on the lightmap unit. Binding the minimap
        // there makes the textured quad sample the default unit's empty texture, which renders black.
        // Restore the default unit and explicitly enable texture/blend for this quad.
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        mc.getTextureManager().bindTexture(MINIMAP_TEXTURE);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawScaledCustomSizeModalRect(minimapOriginX, minimapOriginY, 0.0F, 0.0F,
                minimapTexWidth, minimapTexHeight, drawWidth, drawHeight, minimapTexWidth, minimapTexHeight);
        GlStateManager.disableBlend();

        Vector3f eye = wrapper.getPreviewCameraEye();
        if (eye != null) {
            int markerX = minimapOriginX + Math.round((eye.x - minimapMinX) * minimapScale);
            int markerY = minimapOriginY + Math.round((eye.z - minimapMinZ) * minimapScale);
            markerX = MathHelper.clamp(markerX, minimapOriginX, minimapOriginX + drawWidth - 1);
            markerY = MathHelper.clamp(markerY, minimapOriginY, minimapOriginY + drawHeight - 1);
            drawRect(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFF000000);
            drawRect(markerX - 1, markerY - 1, markerX + 2, markerY + 2, 0xFFFF3030);
        }
        drawBorder(mapX, mapY, mapSize, mapSize, WINDOW_EDGE);
    }

    /**
     * Maps a minimap click to a structure position and moves the orbit target there, which is how GTO's
     * minimap lets the player pan around a structure larger than the viewport.
     */
    private void focusMinimap(int mouseX, int mouseY) {
        if (minimapScale <= 0.0F) {
            return;
        }
        float localX = (mouseX - minimapOriginX) / minimapScale;
        float localZ = (mouseY - minimapOriginY) / minimapScale;
        localX = Math.max(0.0F, Math.min(minimapTexWidth - 1, localX));
        localZ = Math.max(0.0F, Math.min(minimapTexHeight - 1, localZ));
        float worldX = minimapMinX + localX;
        float worldZ = minimapMinZ + localZ;
        wrapper.panPreviewTo(worldX, worldZ);
    }

    private void drawCandidatesCard() {
        List<PreviewCandidate> candidates = wrapper.getCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        int height = CARD_HEADER + 24;
        drawCard(candX, candY, candWidth, height,
                I18n.format("gregtech.multiblock.preview.fullscreen.candidates"));
        if (candidates.size() == 1) {
            String text = I18n.format("gregtech.multiblock.preview.fullscreen.single_candidate");
            fontRenderer.drawString(fontRenderer.trimStringToWidth(text, candWidth - PADDING * 2 - 22),
                    candX + PADDING, candY + CARD_HEADER + 8, TEXT_SECONDARY, false);
            drawCandidateSlot(candX + candWidth - PADDING - 18, candY + CARD_HEADER + 3, candidates.get(0));
            return;
        }
        int slotX = candX + PADDING;
        int slotY = candY + CARD_HEADER + 3;
        for (int i = 0; i < Math.min(7, candidates.size()); i++) {
            drawCandidateSlot(slotX + i * 20, slotY, candidates.get(i));
        }
    }

    private void drawCandidateSlot(int x, int y, @NotNull PreviewCandidate candidate) {
        drawRect(x, y, x + 18, y + 18, SLOT_FILL);
        drawBorder(x, y, 18, 18, WINDOW_EDGE);
        List<ItemStack> items = candidate.getItemCandidates();
        if (!items.isEmpty()) {
            drawItemStack(items.get(0), x + 1, y + 1);
        }
    }

    /**
     * Configuration card: a vertically scrolled stack of flow nodes. The root node carries the same rows GTO
     * generates for the structure root (one label + one stepper per size control, plus the display-layer
     * stepper); every following node is one tooling piece and clicking it selects the cumulative prefix.
     */
    private int builderHatchCardHeight() {
        if (!builderMode) {
            return 0;
        }
        int rows = Math.max(1, wrapper.getAbilityCountControls().size());
        return CARD_HEADER + 3 + rows * (9 + NODE_ROW + 2) + PADDING;
    }

    private void drawBuilderHatchCard(int mouseX, int mouseY) {
        if (!builderMode) {
            return;
        }
        drawRect(hatchX, hatchY, hatchX + hatchWidth, hatchY + hatchHeight, CARD_FILL);
        drawBorder(hatchX, hatchY, hatchWidth, hatchHeight, WINDOW_EDGE);
        drawRect(hatchX + 1, hatchY + 1, hatchX + hatchWidth - 1, hatchY + CARD_HEADER, HEADER_FILL);
        fontRenderer.drawString(I18n.format("gregtech.multiblock.preview.fullscreen.ability_counts"),
                hatchX + PADDING, hatchY + 3, TEXT_PRIMARY, false);

        List<MultiblockInfoRecipeWrapper.AbilityCountControl> controls = wrapper.getAbilityCountControls();
        int rowY = hatchY + CARD_HEADER + 3;
        if (controls.isEmpty()) {
            fontRenderer.drawString(I18n.format("gregtech.multiblock.build_wizard.no_ability_counts"),
                    hatchX + PADDING, rowY + 4, TEXT_DIM, false);
            return;
        }
        for (MultiblockInfoRecipeWrapper.AbilityCountControl control : controls) {
            String label = fontRenderer.trimStringToWidth(I18n.format(control.getTranslationKey()),
                    CARD_CONTENT_WIDTH - 8);
            fontRenderer.drawString(label, hatchX + 4, rowY, TEXT_SECONDARY, false);
            rowY += 9;
            drawStepperRow(hatchX, rowY, String.valueOf(control.getValue()), mouseX, mouseY,
                    () -> wrapper.stepAbilityCount(control.getKey(), -1),
                    () -> wrapper.stepAbilityCount(control.getKey(), 1));
            rowY += NODE_ROW + 2;
        }
    }

    private void drawConfigCard(int mouseX, int mouseY) {
        if (!configVisible) {
            return;
        }
        drawCard(cfgX, cfgY, cfgWidth, cfgHeight,
                I18n.format("gregtech.multiblock.preview.config"));
        int cardCloseX = cfgX + cfgWidth - PADDING - BUTTON_SIZE;
        int cardCloseY = cfgY + 1;
        boolean cardCloseHovered = inside(mouseX, mouseY, cardCloseX, cardCloseY, BUTTON_SIZE, BUTTON_SIZE);
        drawButton(cardCloseX, cardCloseY, BUTTON_SIZE, cardCloseHovered, false);
        drawCross(cardCloseX, cardCloseY, cardCloseHovered ? SELECTION : TEXT_SECONDARY);
        hit(cardCloseX, cardCloseY, BUTTON_SIZE, BUTTON_SIZE, () -> configVisible = false);

        int contentX = cfgX + PADDING;
        int contentTop = cfgY + CARD_HEADER + 3;
        int contentBottom = cfgY + cfgHeight - PADDING
                - (wrapper.hasWorldController() ? CARD_ACTION_HEIGHT : 0);
        int contentHeight = contentBottom - contentTop;

        if (configNodes.isEmpty() && wrapper.isPreviewReady()) {
            rebuildConfigTree();
        }

        int total = configContentHeight();
        configScrollMax = Math.max(0, total - contentHeight);
        configScroll = Math.max(0, Math.min(configScroll, configScrollMax));

        int clipBottom = Math.min(contentBottom, cfgY + cfgHeight - 1);
        pushClip(contentX - 1, contentTop, CARD_CONTENT_WIDTH + 2, clipBottom - contentTop);

        int y = contentTop - configScroll;
        int selected = wrapper.getPreviewChannelValue(PIECE_CHANNEL);
        List<StructureChannel> channels = configChannels();
        for (int n = 0; n < configNodes.size(); n++) {
            PieceNode node = configNodes.get(n);
            int nodeHeight = n == 0 ? rootNodeHeight(channels)
                    : NODE_HEADER + 4;
            if (node.isPiece) {
                boolean active = selected == node.pieceIndex;
                drawPieceNode(contentX, y, CARD_CONTENT_WIDTH, nodeHeight, node.label, active, mouseX, mouseY);
                if (inside(mouseX, mouseY, contentX, y, CARD_CONTENT_WIDTH, nodeHeight)) {
                    int pieceIndex = node.pieceIndex;
                    hit(contentX, y, CARD_CONTENT_WIDTH, nodeHeight, () -> {
                        int current = wrapper.getPreviewChannelValue(PIECE_CHANNEL);
                        wrapper.setPreviewChannel(PIECE_CHANNEL, current == pieceIndex ? 0 : pieceIndex);
                    });
                }
            } else {
                drawRootNode(contentX, y, nodeHeight, channels, mouseX, mouseY);
            }
            y += nodeHeight;
            if (n < configNodes.size() - 1) {
                drawArrow(contentX + CARD_CONTENT_WIDTH / 2, y);
                y += ARROW_HEIGHT;
            }
        }
        popClip();

        if (configScrollMax > 0) {
            int trackX = cfgX + cfgWidth - 3;
            int trackHeight = contentHeight;
            drawRect(trackX, contentTop, trackX + 2, contentTop + trackHeight, 0xFFBDBDBD);
            int thumbHeight = Math.max(12, trackHeight * contentHeight / total);
            int thumbY = contentTop + (trackHeight - thumbHeight) * configScroll / configScrollMax;
            drawRect(trackX, thumbY, trackX + 2, thumbY + thumbHeight, EDGE_SOFT);
        }
        drawConfigActions(mouseX, mouseY);
    }

    private void drawConfigActions(int mouseX, int mouseY) {
        // The JEI preview is read-only (GTO: minimap + structure configuration only). Projection and the
        // build dialog belong to the terminal/world flow, where a real controller exists.
        if (!wrapper.hasWorldController()) {
            return;
        }
        int y = cfgY + cfgHeight - PADDING - BUTTON_SIZE;
        int x = cfgX + PADDING;
        int width = (CARD_CONTENT_WIDTH - GAP) / 2;

        boolean projectHovered = inside(mouseX, mouseY, x, y, width, BUTTON_SIZE);
        drawButton(x, y, width, projectHovered, false);
        String project = I18n.format("gregtech.multiblock.preview.actions.project");
        fontRenderer.drawString(project, x + (width - fontRenderer.getStringWidth(project)) / 2, y + 3,
                wrapper.hasWorldController() ? TEXT_PRIMARY : TEXT_DIM, false);
        if (wrapper.hasWorldController() && mc.world != null) {
            hit(x, y, width, BUTTON_SIZE, this::projectToWorld);
        }

        int buildX = x + width + GAP;
        boolean buildHovered = inside(mouseX, mouseY, buildX, y, width, BUTTON_SIZE);
        drawButton(buildX, y, width, buildHovered, false);
        String build = I18n.format("gregtech.multiblock.build_wizard.open");
        fontRenderer.drawString(build, buildX + (width - fontRenderer.getStringWidth(build)) / 2, y + 3,
                wrapper.hasWorldController() ? TEXT_PRIMARY : TEXT_DIM, false);
        if (wrapper.hasWorldController()) {
            hit(buildX, y, width, BUTTON_SIZE,
                    () -> gregtech.client.gui.MultiblockBuildWizardScreen.open(wrapper, this));
        }
    }

    private void projectToWorld() {
        if (!wrapper.hasWorldController() || mc.world == null) {
            return;
        }
        GhostBlockRenderer.setChannelValues(wrapper.copyPreviewChannels());
        GhostBlockRenderer.renderGhostPreview(wrapper.getPreviewController(), 15000L);
        mc.displayGuiScreen(null);
    }

    private List<StructureChannel> configChannels() {
        List<StructureChannel> channels = new ArrayList<>();
        for (StructureChannel channel : wrapper.getSupportedChannels()) {
            if (PIECE_CHANNEL.equals(channel.getName())) {
                continue;
            }
            channels.add(channel);
        }
        return channels;
    }

    private int rootNodeHeight(List<StructureChannel> channels) {
        int channelRows = (channels.size() + 1) * (9 + NODE_ROW + 2);
        return NODE_HEADER + channelRows + 4;
    }

    private void drawRootNode(int x, int y, int height, List<StructureChannel> channels, int mouseX, int mouseY) {
        drawRect(x, y, x + CARD_CONTENT_WIDTH, y + height, NODE_FILL);
        drawBorder(x, y, CARD_CONTENT_WIDTH, height, NODE_EDGE);
        fontRenderer.drawString(I18n.format("gregtech.multiblock.preview.config_root"), x + 4, y + 2,
                TEXT_PRIMARY, false);

        int rowY = y + NODE_HEADER + 2;
        for (int i = 0; i < channels.size(); i++) {
            StructureChannel channel = channels.get(i);
            fontRenderer.drawString(fontRenderer.trimStringToWidth(channel.getName(), CARD_CONTENT_WIDTH - 8),
                    x + 4, rowY, TEXT_SECONDARY, false);
            rowY += 9;
            int value = wrapper.getChannelValue(channel);
            String text = value <= 0 ? I18n.format("gregtech.multiblock.preview.fullscreen.auto")
                    : String.valueOf(value);
            int channelIndex = wrapper.getSupportedChannels().indexOf(channel);
            drawStepperRow(x, rowY, text, mouseX, mouseY, channelIndex);
            rowY += NODE_ROW + 2;
        }

        fontRenderer.drawString(I18n.format("gregtech.multiblock.preview.fullscreen.display_layer"), x + 4,
                rowY, TEXT_SECONDARY, false);
        rowY += 9;
        int layer = wrapper.getLayerIndex();
        String layerText = layer == -1 ? I18n.format("gregtech.multiblock.preview.fullscreen.layer_all")
                : String.valueOf(layer);
        drawStepperRow(x, rowY, layerText, mouseX, mouseY, -1);
        rowY += NODE_ROW + 2;
    }

    private void drawStepperRow(int x, int y, String text, int mouseX, int mouseY, int channelIndex) {
        if (channelIndex < 0) {
            drawStepperRow(x, y, text, mouseX, mouseY, () -> stepLayer(-1), () -> stepLayer(1));
        } else {
            drawStepperRow(x, y, text, mouseX, mouseY,
                    () -> wrapper.stepPreviewChannel(channelIndex, -1),
                    () -> wrapper.stepPreviewChannel(channelIndex, 1));
        }
    }

    private void drawStepperRow(int x, int y, String text, int mouseX, int mouseY,
                                Runnable minusAction, Runnable plusAction) {
        int minusX = x + 4;
        int plusX = x + CARD_CONTENT_WIDTH - 4 - BUTTON_SIZE;
        int boxX = minusX + BUTTON_SIZE + 2;
        int boxWidth = plusX - 2 - boxX;
        boolean minusHovered = inside(mouseX, mouseY, minusX, y, BUTTON_SIZE, CONTROL_HEIGHT);
        boolean plusHovered = inside(mouseX, mouseY, plusX, y, BUTTON_SIZE, CONTROL_HEIGHT);
        drawButton(minusX, y, BUTTON_SIZE, minusHovered, false);
        drawButton(plusX, y, BUTTON_SIZE, plusHovered, false);
        fontRenderer.drawString("-", minusX + 5, y + 4, TEXT_SECONDARY, false);
        fontRenderer.drawString("+", plusX + 5, y + 4, TEXT_SECONDARY, false);
        drawRect(boxX, y, boxX + boxWidth, y + CONTROL_HEIGHT, 0xFFEDEDED);
        drawBorder(boxX, y, boxWidth, CONTROL_HEIGHT, EDGE_SOFT);
        fontRenderer.drawString(text, boxX + (boxWidth - fontRenderer.getStringWidth(text)) / 2, y + 4,
                TEXT_PRIMARY, false);
        hit(minusX, y, BUTTON_SIZE, CONTROL_HEIGHT, minusAction);
        hit(plusX, y, BUTTON_SIZE, CONTROL_HEIGHT, plusAction);
    }

    private void stepLayer(int delta) {
        int max = wrapper.getMaxLayerIndex();
        int current = wrapper.getLayerIndex();
        if (current == -1 && delta < 0) {
            wrapper.setLayer(Math.max(0, max - 1));
            return;
        }
        wrapper.setLayer(Math.max(-1, Math.min(max, current + delta)));
    }

    private void drawPieceNode(int x, int y, int width, int height, String label, boolean active, int mouseX,
                               int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, width, height);
        drawRect(x, y, x + width, y + height, active ? NODE_ACTIVE_FILL : (hovered ? 0xFFE2E2E2 : NODE_FILL));
        drawBorder(x, y, width, height, active ? NODE_ACTIVE_EDGE : NODE_EDGE);
        if (active) {
            drawRect(x + 1, y + 1, x + 3, y + height - 1, NODE_ACTIVE_EDGE);
        }
        fontRenderer.drawString(fontRenderer.trimStringToWidth(label, width - 12), x + 5, y + 2, TEXT_PRIMARY,
                false);
    }

    private void drawArrow(int centerX, int y) {
        drawRect(centerX, y, centerX + 1, y + ARROW_HEIGHT - 3, EDGE_SOFT);
        drawRect(centerX - 2, y + ARROW_HEIGHT - 4, centerX + 3, y + ARROW_HEIGHT - 3, EDGE_SOFT);
    }

    private int configContentHeight() {
        if (configNodes.isEmpty()) {
            return NODE_HEADER + 4;
        }
        List<StructureChannel> channels = configChannels();
        int total = 0;
        for (int n = 0; n < configNodes.size(); n++) {
            int nodeHeight = n == 0 ? rootNodeHeight(channels)
                    : NODE_HEADER + 4;
            total += nodeHeight;
            if (n < configNodes.size() - 1) {
                total += ARROW_HEIGHT;
            }
        }
        return total;
    }

    private void rebuildConfigTree() {
        configNodes.clear();
        configNodes.add(new PieceNode(0, I18n.format("gregtech.multiblock.preview.config_root"), false));
        MultiPiecePattern pattern = wrapper.getMultiPiecePattern();
        if (pattern != null) {
            List<StructurePiece> pieces = pattern.getToolingPieceList();
            for (int i = 0; i < pieces.size(); i++) {
                StructurePiece piece = pieces.get(i);
                configNodes.add(new PieceNode(i + 1, piece.getName() + (piece.isOptional() ? " *" : ""), true));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Minimap
    // ---------------------------------------------------------------------------------------------

    private void refreshMinimap() {
        StructurePreviewRenderer renderer = wrapper.getCurrentRenderer();
        if (renderer == null || !(renderer.world instanceof TrackedDummyWorld)) {
            minimapTexture = null;
            minimapSource = null;
            return;
        }
        if (renderer == minimapSource && minimapTexture != null) {
            return;
        }
        TrackedDummyWorld world = (TrackedDummyWorld) renderer.world;
        Vector3f size = world.getSize();
        Vector3f minPos = world.getMinPos();
        int sx = Math.max(1, Math.round(size.x));
        int sz = Math.max(1, Math.round(size.z));
        int sy = Math.max(1, Math.round(size.y));
        int minX = MathHelper.floor(minPos.x);
        int minY = MathHelper.floor(minPos.y);
        int minZ = MathHelper.floor(minPos.z);
        minimapMinX = minX;
        minimapMinZ = minZ;
        BufferedImage image = new BufferedImage(sx, sz, BufferedImage.TYPE_INT_ARGB);
        boolean any = false;
        for (int x = 0; x < sx; x++) {
            for (int z = 0; z < sz; z++) {
                // An opaque background is important: a transparent DynamicTexture samples as black on
                // some drivers when blend is disabled by a preceding Gui.drawRect.
                int color = 0xFFB0B0B0;
                for (int y = sy - 1; y >= 0; y--) {
                    BlockPos pos = new BlockPos(minX + x, minY + y, minZ + z);
                    IBlockState state = world.getBlockState(pos);
                    if (state.getBlock() == Blocks.AIR) {
                        continue;
                    }
                    MapColor mapColor = state.getMapColor(world, pos);
                    int rgb = mapColor == null ? 0x9A9A9A : mapColor.colorValue;
                    if ((rgb & 0xFFFFFF) == 0) {
                        // Some machine blocks report MapColor.AIR (0x000000); using it would paint
                        // the entire minimap black.
                        rgb = 0x9A9A9A;
                    }
                    float shade = 0.72F + 0.28F * (y / (float) sy);
                    color = 0xFF000000 | shadeRgb(rgb, shade);
                    any = true;
                    break;
                }
                image.setRGB(x, z, color);
            }
        }
        if (!any) {
            return;
        }
        if (minimapTexture == null || minimapTexWidth != sx || minimapTexHeight != sz) {
            minimapTexture = new DynamicTexture(image);
            minimapTexWidth = sx;
            minimapTexHeight = sz;
            mc.getTextureManager().loadTexture(MINIMAP_TEXTURE, minimapTexture);
        } else {
            int[] pixels = image.getRGB(0, 0, sx, sz, null, 0, sx);
            System.arraycopy(pixels, 0, minimapTexture.getTextureData(), 0, pixels.length);
            minimapTexture.updateDynamicTexture();
        }
        minimapSource = renderer;
    }

    private static int shadeRgb(int rgb, float shade) {
        int r = (int) (((rgb >> 16) & 0xFF) * shade);
        int g = (int) (((rgb >> 8) & 0xFF) * shade);
        int b = (int) ((rgb & 0xFF) * shade);
        return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    // ---------------------------------------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------------------------------------

    private void drawSummaryLine() {
        StructurePreviewRenderer renderer = wrapper.getCurrentRenderer();
        if (!(renderer != null && renderer.world instanceof TrackedDummyWorld)) {
            return;
        }
        Vector3f size = ((TrackedDummyWorld) renderer.world).getSize();
        String text = I18n.format("gregtech.multiblock.preview.fullscreen.summary",
                (int) size.x, (int) size.y, (int) size.z, wrapper.getPreviewParts().size());
        fontRenderer.drawString(text, sceneX + PADDING, sceneY + sceneHeight - 11, TEXT_SECONDARY, false);
    }

    private void drawHintBar() {
        String hint = I18n.format("gregtech.multiblock.preview.fullscreen.hint");
        // Keep the hint clear of the right-hand config card: in the world flow its footer carries the
        // project/build buttons, and the centered hint used to run underneath them.
        int hintLeft = sceneX + 2;
        int hintRight = Math.min(sceneX + sceneWidth - 2, cfgX - GAP);
        if (hintRight - hintLeft < 80) {
            hintRight = sceneX + sceneWidth - 2;
        }
        int available = Math.max(40, hintRight - hintLeft);
        int textWidth = fontRenderer.getStringWidth(hint);
        if (textWidth > available) {
            hint = fontRenderer.trimStringToWidth(hint, available);
            textWidth = fontRenderer.getStringWidth(hint);
        }
        int x = hintLeft + (available - textWidth) / 2;
        int y = sceneY + sceneHeight - (wrapper.hasWorldController() ? 48 : 24);
        drawRect(x - 5, y - 2, x + textWidth + 5, y + 9, 0x66000000);
        fontRenderer.drawString(hint, x, y, 0xFFF2F2F2, false);
    }

    // ---------------------------------------------------------------------------------------------
    // Tooltips / items
    // ---------------------------------------------------------------------------------------------

    private void drawTooltips(int mouseX, int mouseY) {
        if (hoveredTooltip != null && !hoveredTooltip.isEmpty()) {
            drawHoveringText(hoveredTooltip, mouseX, mouseY);
            return;
        }
        List<String> blockTooltip = wrapper.getBlockTooltip();
        if (blockTooltip != null && !blockTooltip.isEmpty()) {
            drawHoveringText(blockTooltip, mouseX, mouseY);
            return;
        }
        List<String> tips = wrapper.getSelectionTips();
        if (tips != null && !tips.isEmpty()) {
            drawHoveringText(tips, mouseX, mouseY);
        }
    }

    private void drawItemStack(@NotNull ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y - 1, null);
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        applyLayout(width, height);
        if (mouseButton == 0) {
            for (Hit hit : hits) {
                if (inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                    hit.action.run();
                    return;
                }
            }
            if (inside(mouseX, mouseY, mapX, mapY, mapSize, mapSize) && minimapTexture != null) {
                minimapDragging = true;
                focusMinimap(mouseX, mouseY);
                return;
            }
            if (isSceneHovered(mouseX, mouseY)) {
                draggingLeft = true;
                wrapper.resetCameraDrag();
            }
            return;
        }
        if (mouseButton == 1 && isSceneHovered(mouseX, mouseY)) {
            rightPressX = mouseX;
            rightPressY = mouseY;
            dragRightCandidate = true;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && minimapDragging) {
            focusMinimap(mouseX, mouseY);
            return;
        }
        if (clickedMouseButton == 0 && draggingLeft) {
            wrapper.updateCameraDrag(CameraDragMode.ROTATE, mouseX, mouseY, sceneHeight);
            return;
        }
        if (clickedMouseButton == 1 && dragRightCandidate) {
            if (Math.abs(mouseX - rightPressX) > 2 || Math.abs(mouseY - rightPressY) > 2) {
                wrapper.updateCameraDrag(CameraDragMode.PAN, mouseX, mouseY, sceneHeight);
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) {
            draggingLeft = false;
            minimapDragging = false;
            wrapper.cancelChannelDrag();
        }
        if (state == 1) {
            if (dragRightCandidate && Math.abs(mouseX - rightPressX) <= 2 &&
                    Math.abs(mouseY - rightPressY) <= 2) {
                RayTraceResult hit = wrapper.pickPreview(mouseX, mouseY, sceneX, sceneY, sceneWidth, sceneHeight);
                if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK) {
                    wrapper.selectBlock(hit.getBlockPos());
                } else {
                    wrapper.clearSelection();
                }
            }
            dragRightCandidate = false;
        }
        wrapper.resetCameraDrag();
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (configVisible && inside(mouseX, mouseY, cfgX, cfgY, cfgWidth, cfgHeight)) {
            configScroll = Math.max(0, Math.min(configScrollMax, configScroll - wheel / 120 * 18));
            return;
        }
        float zoom = wrapper.getZoom();
        zoom = Math.max(3.0F, Math.min(999.0F, zoom + (wheel > 0 ? -0.5F : 0.5F)));
        wrapper.setZoom(zoom);
        wrapper.applyCamera();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            collapse();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    // ---------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------

    private void pushClip(int x, int y, int w, int h) {
        net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(mc);
        int scale = resolution.getScaleFactor();
        int fbHeight = mc.getFramebuffer().framebufferHeight;
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, fbHeight - (y + h) * scale, w * scale, h * scale);
    }

    private void popClip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
    }

    private void drawButton(int x, int y, int w, boolean hovered, boolean active) {
        drawRect(x, y, x + w, y + BUTTON_SIZE, hovered ? 0xFFD9D9D9 : 0xFFBFBFBF);
        drawBorder(x, y, w, BUTTON_SIZE, WINDOW_EDGE);
        if (active) {
            drawRect(x + 1, y + 1, x + w - 1, y + 2, SELECTION);
        }
    }

    private void drawCross(int x, int y, int color) {
        for (int i = 4; i < 10; i++) {
            drawRect(x + i, y + i, x + i + 1, y + i + 1, color);
            drawRect(x + i, y + 14 - i, x + i + 1, y + 15 - i, color);
        }
    }

    private void drawGear(int x, int y, int color) {
        drawRect(x + 5, y + 3, x + 9, y + 4, color);
        drawRect(x + 5, y + 10, x + 9, y + 11, color);
        drawRect(x + 3, y + 5, x + 4, y + 9, color);
        drawRect(x + 10, y + 5, x + 11, y + 9, color);
        drawRect(x + 6, y + 6, x + 8, y + 9, color);
    }

    private void drawBorder(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y, x + 1, y + h, color);
        drawRect(x + w - 1, y, x + w, y + h, color);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static final class PieceNode {

        private final int pieceIndex;
        private final String label;
        private final boolean isPiece;

        private PieceNode(int pieceIndex, String label, boolean isPiece) {
            this.pieceIndex = pieceIndex;
            this.label = label;
            this.isPiece = isPiece;
        }
    }

    private static final class Hit {

        private final int x, y, w, h;
        private final Runnable action;

        private Hit(int x, int y, int w, int h, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }
    }
}
