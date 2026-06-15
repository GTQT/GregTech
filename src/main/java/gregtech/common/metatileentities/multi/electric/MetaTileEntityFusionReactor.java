package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.FormedStructureView;
import gregtech.api.pattern.SoftReferenceHolder;
import gregtech.api.pattern.TemplatePool;
import gregtech.api.pattern.element.StructureDefinition;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.properties.RecipePropertyStorage;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.interpolate.Eases;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.IRenderSetup;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.shader.postprocessing.BloomEffect;
import gregtech.client.shader.postprocessing.BloomType;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.EffectRenderContext;
import gregtech.client.utils.IBloomEffect;
import gregtech.client.utils.RenderBufferHelper;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.google.common.collect.Lists;
import gtqt.common.metatileentities.multi.multiblockpart.MetaTileEntityWirelessEnergyHatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static gregtech.api.recipes.logic.OverclockingLogic.PERFECT_HALF_DURATION_FACTOR;
import static gregtech.api.recipes.logic.OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR;

public class MetaTileEntityFusionReactor extends RecipeMapMultiblockController
        implements IFastRenderMetaTileEntity, IBloomEffect, ProgressBarMultiblock, ITieredMetaTileEntity {

    protected static final int NO_COLOR = 0;

    private static final Map<String, SoftReferenceHolder<? extends StructureDefinition<?>>> STRUCTURE_DEFINITIONS =
            new HashMap<>();

    static {
        STRUCTURE_DEFINITIONS.put("luv", TemplatePool.getInstance()
                .registerStructure("gregtech:fusion_reactor.luv",
                        () -> StructureDefinition.fromTemplate(buildTemplate(FusionReactorType.MK1))));
        STRUCTURE_DEFINITIONS.put("zpm", TemplatePool.getInstance()
                .registerStructure("gregtech:fusion_reactor.zpm",
                        () -> StructureDefinition.fromTemplate(buildTemplate(FusionReactorType.MK2))));
        STRUCTURE_DEFINITIONS.put("uv", TemplatePool.getInstance()
                .registerStructure("gregtech:fusion_reactor.uv",
                        () -> StructureDefinition.fromTemplate(buildTemplate(FusionReactorType.MK3))));
    }

    private final IFusionReactorType type;
    private EnergyContainerList inputEnergyContainers;
    private long heat = 0; // defined in TileEntityFusionReactor but serialized in FusionRecipeLogic
    private int fusionRingColor = NO_COLOR;

    @SideOnly(Side.CLIENT)
    private boolean registeredBloomRenderTicket;

    public MetaTileEntityFusionReactor(ResourceLocation metaTileEntityId, IFusionReactorType type) {
        super(metaTileEntityId, RecipeMaps.FUSION_RECIPES);
        this.recipeMapWorkable = new FusionRecipeLogic(this);
        this.type = type;
        this.energyContainer = new EnergyContainerHandler(this, 0, 0, 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
    }

    public static void registerFusionType(String key, Supplier<BlockPatternTemplate> templateSupplier) {
        STRUCTURE_DEFINITIONS.put(key, TemplatePool.getInstance()
                .registerStructure(key, () -> StructureDefinition.fromTemplate(templateSupplier.get())));
    }

    public static BlockPatternTemplate buildTemplate(IFusionReactorType type) {
        return FactoryBlockPattern.start()
                .aisle("###############", "######OGO######", "###############")
                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                .aisle("#C###########C#", "GAG#########GAG", "#C###########C#")
                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                .aisle("###############", "######OSO######", "###############")
                .where('S', selfPredicate(MetaTileEntityFusionReactor.class))
                .where('G', states(type.getCasingState(), type.getGlassState()))
                .where('E', metaTileEntities(getAllowedEnergyHatches(type))
                        .setAbility(MultiblockAbility.INPUT_ENERGY)
                        .setPreviewCount(16))
                .where('C', states(type.getCasingState()))
                .where('K', states(type.getCoilState()))
                .where('O', states(type.getCasingState(), type.getGlassState())
                        .fluidOutput()
                )
                .where('A', air())
                .where('I',
                        states(type.getCasingState()).or(
                                abilities(MultiblockAbility.IMPORT_FLUIDS).setMinGlobalLimited(2)))
                .where('#', any())
                .buildTemplate();
    }

    private static MetaTileEntity[] getAllowedEnergyHatches(IFusionReactorType type) {
        return Stream
                .concat(
                        Arrays.stream(MetaTileEntities.ENERGY_INPUT_HATCH),
                        MultiblockAbility.REGISTRY
                                .getOrDefault(MultiblockAbility.INPUT_ENERGY, Collections.emptyList())
                                .stream()
                                .filter(MetaTileEntityWirelessEnergyHatch.class::isInstance))
                .filter(mte -> mte instanceof ITieredMetaTileEntity tiered &&
                        type.getTier() <= tiered.getTier())
                .toArray(MetaTileEntity[]::new);
    }

    private static BloomType getBloomType() {
        ConfigHolder.FusionBloom fusionBloom = ConfigHolder.client.shader.fusionBloom;
        return BloomType.fromValue(fusionBloom.useShader ? fusionBloom.bloomStyle : -1);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityFusionReactor(metaTileEntityId, type);
    }

    @NotNull
    @Override
    protected StructureDefinition createStructureDefinition() {
        SoftReferenceHolder<? extends StructureDefinition<?>> definition = STRUCTURE_DEFINITIONS.get(type.getName());
        if (definition == null) {
            throw new IllegalStateException("Unknown turbine type: " + type.getName());
        }
        return definition.get();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (this.recipeMapWorkable.isActive()) {
            return Textures.ACTIVE_FUSION_TEXTURE;
        } else {
            return Textures.FUSION_TEXTURE;
        }
    }

    protected int getFusionRingColor() {
        return this.fusionRingColor;
    }

    protected void setFusionRingColor(int fusionRingColor) {
        if (this.fusionRingColor != fusionRingColor) {
            this.fusionRingColor = fusionRingColor;
            writeCustomData(GregtechDataCodes.UPDATE_COLOR, buf -> buf.writeVarInt(fusionRingColor));
        }
    }

    protected boolean hasFusionRingColor() {
        return this.fusionRingColor != NO_COLOR;
    }

    @Override
    protected void formStructure(@NotNull FormedStructureView formed) {
        long energyStored = this.energyContainer.getEnergyStored();
        formRecipeMapStructure(formed);
        this.initializeAbilities();
        ((EnergyContainerHandler) this.energyContainer).setEnergyStored(energyStored);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.energyContainer = new EnergyContainerHandler(this, 0, 0, 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
        this.inputEnergyContainers = new EnergyContainerList(Lists.newArrayList());
        this.heat = 0;
        this.setFusionRingColor(NO_COLOR);
    }

    @Override
    protected void initializeAbilities() {
        super.initializeAbilities();
        List<IEnergyContainer> energyInputs = getAbilities(MultiblockAbility.INPUT_ENERGY);
        this.inputEnergyContainers = new EnergyContainerList(energyInputs);
        long euCapacity = calculateEnergyStorageFactor(energyInputs.size());
        this.energyContainer = new EnergyContainerHandler(this, euCapacity, GTValues.V[type.getTier()], 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
    }

    private long calculateEnergyStorageFactor(int energyInputAmount) {
        return energyInputAmount * type.getEnergyMultiplier() * 10000000L;
    }

    @Override
    protected void updateFormedValid() {
        if (this.inputEnergyContainers.getEnergyStored() > 0) {
            long energyAdded = this.energyContainer.addEnergy(this.inputEnergyContainers.getEnergyStored());
            if (energyAdded > 0) this.inputEnergyContainers.removeEnergy(energyAdded);
        }
        super.updateFormedValid();
        if (recipeMapWorkable.isWorking() && fusionRingColor == NO_COLOR) {
            if (recipeMapWorkable.getPreviousRecipe() != null &&
                    !recipeMapWorkable.getPreviousRecipe().getFluidOutputs().isEmpty()) {
                setFusionRingColor(0xFF000000 |
                        recipeMapWorkable.getPreviousRecipe().getFluidOutputs().get(0).getFluid().getColor());
            }
        } else if (!recipeMapWorkable.isWorking() && isStructureFormed()) {
            setFusionRingColor(NO_COLOR);
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeVarInt(this.fusionRingColor);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.fusionRingColor = buf.readVarInt();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        if (dataId == GregtechDataCodes.UPDATE_COLOR) {
            this.fusionRingColor = buf.readVarInt();
        } else {
            super.receiveCustomData(dataId, buf);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create().addSpecialLogic().build(this, tooltip);
        tooltip.add(
                I18n.format("gregtech.machine.fusion_reactor.capacity", calculateEnergyStorageFactor(16) / 1000000L));
        tooltip.add(I18n.format("gregtech.machine.fusion_reactor.overclocking"));
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.FUSION_REACTOR_OVERLAY;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    public long getHeat() {
        return heat;
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        IDrawable title;

        title = type.getUITitle();

        DoubleSyncValue progress = new DoubleSyncValue(recipeMapWorkable::getProgressPercent);
        return new MultiblockUIFactory(this)
                .setScreenHeight(138)
                .disableDisplayText()
                .addScreenChildren((parent, syncManager) -> {
                    var status = MultiblockUIFactory.builder("status", syncManager);
                    status.setAction(b -> b.structureFormed(true)
                            .setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                            .addWorkingStatusLine());
                    parent.child(Flow.column()
                            .padding(4)
                            .expanded()
                            .child(title.asWidget()
                                    .marginBottom(8)
                                    .size(69, 12))
                            .child(new ProgressWidget()
                                    .size(77, 77)
                                    .tooltipAutoUpdate(true)
                                    .tooltipBuilder(status::build)
                                    .background(GTGuiTextures.FUSION_DIAGRAM.asIcon()
                                            .size(89, 101)
                                            .marginTop(11))
                                    .direction(ProgressWidget.Direction.CIRCULAR_CW)
                                    .value(progress)
                                    .texture(null, GTGuiTextures.FUSION_PROGRESS, 77))
                            .child(GTGuiTextures.FUSION_LEGEND.asWidget()
                                    .left(4)
                                    .bottom(4)
                                    .size(108, 41)));
                });
    }

    @Override
    public int getProgressBarCount() {
        return 2;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        LongSyncValue capacity = new LongSyncValue(energyContainer::getEnergyCapacity);
        syncManager.syncValue("capacity", capacity);
        LongSyncValue stored = new LongSyncValue(energyContainer::getEnergyStored);
        syncManager.syncValue("stored", stored);
        LongSyncValue heat = new LongSyncValue(this::getHeat);
        syncManager.syncValue("heat", heat);

        bars.add(barTest -> barTest
                .progress(() -> capacity.getLongValue() > 0 ?
                        1.0 * stored.getLongValue() / capacity.getLongValue() : 0)
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_ENERGY)
                .tooltipBuilder(tooltip -> tooltip
                        .add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.energy_stored",
                                stored.getLongValue(), capacity.getLongValue()))));

        bars.add(barTest -> barTest
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_HEAT)
                .tooltipBuilder(tooltip -> {
                    IKey heatInfo = KeyUtil.string(TextFormatting.AQUA,
                            "%,d / %,d EU",
                            heat.getLongValue(), capacity.getLongValue());
                    tooltip.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.fusion_reactor.heat",
                            heatInfo));
                })
                .progress(() -> capacity.getLongValue() > 0 ?
                        1.0 * heat.getLongValue() / capacity.getLongValue() : 0));
    }

    @Override
    public int getTier() {
        return type.getTier();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        if (this.hasFusionRingColor() && !this.registeredBloomRenderTicket) {
            this.registeredBloomRenderTicket = true;
            BloomEffectUtil.registerBloomRender(FusionBloomSetup.INSTANCE, getBloomType(), this, this);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderBloomEffect(@NotNull BufferBuilder buffer, @NotNull EffectRenderContext context) {
        if (!this.hasFusionRingColor()) return;
        int color = RenderUtil.interpolateColor(this.getFusionRingColor(), -1, Eases.QUAD_IN.getInterpolation(
                Math.abs((Math.abs(getOffsetTimer() % 50) + context.partialTicks()) - 25) / 25));
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        EnumFacing relativeBack = RelativeDirection.BACK.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());
        EnumFacing.Axis axis = RelativeDirection.UP.getRelativeFacing(getFrontFacing(), getUpwardsFacing(), isFlipped())
                .getAxis();

        buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        RenderBufferHelper.renderRing(buffer,
                getPos().getX() - context.cameraX() + relativeBack.getXOffset() * 7 + 0.5,
                getPos().getY() - context.cameraY() + relativeBack.getYOffset() * 7 + 0.5,
                getPos().getZ() - context.cameraZ() + relativeBack.getZOffset() * 7 + 0.5,
                6, 0.2, 10, 20,
                r, g, b, a, axis);
        Tessellator.getInstance().draw();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldRenderBloomEffect(@NotNull EffectRenderContext context) {
        return this.hasFusionRingColor() && context.camera().isBoundingBoxInFrustum(getRenderBoundingBox());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        EnumFacing relativeRight = RelativeDirection.RIGHT.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());
        EnumFacing relativeBack = RelativeDirection.BACK.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());

        return new AxisAlignedBB(
                this.getPos().offset(relativeBack).offset(relativeRight, 6),
                this.getPos().offset(relativeBack, 13).offset(relativeRight.getOpposite(), 6));
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0;
    }

    @Override
    public boolean isGlobalRenderer() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    private static final class FusionBloomSetup implements IRenderSetup {

        private static final FusionBloomSetup INSTANCE = new FusionBloomSetup();

        float lastBrightnessX;
        float lastBrightnessY;

        @Override
        public void preDraw(@NotNull BufferBuilder buffer) {
            BloomEffect.strength = (float) ConfigHolder.client.shader.fusionBloom.strength;
            BloomEffect.baseBrightness = (float) ConfigHolder.client.shader.fusionBloom.baseBrightness;
            BloomEffect.highBrightnessThreshold = (float) ConfigHolder.client.shader.fusionBloom.highBrightnessThreshold;
            BloomEffect.lowBrightnessThreshold = (float) ConfigHolder.client.shader.fusionBloom.lowBrightnessThreshold;
            BloomEffect.step = 1;

            lastBrightnessX = OpenGlHelper.lastBrightnessX;
            lastBrightnessY = OpenGlHelper.lastBrightnessY;
            GlStateManager.color(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.disableTexture2D();
        }

        @Override
        public void postDraw(@NotNull BufferBuilder buffer) {
            GlStateManager.enableTexture2D();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
        }
    }

    private class FusionRecipeLogic extends MultiblockRecipeLogic {

        public FusionRecipeLogic(MetaTileEntityFusionReactor tileEntity) {
            super(tileEntity);
        }

        @Override
        protected double getOverclockingDurationFactor() {
            return PERFECT_HALF_DURATION_FACTOR;
        }

        @Override
        protected double getOverclockingVoltageFactor() {
            return PERFECT_HALF_VOLTAGE_FACTOR;
        }

        @Override
        public long getMaxVoltage() {
            return Math.min(GTValues.V[type.getTier()], super.getMaxVoltage());
        }

        @Override
        public void updateWorkable() {
            super.updateWorkable();
            // Drain heat when the reactor is not active, is paused via soft mallet, or does not have enough energy and
            // has fully wiped recipe progress
            // Don't drain heat when there is not enough energy and there is still some recipe progress, as that makes
            // it doubly hard to complete the recipe
            // (Will have to recover heat and recipe progress)
            if (heat > 0) {
                if (!isActive || !workingEnabled || (hasNotEnoughEnergy && progressTime == 0)) {
                    heat = heat <= 10000 ? 0 : (heat - 10000);
                }
            }
        }

        @Override
        public boolean checkRecipe(@NotNull Recipe recipe) {
            if (!super.checkRecipe(recipe))
                return false;

            // if the reactor is not able to hold enough energy for it, do not run the recipe
            if (recipe.getProperty(FusionEUToStartProperty.getInstance(), 0L) > energyContainer.getEnergyCapacity()) {
                setWhyFailed("聚变反应堆热容量大于能源仓最大容量，永远无法达到预定热量");
                return false;
            }

            long heatDiff = recipe.getProperty(FusionEUToStartProperty.getInstance(), 0L) - heat;
            // if the stored heat is >= required energy, recipe is okay to run
            if (heatDiff <= 0)
                return true;

            // if the remaining energy needed is more than stored, do not run
            if (energyContainer.getEnergyStored() < heatDiff) {
                setWhyFailed("聚变反应堆没有足够的能量");
                return false;
            }

            // remove the energy needed
            energyContainer.removeEnergy(heatDiff);
            // increase the stored heat
            heat += heatDiff;
            return true;
        }

        @Override
        protected void modifyOverclockPre(@NotNull OCParams ocParams, @NotNull RecipePropertyStorage storage) {
            super.modifyOverclockPre(ocParams, storage);

            // Limit the number of OCs to the difference in fusion reactor MK.
            // I.e., a MK2 reactor can overclock a MK1 recipe once, and a
            // MK3 reactor can overclock a MK2 recipe once, or a MK1 recipe twice.
            long euToStart = storage.get(FusionEUToStartProperty.getInstance(), 0L);
            int fusionTier = FusionEUToStartProperty.getFusionTier(euToStart);
            if (fusionTier != 0) fusionTier = MetaTileEntityFusionReactor.this.type.getTier() - fusionTier;
            ocParams.setOcAmount(Math.min(fusionTier, ocParams.ocAmount()));
        }

        @NotNull
        @Override
        public NBTTagCompound serializeNBT() {
            NBTTagCompound tag = super.serializeNBT();
            tag.setLong("Heat", heat);
            return tag;
        }

        @Override
        public void deserializeNBT(@NotNull NBTTagCompound compound) {
            super.deserializeNBT(compound);
            heat = compound.getLong("Heat");
        }
    }
}
