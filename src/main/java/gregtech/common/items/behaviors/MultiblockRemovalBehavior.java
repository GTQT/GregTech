package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureFailureReport;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * 多方块拆除行为：潜行右键拆除已成型结构，直接右键输出结构诊断报告。
 *
 * <p>无状态：不读写物品 NBT，世界修改全部交给控制器自身（{@code dismantleStructure}）。
 */
public class MultiblockRemovalBehavior implements IItemBehaviour {

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        // Initial checks
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) return EnumActionResult.PASS;
        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(mte instanceof MultiblockControllerBase multiblock)) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;

        boolean formed = multiblock.isStructureFormed();

        if (player.isSneaking()) {
            // 潜行：拆除已成型结构；未成型时给出提示而不是静默失败
            if (!formed) {
                player.sendMessage(new TextComponentTranslation(
                        "gregtech.multiblock_removal.not_formed",
                        new TextComponentTranslation(multiblock.getMetaFullName()))
                        .setStyle(new Style().setColor(TextFormatting.RED)));
                return EnumActionResult.SUCCESS;
            }
            if (multiblock.dismantleStructure(player)) {
                player.sendMessage(new TextComponentTranslation(
                        "gregtech.multiblock_removal.dismantled",
                        new TextComponentTranslation(multiblock.getMetaFullName()))
                        .setStyle(new Style().setColor(TextFormatting.GREEN)));
                return EnumActionResult.SUCCESS;
            }
            // 已成型但一个方块都没拆掉：不能报“已拆除”，否则会出现“提示成功、结构还在”的假成功
            player.sendMessage(new TextComponentTranslation(
                    "gregtech.multiblock_removal.failed",
                    new TextComponentTranslation(multiblock.getMetaFullName()))
                    .setStyle(new Style().setColor(TextFormatting.RED)));
            return EnumActionResult.SUCCESS;
        }

        // 非潜行：报告结构为什么不成立，或确认没有错误
        if (!formed) {
            StructureFailureReport.sendTo(player, multiblock, world);
            return EnumActionResult.SUCCESS;
        }
        player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                .setStyle(new Style().setColor(TextFormatting.GREEN)));
        return EnumActionResult.SUCCESS;
    }
}
