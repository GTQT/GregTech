package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.pattern.StructureBuildResult;
import gregtech.api.pattern.StructureFailureReport;
import gregtech.api.pattern.StructureOperationRequest;
import gregtech.api.pattern.StructureOrientation;
import gregtech.api.util.GTLog;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
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
 * 基础结构建造器：只提供“结构检查 + 自动建造”。
 *
 * <p>前期用的轻量版本：不需要电力，也没有全息预览、比较模式、通道配置与搬运能力；
 * 建造时通道一律取结构默认值。世界修改仍由结构运行时执行，本行为无状态。
 */
public class BasicMultiblockBuilderBehavior implements IItemBehaviour {

    /** 失败报告末尾追加的提示：告诉玩家本工具可以自动建造。 */
    private static final String AUTO_BUILD_ADVICE = "gregtech.multiblock.pattern.failure.advice.basic_build";

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (!(tileEntity instanceof IGregTechTileEntity)) return EnumActionResult.PASS;
        MetaTileEntity mte = ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        if (!(mte instanceof MultiblockControllerBase multiblock)) return EnumActionResult.PASS;
        if (world.isRemote) return EnumActionResult.SUCCESS;

        ItemStack heldStack = player.getHeldItem(hand);
        if (!player.canPlayerEdit(pos, side, heldStack)) return EnumActionResult.FAIL;

        if (player.isSneaking()) {
            // 已成型时什么都不做，避免误触重复建造
            if (multiblock.isStructureFormed()) return EnumActionResult.PASS;
            buildStructure(player, multiblock, heldStack);
            return EnumActionResult.SUCCESS;
        }

        if (!multiblock.isStructureFormed()) {
            StructureFailureReport.sendTo(player, multiblock, world, AUTO_BUILD_ADVICE);
            return EnumActionResult.SUCCESS;
        }
        player.sendMessage(new TextComponentTranslation("gregtech.multiblock.pattern.no_errors")
                .setStyle(new Style().setColor(TextFormatting.GREEN)));
        return EnumActionResult.SUCCESS;
    }

    private static void buildStructure(EntityPlayer player, MultiblockControllerBase multiblock,
                                       ItemStack triggerStack) {
        StructureOperationRequest request = StructureOperationRequest.build(
                player, multiblock, StructureOrientation.fromController(multiblock), null, triggerStack);
        if (multiblock.autoBuildStructure(request)) return;

        StructureBuildResult result = multiblock.getOrCreateStructureRuntime().buildAllPieces(request);
        GTLog.logger.info("[BasicStructureBuilder] build result controller={} pos={}, {}",
                multiblock.getMetaName(), multiblock.getPos(), result.describeCounts());
    }
}
