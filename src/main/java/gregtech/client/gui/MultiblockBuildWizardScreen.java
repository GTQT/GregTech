package gregtech.client.gui;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.AbilityGroupLimit;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.client.gui.gl.GDraw;
import gregtech.common.network.NetworkHandler;
import gregtech.common.network.multiblock.CPacketExecuteStructureBuild;
import gregtech.common.network.multiblock.SPacketStructureBuildResult;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper;
import gregtech.integration.jei.multiblock.MultiblockInfoRecipeWrapper.AbilityCountControl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Mouse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build dialog modelled after GTO's {@code PatternBuilderPanel}: a 178 px popup listing the block palette,
 * the structure role tallies and a {@code Back}/{@code Build} footer.
 *
 * <p>Layout tokens follow GTO's {@code UISizes}: 18 px rows, 14 px control height, 5 px popup padding and a
 * 168 px content column. The screen is read-only; the server resolves material consumption and reports
 * missing stacks through {@link SPacketStructureBuildResult}.
 */
@SideOnly(Side.CLIENT)
public class MultiblockBuildWizardScreen extends GuiScreen {

    private static final int PANEL_WIDTH = 178;
    private static final int CONTENT_WIDTH = 168;
    private static final int PADDING = 5;
    private static final int CONTROL_HEIGHT = 14;
    private static final int SECTION_GAP = 4;
    private static final int ROW = 18;
    private static final int BOTTOM_PADDING = 7;
    private static final int BUTTON_WIDTH = 48;
    private static final int CHROME = PADDING + CONTROL_HEIGHT + SECTION_GAP + CONTROL_HEIGHT
            + SECTION_GAP + BOTTOM_PADDING;

    private final MultiblockInfoRecipeWrapper wrapper;
    private final GuiScreen parent;

    private final List<ItemStack> parts = new ArrayList<>();
    private final List<AbilityRow> abilities = new ArrayList<>();
    private List<AbilityCountControl> abilityControls = Collections.emptyList();

    private int left;
    private int top;
    private int panelHeight;
    private int scrollTop;
    private int scrollHeight;
    private int scroll;
    private int scrollMax;

    private List<ItemStack> missing = Collections.emptyList();
    private boolean buildSent;
    private boolean listsLoaded;
    private int placedCells;
    private boolean handledByController;
    private String resultText = "";
    private int resultColor = 0xFF404040;
    private String abilityPopupKey;

    public MultiblockBuildWizardScreen(MultiblockInfoRecipeWrapper wrapper, GuiScreen parent) {
        this.wrapper = wrapper;
        this.parent = parent;
    }

    public static void open(MultiblockInfoRecipeWrapper wrapper, GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(new MultiblockBuildWizardScreen(wrapper, parent));
    }

    @Override
    public void initGui() {
        super.initGui();
        panelHeight = Math.max(CHROME + 2 * ROW, (int) (height * 2.0F / 3.0F));
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;
        scrollTop = top + PADDING + CONTROL_HEIGHT + SECTION_GAP;
        scrollHeight = panelHeight - CHROME;

        parts.clear();
        parts.addAll(wrapper.getPreviewParts());
        rebuildAbilityRows();
        listsLoaded = wrapper.isPreviewReady();
        if (!listsLoaded) {
            wrapper.ensurePreviewLoading();
        }
        scrollMax = Math.max(0, contentHeight() - scrollHeight);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
    }

