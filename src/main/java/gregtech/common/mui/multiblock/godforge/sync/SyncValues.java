package gregtech.common.mui.multiblock.godforge.sync;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.value.sync.BigIntSyncValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.EnumSyncValue;
import com.cleanroommc.modularui.value.sync.FloatSyncValue;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.StringSyncValue;

import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;
import gregtech.common.metatileentities.multi.electric.godforge.data.Formatters;
import gregtech.common.metatileentities.multi.electric.godforge.data.Fuels;
import gregtech.common.metatileentities.multi.electric.godforge.data.Milestones;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEBaseModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEExoticModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTEPlasmaModule;
import gregtech.common.metatileentities.multi.electric.godforge.module.MTESmeltingModule;
import gregtech.common.metatileentities.multi.electric.godforge.data.UpgradeColor;
import gregtech.common.metatileentities.multi.electric.godforge.data.UpgradeType;
import gregtech.common.metatileentities.multi.electric.godforge.upgrade.ForgeOfGodsUpgrade;
import gregtech.common.mui.multiblock.godforge.sync.SyncValue.ForgeOfGodsSyncValue;
import gregtech.common.mui.multiblock.godforge.sync.SyncValue.HybridSyncValue;
import gregtech.common.mui.multiblock.godforge.sync.SyncValue.ModuleSyncValue;

public class SyncValues {

    public static final ForgeOfGodsSyncValue<BooleanSyncValue> STRUCTURE_UPDATE = new ForgeOfGodsSyncValue<>(
        "structureUpdateButton",
        hypervisor -> {
            MutableObject<Boolean> value = new MutableObject<>(false);
            return new BooleanSyncValue(value::getValue, val -> {
                value.setValue(val);
                if (!hypervisor.isClient() && hypervisor.getMultiblock() != null) {
                    hypervisor.getMultiblock().refreshStructureFromGui();
                }
            });
        },
        true);

    public static final ForgeOfGodsSyncValue<EnumSyncValue<Formatters>> FORMATTER = new ForgeOfGodsSyncValue<>(
        "fog.sync.formatter",
        data -> new EnumSyncValue<>(Formatters.class, data::getFormatter, data::setFormatter));

    public static final HybridSyncValue<BooleanSyncValue> INVERSION = new HybridSyncValue<>(
        "fog.sync.inversion",
        data -> new BooleanSyncValue(data::isInversion, data::setInversion),
        module -> new BooleanSyncValue(module::getInversionConfig, module::setInversionConfig));

    public static final ForgeOfGodsSyncValue<IntSyncValue> RING_AMOUNT = new ForgeOfGodsSyncValue<>(
        "fog.sync.ring_amount",
        data -> new IntSyncValue(data::getRingAmount, data::setRingAmount));

