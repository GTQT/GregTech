package gregtech.api.bridge.impl;

import gregtech.api.block.machines.BlockMachine;
import gregtech.api.bridge.IGTMachineHelper;
import gregtech.api.bridge.IGTMachineInfo;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;

import org.jetbrains.annotations.Nullable;

public class GTMachineHelperImpl implements IGTMachineHelper {

    @Override
    public boolean isGTMachineBlock(Block block) {
        return block instanceof BlockMachine;
    }

    @Nullable
    @Override
    public IGTMachineInfo getMachineInfo(IBlockAccess world, BlockPos pos) {
        MetaTileEntity mte = GTUtility.getMetaTileEntity(world, pos);
        return mte;
    }

    @Nullable
    @Override
    public IGTMachineInfo getMachineInfoFromTileEntity(TileEntity te) {
        if (te instanceof IGregTechTileEntity igtte) {
            return igtte.getMetaTileEntity();
        }
        return null;
    }
}
