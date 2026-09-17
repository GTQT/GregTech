package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.util.ColorUtil;
import gregtech.api.util.GTUtility;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ProbeMode;
import mcjty.theoneprobe.api.TextStyleClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 在 TOP 检测器信息中显示仓的颜色通道(输入仓染色隔离)。
 * 探测输入仓时显示自身通道;探测多方块控制器时汇总该控制器上的全部在用通道。
 * 与 GT5U 的 WAILA 颜色通道显示对应。
 */
public class ColorChannelInfoProvider implements IProbeInfoProvider {

    @Override
    public String getID() {
        return GTValues.MODID + ":color_channel_provider";
    }

    @Override
    public void addProbeInfo(@NotNull ProbeMode mode, @NotNull IProbeInfo probeInfo, @NotNull EntityPlayer player,
                             @NotNull World world, @NotNull IBlockState blockState, @NotNull IProbeHitData data) {
        MetaTileEntity mte = GTUtility.getMetaTileEntity(world, data.getPos());
        if (mte == null) return;

        if (mte instanceof MetaTileEntityMultiblockPart part) {
            addPartChannel(probeInfo, part);
        } else if (mte instanceof MultiblockControllerBase controller) {
            addControllerChannels(probeInfo, controller);
        }
    }

    /** 单个输入仓显示自身通道。 */
    private void addPartChannel(IProbeInfo probeInfo, MetaTileEntityMultiblockPart part) {
        if (!isImportPart(part)) return;
        EnumDyeColor dye = getChannelDye(part);
        if (dye == null) return;
        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.color_channel*} " + describeChannel(dye));
    }

    /**
     * 控制器显示其上全部在用通道。未染色仓不占通道,但只要存在就单独标注;
     * 完全没有染色仓时整行不显示。
     */
    private void addControllerChannels(IProbeInfo probeInfo, MultiblockControllerBase controller) {
        TreeSet<EnumDyeColor> channels = new TreeSet<>();
        boolean uncolored = false;

        for (IMultiblockPart part : controller.getMultiblockParts()) {
            if (!(part instanceof MetaTileEntityMultiblockPart mtePart) || !isImportPart(mtePart)) continue;
            EnumDyeColor dye = getChannelDye(mtePart);
            if (dye == null) {
                uncolored = true;
            } else {
                // EnumDyeColor 的枚举序即 metadata 序,TreeSet 天然按通道号输出
                channels.add(dye);
            }
        }

        if (channels.isEmpty()) return;

        List<String> entries = new ArrayList<>();
        for (EnumDyeColor dye : channels) {
            entries.add(describeChannel(dye));
        }
        if (uncolored) {
            entries.add(TextFormatting.GRAY + I18n.translateToLocal("gregtech.top.color_channel.uncolored"));
        }

        probeInfo.text(TextStyleClass.INFO + "{*gregtech.top.color_channel*} "
                + String.join(TextFormatting.GRAY + ", ", entries));
    }

    /** 输入仓:带 IMPORT_ITEMS / IMPORT_FLUIDS 能力的多方块部件。 */
    private static boolean isImportPart(MetaTileEntity mte) {
        if (!(mte instanceof IMultiblockAbilityPart<?> abilityPart)) return false;
        List<MultiblockAbility<?>> abilities = abilityPart.getAbilities();
        return abilities.contains(MultiblockAbility.IMPORT_ITEMS)
                || abilities.contains(MultiblockAbility.IMPORT_FLUIDS);
    }

    /** 仓的染料通道;未染色或非染料色(自定义 ARGB 喷涂)返回 null。 */
    private static @Nullable EnumDyeColor getChannelDye(MetaTileEntity mte) {
        return mte.isPainted() ? ColorUtil.getDyeColorFromRGB(mte.getPaintingColor()) : null;
    }

    private static String describeChannel(EnumDyeColor dye) {
        TextFormatting formatting = ColorUtil.getTextFormatting(dye.colorValue);
        String name = I18n.translateToLocal("item.dyePowder." + dye.getName() + ".name");
        return formatting == null ? name : formatting + name;
    }
}
