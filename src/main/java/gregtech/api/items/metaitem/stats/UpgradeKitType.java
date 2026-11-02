package gregtech.api.items.metaitem.stats;

import org.jetbrains.annotations.NotNull;

/**
 * 升级套件的种类。
 *
 * <p>
 * 套件是塞在实现 {@link gregtech.api.metatileentity.IUpgradeSlots} 的机器的升级槽里的一次性道具，
 * 由机器在对应时机消耗（例如保险丝在过压时熔断、备用电容在跳电时耗尽）。
 * 物品侧只负责"声明自己是哪一类套件"，具体效果在机器/配方逻辑里实现。
 */
public enum UpgradeKitType {

    /** 保险丝：能源仓过压时熔断一根，代替爆炸。 */
    FUSE("fuse"),

    /** 备用电容：多方块跳电时消耗，保住配方（进度归零）。 */
    BACKUP_CAPACITOR("backup_capacitor");

    private final String name;

    UpgradeKitType(@NotNull String name) {
        this.name = name;
    }

    /** 用于语言键 / 调试输出的短名。 */
    @NotNull
    public String getName() {
        return name;
    }
}
