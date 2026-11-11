package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.MultiPiecePattern;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureFailureReport;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.pattern.casing.StructureChannel;
import gregtech.api.util.GTLog;
import gregtech.client.gui.ClientToolScreens;
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构投影行为：全息预览、建造配置、比较模式、通道配置。
 *
 * <p>右键控制器先打开结构预览界面；预览界面的“建造配置”按钮再打开“结构建造配置”对话框。
 * 潜行右键控制器只检查结构并报告问题，不再自动建造。右键其他 GT 方块或空气不再打开配置面板。
 *
 * <p>本类无状态：所有持久化数据都在 ItemStack 的 NBT 中（见 {@link ChannelSettings}），
 * 方法内的局部变量只是单次调用的工作副本。
 */
public class MultiblockBuilderBehavior implements IItemBehaviour {

    /** Hint appended to a failure report, pointing at diagnosis and the preview/build entry points. */
    private static final String AUTO_BUILD_ADVICE = "gregtech.multiblock.pattern.failure.advice.build";

    // --- Compare mode (item-scoped, not a structure channel) ---

    private static boolean readCompareMode(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(MultiblockToolNbt.COMPARE_MODE)) return true;
        return tag.getBoolean(MultiblockToolNbt.COMPARE_MODE);
    }

    // --- Item use actions ---

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack heldStack = player.getHeldItem(hand);

        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            // Only multiblock controllers expose the build UI.
            return EnumActionResult.PASS;
        }

        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(mte instanceof MultiblockControllerBase)) return EnumActionResult.PASS;
        MultiblockControllerBase multiblock = (MultiblockControllerBase) mte;
        if (!player.canPlayerEdit(pos, side, player.getHeldItem(hand))) return EnumActionResult.FAIL;

        ChannelSettings settings = ChannelSettings.load(heldStack);

        if (player.isSneaking()) {
            // Shift+right-click: only diagnose the structure, no building (the preview screen owns building now).
            if (world.isRemote) return EnumActionResult.SUCCESS;

            saveControllerChannelRanges(settings, multiblock);
            if (multiblock.isStructureFormed()) {
                player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                        .setStyle(new Style().setColor(TextFormatting.GREEN)));
                return EnumActionResult.SUCCESS;
            }

            // Report the failure before spawning hints: hint generation re-runs pattern
            // matching, which recycles the shared block-state buffer the live error points at.
            StructureFailureReport.sendTo(player, multiblock, world, AUTO_BUILD_ADVICE);
            spawnStructureHints(multiblock, player, heldStack, settings.asRequestChannels());
            return EnumActionResult.SUCCESS;
        } else {
            // Right-click opens the structure preview; the build dialog is reached from its build-configuration
            // button, so the player does not have to press Back manually.
            if (world.isRemote) {
                ClientToolScreens.openControllerPreview(multiblock, settings.values());
                return EnumActionResult.SUCCESS;
            }

            // Server-side: store supported channel ranges into NBT for GUI use.
            saveControllerChannelRanges(settings, multiblock);
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
        // The UI only opens from a controller, never from the air. Right-clicking empty space keeps the
        // vanilla behaviour so the builder cannot shadow normal item use.
        return new ActionResult<>(EnumActionResult.PASS, player.getHeldItem(hand));
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
}
