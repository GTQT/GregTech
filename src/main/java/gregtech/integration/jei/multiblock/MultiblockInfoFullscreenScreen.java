package gregtech.integration.jei.multiblock;

import gregtech.api.pattern.casing.StructureChannel;
import gregtech.client.renderer.scene.WorldSceneRenderer;
import gregtech.client.utils.TrackedDummyWorld;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper.PreviewCandidate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.gui.TooltipRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.vecmath.Vector3f;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fullscreen counterpart of the compact JEI multiblock preview.
 *
 * <p>
 * It keeps the same information architecture as the recipe page (structure name on top, tier/channel sliders at
 * the bottom, required components on the left, alternatives and misc controls on the right) but lays it out over
 * the whole screen with a blueprint/industrial-scifi skin. Rendering and camera state stay owned by
 * {@link MultiblockInfoRecipeWrapper}; this screen only draws the chrome and forwards input.
 */
@SideOnly(Side.CLIENT)
public class MultiblockInfoFullscreenScreen extends GuiScreen {

    // ---------------------------------------------------------------------------------------------
    // Palette: dark blueprint panels with electric cyan accents.
    // ---------------------------------------------------------------------------------------------
    private static final int BACKDROP = 0xE8070B12;
    private static final int VIGNETTE = 0x40000000;
    private static final int PANEL_FILL = 0xC00D141F;
    private static final int PANEL_FILL_SOFT = 0x8A0A1017;
    private static final int PANEL_EDGE = 0x6638D6FF;
    private static final int PANEL_EDGE_SOFT = 0x2E38D6FF;
    private static final int HEADER_FILL = 0x9E132030;
    private static final int ACCENT = 0xFF00E5FF;
    private static final int ACCENT_DIM = 0x8C00B8D4;
    private static final int ACCENT_SOFT = 0x3D00E5FF;
    private static final int ACCENT_FAINT = 0x1A00E5FF;
    private static final int TEXT_PRIMARY = 0xFFE9FBFF;
    private static final int TEXT_SECONDARY = 0xFF93AFC0;
    private static final int TEXT_DIM = 0xFF5C7385;
    private static final int SLOT_FILL = 0xFF0F1720;
    private static final int SLOT_EDGE = 0x5938D6FF;
    private static final int SLOT_EDGE_HOVER = 0xCC7CF3FF;
    private static final int SELECTION = 0xFFFF5C6E;
    private static final int WARN = 0xFFFFB24D;
    private static final int OK = 0xFF4CE0A0;

    // Vertical layout of a panel card.
    private static final int HEADER_HEIGHT = 16;
    private static final int PANEL_PADDING = 8;
    private static final int TITLE_BAR_HEIGHT = 46;
    private static final float TITLE_SCALE = 1.35F;

    // Material panel: a fixed 4-column grid of 18px slots. Slots fill a row before wrapping, and the panel
    // width stays constant so the viewer between the side panels keeps its size.
    private static final int MATERIAL_COLUMNS = 4;
    private static final int MATERIAL_MIN_ROWS = 3;
    private static final int MATERIAL_SLOT_SIZE = 18;
    private static final int MATERIAL_GRID_PADDING = 6;
    private static final int MATERIAL_PANEL_WIDTH = MATERIAL_GRID_PADDING * 2 + MATERIAL_COLUMNS * MATERIAL_SLOT_SIZE;

    // Alternatives panel: single column of 6 slots plus a readout.
    private static final int CANDIDATE_ROWS = 6;
    private static final int CANDIDATE_SLOT_SIZE = 20;
    private static final int CANDIDATE_PANEL_WIDTH = 20 + CANDIDATE_SLOT_SIZE + 10;

    // Bottom tier/channel strip.
    private static final int SLIDER_ROW_HEIGHT = 22;
    private static final int SLIDER_LABEL_WIDTH = 96;
    private static final int SLIDER_VALUE_WIDTH = 108;
    private static final int MAX_SLIDER_ROWS = 3;

    private static final int HINT_Y_OFFSET = 14;
    private static final int BUTTON_SIZE = 20;
    private static final int LAYER_BUTTON_WIDTH = 74;

    private static final int MIN_VIEWPORT_WIDTH = 140;
    private static final int MIN_VIEWPORT_HEIGHT = 110;
    /** Gap between the preview window and the side panels. */
    private static final int VIEWPORT_GAP = 8;

    private static final long CLICK_DEBOUNCE_MS = 180L;

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

    /**
     * Screen that was open when this viewer was expanded, normally JEI's recipe page. Collapsing returns to
     * it instead of closing the whole recipe view. Only touched from the client thread.
     */
    @Nullable
    private static GuiScreen previousScreen;

    /** Renderer this viewer has already upgraded to fullscreen resolution. */
    @Nullable
    private WorldSceneRenderer syncedRenderer;

    // Computed layout for the current frame.
    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int leftPanelX;
    private int leftPanelY;
    private int leftPanelWidth;
    private int leftPanelHeight;
    private int rightPanelX;
    private int rightPanelY;
    private int rightPanelWidth;
    private int rightPanelHeight;
    private int bottomPanelX;
    private int bottomPanelY;
    private int bottomPanelWidth;
    private int bottomPanelHeight;
    private int sliderTrackX;
    private int sliderTrackWidth;
    /** Number of material slots the grid can show; the grid itself is a fixed 4 columns wide. */
    private int materialVisibleSlots;

    // Interaction state.
    private boolean draggingLeft;
    private boolean insideViewport;
    private int lastMouseX;
    private int lastMouseY;
    private long lastClickTime;
    private long lastClickButton = -1L;

    // Hover state, recorded while drawing and consumed by the tooltip pass.
    @Nullable
    private List<String> hoveredTooltip;
    private boolean hoversInfoIcon;
    private boolean hoversLayerButton;
    private long tick;

