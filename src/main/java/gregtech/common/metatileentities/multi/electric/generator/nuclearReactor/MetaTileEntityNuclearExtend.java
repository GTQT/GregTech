package gregtech.common.metatileentities.multi.electric.generator.nuclearReactor;

import gregtech.api.capability.INuclearExtend;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.SCMultiblockAbility;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.item.behaviors.NuclearUpdateBehavior;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MetaTileEntityNuclearExtend extends MetaTileEntityMultiblockPart
        implements IMultiblockAbilityPart<INuclearExtend>, INuclearExtend {

    public ItemStackHandler updateHandler;
    public List<NuclearAbility> abilities = new ArrayList<>();

    public MetaTileEntityNuclearExtend(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, 4);
        this.updateHandler = new GTItemStackHandler(this, 4){
            public void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                refreshAbility();
            }
        };
    }

    public List<NuclearAbility> getUpdateAbilities(){
        return abilities;
    }

    public void refreshAbility() {
        for (int i=0;i<updateHandler.getSlots();i++){
            ItemStack stack = updateHandler.getStackInSlot(i);
            if(stack!=ItemStack.EMPTY){
                NuclearUpdateBehavior update = NuclearUpdateBehavior.getInstanceFor(stack);
                if(update!=null){
                    abilities.add(update.getAbility());
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("updateHandler", this.updateHandler.serializeNBT());
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.updateHandler.deserializeNBT(data.getCompoundTag("updateHandler"));
        refreshAbility();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityNuclearExtend(metaTileEntityId);
    }

    @Override
    public MultiblockAbility<INuclearExtend> getAbility() {
        return SCMultiblockAbility.REACTOR_EXTEND_HATCH;
    }

    @Override
    public void registerAbilities(AbilityInstances abilityInstances) {
        abilityInstances.add(this);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (this.getController() != null && this.getController() instanceof MetaTileEntityNuclearReactor nuclearReactor) {
            this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), true, nuclearReactor.isWorkingEnabled());
        } else {
            this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(), false, false);
        }
    }

    protected @NotNull ICubeRenderer getFrontOverlay() {
        return Textures.NUCLEAR_REACTOR_EXTEND_OVERLAY;
    }

    @Override
    public void addInformation(ItemStack stack, World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(TextFormatting.GREEN + I18n.format("-工作原理："));
        tooltip.add("通过安装燃料拓展仓来扩展核反应堆的内部容量。");
        tooltip.add("每个燃料拓展仓将反应堆内部空间的X和Y方向各增加1格。");
        tooltip.add("安装多个拓展仓可以叠加效果，最大内部空间可达9x9。");
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        int rowSize = 2;
        panelSyncManager.registerSlotGroup("item_inv", rowSize);

        // Player Inv width
        int backgroundWidth = 9 * 18 + 14 + 5; // Bus Inv width
        int backgroundHeight = 18 + 18 * rowSize + 94;


        ItemStackHandler handler = updateHandler;

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new Grid()
                        .top(18).height(rowSize * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .alignX(0.5f)
                        .mapTo(rowSize, rowSize * rowSize, index -> new ItemSlot()
                                .slot(SyncHandlers.itemSlot(handler, index)
                                        .slotGroup("item_inv")
                                        .changeListener((newItem, onlyAmountChanged, client, init) -> {
                                            if (onlyAmountChanged &&
                                                    handler instanceof GTItemStackHandler gtHandler) {
                                                gtHandler.onContentsChanged(index);
                                            }
                                        })
                                        .accessibility(true, true))));
    }
}
