package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureFailureReport;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.GTLog;
import gregtech.client.renderer.handler.GhostBlockRenderer;
import gregtech.common.ConfigHolder;
import gregtech.common.items.behaviors.multiblock.ChannelSettings;
import gregtech.common.items.behaviors.multiblock.MultiblockToolNbt;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
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
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构投影行为：全息预览、自动建造、比较模式、通道配置。
 *
 * <p>右键控制器显示预览并报告结构问题；潜行右键控制器按当前通道配置自动建造
 * （{@code structure_piece} 通道 > 0 时只补该分段）；右键非 GT 方块或右键空气打开配置面板。
 *
 * <p>本类无状态：所有持久化数据都在 ItemStack 的 NBT 中（见 {@link ChannelSettings}），
 * 方法内的局部变量只是单次调用的工作副本。
 */
public class MultiblockBuilderBehavior implements IItemBehaviour, ItemUIFactory {

    private static final int MAX_CHANNEL_ROWS = 12;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 18;

    /** Hint appended to a structure failure report, pointing at this tool's auto-build mode. */
    private static final String AUTO_BUILD_ADVICE = "gregtech.multiblock.pattern.failure.advice.build";

    // --- Compare mode (item-scoped, not a structure channel) ---

    private static boolean readCompareMode(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(MultiblockToolNbt.COMPARE_MODE)) return true;
        return tag.getBoolean(MultiblockToolNbt.COMPARE_MODE);
    }

    private static void writeCompareMode(@NotNull ItemStack stack, boolean enabled) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(MultiblockToolNbt.COMPARE_MODE, enabled);
    }

    // --- Item use actions ---

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack heldStack = player.getHeldItem(hand);

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

        ChannelSettings settings = ChannelSettings.load(heldStack);

        if (player.isSneaking()) {
            // Shift+right-click: Auto-build the structure
            if (world.isRemote) return EnumActionResult.SUCCESS;

            Map<String, Integer> channels = settings.asRequestChannels();

            // Check if a specific piece is requested via STRUCTURE_PIECE channel
            int pieceIndex = settings.get(GTStructureChannels.STRUCTURE_PIECE.getName());
            if (pieceIndex > 0) {
                buildPiece(multiblock, player, pieceIndex, channels, heldStack);
                return EnumActionResult.SUCCESS;
            }

            if (!multiblock.isStructureFormed()) {
                StructureOperationRequest request = StructureOperationRequest.build(
                        player, multiblock, StructureOrientation.fromController(multiblock),
                        channels, heldStack);
                if (multiblock.autoBuildStructure(request)) {
                    return EnumActionResult.SUCCESS;
                }
                buildStructure(multiblock, request);
                return EnumActionResult.SUCCESS;
            }
            return EnumActionResult.PASS;
        } else {
            // Right-click: Show hologram preview / error info
            if (world.isRemote) {
                GhostBlockRenderer.setCompareMode(readCompareMode(heldStack));
                GhostBlockRenderer.setChannelValues(settings.values());
                GhostBlockRenderer.renderGhostPreview(multiblock, 10000);
                return EnumActionResult.SUCCESS;
            }

            // Server-side: store supported channel ranges into NBT for GUI use
            saveControllerChannelRanges(settings, multiblock);

            if (multiblock.isStructureFormed()) {
                spawnStructureHints(multiblock, player, heldStack, settings.asRequestChannels());
                player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                        .setStyle(new Style().setColor(TextFormatting.GREEN)));
                return EnumActionResult.SUCCESS;
            }

            // Report the failure before spawning hints: hint generation re-runs pattern
            // matching, which recycles the shared block-state buffer the live error points at.
            StructureFailureReport.sendTo(player, multiblock, world, AUTO_BUILD_ADVICE);
            spawnStructureHints(multiblock, player, heldStack, settings.asRequestChannels());
            return EnumActionResult.SUCCESS;
        }
    }

    /**
     * Save the controller's supported channel names and their valid ranges into the item NBT.
     * This allows the GUI to show proper min/max ranges for each channel.
     */
    private static void saveControllerChannelRanges(@NotNull ChannelSettings settings,
                                                    @NotNull MultiblockControllerBase controller) {
        List<StructureChannel> supported = controller.getSupportedChannels();
        Map<String, int[]> ranges = new HashMap<>();
        for (StructureChannel ch : supported) {
            ranges.put(ch.getName(), controller.getChannelRange(ch));
        }
        if (ConfigHolder.machines.debugStructureTrace) {
            GTLog.logger.debug("[StructureProjector] saved channel ranges controller={} ranges={}",
                    controller.getMetaName(), ranges.keySet());
        }
        settings.setSupportedRanges(ranges);
        settings.store();
    }

    private static void buildStructure(@NotNull MultiblockControllerBase multiblock,
                                       @NotNull StructureOperationRequest request) {
        var runtime = multiblock.getOrCreateStructureRuntime();
        StructureBuildResult result = runtime.buildAllPieces(request);
        logBuildResult(multiblock, 0, request.getChannelValues(), result);
    }

    private static void buildPiece(@NotNull MultiblockControllerBase multiblock,
                                   @NotNull EntityPlayer player,
                                   int pieceIndex,
                                   Map<String, Integer> channels,
                                   @NotNull ItemStack triggerStack) {
        var runtime = multiblock.getOrCreateStructureRuntime();
        MultiPiecePattern pattern = runtime.getMultiPiecePattern();
        int compiledPieceIndex = pattern == null ? pieceIndex : pattern.resolveToolingPieceIndex(pieceIndex);
        if (compiledPieceIndex < 1) {
            GTLog.logger.debug(
                    "[StructureProjector] skipped invalid structure_piece={} for controller={}",
                    pieceIndex, multiblock.getMetaName());
            return;
        }
        StructureBuildResult result = runtime.buildPiece(StructureOperationRequest.buildPiece(
                compiledPieceIndex, player, multiblock, StructureOrientation.fromController(multiblock),
                channels, triggerStack));
        logBuildResult(multiblock, compiledPieceIndex, channels, result);
    }

    private static void logBuildResult(@NotNull MultiblockControllerBase multiblock,
                                       int pieceIndex,
                                       @Nullable Map<String, Integer> channelValues,
                                       @NotNull StructureBuildResult result) {
        GTLog.logger.info("[StructureProjector] build result controller={} pos={} piece={} noHatch={}, {}",
                multiblock.getMetaName(), multiblock.getPos(), pieceIndex,
                channelValues != null && StructureOperationRequest.isNoHatch(channelValues),
                result.describeCounts());
    }

    private static void spawnStructureHints(@NotNull MultiblockControllerBase multiblock,
                                            @NotNull EntityPlayer player,
                                            @NotNull ItemStack triggerStack,
                                            @Nullable Map<String, Integer> channels) {
        long start = System.nanoTime();
        StructureOperationRequest request = StructureOperationRequest.hint(
                player, multiblock, StructureOrientation.fromController(multiblock),
                channels, triggerStack);
        multiblock.spawnStructureHints(request);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
        if (ConfigHolder.machines.debugStructureTrace && elapsedMillis >= 25L) {
            GTLog.logger.debug(
                    "[StructureProjector] slow server hints controller={} channels={} totalMs={}",
                    multiblock.getMetaName(), channels, elapsedMillis);
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
    public void addInformation(ItemStack itemStack, List<String> lines) {
        ChannelSettings settings = ChannelSettings.load(itemStack);
        if (readCompareMode(itemStack)) {
            lines.add(TextFormatting.GREEN + I18n.format("gregtech.tool.multiblock_builder.compare_on"));
        }
        if (settings.isNoHatch()) {
            lines.add(TextFormatting.RED + I18n.format("gregtech.tool.multiblock_builder.no_hatch_on"));
        }
        if (!settings.values().isEmpty()) {
            lines.add(TextFormatting.YELLOW + I18n.format("gregtech.tool.multiblock_builder.channels_set",
                    settings.values().size()));
        }
        lines.add(TextFormatting.GRAY + I18n.format("gregtech.tool.multiblock_builder.channel_config_hint"));
    }

    // --- GUI ---

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();

        // Read all state from NBT into local working copies
        boolean[] compareHolder = { readCompareMode(stack) };
        ChannelSettings channels = ChannelSettings.load(stack);
        List<ChannelEntry> entries = buildChannelEntries(channels);
        if (ConfigHolder.machines.debugStructureTrace) {
            GTLog.logger.debug("[StructureProjector] building channel GUI values={} ranges={} entries={}",
                    channels.values().keySet(), channels.supportedRanges().keySet(), entries.size());
        }

        var panel = GTGuis.createPanel(stack, 176, 240);

        // --- Compare mode sync ---
        BooleanSyncValue compareModeValue = new BooleanSyncValue(
                () -> compareHolder[0],
                v -> {
                    compareHolder[0] = v;
                    writeCompareMode(stack, v);
                });
        guiSyncManager.syncValue("compare_mode", compareModeValue);

        // --- No-hatch mode sync ---
        BooleanSyncValue noHatchValue = new BooleanSyncValue(
                channels::isNoHatch,
                v -> {
                    channels.setNoHatch(v);
                    channels.store();
                });
        guiSyncManager.syncValue("no_hatch", noHatchValue);

        // --- Structure width/height/length sync ---
        int[] widthRange = channels.supportedRanges().getOrDefault(
                GTStructureChannels.STRUCTURE_WIDTH.getName(), new int[] { 0, 100 });
        int[] heightRange = channels.supportedRanges().getOrDefault(
                GTStructureChannels.STRUCTURE_HEIGHT.getName(), new int[] { 0, 100 });
        int[] lengthRange = channels.supportedRanges().getOrDefault(
                GTStructureChannels.STRUCTURE_LENGTH.getName(), new int[] { 0, 100 });

        IntSyncValue widthValue = new IntSyncValue(
                () -> channels.get(GTStructureChannels.STRUCTURE_WIDTH.getName()),
                v -> {
                    channels.put(GTStructureChannels.STRUCTURE_WIDTH.getName(), v);
                    channels.store();
                });
        guiSyncManager.syncValue("structure_width", widthValue);

        IntSyncValue heightValue = new IntSyncValue(
                () -> channels.get(GTStructureChannels.STRUCTURE_HEIGHT.getName()),
                v -> {
                    channels.put(GTStructureChannels.STRUCTURE_HEIGHT.getName(), v);
                    channels.store();
                });
        guiSyncManager.syncValue("structure_height", heightValue);

        IntSyncValue lengthValue = new IntSyncValue(
                () -> channels.get(GTStructureChannels.STRUCTURE_LENGTH.getName()),
                v -> {
                    channels.put(GTStructureChannels.STRUCTURE_LENGTH.getName(), v);
                    channels.store();
                });
        guiSyncManager.syncValue("structure_length", lengthValue);

        // --- Channel entries sync ---
        StringSyncValue[] nameSyncs = new StringSyncValue[MAX_CHANNEL_ROWS];
        IntSyncValue[] valueSyncs = new IntSyncValue[MAX_CHANNEL_ROWS];

        for (int i = 0; i < MAX_CHANNEL_ROWS; i++) {
            final int idx = i;
            nameSyncs[i] = new StringSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).name : "",
                    n -> updateEntryName(channels, entries, idx, n != null ? n : ""));
            valueSyncs[i] = new IntSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).value : 0,
                    v -> updateEntryValue(channels, entries, idx, v));
            guiSyncManager.syncValue("ch_name_" + i, nameSyncs[i]);
            guiSyncManager.syncValue("ch_val_" + i, valueSyncs[i]);
        }

        int visibleRows = Math.max(entries.size(), 1);
        int listHeight = Math.min(visibleRows, MAX_VISIBLE_ROWS) * ROW_HEIGHT;

        // Determine valid ranges for channel entry value fields
        var channelList = new ListWidget<>()
                .children(visibleRows, i -> {
                    int[] range = getEntryRange(channels, entries, i);
                    return Flow.row()
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
                                    .setNumbers(range[0], range[1])
                                    .value(valueSyncs[i])
                                    .background(GTGuiTextures.DISPLAY));
                })
                .scrollDirection(new VerticalScrollData())
                .size(162, listHeight)
                .pos(7, 105);

        // --- Clear button: properly resets all sync values ---
        var clearButton = new ButtonWidget<>()
                .pos(7, 215)
                .width(60).height(16)
                .overlay(IKey.lang("gregtech.tool.multiblock_builder.clear"))
                .onMousePressed(m -> {
                    // Clear all channel values and entries
                    channels.clear();
                    entries.clear();
                    // Reset sync values so GUI and server reflect the cleared state
                    widthValue.setValue(0, true, true);
                    heightValue.setValue(0, true, true);
                    lengthValue.setValue(0, true, true);
                    for (int i = 0; i < MAX_CHANNEL_ROWS; i++) {
                        nameSyncs[i].setValue("", true, true);
                        valueSyncs[i].setValue(0, true, true);
                    }
                    return true;
                });

        return panel
                .child(IKey.lang("gregtech.tool.multiblock_builder.gui_title").asWidget().pos(5, 5))
                .child(new ToggleButton()
                        .pos(15, 20)
                        .width(60).height(18)
                        .value(compareModeValue)
                        .tooltip(tooltip -> tooltip.addLine(
                                IKey.lang("gregtech.tool.multiblock_builder.compare_tooltip")))
                        .onUpdateListener(w -> w.overlay(IKey.str(
                                compareModeValue.getValue() ? "CMP:ON" : "CMP:OFF"))))
                .child(new ToggleButton()
                        .pos(85, 20)
                        .width(60).height(18)
                        .value(noHatchValue)
                        .tooltip(tooltip -> tooltip.addLine(
                                IKey.lang("gregtech.tool.multiblock_builder.no_hatch_tooltip")))
                        .onUpdateListener(w -> w.overlay(IKey.str(
                                noHatchValue.getValue() ? "NO HATCH" : "HATCH"))))
                .child(IKey.lang("gregtech.tool.multiblock_builder.structure_height").asWidget().pos(7, 42))
                .child(new TextFieldWidget()
                        .pos(80, 42)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(heightRange[0], heightRange[1])
                        .value(heightValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(IKey.lang("gregtech.tool.multiblock_builder.structure_width").asWidget().pos(7, 62))
                .child(new TextFieldWidget()
                        .pos(80, 62)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(widthRange[0], widthRange[1])
                        .value(widthValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(IKey.lang("gregtech.tool.multiblock_builder.structure_length").asWidget().pos(7, 82))
                .child(new TextFieldWidget()
                        .pos(80, 82)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(lengthRange[0], lengthRange[1])
                        .value(lengthValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(channelList)
                .child(clearButton);
    }

    /**
     * Build the channel entries list for the GUI from persisted channel values.
     * Excludes height/length which have dedicated controls.
     * If no values are stored but ranges exist (from a previous controller interaction),
     * auto-fills entries with default value 0 for each known channel.
     */
    private static List<ChannelEntry> buildChannelEntries(@NotNull ChannelSettings channels) {
        List<ChannelEntry> entries = new ArrayList<>();

        // Build entries from existing channel values first, preserving configured values.
        for (Map.Entry<String, Integer> e : channels.values().entrySet()) {
            if (isDedicatedDimensionChannel(e.getKey())) continue;
            entries.add(new ChannelEntry(e.getKey(), e.getValue()));
        }

        // Then append supported channels discovered from the last clicked controller.
        for (String name : channels.supportedRanges().keySet()) {
            if (isDedicatedDimensionChannel(name) || containsEntry(entries, name)) continue;
            entries.add(new ChannelEntry(name, channels.get(name)));
        }

        return entries;
    }

    private static boolean isDedicatedDimensionChannel(@NotNull String name) {
        return name.equals(GTStructureChannels.STRUCTURE_WIDTH.getName()) ||
                name.equals(GTStructureChannels.STRUCTURE_HEIGHT.getName()) ||
                name.equals(GTStructureChannels.STRUCTURE_LENGTH.getName());
    }

    private static boolean containsEntry(@NotNull List<ChannelEntry> entries, @NotNull String name) {
        for (ChannelEntry entry : entries) {
            if (name.equals(entry.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the valid value range for a channel entry by looking up stored ranges.
     * Falls back to [0, 100] if no range is stored for the entry's channel name.
     */
    private static int[] getEntryRange(@NotNull ChannelSettings channels,
                                       List<ChannelEntry> entries,
                                       int idx) {
        if (idx < entries.size()) {
            String name = entries.get(idx).name;
            if (name != null && !name.isEmpty() && channels.supportedRanges().containsKey(name)) {
                return channels.supportedRanges().get(name);
            }
        }
        return new int[] { 0, 100 };
    }

    /**
     * Update a channel entry name and persist it.
     */
    private static void updateEntryName(@NotNull ChannelSettings channels,
                                        List<ChannelEntry> entries,
                                        int idx,
                                        String name) {
        while (entries.size() <= idx) {
            entries.add(new ChannelEntry("", 0));
        }
        String oldName = entries.get(idx).name;
        entries.get(idx).name = name;

        if (oldName != null && !oldName.isEmpty()) {
            channels.put(oldName, 0);
        }
        if (name != null && !name.isEmpty()) {
            channels.put(name, entries.get(idx).value);
        }
        channels.store();
    }

    /**
     * Update a channel entry value and persist it.
     */
    private static void updateEntryValue(@NotNull ChannelSettings channels,
                                         List<ChannelEntry> entries,
                                         int idx,
                                         int value) {
        while (entries.size() <= idx) {
            entries.add(new ChannelEntry("", 0));
        }
        entries.get(idx).value = value;

        String name = entries.get(idx).name;
        if (name != null && !name.isEmpty()) {
            channels.put(name, value);
        }
        channels.store();
    }

    // --- Inner data class ---

    private static class ChannelEntry {

        String name;
        int value;

        ChannelEntry(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }
}
