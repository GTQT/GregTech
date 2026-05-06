package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.pattern.MultiblockState;
import gregtech.api.pattern.PatternError;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.GTUtility;

import com.cleanroommc.modularui.widgets.layout.Flow;
import gregtech.client.renderer.handler.MultiblockPreviewRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified structure projector behavior that combines:
 * - Structure hologram preview (right-click controller)
 * - Auto-building (shift+right-click controller)
 * - Comparison mode (shows missing/wrong blocks)
 * - Channel configuration (select tier for auto-build)
 * - Nearby container search for survival mode building
 *
 * <p>Replaces the separate RenderItemBehavior and MultiblockBuilderBehavior.
 */
public class StructureProjectorBehavior implements IItemBehaviour, ItemUIFactory {

    // --- Persistent state ---
    private boolean compareModeEnabled = true;
    private boolean noHatch = false;
    private final Map<String, Integer> channelValues = new HashMap<>();

    // --- Client-side preview state ---
    private List<StructureChannel> supportedChannels = new ArrayList<>();

    private static final int MAX_CHANNEL_ROWS = 12;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 18;

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            // Not a GT tile — open configuration GUI
            if (!world.isRemote) {
                MetaItemGuiFactory.open(player, hand);
            }
            return EnumActionResult.SUCCESS;
        }

        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(mte instanceof MultiblockControllerBase)) return EnumActionResult.PASS;
        MultiblockControllerBase multiblock = (MultiblockControllerBase) mte;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;

        if (player.isSneaking()) {
            // Shift+right-click: Auto-build the structure
            if (world.isRemote) return EnumActionResult.SUCCESS;

            if (!multiblock.isStructureFormed()) {
                Map<String, Integer> channels = channelValues.isEmpty() ? null : channelValues;
                MultiblockState state = multiblock.getMultiblockState();
                if (state != null) {
                    state.autoBuild(player, multiblock, channels, noHatch);
                }
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.PASS;
        } else {
            // Right-click: Show hologram preview / error info
            if (world.isRemote) {
                MultiblockPreviewRenderer.setCompareMode(compareModeEnabled);
                supportedChannels = multiblock.getSupportedChannels();
                MultiblockPreviewRenderer.renderMultiBlockPreview(multiblock, 10000);
                return EnumActionResult.SUCCESS;
            }

            // Server-side: show error info if structure is not formed
            if (!multiblock.isStructureFormed()) {
                MultiblockState state = multiblock.getMultiblockState();
                PatternError error = state != null ? state.getError() : null;
                if (error != null) {
                    player.sendMessage(new TextComponentString("============================"));
                    player.sendMessage(
                            new TextComponentTranslation("gregtech.multiblock.pattern.error_message_header"));
                    for (List<ItemStack> stack : error.getCandidates()) {
                        player.sendMessage(new TextComponentString(
                                TextFormatting.RED + "  " + stack.get(0).getDisplayName()));
                    }
                    player.sendMessage(new TextComponentString(
                            TextFormatting.GRAY + "  @ " + error.getPosString(error.getPos())));
                    player.sendMessage(new TextComponentString(
                            TextFormatting.YELLOW + "  " + error.getErrorInfo()));
                    player.sendMessage(new TextComponentString("============================"));
                    return EnumActionResult.SUCCESS;
                }
            }
            player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                    .setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)));
            return EnumActionResult.SUCCESS;
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public void addPropertyOverride(@NotNull Item item) {
        item.addPropertyOverride(GTUtility.gregtechId("auto_mode"),
                (stack, world, entity) -> (entity != null && entity.isSneaking()) ? 1.0F : 0.0F);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("gregtech.tool.projector.tooltip1"));
        lines.add(I18n.format("gregtech.tool.projector.tooltip2"));
        lines.add(I18n.format("gregtech.tool.projector.tooltip3"));
        if (compareModeEnabled) {
            lines.add(TextFormatting.GREEN + I18n.format("gregtech.tool.projector.compare_on"));
        }
        if (noHatch) {
            lines.add(TextFormatting.RED + I18n.format("gregtech.tool.projector.no_hatch_on"));
        }
        if (!channelValues.isEmpty()) {
            lines.add(TextFormatting.YELLOW + I18n.format("gregtech.tool.projector.channels_set",
                    channelValues.size()));
        }
        lines.add(TextFormatting.GRAY + I18n.format("gregtech.tool.projector.channel_config_hint"));
    }

    // --- GUI ---

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        var panel = GTGuis.createPanel(guiData.getUsedItemStack(), 176, 220);

        BooleanSyncValue compareModeValue = new BooleanSyncValue(
                () -> compareModeEnabled, v -> compareModeEnabled = v);
        guiSyncManager.syncValue("compare_mode", compareModeValue);

        BooleanSyncValue noHatchValue = new BooleanSyncValue(
                () -> noHatch, v -> noHatch = v);
        guiSyncManager.syncValue("no_hatch", noHatchValue);

        IntSyncValue heightValue = new IntSyncValue(
                () -> channelValues.getOrDefault(GTStructureChannels.STRUCTURE_HEIGHT.getName(), 0),
                v -> {
                    if (v <= 0) channelValues.remove(GTStructureChannels.STRUCTURE_HEIGHT.getName());
                    else channelValues.put(GTStructureChannels.STRUCTURE_HEIGHT.getName(), v);
                });
        guiSyncManager.syncValue("structure_height", heightValue);

        IntSyncValue lengthValue = new IntSyncValue(
                () -> channelValues.getOrDefault(GTStructureChannels.STRUCTURE_LENGTH.getName(), 0),
                v -> {
                    if (v <= 0) channelValues.remove(GTStructureChannels.STRUCTURE_LENGTH.getName());
                    else channelValues.put(GTStructureChannels.STRUCTURE_LENGTH.getName(), v);
                });
        guiSyncManager.syncValue("structure_length", lengthValue);

        List<ChannelEntry> entries = buildChannelEntries();

        StringSyncValue[] nameSyncs = new StringSyncValue[MAX_CHANNEL_ROWS];
        IntSyncValue[] valueSyncs = new IntSyncValue[MAX_CHANNEL_ROWS];

        for (int i = 0; i < MAX_CHANNEL_ROWS; i++) {
            final int idx = i;
            nameSyncs[i] = new StringSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).name : "",
                    n -> updateEntryName(entries, idx, n != null ? n : ""));
            valueSyncs[i] = new IntSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).value : 0,
                    v -> updateEntryValue(entries, idx, v));
            guiSyncManager.syncValue("ch_name_" + i, nameSyncs[i]);
            guiSyncManager.syncValue("ch_val_" + i, valueSyncs[i]);
        }

        int visibleRows = Math.max(entries.size(), 1);
        int listHeight = Math.min(visibleRows, MAX_VISIBLE_ROWS) * ROW_HEIGHT;

        var channelList = new ListWidget<>()
                .children(visibleRows, i -> Flow.row()
                        .widthRel(1f)
                        .height(ROW_HEIGHT - 2)
                        .child(new TextFieldWidget()
                                .width(100)
                                .height(12)
                                .setTextColor(Color.WHITE.darker(1))
                                .setMaxLength(32)
                                .value(nameSyncs[i])
                                .background(GTGuiTextures.DISPLAY))
                        .child(new TextFieldWidget()
                                .width(40)
                                .height(12)
                                .setTextColor(Color.WHITE.darker(1))
                                .setNumbers(0, 100)
                                .value(valueSyncs[i])
                                .background(GTGuiTextures.DISPLAY)))
                .scrollDirection(new VerticalScrollData())
                .size(162, listHeight)
                .pos(7, 85);

        var clearButton = new ButtonWidget<>()
                .pos(7, 195)
                .width(60).height(16)
                .overlay(IKey.lang("gregtech.tool.projector.clear"))
                .onMousePressed(m -> {
                    channelValues.clear();
                    entries.clear();
                    return true;
                });

        return panel
                .child(IKey.lang("gregtech.tool.projector.gui_title").asWidget().pos(5, 5))
                .child(new ToggleButton()
                        .pos(15, 20)
                        .width(60).height(18)
                        .value(compareModeValue)
                        .tooltip(tooltip -> tooltip.addLine(
                                IKey.lang("gregtech.tool.projector.compare_tooltip")))
                        .onUpdateListener(w -> w.overlay(IKey.str(
                                compareModeValue.getValue() ? "CMP:ON" : "CMP:OFF"))))
                .child(new ToggleButton()
                        .pos(85, 20)
                        .width(60).height(18)
                        .value(noHatchValue)
                        .tooltip(tooltip -> tooltip.addLine(
                                IKey.lang("gregtech.tool.projector.no_hatch_tooltip")))
                        .onUpdateListener(w -> w.overlay(IKey.str(
                                noHatchValue.getValue() ? "NO HATCH" : "HATCH"))))
                .child(IKey.lang("gregtech.tool.projector.structure_height").asWidget().pos(7, 42))
                .child(new TextFieldWidget()
                        .pos(80, 42)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(0, 100)
                        .value(heightValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(IKey.lang("gregtech.tool.projector.structure_length").asWidget().pos(7, 62))
                .child(new TextFieldWidget()
                        .pos(80, 62)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(0, 100)
                        .value(lengthValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(channelList)
                .child(clearButton);
    }

    private List<ChannelEntry> buildChannelEntries() {
        List<ChannelEntry> entries = new ArrayList<>();

        if (channelValues.isEmpty() && !supportedChannels.isEmpty()) {
            autoFillFromSupported(entries);
            return entries;
        }

        for (Map.Entry<String, Integer> e : channelValues.entrySet()) {
            if (e.getKey().equals(GTStructureChannels.STRUCTURE_HEIGHT.getName())) continue;
            if (e.getKey().equals(GTStructureChannels.STRUCTURE_LENGTH.getName())) continue;
            entries.add(new ChannelEntry(e.getKey(), e.getValue()));
        }

        return entries;
    }

    private void autoFillFromSupported(List<ChannelEntry> entries) {
        for (StructureChannel ch : supportedChannels) {
            String name = ch.getName();
            if (name.equals(GTStructureChannels.STRUCTURE_HEIGHT.getName())) continue;
            if (name.equals(GTStructureChannels.STRUCTURE_LENGTH.getName())) continue;
            entries.add(new ChannelEntry(name, 0));
            channelValues.put(name, 0);
        }
    }

    private void updateEntryName(List<ChannelEntry> entries, int idx, String name) {
        while (entries.size() <= idx) {
            entries.add(new ChannelEntry("", 0));
        }
        String oldName = entries.get(idx).name;
        entries.get(idx).name = name;

        if (oldName != null && !oldName.isEmpty()) {
            channelValues.remove(oldName);
        }
        if (name != null && !name.isEmpty()) {
            channelValues.put(name, entries.get(idx).value);
        }
    }

    private void updateEntryValue(List<ChannelEntry> entries, int idx, int value) {
        while (entries.size() <= idx) {
            entries.add(new ChannelEntry("", 0));
        }
        entries.get(idx).value = value;

        String name = entries.get(idx).name;
        if (name != null && !name.isEmpty()) {
            channelValues.put(name, value);
        }
    }

    private static class ChannelEntry {

        String name;
        int value;

        ChannelEntry(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    // --- Getters/Setters ---

    public Map<String, Integer> getChannelValues() {
        return channelValues;
    }

    public void setChannelValue(String channelName, int value) {
        channelValues.put(channelName, value);
    }

    public void clearChannelValues() {
        channelValues.clear();
    }
}
