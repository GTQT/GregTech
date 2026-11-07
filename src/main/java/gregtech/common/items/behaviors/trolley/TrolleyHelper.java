package gregtech.common.items.behaviors.trolley;

import gregtech.api.GregTechAPI;
import gregtech.api.block.machines.BlockMachine;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.registry.MTERegistry;
import gregtech.api.util.GTLog;
import gregtech.api.util.GTUtility;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public final class TrolleyHelper {

    private TrolleyHelper() {
    }

    public static EnumActionResult pickUp(World world, BlockPos pos, ItemStack trolley, EntityPlayer player) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }
        MetaTileEntity mte = getMachine(world, pos);
        if (mte == null) {
            return EnumActionResult.PASS;
        }
        if (TrolleyData.hasMachine(trolley)) {
            message(player, "gregtech.message.trolley.already_loaded");
            return EnumActionResult.SUCCESS;
        }
        if (mte instanceof MultiblockControllerBase || mte instanceof IMultiblockPart) {
            message(player, "gregtech.message.trolley.multiblock_denied");
            return EnumActionResult.SUCCESS;
        }

        NBTTagCompound blockEntityNbt = new NBTTagCompound();
        mte.writeToNBT(blockEntityNbt);

        NBTTagCompound stored = new NBTTagCompound();
        stored.setString(TrolleyData.REGISTRY_NAME, mte.metaTileEntityId.toString());
        NBTUtil.writeBlockState(stored, world.getBlockState(pos));
        stored.setTag(TrolleyData.BLOCK_ENTITY_NBT, blockEntityNbt);
        stored.setString(TrolleyData.DISPLAY_KEY, mte.getMetaName() + ".name");
        TrolleyData.store(trolley, stored);

        mte.onRemoval();

        world.setBlockToAir(pos);

        GTLog.logger.info("Trolley picked up machine {} at {}", mte.metaTileEntityId, pos);
        message(player, "gregtech.message.trolley.picked_up");
        return EnumActionResult.SUCCESS;
    }

    public static EnumActionResult place(World world, BlockPos clickedPos, EnumFacing side, ItemStack trolley,
                                         EntityPlayer player) {
        if (world.isRemote) {
            return EnumActionResult.PASS;
        }
        if (!TrolleyData.hasMachine(trolley)) {
            return EnumActionResult.PASS;
        }

        ResourceLocation id = TrolleyData.getRegistryName(trolley);
        NBTTagCompound blockEntityNbt = TrolleyData.getBlockEntityNbt(trolley);
        if (id == null || blockEntityNbt == null) {
            TrolleyData.clear(trolley);
            message(player, "gregtech.message.trolley.corrupted");
            return EnumActionResult.SUCCESS;
        }

        MTERegistry registry = GregTechAPI.mteManager.getRegistry(id.getNamespace());
        MetaTileEntity sample = registry.getObject(id);
        if (sample == null) {
            message(player, "gregtech.message.trolley.unknown_machine", id);
            return EnumActionResult.SUCCESS;
        }
        Block block = Block.getBlockFromItem(sample.getStackForm().getItem());
        if (!(block instanceof BlockMachine)) {
            message(player, "gregtech.message.trolley.unknown_machine", id);
            return EnumActionResult.SUCCESS;
        }

        BlockPos targetPos = side == null ? clickedPos : clickedPos.offset(side);
        if (!canPlaceAt(world, targetPos)) {
            if (side == null || !canPlaceAt(world, clickedPos)) {
                message(player, "gregtech.message.trolley.no_space");
                return EnumActionResult.SUCCESS;
            }
            targetPos = clickedPos;
        }

        IBlockState state = resolveState(trolley, block, sample);
        if (!world.setBlockState(targetPos, state, 3)) {
            message(player, "gregtech.message.trolley.no_space");
            return EnumActionResult.SUCCESS;
        }

        TileEntity tileEntity = world.getTileEntity(targetPos);
        if (!(tileEntity instanceof IGregTechTileEntity)) {
            world.setBlockToAir(targetPos);
            message(player, "gregtech.message.trolley.failed");
            return EnumActionResult.SUCCESS;
        }

        MetaTileEntity placed = ((IGregTechTileEntity) tileEntity).setMetaTileEntity(sample, blockEntityNbt);
        if (placed == null) {
            world.setBlockToAir(targetPos);
            message(player, "gregtech.message.trolley.failed");
            return EnumActionResult.SUCCESS;
        }

        placed.onPlacement();

        TrolleyData.clear(trolley);
        GTLog.logger.info("Trolley placed machine {} at {}", id, targetPos);
        message(player, "gregtech.message.trolley.placed");
        return EnumActionResult.SUCCESS;
    }

    private static MetaTileEntity getMachine(World world, BlockPos pos) {
        if (!(world.getBlockState(pos).getBlock() instanceof BlockMachine)) {
            return null;
        }
        return GTUtility.getMetaTileEntity(world, pos);
    }

    private static boolean canPlaceAt(World world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == Blocks.AIR) {
            return true;
        }
        if (world.getTileEntity(pos) != null) {
            return false;
        }
        return state.getBlock().isReplaceable(world, pos);
    }

    private static IBlockState resolveState(ItemStack trolley, Block block, MetaTileEntity sample) {
        IBlockState state = block.getDefaultState();
        NBTTagCompound stored = TrolleyData.getStored(trolley);
        if (stored != null && stored.hasKey(TrolleyData.BLOCK_STATE, 10)) {
            try {
                IBlockState saved = NBTUtil.readBlockState(stored.getCompoundTag(TrolleyData.BLOCK_STATE));
                if (saved != null && saved.getBlock() == block) {
                    state = saved;
                }
            } catch (RuntimeException e) {
                GTLog.logger.warn("Invalid stored BlockState for trolley, falling back to the default state.", e);
            }
        }
        return state.withProperty(BlockMachine.OPAQUE, sample.isOpaqueCube());
    }

    private static void message(EntityPlayer player, String langKey, Object... args) {
        player.sendStatusMessage(new TextComponentTranslation(langKey, args), true);
    }
}
