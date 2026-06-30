package gregtech.common.metatileentities.multi.electric.godforge.module;

import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IGodforgeModule;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.casing.DeclarativePatternBuilder;
import gregtech.api.pattern.element.Elements;
import gregtech.api.pattern.element.IStructureElement;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockGodforgeCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.mui.multiblock.godforge.MTEBaseModuleGui;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public abstract class MTEBaseModule extends RecipeMapMultiblockController
        implements IGodforgeModule, IMultiblockAbilityPart<IGodforgeModule> {

    protected boolean isConnected = false;
    protected int machineHeat = 0;
    protected int overclockHeat = 0;
    protected int calculatedMaxParallel = 0;
    protected int plasmaTier = 0;
    protected double processingSpeedBonus = 0;
    protected double energyDiscount = 0;
    protected long processingVoltage = 2_000_000_000L;
    protected double overclockTimeFactor = 2.0;
    protected boolean isUpgrade83Unlocked = false;
    protected boolean isMultiStepPlasmaCapable = false;
    protected boolean isMagmatterCapable = false;
    protected boolean isVoltageConfigUnlocked = false;
    protected boolean isInversionUnlocked = false;
    protected int powerPanelMaxParallel = 1;
    protected boolean alwaysMaxParallel = true;
    protected BigInteger powerTally = BigInteger.ZERO;
    protected long recipeTally = 0;
    protected long currentRecipeHeat = 0;
    private MultiblockControllerBase attachedGodforge;
    private StructureDefinition<?> structureDefinition;

    public MTEBaseModule(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId, recipeMap);
        this.recipeMapWorkable = new GodforgeModuleRecipeLogic(this);
    }

    // region GUI

    private static IKey createStatLine(String labelKey, IKey value) {
        return IKey.comp(
                IKey.lang(labelKey).style(TextFormatting.GRAY),
                KeyUtil.string(TextFormatting.GRAY, ": "),
                value);
    }

    private static IKey formatDouble(double value) {
        return KeyUtil.string(TextFormatting.AQUA, TextFormattingUtil.formatNumbers(value));
    }

    protected static IBlockState getCasingState(BlockGodforgeCasing.CasingType type) {
        return MetaBlocks.GODFORGE_CASING.getState(type);
    }

    protected abstract MTEBaseModuleGui<?> createModuleGui();

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return new ModuleUIFactory(this, createModuleGui());
    }

    public void configureModuleDisplayText(MultiblockUIBuilder builder) {
        builder.title(getMetaFullName())
                .structureFormed(isStructureFormed());
        configureDisplayText(builder);
        builder.addCustom(this::addModuleDisplayText);
    }

    // endregion

    private void addModuleDisplayText(KeyManager keyManager, UISyncer syncer) {
        if (!syncer.syncBoolean(this::isStructureFormed)) {
            return;
        }

        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.heat",
                KeyUtil.number(TextFormatting.AQUA, syncer.syncInt(this::getHeat))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.effectiveheat",
                KeyUtil.number(TextFormatting.AQUA, syncer.syncInt(this::getHeatForOC))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.parallel",
                KeyUtil.number(TextFormatting.AQUA, syncer.syncInt(this::getActualParallel))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.speedbonus",
                formatDouble(syncer.syncDouble(this::getSpeedBonus))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.energydiscount",
                formatDouble(syncer.syncDouble(this::getEnergyDiscount))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.ocdivisor",
                formatDouble(syncer.syncDouble(this::getOverclockTimeFactor))));
        keyManager.add(createStatLine(
                "gt.blockmachines.multimachine.FOG.processingvoltage",
                KeyUtil.number(TextFormatting.AQUA, syncer.syncLong(this::getProcessingVoltage), " EU/t")));
    }

    @NotNull
    @Override
    protected StructureDefinition<?> createStructureDefinition() {
        if (structureDefinition == null) {
            structureDefinition = createStructureDefinitionForModule();
        }
        return structureDefinition;
    }

    @NotNull
    private StructureDefinition<?> createStructureDefinitionForModule() {
        return DeclarativePatternBuilder.start()
                .aisle("       ", "       ", "       ", "   G   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   D   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   D   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   D   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   D   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "       ", "   D   ", "       ", "       ", "       ")
                .aisle("       ", "       ", "   E   ", "  EAE  ", "   E   ", "       ", "       ")
                .aisle("       ", "       ", "   E   ", "  EAE  ", "   E   ", "       ", "       ")
                .aisle("       ", "       ", "   E   ", "  EAE  ", "   E   ", "       ", "       ")
                .aisle("       ", "       ", "   E   ", "  EAE  ", "   E   ", "       ", "       ")
                .aisle("       ", "       ", "   E   ", "  EAE  ", "   E   ", "       ", "       ")
                .aisle("  CCC  ", " CFFFC ", "CFFFFFC", "CFFFFFC", "CFFFFFC", " CFFFC ", "  CCC  ")
                .aisle("       ", "  BBB  ", " BBBBB ", " BB~BB ", " BBBBB ", "  BBB  ", "       ")
                .metaTileEntities('~', this)
                .where('A', getCoilBlockElement())
                .where('B', Elements.chain(
                        Elements.block(getCasingState(
                                BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING)),
                        Elements.abilities(MultiblockAbility.IMPORT_ITEMS),
                        Elements.abilities(MultiblockAbility.IMPORT_FLUIDS),
                        Elements.abilities(MultiblockAbility.EXPORT_ITEMS),
                        Elements.abilities(MultiblockAbility.EXPORT_FLUIDS)))
                .block('C',
                        getCasingState(BlockGodforgeCasing.CasingType.SINGULARITY_REINFORCED_STELLAR_SHIELDING_CASING))
                .block('D', getCasingState(BlockGodforgeCasing.CasingType.CELESTIAL_MATTER_GUIDANCE_CASING))
                .block('E', getCasingState(
                        BlockGodforgeCasing.CasingType.BOUNDLESS_GRAVITATIONALLY_SEVERED_STRUCTURE_CASING))
                .block('F', getCasingState(
                        BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING))
                .block('G', getCasingState(BlockGodforgeCasing.CasingType.STELLAR_ENERGY_SIPHON_CASING))
                .buildStructureDefinition();
    }

    protected abstract IStructureElement getCoilBlockElement();

    @Override
    protected void updateFormedValid() {
        if (!isConnected) return;
        if (!hasMufflerMechanics() || isMufflerReady()) {
            this.recipeMapWorkable.updateWorkable();
        }
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    @Override
    public MultiblockAbility<IGodforgeModule> getAbility() {
        return MultiblockAbility.GODFORGE_MODULE;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public boolean isAttachedToMultiBlock() {
        return attachedGodforge != null;
    }

    @Override
    public void addToMultiBlock(MultiblockControllerBase controllerBase) {
        attachedGodforge = controllerBase;
    }

    @Override
    public void removeFromMultiBlock(MultiblockControllerBase controllerBase) {
        if (attachedGodforge == controllerBase) {
            attachedGodforge = null;
            disconnect();
        }
    }

    @Override
    public boolean canPartShare() {
        return true;
    }

    @Override
    public void connect() {
        isConnected = true;
    }

    @Override
    public void disconnect() {
        isConnected = false;
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    public void setConnected(boolean val) {
        isConnected = val;
    }

    public int getHeat() {
        return machineHeat;
    }

    public void setHeat(int heat) {
        machineHeat = heat;
    }

    public int getHeatForOC() {
        return overclockHeat;
    }

    public void setHeatForOC(int heat) {
        overclockHeat = heat;
    }

    public int getCalculatedMaxParallel() {
        return calculatedMaxParallel;
    }

    public void setCalculatedMaxParallel(int parallel) {
        calculatedMaxParallel = parallel;
    }

    public int getActualParallel() {
        int calculated = Math.max(1, getCalculatedMaxParallel());
        if (alwaysMaxParallel || powerPanelMaxParallel <= 0) {
            return calculated;
        }
        return Math.max(1, Math.min(calculated, powerPanelMaxParallel));
    }

    public double getSpeedBonus() {
        return processingSpeedBonus;
    }

    public void setSpeedBonus(double bonus) {
        processingSpeedBonus = bonus;
    }

    public double getEnergyDiscount() {
        return energyDiscount;
    }

    public void setEnergyDiscount(double discount) {
        energyDiscount = discount;
    }

    public long getProcessingVoltage() {
        return processingVoltage;
    }

    public void setProcessingVoltage(long voltage) {
        processingVoltage = voltage;
    }

    public double getOverclockTimeFactor() {
        return overclockTimeFactor;
    }

    public void setOverclockTimeFactor(double factor) {
        overclockTimeFactor = factor;
    }

    public boolean isUpgrade83() {
        return isUpgrade83Unlocked;
    }

    public void setUpgrade83(boolean unlocked) {
        isUpgrade83Unlocked = unlocked;
    }

    public boolean isMultiStepPlasma() {
        return isMultiStepPlasmaCapable;
    }

    public void setMultiStepPlasma(boolean isCapable) {
        isMultiStepPlasmaCapable = isCapable;
    }

    public boolean isMagmatterCapable() {
        return isMagmatterCapable;
    }

    public void setMagmatterCapable(boolean isCapable) {
        isMagmatterCapable = isCapable;
    }

    public boolean isVoltageConfig() {
        return isVoltageConfigUnlocked;
    }

    public boolean getVoltageConfig() {
        return isVoltageConfig();
    }

    public void setVoltageConfig(boolean unlocked) {
        isVoltageConfigUnlocked = unlocked;
    }

    public boolean isInversionConfig() {
        return isInversionUnlocked;
    }

    public boolean getInversionConfig() {
        return isInversionConfig();
    }

    public void setInversionConfig(boolean inversion) {
        isInversionUnlocked = inversion;
    }

    public int getPowerPanelMaxParallel() {
        return powerPanelMaxParallel;
    }

    public void setPowerPanelMaxParallel(int powerPanelMaxParallel) {
        this.powerPanelMaxParallel = powerPanelMaxParallel;
    }

    public boolean isAlwaysMaxParallel() {
        return alwaysMaxParallel;
    }

    public void setAlwaysMaxParallel(boolean alwaysMaxParallel) {
        this.alwaysMaxParallel = alwaysMaxParallel;
    }

    public boolean isAllowedToWork() {
        return isWorkingEnabled();
    }

    public int getStructureUpdateTime() {
        return 0;
    }

    public void refreshStructureFromGui() {
        if (getWorld() == null || getWorld().isRemote) return;

        if (isStructureFormed()) {
            invalidateStructure();
        }
        reinitializeStructurePattern();
        checkStructurePattern();
        markDirty();
    }

    public boolean isInputSeparationEnabled() {
        return isDistinct();
    }

    public boolean isBatchModeEnabled() {
        return isBatchEnable();
    }

    public boolean isRecipeLockingEnabled() {
        return isRecipeLocked();
    }

    public int getPlasmaTier() {
        return plasmaTier;
    }

    public void setPlasmaTier(int tier) {
        plasmaTier = tier;
    }

    public BigInteger getPowerTally() {
        return powerTally;
    }

    public void setPowerTally(BigInteger amount) {
        powerTally = amount;
    }

    public void addToPowerTally(BigInteger amount) {
        powerTally = powerTally.add(amount);
    }

    public long getRecipeTally() {
        return recipeTally;
    }

    public void setRecipeTally(long amount) {
        recipeTally = amount;
    }

    public void addToRecipeTally(long amount) {
        recipeTally += amount;
    }

    public long getCurrentRecipeHeat() {
        return currentRecipeHeat;
    }

    public void setCurrentRecipeHeat(long heat) {
        currentRecipeHeat = heat;
    }

    public double getHeatEnergyDiscount() {
        return isUpgrade83Unlocked ? 0.92 : 0.95;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isConnected", isConnected);
        data.setInteger("machineHeat", machineHeat);
        data.setInteger("overclockHeat", overclockHeat);
        data.setInteger("calculatedMaxParallel", calculatedMaxParallel);
        data.setInteger("plasmaTier", plasmaTier);
        data.setDouble("processingSpeedBonus", processingSpeedBonus);
        data.setDouble("energyDiscount", energyDiscount);
        data.setLong("processingVoltage", processingVoltage);
        data.setDouble("overclockTimeFactor", overclockTimeFactor);
        data.setBoolean("isUpgrade83Unlocked", isUpgrade83Unlocked);
        data.setBoolean("isMultiStepPlasmaCapable", isMultiStepPlasmaCapable);
        data.setBoolean("isMagmatterCapable", isMagmatterCapable);
        data.setBoolean("isVoltageConfigUnlocked", isVoltageConfigUnlocked);
        data.setBoolean("isInversionUnlocked", isInversionUnlocked);
        data.setInteger("powerPanelMaxParallel", powerPanelMaxParallel);
        data.setBoolean("alwaysMaxParallel", alwaysMaxParallel);
        data.setString("powerTally", powerTally.toString());
        data.setLong("recipeTally", recipeTally);
        data.setLong("currentRecipeHeat", currentRecipeHeat);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isConnected = data.getBoolean("isConnected");
        machineHeat = data.getInteger("machineHeat");
        overclockHeat = data.getInteger("overclockHeat");
        calculatedMaxParallel = data.getInteger("calculatedMaxParallel");
        plasmaTier = data.getInteger("plasmaTier");
        processingSpeedBonus = data.getDouble("processingSpeedBonus");
        energyDiscount = data.getDouble("energyDiscount");
        processingVoltage = data.getLong("processingVoltage");
        overclockTimeFactor = data.getDouble("overclockTimeFactor");
        isUpgrade83Unlocked = data.getBoolean("isUpgrade83Unlocked");
        isMultiStepPlasmaCapable = data.getBoolean("isMultiStepPlasmaCapable");
        isMagmatterCapable = data.getBoolean("isMagmatterCapable");
        isVoltageConfigUnlocked = data.getBoolean("isVoltageConfigUnlocked");
        isInversionUnlocked = data.getBoolean("isInversionUnlocked");
        powerPanelMaxParallel = data.hasKey("powerPanelMaxParallel") ? data.getInteger("powerPanelMaxParallel") : 1;
        alwaysMaxParallel = !data.hasKey("alwaysMaxParallel") || data.getBoolean("alwaysMaxParallel");
        String powerTallyStr = data.getString("powerTally");
        powerTally = powerTallyStr.isEmpty() ? BigInteger.ZERO : new BigInteger(powerTallyStr);
        recipeTally = data.getLong("recipeTally");
        currentRecipeHeat = data.getLong("currentRecipeHeat");
    }

    @Override
    public ICubeRenderer getBaseTexture(gregtech.api.metatileentity.multiblock.IMultiblockPart sourcePart) {
        return Textures.GODFORGE_INNER_CASING;
    }
}