    private void rebuildAbilityRows() {
        abilities.clear();
        abilityControls = wrapper.getAbilityCountControls();
        MultiPiecePattern pattern = wrapper.getMultiPiecePattern();
        if (pattern == null) {
            return;
        }
        Map<String, Integer> counts = wrapper.getPreviewAbilityCounts();
        Map<String, Integer> groupCounts = wrapper.getPreviewAbilityGroupCounts();
        Map<String, String> controlKeys = new HashMap<>();
        for (AbilityCountControl control : wrapper.getAbilityCountControls()) {
            controlKeys.put(control.getTranslationKey(), control.getKey());
        }
        Map<String, AbilityRow> byLabel = new HashMap<>();
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : pattern.getAbilityLimits().entrySet()) {
            int[] range = entry.getValue();
            if (range == null) {
                continue;
            }
            String labelKey = "gregtech.multiblock.ability." + entry.getKey();
            byLabel.put(labelKey, new AbilityRow(labelKey, range[0], range[1],
                    counts.getOrDefault(entry.getKey().toString(), 0), 1, controlKeys.get(labelKey)));
        }
        for (AbilityGroupLimit group : pattern.getAbilityGroupLimits()) {
            String labelKey = "gregtech.multiblock.ability." + group.getDisplayAbility();
            if (byLabel.containsKey(labelKey)) {
                continue;
            }
            int current = groupCounts.getOrDefault(group.getDisplayAbility().toString(), 0);
            byLabel.put(labelKey, new AbilityRow(labelKey, group.getMin(), group.getMax(), current,
                    group.getAbilities().size(), controlKeys.get(labelKey)));
        }
        abilities.addAll(byLabel.values());
        abilities.sort((one, two) -> one.labelKey.compareToIgnoreCase(two.labelKey));
    }

    /**
     * The dialog is opened directly from the controller, so it owns pumping the incremental preview loader:
     * block counts and the palette are only available once the preview has finished assembling.
     */
    private void refreshListsWhenReady() {
        if (!wrapper.isPreviewReady()) {
            wrapper.advancePreviewLoadingStep();
            if (!wrapper.isPreviewReady()) {
                listsLoaded = false;
                return;
            }
        } else if (listsLoaded) {
            return;
        }
        listsLoaded = true;
        parts.clear();
        parts.addAll(wrapper.getPreviewParts());
        rebuildAbilityRows();
        int previousMax = scrollMax;
        scrollMax = Math.max(0, contentHeight() - scrollHeight);
        if (scrollMax != previousMax) {
            scroll = Math.max(0, Math.min(scroll, scrollMax));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        refreshListsWhenReady();
        drawDefaultBackground();
        drawWindow();
        drawTitle();
        drawCloseButton(mouseX, mouseY);
        drawContent(mouseX, mouseY);
        drawScrollbar();
        drawFooter(mouseX, mouseY);
        drawAbilityPopup(mouseX, mouseY);
    }

    private void drawWindow() {
        GDraw.rect(left, top, PANEL_WIDTH, panelHeight, 0xFFC6C6C6);
        GDraw.outline(left, top, PANEL_WIDTH, panelHeight, 0xFF555555);
        GDraw.rect(left + PADDING, top + PADDING, CONTENT_WIDTH, CONTROL_HEIGHT, 0xFFBEBEBE);
        GDraw.outline(left + PADDING, top + PADDING, CONTENT_WIDTH, CONTROL_HEIGHT, 0xFF8A8A8A);
    }

    private void drawTitle() {
        String title = I18n.format("gregtech.multiblock.build_wizard.title", wrapper.getMultiblockName());
        GDraw.textTrimmed(title, left + PADDING + 2, top + PADDING + 3,
                CONTENT_WIDTH - 4 - CONTROL_HEIGHT - 2, 0xFF202020);
    }

    private void drawCloseButton(int mouseX, int mouseY) {
        int x = left + PANEL_WIDTH - PADDING - CONTROL_HEIGHT;
        int y = top + PADDING;
        boolean hovered = inside(mouseX, mouseY, x, y, CONTROL_HEIGHT, CONTROL_HEIGHT);
        GDraw.rect(x, y, CONTROL_HEIGHT, CONTROL_HEIGHT, hovered ? 0xFFD2D2D2 : 0xFFBEBEBE);
        GDraw.outline(x, y, CONTROL_HEIGHT, CONTROL_HEIGHT, 0xFF8A8A8A);
        GDraw.textCentered("x", x + CONTROL_HEIGHT / 2, y + 3, hovered ? 0xFF202020 : 0xFF505050);
    }

    private void drawContent(int mouseX, int mouseY) {
        GDraw.pushScissor(left + PADDING, scrollTop, CONTENT_WIDTH, scrollHeight);
        int y = scrollTop - scroll;
        // Hatch caps come first: the dialog is also the only adjustment surface on short screens,
        // where the role list after the block palette would otherwise be below the fold.
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.ability_counts"), y);
        if (abilityControls.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_ability_counts"),
                    left + PADDING + 2, y + 5, 0xFF6A6A6A);
            y += ROW;
        } else {
            for (AbilityCountControl control : abilityControls) {
                drawAbilityCountRow(control, y, mouseX, mouseY);
                y += ROW;
            }
        }
        y += SECTION_GAP;
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.blocks"), y);
        if (parts.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_parts"), left + PADDING + 2, y + 5,
                    0xFF6A6A6A);
            y += ROW;
        } else {
            for (ItemStack stack : parts) {
                drawPartRow(stack, y);
                y += ROW;
            }
        }
        y += SECTION_GAP;
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.roles"), y);
        if (abilities.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_roles"), left + PADDING + 2, y + 5,
                    0xFF6A6A6A);
            y += ROW;
        } else {
            for (AbilityRow row : abilities) {
                drawAbilityRow(row, y, mouseX, mouseY);
                y += ROW;
            }
        }
        y += SECTION_GAP;
        if (!missing.isEmpty()) {
            y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.missing_items"), y);
            for (ItemStack stack : missing) {
                drawPartRow(stack, y);
                y += ROW;
            }
        }
        GDraw.popScissor();
    }

    private int drawSectionHeader(String label, int y) {
        GDraw.text(label, left + PADDING + 2, y + 5, 0xFF202020);
        GDraw.rect(left + PADDING, y + ROW - 1, CONTENT_WIDTH, 1, 0xFFB0B0B0);
        return y + ROW;
    }

    private void drawPartRow(ItemStack stack, int y) {
        GDraw.item(stack, left + PADDING + 1, y + 1);
        String countText = "x" + stack.getCount();
        int countWidth = GDraw.textWidth(countText);
        GDraw.textTrimmed(stack.getDisplayName(), left + PADDING + 22, y + 5,
                CONTENT_WIDTH - 22 - countWidth - 8, 0xFF202020);
        GDraw.textRight(countText, left + PADDING + CONTENT_WIDTH - 3, y + 5, 0xFF404040);
    }

    private void drawAbilityCountRow(AbilityCountControl control, int y, int mouseX, int mouseY) {
        int plusX = left + PADDING + CONTENT_WIDTH - 3 - CONTROL_HEIGHT;
        int minusX = plusX - 2 - 28 - CONTROL_HEIGHT;
        int boxX = minusX + CONTROL_HEIGHT + 2;
        int boxWidth = plusX - 2 - boxX;
        GDraw.textTrimmed(I18n.format(control.getTranslationKey()), left + PADDING + 2, y + 5,
                minusX - (left + PADDING + 2) - 4, 0xFF202020);

        boolean minusHovered = inside(mouseX, mouseY, minusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT);
        GDraw.bevel(minusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT,
                minusHovered ? 0xFFD2D2D2 : 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered("-", minusX + CONTROL_HEIGHT / 2, y + 5, 0xFF202020);

        GDraw.rect(boxX, y + 2, boxWidth, CONTROL_HEIGHT, 0xFF9A9A9A);
        GDraw.outline(boxX, y + 2, boxWidth, CONTROL_HEIGHT, 0xFF555555);
        GDraw.textCentered(String.valueOf(control.getValue()), boxX + boxWidth / 2, y + 5, 0xFFFFFFFF);

        boolean plusHovered = inside(mouseX, mouseY, plusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT);
        GDraw.bevel(plusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT,
                plusHovered ? 0xFFD2D2D2 : 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered("+", plusX + CONTROL_HEIGHT / 2, y + 5, 0xFF202020);
    }

    private void drawAbilityRow(AbilityRow row, int y, int mouseX, int mouseY) {
        String label = row.labelText();
        int selectWidth = row.controlKey != null ? BUTTON_WIDTH : 0;
        int selectX = left + PADDING + CONTENT_WIDTH - 3 - selectWidth;
        int tallyRight = selectWidth > 0 ? selectX - 4 : left + PADDING + CONTENT_WIDTH - 3;
        GDraw.textTrimmed(label, left + PADDING + 2, y + 5, tallyRight - (left + PADDING + 2) - 30,
                0xFF202020);
        String requirement = row.requirementText();
        String tally = (listsLoaded ? String.valueOf(row.current) : "-")
                + (requirement.isEmpty() ? "" : " / " + requirement);
        int color;
        if (!listsLoaded) {
            color = 0xFF6A6A6A;
        } else if (row.min > 0) {
            color = row.satisfied() ? 0xFF2E7D32 : 0xFFC62828;
        } else {
            color = row.satisfied() ? 0xFF404040 : 0xFFC62828;
        }
        GDraw.textRight(tally, tallyRight, y + 5, color);
        if (selectWidth > 0) {
            boolean hovered = inside(mouseX, mouseY, selectX, y + 2, selectWidth, CONTROL_HEIGHT);
            GDraw.bevel(selectX, y + 2, selectWidth, CONTROL_HEIGHT,
                    hovered ? 0xFFD2D2D2 : 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
            GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.select"),
                    selectX + selectWidth / 2, y + 5, 0xFF202020);
        }
    }

    private void drawScrollbar() {
        if (scrollMax <= 0) {
            return;
        }
        int trackX = left + PANEL_WIDTH - 3;
        GDraw.rect(trackX, scrollTop, 2, scrollHeight, 0xFFBDBDBD);
        int thumbHeight = Math.max(10, scrollHeight * scrollHeight / contentHeight());
        int thumbY = scrollTop + (scrollHeight - thumbHeight) * scroll / scrollMax;
        GDraw.rect(trackX, thumbY, 2, thumbHeight, 0xFF8A8A8A);
    }

    private void drawFooter(int mouseX, int mouseY) {
        int buttonY = top + panelHeight - BOTTOM_PADDING - CONTROL_HEIGHT;
        int leftX = left + PADDING;
        int rightX = left + PANEL_WIDTH - PADDING - BUTTON_WIDTH;

        boolean backHovered = inside(mouseX, mouseY, leftX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT);
        GDraw.bevel(leftX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT, backHovered ? 0xFFD8D8D8 : 0xFFC6C6C6,
                0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.back"), leftX + BUTTON_WIDTH / 2,
                buttonY + 3, 0xFF202020);

        boolean buildHovered = !buildSent && inside(mouseX, mouseY, rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT);
        GDraw.rect(rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT,
                buildSent ? 0xFF7F9C83 : (buildHovered ? 0xFF3D8A48 : 0xFF2E7D32));
        GDraw.outline(rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT, 0xFF1B5E20);
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.build"),
                rightX + BUTTON_WIDTH / 2, buttonY + 3, buildSent ? 0xFFE0E0E0 : 0xFFFFFFFF);

        String status = null;
        int statusColor = 0xFF404040;
        if (buildSent) {
            status = I18n.format("gregtech.multiblock.build_wizard.building");
            statusColor = 0xFF6A6A6A;
        } else if (!missing.isEmpty()) {
            status = I18n.format("gregtech.multiblock.build_wizard.missing", missing.size());
            statusColor = 0xFFC62828;
        } else if (!resultText.isEmpty()) {
            status = resultText;
            statusColor = resultColor;
        }
        if (status != null) {
            GDraw.textTrimmed(status, leftX + BUTTON_WIDTH + 4, buttonY + 4,
                    PANEL_WIDTH - 2 * PADDING - 2 * BUTTON_WIDTH - 8, statusColor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0) {
            return;
        }
        if (abilityPopupKey != null) {
            handleAbilityPopupClick(mouseX, mouseY);
            return;
        }
        int closeX = left + PANEL_WIDTH - PADDING - CONTROL_HEIGHT;
        int closeY = top + PADDING;
        if (inside(mouseX, mouseY, closeX, closeY, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
            goBack();
            return;
        }
        if (handleAbilityCountClick(mouseX, mouseY)) {
            return;
        }
        if (handleAbilityRowClick(mouseX, mouseY)) {
            return;
        }
        int buttonY = top + panelHeight - BOTTOM_PADDING - CONTROL_HEIGHT;
        int leftX = left + PADDING;
        int rightX = left + PANEL_WIDTH - PADDING - BUTTON_WIDTH;
        if (inside(mouseX, mouseY, leftX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT)) {
            goBack();
            return;
        }
        if (inside(mouseX, mouseY, rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT)) {
            if (!buildSent) {
                executeBuild();
            }
        }
    }

    private boolean handleAbilityCountClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = abilityControlsTop();
        for (AbilityCountControl control : abilityControls) {
            int plusX = left + PADDING + CONTENT_WIDTH - 3 - CONTROL_HEIGHT;
            int minusX = plusX - 2 - 28 - CONTROL_HEIGHT;
            if (inside(mouseX, mouseY, plusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
                wrapper.stepAbilityCount(control.getKey(), 1);
                return true;
            }
            if (inside(mouseX, mouseY, minusX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
                wrapper.stepAbilityCount(control.getKey(), -1);
                return true;
            }
            y += ROW;
        }
        return false;
    }

    private int abilityControlsTop() {
        return scrollTop - scroll + ROW;
    }

    private boolean handleAbilityRowClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = rolesTop();
        for (AbilityRow row : abilities) {
            if (row.controlKey != null) {
                int selectX = left + PADDING + CONTENT_WIDTH - 3 - BUTTON_WIDTH;
                if (inside(mouseX, mouseY, selectX, y + 2, BUTTON_WIDTH, CONTROL_HEIGHT)) {
                    abilityPopupKey = row.controlKey;
                    return true;
                }
            }
            y += ROW;
        }
        return false;
    }

    private int rolesTop() {
        int y = scrollTop - scroll + ROW + Math.max(1, abilityControls.size()) * ROW + SECTION_GAP;
        y += ROW + Math.max(1, parts.size()) * ROW + SECTION_GAP;
        return y + ROW;
    }

    private void drawAbilityPopup(int mouseX, int mouseY) {
        AbilityCountControl control = findAbilityControl(abilityPopupKey);
        if (control == null) {
            abilityPopupKey = null;
            return;
        }
        int popupWidth = 176;
        int popupHeight = 72;
        int x = (width - popupWidth) / 2;
        int y = (height - popupHeight) / 2;
        GDraw.rect(x, y, popupWidth, popupHeight, 0xFFC6C6C6);
        GDraw.outline(x, y, popupWidth, popupHeight, 0xFF555555);
        GDraw.rect(x + 1, y + 1, popupWidth - 2, CONTROL_HEIGHT, 0xFFBEBEBE);
        GDraw.textTrimmed(I18n.format(control.getTranslationKey()), x + PADDING, y + 4,
                popupWidth - 2 * PADDING - CONTROL_HEIGHT - 2, 0xFF202020);
        int closeX = x + popupWidth - PADDING - CONTROL_HEIGHT;
        GDraw.rect(closeX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT,
                inside(mouseX, mouseY, closeX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT) ? 0xFFD2D2D2 : 0xFFC6C6C6);
        GDraw.outline(closeX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT, 0xFF8A8A8A);
        GDraw.textCentered("x", closeX + CONTROL_HEIGHT / 2, y + 5, 0xFF202020);

        int minusX = x + 8;
        int plusX = x + popupWidth - 8 - CONTROL_HEIGHT;
        int boxX = minusX + CONTROL_HEIGHT + 2;
        int boxWidth = plusX - 2 - boxX;
        int rowY = y + 28;
        GDraw.bevel(minusX, rowY, CONTROL_HEIGHT, CONTROL_HEIGHT, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.bevel(plusX, rowY, CONTROL_HEIGHT, CONTROL_HEIGHT, 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered("-", minusX + CONTROL_HEIGHT / 2, rowY + 3, 0xFF202020);
        GDraw.textCentered("+", plusX + CONTROL_HEIGHT / 2, rowY + 3, 0xFF202020);
        GDraw.rect(boxX, rowY, boxWidth, CONTROL_HEIGHT, 0xFFEDEDED);
        GDraw.outline(boxX, rowY, boxWidth, CONTROL_HEIGHT, 0xFF8A8A8A);
        GDraw.textCentered(String.valueOf(control.getValue()), boxX + boxWidth / 2, rowY + 3, 0xFF202020);
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.ability_range",
                control.getMin(), control.getMax()), x + popupWidth / 2, y + 50, 0xFF505050);
    }

    private void handleAbilityPopupClick(int mouseX, int mouseY) {
        AbilityCountControl control = findAbilityControl(abilityPopupKey);
        if (control == null) {
            abilityPopupKey = null;
            return;
        }
        int popupWidth = 176;
        int popupHeight = 72;
        int x = (width - popupWidth) / 2;
        int y = (height - popupHeight) / 2;
        int closeX = x + popupWidth - PADDING - CONTROL_HEIGHT;
        if (inside(mouseX, mouseY, closeX, y + 2, CONTROL_HEIGHT, CONTROL_HEIGHT)
                || !inside(mouseX, mouseY, x, y, popupWidth, popupHeight)) {
            abilityPopupKey = null;
            return;
        }
        int minusX = x + 8;
        int plusX = x + popupWidth - 8 - CONTROL_HEIGHT;
        int rowY = y + 28;
        if (inside(mouseX, mouseY, minusX, rowY, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
            wrapper.stepAbilityCount(control.getKey(), -1);
        } else if (inside(mouseX, mouseY, plusX, rowY, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
            wrapper.stepAbilityCount(control.getKey(), 1);
        }
    }

    @NotNull
    private AbilityCountControl findAbilityControl(@NotNull String key) {
        if (key == null) {
            return null;
        }
        for (AbilityCountControl control : wrapper.getAbilityCountControls()) {
            if (key.equals(control.getKey())) {
                return control;
            }
        }
        return null;
    }

    private void goBack() {
        // Opened straight from the controller: Back leads to the fullscreen structure preview,
        // which is the other half of GTO's flow (preview <-> build configuration).
        if (parent != null) {
            mc.displayGuiScreen(parent);
        } else {
            wrapper.openBuilderPreview();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel == 0 || scrollMax <= 0) {
            return;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll - Integer.signum(dWheel) * ROW));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        SPacketStructureBuildResult.Snapshot snapshot = SPacketStructureBuildResult.takeSnapshot();
        if (snapshot != null && buildSent) {
            buildSent = false;
            missing = snapshot.getMissingItems();
            placedCells = snapshot.getPlacedCells();
            handledByController = snapshot.isHandledByController();
            if (missing.isEmpty()) {
                if (handledByController) {
                    resultText = I18n.format("gregtech.multiblock.build_wizard.build_handled");
                    resultColor = 0xFF2E7D32;
                } else if (placedCells > 0) {
                    resultText = I18n.format("gregtech.multiblock.build_wizard.build_done", placedCells);
                    resultColor = 0xFF2E7D32;
                } else {
                    resultText = I18n.format("gregtech.multiblock.build_wizard.nothing_to_build");
                    resultColor = 0xFF6A6A6A;
                }
            } else {
                resultText = "";
            }
            int previousMax = scrollMax;
            scrollMax = Math.max(0, contentHeight() - scrollHeight);
            if (scrollMax != previousMax) {
                scroll = Math.max(0, Math.min(scroll, scrollMax));
            }
        }
    }

    private void executeBuild() {
        if (!wrapper.hasWorldController()) {
            return;
        }
        buildSent = true;
        missing = Collections.emptyList();
        resultText = "";
        placedCells = 0;
        handledByController = false;
        NetworkHandler.INSTANCE.sendToServer(new CPacketExecuteStructureBuild(wrapper.getControllerPos(),
                wrapper.copyPreviewChannels()));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int contentHeight() {
        int height = ROW + Math.max(1, abilityControls.size()) * ROW;
        height += SECTION_GAP + ROW + Math.max(1, parts.size()) * ROW;
        height += SECTION_GAP + ROW + Math.max(1, abilities.size()) * ROW;
        if (!missing.isEmpty()) {
            height += SECTION_GAP + ROW + missing.size() * ROW;
        }
        return height;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static final class AbilityRow {

        private final String labelKey;
        private final int min;
        private final int max;
        private final int current;
        private final int variants;
        @Nullable
        private final String controlKey;

        private AbilityRow(String labelKey, int min, int max, int current, int variants,
                           @Nullable String controlKey) {
            this.labelKey = labelKey;
            this.min = min;
            this.max = max;
            this.current = current;
            this.variants = variants;
            this.controlKey = controlKey;
        }

        private String labelText() {
            String label = I18n.format(labelKey);
            return variants > 1
                    ? I18n.format("gregtech.multiblock.build_wizard.role_many", label, variants)
                    : label;
        }

        private boolean satisfied() {
            return current >= min && (max < 0 || current <= max);
        }

        private String requirementText() {
            if (min > 0 && max >= 0) {
                return min == max ? String.valueOf(min) : min + "~" + max;
            }
            if (min > 0) {
                return "\u2265" + min;
            }
            if (max >= 0) {
                return "\u2264" + max;
            }
            return "";
        }
    }
}
