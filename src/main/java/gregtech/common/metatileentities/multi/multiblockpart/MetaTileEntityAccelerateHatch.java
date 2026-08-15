package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IAccelerateHatch;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;

import gregtech.client.utils.TooltipHelper;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityAccelerateHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IAccelerateHatch>, IAccelerateHatch {

    private final int minPercentage;

    private int currentPercentage;

    public MetaTileEntityAccelerateHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.minPercentage = 52 - tier * 2; // LV(1)→50, MV(2)→48, ... MAX(14)→24
        this.currentPercentage = this.minPercentage;
    }

    public MetaTileEntityAccelerateHatch(ResourceLocation metaTileEntityId, int tier, int minPercentage) {
        super(metaTileEntityId, tier);
        this.minPercentage = minPercentage;
        this.currentPercentage = this.minPercentage;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityAccelerateHatch(this.metaTileEntityId, this.getTier());
    }

    @Override
    public int getCurrentPercentage() {
        return currentPercentage;
    }

    @Override
    public void setCurrentPercentage(int percentage) {
        this.currentPercentage = MathHelper.clamp(percentage, this.minPercentage, 100);
    }

    @Override
    public int getMinPercentage() {
        return minPercentage;
    }

    @Override
    public int getHatchTier() {
        return this.getTier();
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        IntSyncValue currentPercentageValue = new IntSyncValue(this::getCurrentPercentage, this::setCurrentPercentage);
        guiSyncManager.syncValue("currentPercentageValue", currentPercentageValue);

        IntSyncValue minPercentageValue = new IntSyncValue(
                this::getMinPercentage,
                value -> {}
        );
        guiSyncManager.syncValue("minPercentageValue", minPercentageValue);

        StringSyncValue currentPercentageStringValue = new StringSyncValue(
                () -> "耗时百分比：" + this.currentPercentage + "%",
                str -> {}
        );

        return GTGuis.createPanel(this, 176, 126)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(Flow.row()
                        .top(18)
                        .height(20)
                        .child(new ButtonWidget<>()
                                .left(5).width(40)
                                .height(18)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("减小耗时百分比")))
                                .onMousePressed(mouseButton -> {
                                    currentPercentageValue.setValue(MathHelper.clamp(
                                            currentPercentageValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)),
                                            minPercentageValue.getValue(), 100));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new TextFieldWidget()
                                .left(50)
                                .width(76)
                                .height(18)
                                .setValidator(str -> currentPercentageStringValue.getValue())
                                .value(currentPercentageStringValue)
                                .background(GTGuiTextures.DISPLAY)
                        )
                        .child(new ButtonWidget<>()
                                .left(131)
                                .width(40)
                                .height(18)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大耗时百分比")))
                                .onMousePressed(mouseButton -> {
                                    currentPercentageValue.setValue(MathHelper.clamp(
                                            currentPercentageValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)),
                                            minPercentageValue.getValue(), 100));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(true)))
                        )
                )
                .bindPlayerInventory();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        if (getTier() == GTValues.MAX) {
            tooltip.add(I18n.format("gregtech.creative_tooltip.1") + TooltipHelper.RAINBOW +
                    I18n.format("gregtech.creative_tooltip.2") + I18n.format("gregtech.creative_tooltip.3"));
        }
        tooltip.add(I18n.format("gregtech.machine.accelerate_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.accelerate_hatch.tooltip.2", this.minPercentage));
        tooltip.add(I18n.format("gregtech.universal.disabled"));
    }

    @Override
    public MultiblockAbility<IAccelerateHatch> getAbility() {
        return MultiblockAbility.ACCELERATE_HATCH;
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            OrientedOverlayRenderer overlayRenderer;
            if (getTier() <= GTValues.LV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK1_OVERLAY;
            else if (getTier() <= GTValues.MV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK2_OVERLAY;
            else if (getTier() <= GTValues.HV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK3_OVERLAY;
            else if (getTier() <= GTValues.EV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK4_OVERLAY;
            else if (getTier() <= GTValues.IV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK5_OVERLAY;
            else if (getTier() <= GTValues.LuV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK6_OVERLAY;
            else if (getTier() <= GTValues.ZPM)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK7_OVERLAY;
            else if (getTier() <= GTValues.UV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK8_OVERLAY;
            else if (getTier() <= GTValues.UHV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK9_OVERLAY;
            else if (getTier() <= GTValues.UEV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK10_OVERLAY;
            else if (getTier() <= GTValues.UIV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK11_OVERLAY;
            else if (getTier() <= GTValues.UXV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK12_OVERLAY;
            else if (getTier() <= GTValues.OpV)
                overlayRenderer = Textures.ACCELERATE_HATCH_MK13_OVERLAY;
            else
                overlayRenderer = Textures.ACCELERATE_HATCH_MK14_OVERLAY;

            if (getController() instanceof IRecipeMapHolder) {
                overlayRenderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                        getController().isActive(),
                        getController().getCapability(GregtechTileCapabilities.CAPABILITY_CONTROLLABLE, null)
                                .isWorkingEnabled());
            } else {
                overlayRenderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(), false, false);
            }
        }
    }

    @Override
    public boolean canPartShare() {
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound data) {
        data.setInteger("currentPercentage", this.currentPercentage);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.currentPercentage = data.getInteger("currentPercentage");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.currentPercentage);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.currentPercentage = buf.readInt();
    }
}
