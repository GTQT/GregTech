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
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.casing.GTStructureChannels;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
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
 * Structure builder behavior that handles auto-building multiblock structures.
 *
 * <p>Shift+right-click a multiblock controller to auto-build the structure using
 * resources from the player's inventory or nearby containers.
 *
 * <p>Right-click in air to open the channel configuration GUI where you can set
 * structure dimensions, channel tiers, and toggle no-hatch mode.
 *
 * <p>All persistent data is stored in the ItemStack's NBT.
 */
public class StructureBuilderBehavior implements IItemBehaviour, ItemUIFactory {

    // --- State keys in ItemStack NBT ---
    private static final String NBT_CHANNELS = "BuilderChannels";
    private static final String NBT_CHANNEL_RANGES = "BuilderChannelRanges";

    private static final int MAX_CHANNEL_ROWS = 12;
    private static final int MAX_VISIBLE_ROWS = 6;
    private static final int ROW_HEIGHT = 18;

    // --- Static NBT read/write helpers ---

    @NotNull
    private static Map<String, Integer> readChannelValues(@NotNull ItemStack stack) {
        Map<String, Integer> result = new HashMap<>();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_CHANNELS)) return result;
        NBTTagCompound channels = tag.getCompoundTag(NBT_CHANNELS);
        for (String key : channels.getKeySet()) {
            result.put(key, channels.getInteger(key));
        }
        return result;
    }

    @NotNull
    private static Map<String, int[]> readChannelRanges(@NotNull ItemStack stack) {
        Map<String, int[]> result = new HashMap<>();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_CHANNEL_RANGES)) return result;
        NBTTagCompound ranges = tag.getCompoundTag(NBT_CHANNEL_RANGES);
        for (String key : ranges.getKeySet()) {
            int[] range = ranges.getIntArray(key);
            if (range.length == 2) {
                result.put(key, range);
            }
        }
        return result;
    }

    private static void writeChannelValues(@NotNull ItemStack stack, @NotNull Map<String, Integer> channelValues) {
        NBTTagCompound tag = getOrCreateTag(stack);
        NBTTagCompound channels = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : channelValues.entrySet()) {
            if (entry.getValue() != 0) {
                channels.setInteger(entry.getKey(), entry.getValue());
            }
        }
        if (!channels.isEmpty()) {
            tag.setTag(NBT_CHANNELS, channels);
        } else {
            tag.removeTag(NBT_CHANNELS);
        }
    }

    private static boolean isNoHatch(@NotNull Map<String, Integer> channelValues) {
        return StructureOperationRequest.isNoHatch(channelValues);
    }

    private static void setNoHatch(@NotNull Map<String, Integer> channelValues, boolean enabled) {
        if (enabled) {
            channelValues.put(GTStructureChannels.NO_HATCH.getName(), 1);
        } else {
            channelValues.remove(GTStructureChannels.NO_HATCH.getName());
        }
    }

    private static void writeChannelRanges(@NotNull ItemStack stack, @NotNull Map<String, int[]> ranges) {
        NBTTagCompound tag = getOrCreateTag(stack);
        if (ranges.isEmpty()) {
            tag.removeTag(NBT_CHANNEL_RANGES);
            return;
        }
        NBTTagCompound rangesTag = new NBTTagCompound();
        for (Map.Entry<String, int[]> entry : ranges.entrySet()) {
            rangesTag.setIntArray(entry.getKey(), entry.getValue());
        }
        tag.setTag(NBT_CHANNEL_RANGES, rangesTag);
    }

    @NotNull
    private static NBTTagCompound getOrCreateTag(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        return tag;
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

        Map<String, Integer> channelValues = readChannelValues(heldStack);

        if (player.isSneaking()) {
            // Shift+right-click: Auto-build the structure
            if (world.isRemote) return EnumActionResult.SUCCESS;

            Map<String, Integer> channels = channelValues.isEmpty() ? null : channelValues;

            // Check if a specific piece is requested via STRUCTURE_PIECE channel
            int pieceIndex = channelValues.getOrDefault(GTStructureChannels.STRUCTURE_PIECE.getName(), 0);
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
            // Right-click: Save channel ranges for GUI configuration
            if (!world.isRemote) {
                saveControllerChannelRanges(heldStack, multiblock);
                if (multiblock.isStructureFormed()) {
                    player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                            .setStyle(new net.minecraft.util.text.Style().setColor(TextFormatting.GREEN)));
                } else {
                    player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.error_message_header"));
                }
            }
            return EnumActionResult.SUCCESS;
        }
    }

    private static void saveControllerChannelRanges(@NotNull ItemStack stack,
                                                     @NotNull MultiblockControllerBase controller) {
        List<StructureChannel> supported = controller.getSupportedChannels();
        Map<String, int[]> ranges = new HashMap<>();
        for (StructureChannel ch : supported) {
            int[] range = controller.getChannelRange(ch);
            ranges.put(ch.getName(), range);
        }
        if (ConfigHolder.machines.debugStructureTrace) {
            GTLog.logger.debug("[StructureBuilder] saved channel ranges controller={} ranges={}",
                    controller.getMetaName(), ranges.keySet());
        }
        writeChannelRanges(stack, ranges);
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
                    "[StructureBuilder] skipped invalid structure_piece={} for controller={}",
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
        GTLog.logger.info("[StructureBuilder] build result controller={} pos={} piece={} noHatch={}, {}",
                multiblock.getMetaName(), multiblock.getPos(), pieceIndex,
                channelValues != null && isNoHatch(channelValues), result.describeCounts());
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
        Map<String, Integer> channelValues = readChannelValues(itemStack);

        lines.add(I18n.format("gregtech.tool.builder.tooltip1"));
        lines.add(I18n.format("gregtech.tool.builder.tooltip2"));
        if (isNoHatch(channelValues)) {
            lines.add(TextFormatting.RED + I18n.format("gregtech.tool.builder.no_hatch_on"));
        }
        if (!channelValues.isEmpty()) {
            lines.add(TextFormatting.YELLOW + I18n.format("gregtech.tool.builder.channels_set",
                    channelValues.size()));
        }
        lines.add(TextFormatting.GRAY + I18n.format("gregtech.tool.builder.channel_config_hint"));
    }

    // --- GUI ---

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();

        // Read all state from NBT into local working copies
        Map<String, Integer> channelValues = readChannelValues(stack);
        Map<String, int[]> channelRanges = readChannelRanges(stack);
        List<ChannelEntry> entries = buildChannelEntries(channelValues, channelRanges);
        if (ConfigHolder.machines.debugStructureTrace) {
            GTLog.logger.debug("[StructureBuilder] building channel GUI values={} ranges={} entries={}",
                    channelValues.keySet(), channelRanges.keySet(), entries.size());
        }

        var panel = GTGuis.createPanel(stack, 176, 220);

        // --- No-hatch mode sync ---
        BooleanSyncValue noHatchValue = new BooleanSyncValue(
                () -> isNoHatch(channelValues),
                v -> {
                    setNoHatch(channelValues, v);
                    writeChannelValues(stack, channelValues);
                });
        guiSyncManager.syncValue("no_hatch", noHatchValue);

        // --- Structure width/height/length sync ---
        int[] widthRange = channelRanges.getOrDefault(
                GTStructureChannels.STRUCTURE_WIDTH.getName(), new int[] { 0, 100 });
        int[] heightRange = channelRanges.getOrDefault(
                GTStructureChannels.STRUCTURE_HEIGHT.getName(), new int[] { 0, 100 });
        int[] lengthRange = channelRanges.getOrDefault(
                GTStructureChannels.STRUCTURE_LENGTH.getName(), new int[] { 0, 100 });

        IntSyncValue widthValue = new IntSyncValue(
                () -> channelValues.getOrDefault(GTStructureChannels.STRUCTURE_WIDTH.getName(), 0),
                v -> {
                    if (v <= 0) channelValues.remove(GTStructureChannels.STRUCTURE_WIDTH.getName());
                    else channelValues.put(GTStructureChannels.STRUCTURE_WIDTH.getName(), v);
                    writeChannelValues(stack, channelValues);
                });
        guiSyncManager.syncValue("structure_width", widthValue);

        IntSyncValue heightValue = new IntSyncValue(
                () -> channelValues.getOrDefault(GTStructureChannels.STRUCTURE_HEIGHT.getName(), 0),
                v -> {
                    if (v <= 0) channelValues.remove(GTStructureChannels.STRUCTURE_HEIGHT.getName());
                    else channelValues.put(GTStructureChannels.STRUCTURE_HEIGHT.getName(), v);
                    writeChannelValues(stack, channelValues);
                });
        guiSyncManager.syncValue("structure_height", heightValue);

        IntSyncValue lengthValue = new IntSyncValue(
                () -> channelValues.getOrDefault(GTStructureChannels.STRUCTURE_LENGTH.getName(), 0),
                v -> {
                    if (v <= 0) channelValues.remove(GTStructureChannels.STRUCTURE_LENGTH.getName());
                    else channelValues.put(GTStructureChannels.STRUCTURE_LENGTH.getName(), v);
                    writeChannelValues(stack, channelValues);
                });
        guiSyncManager.syncValue("structure_length", lengthValue);

        // --- Channel entries sync ---
        StringSyncValue[] nameSyncs = new StringSyncValue[MAX_CHANNEL_ROWS];
        IntSyncValue[] valueSyncs = new IntSyncValue[MAX_CHANNEL_ROWS];

        for (int i = 0; i < MAX_CHANNEL_ROWS; i++) {
            final int idx = i;
            nameSyncs[i] = new StringSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).name : "",
                    n -> updateEntryName(stack, entries, channelValues, idx, n != null ? n : ""));
            valueSyncs[i] = new IntSyncValue(
                    () -> idx < entries.size() ? entries.get(idx).value : 0,
                    v -> updateEntryValue(stack, entries, channelValues, idx, v));
            guiSyncManager.syncValue("ch_name_" + i, nameSyncs[i]);
            guiSyncManager.syncValue("ch_val_" + i, valueSyncs[i]);
        }

        int visibleRows = Math.max(entries.size(), 1);
        int listHeight = Math.min(visibleRows, MAX_VISIBLE_ROWS) * ROW_HEIGHT;

        var channelList = new ListWidget<>()
                .children(visibleRows, i -> {
                    int[] range = getEntryRange(entries, channelRanges, i);
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

        // --- Clear button ---
        var clearButton = new ButtonWidget<>()
                .pos(7, 195)
                .width(60).height(16)
                .overlay(IKey.lang("gregtech.tool.builder.clear"))
                .onMousePressed(m -> {
                    channelValues.clear();
                    entries.clear();
                    writeChannelValues(stack, channelValues);
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
                .child(IKey.lang("gregtech.tool.builder.gui_title").asWidget().pos(5, 5))
                .child(new ToggleButton()
                        .pos(15, 20)
                        .width(60).height(18)
                        .value(noHatchValue)
                        .tooltip(tooltip -> tooltip.addLine(
                                IKey.lang("gregtech.tool.builder.no_hatch_tooltip")))
                        .onUpdateListener(w -> w.overlay(IKey.str(
                                noHatchValue.getValue() ? "NO HATCH" : "HATCH"))))
                .child(IKey.lang("gregtech.tool.builder.structure_height").asWidget().pos(7, 42))
                .child(new TextFieldWidget()
                        .pos(80, 42)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(heightRange[0], heightRange[1])
                        .value(heightValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(IKey.lang("gregtech.tool.builder.structure_width").asWidget().pos(7, 62))
                .child(new TextFieldWidget()
                        .pos(80, 62)
                        .width(40).height(12)
                        .setTextColor(Color.WHITE.darker(1))
                        .setNumbers(widthRange[0], widthRange[1])
                        .value(widthValue)
                        .background(GTGuiTextures.DISPLAY))
                .child(IKey.lang("gregtech.tool.builder.structure_length").asWidget().pos(7, 82))
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

    // --- Channel entry helpers ---

    private static List<ChannelEntry> buildChannelEntries(@NotNull Map<String, Integer> channelValues,
                                                          @NotNull Map<String, int[]> channelRanges) {
        List<ChannelEntry> entries = new ArrayList<>();

        for (Map.Entry<String, Integer> e : channelValues.entrySet()) {
            if (isDedicatedDimensionChannel(e.getKey())) continue;
            entries.add(new ChannelEntry(e.getKey(), e.getValue()));
        }

        for (Map.Entry<String, int[]> rangeEntry : channelRanges.entrySet()) {
            String name = rangeEntry.getKey();
            if (isDedicatedDimensionChannel(name) || containsEntry(entries, name)) continue;
            entries.add(new ChannelEntry(name, channelValues.getOrDefault(name, 0)));
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

    private static int[] getEntryRange(List<ChannelEntry> entries, Map<String, int[]> channelRanges, int idx) {
        if (idx < entries.size()) {
            String name = entries.get(idx).name;
            if (name != null && !name.isEmpty() && channelRanges.containsKey(name)) {
                return channelRanges.get(name);
            }
        }
        return new int[] { 0, 100 };
    }

    private static void updateEntryName(@NotNull ItemStack stack, List<ChannelEntry> entries,
                                        Map<String, Integer> channelValues, int idx, String name) {
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
        writeChannelValues(stack, channelValues);
    }

    private static void updateEntryValue(@NotNull ItemStack stack, List<ChannelEntry> entries,
                                         Map<String, Integer> channelValues, int idx, int value) {
        while (entries.size() <= idx) {
            entries.add(new ChannelEntry("", 0));
        }
        entries.get(idx).value = value;

        String name = entries.get(idx).name;
        if (name != null && !name.isEmpty()) {
            channelValues.put(name, value);
        }
        writeChannelValues(stack, channelValues);
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

    // --- Public API ---

    @NotNull
    public static Map<String, Integer> getChannelValues(@NotNull ItemStack stack) {
        return readChannelValues(stack);
    }

    public static void setChannelValue(@NotNull ItemStack stack, String channelName, int value) {
        Map<String, Integer> values = readChannelValues(stack);
        if (value == 0) {
            values.remove(channelName);
        } else {
            values.put(channelName, value);
        }
        writeChannelValues(stack, values);
    }

    public static void clearChannelValues(@NotNull ItemStack stack) {
        writeChannelValues(stack, new HashMap<>());
    }
}
