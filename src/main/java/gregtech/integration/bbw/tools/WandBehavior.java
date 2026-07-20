package gregtech.integration.bbw.tools;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.items.toolitem.behavior.IToolBehavior;
import gregtech.api.unification.material.Materials;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import portablejim.bbw.basics.EnumFluidLock;
import portablejim.bbw.basics.EnumLock;
import portablejim.bbw.basics.Point3d;
import portablejim.bbw.basics.ReplacementTriplet;
import portablejim.bbw.core.WandWorker;
import portablejim.bbw.core.items.IWandItem;
import portablejim.bbw.core.wands.IWand;
import portablejim.bbw.shims.BasicWorldShim;
import portablejim.bbw.shims.CreativePlayerShim;
import portablejim.bbw.shims.IPlayerShim;
import portablejim.bbw.shims.IWorldShim;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class WandBehavior implements IToolBehavior {

    public static final WandBehavior INSTANCE = new WandBehavior();
    private static final int UNBREAKABLE_BLOCK_LIMIT = 512;

    private WandBehavior() {}

    private static boolean isUnbreakable(IGTTool gtTool, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.getBoolean(ToolHelper.UNBREAKABLE_KEY)) {
            return true;
        }
        return gtTool.getToolMaterial(stack) == Materials.Neutronium;
    }

    private static int getBlockPlaceLimit(IGTTool gtTool, ItemStack stack) {
        if (isUnbreakable(gtTool, stack)) {
            return UNBREAKABLE_BLOCK_LIMIT;
        }
        int toolDurability = gtTool.getTotalMaxDurability(stack);
        if (toolDurability <= 0) {
            return 64;
        }
        // Scale block place limit based on material's tool durability
        return Math.max(1, (toolDurability / 16) + 1);
    }

    private static void storeUndoData(ItemStack itemstack, ArrayList<Point3d> placedBlocks,
                                      ReplacementTriplet sourceTriplet, ItemStack sourceItems) {
        int[] placedIntArray = new int[placedBlocks.size() * 3];
        for (int i = 0; i < placedBlocks.size(); i++) {
            Point3d currentPoint = placedBlocks.get(i);
            placedIntArray[i * 3] = currentPoint.x;
            placedIntArray[i * 3 + 1] = currentPoint.y;
            placedIntArray[i * 3 + 2] = currentPoint.z;
        }
        NBTTagCompound itemNBT = itemstack.hasTagCompound() ? itemstack.getTagCompound() : new NBTTagCompound();
        NBTTagCompound bbwCompound = new NBTTagCompound();
        if (itemNBT.hasKey("bbw", Constants.NBT.TAG_COMPOUND)) {
            bbwCompound = itemNBT.getCompoundTag("bbw");
        }
        if (!bbwCompound.hasKey("mask", Constants.NBT.TAG_SHORT)) {
            bbwCompound.setShort("mask", (short) EnumLock.HORIZONTAL.mask);
        }
        if (!bbwCompound.hasKey("fluidmask", Constants.NBT.TAG_SHORT)) {
            bbwCompound.setShort("fluidmask", (short) EnumFluidLock.STOPAT.mask);
        }
        bbwCompound.setIntArray("lastPlaced", placedIntArray);
        bbwCompound.setString("lastBlock", sourceTriplet.target.toString());
        bbwCompound.setString("lastItemBlock",
                net.minecraft.item.Item.REGISTRY.getNameForObject(sourceItems.getItem()).toString());
        bbwCompound.setInteger("lastBlockMeta", sourceItems.getItemDamage());
        bbwCompound.setInteger("lastPerBlock", sourceItems.getCount());
        itemstack.setTagInfo("bbw", bbwCompound);
    }

    @NotNull
    @Override
    public EnumActionResult onItemUse(@NotNull EntityPlayer player, @NotNull World world, @NotNull BlockPos pos,
                                      @NotNull EnumHand hand, @NotNull EnumFacing facing, float hitX, float hitY,
                                      float hitZ) {
        ItemStack itemstack = player.getHeldItem(hand);
        if (itemstack.isEmpty() || !(itemstack.getItem() instanceof IWandItem wandItem)) {
            return EnumActionResult.PASS;
        }

        if (!(itemstack.getItem() instanceof IGTTool gtTool)) {
            return EnumActionResult.PASS;
        }

        if (!world.isRemote) {

            IPlayerShim playerShim = wandItem.getPlayerShim(player);
            if (player.capabilities.isCreativeMode) {
                playerShim = new CreativePlayerShim(player);
            }
            IWorldShim worldShim = new BasicWorldShim(world);

            int blockPlaceLimit = getBlockPlaceLimit(gtTool, itemstack);
            IWand wand = new GTWand(blockPlaceLimit);

            WandWorker worker = new WandWorker(wand, playerShim, worldShim);
            Point3d clickedPos = new Point3d(pos.getX(), pos.getY(), pos.getZ());

            ReplacementTriplet sourceTriplet = worker.getProperItemStack(worldShim, playerShim, clickedPos, hitX, hitY,
                    hitZ);

            if (sourceTriplet != null && sourceTriplet.items != null &&
                    sourceTriplet.items.getItem() instanceof ItemBlock) {
                ItemStack sourceItems = sourceTriplet.items;
                int numBlocks = Math.min(wand.getMaxBlocks(itemstack), playerShim.countItems(sourceItems));

                LinkedList<Point3d> blocks = worker.getBlockPositionList(clickedPos, facing, numBlocks,
                        wandItem.getMode(itemstack), wandItem.getFaceLock(itemstack),
                        wandItem.getFluidMode(itemstack));

                ArrayList<Point3d> placedBlocks = worker.placeBlocks(itemstack, blocks, sourceTriplet.target,
                        sourceItems, facing, hitX, hitY, hitZ);

                if (!placedBlocks.isEmpty()) {
                    // Damage tool for each block placed (in non-creative, non-unbreakable)
                    if (!player.capabilities.isCreativeMode && !isUnbreakable(gtTool, itemstack)) {
                        // +1 to ensure the tool breaks on last use
                        // GT's damageItem breaks the tool when newDurability > maxDamage
                        int remaining = itemstack.getMaxDamage() - itemstack.getItemDamage();
                        int damage = placedBlocks.size();
                        if (remaining - damage <= 0) {
                            damage = remaining + 1;
                        }
                        ToolHelper.damageItem(itemstack, player, damage);
                    }

                    // Store undo data in NBT
                    if (!itemstack.isEmpty()) {
                        storeUndoData(itemstack, placedBlocks, sourceTriplet, sourceItems);
                    }
                }
            }
        }
        return EnumActionResult.SUCCESS;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               @NotNull ITooltipFlag flag) {
        if (!(stack.getItem() instanceof IWandItem wandItem)) return;

        // Mode tooltip (reuse BBW lang keys)
        EnumLock mode = wandItem.getMode(stack);
        switch (mode) {
            case NORTHSOUTH:
                tooltip.add(I18n.format("bbw.hover.mode.northsouth"));
                break;
            case VERTICAL:
                tooltip.add(I18n.format("bbw.hover.mode.vertical"));
                break;
            case VERTICALEASTWEST:
                tooltip.add(I18n.format("bbw.hover.mode.verticaleastwest"));
                break;
            case EASTWEST:
                tooltip.add(I18n.format("bbw.hover.mode.eastwest"));
                break;
            case HORIZONTAL:
                tooltip.add(I18n.format("bbw.hover.mode.horizontal"));
                break;
            case VERTICALNORTHSOUTH:
                tooltip.add(I18n.format("bbw.hover.mode.verticalnorthsouth"));
                break;
            case NOLOCK:
                tooltip.add(I18n.format("bbw.hover.mode.nolock"));
                break;
        }

        // Fluid mode tooltip
        EnumFluidLock fluidMode = wandItem.getFluidMode(stack);
        switch (fluidMode) {
            case STOPAT:
                tooltip.add(I18n.format("bbw.hover.fluidmode.stopat"));
                break;
            case IGNORE:
                tooltip.add(I18n.format("bbw.hover.fluidmode.ignore"));
                break;
        }

        // Max blocks tooltip
        if (stack.getItem() instanceof IGTTool gtTool) {
            int blockPlaceLimit = getBlockPlaceLimit(gtTool, stack);
            if (isUnbreakable(gtTool, stack)) {
                tooltip.add(I18n.format("bbw.hover.maxblocks", blockPlaceLimit));
            } else {
                int remaining = stack.getMaxDamage() - stack.getItemDamage();
                int maxBlocks = Math.min(remaining, blockPlaceLimit);
                tooltip.add(I18n.format("bbw.hover.maxblocks", maxBlocks));
            }
        }
    }

    /**
         * GT-based wand implementation that uses GT durability instead of vanilla damage. Block placement damage is handled
         * externally via ToolHelper.damageItem after all blocks are placed.
         */
        private static class GTWand implements IWand {

        private final int blockLimit;

            private GTWand(int blockLimit) {
                this.blockLimit = blockLimit;
            }

        @Override
            public int getMaxBlocks(ItemStack itemStack) {
                if (itemStack.getItem() instanceof IGTTool gtTool) {
                    if (isUnbreakable(gtTool, itemStack)) {
                        return blockLimit;
                    }
                    int remaining = itemStack.getMaxDamage() - itemStack.getItemDamage();
                    return Math.min(remaining, blockLimit);
                }
                return blockLimit;
            }

            @Override
            public boolean placeBlock(ItemStack itemStack, EntityLivingBase entityLivingBase) {
                // Durability is handled externally after all blocks are placed
                return true;
            }
        }
}
