package gregtech.common.items.behaviors.multiblock;

/**
 * 多方块工具在 ItemStack NBT 上使用的键。
 *
 * <p>集中定义，避免各行为、面板、会话层各写一遍字符串。键名保持历史值不变，
 * 以免旧存档里的模式/通道配置失效；新增键按 {@code GT.MultiblockTool} 前缀扩展。
 */
public final class MultiblockToolNbt {

    /** 工具根 compound：模式等工具级设置。 */
    public static final String ROOT = "GT.MultiblockTool";
    /** 当前模式 id（int）。 */
    public static final String MODE = "GTMultiblockToolMode";
    /** 搬运会话绑定 compound（会话 id / 维度 / 控制器坐标）。 */
    public static final String SESSION = "GTMultiblockMoverSession";
    /** 通道值 compound（通道名 -> int；值为 0 不落盘）。 */
    public static final String CHANNEL_VALUES = "ProjectorChannels";
    /** 控制器上报的通道范围缓存（通道名 -> [min, max]）。 */
    public static final String CHANNEL_RANGES = "ProjectorChannelRanges";
    /** 全息预览是否比较模式（boolean）。 */
    public static final String COMPARE_MODE = "ProjectorCompareMode";

    private MultiblockToolNbt() {}
}
