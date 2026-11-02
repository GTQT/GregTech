package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IUpgradeKit;
import gregtech.api.items.metaitem.stats.UpgradeKitType;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 升级套件的通用物品行为。
 *
 * <p>
 * 套件本身没有主动效果，只是"塞进升级槽的消耗品"：效果由机器在对应时机触发并消耗它，
 * 见 {@link gregtech.api.metatileentity.IUpgradeSlots#consumeUpgradeKit(UpgradeKitType)}。
 *
 * <ul>
 * <li>{@link UpgradeKitType#FUSE}：能源仓过压时熔断，代替爆炸；</li>
 * <li>{@link UpgradeKitType#BACKUP_CAPACITOR}：结构跳电时消耗，保住配方（进度归零）。</li>
 * </ul>
 */
public class UpgradeKitBehavior implements IItemBehaviour, IUpgradeKit {

    private final UpgradeKitType type;

    public UpgradeKitBehavior(@NotNull UpgradeKitType type) {
        this.type = type;
    }

    @Override
    public @NotNull UpgradeKitType getType() {
        return this.type;
    }

    @Override
    public void addInformation(@NotNull ItemStack itemStack, @NotNull List<String> lines) {
        String prefix = "metaitem.upgrade." + type.getName();
        lines.add(I18n.format(prefix + ".tooltip.1"));
        lines.add(I18n.format(prefix + ".tooltip.2"));
    }
}
