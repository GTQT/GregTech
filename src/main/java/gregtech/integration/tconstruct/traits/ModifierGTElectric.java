package gregtech.integration.tconstruct.traits;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.integration.tconstruct.handler.ToolCapabilityHandler;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.library.modifiers.IToolMod;
import slimeknights.tconstruct.library.modifiers.ModifierAspect;
import slimeknights.tconstruct.library.modifiers.ModifierTrait;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.tools.modifiers.ModMendingMoss;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * TiC modifier that allows tools to consume GT Electric energy (GTEU)
 * instead of durability damage.
 *
 * <p>
 * Converts durability damage to energy consumption at a rate of {@value #ENERGY_PER_DURABILITY}
 * EU per durability point. The tool can be charged in any GT charger or battery
 * buffer — just place it in an energy slot.
 *
 * <p>
 * <b>Energy storage</b> defaults to 100,000 EU (LV tier, transfer 32 EU/t). Call
 * {@link ElectricToolBuffer#setMaxChargeOverride} and {@link ElectricToolBuffer#setTier}
 * to adjust for higher-tier materials.
 *
 * <p>
 * <b>Incompatible with:</b>
 * <ul>
 *   <li>{@link ModMendingMoss} — only one repair/energy mechanic per tool</li>
 *   <li>{@link TraitUnbreakable} — zero-durability tools don't need electricity</li>
 * </ul>
 *
 * @see ElectricToolBuffer
 */
public class ModifierGTElectric extends ModifierTrait {

    public static final int COLOUR = 0x0A00C6;

    /** Default EU stored when no explicit max charge is set. */
    static final long DEFAULT_MAX_CHARGE = 100_000L;

    /** Default GT voltage tier (LV = 2). */
    static final int DEFAULT_TIER = 2;

    /** EU consumed per point of durability damage prevented. */
    static final double ENERGY_PER_DURABILITY = 300.0;

    // --------------- NBT keys ---------------
    static final String NBT_CHARGE = "GTElectricCharge";
    static final String NBT_MAX_CHARGE = "GTElectricMaxCharge";
    static final String NBT_TIER = "GTElectricTier";
    static final String NBT_INFINITE = "GTElectricInfinite";

    public ModifierGTElectric() {
        super("gregtech.electric", COLOUR);

        // Cost one modifier slot: remove the default free aspect, then
        // add a "first one is free" aspect so the first electric modifier
        // still costs exactly 1 slot.
        aspects.remove(aspects.lastIndexOf(ModifierAspect.freeModifier));
        addAspects(new ModifierAspect.FreeFirstModifierAspect(this, 1));

        // Register the capability bridge so GT energy infrastructure
        // (chargers, battery buffers) can see and interact with this tool.
        ToolCapabilityHandler.addModifierCap(this, ElectricToolBuffer::new);

        // Listen for tooltip events to display charge information.
        MinecraftForge.EVENT_BUS.register(this);
    }

    // ==================== Damage → Energy ====================

    @Override
    public int onToolDamage(ItemStack tool, int damage, int newDamage, EntityLivingBase entity) {
        if (newDamage <= 0) return 0;

        IElectricItem electric = tool.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electric == null) return newDamage;

        long energyCost = (long) Math.ceil(newDamage * ENERGY_PER_DURABILITY);
        long discharged = electric.discharge(energyCost, Integer.MAX_VALUE, true, false, false);

        // Only prevent damage when the full energy cost can be covered.
        if (discharged >= energyCost) {
            return 0;
        }

        return newDamage;
    }

    @Override
    public int getPriority() {
        // Run ahead of most damage modifiers so energy is checked first.
        return 25;
    }

    // ==================== Compatibility ====================

    @Override
    public boolean canApplyTogether(@NotNull IToolMod otherModifier) {
        // Mending moss and electricity are mutually exclusive.
        if (otherModifier instanceof ModMendingMoss) return false;
        // Unbreakable tools don't need electricity — and vice versa.
        if (otherModifier instanceof TraitUnbreakable) return false;
        return super.canApplyTogether(otherModifier);
    }

    // ==================== Client Tooltip ====================

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onToolTips(ItemTooltipEvent event) {
        if (!isToolWithTrait(event.getItemStack())) return;

        IElectricItem electric = event.getItemStack()
                .getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electric == null) return;

        event.getToolTip().add(I18n.format("gregtech.trait.electric.tooltip",
                electric.getCharge(),
                electric.getMaxCharge(),
                GTValues.VNF[electric.getTier()]));
    }

    // ==================== Electric Tool Buffer ====================

    /**
     * GT {@link IElectricItem} implementation that stores energy in the tool's
     * NBT. Provides charge / discharge capability so GT energy infrastructure
     * (chargers, battery buffers, etc.) can interact with the tool.
     *
     * <p>
     * <b>Energy tier and max charge</b> are configurable per-tool via
     * {@link #setTier} / {@link #setMaxChargeOverride}, allowing material-based
     * differentiation: higher-tier materials can grant more energy storage and
     * faster transfer.
     */
    public static class ElectricToolBuffer implements IElectricItem, ICapabilityProvider {

        private final ItemStack stack;
        private final List<BiConsumer<ItemStack, Long>> listeners = new ArrayList<>();

        public ElectricToolBuffer(ItemStack stack) {
            this.stack = stack;
        }

        // --- Configuration setters (call from material-trait logic) ---

        /** Override the default maximum charge for this tool. */
        public void setMaxChargeOverride(long maxCharge) {
            TagUtil.getToolTag(stack).setLong(NBT_MAX_CHARGE, maxCharge);
            listeners.forEach(l -> l.accept(stack, maxCharge));
        }

        /** Set the GT voltage tier (determines charger compatibility and transfer limit). */
        public void setTier(int tier) {
            TagUtil.getToolTag(stack).setInteger(NBT_TIER, tier);
        }

        /** Grant infinite energy — for creative or extreme-endgame use. */
        public void setInfiniteCharge(boolean infinite) {
            TagUtil.getToolTag(stack).setBoolean(NBT_INFINITE, infinite);
            listeners.forEach(l -> l.accept(stack, getMaxCharge()));
        }

        // --- IElectricItem ---

        @Override
        public boolean chargeable() {
            return true;
        }

        @Override
        public boolean canProvideChargeExternally() {
            return false;
        }

        @Override
        public void addChargeListener(BiConsumer<ItemStack, Long> chargeListener) {
            listeners.add(chargeListener);
        }

        @Override
        public long charge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean simulate) {
            if (stack.getCount() != 1) return 0;
            if (chargerTier < getTier()) return 0;
            if (amount <= 0) return 0;

            long canReceive = getMaxCharge() - getCharge();
            if (!ignoreTransferLimit) {
                amount = Math.min(amount, getTransferLimit());
            }
            long charged = Math.min(amount, canReceive);
            if (!simulate) {
                setCharge(getCharge() + charged);
            }
            return charged;
        }

        @Override
        public long discharge(long amount, int dischargerTier, boolean ignoreTransferLimit,
                              boolean externally, boolean simulate) {
            if (stack.getCount() != 1) return 0;
            if (externally && !canProvideChargeExternally()) return 0;
            if (dischargerTier < getTier()) return 0;
            if (amount <= 0) return 0;

            if (!ignoreTransferLimit) {
                amount = Math.min(amount, getTransferLimit());
            }
            long charge = getCharge();
            long discharged = Math.min(amount, charge);
            if (!simulate) {
                setCharge(charge - discharged);
            }
            return discharged;
        }

        @Override
        public long getTransferLimit() {
            return GTValues.V[getTier()];
        }

        @Override
        public long getMaxCharge() {
            NBTTagCompound tag = TagUtil.getToolTag(stack);
            if (tag.hasKey(NBT_MAX_CHARGE)) return tag.getLong(NBT_MAX_CHARGE);
            return DEFAULT_MAX_CHARGE;
        }

        @Override
        public long getCharge() {
            NBTTagCompound tag = TagUtil.getToolTag(stack);
            if (tag.getBoolean(NBT_INFINITE)) return getMaxCharge();
            return Math.min(tag.getLong(NBT_CHARGE), getMaxCharge());
        }

        @Override
        public int getTier() {
            NBTTagCompound tag = TagUtil.getToolTag(stack);
            if (tag.hasKey(NBT_TIER)) return tag.getInteger(NBT_TIER);
            return DEFAULT_TIER;
        }

        // --- ICapabilityProvider ---

        @Override
        public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM;
        }

        @Nullable
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
            return capability == GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM ? (T) this : null;
        }

        // --- Internal helpers ---

        private void setCharge(long charge) {
            TagUtil.getToolTag(stack).setLong(NBT_CHARGE, charge);
            listeners.forEach(l -> l.accept(stack, charge));
        }
    }
}
