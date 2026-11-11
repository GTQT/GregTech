package gregtech.client.gui;

import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.pattern.AbilityGroupLimit;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.StructureExclusions;
import gregtech.api.pattern.StructurePiece;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.client.gui.gl.GDraw;
import gregtech.common.network.NetworkHandler;
import gregtech.common.network.multiblock.CPacketDismantleStructure;
import gregtech.common.network.multiblock.CPacketEncodeStructure;
import gregtech.common.network.multiblock.CPacketEvaluateStructureBuild;
import gregtech.common.network.multiblock.CPacketExecuteStructureBuild;
import gregtech.common.network.multiblock.SPacketStructureBuildBill;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Build dialog: a 178 px popup with the block palette, role tallies, optional pieces and a
 * {@code Back}/{@code Demolish}/{@code Build} footer.
 *
 * <p>Layout: 18 px rows, 14 px control height, 5 px popup padding and a 168 px content column. The screen is
 * read-only; the server resolves material consumption and reports missing stacks through
 * {@link SPacketStructureBuildResult}.
 */
@SideOnly(Side.CLIENT)
public class MultiblockBuildWizardScreen extends GuiScreen {

    private static final int PANEL_WIDTH = 178;
    private static final int CONTENT_WIDTH = 168;
    private static final int PADDING = 5;
    private static final int CONTROL_HEIGHT = 14;
    private static final int SECTION_GAP = 4;
    private static final int ROW = 18;
    private static final int MAX_ROLE_BILL_LINES = 10;
    private static final int BOTTOM_PADDING = 7;
    private static final int BUTTON_WIDTH = 48;
    private static final int CHROME = PADDING + CONTROL_HEIGHT + SECTION_GAP + CONTROL_HEIGHT
            + SECTION_GAP + BOTTOM_PADDING;
    private static final int ROLE_CARD_HEIGHT = 26;

    private final MultiblockInfoRecipeWrapper wrapper;
    private final GuiScreen parent;

