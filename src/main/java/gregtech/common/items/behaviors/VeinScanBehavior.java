package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.worldgen.vein.VeinHelper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

public class VeinScanBehavior implements IItemBehaviour {
    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos posin, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) return EnumActionResult.PASS;
        if(player.isSneaking())
        {
            var data = VeinHelper.getVeinEntry(world, posin);
            if (data != null && data.getType() != null) {
                player.sendMessage(new TextComponentString("该区块的矿脉为：" + data.getType().id));
            } else {
                player.sendMessage(new TextComponentString("该区块无矿脉"));
            }
            return EnumActionResult.SUCCESS;
        }
        int count = VeinHelper.scanVeinsAround(world,posin,3);
        player.sendMessage(new TextComponentString("扫描到矿脉数量:"+count));
        return EnumActionResult.SUCCESS;
    }

}
