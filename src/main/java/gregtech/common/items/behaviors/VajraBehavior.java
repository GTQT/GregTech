package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IEnchantabilityHelper;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.common.ConfigHolder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
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

import java.util.List;
import java.util.UUID;

import static gregtech.api.GTValues.*;

public class VajraBehavior implements IItemBehaviour, IEnchantabilityHelper {

    protected static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A288-9C13A33DB5CF");
    protected static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("FA233E1C-4180-4288-B01B-BCCE9785ACA3");
    private static final long NORMAL_ENERGY_COST = VA[ULV];
    private static final long SILKTOUCH_ENERGY_COST = VA[LV];
    private static final String MODE_TAG = "VajraMode"; // 0 = Normal, 1 = SilkTouch
    private static final String MINING_TIER_TAG = "VajraMiningTier";
    private final double baseAttackDamage;
    private final double additionalAttackDamage;

    public VajraBehavior(int tier) {
        this.baseAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberBaseDamage * tier;
        this.additionalAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberDamageBoost * tier;
    }

    /**
     * Checks if the given item stack is one of the Vajra tools.
     * Used by the left-click event handler and the client dig-interaction mixin to identify
     * Vajra tools, whose blocks are broken through {@link #breakBlock} instead of vanilla digging.
     */
    public static boolean isVajra(@NotNull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof MetaItem<?> metaItem)) return false;
        MetaItem<?>.MetaValueItem valueItem = metaItem.getItem(stack);
        return valueItem != null && valueItem.unlocalizedName.startsWith("vajra");
    }

    /**
     * @return whether the stack's mode tag is set to silk touch mode; defaults to normal mode
     *         when the tag is absent
     */
    public static boolean isSilkTouchMode(@NotNull ItemStack stack) {
        return getMode(stack) == 1;
    }

    /**
     * @return the energy cost of a single block break for the stack's current mode
     */
    public static long getEnergyCost(@NotNull ItemStack stack) {
        return isSilkTouchMode(stack) ? SILKTOUCH_ENERGY_COST : NORMAL_ENERGY_COST;
    }

    /**
     * @return the selected mining speed tier index, saved on the item
     */
    public static int getMiningTier(@NotNull ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger(MINING_TIER_TAG);
    }

    private static void setMiningTier(@NotNull ItemStack stack, int index) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(MINING_TIER_TAG, index);
    }

    /**
     * @return the mining time in ticks required by the selected tier, 0 for instant breaking
     */
    public static int getMiningTicks(@NotNull ItemStack stack) {
        int[] tiers = ConfigHolder.tools.vajraMiningTiers;
        if (tiers.length == 0) return 0;
        return tiers[Math.max(0, Math.min(getMiningTier(stack), tiers.length - 1))];
    }

    private static int cycleMiningTier(@NotNull ItemStack stack) {
        int[] tiers = ConfigHolder.tools.vajraMiningTiers;
        if (tiers.length == 0) {
            setMiningTier(stack, 0);
            return 0;
        }
        int next = (getMiningTier(stack) + 1) % tiers.length;
        setMiningTier(stack, next);
        return next;
    }

    private static String speedTierNameKey(int index) {
        return index >= 0 && index <= 3 ? "behavior.vajra.speed.name." + index
                : "behavior.vajra.speed.name.generic";
    }

    /**
     * Left-click block breaking logic — ported from Laser Destroyer's approach.
     * Uses removedByPlayer + harvestBlock so that vanilla and Forge drop logic runs,
     * sends SPacketBlockChange for immediate client sync.
     *
     * <p>Drops are deliberately not collected and spawned by hand: doing so bypassed all
     * tile-entity aware drop handling (containers such as chests/shulker boxes kept their
     * full inventory, GT machines lost their item data and covers). Passing the tool stack
     * down to {@code harvestBlock} also lets the enchantment-driven silk touch branch and
     * the {@code canSilkHarvest} check work as in vanilla.
     */
    @SuppressWarnings("deprecation")
    public static boolean breakBlock(@NotNull ItemStack stack, @NotNull EntityPlayer player,
                                     @NotNull World world, @NotNull BlockPos pos, long energyCost) {
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

        // Play break sound
        var soundType = block.getSoundType(state, world, pos, player);
        world.playSound(player, pos, soundType.getBreakSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);

        // Sync to client immediately
        if (player instanceof EntityPlayerMP) {
            ((EntityPlayerMP) player).connection.sendPacket(new SPacketBlockChange(world, pos));
        }

        // Proper block removal. willHarvest is always true: harvestBlock below is what
        // actually destroys the block and spawns the drops.
        if (!block.removedByPlayer(state, world, pos, player, true)) {
            return false;
        }

        block.onPlayerDestroy(world, pos, state);
        block.harvestBlock(world, player, pos, state, world.getTileEntity(pos), stack);

        // Containers such as chests overwrite the block state instead of removing it
        if (world.getBlockState(pos) != Blocks.AIR.getDefaultState()) {
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }

        // Energy is only spent once the block is actually gone
        if (!player.isCreative()) {
            drainEnergy(stack, energyCost, false);
        }

        return true;
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

    private static int getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return 0;
        return stack.getTagCompound().getInteger(MODE_TAG);
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger(MODE_TAG, mode);
    }

    private String getModeName(int mode) {
        return mode == 0 ? "普通模式" : "精准采集模式";
    }

    private void toggleMode(ItemStack stack, EntityPlayer player) {
        int currentMode = getMode(stack);
        int newMode = (currentMode + 1) % 2;
        setMode(stack, newMode);

        // harvestBlock picks silk touch up from the tool's enchantment, so the mode has to be
        // mirrored onto the item; leaving a stale SILK_TOUCH enchantment behind would keep
        // silk-touching while the tool reports normal mode.
        if (newMode == 1) {
            stack.addEnchantment(Enchantments.SILK_TOUCH, 1);
        } else if (stack.getTagCompound() != null) {
            stack.getTagCompound().removeTag("ench");
        }

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
        } else if (!world.isRemote) {
            int tierIndex = cycleMiningTier(heldItem);
            double seconds = getMiningTicks(heldItem) / 20.0;
            player.sendMessage(new TextComponentTranslation("behavior.vajra.speed.changed",
                    new TextComponentTranslation(speedTierNameKey(tierIndex), tierIndex + 1),
                    String.format("%.2f", seconds)));
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

        int tier = getMiningTier(itemStack);
        int ticks = getMiningTicks(itemStack);
        String seconds = ticks <= 0 ? "0.00" : String.format("%.2f", ticks / 20.0);
        lines.add(TextFormatting.AQUA + I18n.format("behavior.vajra.tooltip.speed",
                I18n.format(speedTierNameKey(tier), tier + 1), seconds));
    }
}
