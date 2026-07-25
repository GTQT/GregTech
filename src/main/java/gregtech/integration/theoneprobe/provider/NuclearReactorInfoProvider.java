package gregtech.integration.theoneprobe.provider;

import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import mcjty.theoneprobe.api.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import gregtech.SCValues;
import gregtech.common.item.behaviors.NuclearComponentBehavior;
import gregtech.common.metatileentities.multi.nuclearReactor.MetaTileEntityNuclearReactor;

public class NuclearReactorInfoProvider implements IProbeInfoProvider {

    private static IProbeInfo newVertical(final IProbeInfo probeInfo) {
        return probeInfo.vertical(probeInfo.defaultLayoutStyle().spacing(0));
    }

    @Override
    public String getID() {
        return SCValues.MODID + ":nuclear_reactor_provider";
    }

    @Override
    public void addProbeInfo(ProbeMode probeMode, IProbeInfo iProbeInfo, EntityPlayer entityPlayer,
                             World world, IBlockState iBlockState, IProbeHitData iProbeHitData) {
        IProbeInfo horizontalPane = iProbeInfo.horizontal(
                iProbeInfo.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER)
        );

        if (iBlockState.getBlock().hasTileEntity(iBlockState)) {
            TileEntity te = world.getTileEntity(iProbeHitData.getPos());

            if (te instanceof IGregTechTileEntity igtte) {
                MetaTileEntity mte = igtte.getMetaTileEntity();

                if (mte instanceof MetaTileEntityNuclearReactor reactor) {
                    IProbeInfo verticalPane = newVertical(horizontalPane);

                    // 1. 反应堆状态
                    addReactorStatusInfo(verticalPane, reactor);

                    // 2. 热量信息
                    addHeatInfo(verticalPane, reactor, entityPlayer);

                    // 3. 能量输出
                    addEnergyInfo(verticalPane, reactor, entityPlayer);

                    // 4. 组件统计
                    addComponentStats(verticalPane, reactor);

                    // 5. 大小详细
                    addSizeInfo(verticalPane, reactor);
                }
            }
        }
    }

    private void addSizeInfo(IProbeInfo verticalPane, MetaTileEntityNuclearReactor reactor) {
        IProbeInfo sizeLine = verticalPane.horizontal(verticalPane.defaultLayoutStyle().spacing(2));
        sizeLine.text(TextFormatting.GRAY + "大小: " + TextFormatting.WHITE + reactor.getReactorWidth() + "×" + reactor.getReactorHeight());

        IProbeInfo extendLine = verticalPane.horizontal(verticalPane.defaultLayoutStyle().spacing(2));
        extendLine.text(TextFormatting.GRAY + "扩展仓: " + TextFormatting.WHITE + reactor.getExtendCount());
    }

    private void addReactorStatusInfo(IProbeInfo pane, MetaTileEntityNuclearReactor controller) {
        String statusText;
        String statusPrefix;

        if (controller.hasMeltdown()) {
            statusText = "反应堆熔毁";
            statusPrefix = TextFormatting.RED + "✗ ";
        } else if (controller.isReactorActive()) {
            statusText = "运行中";
            statusPrefix = TextFormatting.GREEN + "✓ ";
        } else {
            statusText = "待机";
            statusPrefix = TextFormatting.GRAY + "○ ";
        }

        pane.text(statusPrefix + TextFormatting.BOLD + statusText);
    }

    private void addHeatInfo(IProbeInfo pane, MetaTileEntityNuclearReactor controller, EntityPlayer player) {
        int currentHeat = controller.getCurrentHeat();
        int maxHeat = controller.getMaxHeatCapacity();
        if (maxHeat == 0) return;

        int heatPercent = (currentHeat * 100) / maxHeat;

        // 热量进度条颜色
        int barColor;
        if (heatPercent >= 90) {
            barColor = 0xFF4444;       // 红色
        } else if (heatPercent >= 70) {
            barColor = 0xFFAA00;       // 橙色
        } else if (heatPercent >= 40) {
            barColor = 0xFFFF00;       // 黄色
        } else {
            barColor = 0x44FF44;       // 绿色
        }

        // 创建热量信息行
        IProbeInfo heatLine = pane.horizontal(pane.defaultLayoutStyle().spacing(2));
        heatLine.text(TextFormatting.GRAY + "热量: ");

        // 修正：调整进度条高度和文字高度
        IProgressStyle progressStyle = pane.defaultProgressStyle()
                .backgroundColor(0xFF555555)
                .filledColor(barColor)
                .borderColor(0xFF888888)
                .width(90)  // 稍微增加宽度
                .height(12); // 增加高度以容纳文字


        // 设置数字格式
        if (player.isSneaking() || currentHeat < 10000) {
            progressStyle.numberFormat(NumberFormat.NONE); // 不在进度条内显示数字
        } else {
            progressStyle.numberFormat(NumberFormat.NONE); // 不在进度条内显示数字
        }

        // 设置后缀文本（显示在进度条右侧）
        String suffix;
        if (player.isSneaking() || maxHeat < 10000) {
            suffix = String.format("%d / %d HU", currentHeat, maxHeat);
        } else {
            suffix = String.format("%s / %s HU",
                    formatCompactNumber(currentHeat),
                    formatCompactNumber(maxHeat));
        }
        progressStyle.suffix(TextFormatting.GRAY + suffix);

        // 添加进度条
        heatLine.progress(currentHeat, maxHeat, progressStyle);

        // 过热警告
        if (heatPercent >= 85) {
            pane.text(TextFormatting.RED + "⚠ 警告: 接近熔毁阈值!");
        }
    }

    private String formatCompactNumber(long number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        }
        return String.valueOf(number);
    }

    private void addEnergyInfo(IProbeInfo pane, MetaTileEntityNuclearReactor controller, EntityPlayer player) {
        long currentOutput = controller.getCurrentOutput();

        // 创建能量信息行
        IProbeInfo energyLine = pane.horizontal(pane.defaultLayoutStyle().spacing(2));
        energyLine.text(TextFormatting.GRAY + "输出: ");

        if (currentOutput > 0) {
            energyLine.text(TextFormatting.AQUA + formatEnergyOutput(currentOutput, player) + " EU/t");
        } else {
            energyLine.text(TextFormatting.GRAY + "0 EU/t");
        }
    }

    private String formatEnergyOutput(long output, EntityPlayer player) {
        if (player.isSneaking()) {
            return String.valueOf(output);
        }

        if (output >= 1000000) {
            return String.format("%.1fM", output / 1000000.0);
        } else if (output >= 10000) {
            return String.format("%.1fK", output / 1000.0);
        }
        return String.valueOf(output);
    }

    private void addComponentStats(IProbeInfo pane, MetaTileEntityNuclearReactor controller) {
        gregtech.api.nuclear.ic.NuclearReactorSimulator simulator = controller.getReactorSimulator();
        int fuelRods = simulator.getTotalFuelRods();
        int heatVents = simulator.getTotalHeatVents();
        int coolantCells = simulator.getTotalCoolantCells();
        int reflectors = simulator.getTotalReflectors();

        if (fuelRods + heatVents + coolantCells + reflectors == 0) return;

        // 创建组件统计行
        IProbeInfo compLine = pane.horizontal(pane.defaultLayoutStyle().spacing(2));
        compLine.text(TextFormatting.GRAY + "组件: ");

        boolean first = true;
        if (fuelRods > 0) {
            compLine.text((first ? "" : " ") + TextFormatting.RED + "燃料:" + TextFormatting.WHITE + fuelRods);
            first = false;
        }
        if (heatVents > 0) {
            compLine.text((first ? "" : " ") + TextFormatting.YELLOW + "散热:" + TextFormatting.WHITE + heatVents);
            first = false;
        }
        if (coolantCells > 0) {
            compLine.text((first ? "" : " ") + TextFormatting.BLUE + "冷却:" + TextFormatting.WHITE + coolantCells);
            first = false;
        }
        if (reflectors > 0) {
            compLine.text((first ? "" : " ") + TextFormatting.AQUA + "反射:" + TextFormatting.WHITE + reflectors);
        }

        // 检查耗尽组件
        int depleted = countDepletedComponents(controller);
        if (depleted > 0) {
            pane.text(TextFormatting.YELLOW + "⚠ " + depleted + "个组件已耗尽");
        }
    }

    private int countDepletedComponents(MetaTileEntityNuclearReactor controller) {
        int count = 0;
        GTItemStackHandler handler = controller.getComponentHandler();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            net.minecraft.item.ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                NuclearComponentBehavior behavior =
                        NuclearComponentBehavior.getInstanceFor(stack);
                if (behavior != null) {
                    int damage = gregtech.common.items.behaviors.AbstractMaterialPartBehavior.getPartDamage(stack);
                    int maxDurability = behavior.getPartMaxDurability(stack);
                    if (damage >= maxDurability) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}