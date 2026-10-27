package gregtech.common.items.behaviors.multiblock;

import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.casing.GTStructureChannels;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 多方块工具保存在 ItemStack 上的通道配置：通道值、控制器上报的支持范围、no-hatch 开关。
 *
 * <p>行为层与配置面板共用这一份读写逻辑，避免每个界面/模式各写一遍 NBT 处理。
 * 实例是 NBT 的一次快照：修改 {@link #values()} 或调用 {@link #put(String, int)} 之后，
 * 必须调用 {@link #store()} 才会写回物品；值为 0 的通道视为“未配置”，不会落盘。
 */
public final class ChannelSettings {

    private final ItemStack stack;
    private final Map<String, Integer> values = new HashMap<>();
    private final Map<String, int[]> supportedRanges = new HashMap<>();

    private ChannelSettings(ItemStack stack) {
        this.stack = stack;
    }

    /** 从物品 NBT 读取一份配置快照。 */
    @NotNull
    public static ChannelSettings load(@NotNull ItemStack stack) {
        ChannelSettings settings = new ChannelSettings(stack);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return settings;
        if (tag.hasKey(MultiblockToolNbt.CHANNEL_VALUES)) {
            NBTTagCompound channels = tag.getCompoundTag(MultiblockToolNbt.CHANNEL_VALUES);
            for (String key : channels.getKeySet()) {
                settings.values.put(key, channels.getInteger(key));
            }
        }
        if (tag.hasKey(MultiblockToolNbt.CHANNEL_RANGES)) {
            NBTTagCompound ranges = tag.getCompoundTag(MultiblockToolNbt.CHANNEL_RANGES);
            for (String key : ranges.getKeySet()) {
                int[] range = ranges.getIntArray(key);
                if (range.length == 2) settings.supportedRanges.put(key, range);
            }
        }
        return settings;
    }

    /** 通道值（可变视图，修改后记得 {@link #store()}）。 */
    @NotNull
    public Map<String, Integer> values() {
        return values;
    }

    /** 控制器上次上报的通道合法范围（只读视图）。 */
    @NotNull
    public Map<String, int[]> supportedRanges() {
        return supportedRanges;
    }

    public int get(@NotNull String channelName) {
        return values.getOrDefault(channelName, 0);
    }

    /** 写入通道值；值 0 等价于移除该通道。 */
    public void put(@NotNull String channelName, int value) {
        if (value == 0) {
            values.remove(channelName);
        } else {
            values.put(channelName, value);
        }
    }

    public boolean isNoHatch() {
        return StructureOperationRequest.isNoHatch(values);
    }

    public void setNoHatch(boolean enabled) {
        put(GTStructureChannels.NO_HATCH.getName(), enabled ? 1 : 0);
    }

    public void setSupportedRanges(@NotNull Map<String, int[]> ranges) {
        supportedRanges.clear();
        supportedRanges.putAll(ranges);
    }

    /** 建造/提示请求使用的通道参数；未配置任何通道时返回 {@code null}。 */
    @Nullable
    public Map<String, Integer> asRequestChannels() {
        return values.isEmpty() ? null : values;
    }

    /** 清空通道值并立即落盘（支持范围保留，供下次打开面板时列出通道）。 */
    public void clear() {
        values.clear();
        store();
    }

    /** 把当前快照写回物品 NBT。 */
    public void store() {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            if (values.isEmpty() && supportedRanges.isEmpty()) return;
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        NBTTagCompound channels = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getValue() != 0) {
                channels.setInteger(entry.getKey(), entry.getValue());
            }
        }
        if (channels.isEmpty()) {
            tag.removeTag(MultiblockToolNbt.CHANNEL_VALUES);
        } else {
            tag.setTag(MultiblockToolNbt.CHANNEL_VALUES, channels);
        }

        if (supportedRanges.isEmpty()) {
            tag.removeTag(MultiblockToolNbt.CHANNEL_RANGES);
        } else {
            NBTTagCompound ranges = new NBTTagCompound();
            for (Map.Entry<String, int[]> entry : supportedRanges.entrySet()) {
                ranges.setIntArray(entry.getKey(), entry.getValue());
            }
            tag.setTag(MultiblockToolNbt.CHANNEL_RANGES, ranges);
        }
    }
}
