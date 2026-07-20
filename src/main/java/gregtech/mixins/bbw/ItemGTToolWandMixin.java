package gregtech.mixins.bbw;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTTool;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.unification.material.Materials;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import org.spongepowered.asm.mixin.Mixin;
import portablejim.bbw.basics.EnumFluidLock;
import portablejim.bbw.basics.EnumLock;
import portablejim.bbw.core.items.IWandItem;
import portablejim.bbw.core.wands.IWand;
import portablejim.bbw.shims.BasicPlayerShim;
import portablejim.bbw.shims.IPlayerShim;

/**
 * Mixin to make GregTech tools with "wand" tool class implement BBW's IWandItem interface.
 * This allows GT wand tools to work with BBW's mode switching and keybinds.
 */
@Mixin(value = ItemGTTool.class, remap = false)
public abstract class ItemGTToolWandMixin implements IWandItem {

    private boolean gregtech$isWandTool() {
        ItemGTTool tool = (ItemGTTool) (Object) this;
        return tool.getToolClasses(ItemStack.EMPTY).contains("wand");
    }

    @Override
    public EnumLock getMode(ItemStack itemStack) {
        if (!gregtech$isWandTool()) return EnumLock.NOLOCK;
        NBTTagCompound itemBaseNBT = itemStack.getTagCompound();
        if (itemBaseNBT != null && itemBaseNBT.hasKey("bbw", Constants.NBT.TAG_COMPOUND)) {
            NBTTagCompound bbwNBT = itemBaseNBT.getCompoundTag("bbw");
            int mask = bbwNBT.hasKey("mask", Constants.NBT.TAG_SHORT) ? bbwNBT.getShort("mask") :
                    EnumLock.HORIZONTAL.mask;
            return EnumLock.fromMask(mask);
        }
        return EnumLock.HORIZONTAL;
    }

    @Override
    public void nextMode(ItemStack itemStack, EntityPlayer player) {
        if (!gregtech$isWandTool()) return;
        switch (getMode(itemStack)) {
            case VERTICAL:
                gregtech$setMode(itemStack, EnumLock.HORIZONTAL);
                break;
            case HORIZONTAL:
                gregtech$setMode(itemStack, EnumLock.VERTICAL);
                break;
            default:
                gregtech$setMode(itemStack, EnumLock.HORIZONTAL);
                break;
        }
    }

    @Override
    public EnumFluidLock getFluidMode(ItemStack itemStack) {
        if (!gregtech$isWandTool()) return EnumFluidLock.STOPAT;
        return EnumFluidLock.STOPAT;
    }

    @Override
    public void nextFluidMode(ItemStack itemStack, EntityPlayer player) {
        // Fixed to STOPAT - no cycling
    }

    @Override
    public IWand getWand() {
        if (!gregtech$isWandTool()) {
            // Non-wand GT tools: return IWand with 0 maxBlocks to suppress preview
            return new IWand() {

                @Override
                public int getMaxBlocks(ItemStack itemStack) {
                    return 0;
                }

                @Override
                public boolean placeBlock(ItemStack itemStack, EntityLivingBase entity) {
                    return false;
                }
            };
        }
        return new IWand() {

            @Override
            public int getMaxBlocks(ItemStack itemStack) {
                if (itemStack.getItem() instanceof IGTTool) {
                    IGTTool gtTool = (IGTTool) itemStack.getItem();
                    int blockPlaceLimit = gregtech$getBlockPlaceLimit(gtTool, itemStack);
                    if (gregtech$isUnbreakable(gtTool, itemStack)) {
                        return blockPlaceLimit;
                    }
                    int remaining = itemStack.getMaxDamage() - itemStack.getItemDamage();
                    return Math.min(remaining, blockPlaceLimit);
                }
                return 64;
            }

            @Override
            public boolean placeBlock(ItemStack itemStack, EntityLivingBase entity) {
                // Durability is handled by WandBehavior after all blocks are placed
                return true;
            }
        };
    }

    @Override
    public EnumLock getFaceLock(ItemStack itemStack) {
        if (!gregtech$isWandTool()) return EnumLock.NOLOCK;
        return EnumLock.NOLOCK;
    }

    @Override
    public IPlayerShim getPlayerShim(EntityPlayer player) {
        return new BasicPlayerShim(player);
    }

    private static final int UNBREAKABLE_BLOCK_LIMIT = 512;

    private static boolean gregtech$isUnbreakable(IGTTool gtTool, ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.getBoolean(ToolHelper.UNBREAKABLE_KEY)) {
            return true;
        }
        return gtTool.getToolMaterial(stack) == Materials.Neutronium;
    }

    private static int gregtech$getBlockPlaceLimit(IGTTool gtTool, ItemStack stack) {
        if (gregtech$isUnbreakable(gtTool, stack)) {
            return UNBREAKABLE_BLOCK_LIMIT;
        }
        int toolDurability = gtTool.getTotalMaxDurability(stack);
        if (toolDurability <= 0) {
            return 64;
        }
        return Math.max(1, (toolDurability / 16) + 1);
    }

    private void gregtech$setMode(ItemStack item, EnumLock mode) {
        NBTTagCompound tagCompound = item.hasTagCompound() ? item.getTagCompound() : new NBTTagCompound();
        NBTTagCompound bbwCompound = new NBTTagCompound();
        if (tagCompound.hasKey("bbw", Constants.NBT.TAG_COMPOUND)) {
            bbwCompound = tagCompound.getCompoundTag("bbw");
        }
        short shortMask = (short) (mode.mask & 7);
        bbwCompound.setShort("mask", shortMask);
        tagCompound.setTag("bbw", bbwCompound);
        item.setTagCompound(tagCompound);
    }
}
