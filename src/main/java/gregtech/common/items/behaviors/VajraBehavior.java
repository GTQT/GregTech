package gregtech.common.items.behaviors;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IEnchantabilityHelper;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.common.ConfigHolder;
import gregtech.common.mui.widget.GTTextFieldWidget;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static gregtech.api.GTValues.*;

public class VajraBehavior implements IItemBehaviour, IEnchantabilityHelper, ItemUIFactory {

    protected static final UUID ATTACK_DAMAGE_MODIFIER = UUID.fromString("CB3F55D3-645C-4F38-A288-9C13A33DB5CF");
    protected static final UUID ATTACK_SPEED_MODIFIER = UUID.fromString("FA233E1C-4180-4288-B01B-BCCE9785ACA3");
    private static final long NORMAL_ENERGY_COST = VA[ULV];
    private static final long SILKTOUCH_ENERGY_COST = VA[LV];
    private static final String MODE_TAG = "VajraMode"; // 0 = Normal, 1 = SilkTouch
    private static final String MINING_TICKS_TAG = "VajraMiningTicks";

    /** Ticks per block. 0 breaks blocks instantly, 20 is one second per block. */
    public static final int MIN_MINING_TICKS = 0;
    public static final int MAX_MINING_TICKS = 200;
    public static final int DEFAULT_MINING_TICKS = 0;

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 120;

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
     * @return the mining time in ticks required for the stack, 0 for instant breaking.
     *         The interval is only configurable through the settings UI; stacks without the tag
     *         fall back to {@link #DEFAULT_MINING_TICKS}.
     */
    public static int getMiningTicks(@NotNull ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(MINING_TICKS_TAG)) {
            return MathHelper.clamp(stack.getTagCompound().getInteger(MINING_TICKS_TAG), MIN_MINING_TICKS,
                    MAX_MINING_TICKS);
        }
        return DEFAULT_MINING_TICKS;
    }

    /**
     * Stores the mining interval set in the settings UI.
     */
    public static void setMiningTicks(@NotNull ItemStack stack, int ticks) {
        int clamped = MathHelper.clamp(ticks, MIN_MINING_TICKS, MAX_MINING_TICKS);
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(MINING_TICKS_TAG, clamped);
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
        return stack.getTagCompound().getInteger(MODE_TAG) == 1 ? 1 : 0;
    }

    private static String getModeKey(int mode) {
        return mode == 1 ? "behavior.vajra.mode.silk_touch" : "behavior.vajra.mode.normal";
    }

    /**
     * Writes the mode tag. The silk touch enchantment is mirrored onto the item because
     * {@code harvestBlock} only honours the real enchantment, and the operation is idempotent
     * so it can safely be called from both the settings UI and its synchronisation handlers.
     * Only the silk touch enchantment is added/removed — other enchantments stay untouched.
     */
    public static void setMode(@NotNull ItemStack stack, int mode) {
        int newMode = mode == 1 ? 1 : 0;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(MODE_TAG, newMode);
        syncSilkTouchEnchantment(stack, newMode == 1);
    }

