package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.MultiblockFuelRecipeLogic;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.FuelMultiblockController;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

public class MetaTileEntityLargeTurbine extends FuelMultiblockController
        implements ITieredMetaTileEntity, ProgressBarMultiblock {

    private static final int MIN_DURABILITY_TO_WARN = 10;
    private static final String STRUCTURE_POOL_KEY = "gregtech:large_turbine";

    public final ILargeTurbineType type;
    public IFluidHandler exportFluidHandler;

    public MetaTileEntityLargeTurbine(ResourceLocation metaTileEntityId, ILargeTurbineType type) {
        super(metaTileEntityId, type.getRecipeMap(), type.getTier());
        this.type = type;
        this.recipeMapWorkable = new LargeTurbineWorkableHandler(this, tier);
        this.recipeMapWorkable.setMaximumOverclockVoltage(GTValues.V[tier]);
    }

    private static StructureDefinition<?> buildStructureDefinition(ILargeTurbineType type,
                                                                   ResourceLocation controllerId) {
        return DeclarativePatternBuilder.start()
                .aisle("CCCC", "CHHC", "CCCC")
                .aisle("CHHC", "RGGR", "CHHC")
                .aisle("CCCC", "CSHC", "CCCC")
                .where('S', Elements.self(MetaTileEntityLargeTurbine.class, controllerId))
                .block('G', type.getGearboxState())
                .block('C', type.getCasingState())
                .where('R', Elements.chain(
                        Elements.withDefaultCandidate(
                                Elements.withTooltips(
                                        Elements.metaTileEntities(1, 1, MultiblockAbility.REGISTRY
                                                .get(MultiblockAbility.ROTOR_HOLDER).stream()
                                                .filter(mte -> (mte instanceof ITieredMetaTileEntity) &&
                                                        (((ITieredMetaTileEntity) mte).getTier() >= type.getTier()))
                                                .toArray(MetaTileEntity[]::new)),
                                        "gregtech.multiblock.pattern.clear_amount_3",
                                        "gregtech.multiblock.pattern.error.limited.1 " + GTValues.VN[type.getTier()]),
                                () -> getDefaultRotorHolder(type)),
                        Elements.withDefaultCandidate(
                                Elements.abilities(1, 1, MultiblockAbility.OUTPUT_ENERGY),
                                () -> getDefaultEnergyOutputHatch(type))))
                .casing('H', type.getCasingState())
                .maintenance()
                .optionalHatch(MultiblockAbility.IMPORT_FLUIDS, 4)
                .optionalHatch(MultiblockAbility.EXPORT_FLUIDS, 4)
                .optionalHatch(MultiblockAbility.MUFFLER_HATCH, type.hasMufflerHatch() ? 1 : 0)
                .globalAbilityLimit(MultiblockAbility.ROTOR_HOLDER, 1, 1)
                .globalAbilityLimit(MultiblockAbility.OUTPUT_ENERGY, 1, 1)
                .buildStructureDefinition();
    }

    @Nullable
    private static MetaTileEntity getDefaultRotorHolder(@NotNull ILargeTurbineType type) {
        int index = type.getTier() - GTValues.HV;
        if (index < 0 || index >= MetaTileEntities.ROTOR_HOLDER.length) {
            return null;
        }
        return MetaTileEntities.ROTOR_HOLDER[index];
    }

    @Nullable
    private static MetaTileEntity getDefaultEnergyOutputHatch(@NotNull ILargeTurbineType type) {
        int tier = type.getTier();
        if (tier < 0 || tier >= MetaTileEntities.ENERGY_OUTPUT_HATCH.length) {
            return null;
        }
        return MetaTileEntities.ENERGY_OUTPUT_HATCH[tier];
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeTurbine(metaTileEntityId, type);
    }

    public IRotorHolder getRotorHolder() {
        List<IRotorHolder> abilities = getAbilities(MultiblockAbility.ROTOR_HOLDER);
        if (abilities.isEmpty())
            return null;
        return abilities.get(0);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.exportFluidHandler = null;
    }

    /**
     * @return true if turbine is formed and it's face is free and contains only air blocks in front of rotor holder
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isRotorFaceFree() {
        IRotorHolder rotorHolder = getRotorHolder();
        if (rotorHolder != null)
            return isStructureFormed() && getRotorHolder().isFrontFaceFree();
        return false;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        formRecipeMapStructure(formed);
        this.exportFluidHandler = new FluidTankList(true, getAbilities(MultiblockAbility.EXPORT_FLUIDS));
        ((LargeTurbineWorkableHandler) this.recipeMapWorkable).updateTanks();
    }

    @Override
    protected long getMaxVoltage() {
        long maxProduction = recipeMapWorkable.getMaxVoltage();
        long currentProduction = ((LargeTurbineWorkableHandler) recipeMapWorkable).boostProduction((int) maxProduction);
        if (isActive() && currentProduction <= maxProduction) {
            return recipeMapWorkable.getMaxVoltage();
        } else {
            return 0L;
        }
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        MultiblockFuelRecipeLogic recipeLogic = (MultiblockFuelRecipeLogic) recipeMapWorkable;
        boolean dynamoFull = isDynamoFull();
        builder.setWorkingStatus(recipeLogic.isWorkingEnabled() && !dynamoFull,
                        recipeLogic.isActive() && !dynamoFull)
                .addEnergyProductionLine(getMaxVoltage(), recipeLogic.getRecipeEUt())
                .addCustom((keyList, syncer) -> {
                    IRotorHolder rotorHolder = getRotorHolder();
                    if (!syncer.syncBoolean(rotorHolder != null)) return;

                    int rotorEfficiency = syncer.syncInt(
                            () -> rotorHolder == null ? 0 : rotorHolder.getRotorEfficiency());
                    int totalEfficiency = syncer.syncInt(
                            () -> rotorHolder == null ? 0 : rotorHolder.getTotalEfficiency());

                    if (rotorEfficiency > 0) {
                        IKey efficiencyInfo = KeyUtil.number(TextFormatting.AQUA,
                                totalEfficiency, "%");
                        keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.turbine.efficiency",
                                efficiencyInfo));
                    }
                })
                .addFuelNeededLine(recipeLogic::getRecipeFluidInputInfo, recipeLogic::getPreviousRecipeDuration)
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addCustom((keyList, syncer) -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (!syncer.syncBoolean(rotorHolder != null)) return;

            int rotorEfficiency = syncer.syncInt(
                    () -> rotorHolder == null ? 0 : rotorHolder.getRotorEfficiency());
            int rotorDurability = syncer.syncInt(
                    () -> rotorHolder == null ? 0 : rotorHolder.getRotorDurabilityPercent());

            if (rotorEfficiency > 0 && rotorDurability <= MIN_DURABILITY_TO_WARN) {
                keyList.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.turbine.rotor_durability_low"));
            }
        });
        super.configureWarningText(builder);
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((keyList, syncer) -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (!syncer.syncBoolean(rotorHolder != null)) return;

            if (syncer.syncBoolean(() -> rotorHolder != null && !rotorHolder.isFrontFaceFree())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.obstructed"));
                keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.turbine.obstructed.desc"));
            }
            int rotorEfficiency = syncer.syncInt(
                    () -> rotorHolder == null ? 0 : rotorHolder.getRotorEfficiency());

            if (rotorEfficiency <= 0) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.no_rotor"));
            }
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.base_production_eut", GTValues.V[tier] * 2));
        tooltip.add(I18n.format("gregtech.multiblock.turbine.efficiency_tooltip", GTValues.VNF[tier]));
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        return StructureDefinition.getOrBuild(STRUCTURE_POOL_KEY, type.getName(),
                () -> buildStructureDefinition(type, metaTileEntityId));
    }

    @Override
    public String[] getDescription() {
        return new String[] { I18n.format("gregtech.multiblock.large_turbine.description") };
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return type.getCasingRenderer();
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return type.getFrontOverlay();
    }

    @Override
    public IBlockState getCasingBlock() {
        return type.getCasingState();
    }

    @Override
    public boolean hasMufflerMechanics() {
        return type.hasMufflerHatch();
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !isRotorFaceFree();
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return true;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return true;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    public int getProgressBarCount() {
        return 3;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        FixedIntArraySyncValue fuelValue = new FixedIntArraySyncValue(this::getFuelAmount);
        StringSyncValue fuelNameValue = new StringSyncValue(() -> {
            FluidStack stack = ((MultiblockFuelRecipeLogic) recipeMapWorkable).getCachedInputFluidStack();
            if (stack == null) {
                return null;
            }
            Fluid fluid = stack.getFluid();
            if (fluid == null) {
                return null;
            }
            return fluid.getName();
        });
        syncManager.syncValue("fuel_amount", fuelValue);
        syncManager.syncValue("fuel_name", fuelNameValue);

        IntSyncValue rotorSpeedValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getRotorSpeed();
        });

        IntSyncValue rotorMaxSpeedValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getMaxRotorHolderSpeed();
        });

        syncManager.syncValue("rotor_speed", rotorSpeedValue);
        syncManager.syncValue("rotor_max_speed", rotorMaxSpeedValue);
        IntSyncValue durabilityValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getRotorDurabilityPercent();
        });
        IntSyncValue efficiencyValue = new IntSyncValue(() -> {
            IRotorHolder rotorHolder = getRotorHolder();
            if (rotorHolder == null) {
                return 0;
            }
            return rotorHolder.getRotorEfficiency();
        });

        syncManager.syncValue("rotor_durability", durabilityValue);
        syncManager.syncValue("rotor_efficiency", efficiencyValue);

        bars.add(barTest -> barTest
                .progress(() -> fuelValue.getValue(1) == 0 ? 0 :
                        1.0 * fuelValue.getValue(0) / fuelValue.getValue(1))
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL)
                .tooltipBuilder(t -> createFuelTooltip(t, fuelValue, fuelNameValue)));

        bars.add(barTest -> barTest
                .progress(() -> rotorMaxSpeedValue.getIntValue() == 0 ? 0 :
                        1.0 * rotorSpeedValue.getIntValue() / rotorMaxSpeedValue.getIntValue())
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_SPEED)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        int speed = rotorSpeedValue.getIntValue();
                        int maxSpeed = rotorMaxSpeedValue.getIntValue();

                        t.addLine(KeyUtil.lang("gregtech.multiblock.turbine.rotor_speed",
                                getSpeedFormat(maxSpeed, speed), speed, maxSpeed));
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        bars.add(barTest -> barTest
                .progress(() -> durabilityValue.getIntValue() / 100.0)
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_DURABILITY)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (efficiencyValue.getIntValue() <= 0) {
                            t.addLine(IKey.lang("gregtech.multiblock.turbine.no_rotor"));
                        } else {
                            int durability = durabilityValue.getIntValue();
                            // TODO working dynamic color substitutions into IKey.lang
                            if (durability > 40) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.high",
                                        durability));
                            } else if (durability > MIN_DURABILITY_TO_WARN) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.medium",
                                        durability));
                            } else {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.low",
                                        durability));
                            }
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    private @NotNull TextFormatting getSpeedFormat(int maxSpeed, int speed) {
        float percent = maxSpeed == 0 ? 0 : 1.0f * speed / maxSpeed;

        if (percent < 0.4) {
            return TextFormatting.RED;
        } else if (percent < 0.8) {
            return TextFormatting.YELLOW;
        } else {
            return TextFormatting.GREEN;
        }
    }

    /**
     * @return an array of [fuel stored, fuel capacity]
     */
    private int[] getFuelAmount() {
        if (getInputFluidInventory() != null) {
            MultiblockFuelRecipeLogic recipeLogic = (MultiblockFuelRecipeLogic) recipeMapWorkable;
            FluidStack fuelStack = recipeLogic.getCachedInputFluidStack();
            if (fuelStack != null) {
                FluidStack testStack = fuelStack.copy();
                testStack.amount = Integer.MAX_VALUE;
                return getTotalFluidAmount(testStack, getInputFluidInventory());
            }
        }
        return new int[2];
    }
}
