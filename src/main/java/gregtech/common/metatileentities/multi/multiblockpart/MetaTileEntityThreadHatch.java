package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IRecipeMapHolder;
import gregtech.api.capability.IThreadController;
import gregtech.api.capability.IThreadHatch;
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

public class MetaTileEntityThreadHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IThreadHatch>, IThreadHatch {

    private final int maxThread;

    private int currentThread;

    public MetaTileEntityThreadHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.maxThread = (int) Math.pow(2, tier);
        this.currentThread = this.maxThread;
    }

    public MetaTileEntityThreadHatch(ResourceLocation metaTileEntityId, int tier, int maxThread) {
        super(metaTileEntityId, tier);
        this.maxThread = maxThread;
        this.currentThread = this.maxThread;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityThreadHatch(this.metaTileEntityId, this.getTier());
    }

    @Override
    public int getCurrentThread() {
        return currentThread;
    }

    @Override
    public void setCurrentThread(int ThreadAmount) {
        this.currentThread = MathHelper.clamp(ThreadAmount, 1, this.maxThread);
        if (this.getController() instanceof IThreadController iThreadController) {
            iThreadController.refreshThread(currentThread);
        }
    }

    @Override
    public int getMaxThread() {
        return maxThread;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        IntSyncValue currentThreadValue = new IntSyncValue(this::getCurrentThread, this::setCurrentThread);
        guiSyncManager.syncValue("currentThreadValue", currentThreadValue);

        IntSyncValue maxThreadValue = new IntSyncValue(
                this::getMaxThread,
                value -> {}
        );
        guiSyncManager.syncValue("maxThreadValue", maxThreadValue);

        StringSyncValue currentThreadStringValue = new StringSyncValue(
                // 获取值的方法
                () -> "线程数量：" + this.currentThread,
                str -> {
                }
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
                                        .addLine(IKey.lang("减小线程数量")))
                                .onMousePressed(mouseButton -> {
                                    currentThreadValue.setValue(MathHelper.clamp(
                                            currentThreadValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxThreadValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new TextFieldWidget()
                                .left(50)
                                .width(76)
                                .height(18)
                                .setValidator(str -> currentThreadStringValue.getValue())
                                .value(currentThreadStringValue)
                                .background(GTGuiTextures.DISPLAY)
                        )
                        .child(new ButtonWidget<>()
                                .left(131)
                                .width(40)
                                .height(18)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大线程数量")))
                                .onMousePressed(mouseButton -> {
                                    currentThreadValue.setValue(MathHelper.clamp(
                                            currentThreadValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxThreadValue.getValue()));
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
        tooltip.add(I18n.format("gregtech.machine.thread_hatch.tooltip", this.maxThread));
        tooltip.add(I18n.format("gregtech.universal.disabled"));
    }

    @Override
    public MultiblockAbility<IThreadHatch> getAbility() {
        return MultiblockAbility.THREAD_HATCH;
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

            if (getTier() <= GTValues.MV)
                overlayRenderer = Textures.THREAD_HATCH_MK1_OVERLAY;
            else if (getTier() <= GTValues.EV)
                overlayRenderer = Textures.THREAD_HATCH_MK2_OVERLAY;
            else if (getTier() <= GTValues.LuV)
                overlayRenderer = Textures.THREAD_HATCH_MK3_OVERLAY;
            else if (getTier() <= GTValues.UV)
                overlayRenderer = Textures.THREAD_HATCH_MK4_OVERLAY;
            else if (getTier() <= GTValues.UEV)
                overlayRenderer = Textures.THREAD_HATCH_MK5_OVERLAY;
            else if (getTier() <= GTValues.UXV)
                overlayRenderer = Textures.THREAD_HATCH_MK6_OVERLAY;
            else
                overlayRenderer = Textures.THREAD_HATCH_MK7_OVERLAY;

            if (getController() != null && getController() instanceof IRecipeMapHolder) {
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
        data.setInteger("currentThread", this.currentThread);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        // Hatches saved before this tag existed have no value to read, and a tag out of range would survive here
        // because only setCurrentThread clamps. Either way a zero would make the controller hand out zero threads.
        this.currentThread = data.hasKey("currentThread") ?
                MathHelper.clamp(data.getInteger("currentThread"), 1, this.maxThread) : this.maxThread;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.currentThread);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.currentThread = buf.readInt();
    }
}
