package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.FuelRecipeLogic;
import gregtech.api.capability.impl.RecipeLogicEnergy;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.LabelWidget;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.PipelineUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class SimpleGeneratorMetaTileEntity extends WorkableTieredMetaTileEntity implements IActiveOutputSide {

    private static final int FONT_HEIGHT = 9; // Minecraft's FontRenderer FONT_HEIGHT value

    public SimpleGeneratorMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                         ICubeRenderer renderer, int tier,
                                         Function<Integer, Integer> tankScalingFunction) {
        this(metaTileEntityId, recipeMap, renderer, tier, tankScalingFunction, false);
    }

    public SimpleGeneratorMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                         ICubeRenderer renderer, int tier,
                                         Function<Integer, Integer> tankScalingFunction, boolean handlesRecipeOutputs) {
        super(metaTileEntityId, recipeMap, renderer, tier, tankScalingFunction, handlesRecipeOutputs);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SimpleGeneratorMetaTileEntity(metaTileEntityId, workable.getRecipeMap(), renderer, getTier(),
                getTankScalingFunction(), handlesRecipeOutputs);
    }

    @Override
    protected RecipeLogicEnergy createWorkable(RecipeMap<?> recipeMap) {
        return new FuelRecipeLogic(this, recipeMap, () -> energyContainer);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        if (handlesRecipeOutputs)
            return super.createExportFluidHandler();
        return new FluidTankList(false);
    }

    @Override
    protected void reinitializeEnergyContainer() {
        super.reinitializeEnergyContainer();
        ((EnergyContainerHandler) this.energyContainer).setSideOutputCondition(side -> side == getFrontFacing());
    }

    @Override
    public boolean hasFrontFacing() {
        return true;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (fluidInventory.getTankProperties().length > 0) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidInventory);
            }
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (itemInventory.getSlots() > 0) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemInventory);
            }
            return null;
        }
        return super.getCapability(capability, side);
    }

    protected ModularUI.Builder createGuiTemplate(EntityPlayer player) {
        RecipeMap<?> workableRecipeMap = workable.getRecipeMap();
        int yOffset = 0;
        if (workableRecipeMap.getMaxInputs() >= 6 || workableRecipeMap.getMaxFluidInputs() >= 6 ||
                workableRecipeMap.getMaxOutputs() >= 6 || workableRecipeMap.getMaxFluidOutputs() >= 6)
            yOffset = FONT_HEIGHT;

        ModularUI.Builder builder;
        if (handlesRecipeOutputs)
            builder = workableRecipeMap.getRecipeMapUI().createUITemplate(workable::getProgressPercent,
                    importItems, exportItems, importFluids, exportFluids, yOffset);
        else builder = workableRecipeMap.getRecipeMapUI().createUITemplateNoOutputs(workable::getProgressPercent,
                importItems,
                exportItems, importFluids, exportFluids, yOffset);
        builder.widget(new LabelWidget(6, 6, getMetaFullName()))
                .bindPlayerInventory(player.inventory, GuiTextures.SLOT, yOffset);

        return builder;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        renderOverlays(renderState, translation, pipeline);
        Textures.ENERGY_OUT.renderSided(getFrontFacing(), renderState, translation,
                PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
    }

    protected void renderOverlays(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        this.renderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(), workable.isActive(),
                workable.isWorkingEnabled());
    }

    @Override
    public boolean usesMui2() {
        RecipeMap<?> map = getRecipeMap();
        return map != null && map.getRecipeMapUI().usesMui2();
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager) {
        RecipeMap<?> workableRecipeMap = Objects.requireNonNull(workable.getRecipeMap(), "recipe map is null");
        int yOffset = 0;
        if (workableRecipeMap.getMaxInputs() >= 6 || workableRecipeMap.getMaxFluidInputs() >= 6 ||
                workableRecipeMap.getMaxOutputs() >= 6 || workableRecipeMap.getMaxFluidOutputs() >= 6) {
            yOffset = FONT_HEIGHT;
        }

        ModularPanel panel = GTGuis.createPanel(this, 176, 166 + yOffset);
        Widget<?> widget = workableRecipeMap.getRecipeMapUI().buildWidget(workable::getProgressPercent, importItems,
                exportItems, importFluids, exportFluids, yOffset, guiSyncManager);

        panel.child(widget)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                // .child(new ItemSlot()
                // .slot(SyncHandlers.itemSlot(chargerInventory, 0))
                // .pos(79, 62 + yOffset)
                // .background(GTGuiTextures.SLOT, GTGuiTextures.CHARGER_OVERLAY)
                // .tooltip(t -> t.addLine(IKey.lang("gregtech.gui.charger_slot.tooltip", GTValues.VNF[getTier()],
                // GTValues.VNF[getTier()]))))
                .bindPlayerInventory();

        if (exportItems.getSlots() + exportFluids.getTanks() <= 9) {
            panel.child(new Widget<>()
                    .size(17)
                    .pos(152, 63 + yOffset)
                    .background(GTGuiTextures.getLogo(getUITheme())));
        }
        return panel;
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return createGuiTemplate(entityPlayer).build(getHolder(), entityPlayer);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        String key = this.metaTileEntityId.getPath().split("\\.")[0];
        String mainKey = String.format("gregtech.machine.%s.tooltip", key);
        if (I18n.hasKey(mainKey)) {
            tooltip.add(1, I18n.format(mainKey));
        }
        tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", energyContainer.getOutputVoltage(),
                GTValues.VNF[getTier()]));
        tooltip.add(
                I18n.format("gregtech.universal.tooltip.energy_storage_capacity", energyContainer.getEnergyCapacity()));
        if (recipeMap.getMaxFluidInputs() > 0 || recipeMap.getMaxFluidOutputs() > 0)
            tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity",
                    this.getTankScalingFunction().apply(getTier())));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.reset"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public boolean isAutoOutputItems() {
        return false;
    }

    @Override
    public boolean isAutoOutputFluids() {
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideItems() {
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideFluids() {
        return false;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return 1L;
    }

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return !handlesRecipeOutputs;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return !handlesRecipeOutputs;
    }
}
