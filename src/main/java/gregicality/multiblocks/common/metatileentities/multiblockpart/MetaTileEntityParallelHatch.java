package gregicality.multiblocks.common.metatileentities.multiblockpart;

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
import gregicality.multiblocks.api.capability.IParallelHatch;
import gregicality.multiblocks.api.metatileentity.GCYMMultiblockAbility;
import gregicality.multiblocks.api.render.GCYMTextures;
import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.cube.OrientedOverlayRenderer;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityParallelHatch extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<IParallelHatch>, IParallelHatch {

    private final int maxParallel;

    private int currentParallel;

    public MetaTileEntityParallelHatch(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.maxParallel = (int) Math.pow(4, tier);
        this.currentParallel = this.maxParallel;
    }

    public MetaTileEntityParallelHatch(ResourceLocation metaTileEntityId, int tier , int maxParallel) {
        super(metaTileEntityId, tier);
        this.maxParallel = maxParallel;
        this.currentParallel = this.maxParallel;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity metaTileEntityHolder) {
        return new MetaTileEntityParallelHatch(this.metaTileEntityId, this.getTier());
    }

    @Override
    public int getCurrentParallel() {
        return currentParallel;
    }

    @Override
    public void setCurrentParallel(int parallelAmount) {
        this.currentParallel = MathHelper.clamp(parallelAmount, 1, this.maxParallel);
    }

    @Override
    public int getMaxParallel() {
        return this.maxParallel;
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        IntSyncValue currentParallelValue = new IntSyncValue(this::getCurrentParallel, this::setCurrentParallel);
        guiSyncManager.syncValue("currentParallelValue", currentParallelValue);

        IntSyncValue maxParallelValue = new IntSyncValue(
                this::getMaxParallel,
                value -> {
                }
        );
        guiSyncManager.syncValue("maxParallelValue", maxParallelValue);

        StringSyncValue currentParallelStringValue = new StringSyncValue(
                // 获取值的方法
                () -> "并行数量：" + this.currentParallel,
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
                                        .addLine(IKey.lang("减小并行数量")))
                                .onMousePressed(mouseButton -> {
                                    currentParallelValue.setValue(MathHelper.clamp(
                                            currentParallelValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxParallelValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new TextFieldWidget()
                                .left(50)
                                .width(76)
                                .height(18)
                                .setValidator(str -> currentParallelStringValue.getValue())
                                .value(currentParallelStringValue)
                                .background(GTGuiTextures.DISPLAY)
                        )
                        .child(new ButtonWidget<>()
                                .left(131)
                                .width(40)
                                .height(18)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大并行数量")))
                                .onMousePressed(mouseButton -> {
                                    currentParallelValue.setValue(MathHelper.clamp(
                                            currentParallelValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxParallelValue.getValue()));
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
        tooltip.add(I18n.format("gcym.machine.parallel_hatch.tooltip", this.maxParallel));
        tooltip.add(I18n.format("gregtech.universal.disabled"));
    }

    @Override
    public MultiblockAbility<IParallelHatch> getAbility() {
        return GCYMMultiblockAbility.PARALLEL_HATCH;
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
            if (getTier() <= GTValues.HV)
                overlayRenderer = GCYMTextures.PARALLEL_HATCH_MK1_OVERLAY;
            else if (getTier() <= GTValues.LuV)
                overlayRenderer = GCYMTextures.PARALLEL_HATCH_MK2_OVERLAY;
            else if (getTier() <= GTValues.UHV)
                overlayRenderer = GCYMTextures.PARALLEL_HATCH_MK3_OVERLAY;
            else
                overlayRenderer = GCYMTextures.PARALLEL_HATCH_MK4_OVERLAY;

            if (getController() != null && getController() instanceof RecipeMapMultiblockController) {
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
        data.setInteger("currentParallel", this.currentParallel);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.currentParallel = data.getInteger("currentParallel");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(this.currentParallel);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.currentParallel = buf.readInt();
    }
}
