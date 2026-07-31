package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.metaitem.stats.IEnchantabilityHelper;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.SPacketBlockChange;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static gregtech.api.GTValues.*;

public class VajraBehavior implements IItemBehaviour, IEnchantabilityHelper {

    protected static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A288-9C13A33DB5CF");
    protected static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("FA233E1C-4180-4288-B01B-BCCE9785ACA3");
    private static final long NORMAL_ENERGY_COST = VA[ULV];
    private static final long SILKTOUCH_ENERGY_COST = VA[LV];
    private static final String MODE_TAG = "VajraMode"; // 0 = Normal, 1 = SilkTouch
    private final double baseAttackDamage;
    private final double additionalAttackDamage;

    public VajraBehavior(int tier) {
        this.baseAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberBaseDamage * tier;
        this.additionalAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberDamageBoost * tier;
    }

    /**
     * Checks if the given item stack has Vajra behavior attached.
     * Used by the left-click event handler to identify Vajra tools.
     */
    public static boolean isVajra(@NotNull ItemStack stack) {
        if (stack.isEmpty()) return false;
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;
        // Vajra has a unique pattern: it has both electric capability and mode NBT
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(MODE_TAG);
    }

    /**
     * Left-click block breaking logic — ported from Laser Destroyer's approach.
     * Uses removedByPlayer + onPlayerDestroy for proper block removal,
     * sends SPacketBlockChange for immediate client sync.
     */
    @SuppressWarnings("deprecation")
    public static boolean breakBlock(@NotNull ItemStack stack, @NotNull EntityPlayer player,
                                     @NotNull World world, @NotNull BlockPos pos,
                                     boolean silkTouch, long energyCost) {
        if (world.isRemote) return true;

        // Energy check
        if (!player.isCreative() && !drainEnergy(stack, energyCost, false)) {
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.AIR || state.getBlockHardness(world, pos) < 0) {
            return false;
        }

        // Collect drops
        List<ItemStack> drops = new ArrayList<>();
        MetaTileEntity mte = GTUtility.getMetaTileEntity(world, pos);
        if (mte != null) {
            drops.add(mte.getStackForm());
            mte.onRemoval();
        } else if (silkTouch) {
            drops.add(getSilkDrops(state));
        } else {
            drops = block.getDrops(world, pos, state, 0);
        }

        // Play break sound
        var soundType = block.getSoundType(state, world, pos, player);
        world.playSound(player, pos, soundType.getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Sync to client immediately
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketBlockChange(world, pos));
        }

        // Proper block removal
        boolean removed = block.removedByPlayer(state, world, pos, player, !silkTouch);
        if (removed) {
            block.onPlayerDestroy(world, pos, state);
        } else {
            block.onPlayerDestroy(world, pos, state);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }

        // Spawn drops on ground
        for (ItemStack drop : drops) {
            if (player.isCreative()) continue;
            float f = 0.7f;
            double dx = world.rand.nextFloat() * f + (1.0f - f) * 0.5;
            double dy = world.rand.nextFloat() * f + (1.0f - f) * 0.5;
            double dz = world.rand.nextFloat() * f + (1.0f - f) * 0.5;
            EntityItem entityItem = new EntityItem(world,
                    pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz, drop);
            entityItem.setDefaultPickupDelay();
            world.spawnEntity(entityItem);
        }

        return true;
    }

    private static ItemStack getSilkDrops(IBlockState state) {
        try {
            var method = state.getBlock().getClass().getMethod("getSilkTouchDrop", IBlockState.class);
            method.setAccessible(true);
            return (ItemStack) method.invoke(state.getBlock(), state);
        } catch (Exception e) {
            return new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
        }
    }

    private static boolean drainEnergy(@NotNull ItemStack stack, long amount, boolean simulate) {
        IElectricItem electricItem = stack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;
        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public Multimap<String, AttributeModifier> getAttributeModifiers(EntityEquipmentSlot slot, ItemStack stack) {
        HashMultimap<String, AttributeModifier> modifiers = HashMultimap.create();
        if (slot == EntityEquipmentSlot.MAINHAND) {
            double attackDamage = baseAttackDamage + getMode(stack) * additionalAttackDamage;
            modifiers.put(SharedMonsterAttributes.ATTACK_SPEED.getName(),
                    new AttributeModifier(ATTACK_SPEED_MODIFIER, "Weapon modifier", -2.0, 0));
            modifiers.put(SharedMonsterAttributes.ATTACK_DAMAGE.getName(),
                    new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Weapon Modifier", attackDamage, 0));
        }
        return modifiers;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getItemEnchantability(ItemStack stack) {
        return 33;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment.type == null) {
            return false;
        }
        return enchantment != Enchantments.UNBREAKING &&
                enchantment != Enchantments.MENDING &&
                enchantment.type.canEnchantItem(Items.IRON_SWORD);
    }

    private int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger(MODE_TAG);
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger(MODE_TAG, mode);
    }

    private long getEnergyCostForMode(int mode) {
        return mode == 0 ? NORMAL_ENERGY_COST : SILKTOUCH_ENERGY_COST;
    }

    private String getModeName(int mode) {
        return mode == 0 ? "普通模式" : "精准采集模式";
    }

    private void toggleMode(ItemStack stack, EntityPlayer player) {
        int currentMode = getMode(stack);
        int newMode = (currentMode + 1) % 2;
        setMode(stack, newMode);

        if (!player.world.isRemote) {
            String modeName = getModeName(newMode);
            player.sendMessage(new TextComponentTranslation(
                    "behavior.vajra.mode_switched",
                    modeName
            ));
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (player.isSneaking()) {
            toggleMode(heldItem, player);
        }
        return pass(player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (player.isSneaking()) {
            toggleMode(heldItem, player);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        int mode = getMode(itemStack);
        String modeName = getModeName(mode);
        lines.add(TextFormatting.GOLD + I18n.format("behavior.vajra.tooltip.current_mode", modeName));
        lines.add(TextFormatting.AQUA + I18n.format("behavior.vajra.tooltip.mode_switch"));
    }
}