    public static final ForgeOfGodsSyncValue<EnumSyncValue<Fuels>> SELECTED_FUEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.selected_fuel",
        data -> new EnumSyncValue<>(Fuels.class, () -> Fuels.getFromData(data), fuel -> fuel.select(data)));

    public static final ForgeOfGodsSyncValue<LongSyncValue> FUEL_CONSUMPTION = new ForgeOfGodsSyncValue<>(
        "fog.sync.fuel_consumption",
        data -> new LongSyncValue(data::getFuelConsumption, data::setFuelConsumption));

    public static final ForgeOfGodsSyncValue<IntSyncValue> FUEL_FACTOR = new ForgeOfGodsSyncValue<>(
        "fog.sync.fuel_factor",
        data -> new IntSyncValue(data::getFuelConsumptionFactor, data::setFuelConsumptionFactor));

    public static final ForgeOfGodsSyncValue<IntSyncValue> NEEDED_STARTUP_FUEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.needed_startup_fuel",
        data -> new IntSyncValue(data::getNeededStartupFuel, data::setNeededStartupFuel));

    public static final ForgeOfGodsSyncValue<IntSyncValue> FUEL_AMOUNT = new ForgeOfGodsSyncValue<>(
        "fog.sync.fuel_amount",
        data -> new IntSyncValue(data::getStellarFuelAmount, data::setStellarFuelAmount));

    public static final ForgeOfGodsSyncValue<BooleanSyncValue> BATTERY_CHARGING = new ForgeOfGodsSyncValue<>(
        "fog.sync.battery_charging",
        data -> new BooleanSyncValue(data::isBatteryCharging, data::setBatteryCharging));

    public static final ForgeOfGodsSyncValue<IntSyncValue> INTERNAL_BATTERY = new ForgeOfGodsSyncValue<>(
        "fog.sync.internal_battery",
        data -> new IntSyncValue(data::getInternalBattery, data::setInternalBattery));

    public static final ForgeOfGodsSyncValue<IntSyncValue> MAX_BATTERY_CHARGE = new ForgeOfGodsSyncValue<>(
        "fog.sync.max_battery_charge",
        data -> new IntSyncValue(data::getMaxBatteryCharge, data::setMaxBatteryCharge));

    public static final ForgeOfGodsSyncValue<BooleanSyncValue> SHARD_EJECTION = new ForgeOfGodsSyncValue<>(
        "fog.sync.shard_ejection",
        data -> new BooleanSyncValue(data::isGravitonShardEjection, data::setGravitonShardEjection));

    public static final ForgeOfGodsSyncValue<IntSyncValue> AVAILABLE_GRAVITON_SHARDS = new ForgeOfGodsSyncValue<>(
        "fog.sync.available_graviton_shards",
        data -> new IntSyncValue(data::getGravitonShardsAvailable, data::setGravitonShardsAvailable));

    public static final ForgeOfGodsSyncValue<EnumSyncValue<ForgeOfGodsUpgrade>> UPGRADE_CLICKED = new ForgeOfGodsSyncValue<>(
        "fog.sync.upgrade_clicked",
        data -> {
            MutableInt i = new MutableInt();
            return new EnumSyncValue<>(
                ForgeOfGodsUpgrade.class,
                () -> ForgeOfGodsUpgrade.VALUES[i.intValue()],
                val -> i.setValue(val.ordinal()));
        });

    public static final ForgeOfGodsSyncValue<GenericListSyncHandler<?>> UPGRADES_LIST = new ForgeOfGodsSyncValue<>(
        "fog.sync.upgrades_list",
        data -> data.getUpgrades().getFullSyncer());

    public static final ForgeOfGodsSyncValue<BooleanSyncValue> SECRET_UPGRADE = new ForgeOfGodsSyncValue<>(
        "fog.sync.secret_upgrade",
        data -> new BooleanSyncValue(data::isSecretUpgrade, data::setSecretUpgrade));

    public static final ForgeOfGodsSyncValue<EnumSyncValue<Milestones>> MILESTONE_CLICKED = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_clicked",
        data -> {
            MutableInt i = new MutableInt();
            return new EnumSyncValue<>(
                Milestones.class,
                () -> Milestones.VALUES[i.intValue()],
                val -> i.setValue(val.ordinal()));
        });

    public static final ForgeOfGodsSyncValue<BigIntSyncValue> TOTAL_POWER_CONSUMED = new ForgeOfGodsSyncValue<>(
        "fog.sync.total_power_consumed",
        data -> new BigIntSyncValue(data::getTotalPowerConsumed, data::setTotalPowerConsumed));

    public static final ForgeOfGodsSyncValue<LongSyncValue> TOTAL_RECIPES_PROCESSED = new ForgeOfGodsSyncValue<>(
        "fog.sync.total_recipes_processed",
        data -> new LongSyncValue(data::getTotalRecipesProcessed, data::setTotalRecipesProcessed));

    public static final ForgeOfGodsSyncValue<LongSyncValue> TOTAL_FUEL_CONSUMED = new ForgeOfGodsSyncValue<>(
        "fog.sync.total_fuel_consumed",
        data -> new LongSyncValue(data::getTotalFuelConsumed, data::setTotalFuelConsumed));

    public static final ForgeOfGodsSyncValue<IntSyncValue> MILESTONE_CHARGE_LEVEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_charge_level",
        data -> new IntSyncValue(() -> data.getMilestoneProgress(0), val -> data.setMilestoneProgress(0, val)));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CHARGE_PROGRESS = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_charge_progress",
        data -> new FloatSyncValue(data::getPowerMilestonePercentage, data::setPowerMilestonePercentage));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CHARGE_PROGRESS_INVERTED = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_charge_progress_inverted",
        data -> new FloatSyncValue(data::getInvertedPowerMilestonePercentage, data::setInvertedPowerMilestonePercentage));

    public static final ForgeOfGodsSyncValue<IntSyncValue> MILESTONE_CONVERSION_LEVEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_conversion_level",
        data -> new IntSyncValue(() -> data.getMilestoneProgress(1), val -> data.setMilestoneProgress(1, val)));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CONVERSION_PROGRESS = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_conversion_progress",
        data -> new FloatSyncValue(data::getRecipeMilestonePercentage, data::setRecipeMilestonePercentage));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CONVERSION_PROGRESS_INVERTED = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_conversion_progress_inverted",
        data -> new FloatSyncValue(data::getInvertedRecipeMilestonePercentage, data::setInvertedRecipeMilestonePercentage));

    public static final ForgeOfGodsSyncValue<IntSyncValue> MILESTONE_CATALYST_LEVEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_catalyst_level",
        data -> new IntSyncValue(() -> data.getMilestoneProgress(2), val -> data.setMilestoneProgress(2, val)));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CATALYST_PROGRESS = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_catalyst_progress",
        data -> new FloatSyncValue(data::getFuelMilestonePercentage, data::setFuelMilestonePercentage));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_CATALYST_PROGRESS_INVERTED = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_catalyst_progress_inverted",
        data -> new FloatSyncValue(data::getInvertedFuelMilestonePercentage, data::setInvertedFuelMilestonePercentage));

    public static final ForgeOfGodsSyncValue<IntSyncValue> MILESTONE_COMPOSITION_LEVEL = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_composition_level",
        data -> new IntSyncValue(() -> data.getMilestoneProgress(3), val -> data.setMilestoneProgress(3, val)));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_COMPOSITION_PROGRESS = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_composition_progress",
        data -> new FloatSyncValue(data::getStructureMilestonePercentage, data::setStructureMilestonePercentage));
    public static final ForgeOfGodsSyncValue<FloatSyncValue> MILESTONE_COMPOSITION_PROGRESS_INVERTED = new ForgeOfGodsSyncValue<>(
        "fog.sync.milestone_composition_progress_inverted",
        data -> new FloatSyncValue(data::getInvertedStructureMilestonePercentage, data::setInvertedStructureMilestonePercentage));

    public static final ForgeOfGodsSyncValue<GenericSyncValue<ForgeOfGodsStarColor>> STAR_COLOR_CLICKED = new ForgeOfGodsSyncValue<>(
        "fog.sync.star_color_clicked",
        data -> {
            MutableObject<ForgeOfGodsStarColor> mut = new MutableObject<>(data.getStarColors().newTemplateColor());

            return new GenericSyncValue<>(ForgeOfGodsStarColor.class,
                mut::getValue,
                mut::setValue,
                ForgeOfGodsStarColor::readFromBuffer,
                ForgeOfGodsStarColor::writeToBuffer, null, null, false) {

                @Override
                public void setValue(ForgeOfGodsStarColor value, boolean setSource, boolean sync) {
                    if (value == null) {
                        value = data.getStarColors().newTemplateColor();
                    }
                    super.setValue(value, setSource, sync);
                }
            };
        });

    public static final ForgeOfGodsSyncValue<IntSyncValue> STAR_COLOR_EDITING_INDEX = new ForgeOfGodsSyncValue<>(
        "fog.sync.star_color_editing_index",
        data -> {
            MutableInt i = new MutableInt(-1);
            return new IntSyncValue(i::intValue, i::setValue);
        });

    public static final ForgeOfGodsSyncValue<StringSyncValue> SELECTED_STAR_COLOR = new ForgeOfGodsSyncValue<>(
        "fog.sync.selected_star_color",
        data -> new StringSyncValue(data::getSelectedStarColor, data::setSelectedStarColor));

    public static final ForgeOfGodsSyncValue<GenericListSyncHandler<ForgeOfGodsStarColor>> STAR_COLORS = new ForgeOfGodsSyncValue<>(
        "fog.sync.star_colors",
        data -> data.getStarColors().getSyncer());

    public static final ForgeOfGodsSyncValue<IntSyncValue> STAR_ROTATION_SPEED = new ForgeOfGodsSyncValue<>(
        "fog.sync.star_rotation_speed",
        data -> new IntSyncValue(data::getRotationSpeed, data::setRotationSpeed));

    public static final ForgeOfGodsSyncValue<IntSyncValue> STAR_SIZE = new ForgeOfGodsSyncValue<>(
        "fog.sync.star_size",
        data -> new IntSyncValue(data::getStarSize, data::setStarSize));

    public static final ForgeOfGodsSyncValue<BooleanSyncValue> RENDERER_DISABLED = new ForgeOfGodsSyncValue<>(
        "fog.sync.renderer_disabled",
        data -> new BooleanSyncValue(data::isRendererDisabled, data::setRendererDisabled));

    public static final ModuleSyncValue<BooleanSyncValue, MTEBaseModule> CONNECTION_STATUS = new ModuleSyncValue<>(
        "fog.sync.connection_status",
        module -> new BooleanSyncValue(module::isConnected, module::setConnected));

    public static final ModuleSyncValue<BooleanSyncValue, MTESmeltingModule> SMELTING_MODE = new ModuleSyncValue<>(
        "fog.sync.smelting_mode",
        module -> new BooleanSyncValue(module::isFurnaceModeOn, module::setFurnaceMode));

    public static final ModuleSyncValue<IntSyncValue, MTEPlasmaModule> DEBUG_PLASMA_PARALLEL = new ModuleSyncValue<>(
        "fog.sync.debug_plasma_parallel",
        module -> new IntSyncValue(module::getInputMaxParallel, module::setInputMaxParallel));

    public static final ModuleSyncValue<IntSyncValue, MTEPlasmaModule> DEBUG_FUSION_TIER = new ModuleSyncValue<>(
        "fog.sync.debug_fusion_tier",
        module -> new IntSyncValue(module::getPlasmaTier, module::setPlasmaTier));

    public static final ModuleSyncValue<BooleanSyncValue, MTEPlasmaModule> DEBUG_MULTI_STEP = new ModuleSyncValue<>(
        "fog.sync.debug_multi_step",
        module -> new BooleanSyncValue(module::isMultiStepPlasma, module::setMultiStepPlasma));

    public static final ModuleSyncValue<BooleanSyncValue, MTEExoticModule> MAGMATTER_CAPABLE = new ModuleSyncValue<>(
        "fog.sync.magmatter_capable",
        module -> new BooleanSyncValue(module::isMagmatterCapable, module::setMagmatterCapable));

    public static final ModuleSyncValue<BooleanSyncValue, MTEExoticModule> MAGMATTER_MODE = new ModuleSyncValue<>(
        "fog.sync.magmatter_mode",
        module -> new BooleanSyncValue(module::isMagmatterModeOn, module::setMagmatterMode));

    public static final ModuleSyncValue<LongSyncValue, MTEExoticModule> EXOTIC_INPUTS_TICKER = new ModuleSyncValue<>(
        "fog.sync.exotic_inputs_ticker",
        module -> new LongSyncValue(module::getTicker, module::setTicker));

    public static final ModuleSyncValue<IntSyncValue, MTEBaseModule> MODULE_CALCULATED_MAX_PARALLEL = new ModuleSyncValue<>(
        "fog.sync.module_calculated_max_parallel",
        module -> new IntSyncValue(module::getCalculatedMaxParallel, module::setCalculatedMaxParallel));

    public static final ModuleSyncValue<IntSyncValue, MTEBaseModule> MODULE_SET_MAX_PARALLEL = new ModuleSyncValue<>(
        "fog.sync.module_set_max_parallel",
        module -> new IntSyncValue(module::getPowerPanelMaxParallel, module::setPowerPanelMaxParallel));

    public static final ModuleSyncValue<BooleanSyncValue, MTEBaseModule> MODULE_ALWAYS_MAX_PARALLEL = new ModuleSyncValue<>(
        "fog.sync.module_always_max_parallel",
        module -> new BooleanSyncValue(module::isAlwaysMaxParallel, module::setAlwaysMaxParallel));

    public static final ModuleSyncValue<LongSyncValue, MTEBaseModule> MODULE_PROCESSING_VOLTAGE = new ModuleSyncValue<>(
        "fog.sync.module_processing_voltage",
        module -> new LongSyncValue(module::getProcessingVoltage, module::setProcessingVoltage));

    public static final ModuleSyncValue<BooleanSyncValue, MTEBaseModule> MODULE_VOLTAGE_CONFIG = new ModuleSyncValue<>(
        "fog.sync.module_voltage_config",
        module -> new BooleanSyncValue(module::getVoltageConfig, module::setVoltageConfig));

    public static final ModuleSyncValue<GenericListSyncHandler<ItemStack>, MTEExoticModule> EXOTIC_INPUTS = new ModuleSyncValue<>(
        "fog.sync.exotic_inputs",
        module -> module.getInputsSyncer());

    public static final ModuleSyncValue<GenericListSyncHandler<ItemStack>, MTEExoticModule> EXOTIC_POSSIBLE_INPUTS = new ModuleSyncValue<>(
        "fog.sync.exotic_possible_inputs",
        module -> module.getPossibleInputsSyncer());

    public static final ForgeOfGodsSyncValue<StringSyncValue> MANUAL_INSERTION_MODULE = new ForgeOfGodsSyncValue<>(
        "fog.sync.manual_insertion_module",
        data -> {
            MutableObject<String> mut = new MutableObject<>(Modules.SMELTING.getModuleId());
            return new StringSyncValue(mut::getValue, mut::setValue);
        });

    public static final ForgeOfGodsSyncValue<EnumSyncValue<UpgradeType>> MANUAL_INSERTION_UPGRADE_TYPE = new ForgeOfGodsSyncValue<>(
        "fog.sync.manual_insertion_upgrade_type",
        data -> {
            MutableObject<UpgradeType> mut = new MutableObject<>(UpgradeType.VALUES[0]);
            return new EnumSyncValue<>(
                UpgradeType.class,
                mut::getValue,
                mut::setValue);
        });

    public static final ForgeOfGodsSyncValue<EnumSyncValue<UpgradeColor>> MANUAL_INSERTION_UPGRADE_COLOR = new ForgeOfGodsSyncValue<>(
        "fog.sync.manual_insertion_upgrade_color",
        data -> {
            MutableObject<UpgradeColor> mut = new MutableObject<>(UpgradeColor.VALUES[0]);
            return new EnumSyncValue<>(
                UpgradeColor.class,
                mut::getValue,
                mut::setValue);
        });

    public static final ForgeOfGodsSyncValue<IntSyncValue> MANUAL_INSERTION_AMOUNT = new ForgeOfGodsSyncValue<>(
        "fog.sync.manual_insertion_amount",
        data -> {
            MutableInt i = new MutableInt(1);
            return new IntSyncValue(i::intValue, i::setValue);
        });

    public static final ForgeOfGodsSyncValue<StringSyncValue> UPGRADE_TREE_MODULE = new ForgeOfGodsSyncValue<>(
        "fog.sync.upgrade_tree_module",
        data -> {
            MutableObject<String> mut = new MutableObject<>(Modules.SMELTING.getModuleId());
            return new StringSyncValue(mut::getValue, mut::setValue);
        });

    public static final ForgeOfGodsSyncValue<EnumSyncValue<UpgradeType>> UPGRADE_TREE_CLICKED = new ForgeOfGodsSyncValue<>(
        "fog.sync.upgrade_tree_clicked",
        data -> {
            MutableObject<UpgradeType> mut = new MutableObject<>(UpgradeType.VALUES[0]);
            return new EnumSyncValue<>(
                UpgradeType.class,
                mut::getValue,
                mut::setValue);
        });

    public static final ForgeOfGodsSyncValue<EnumSyncValue<UpgradeColor>> INDIVIDUAL_UPGRADE_COLOR = new ForgeOfGodsSyncValue<>(
        "fog.sync.individual_upgrade_color",
        data -> {
            MutableObject<UpgradeColor> mut = new MutableObject<>(UpgradeColor.VALUES[0]);
            return new EnumSyncValue<>(
                UpgradeColor.class,
                mut::getValue,
                mut::setValue);
        });
}