    public MultiblockInfoFullscreenScreen(MultiblockInfoRecipeWrapper wrapper) {
        this.wrapper = wrapper;
    }

    // ---------------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------------

    public void initFullscreenView() {
        applyLayout(width, height);
        fitCameraToViewport();
        wrapper.setRotation(20.0F, 52.0F);
        wrapper.setLayer(-1);
        wrapper.applyCamera();
        wrapper.markRendererDirty();
    }

    /**
     * Frames the structure to use as much of the viewport as possible. Because the buffer shares the
     * viewport's aspect ratio, the structure fills the whole window rather than sitting in a small square.
     * Re-applied when the renderer changes, so a channel change that rebuilds the preview keeps the framing.
     */
    private void fitCameraToViewport() {
        WorldSceneRenderer renderer = wrapper.getRenderer();
        if (!(renderer != null && renderer.world instanceof TrackedDummyWorld world)) {
            return;
        }
        Vector3f size = world.getSize();
        float largest = Math.max(Math.max(size.x, size.y), size.z);
        // Slightly more than the fitted radius keeps a small margin so the structure stays fully visible.
        wrapper.setZoom(Math.max(2.5F, 1.45F * Math.max(largest, 1.5F)));
    }
    /**
     * Opens this viewer. The screen that is currently open (JEI's recipe page) is remembered so collapsing
     * returns to it rather than closing the whole recipe view.
     */
    public void openFromCurrentScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen current = minecraft.currentScreen;
        if (current != null && current != this) {
            previousScreen = current;
        }
        minecraft.displayGuiScreen(this);
    }

    @Override
    public void initGui() {
        applyLayout(width, height);
        // JEI's other screens are not relevant while expanded; keep the viewer minimal.
        buttonList.clear();
    }

    /**
     * Collapses the viewer and puts the shared item slots back where the compact recipe page expects them.
     */
    public void close() {
        if (openScreen == this) {
            openScreen = null;
        }
        wrapper.restoreCompactLayout();
    }

    /**
     * Collapses the viewer back to the screen it was expanded from (normally JEI's multiblock recipe page).
     * Used by ESC and the close button; unlike {@link #close()} it does not leave the recipe view.
     */
    public void collapse() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen target = previousScreen;
        previousScreen = null;
        close();
        if (target != null && target != minecraft.currentScreen) {
            // Reopening the existing instance keeps JEI's search, page and selected recipe intact.
            minecraft.displayGuiScreen(target);
        } else {
            minecraft.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        close();
        super.onGuiClosed();
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    private void applyLayout(int width, int height) {
        int hintsHeight = fontRenderer == null ? HINT_Y_OFFSET : fontRenderer.FONT_HEIGHT + HINT_Y_OFFSET;
        // Always reserve room for the maximum number of channel rows so the layout is stable while the
        // preview loads and the channel list is not known yet.
        int bottomRows = wrapper.getSupportedChannels().isEmpty()
                ? MAX_SLIDER_ROWS
                : Math.min(MAX_SLIDER_ROWS, wrapper.getSupportedChannels().size());
        bottomPanelHeight = HEADER_HEIGHT + 2 + bottomRows * SLIDER_ROW_HEIGHT + PANEL_PADDING;
        bottomPanelX = 14;
        bottomPanelWidth = Math.max(220, width - 28);
        bottomPanelY = height - hintsHeight - bottomPanelHeight - 6;

        int panelTop = TITLE_BAR_HEIGHT + 6;
        int panelHeight = Math.max(MIN_VIEWPORT_HEIGHT, bottomPanelY - 6 - panelTop);

        // The material panel keeps a fixed slot grid so the viewer between the side panels never changes size.
        // Slots fill the row left-to-right and wrap, so the grid uses its full width before starting a new row.
        leftPanelX = 14;
        leftPanelY = panelTop;
        leftPanelWidth = MATERIAL_PANEL_WIDTH;
        leftPanelHeight = panelHeight;

        int materialRows = Math.max(MATERIAL_MIN_ROWS,
                (gridPartCount() + MATERIAL_COLUMNS - 1) / MATERIAL_COLUMNS);
        int maxRows = Math.max(MATERIAL_MIN_ROWS,
                (panelHeight - HEADER_HEIGHT - MATERIAL_GRID_PADDING * 2) / MATERIAL_SLOT_SIZE);
        materialVisibleSlots = MATERIAL_COLUMNS * Math.min(materialRows, maxRows);

        rightPanelWidth = CANDIDATE_PANEL_WIDTH;
        rightPanelX = width - 14 - rightPanelWidth;
        rightPanelY = panelTop;
        rightPanelHeight = panelHeight;

        // The viewport spans all the space between the side panels (small breathing gap on each side) so it
        // reads as one window and fills it at its own aspect ratio.
        viewportX = leftPanelX + leftPanelWidth + VIEWPORT_GAP;
        viewportY = panelTop;
        viewportWidth = Math.max(MIN_VIEWPORT_WIDTH, rightPanelX - VIEWPORT_GAP - viewportX);
        viewportHeight = panelHeight;

        sliderTrackX = bottomPanelX + PANEL_PADDING + SLIDER_LABEL_WIDTH;
        sliderTrackWidth = Math.max(40, bottomPanelWidth - PANEL_PADDING * 2 - SLIDER_LABEL_WIDTH
                - SLIDER_VALUE_WIDTH);
    }

    /**
     * @return the component count, or 1 while the preview is still loading so the grid keeps a minimum size
     */
    private int gridPartCount() {
        return Math.max(1, wrapper.isPreviewReady() ? wrapper.getPreviewParts().size() : 0);
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tick++;
        applyLayout(width, height);
        hoveredTooltip = null;
        hoversInfoIcon = false;
        hoversLayerButton = false;
        insideViewport = mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;

        drawBackdrop();
        drawTitleBar(mouseX, mouseY);

        // The compact recipe page is not drawn while this viewer is open, so the viewer has to drive the
        // resumable preview build itself; otherwise a rebuild triggered by a channel change never finishes.
        wrapper.advancePreviewLoadingStep();
        syncFullscreenRenderer();
        // Keeps the in-place candidate rotation ticking even while the cached preview FBO is clean.
        wrapper.advanceCandidateCycle();

        if (wrapper.isPreviewReady()) {
            drawViewport(mouseX, mouseY);
        } else {
            drawViewportPlaceholder();
        }

        drawPartsPanel(mouseX, mouseY);
        drawCandidatesPanel(mouseX, mouseY);
        drawChannelPanel(mouseX, mouseY);
        drawHints();

        // Hover state feeds the shared tooltip machinery of the wrapper (block tooltips, typed preview tips).
        wrapper.updateHoverState(mc, mouseX, mouseY, insideViewport && !isOverlayHovered());

        drawTooltips(mouseX, mouseY);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    private boolean isOverlayHovered() {
        return hoversInfoIcon || hoversLayerButton;
    }

    private void drawBackdrop() {
        drawRect(0, 0, width, height, BACKDROP);
        // Subtle blueprint grid so the empty space still reads as a technical surface.
        for (int x = 0; x < width; x += 32) {
            drawRect(x, 0, x + 1, height, ACCENT_FAINT);
        }
        for (int y = 0; y < height; y += 32) {
            drawRect(0, y, width, y + 1, ACCENT_FAINT);
        }
        // Vignette bands make the edges recede.
        drawRect(0, 0, width, 2, VIGNETTE);
        drawRect(0, height - 2, width, height, VIGNETTE);
    }

    private void drawTitleBar(int mouseX, int mouseY) {
        int barBottom = TITLE_BAR_HEIGHT;
        drawGradientRect(0, 0, width, barBottom, 0xF00B1522, 0x40070B12);
        drawRect(0, barBottom - 1, width, barBottom, ACCENT_SOFT);

        int closeX = rightPanelX - BUTTON_SIZE;
        int layerX = closeX - 8 - LAYER_BUTTON_WIDTH;
        int badgeX = layerX - 8 - BUTTON_SIZE;
        int titleLimit = Math.max(60, badgeX - 18 - 12);

        // Title, followed inline by the structure dimensions.
        String name = fontRenderer.trimStringToWidth(wrapper.getMultiblockName(), titleLimit);
        int nameWidth = Math.round(fontRenderer.getStringWidth(name) * TITLE_SCALE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(18.0F, 9.0F, 0.0F);
        GlStateManager.scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
        fontRenderer.drawString(name, 0, 0, TEXT_PRIMARY, true);
        GlStateManager.popMatrix();

        String dimensions = buildDimensions();
        if (!dimensions.isEmpty() && nameWidth + 10 < titleLimit) {
            fontRenderer.drawString(dimensions, 18 + nameWidth + 10, 12, ACCENT, false);
        }

        // Subtitle: structure kind and component count, trimmed so it stays clear of the header controls.
        String subtitle = buildSubtitle();
        int subtitleLimit = Math.max(40, badgeX - 12 - 18);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(subtitle, subtitleLimit), 18, 30, ACCENT_DIM, false);

        // Collapse hint, kept away from the buttons so the button tooltips are unambiguous.
        String escHint = I18n.format("gregtech.multiblock.preview.fullscreen.esc");
        int escX = Math.max(18 + subtitleLimit + 12, badgeX - 30 - fontRenderer.getStringWidth(escHint));
        if (escX + fontRenderer.getStringWidth(escHint) < badgeX - 6) {
            fontRenderer.drawString(escHint, escX, 30, TEXT_DIM, false);
        }

        // Close / collapse button.
        int closeY = 12;
        boolean closeHovered = isInside(mouseX, mouseY, closeX, closeY, BUTTON_SIZE, BUTTON_SIZE);
        drawPanel(closeX, closeY, BUTTON_SIZE, BUTTON_SIZE, closeHovered ? 0xE014222E : HEADER_FILL,
                closeHovered ? SELECTION : PANEL_EDGE);
        drawCornerBrackets(closeX, closeY, BUTTON_SIZE, BUTTON_SIZE, 4, closeHovered ? SELECTION : ACCENT_SOFT);
        drawIconCross(closeX, closeY, BUTTON_SIZE, closeHovered ? SELECTION : TEXT_SECONDARY);
        if (closeHovered) {
            hoveredTooltip = Collections.singletonList(TextFormatting.GRAY + escHint);
        }

        // Layer button.
        int layerY = 12;
        boolean layerHovered = isInside(mouseX, mouseY, layerX, layerY, LAYER_BUTTON_WIDTH, BUTTON_SIZE);
        hoversLayerButton = layerHovered;
        int layer = wrapper.getLayerIndex();
        // A single selected layer gets a soft breathing glow so the active filter is obvious.
        if (layer != -1) {
            drawGlow(layerX, layerY, LAYER_BUTTON_WIDTH, BUTTON_SIZE, ACCENT);
        }
        drawPanel(layerX, layerY, LAYER_BUTTON_WIDTH, BUTTON_SIZE, layerHovered ? 0xE014222E : HEADER_FILL,
                layerHovered ? ACCENT : (layer == -1 ? PANEL_EDGE : ACCENT_DIM));
        String layerText = buildLayerText();
        fontRenderer.drawString(layerText, layerX + (LAYER_BUTTON_WIDTH - fontRenderer.getStringWidth(layerText)) / 2,
                layerY + (BUTTON_SIZE - fontRenderer.FONT_HEIGHT) / 2 + 1,
                layerHovered || layer != -1 ? ACCENT : TEXT_SECONDARY, false);
        if (layerHovered) {
            hoveredTooltip = Collections.singletonList(
                    TextFormatting.GRAY + I18n.format("gregtech.multiblock.preview.fullscreen.layer_tip"));
        }

        // Info badge on the left of the layer button.
        hoversInfoIcon = isInside(mouseX, mouseY, badgeX, 12, BUTTON_SIZE, BUTTON_SIZE);
        if (hoversInfoIcon) {
            drawPanel(badgeX, 12, BUTTON_SIZE, BUTTON_SIZE, 0xE014222E, ACCENT);
            hoveredTooltip = Arrays.asList(
                    I18n.format("gregtech.multiblock.preview.zoom"),
                    I18n.format("gregtech.multiblock.preview.rotate"),
                    I18n.format("gregtech.multiblock.preview.select"));
        }
        drawInfoGlyph(badgeX, 12, BUTTON_SIZE, hoversInfoIcon ? ACCENT : ACCENT_DIM);
    }

    /**
     * @return the structure footprint, e.g. {@code 3x4x3}, or an empty string when nothing is loaded.
     */
    @NotNull
    private String buildDimensions() {
        WorldSceneRenderer renderer = wrapper.getRenderer();
        if (!(renderer != null && renderer.world instanceof TrackedDummyWorld world)) {
            return "";
        }
        Vector3f size = world.getSize();
        return (int) size.x + "x" + (int) size.y + "x" + (int) size.z;
    }

    @NotNull
    private String buildSubtitle() {
        StringBuilder builder = new StringBuilder();
        builder.append(I18n.format("gregtech.multiblock.preview.fullscreen.subtitle"));
        builder.append("   ")
                .append(I18n.format("gregtech.multiblock.preview.fullscreen.parts", totalPartCount()));
        return builder.toString().toUpperCase(Locale.ROOT);
    }

    private int totalPartCount() {
        int total = 0;
        for (ItemStack stack : wrapper.getPreviewParts()) {
            total += Math.max(1, stack.getCount());
        }
        return total;
    }

    @NotNull
    private String buildLayerText() {
        int layer = wrapper.getLayerIndex();
        return "LAYER " + (layer == -1 ? "ALL" : Integer.toString(layer + 1));
    }

    /**
     * The preview buffer is square and always drawn with equal scaling, centered in the viewport: the largest
     * square that fits is used, so the structure is never stretched and never clipped away.
     */
    private void drawViewport(int mouseX, int mouseY) {
        drawPanel(viewportX, viewportY, viewportWidth, viewportHeight, 0xF0101820, PANEL_EDGE);

        int renderX = viewportX + 2;
        int renderY = viewportY + 2;
        int renderWidth = Math.max(1, viewportWidth - 4);
        int renderHeight = Math.max(1, viewportHeight - 4);
        int side = Math.min(renderWidth, renderHeight);
        int squareX = renderX + (renderWidth - side) / 2;
        int squareY = renderY + (renderHeight - side) / 2;
        wrapper.renderScene(mc, renderX, renderY, renderWidth, renderHeight, mouseX, mouseY);

        drawCornerBrackets(viewportX, viewportY, viewportWidth, viewportHeight, 12, ACCENT);
        drawSelectionReadout(squareX, squareY, side, side);
    }

    private void drawViewportPlaceholder() {
        drawPanel(viewportX, viewportY, viewportWidth, viewportHeight, 0xF0101820, PANEL_EDGE);
        drawCornerBrackets(viewportX, viewportY, viewportWidth, viewportHeight, 12, ACCENT_SOFT);
        String text = I18n.format("gregtech.multiblock.preview.fullscreen.loading");
        if (wrapper.hasPreviewFailure()) {
            text = I18n.format("gregtech.multiblock.preview.loading_failed");
        }
        int textWidth = fontRenderer.getStringWidth(text);
        int barWidth = Math.max(80, Math.min(220, viewportWidth - 40));
        int barX = viewportX + (viewportWidth - barWidth) / 2;
        int barY = viewportY + viewportHeight / 2;
        float progress = wrapper.getPreviewProgress();
        drawRect(barX - 1, barY - 1, barX + barWidth + 1, barY + 9, 0xFF101820);
        drawRect(barX, barY, barX + barWidth, barY + 8, 0xFF1B2A36);
        drawRect(barX, barY, barX + Math.round(barWidth * progress), barY + 8, ACCENT_DIM);
        fontRenderer.drawString(text, viewportX + (viewportWidth - textWidth) / 2, barY - fontRenderer.FONT_HEIGHT - 6,
                TEXT_SECONDARY, false);
        String percent = Math.round(progress * 100.0F) + "%";
        fontRenderer.drawString(percent, viewportX + (viewportWidth - fontRenderer.getStringWidth(percent)) / 2,
                barY + 12, TEXT_DIM, false);
    }

    private void drawSelectionReadout(int renderX, int renderY, int renderWidth, int renderHeight) {
        BlockPos selected = wrapper.getSelectedBlock();
        List<String> tips = wrapper.getSelectionTips();
        String text;
        int color;
        if (selected == null) {
            text = I18n.format("gregtech.multiblock.preview.fullscreen.hint_select");
            color = TEXT_DIM;
        } else if (tips.isEmpty()) {
            text = I18n.format("gregtech.multiblock.preview.fullscreen.selected", selected.getX(), selected.getY(),
                    selected.getZ());
            color = OK;
        } else {
            text = tips.get(0);
            color = ACCENT;
        }
        String trimmed = fontRenderer.trimStringToWidth(text, Math.max(20, renderWidth - 16));
        fontRenderer.drawString(trimmed, renderX + 8, renderY + renderHeight - fontRenderer.FONT_HEIGHT - 6,
                color, false);
    }

    /**
     * Handles a renderer that changed under the viewer (first load, or a rebuild after a channel change). The
     * offscreen buffer stays square; this viewer frames the larger camera view itself, so it takes the framing
     * over from the wrapper's compact default.
     */
    private void syncFullscreenRenderer() {
        WorldSceneRenderer renderer = wrapper.getRenderer();
        if (renderer == null || renderer == syncedRenderer) {
            return;
        }
        syncedRenderer = renderer;
        wrapper.setPreviewFrameApplied(true);
        fitCameraToViewport();
        wrapper.applyCamera();
        wrapper.markRendererDirty();
    }

    private void drawPartsPanel(int mouseX, int mouseY) {
        drawPanel(leftPanelX, leftPanelY, leftPanelWidth, leftPanelHeight, PANEL_FILL, PANEL_EDGE);
        drawPanelHeader(leftPanelX, leftPanelY, leftPanelWidth,
                I18n.format("gregtech.multiblock.preview.fullscreen.materials"));

        int gridX = leftPanelX + MATERIAL_GRID_PADDING;
        int gridY = leftPanelY + HEADER_HEIGHT + MATERIAL_GRID_PADDING;
        List<ItemStack> parts = wrapper.getPreviewParts();
        int shown = Math.min(parts.size(), Math.max(0, materialVisibleSlots));

        // Row-major fill: the row uses all four columns before wrapping to the next row.
        for (int i = 0; i < shown; i++) {
            int col = i % MATERIAL_COLUMNS;
            int row = i / MATERIAL_COLUMNS;
            int slotX = gridX + col * MATERIAL_SLOT_SIZE;
            int slotY = gridY + row * MATERIAL_SLOT_SIZE;
            if (slotY + MATERIAL_SLOT_SIZE > leftPanelY + leftPanelHeight - MATERIAL_GRID_PADDING) {
                break;
            }
            boolean hovered = isInside(mouseX, mouseY, slotX, slotY, 16, 16);
            drawSlotFrame(slotX, slotY, hovered);
            drawItemStack(parts.get(i), slotX + 1, slotY + 1);
            if (hovered) {
                hoveredTooltip = buildStackTooltip(parts.get(i));
            }
        }

        // Overflow indicator: tell the player the list is truncated instead of silently dropping entries.
        if (parts.size() > shown) {
            String more = "+" + (parts.size() - shown);
            fontRenderer.drawString(more, leftPanelX + leftPanelWidth - MATERIAL_GRID_PADDING
                    - fontRenderer.getStringWidth(more),
                    leftPanelY + leftPanelHeight - fontRenderer.FONT_HEIGHT - 4, WARN, false);
        }
    }

    private void drawCandidatesPanel(int mouseX, int mouseY) {
        drawPanel(rightPanelX, rightPanelY, rightPanelWidth, rightPanelHeight, PANEL_FILL, PANEL_EDGE);
        drawPanelHeader(rightPanelX, rightPanelY, rightPanelWidth,
                I18n.format("gregtech.multiblock.preview.fullscreen.alternatives"));

        List<PreviewCandidate> candidates = wrapper.getCandidates();
        int slotX = rightPanelX + 10;
        int slotY = rightPanelY + HEADER_HEIGHT + 4;
        int activeIndex = wrapper.getActiveCandidateIndex();

        int shown = Math.min(candidates.size(), CANDIDATE_ROWS);
        for (int i = 0; i < shown; i++) {
            int y = slotY + i * CANDIDATE_SLOT_SIZE;
            boolean hovered = isInside(mouseX, mouseY, slotX, y, 18, 18);
            drawSlotFrame(slotX, y, hovered || i + 1 == activeIndex, hovered);
            List<ItemStack> items = candidates.get(i).getItemCandidates();
            if (!items.isEmpty()) {
                drawItemStack(items.get(0), slotX + 1, y + 1);
            }
            if (hovered) {
                List<String> tooltip = new ArrayList<>();
                if (!items.isEmpty()) {
                    tooltip.addAll(buildStackTooltip(items.get(0)));
                }
                tooltip.addAll(candidates.get(i).getTooltip());
                hoveredTooltip = tooltip;
            }
        }

        // Readout of the alternative currently shown in place in the 3D scene.
        int readoutY = rightPanelY + rightPanelHeight - fontRenderer.FONT_HEIGHT - 4;
        drawRect(rightPanelX + 8, readoutY - 4, rightPanelX + rightPanelWidth - 8, readoutY - 3, ACCENT_FAINT);
        PreviewCandidate active = wrapper.getActiveCandidate();
        if (active != null) {
            String label = activeIndex + "/" + candidates.size() + " " + active.getDisplayName();
            String trimmed = fontRenderer.trimStringToWidth(label, rightPanelWidth - 18);
            fontRenderer.drawString(trimmed, rightPanelX + 9, readoutY, ACCENT, false);
        } else if (wrapper.getSelectedBlock() != null) {
            // A selection without typed alternatives: say so instead of leaving the panel silent.
            String none = fontRenderer.trimStringToWidth(
                    I18n.format("gregtech.multiblock.preview.fullscreen.no_alternatives"), rightPanelWidth - 18);
            fontRenderer.drawString(none, rightPanelX + 9, readoutY, TEXT_DIM, false);
        }
    }

    private void drawChannelPanel(int mouseX, int mouseY) {
        drawPanel(bottomPanelX, bottomPanelY, bottomPanelWidth, bottomPanelHeight, PANEL_FILL, PANEL_EDGE);
        drawPanelHeader(bottomPanelX, bottomPanelY, bottomPanelWidth,
                I18n.format("gregtech.multiblock.preview.fullscreen.channels"));

        List<StructureChannel> channels = wrapper.getSupportedChannels();
        if (channels.isEmpty() && wrapper.isPreviewReady()) {
            fontRenderer.drawString(I18n.format("gregtech.multiblock.preview.fullscreen.no_channels"),
                    bottomPanelX + PANEL_PADDING, bottomPanelY + HEADER_HEIGHT + 2, TEXT_SECONDARY, false);
            return;
        }

        int rowY = bottomPanelY + HEADER_HEIGHT + 2;
        for (int i = 0; i < MAX_SLIDER_ROWS; i++) {
            int y = rowY + i * SLIDER_ROW_HEIGHT;
            boolean hovered;
            if (i < channels.size()) {
                StructureChannel channel = channels.get(i);
                int value = wrapper.getChannelValue(channel);
                int steps = wrapper.getSliderSteps(i);
                int sliderIndex = wrapper.getSliderIndexFor(channel, i);
                float ratio = steps <= 0 ? 0.0F : (float) sliderIndex / steps;

                String label = fontRenderer.trimStringToWidth(I18n.format(channel.getDefaultTooltip()),
                        SLIDER_LABEL_WIDTH - 6);
                fontRenderer.drawString(label, bottomPanelX + PANEL_PADDING, y + 4, TEXT_SECONDARY, false);

                int trackY = y + 13;
                hovered = isInside(mouseX, mouseY, sliderTrackX, y, sliderTrackWidth, SLIDER_ROW_HEIGHT - 4);
                drawRect(sliderTrackX, trackY, sliderTrackX + sliderTrackWidth, trackY + 3, 0xFF16222C);
                if (steps > 0) {
                    // Tick marks give the slider a measurable, instrument-like feel.
                    for (int step = 0; step <= steps && step <= 24; step++) {
                        int tickX = sliderTrackX + Math.round((float) step / steps * (sliderTrackWidth - 2));
                        drawRect(tickX, trackY - 2, tickX + 1, trackY + 5, ACCENT_FAINT);
                    }
                }
                int filledTo = sliderTrackX + Math.round(ratio * (sliderTrackWidth - 3));
                drawGradientRect(sliderTrackX, trackY, Math.max(sliderTrackX + 1, filledTo), trackY + 3, ACCENT_DIM,
                        0xFF1E6B8C);

                int handleX = sliderTrackX + Math.round(ratio * (sliderTrackWidth - 5));
                int handleColor = hovered ? ACCENT : 0xFF7FE9FF;
                drawRect(handleX - 3, trackY - 2, handleX + 5, trackY + 5, 0x6600E5FF);
                drawRect(handleX - 2, trackY - 3, handleX + 4, trackY + 6, handleColor);

                String valueText = buildChannelValueText(channel, value);
                fontRenderer.drawString(valueText,
                        bottomPanelX + bottomPanelWidth - PANEL_PADDING - fontRenderer.getStringWidth(valueText),
                        y + 4, value > 0 ? ACCENT : TEXT_DIM, false);

                if (hovered) {
                    List<String> tips = new ArrayList<>();
                    tips.add(TextFormatting.WHITE + I18n.format(channel.getDefaultTooltip()));
                    int[] range = wrapper.getChannelRange(i);
                    tips.add(TextFormatting.GRAY + I18n.format("gregtech.multiblock.preview.channel_range", range[0],
                            range[1]));
                    tips.add(value > 0
                            ? TextFormatting.AQUA + I18n.format("gregtech.multiblock.preview.channel_current", valueText)
                            : TextFormatting.YELLOW + I18n.format("gregtech.multiblock.preview.channel_auto"));
                    tips.add(TextFormatting.DARK_GRAY + I18n.format("gregtech.multiblock.preview.channel_click"));
                    hoveredTooltip = tips;
                }
            } else {
                // Inactive rows keep the strip from collapsing while the preview or channel list loads.
                int trackY = y + 13;
                drawRect(sliderTrackX, trackY, sliderTrackX + sliderTrackWidth, trackY + 3, 0xFF121B23);
                fontRenderer.drawString(I18n.format("gregtech.multiblock.preview.fullscreen.idle"),
                        bottomPanelX + PANEL_PADDING, y + 4, TEXT_DIM, false);
            }
        }
    }

    @NotNull
    private String buildChannelValueText(StructureChannel channel, int value) {
        if (value <= 0) {
            return I18n.format("gregtech.multiblock.preview.fullscreen.auto");
        }
        ItemStack indicator = channel.getIndicatorItem(value);
        return indicator.isEmpty() ? Integer.toString(value) : indicator.getDisplayName();
    }

    private void drawHints() {
        int y = height - fontRenderer.FONT_HEIGHT - 5;
        String hint = I18n.format("gregtech.multiblock.preview.fullscreen.hint");
        int hintWidth = fontRenderer.getStringWidth(hint);
        int hintX = Math.max(14, (width - hintWidth) / 2);
        drawRect(hintX - 6, y - 3, hintX + hintWidth + 6, y + fontRenderer.FONT_HEIGHT + 3, PANEL_FILL_SOFT);
        drawRect(hintX - 6, y - 3, hintX - 5, y + fontRenderer.FONT_HEIGHT + 3, ACCENT_SOFT);
        drawRect(hintX + hintWidth + 5, y - 3, hintX + hintWidth + 6, y + fontRenderer.FONT_HEIGHT + 3, ACCENT_SOFT);
        fontRenderer.drawString(hint, hintX, y, TEXT_SECONDARY, false);
    }

    private void drawTooltips(int mouseX, int mouseY) {
        List<String> tooltip = hoveredTooltip;
        if (tooltip == null || tooltip.isEmpty()) {
            if (wrapper.isPreviewReady() && insideViewport && !isOverlayHovered()) {
                tooltip = wrapper.getBlockTooltip();
            }
        }
        if (tooltip != null && !tooltip.isEmpty()) {
            GlStateManager.disableDepth();
            TooltipRenderer.drawHoveringText(mc, tooltip, mouseX, mouseY);
            GlStateManager.enableDepth();
        }
    }

    @NotNull
    private List<String> buildStackTooltip(@NotNull ItemStack stack) {
        List<String> tooltip = stack.getTooltip(mc.player,
                mc.gameSettings.advancedItemTooltips ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL);
        EnumRarity rarity = stack.getRarity();
        for (int i = 0; i < tooltip.size(); i++) {
            tooltip.set(i, i == 0 ? rarity.color + tooltip.get(i) : TextFormatting.GRAY + tooltip.get(i));
        }
        return tooltip;
    }

    private void drawItemStack(@NotNull ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        GlStateManager.disableLighting();
        itemRender.zLevel = 100.0F;
        itemRender.renderItemAndEffectIntoGUI(stack, x, y);
        itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, x, y, null);
        itemRender.zLevel = 0.0F;
        GlStateManager.enableLighting();
        GlStateManager.disableDepth();
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public void handleMouseInput() throws IOException {
        int delta = Mouse.getEventDWheel();
        if (delta != 0 && insideViewport) {
            float zoom = wrapper.getZoom();
            zoom = (float) Math.max(1.5D, Math.min(400.0D, zoom + (delta > 0 ? -0.6D : 0.6D)));
            wrapper.setZoom(zoom);
            wrapper.applyCamera();
            wrapper.markRendererDirty();
        }
        super.handleMouseInput();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!isDuplicatedClick(mouseButton)) {
            applyLayout(width, height);
            if (mouseButton == 0 && handleLeftClick(mouseX, mouseY)) {
                return;
            }
            if (mouseButton == 1 && handleRightClick(mouseX, mouseY)) {
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private boolean isDuplicatedClick(int mouseButton) {
        long now = System.currentTimeMillis();
        boolean duplicated = mouseButton == lastClickButton && now - lastClickTime < CLICK_DEBOUNCE_MS;
        lastClickTime = now;
        lastClickButton = mouseButton;
        return duplicated;
    }

    private boolean handleLeftClick(int mouseX, int mouseY) {
        // Close button: collapses back to the recipe page, exactly like ESC.
        int closeX = rightPanelX - BUTTON_SIZE;
        if (isInside(mouseX, mouseY, closeX, 12, BUTTON_SIZE, BUTTON_SIZE)) {
            collapse();
            return true;
        }
        // Layer button.
        int layerX = closeX - 8 - LAYER_BUTTON_WIDTH;
        if (isInside(mouseX, mouseY, layerX, 12, LAYER_BUTTON_WIDTH, BUTTON_SIZE)) {
            wrapper.toggleLayer();
            return true;
        }
        // Channel sliders.
        List<StructureChannel> channels = wrapper.getSupportedChannels();
        int rowY = bottomPanelY + HEADER_HEIGHT + 4;
        for (int i = 0; i < channels.size() && i < MAX_SLIDER_ROWS; i++) {
            int y = rowY + i * SLIDER_ROW_HEIGHT;
            if (isInside(mouseX, mouseY, sliderTrackX, y, sliderTrackWidth, SLIDER_ROW_HEIGHT - 4)) {
                int steps = wrapper.getSliderSteps(i);
                float ratio = (float) (mouseX - sliderTrackX) / Math.max(1, sliderTrackWidth);
                if (wrapper.getChannelValue(channels.get(i)) > 0 || ratio > 0.04F) {
                    wrapper.setChannelValueFromSlider(i, Math.round(ratio * steps));
                } else {
                    wrapper.setChannelValueFromSlider(i, 0);
                }
                return true;
            }
        }
        // Viewport: start a camera rotation drag.
        if (insideViewport) {
            draggingLeft = true;
            return true;
        }
        return false;
    }

    private boolean handleRightClick(int mouseX, int mouseY) {
        if (!insideViewport) {
            return false;
        }
        WorldSceneRenderer renderer = wrapper.getRenderer();
        if (renderer == null || renderer.getLastTraceResult() == null) {
            if (wrapper.getSelectedBlock() != null) {
                wrapper.clearSelection();
                return true;
            }
            return false;
        }
        BlockPos selected = renderer.getLastTraceResult().getBlockPos();
        if (Objects.equals(wrapper.getSelectedBlock(), selected)) {
            return true;
        }
        wrapper.selectBlock(selected);
        return true;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        applyLayout(width, height);
        int deltaX = mouseX - lastMouseX;
        int deltaY = mouseY - lastMouseY;
        boolean inView = mouseX >= viewportX && mouseX < viewportX + viewportWidth
                && mouseY >= viewportY && mouseY < viewportY + viewportHeight;
        if (clickedMouseButton == 0 && draggingLeft && inView) {
            wrapper.setRotation(wrapDegrees(wrapper.getRotationPitch() + deltaX),
                    clamp(wrapper.getRotationYaw() + deltaY, -89.9F, 89.9F));
            wrapper.applyCamera();
        } else if (clickedMouseButton == 1 && inView) {
            panCamera(deltaX, deltaY);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    private void panCamera(int deltaX, int deltaY) {
        final float sensitivity = 0.05F;
        double yaw = Math.toRadians(wrapper.getRotationPitch());
        double pitch = Math.toRadians(wrapper.getRotationYaw());
        double forwardX = Math.cos(pitch) * Math.sin(yaw);
        double forwardY = Math.sin(pitch);
        double forwardZ = Math.cos(pitch) * Math.cos(yaw);
        // right = forward x up
        double rightX = forwardZ;
        double rightZ = -forwardX;
        double rightLength = Math.sqrt(rightX * rightX + rightZ * rightZ);
        if (rightLength < 1.0E-4D) {
            rightX = 1.0D;
            rightZ = 0.0D;
            rightLength = 1.0D;
        }
        rightX /= rightLength;
        rightZ /= rightLength;
        // up = right x forward
        double upX = -rightZ * forwardY;
        double upY = rightZ * forwardX - rightX * forwardZ;
        double upZ = rightX * forwardY;

        Vector3f center = wrapper.getCameraCenter();
        Vector3f moved = new Vector3f(
                center.x + (float) ((-deltaX * rightX - deltaY * upX) * sensitivity),
                center.y + (float) (-deltaY * upY * sensitivity),
                center.z + (float) ((-deltaX * rightZ - deltaY * upZ) * sensitivity));
        wrapper.setCameraCenter(moved);
        wrapper.applyCamera();
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingLeft = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            // Collapse back to the recipe page this viewer was expanded from, instead of leaving the view.
            collapse();
            return;
        }
        // Swallow other keys: the viewer is a mode of the recipe page, and the recipe page's own keybinds
        // must not fire while it covers the screen.
    }

    @Override
    public void updateScreen() {
        // No widget state to tick; drawing drives the preview loading task.
    }

    // ---------------------------------------------------------------------------------------------
    // Drawing helpers
    // ---------------------------------------------------------------------------------------------

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        return wrapped < 0.0F ? wrapped + 360.0F : wrapped;
    }

    private static int withAlpha(int color, float alpha) {
        int a = (int) (((color >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float pulse(float speed, float phase) {
        return 0.55F + 0.45F * (float) Math.sin((System.currentTimeMillis() / 1000.0D) * speed + phase);
    }

    private void drawPanel(int x, int y, int w, int h, int fill, int border) {
        drawRect(x, y, x + w, y + h, fill);
        drawRect(x, y, x + w, y + 1, border);
        drawRect(x, y + h - 1, x + w, y + h, border);
        drawRect(x, y, x + 1, y + h, border);
        drawRect(x + w - 1, y, x + w, y + h, border);
    }

    private void drawPanelHeader(int x, int y, int w, @NotNull String label) {
        drawGradientRect(x + 1, y + 1, x + w - 1, y + HEADER_HEIGHT, HEADER_FILL, withAlpha(HEADER_FILL, 0.35F));
        drawRect(x + 1, y + HEADER_HEIGHT, x + w - 1, y + HEADER_HEIGHT + 1, PANEL_EDGE_SOFT);
        drawRect(x + 8, y + HEADER_HEIGHT - 3, x + 8 + 14, y + HEADER_HEIGHT - 2, ACCENT);
        fontRenderer.drawString(label, x + 8, y + 5, ACCENT_DIM, false);
    }

    private void drawSlotFrame(int x, int y, boolean highlighted) {
        drawSlotFrame(x, y, highlighted, highlighted);
    }

    private void drawSlotFrame(int x, int y, boolean highlighted, boolean strong) {
        drawRect(x, y, x + 18, y + 18, SLOT_FILL);
        int edge = strong ? SLOT_EDGE_HOVER : (highlighted ? ACCENT_DIM : SLOT_EDGE);
        drawRect(x, y, x + 18, y + 1, edge);
        drawRect(x, y + 17, x + 18, y + 18, edge);
        drawRect(x, y, x + 1, y + 18, edge);
        drawRect(x + 17, y, x + 18, y + 18, edge);
        drawCornerBrackets(x, y, 18, 18, 4, withAlpha(edge, strong ? 1.0F : 0.5F));
    }

    private void drawCornerBrackets(int x, int y, int w, int h, int length, int color) {
        int len = Math.min(length, Math.min(w, h) / 2);
        if (len <= 0) {
            return;
        }
        // top-left
        drawRect(x, y, x + len, y + 1, color);
        drawRect(x, y, x + 1, y + len, color);
        // top-right
        drawRect(x + w - len, y, x + w, y + 1, color);
        drawRect(x + w - 1, y, x + w, y + len, color);
        // bottom-left
        drawRect(x, y + h - 1, x + len, y + h, color);
        drawRect(x, y + h - len, x + 1, y + h, color);
        // bottom-right
        drawRect(x + w - len, y + h - 1, x + w, y + h, color);
        drawRect(x + w - 1, y + h - len, x + w, y + h, color);
    }

    private void drawGlow(int x, int y, int w, int h, int color) {
        float intensity = pulse(2.6F, 0.0F);
        int outer = withAlpha(color, 0.10F + 0.08F * intensity);
        int inner = withAlpha(color, 0.16F + 0.10F * intensity);
        drawRect(x - 2, y - 2, x + w + 2, y + h + 2, outer);
        drawRect(x - 1, y - 1, x + w + 1, y + h + 1, inner);
    }

    private void drawInfoGlyph(int x, int y, int size, int color) {
        int centerX = x + size / 2;
        // Lowercase "i": dot above a stem, small enough to read at 20px.
        drawRect(centerX - 1, y + 4, centerX + 1, y + 6, color);
        drawRect(centerX - 1, y + 8, centerX + 1, y + size - 5, color);
    }

    private void drawIconCross(int x, int y, int size, int color) {
        int inset = 6;
        for (int i = 0; i < size - inset * 2; i++) {
            drawRect(x + inset + i, y + inset + i, x + inset + i + 1, y + inset + i + 1, color);
            drawRect(x + size - inset - 1 - i, y + inset + i, x + size - inset - i, y + inset + i + 1, color);
        }
    }
}
