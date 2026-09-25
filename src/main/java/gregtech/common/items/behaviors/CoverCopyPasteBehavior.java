package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.cover.Cover;
import gregtech.api.cover.CoverHolder;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 覆盖板复制/粘贴工具（Cover Copy/Paste tool）。
 * <p>
 * 潜行右键覆盖板 = 复制该覆盖板的配置，右键同类覆盖板 = 把配置粘贴上去，用于大批量设置覆盖板。
 * 工具自身是 MV 级电动工具，复制消耗 100 EU、粘贴消耗 25 EU。
 * <p>
 * 移植自 GT5-Unofficial 的 {@code BehaviourCoverTool}，改用 1.12 的覆盖板 API：
 * <ul>
 *     <li>覆盖板类型用 {@link gregtech.api.cover.CoverDefinition} 的 ResourceLocation 标识（而不是运行期数字 id）</li>
 *     <li>粘贴后除服务端应用 NBT 外，还通过 {@link GregtechDataCodes#UPDATE_COVER_NBT} 把完整 NBT 同步给客户端</li>
 *     <li>持有真实物品的覆盖板通过 {@link Cover#allowsCopyPasteTool()} 拒绝复制，避免刷物品</li>
 * </ul>
 */
public class CoverCopyPasteBehavior implements IItemBehaviour {

    /** 被复制覆盖板的 {@link gregtech.api.cover.CoverDefinition} id */
    private static final String NBT_COVER_ID = "mCoverId";
    /** 被复制覆盖板的完整 NBT */
    private static final String NBT_COVER_DATA = "mCoverData";
    /** 复制时记录的来源信息，仅用于 tooltip 显示 */
    private static final String NBT_SIDE = "datalines.side";
    private static final String NBT_STACK = "datalines.stack";
    private static final String NBT_X = "datalines.x";
    private static final String NBT_Y = "datalines.y";
    private static final String NBT_Z = "datalines.z";
    private static final String NBT_DIM = "datalines.dimensionId";

    private static final long COPY_COST = 100L;
    private static final long PASTE_COST = 25L;

    /**
     * @return whether the given stack is a Cover Copy/Paste tool
     */
    public static boolean isCoverCopyPasteTool(@NotNull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MetaItem<?> metaItem)) return false;
        MetaItem<?>.MetaValueItem valueItem = metaItem.getItem(stack);
        if (valueItem == null) return false;
        for (IItemBehaviour behaviour : valueItem.getBehaviours()) {
            if (behaviour instanceof CoverCopyPasteBehavior) return true;
        }
        return false;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        // 只在服务端处理：客户端返回 PASS 继续走原版流程，
        // 原版 1.12 无论 onItemUseFirst 的返回值都会把这次交互发给服务端
        if (world.isRemote) return EnumActionResult.PASS;

        ItemStack stack = player.getHeldItem(hand);
        if (stack.isEmpty()) return EnumActionResult.PASS;

        TileEntity tile = world.getTileEntity(pos);
        CoverHolder holder = tile == null ? null :
                tile.getCapability(GregtechTileCapabilities.CAPABILITY_COVER_HOLDER, null);
        if (holder == null || !holder.acceptsCovers()) {
            // 不是可安装覆盖板的方块：不干扰正常交互（例如打开机器界面）
            return EnumActionResult.PASS;
        }

        EnumFacing coverSide = findCoverSide(holder, side, hitX, hitY, hitZ);
        Cover cover = coverSide == null ? null : holder.getCoverAtSide(coverSide);
        if (cover == null) {
            sendMessage(player, "behavior.cover_copy_paste.no_cover");
            return EnumActionResult.SUCCESS;
        }

        boolean copyMode = player.isSneaking();
        if (copyMode && !cover.allowsCopyPasteTool()) {
            sendMessage(player, "behavior.cover_copy_paste.unavailable");
            return EnumActionResult.SUCCESS;
        }

        long cost = copyMode ? COPY_COST : PASTE_COST;
        if (!player.isCreative() && !drainEnergy(stack, cost, true)) {
            sendMessage(player, "behavior.cover_copy_paste.not_enough_energy");
            return EnumActionResult.SUCCESS;
        }

        NBTTagCompound tag = GTUtility.getOrCreateNbtCompound(stack);
        boolean success = copyMode ?
                copyCover(tag, cover, coverSide, pos, world, player) :
                pasteCover(tag, cover, holder, player);

        if (success) {
            if (!player.isCreative()) {
                drainEnergy(stack, cost, false);
            }
            if (ConfigHolder.client.toolUseSounds) {
                world.playSound(null, pos, GTSoundEvents.TRICORDER_TOOL, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }
        return EnumActionResult.SUCCESS;
    }

    /**
     * 点击的面上没有覆盖板时，退回覆盖板网格判定出的那个面（与放置覆盖板的判定一致）。
     */
    private static @Nullable EnumFacing findCoverSide(@NotNull CoverHolder holder, @NotNull EnumFacing side,
                                                      float hitX, float hitY, float hitZ) {
        if (holder.getCoverAtSide(side) != null) return side;
        EnumFacing gridSide = GTUtility.determineWrenchingSide(side, hitX, hitY, hitZ);
        return holder.getCoverAtSide(gridSide) != null ? gridSide : null;
    }

    private static boolean copyCover(@NotNull NBTTagCompound tag, @NotNull Cover cover, @NotNull EnumFacing side,
                                     @NotNull BlockPos pos, @NotNull World world, @NotNull EntityPlayer player) {
        NBTTagCompound coverData = new NBTTagCompound();
        cover.writeToNBT(coverData);

        tag.setString(NBT_COVER_ID, cover.getDefinition().getResourceLocation().toString());
        tag.setTag(NBT_COVER_DATA, coverData);
        tag.setInteger(NBT_SIDE, side.getIndex());
        tag.setTag(NBT_STACK, cover.getPickItem().writeToNBT(new NBTTagCompound()));
        tag.setInteger(NBT_X, pos.getX());
        tag.setInteger(NBT_Y, pos.getY());
        tag.setInteger(NBT_Z, pos.getZ());
        tag.setInteger(NBT_DIM, world.provider.getDimension());

        sendMessage(player, "behavior.cover_copy_paste.position",
                pos.getX(), pos.getY(), pos.getZ(), world.provider.getDimension());
        sendMessage(player, "behavior.cover_copy_paste.side", side.getName());
        sendMessage(player, "behavior.cover_copy_paste.type", cover.getPickItem().getDisplayName());
        return true;
    }

    private static boolean pasteCover(@NotNull NBTTagCompound tag, @NotNull Cover cover,
                                      @NotNull CoverHolder holder, @NotNull EntityPlayer player) {
        String copiedCoverId = tag.getString(NBT_COVER_ID);
        if (copiedCoverId.isEmpty()) {
            sendMessage(player, "behavior.cover_copy_paste.invalid_cover");
            return false;
        }
        if (!copiedCoverId.equals(cover.getDefinition().getResourceLocation().toString())) {
            // 只能粘贴到同类型的覆盖板上
            sendMessage(player, "behavior.cover_copy_paste.not_match");
            return false;
        }
        if (!cover.allowsCopyPasteTool()) {
            sendMessage(player, "behavior.cover_copy_paste.unavailable");
            return false;
        }

        NBTTagCompound data = tag.getCompoundTag(NBT_COVER_DATA);
        try {
            cover.readFromNBT(data);
        } catch (Exception e) {
            GTLog.logger.error("Failed to paste cover data onto {} at {}", copiedCoverId, holder.getPos(), e);
            sendMessage(player, "behavior.cover_copy_paste.failed");
            return false;
        }

        cover.markDirty();
        cover.notifyBlockUpdate();
        cover.getCoverableView().scheduleRenderUpdate();
        // 覆盖板的配置平时只在区块加载时随方块实体 NBT 下发，这里主动把粘贴后的完整 NBT 送给客户端
        cover.writeCustomData(GregtechDataCodes.UPDATE_COVER_NBT, buf -> buf.writeCompoundTag(data));

        sendMessage(player, "behavior.cover_copy_paste.pasted");
        return true;
    }

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    private static void sendMessage(@NotNull EntityPlayer player, @NotNull String translationKey, Object... args) {
        player.sendMessage(new TextComponentTranslation(translationKey, args));
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        NBTTagCompound tag = itemStack.getTagCompound();
        if (tag != null && !tag.getString(NBT_COVER_ID).isEmpty()) {
            lines.add(TextFormatting.BLUE + I18n.format("behavior.cover_copy_paste.stored"));
            lines.add(I18n.format("behavior.cover_copy_paste.stored.position",
                    tag.getInteger(NBT_X), tag.getInteger(NBT_Y), tag.getInteger(NBT_Z), tag.getInteger(NBT_DIM)));
            if (tag.hasKey(NBT_SIDE, Constants.NBT.TAG_INT)) {
                EnumFacing side = EnumFacing.byIndex(tag.getInteger(NBT_SIDE));
                if (side != null) {
                    lines.add(I18n.format("behavior.cover_copy_paste.stored.side", side.getName()));
                }
            }
            if (tag.hasKey(NBT_STACK, Constants.NBT.TAG_COMPOUND)) {
                ItemStack stored = new ItemStack(tag.getCompoundTag(NBT_STACK));
                if (!stored.isEmpty()) {
                    lines.add(I18n.format("behavior.cover_copy_paste.stored.type", stored.getDisplayName()));
                }
            }
        }
        lines.add(I18n.format("behavior.cover_copy_paste.copy_hint"));
        lines.add(I18n.format("behavior.cover_copy_paste.paste_hint"));
    }
}
