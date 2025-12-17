package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.PatternError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.List;

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

        if (player.isSneaking()) {
            // If sneaking, try to build the multiblock.
            // Only try to auto-build if the structure is not already formed
            if (multiblock.isStructureFormed()) {
                multiblock.dismantleStructure(player);

                // 显示结构名称
                player.sendMessage(new TextComponentString(
                        TextFormatting.GREEN + "拆除多方块: " +
                                TextFormatting.WHITE + KeyUtil.lang(multiblock.getMetaFullName())));
                return EnumActionResult.SUCCESS;
            } else {
                player.sendMessage(new TextComponentTranslation(
                        "无法拆除多方块" + KeyUtil.lang(multiblock.getMetaFullName()))
                        .setStyle(new Style().setColor(TextFormatting.RED)));
            }
            return EnumActionResult.PASS;
        } else {
            // If not sneaking, try to show structure debug info (if any) in chat.
            if (!multiblock.isStructureFormed()) {
                PatternError error = multiblock.structurePattern.getError();
                if (error != null) {

                    player.sendMessage(new TextComponentString("============================"));
                    player.sendMessage(
                            new TextComponentTranslation("gregtech.multiblock.pattern.error_message_header"));
                    for (List<ItemStack> stack : error.getCandidates())
                        player.sendMessage(new TextComponentString("问题模块：" + stack.get(0).getDisplayName()));
                    player.sendMessage(new TextComponentString("问题坐标：" + error.getPosString(error.getPos())));
                    player.sendMessage(new TextComponentString("————————————————————————————"));
                    player.sendMessage(new TextComponentString("整改建议："));
                    player.sendMessage(new TextComponentString(error.getErrorInfo()));
                    player.sendMessage(new TextComponentString("============================"));
                    return EnumActionResult.SUCCESS;
                }
            }
            player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                    .setStyle(new Style().setColor(TextFormatting.GREEN)));
            return EnumActionResult.SUCCESS;
        }
    }

    @Override
    public void addPropertyOverride(@NotNull Item item) {
        item.addPropertyOverride(GTUtility.gregtechId("auto_mode"),
                (stack, world, entity) -> (entity != null && entity.isSneaking()) ? 1.0F : 0.0F);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        lines.add(I18n.format("metaitem.tool.multiblock_remover.tooltip2"));
    }
}
