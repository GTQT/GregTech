package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IOverclockHatch;
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

public class MetaTileEntityOverclockHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IOverclockHatch>, IOverclockHatch {

    private final int maxDivisor;

    private int currentDivisor;

    public MetaTileEntityOverclockHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.maxDivisor = tier - 6; // UV(8)→2, UHV(9)→3, ... MAX(14)→8
        this.currentDivisor = this.maxDivisor;
    }

    public MetaTileEntityOverclockHatch(ResourceLocation metaTileEntityId, int tier, int maxDivisor) {
        super(metaTileEntityId, tier);
        this.maxDivisor = maxDivisor;
        this.currentDivisor = this.maxDivisor;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityOverclockHatch(this.metaTileEntityId, this.getTier());
    }

    @Override
    public int getCurrentDivisor() {
        return currentDivisor;
    }

    @Override
    public void setCurrentDivisor(int divisor) {
        this.currentDivisor = MathHelper.clamp(divisor, 2, this.maxDivisor);
    }

    @Override
    public int getMaxDivisor() {
        return maxDivisor;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        IntSyncValue currentDivisorValue = new IntSyncValue(this::getCurrentDivisor, this::setCurrentDivisor);
        guiSyncManager.syncValue("currentDivisorValue", currentDivisorValue);

        IntSyncValue maxDivisorValue = new IntSyncValue(
                this::getMaxDivisor,
                value -> {}
        );
        guiSyncManager.syncValue("maxDivisorValue", maxDivisorValue);

        StringSyncValue currentDivisorStringValue = new StringSyncValue(
                () -> "耗时除数：" + this.currentDivisor,
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
                                        .addLine(IKey.lang("减小耗时除数")))
                                .onMousePressed(mouseButton -> {
                                    currentDivisorValue.setValue(MathHelper.clamp(
                                            currentDivisorValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 2,
                                            maxDivisorValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new TextFieldWidget()
                                .left(50)
                                .width(76)
                                .height(18)
                                .setValidator(str -> currentDivisorStringValue.getValue())
                                .value(currentDivisorStringValue)
                                .background(GTGuiTextures.DISPLAY)
                        )
                        .child(new ButtonWidget<>()
                                .left(131)
                                .width(40)
                                .height(18)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大耗时除数")))
                                .onMousePressed(mouseButton -> {
                                    currentDivisorValue.setValue(MathHelper.clamp(
                                            currentDivisorValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 2,
                                            maxDivisorValue.getValue()));
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
        tooltip.add(I18n.format("gregtech.machine.overclock_hatch.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.overclock_hatch.tooltip.2", this.maxDivisor));
        tooltip.add(I18n.format("gregtech.universal.disabled"));
    }

    @Override
    public MultiblockAbility<IOverclockHatch> getAbility() {
        return MultiblockAbility.OVERCLOCK_HATCH;
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
            if (getTier() <= GTValues.UV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK1_OVERLAY;
            else if (getTier() <= GTValues.UHV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK2_OVERLAY;
            else if (getTier() <= GTValues.UEV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK3_OVERLAY;
            else if (getTier() <= GTValues.UIV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK4_OVERLAY;
            else if (getTier() <= GTValues.UXV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK5_OVERLAY;
            else if (getTier() <= GTValues.OpV)
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK6_OVERLAY;
            else
                overlayRenderer = Textures.OVERCLOCK_HATCH_MK7_OVERLAY;

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
        data.setInteger("currentDivisor", this.currentDivisor);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.currentDivisor = data.getInteger("currentDivisor");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.currentDivisor);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.currentDivisor = buf.readInt();
    }
}
