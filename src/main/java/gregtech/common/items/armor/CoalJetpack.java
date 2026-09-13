package gregtech.common.items.armor;

import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.items.armor.ArmorMetaItem.ArmorMetaValueItem;
import gregtech.api.items.armor.ArmorUtils;
import gregtech.api.items.armor.ISpecialArmorLogic;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemCapabilityProvider;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.items.metaitem.stats.IItemHUDProvider;
import gregtech.api.items.metaitem.stats.ISubItemHandler;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.ModHandler;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.GradientUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.common.mui.widget.GTFluidSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.factory.ItemGuiFactory;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * The coal jetpack: a bronze age boiler strapped to the back.
 * <p>
 * It works exactly like the coal boiler of the steam age, only in portable form: put a fuel and a water container into
 * its UI, the fuel heats the boiler up, the heat turns water into steam, and the steam is what keeps the wearer in the
 * air while the jump key is held. Flying costs {@value #STEAM_PER_TICK} mB of steam per tick, and the steam only has
 * enough pressure above {@value #MIN_FLIGHT_TEMPERATURE} K, where the boiler also lifts the wearer faster the hotter it
 * runs.
 * <p>
 * Every part of the boiler simulation (heat up, cool down, fuel consumption, steam generation) follows
 * {@code SteamBoiler} and {@code SteamCoalBoiler}, with two differences: it never explodes when it runs dry, and it
 * only turns water into steam while there is room left in the steam tank, so nothing is wasted while idling.
 */
public class CoalJetpack implements ISpecialArmorLogic, IJetpack, IItemHUDProvider {

    public static final int WATER_CAPACITY = 16000;
    public static final int STEAM_CAPACITY = 16000;

    /** Steam consumed per tick of flight. */
    public static final int STEAM_PER_TICK = 1;

    /** The boiler only lifts the wearer once it runs hotter than this. */
    public static final int MIN_FLIGHT_TEMPERATURE = 373;

    /** The values of the low pressure coal boiler. */
    public static final int MAX_TEMPERATURE = 500;
    public static final int BASE_STEAM_OUTPUT = 120;
    public static final int COOLDOWN_INTERVAL = 45;
    public static final int COOL_DOWN_RATE = 1;
    public static final int MIN_STEAM_TEMPERATURE = 100;

    private static final String NBT_TEMPERATURE = "CurrentTemperature";
    private static final String NBT_FUEL_BURN_TIME_LEFT = "FuelBurnTimeLeft";
    private static final String NBT_FUEL_MAX_BURN_TIME = "FuelMaxBurnTime";
    private static final String NBT_TIME_BEFORE_COOLING_DOWN = "TimeBeforeCoolingDown";
    private static final String NBT_HAS_NO_WATER = "HasNoWater";
    private static final String NBT_WATER_TANK = "WaterTank";
    private static final String NBT_STEAM_TANK = "SteamTank";

    /** Lift multiplier of the flight currently being performed, see {@link #performFlying}. */
    private double flightSpeedMultiplier = 1.0D;

    @SideOnly(Side.CLIENT)
    private ArmorUtils.ModularHUD HUD;

    public CoalJetpack() {
        if (ArmorUtils.SIDE.isClient()) {
            // noinspection NewExpressionSideOnly
            HUD = new ArmorUtils.ModularHUD();
        }
    }

    // ------------------------------------------------------------------ boiler

    @Override
    public void onArmorTick(@NotNull World world, @NotNull EntityPlayer player, @NotNull ItemStack stack) {
        if (getState(stack) == null) {
            return;
        }
        // No hover mode: the steam is only there to push the wearer up while the jump key is held.
        performFlying(player, false, false, stack);
    }

    /**
     * Runs the boiler: heats up on fuel, boils water into steam and takes the water from the container slot.
     * <p>
     * This lives in the inventory tick rather than in the armor tick, so that the UI works the same whether the
     * jetpack is worn or just held, see {@link Behaviour#onUpdate}.
     */
    private static void tickBoiler(@NotNull World world, @NotNull ItemStack stack) {
        State state = getState(stack);
        if (state == null) {
            return;
        }
        state.updateTemperature(world.getTotalWorldTime());
        if (world.getTotalWorldTime() % 10 == 0) {
            state.generateSteam();
        }
        GTTransferUtils.fillInternalTankFromFluidContainer(state.waterTank, state.inventory,
                State.SLOT_FLUID_IN, State.SLOT_FLUID_OUT);
        state.tryConsumeNewFuel();
        state.save();
    }

    // ------------------------------------------------------------------ flight

    @Override
    public int getEnergyPerUse() {
        return STEAM_PER_TICK;
    }

    /**
     * The boiler has to be above {@value #MIN_FLIGHT_TEMPERATURE} K before the steam is of any use, and the hotter it
     * runs, the faster it lifts the wearer: {@link #getVerticalSpeed()} goes from 1x at the minimum temperature up to
     * 2x at {@value #MAX_TEMPERATURE} K.
     */
    @Override
    public boolean canUseEnergy(@NotNull ItemStack stack, int amount) {
        State state = getState(stack);
        return state != null && state.currentTemperature >= MIN_FLIGHT_TEMPERATURE &&
                state.steamTank.getFluidAmount() >= amount;
    }

    /**
     * Sets the lift multiplier of the current flight from the temperature of the boiler before flying.
     * <p>
     * {@link IJetpack} reads its speeds from the behavior itself rather than from the stack, so the value has to be
     * put aside for the duration of one flight. It is written and read within the same tick, so jetpacks of several
     * players never see each other's multiplier.
     */
    @Override
    public void performFlying(@NotNull EntityPlayer player, boolean hover, boolean cancelInertia,
                              @NotNull ItemStack stack) {
        State state = getState(stack);
        this.flightSpeedMultiplier = state == null ? 1.0D : getSpeedMultiplier(state.currentTemperature);
        IJetpack.super.performFlying(player, hover, cancelInertia, stack);
    }

    @Override
    public double getVerticalSpeed() {
        return IJetpack.super.getVerticalSpeed() * flightSpeedMultiplier;
    }

    @Override
    public double getVerticalAcceleration() {
        return IJetpack.super.getVerticalAcceleration() * flightSpeedMultiplier;
    }

    /** @return 1x at {@value #MIN_FLIGHT_TEMPERATURE} K, growing to 2x at {@value #MAX_TEMPERATURE} K. */
    private static double getSpeedMultiplier(int temperature) {
        double heat = (temperature - MIN_FLIGHT_TEMPERATURE) /
                (double) (MAX_TEMPERATURE - MIN_FLIGHT_TEMPERATURE);
        return 1.0D + MathHelper.clamp(heat, 0.0D, 1.0D);
    }

    @Override
    public void drainEnergy(@NotNull ItemStack stack, int amount) {
        State state = getState(stack);
        if (state == null) {
            return;
        }
        state.steamTank.drain(amount, true);
        state.save();
    }

    @Override
    public boolean hasEnergy(@NotNull ItemStack stack) {
        State state = getState(stack);
        return state != null && state.steamTank.getFluidAmount() > 0;
    }

    /** Steam puffs, not smoke: it is a boiler, not a combustion engine. */
    @Override
    public EnumParticleTypes getParticle() {
        return EnumParticleTypes.CLOUD;
    }

    /**
     * Sneak-right-click while holding opens the boiler UI, a plain right click wears the jetpack as usual, so it has
     * to be taken off for refuelling.
     */
    @NotNull
    public ActionResult<ItemStack> onRightClick(@NotNull World world, @NotNull EntityPlayer player,
                                                @NotNull EnumHand hand) {
        ItemStack armor = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                ItemGuiFactory.INSTANCE.open((EntityPlayerMP) player, hand);
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, armor);
        }
        if (player.inventory.armorInventory.get(EntityEquipmentSlot.CHEST.getIndex()).isEmpty()) {
            player.inventory.armorInventory.set(EntityEquipmentSlot.CHEST.getIndex(), armor.copy());
            player.setHeldItem(hand, ItemStack.EMPTY);
            player.playSound(new SoundEvent(new ResourceLocation("item.armor.equip_generic")), 1.0F, 1.0F);
            return ActionResult.newResult(EnumActionResult.SUCCESS, armor);
        }
        return ActionResult.newResult(EnumActionResult.PASS, armor);
    }

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return EntityEquipmentSlot.CHEST;
    }

    @Override
    public void addToolComponents(@NotNull ArmorMetaValueItem mvi) {
        mvi.addComponents(new Behaviour());
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "gregtech:textures/armor/coal_jetpack.png";
    }

    @Override
    public ISpecialArmor.ArmorProperties getProperties(EntityLivingBase player, @NotNull ItemStack armor,
                                                       @NotNull DamageSource source, double damage,
                                                       EntityEquipmentSlot equipmentSlot) {
        if (source.isUnblockable()) {
            return new ISpecialArmor.ArmorProperties(0, 0.0, 0);
        }
        State state = getState(armor);
        int damageLimit = state == null ? 0 : (int) Math.min(Integer.MAX_VALUE,
                state.currentTemperature * 1.0 / 32 * 25.0);
        return new ISpecialArmor.ArmorProperties(0, 0, damageLimit);
    }

    @Override
    public int getArmorDisplay(@NotNull EntityPlayer player, @NotNull ItemStack armor, int slot) {
        return 0;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void drawHUD(@NotNull ItemStack item) {
        State state = getState(item);
        if (state == null) {
            return;
        }
        if (state.steamTank.getFluidAmount() > 0) {
            String steam = String.format("%.1f", state.steamTank.getFluidAmount() * 100.0F / STEAM_CAPACITY);
            HUD.newString(I18n.format("metaarmor.hud.steam_lvl", steam + "%"));
        }
        if (state.currentTemperature > 0) {
            HUD.newString(I18n.format("metaarmor.hud.heat_lvl", state.currentTemperature, MAX_TEMPERATURE));
        }
        HUD.draw();
        HUD.reset();
    }

    @Nullable
    private static State getState(@NotNull ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        return handler instanceof State ? (State) handler : null;
    }

    // ------------------------------------------------------------------ components

    public class Behaviour implements IItemBehaviour, IItemCapabilityProvider, IItemDurabilityManager,
                            ISubItemHandler, ItemUIFactory {

        private final Pair<Color, Color> durabilityBarColors = GradientUtil.getGradient(0xE0E0E0, 10);

        @Override
        public ICapabilityProvider createProvider(ItemStack itemStack) {
            return new Provider(itemStack);
        }

        @Override
        public double getDurabilityForDisplay(@NotNull ItemStack itemStack) {
            State state = getState(itemStack);
            return state == null ? 0.0 : (double) state.steamTank.getFluidAmount() / (double) STEAM_CAPACITY;
        }

        @NotNull
        @Override
        public Pair<Color, Color> getDurabilityColorsForDisplay(@NotNull ItemStack itemStack) {
            return durabilityBarColors;
        }

        @Override
        public void addInformation(@NotNull ItemStack itemStack, @NotNull List<String> lines) {
            State state = getState(itemStack);
            if (state == null) {
                return;
            }
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.steam",
                    TextFormattingUtil.formatNumbers(state.steamTank.getFluidAmount()),
                    TextFormattingUtil.formatNumbers(STEAM_CAPACITY)));
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.water",
                    TextFormattingUtil.formatNumbers(state.waterTank.getFluidAmount()),
                    TextFormattingUtil.formatNumbers(WATER_CAPACITY)));
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.heat",
                    state.currentTemperature, MAX_TEMPERATURE));
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.flight", MIN_FLIGHT_TEMPERATURE));
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.fuel"));
            lines.add(I18n.format("gregtech.tooltip.coal_jetpack.open_ui"));
        }

        @Override
        public void getSubItems(@NotNull ItemStack itemStack, @NotNull CreativeTabs creativeTab,
                                @NotNull NonNullList<ItemStack> subItems) {
            ItemStack copy = itemStack.copy();
            State state = getState(copy);
            if (state == null) {
                subItems.add(itemStack);
                return;
            }
            state.waterTank.fill(Materials.Water.getFluid(WATER_CAPACITY), true);
            state.steamTank.fill(Materials.Steam.getFluid(STEAM_CAPACITY), true);
            state.save();
            subItems.add(copy);
        }

        @Override
        public String getItemSubType(@NotNull ItemStack itemStack) {
            return "";
        }

        /**
         * Boils water while the jetpack is worn or held, so its UI is live in both cases. Jetpacks which are just
         * being carried around are left alone instead of burning their fuel away.
         */
        @Override
        public void onUpdate(@NotNull ItemStack itemStack, @NotNull Entity entity) {
            if (entity.world.isRemote || !(entity instanceof EntityPlayer player)) {
                return;
            }
            if (player.getItemStackFromSlot(EntityEquipmentSlot.CHEST) != itemStack &&
                    player.getHeldItemMainhand() != itemStack && player.getHeldItemOffhand() != itemStack) {
                return;
            }
            tickBoiler(entity.world, itemStack);
        }

        @Override
        public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player,
                                                        @NotNull EnumHand hand) {
            return onRightClick(world, player, hand);
        }

        @Nullable
        @Override
        public ModularPanel buildUI(@NotNull HandGuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                    @NotNull UISettings settings) {
            return createPanel(guiData, guiSyncManager, settings);
        }
    }

    /** Exposes the boiler and the slots of the jetpack. */
    public static class Provider implements ICapabilityProvider {

        private final State state;

        public Provider(ItemStack itemStack) {
            this.state = new State(itemStack);
        }

        @Override
        public boolean hasCapability(@NotNull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY ||
                    capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY;
        }

        @Nullable
        @Override
        public <T> T getCapability(@NotNull Capability<T> capability, @Nullable EnumFacing facing) {
            if (capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY) {
                return CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY.cast(state);
            }
            if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(state.inventory);
            }
            return null;
        }
    }

    /**
     * The whole jetpack in one object: water tank, steam tank, fuel burn time, temperature and the slots, all of them
     * living in the NBT tag of the stack. It is both the fluid handler of the item and the state the armor logic works
     * on, so the UI can read live values from it while the armor ticks mutate it.
     */
    public static class State implements IFluidHandlerItem {

        public static final int SLOT_FLUID_IN = 0;
        public static final int SLOT_FLUID_OUT = 1;
        public static final int SLOT_FUEL = 2;
        public static final int SLOT_ASH = 3;

        private final ItemStack container;
        private final Inventory inventory;

        private final FluidTank waterTank = new FluidTank(WATER_CAPACITY) {

            @Override
            public boolean canFillFluidType(FluidStack fluid) {
                return CommonFluidFilters.BOILER_FLUID.test(fluid);
            }
        };
        private final FluidTank steamTank = new FluidTank(STEAM_CAPACITY);

        private int currentTemperature;
        private int fuelBurnTimeLeft;
        private int fuelMaxBurnTime;
        private int timeBeforeCoolingDown;
        private boolean hasNoWater;

        public State(ItemStack container) {
            this.container = container;
            this.inventory = new Inventory(container);

            NBTTagCompound data = container.getTagCompound();
            if (data != null) {
                this.currentTemperature = data.getInteger(NBT_TEMPERATURE);
                this.fuelBurnTimeLeft = data.getInteger(NBT_FUEL_BURN_TIME_LEFT);
                this.fuelMaxBurnTime = data.getInteger(NBT_FUEL_MAX_BURN_TIME);
                this.timeBeforeCoolingDown = data.getInteger(NBT_TIME_BEFORE_COOLING_DOWN);
                this.hasNoWater = data.getBoolean(NBT_HAS_NO_WATER);
                if (data.hasKey(NBT_WATER_TANK)) {
                    this.waterTank.readFromNBT(data.getCompoundTag(NBT_WATER_TANK));
                }
                if (data.hasKey(NBT_STEAM_TANK)) {
                    this.steamTank.readFromNBT(data.getCompoundTag(NBT_STEAM_TANK));
                }
            }
        }

        /** Writes the whole state back into the NBT tag of the stack. */
        public void save() {
            NBTTagCompound data = GTUtility.getOrCreateNbtCompound(container);
            data.setInteger(NBT_TEMPERATURE, currentTemperature);
            data.setInteger(NBT_FUEL_BURN_TIME_LEFT, fuelBurnTimeLeft);
            data.setInteger(NBT_FUEL_MAX_BURN_TIME, fuelMaxBurnTime);
            data.setInteger(NBT_TIME_BEFORE_COOLING_DOWN, timeBeforeCoolingDown);
            data.setBoolean(NBT_HAS_NO_WATER, hasNoWater);
            data.setTag(NBT_WATER_TANK, waterTank.writeToNBT(new NBTTagCompound()));
            data.setTag(NBT_STEAM_TANK, steamTank.writeToNBT(new NBTTagCompound()));
        }

        // -------------------------------------------------------------- boiler

        /** Heats up while burning fuel and cools back down when it is used up, like the coal boiler does. */
        private void updateTemperature(long worldTime) {
            if (fuelMaxBurnTime > 0) {
                if (worldTime % 12 == 0) {
                    if (fuelBurnTimeLeft % 2 == 0 && currentTemperature < MAX_TEMPERATURE) {
                        currentTemperature++;
                    }
                    fuelBurnTimeLeft--;
                    if (fuelBurnTimeLeft <= 0) {
                        fuelMaxBurnTime = 0;
                        timeBeforeCoolingDown = COOLDOWN_INTERVAL;
                    }
                }
            } else if (timeBeforeCoolingDown == 0) {
                if (currentTemperature > 0) {
                    currentTemperature -= COOL_DOWN_RATE;
                    timeBeforeCoolingDown = COOLDOWN_INTERVAL;
                }
            } else {
                timeBeforeCoolingDown--;
            }
        }

        /** Turns water into steam, but never more than the steam tank can hold. */
        private void generateSteam() {
            if (currentTemperature < MIN_STEAM_TEMPERATURE) {
                hasNoWater = waterTank.getFluidAmount() == 0;
                return;
            }
            int steamOutput = Math.min(getTotalSteamOutput(), steamTank.getCapacity() - steamTank.getFluidAmount());
            if (steamOutput <= 0) {
                return;
            }
            boolean hasDrainedWater = waterTank.drain(STEAM_PER_TICK, true) != null;
            if (hasDrainedWater) {
                steamTank.fill(Materials.Steam.getFluid(steamOutput), true);
            }
            hasNoWater = !hasDrainedWater;
        }

        /** @return the steam produced every 10 ticks at the current temperature. */
        private int getTotalSteamOutput() {
            if (currentTemperature < MIN_STEAM_TEMPERATURE) {
                return 0;
            }
            return (int) (BASE_STEAM_OUTPUT * (currentTemperature / (MAX_TEMPERATURE * 1.0)) / 2);
        }

        private void tryConsumeNewFuel() {
            if (fuelMaxBurnTime > 0) {
                return;
            }
            ItemStack fuel = inventory.extractItem(SLOT_FUEL, 1, true);
            if (fuel.isEmpty()) {
                return;
            }
            // Fluid containers are not fuel, even when they happen to be furnace fuels.
            if (FluidUtil.getFluidHandler(fuel) != null) {
                return;
            }
            int burnTime = TileEntityFurnace.getItemBurnTime(fuel);
            if (burnTime <= 0) {
                return;
            }
            inventory.extractItem(SLOT_FUEL, 1, false);
            ItemStack ash = ModHandler.getBurningFuelRemainder(fuel);
            if (!ash.isEmpty()) {
                inventory.insertItem(SLOT_ASH, ash, false);
            }
            this.fuelMaxBurnTime = burnTime;
            this.fuelBurnTimeLeft = burnTime;
        }

        // -------------------------------------------------------------- fluid handler

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[] { waterTank.getTankProperties()[0], steamTank.getTankProperties()[0] };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || !waterTank.canFillFluidType(resource)) {
                return 0;
            }
            int filled = waterTank.fill(resource, doFill);
            if (doFill && filled > 0) {
                save();
            }
            return filled;
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || !resource.isFluidEqual(steamTank.getFluid())) {
                return null;
            }
            FluidStack drained = steamTank.drain(resource.amount, doDrain);
            if (doDrain && drained != null) {
                save();
            }
            return drained;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack drained = steamTank.drain(maxDrain, doDrain);
            if (doDrain && drained != null) {
                save();
            }
            return drained;
        }

        @NotNull
        @Override
        public ItemStack getContainer() {
            return container;
        }
    }

    /** The slots of the jetpack, stored in the NBT tag of the stack. */
    public static class Inventory extends ItemStackHandler {

        private static final String NBT_KEY = "CoalJetpackInv";

        private final ItemStack owner;

        public Inventory(ItemStack owner) {
            super(4);
            this.owner = owner;
            NBTTagCompound data = owner.getTagCompound();
            if (data != null && data.hasKey(NBT_KEY, 10)) {
                deserializeNBT(data.getCompoundTag(NBT_KEY));
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            NBTTagCompound data = GTUtility.getOrCreateNbtCompound(owner);
            data.setTag(NBT_KEY, serializeNBT());
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == State.SLOT_FUEL) {
                return FluidUtil.getFluidHandler(stack) == null && TileEntityFurnace.getItemBurnTime(stack) > 0;
            }
            if (slot == State.SLOT_FLUID_IN) {
                return FluidUtil.getFluidHandler(stack) != null;
            }
            return false;
        }
    }

    // ------------------------------------------------------------------ UI

    /**
     * Builds the boiler panel: heat, water and steam gauges, the fluid container slots and the fuel slot with its burn
     * progress, all laid out like the coal boiler of the steam age.
     */
    @Nullable
    private ModularPanel createPanel(@NotNull HandGuiData guiData, @NotNull PanelSyncManager guiSyncManager,
                                     @NotNull UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();
        State state = getState(stack);
        if (state == null) {
            return null;
        }

        IntSyncValue temperature = new IntSyncValue(() -> state.currentTemperature);
        guiSyncManager.syncValue("temperature", temperature);

        SlotGroup group = new SlotGroup("coal_jetpack", 4);
        guiSyncManager.registerSlotGroup(group);

        return GTGuis.createPanel(stack.getTranslationKey(), 176, 166)
                .child(IKey.str(stack.getDisplayName()).asWidget().pos(5, 5))
                .child(new ProgressWidget()
                        .texture(GTGuiTextures.PROGRESS_BAR_BOILER_EMPTY_BRONZE, GTGuiTextures.PROGRESS_BAR_BOILER_HEAT,
                                -1)
                        .direction(ProgressWidget.Direction.UP)
                        .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gregtech.machine.steam_boiler.heat_tooltip",
                                temperature.getIntValue(), MAX_TEMPERATURE)))
                        .value(new DoubleSyncValue(() -> state.currentTemperature / (MAX_TEMPERATURE * 1.0)))
                        .pos(96, 26)
                        .size(10, 54))
                .child(new GTFluidSlot()
                        .background(GTGuiTextures.PROGRESS_BAR_BOILER_EMPTY_BRONZE)
                        .syncHandler(GTFluidSlot.sync(state.waterTank)
                                .showAmountOnSlot(false)
                                .accessibility(false, false))
                        .pos(83, 26)
                        .size(10, 54))
                .child(new GTFluidSlot()
                        .background(GTGuiTextures.PROGRESS_BAR_BOILER_EMPTY_BRONZE)
                        .syncHandler(GTFluidSlot.sync(state.steamTank)
                                .showAmountOnSlot(false)
                                .accessibility(false, false))
                        .pos(70, 26)
                        .size(10, 54))
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT)
                        .slot(new ModularSlot(state.inventory, State.SLOT_FLUID_IN)
                                .slotGroup(group))
                        .pos(43, 26))
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT)
                        .slot(new ModularSlot(state.inventory, State.SLOT_FLUID_OUT)
                                .slotGroup(group)
                                .accessibility(false, true))
                        .pos(43, 62))
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT)
                        .slot(new ModularSlot(state.inventory, State.SLOT_FUEL)
                                .slotGroup(group))
                        .pos(115, 62))
                .child(new ItemSlot()
                        .background(GTGuiTextures.SLOT)
                        .slot(new ModularSlot(state.inventory, State.SLOT_ASH)
                                .slotGroup(group)
                                .accessibility(false, true))
                        .pos(115, 26))
                .child(new ProgressWidget()
                        .value(new DoubleSyncValue(() -> state.fuelMaxBurnTime == 0 ? 0.0 :
                                state.fuelBurnTimeLeft / (state.fuelMaxBurnTime * 1.0)))
                        .texture(GTGuiTextures.PROGRESS_BAR_BOILER_FUEL_BRONZE, 18)
                        .direction(ProgressWidget.Direction.UP)
                        .pos(115, 44)
                        .size(18))
                .bindPlayerInventory();
    }
}
