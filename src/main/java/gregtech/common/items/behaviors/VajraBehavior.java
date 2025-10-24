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
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
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
    private static final long NORMAL_ENERGY_COST = VA[ULV];  // 普通模式能耗
    private static final long SILKTOUCH_ENERGY_COST = VA[LV]; // 精准采集模式能耗
    private static final String MODE_TAG = "VajraMode"; // 0 = Normal, 1 = SilkTouch
    private final double baseAttackDamage;
    private final double additionalAttackDamage;

    public VajraBehavior() {
        this.baseAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberBaseDamage;
        this.additionalAttackDamage = ConfigHolder.tools.nanoSaber.nanoSaberDamageBoost;
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
        if (stack.getTagCompound() != null) {
            return stack.getTagCompound().getInteger(MODE_TAG);
        }
        return 0;
    }

    private void setMode(ItemStack stack, int mode) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        if (stack.getTagCompound() != null) {
            stack.getTagCompound().setInteger(MODE_TAG, mode);
        }
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
            long energyCost = getEnergyCostForMode(newMode);
            player.sendMessage(new TextComponentString(
                    TextFormatting.GREEN + "金刚杵模式已切换至: " + TextFormatting.YELLOW + modeName +
                            TextFormatting.GREEN + " (" + TextFormatting.RED + energyCost + " EU/次" +
                            TextFormatting.GREEN + ")"
            ));
        }
    }

    private boolean tryBreakBlock(World world, BlockPos pos, EntityPlayer player, ItemStack stack, boolean silkTouch) {
        int mode = silkTouch ? 1 : 0;
        long energyCost = getEnergyCostForMode(mode);

        // 实际消耗能量
        if (drainEnergy(stack, energyCost, true)) {
            drainEnergy(stack, energyCost, false);
        } else {
            if (!world.isRemote) {
                player.sendMessage(new TextComponentString(
                        TextFormatting.RED + "能量不足! 需要 " + energyCost + " EU"
                ));
            }
            return false;
        }

        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // 检查是否是空气或不可破坏的方块
        if (block.isAir(state, world, pos) || state.getBlockHardness(world, pos) < 0) {
            return false;
        }

        // 获取掉落物
        List<ItemStack> drops = new ArrayList<>();

        //MTE
        MetaTileEntity metaTileEntities = GTUtility.getMetaTileEntity(world, pos);
        if (metaTileEntities != null) {
            drops.add(metaTileEntities.getStackForm());
            metaTileEntities.onRemoval();
        }
        //普通方块
        else if (silkTouch) {
            // 精准采集模式 - 尝试获取方块本身
            ItemStack silkDrop = new ItemStack(block);
            drops.add(silkDrop);
        } else {
            // 普通模式 - 正常掉落
            drops = block.getDrops(world, pos, state, 0);
        }

        dropItemStackList(world, player, drops);

        // 破坏方块
        if (!world.isRemote) {
            world.setBlockToAir(pos);
        }

        return true;
    }

    public void dropItemStackList(World world, EntityPlayer player, List<ItemStack> drops) {
        // 给予玩家掉落物
        if (!drops.isEmpty() && !world.isRemote) {
            for (ItemStack drop : drops) {
                if (!player.inventory.addItemStackToInventory(drop)) {
                    // 背包满了，掉落在地上
                    EntityItem entityItem = new EntityItem(world,
                            player.posX, player.posY, player.posZ, drop);
                    world.spawnEntity(entityItem);
                }
            }
            player.openContainer.detectAndSendChanges();
        }
    }

    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (player.isSneaking()) {
            // Shift+右键切换模式
            toggleMode(heldItem, player);
        }
        return pass(player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (player.isSneaking()) {
            // Shift+右键切换模式
            toggleMode(heldItem, player);
        } else {
            if (!world.isRemote) {
                int mode = getMode(heldItem);
                if (mode == 0) {
                    if (tryBreakBlock(world, pos, player, heldItem, false)) {
                        player.swingArm(hand);
                        return EnumActionResult.SUCCESS;
                    }
                } else if (mode == 1) {
                    if (tryBreakBlock(world, pos, player, heldItem, true)) {
                        player.swingArm(hand);
                        return EnumActionResult.SUCCESS;
                    }

                }
            }
        }
        return EnumActionResult.SUCCESS;
    }

    // 添加工具提示显示当前模式
    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        int mode = getMode(itemStack);
        String modeName = getModeName(mode);
        long currentEnergyCost = getEnergyCostForMode(mode);
        long otherEnergyCost = getEnergyCostForMode((mode + 1) % 2);
        String otherModeName = getModeName((mode + 1) % 2);

        // 显示当前模式
        lines.add(TextFormatting.AQUA + "Shift+右键切换模式");
        lines.add(TextFormatting.GOLD + "工具模式: " + TextFormatting.YELLOW + modeName);

        // 显示当前模式耗电量
        lines.add(TextFormatting.GREEN + "当前耗电: " + TextFormatting.WHITE + currentEnergyCost + " EU/次");

        // 显示其他模式耗电量作为对比
        lines.add(
                TextFormatting.GRAY + otherModeName + "耗电: " + TextFormatting.DARK_GRAY + otherEnergyCost + " EU/次");

        // 显示能量信息
        IElectricItem electricItem = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem != null) {
            long charge = electricItem.getCharge();
            long maxCharge = electricItem.getMaxCharge();
            double percentage = (double) charge / maxCharge * 100;

            TextFormatting chargeColor;
            if (percentage > 75) chargeColor = TextFormatting.GREEN;
            else if (percentage > 25) chargeColor = TextFormatting.YELLOW;
            else chargeColor = TextFormatting.RED;

            lines.add(TextFormatting.BLUE + "能量: " + chargeColor + charge + TextFormatting.GRAY + " / " +
                    TextFormatting.BLUE + maxCharge + " EU " + TextFormatting.GRAY + "(" +
                    String.format("%.1f", percentage) + "%)");

            // 计算可用次数
            int availableUses = (int) (charge / currentEnergyCost);
            lines.add(TextFormatting.LIGHT_PURPLE + "可用次数: " + TextFormatting.WHITE + availableUses + " 次");
        }
    }
}