    private final List<ItemStack> parts = new ArrayList<>();
    private final List<AbilityRow> abilities = new ArrayList<>();
    private final List<Integer> roleRowTops = new ArrayList<>();
    private final List<ExtraRow> extras = new ArrayList<>();
    private final List<Integer> extraRowTops = new ArrayList<>();
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
    private int billRequestId;
    @Nullable
    private SPacketStructureBuildBill.Snapshot bill;
    private boolean requireComplete;
    private boolean noHatch;
    private boolean useNetworkStock = true;

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
        noHatch = wrapper.getPreviewChannelValue(GTStructureChannels.NO_HATCH.getName()) != 0;
        rebuildAbilityRows();
        listsLoaded = wrapper.isPreviewReady();
        if (!listsLoaded) {
            wrapper.ensurePreviewLoading();
        }
        scrollMax = Math.max(0, contentHeight() - scrollHeight);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        requestBill();
    }

    /**
     * Asks the server for an authoritative, non-mutating material bill. Every ability count change re-requests it so
     * the band under the block palette always matches the configuration that would be built.
     */
    private void requestBill() {
        bill = null;
        if (!wrapper.hasWorldController()) {
            return;
        }
        billRequestId++;
        NetworkHandler.INSTANCE.sendToServer(new CPacketEvaluateStructureBuild(
                wrapper.getControllerPos(), billRequestId,
                wrapper.copyPreviewChannels(), wrapper.copyCellOverrides(), useNetworkStock));
    }

    private void rebuildAbilityRows() {
        abilities.clear();
        extras.clear();
        abilityControls = wrapper.getAbilityCountControls();
        MultiPiecePattern pattern = wrapper.getMultiPiecePattern();
        if (pattern == null) {
            return;
        }
        List<StructurePiece> tooling = pattern.getToolingPieceList();
        for (int i = 0; i < tooling.size(); i++) {
            StructurePiece piece = tooling.get(i);
            if (piece.isOptional()) {
                extras.add(new ExtraRow(i + 1, piece.getName()));
            }
        }
        Map<String, Integer> counts = wrapper.getPreviewAbilityCounts();
        Map<String, Integer> groupCounts = wrapper.getPreviewAbilityGroupCounts();
        Map<String, String> controlKeys = new HashMap<>();
        for (AbilityCountControl control : wrapper.getAbilityCountControls()) {
            controlKeys.put(control.getTranslationKey(), control.getKey());
        }
        Map<String, AbilityRow> byLabel = new HashMap<>();
        Set<MultiblockAbility<?>> grouped = new HashSet<>();
        for (AbilityGroupLimit group : pattern.getAbilityGroupLimits()) {
            grouped.addAll(group.getAbilities());
        }
        for (Map.Entry<MultiblockAbility<?>, int[]> entry : pattern.getAbilityLimits().entrySet()) {
            int[] range = entry.getValue();
            if (range == null || grouped.contains(entry.getKey())) {
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
        drawRequireCompleteRow(y);
        y += ROW;
        drawNoHatchRow(y);
        y += ROW;
        drawNetworkStockRow(y);
        y += ROW;
        drawEncodeRow(y, mouseX, mouseY);
        y += ROW;
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.blocks"), y);
        if (parts.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_parts"), left + PADDING + 2, y + 5,
                    0xFF6A6A6A);
            y += ROW;
        } else {
            for (ItemStack stack : parts) {
                drawPartRow(stack, y, true);
                y += ROW;
            }
            int missingKinds = countMissingParts();
            if (missingKinds > 0) {
                GDraw.text(I18n.format("gregtech.multiblock.build_wizard.bill_missing", missingKinds),
                        left + PADDING + 2, y + 5, 0xFFC62828);
                y += ROW;
            }
        }
        if (bill != null && !bill.getRequiredItems().isEmpty()) {
            int requiredTotal = 0;
            for (ItemStack stack : bill.getRequiredItems()) {
                requiredTotal += stack.getCount();
            }
            int billMissing = bill.getMissingItems().size();
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.bill_server", requiredTotal, billMissing),
                    left + PADDING + 2, y + 5, billMissing > 0 ? 0xFFC62828 : 0xFF2E7D32);
            y += ROW;
            if (billMissing > 0 && !bill.isNetworkLinked()) {
                GDraw.textTrimmed(I18n.format("gregtech.multiblock.build_wizard.network_missing_hint"),
                        left + PADDING + 2, y + 5, CONTENT_WIDTH - 4, 0xFFC62828);
                y += ROW;
            }
        }
        if (bill != null && !bill.getRoleItems().isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.role_bill"), left + PADDING + 2, y + 5,
                    0xFF404040);
            y += ROW;
            int lines = 0;
            for (Map.Entry<String, List<ItemStack>> entry : bill.getRoleItems().entrySet()) {
                String label = roleBillLabel(entry.getKey());
                for (ItemStack stack : entry.getValue()) {
                    if (lines >= MAX_ROLE_BILL_LINES) {
                        GDraw.text(I18n.format("gregtech.multiblock.build_wizard.role_bill_more"),
                                left + PADDING + 4, y + 5, 0xFF8A8A8A);
                        y += ROW;
                        break;
                    }
                    GDraw.textTrimmed(label + ": " + stack.getDisplayName() + " x" + stack.getCount(),
                            left + PADDING + 4, y + 5, CONTENT_WIDTH - 8, 0xFF505050);
                    y += ROW;
                    lines++;
                }
                if (lines >= MAX_ROLE_BILL_LINES) {
                    break;
                }
            }
        }
        y += SECTION_GAP;
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.extras"), y);
        extraRowTops.clear();
        if (extras.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_extras"), left + PADDING + 2, y + 5,
                    0xFF6A6A6A);
            y += ROW;
        } else {
            for (ExtraRow extra : extras) {
                extraRowTops.add(y);
                drawExtraRow(extra, y, mouseX, mouseY);
                y += ROW;
            }
        }
        y += SECTION_GAP;
        y = drawSectionHeader(I18n.format("gregtech.multiblock.build_wizard.roles"), y);
        roleRowTops.clear();
        if (abilities.isEmpty()) {
            GDraw.text(I18n.format("gregtech.multiblock.build_wizard.no_roles"), left + PADDING + 2, y + 5,
                    0xFF6A6A6A);
            y += ROW;
        } else {
            for (AbilityRow row : abilities) {
                roleRowTops.add(y);
                drawRoleCard(row, y, mouseX, mouseY);
                y += ROLE_CARD_HEIGHT;
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

    /** Blocks the build until the server bill reports no missing material. */
    private void drawRequireCompleteRow(int y) {
        int boxX = left + PADDING + 2;
        GDraw.rect(boxX, y + 3, 10, 10, 0xFFFFFFFF);
        GDraw.outline(boxX, y + 3, 10, 10, 0xFF8A8A8A);
        if (requireComplete) {
            GDraw.rect(boxX + 2, y + 5, 6, 6, 0xFF2E7D32);
        }
        GDraw.textTrimmed(I18n.format("gregtech.multiblock.build_wizard.require_complete"),
                boxX + 16, y + 5, CONTENT_WIDTH - 18, 0xFF202020);
    }

    private boolean handleRequireCompleteClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = scrollTop - scroll;
        if (inside(mouseX, mouseY, left + PADDING + 2, y + 2, 12, 12)) {
            requireComplete = !requireComplete;
            return true;
        }
        return false;
    }

    /**
     * Skips hatch candidates so the structure is previewed with plain casings; the engine honours the same
     * {@code no_hatch} channel when building.
     */
    private void drawNoHatchRow(int y) {
        int boxX = left + PADDING + 2;
        GDraw.rect(boxX, y + 3, 10, 10, 0xFFFFFFFF);
        GDraw.outline(boxX, y + 3, 10, 10, 0xFF8A8A8A);
        if (noHatch) {
            GDraw.rect(boxX + 2, y + 5, 6, 6, 0xFFC62828);
        }
        GDraw.textTrimmed(I18n.format("gregtech.multiblock.build_wizard.no_hatch"),
                boxX + 16, y + 5, CONTENT_WIDTH - 18, 0xFF202020);
    }

    private boolean handleNoHatchClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = scrollTop - scroll + ROW;
        if (inside(mouseX, mouseY, left + PADDING + 2, y + 2, 12, 12)) {
            noHatch = !noHatch;
            wrapper.setPreviewChannel(GTStructureChannels.NO_HATCH.getName(), noHatch ? 1 : 0);
            listsLoaded = false;
            requestBill();
            return true;
        }
        return false;
    }

    /** Whether missing blocks may be pulled from an external item source such as an ME network. */
    private void drawNetworkStockRow(int y) {
        int boxX = left + PADDING + 2;
        GDraw.rect(boxX, y + 3, 10, 10, 0xFFFFFFFF);
        GDraw.outline(boxX, y + 3, 10, 10, 0xFF8A8A8A);
        if (useNetworkStock) {
            GDraw.rect(boxX + 2, y + 5, 6, 6, 0xFF2E7D32);
        }
        String label = I18n.format("gregtech.multiblock.build_wizard.network_stock");
        GDraw.textTrimmed(label, boxX + 16, y + 5, CONTENT_WIDTH - 74, 0xFF202020);
        if (bill == null) {
            return;
        }
        String status = I18n.format(bill.isNetworkLinked()
                ? "gregtech.multiblock.build_wizard.network_linked"
                : "gregtech.multiblock.build_wizard.network_unlinked");
        GDraw.text(status, left + PADDING + CONTENT_WIDTH - 4 - GDraw.textWidth(status), y + 5,
                bill.isNetworkLinked() ? 0xFF2E7D32 : 0xFF8A8A8A);
    }

    private boolean handleNetworkStockClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = scrollTop - scroll + 2 * ROW;
        if (inside(mouseX, mouseY, left + PADDING + 2, y + 2, 12, 12)) {
            useNetworkStock = !useNetworkStock;
            requestBill();
            return true;
        }
        return false;
    }

    /** Writes the server bill into a pattern item through a registered handler. */
    private void drawEncodeRow(int y, int mouseX, int mouseY) {
        int x = left + PADDING + 2;
        int width = 72;
        boolean hovered = inside(mouseX, mouseY, x, y + 2, width, 12);
        GDraw.rect(x, y + 2, width, 12, hovered ? 0xFFD2D2D2 : 0xFFBEBEBE);
        GDraw.outline(x, y + 2, width, 12, 0xFF8A8A8A);
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.encode"), x + width / 2, y + 4,
                0xFF202020);
    }

    private boolean handleEncodeClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        int y = scrollTop - scroll + 3 * ROW;
        if (!inside(mouseX, mouseY, left + PADDING + 2, y + 2, 72, 12)) {
            return false;
        }
        if (wrapper.hasWorldController()) {
            NetworkHandler.INSTANCE.sendToServer(new CPacketEncodeStructure(wrapper.getControllerPos(),
                    wrapper.copyPreviewChannels(), wrapper.copyCellOverrides(), useNetworkStock));
        }
        return true;
    }

    private boolean canBuild() {
        // With "require complete materials" the server bill is the authority: a missing or still-pending bill
        // must not be treated as "nothing missing".
        return !requireComplete || (bill != null && bill.getMissingItems().isEmpty());
    }

    private void drawPartRow(ItemStack stack, int y) {
        drawPartRow(stack, y, false);
    }

    /**
     * One required-block row. The palette shows {@code carried/required} on the right; the missing list keeps the
     * plain count.
     */
    private void drawPartRow(ItemStack stack, int y, boolean showStock) {
        GDraw.item(stack, left + PADDING + 1, y + 1);
        if (!showStock) {
            String countText = "x" + stack.getCount();
            int countWidth = GDraw.textWidth(countText);
            GDraw.textTrimmed(stack.getDisplayName(), left + PADDING + 22, y + 5,
                    CONTENT_WIDTH - 22 - countWidth - 8, 0xFF202020);
            GDraw.textRight(countText, left + PADDING + CONTENT_WIDTH - 3, y + 5, 0xFF404040);
            return;
        }
        int required = stack.getCount();
        int carried = countInInventory(stack);
        String stockText = carried + "/" + required;
        int stockWidth = GDraw.textWidth(stockText);
        GDraw.textTrimmed(stack.getDisplayName(), left + PADDING + 22, y + 5,
                CONTENT_WIDTH - 22 - stockWidth - 8, 0xFF202020);
        GDraw.textRight(stockText, left + PADDING + CONTENT_WIDTH - 3, y + 5,
                carried >= required ? 0xFF2E7D32 : 0xFFC62828);
    }

    private int countMissingParts() {
        int missingKinds = 0;
        for (ItemStack stack : parts) {
            if (countInInventory(stack) < stack.getCount()) {
                missingKinds++;
            }
        }
        return missingKinds;
    }

    private int countInInventory(@NotNull ItemStack stack) {        if (mc.player == null || stack.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack carried : mc.player.inventory.mainInventory) {
            if (!carried.isEmpty() && ItemStack.areItemsEqual(stack, carried)) {
                total += carried.getCount();
            }
        }
        for (ItemStack carried : mc.player.inventory.offHandInventory) {
            if (!carried.isEmpty() && ItemStack.areItemsEqual(stack, carried)) {
                total += carried.getCount();
            }
        }
        return total;
    }

    /**
     * One role card: the role name and {@code tally / requirement} on the first line, the inline hatch stepper on
     * the second.
     */
    private void drawRoleCard(AbilityRow row, int y, int mouseX, int mouseY) {
        int x = left + PADDING;
        int cardHeight = ROLE_CARD_HEIGHT;
        GDraw.rect(x, y, CONTENT_WIDTH, cardHeight, 0xFFE7E7E7);
        GDraw.outline(x, y, CONTENT_WIDTH, cardHeight, 0xFFB4B4B4);

        String requirement = row.requirementText();
        String tally = (listsLoaded ? String.valueOf(row.current) : "-")
                + (requirement.isEmpty() ? "" : " / " + requirement);
        int tallyWidth = GDraw.textWidth(tally);
        GDraw.textTrimmed(row.labelText(), x + 4, y + 3, CONTENT_WIDTH - 8 - tallyWidth - 4, 0xFF202020);
        int color;
        if (!listsLoaded) {
            color = 0xFF6A6A6A;
        } else if (row.min > 0) {
            color = row.satisfied() ? 0xFF2E7D32 : 0xFFC62828;
        } else {
            color = row.satisfied() ? 0xFF404040 : 0xFFC62828;
        }
        GDraw.textRight(tally, x + CONTENT_WIDTH - 4, y + 3, color);

        AbilityCountControl control = findAbilityControl(row.controlKey);
        if (control == null) {
            return;
        }
        int stepY = y + 13;
        int minusX = x + 4;
        int plusX = x + 4 + 12 + 2 + 34 + 2;
        boolean minusHovered = inside(mouseX, mouseY, minusX, stepY, 12, 12);
        GDraw.bevel(minusX, stepY, 12, 12, minusHovered ? 0xFFD2D2D2 : 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered("-", minusX + 6, stepY + 3, 0xFF202020);
        int valueX = minusX + 14;
        GDraw.rect(valueX, stepY, 34, 12, 0xFF9A9A9A);
        GDraw.outline(valueX, stepY, 34, 12, 0xFF555555);
        GDraw.textCentered(String.valueOf(control.getValue()), valueX + 17, stepY + 3, 0xFFFFFFFF);
        boolean plusHovered = inside(mouseX, mouseY, plusX, stepY, 12, 12);
        GDraw.bevel(plusX, stepY, 12, 12, plusHovered ? 0xFFD2D2D2 : 0xFFC6C6C6, 0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered("+", plusX + 6, stepY + 3, 0xFF202020);
        if (inside(mouseX, mouseY, valueX, stepY, 34, 12)) {
            GDraw.outline(valueX, stepY, 34, 12, 0xFFFFC83D);
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
        int demolishX = left + (PANEL_WIDTH - BUTTON_WIDTH) / 2;

        boolean backHovered = inside(mouseX, mouseY, leftX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT);
        GDraw.bevel(leftX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT, backHovered ? 0xFFD8D8D8 : 0xFFC6C6C6,
                0xFFFFFFFF, 0xFF8A8A8A);
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.back"), leftX + BUTTON_WIDTH / 2,
                buttonY + 3, 0xFF202020);

        if (wrapper.hasWorldController()) {
            boolean demolishHovered = inside(mouseX, mouseY, demolishX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT);
            GDraw.rect(demolishX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT,
                    demolishHovered ? 0xFFC05A5A : 0xFF9C3C3C);
            GDraw.outline(demolishX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT, 0xFF6B2020);
            GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.demolish"),
                    demolishX + BUTTON_WIDTH / 2, buttonY + 3, 0xFFFFFFFF);
        }

        boolean buildEnabled = !buildSent && canBuild();
        boolean buildHovered = buildEnabled
                && inside(mouseX, mouseY, rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT);
        GDraw.rect(rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT,
                !canBuild() ? 0xFF8E8E8E : (buildSent ? 0xFF7F9C83 : (buildHovered ? 0xFF3D8A48 : 0xFF2E7D32)));
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
            // The footer is fully occupied by the three actions, so transient feedback floats just above it.
            int statusY = buttonY - 12;
            GDraw.rect(left + PADDING, statusY - 1, CONTENT_WIDTH, 11, 0xEEC6C6C6);
            GDraw.textTrimmed(status, left + PADDING + 2, statusY + 1, CONTENT_WIDTH - 4, statusColor);
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
        if (handleRequireCompleteClick(mouseX, mouseY)) {
            return;
        }
        if (handleNoHatchClick(mouseX, mouseY)) {
            return;
        }
        if (handleNetworkStockClick(mouseX, mouseY)) {
            return;
        }
        if (handleEncodeClick(mouseX, mouseY)) {
            return;
        }
        if (handleExtraClick(mouseX, mouseY)) {
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
        if (wrapper.hasWorldController()
                && inside(mouseX, mouseY, left + (PANEL_WIDTH - BUTTON_WIDTH) / 2, buttonY,
                BUTTON_WIDTH, CONTROL_HEIGHT)) {
            requestDismantle();
            return;
        }
        if (inside(mouseX, mouseY, rightX, buttonY, BUTTON_WIDTH, CONTROL_HEIGHT)) {
            if (!buildSent && canBuild()) {
                executeBuild();
            } else if (!canBuild()) {
                resultText = I18n.format("gregtech.multiblock.build_wizard.incomplete");
                resultColor = 0xFFC62828;
            }
        }
    }

    private void requestDismantle() {
        if (!wrapper.hasWorldController()) {
            return;
        }
        NetworkHandler.INSTANCE.sendToServer(
                new CPacketDismantleStructure(wrapper.getControllerPos()));
        resultText = I18n.format("gregtech.multiblock.build_wizard.demolish_pending");
        resultColor = 0xFF6A6A6A;
    }

    private boolean handleAbilityRowClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        for (int i = 0; i < abilities.size() && i < roleRowTops.size(); i++) {
            AbilityRow row = abilities.get(i);
            AbilityCountControl control = findAbilityControl(row.controlKey);
            if (control == null) {
                continue;
            }
            int y = roleRowTops.get(i) + 13;
            int minusX = left + PADDING + 4;
            int valueX = minusX + 14;
            int plusX = valueX + 34 + 2;
            if (inside(mouseX, mouseY, minusX, y, 12, 12)) {
                wrapper.stepAbilityCount(control.getKey(), -abilityStep());
                listsLoaded = false;
                requestBill();
                return true;
            }
            if (inside(mouseX, mouseY, plusX, y, 12, 12)) {
                wrapper.stepAbilityCount(control.getKey(), abilityStep());
                listsLoaded = false;
                requestBill();
                return true;
            }
            if (inside(mouseX, mouseY, valueX, y, 34, 12)) {
                abilityPopupKey = row.controlKey;
                return true;
            }
        }
        return false;
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
        GDraw.textCentered(I18n.format("gregtech.multiblock.build_wizard.step_hint"),
                x + popupWidth / 2, y + 61, 0xFF707070);
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
            wrapper.stepAbilityCount(control.getKey(), -abilityStep());
            requestBill();
        } else if (inside(mouseX, mouseY, plusX, rowY, CONTROL_HEIGHT, CONTROL_HEIGHT)) {
            wrapper.stepAbilityCount(control.getKey(), abilityStep());
            requestBill();
        }
    }

    /** Plain click steps by one, Shift+click by eight. */
    private static int abilityStep() {
        return isShiftKeyDown() ? 8 : 1;
    }

    @NotNull
    private AbilityCountControl findAbilityControl(@Nullable String key) {
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
        // Opened from the controller directly: go back to the structure preview.
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
        SPacketStructureBuildBill.Snapshot polledBill = SPacketStructureBuildBill.takeBill();
        if (polledBill != null && polledBill.getRequestId() == billRequestId) {
            bill = polledBill;
            int previousMax = scrollMax;
            scrollMax = Math.max(0, contentHeight() - scrollHeight);
            if (scrollMax != previousMax) {
                scroll = Math.max(0, Math.min(scroll, scrollMax));
            }
        }
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
        if (!wrapper.hasWorldController() || !canBuild()) {
            return;
        }
        buildSent = true;
        missing = Collections.emptyList();
        resultText = "";
        placedCells = 0;
        handledByController = false;
        NetworkHandler.INSTANCE.sendToServer(new CPacketExecuteStructureBuild(wrapper.getControllerPos(),
                wrapper.copyPreviewChannels(), wrapper.copyCellOverrides(), useNetworkStock));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /**
     * Optional tooling pieces can be skipped through the same {@code @exclude:} channel the preview config card
     * writes.
     */
    private void drawExtraRow(ExtraRow extra, int y, int mouseX, int mouseY) {
        boolean excluded = wrapper.getPreviewChannelValue(StructureExclusions.key(extra.index)) != 0;
        int boxX = left + PADDING + 2;
        GDraw.rect(boxX, y + 3, 10, 10, 0xFFFFFFFF);
        GDraw.outline(boxX, y + 3, 10, 10, excluded ? 0xFF8A8A8A : 0xFF2E7D32);
        if (!excluded) {
            GDraw.rect(boxX + 2, y + 5, 6, 6, 0xFF2E7D32);
        }
        GDraw.textTrimmed(extra.name, boxX + 16, y + 5, CONTENT_WIDTH - 20,
                excluded ? 0xFF8A8A8A : 0xFF202020);
    }

    private boolean handleExtraClick(int mouseX, int mouseY) {
        if (mouseY < scrollTop || mouseY >= scrollTop + scrollHeight) {
            return false;
        }
        for (int i = 0; i < extras.size() && i < extraRowTops.size(); i++) {
            int y = extraRowTops.get(i);
            if (inside(mouseX, mouseY, left + PADDING + 2, y + 2, 12, 12)) {
                ExtraRow extra = extras.get(i);
                String key = StructureExclusions.key(extra.index);
                boolean excluded = wrapper.getPreviewChannelValue(key) != 0;
                wrapper.setPreviewChannel(key, excluded ? 0 : 1);
                listsLoaded = false;
                requestBill();
                return true;
            }
        }
        return false;
    }

    private int contentHeight() {
        int height = 4 * ROW;
        height += SECTION_GAP + ROW + Math.max(1, parts.size()) * ROW;
        if (bill != null && !bill.getRequiredItems().isEmpty()) {
            height += ROW;
            if (!bill.getMissingItems().isEmpty() && !bill.isNetworkLinked()) {
                height += ROW;
            }
        }
        if (bill != null && !bill.getRoleItems().isEmpty()) {
            height += ROW + roleBillRowCount() * ROW;
        }
        height += SECTION_GAP + ROW + Math.max(1, extras.size()) * ROW;
        height += SECTION_GAP + ROW + Math.max(1, abilities.size()) * ROLE_CARD_HEIGHT;
        if (!missing.isEmpty()) {
            height += SECTION_GAP + ROW + missing.size() * ROW;
        }
        return height;
    }

    private int roleBillRowCount() {
        int lines = 0;
        for (List<ItemStack> items : bill.getRoleItems().values()) {
            lines += items.size();
            if (lines >= MAX_ROLE_BILL_LINES) {
                return MAX_ROLE_BILL_LINES + 1;
            }
        }
        return lines;
    }

    private String roleBillLabel(String key) {
        for (AbilityRow row : abilities) {
            if (key.equals(row.controlKey)) {
                return row.labelText();
            }
        }
        int idx = key.lastIndexOf(':');
        return idx >= 0 && idx + 1 < key.length() ? key.substring(idx + 1) : key;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static final class ExtraRow {

        private final int index;
        private final String name;

        private ExtraRow(int index, String name) {
            this.index = index;
            this.name = name;
        }
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
