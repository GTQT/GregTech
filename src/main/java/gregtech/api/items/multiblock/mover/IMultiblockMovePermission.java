package gregtech.api.items.multiblock.mover;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

public interface IMultiblockMovePermission {
    boolean canRemove(EntityPlayerMP player, WorldServer world, BlockPos sourcePos);

    boolean canPlace(EntityPlayerMP player, WorldServer world, BlockPos destinationPos);
}