    private static void syncSilkTouchEnchantment(@NotNull ItemStack stack, boolean silkTouch) {
        if (silkTouch) {
            if (EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) <= 0) {
                stack.addEnchantment(Enchantments.SILK_TOUCH, 1);
            }
            return;
        }
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);
        if (enchantments.containsKey(Enchantments.SILK_TOUCH)) {
            enchantments.remove(Enchantments.SILK_TOUCH);
            EnchantmentHelper.setEnchantments(enchantments, stack);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        // The mining interval is only adjustable from the settings UI, opened by sneak + right-click
        if (player.isSneaking()) {
            openSettings(world, player, hand, heldItem);
        }
        return pass(heldItem);
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (player.isSneaking()) {
            openSettings(world, player, hand, player.getHeldItem(hand));
        }
        return EnumActionResult.SUCCESS;
    }

    private static void openSettings(World world, EntityPlayer player, EnumHand hand, ItemStack stack) {
        // MetaItemGuiFactory casts to EntityPlayerMP, so opening must only happen server side
        if (world.isRemote || !isVajra(stack)) return;
        MetaItemGuiFactory.open(player, hand);
    }

    @Override
    public void addInformation(ItemStack itemStack, List<String> lines) {
        String modeName = I18n.format(getModeKey(getMode(itemStack)));
        lines.add(TextFormatting.GOLD + I18n.format("behavior.vajra.tooltip.current_mode", modeName));
        lines.add(TextFormatting.AQUA + I18n.format("behavior.vajra.tooltip.mode_switch"));

        int ticks = getMiningTicks(itemStack);
        String interval = ticks <= 0 ? I18n.format("behavior.vajra.tooltip.instant")
                : I18n.format("behavior.vajra.tooltip.seconds", String.format("%.2f", ticks / 20.0));
        lines.add(TextFormatting.AQUA + I18n.format("behavior.vajra.tooltip.speed", interval));
    }

    private static int getIntervalStep(@NotNull MouseData data) {
        int step = 1;
        if (data.shift) step *= 10;
        if (data.ctrl) step *= 50;
        return step;
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();

        IntSyncValue intervalValue = new IntSyncValue(
                () -> getMiningTicks(stack),
                ticks -> setMiningTicks(stack, ticks));
        IntSyncValue modeValue = new IntSyncValue(
                () -> getMode(stack),
                mode -> setMode(stack, mode));

        guiSyncManager.syncValue("mining_interval", intervalValue);
        guiSyncManager.syncValue("mining_mode", modeValue);

        return GTGuis.createPanel(stack, PANEL_WIDTH, PANEL_HEIGHT)
                .child(GTGuis.popupTitle(stack, stack.getTranslationKey() + ".name"))
                .child(Flow.column()
                        .top(26)
                        .margin(7, 0)
                        .widthRel(1f)
                        .coverChildrenHeight()
                        .child(IKey.lang("behavior.vajra.gui.interval")
                                .asWidget()
                                .marginBottom(3)
                                .color(Color.WHITE.darker(1)))
                        .child(Flow.row()
                                .widthRel(1f)
                                .coverChildrenHeight()
                                .marginBottom(4)
                                .child(new ButtonWidget<>()
                                        .left(0)
                                        .width(18)
                                        .background(GTGuiTextures.MC_BUTTON)
                                        .overlay(GTGuiTextures.MINUS)
                                        .disableHoverBackground()
                                        .tooltip(tooltip -> tooltip
                                                .addLine(IKey.lang("behavior.vajra.gui.decrease")))
                                        .onMousePressed(mouseButton -> {
                                            int step = getIntervalStep(MouseData.create(mouseButton));
                                            intervalValue.setIntValue(MathHelper.clamp(
                                                    intervalValue.getIntValue() - step,
                                                    MIN_MINING_TICKS, MAX_MINING_TICKS), true, true);
                                            return true;
                                        }))
                                .child(new GTTextFieldWidget()
                                        .left(18)
                                        .right(18)
                                        .setPostFix(IKey.lang("behavior.vajra.gui.unit"))
                                        .setTextColor(Color.WHITE.darker(1))
                                        .setNumbers(MIN_MINING_TICKS, MAX_MINING_TICKS)
                                        .setMaxLength(3)
                                        .value(intervalValue)
                                        .background(GTGuiTextures.DISPLAY))
                                .child(new ButtonWidget<>()
                                        .right(0)
                                        .width(18)
                                        .background(GTGuiTextures.MC_BUTTON)
                                        .overlay(GTGuiTextures.PLUS)
                                        .disableHoverBackground()
                                        .tooltip(tooltip -> tooltip
                                                .addLine(IKey.lang("behavior.vajra.gui.increase")))
                                        .onMousePressed(mouseButton -> {
                                            int step = getIntervalStep(MouseData.create(mouseButton));
                                            intervalValue.setIntValue(MathHelper.clamp(
                                                    intervalValue.getIntValue() + step,
                                                    MIN_MINING_TICKS, MAX_MINING_TICKS), true, true);
                                            return true;
                                        })))
                        .child(IKey.lang("behavior.vajra.gui.seconds",
                                () -> new Object[] { String.format("%.2f", intervalValue.getIntValue() / 20.0) })
                                .asWidget()
                                .marginBottom(6)
                                .color(Color.WHITE.darker(1)))
                        .child(new ToggleButton()
                                .widthRel(1f)
                                .height(20)
                                .marginBottom(4)
                                .background(GTGuiTextures.MC_BUTTON)
                                .selectedBackground(GTGuiTextures.MC_BUTTON_DISABLED)
                                .disableHoverBackground()
                                .value(new BoolValue.Dynamic(
                                        () -> modeValue.getIntValue() == 1,
                                        selected -> modeValue.setIntValue(selected ? 1 : 0, true, true)))
                                .overlay(IKey.lang(() -> getModeKey(modeValue.getIntValue()))))
                        .child(IKey.lang("behavior.vajra.gui.hint")
                                .asWidget()
                                .color(Color.WHITE.darker(1))));
    }
}
