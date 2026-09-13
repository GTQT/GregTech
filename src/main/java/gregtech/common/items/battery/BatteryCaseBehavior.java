package gregtech.common.items.battery;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemCapabilityProvider;
import gregtech.api.items.metaitem.stats.IItemComponent;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.TextFormattingUtil;
import gregtech.integration.baubles.BaublesModule;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.CapabilityItemHandler;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.factory.ItemGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The component of the battery case, carrying the capability provider, the item behaviour and the item UI all at once.
 * <p>
 * The four batteries live in the plain NBT tag of the battery case itself rather than in {@code ForgeCaps}, see
 * {@link BatteryCaseInventory} for why.
 */
public class BatteryCaseBehavior implements IItemComponent, IItemCapabilityProvider, IItemBehaviour, ItemUIFactory {

    /** NBT key of the discharge mode, identical to the one used by GT batteries. */
    public static final String DISCHARGE_MODE_KEY = "DischargeMode";

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int SLOT_Y = 24;

    @Override
    public ICapabilityProvider createProvider(ItemStack itemStack) {
        return new BatteryCaseProvider(itemStack);
    }

    /** @return the contents of the battery case, or {@code null} when the stack is not a battery case. */
    @Nullable
    public static BatteryCaseInventory getInventory(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Object handler = stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler instanceof BatteryCaseInventory) {
            return (BatteryCaseInventory) handler;
        }
        return null;
    }

    @NotNull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player,
                                                    @NotNull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                boolean enabled = isInDischargeMode(stack);
                setInDischargeMode(stack, !enabled);
                player.sendStatusMessage(new TextComponentTranslation(
                        "metaitem.electric.discharge_mode." + (enabled ? "disabled" : "enabled")), true);
            }
            return success(stack);
        }
        if (!world.isRemote) {
            ItemGuiFactory.INSTANCE.open((EntityPlayerMP) player, hand);
        }
        return success(stack);
    }

    public static boolean isInDischargeMode(@NotNull ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(DISCHARGE_MODE_KEY);
    }

    /**
     * Sets the discharge mode.
     * <p>
     * The tag is never dropped completely when disabling the mode, since the battery slots live in the very same tag.
     */
    public static void setInDischargeMode(@NotNull ItemStack stack, boolean enabled) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            if (!enabled) {
                return;
            }
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }
        tag.setBoolean(DISCHARGE_MODE_KEY, enabled);
    }

    /**
     * In discharge mode the charge of the case is fed into the chargeable items of the player inventory, including the
     * baubles slots when baubles is installed. This mirrors {@code ElectricStats#onUpdate}.
     */
    @Override
    public void onUpdate(@NotNull ItemStack itemStack, @NotNull Entity entity) {
        if (entity.world.isRemote || !(entity instanceof EntityPlayer player)) {
            return;
        }
        if (!isInDischargeMode(itemStack)) {
            return;
        }
        IElectricItem source = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (source == null || source.getCharge() <= 0L) {
            return;
        }

        IInventory inventory = player.inventory;
        if (Loader.isModLoaded("baubles")) {
            inventory = BaublesModule.getBaublesWrappedInventory(player);
        }

        long transferLimit = source.getTransferLimit();
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack slotStack = inventory.getStackInSlot(i);
            if (slotStack.isEmpty() || slotStack == itemStack) {
                continue;
            }
            IElectricItem target = slotStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
            if (target == null || target.canProvideChargeExternally()) {
                continue;
            }
            long charged = chargeElectricItem(transferLimit, source, target);
            if (charged > 0L) {
                transferLimit -= charged;
                if (transferLimit <= 0L) {
                    break;
                }
            }
        }
    }

    /** The two stage transfer used by {@code ElectricStats#chargeElectricItem}. */
    private static long chargeElectricItem(long maxDischargeAmount, @NotNull IElectricItem source,
                                           @NotNull IElectricItem target) {
        long maxDischarged = source.discharge(maxDischargeAmount, source.getTier(), false, false, true);
        long maxReceived = target.charge(maxDischarged, source.getTier(), false, true);
        if (maxReceived > 0L) {
            long resultDischarged = source.discharge(maxReceived, source.getTier(), false, true, false);
            target.charge(resultDischarged, source.getTier(), false, false);
            return resultDischarged;
        }
        return 0L;
    }

    @Override
    public void addInformation(@NotNull ItemStack itemStack, @NotNull List<String> lines) {
        BatteryCaseInventory inventory = getInventory(itemStack);
        long charge = inventory == null ? 0L : inventory.getTotalCharge();
        long maxCharge = inventory == null ? 0L : inventory.getTotalMaxCharge();
        int tier = inventory == null ? -1 : inventory.getTier();
        int count = inventory == null ? 0 : inventory.getBatteryCount();

        lines.add(I18n.format("gregtech.tooltip.battery_case.charge",
                TextFormattingUtil.formatNumbers(charge), TextFormattingUtil.formatNumbers(maxCharge)));
        if (tier >= 0 && tier < GTValues.VN.length) {
            lines.add(I18n.format("gregtech.tooltip.battery_case.tier", GTValues.VNF[tier]));
        } else {
            lines.add(I18n.format("gregtech.tooltip.battery_case.tier_empty"));
        }
        lines.add(I18n.format(isInDischargeMode(itemStack) ? "metaitem.electric.discharge_mode.enabled" :
                "metaitem.electric.discharge_mode.disabled"));
        lines.add(I18n.format("gregtech.tooltip.battery_case.count", count, BatteryCaseInventory.SIZE));
        lines.add(I18n.format("gregtech.tooltip.battery_case.open_ui"));
        lines.add(I18n.format("gregtech.tooltip.battery_case.toggle_mode"));
        lines.add(I18n.format("gregtech.tooltip.battery_case.same_tier"));
    }

    @Nullable
    @Override
    public ModularPanel buildUI(@NotNull HandGuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                @NotNull UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();
        BatteryCaseInventory inventory = getInventory(stack);
        if (inventory == null) {
            return null;
        }

        SlotGroup group = new SlotGroup("battery_case_inventory", BatteryCaseInventory.SIZE);
        guiSyncManager.registerSlotGroup(group);

        List<ItemSlot> slots = new ArrayList<>();
        for (int i = 0; i < inventory.getSlots(); i++) {
            slots.add(new ItemSlot());
        }

        return GTGuis.createPanel(stack.getTranslationKey(), GUI_WIDTH, GUI_HEIGHT)
                .child(IKey.str(stack.getDisplayName()).asWidget()
                        .pos(5, 5)
                        .height(12))
                .child(new Grid()
                        .margin(0)
                        .leftRel(0.5f)
                        .top(SLOT_Y)
                        .coverChildren()
                        .mapTo(group.getRowSize(), slots, (index, value) -> value
                                .slot(SyncHandlers.itemSlot(inventory, index)
                                        .slotGroup(group)
                                        // The batteries are charged in place, so persist the contents on every change.
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> inventory
                                                .save()))
                                .background(GTGuiTextures.SLOT)))
                .bindPlayerInventory();
    }

    /**
     * Per stack capability carrier exposing both the aggregated {@code CAPABILITY_ELECTRIC_ITEM} view of the case and
     * the {@code ITEM_HANDLER_CAPABILITY} of its four slots.
     * <p>
     * It deliberately is not {@code INBTSerializable}: the contents are written to the plain tag of the stack by
     * {@link BatteryCaseInventory}, so they survive the share tag sync.
     */
    public static class BatteryCaseProvider implements ICapabilityProvider {

        private final BatteryCaseInventory inventory;
        private final BatteryCaseEnergyStorage energy;

        public BatteryCaseProvider(ItemStack itemStack) {
            this.inventory = new BatteryCaseInventory(itemStack);
            this.energy = new BatteryCaseEnergyStorage(inventory);
        }

        @Override
        public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM ||
                    capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        }

        @Nullable
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
            if (capability == GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM) {
                return GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM.cast(energy);
            }
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
            }
            return null;
        }
    }
}
