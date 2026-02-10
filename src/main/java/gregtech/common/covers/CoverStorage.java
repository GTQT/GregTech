package gregtech.common.covers;

import gregtech.api.cover.CoverBase;
import gregtech.api.cover.CoverDefinition;
import gregtech.api.cover.CoverWithUI;
import gregtech.api.cover.CoverableView;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraftforge.items.ItemStackHandler;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.SidedPosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CoverStorage extends CoverBase implements CoverWithUI {

    private final ItemStackHandler storageHandler;
    int inventorySize;

    public CoverStorage(@NotNull CoverDefinition definition, @NotNull CoverableView coverableView,
                        @NotNull EnumFacing attachedSide, int inventorySize) {
        super(definition, coverableView, attachedSide);
        this.inventorySize = inventorySize;
        storageHandler = new ItemStackHandler(inventorySize);
    }

    @Override
    public boolean canAttach(@NotNull CoverableView coverable, @NotNull EnumFacing side) {
        return true;
    }

    @Override
    public void renderCover(@NotNull CCRenderState renderState, @NotNull Matrix4 translation,
                            IVertexOperation[] pipeline, @NotNull Cuboid6 plateBox, @NotNull BlockRenderLayer layer) {
        Textures.STORAGE.renderSided(getAttachedSide(), plateBox, renderState, pipeline, translation);
    }

    @Override
    public void onRemoval() {
        dropInventoryContents(storageHandler);
    }

    @Override
    public @NotNull EnumActionResult onRightClick(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                  @NotNull CuboidRayTraceResult hitResult) {
        if (!getCoverableView().getWorld().isRemote) {
            openUI((EntityPlayerMP) player);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public @NotNull EnumActionResult onScrewdriverClick(@NotNull EntityPlayer player, @NotNull EnumHand hand,
                                                        @NotNull CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            openUI((EntityPlayerMP) player);
        }
        return EnumActionResult.SUCCESS;
    }

    @Override
    public ModularPanel buildUI(SidedPosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        guiSyncManager.registerSlotGroup("item_inv", 9);

        int rows = inventorySize / 9;
        List<List<IWidget>> widgets = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            widgets.add(new ArrayList<>());
            for (int j = 0; j < 9; j++) {
                int index = i * 9 + j;
                widgets.get(i).add(new ItemSlot().slot(SyncHandlers.itemSlot(storageHandler, index)
                        .slotGroup("item_inv")));
            }
        }
        return GTGuis.createPanel(this, 9 * 18 + 14, 18 + 4 * 18 + 5 + 14 + 18 * rows)
                .child(IKey.lang("cover.storage.title").asWidget().pos(5, 5))
                .bindPlayerInventory()
                .child(new Grid()
                        .top(18).left(7).right(7).height(rows * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18).minRowHeight(18)
                        .matrix(widgets));
    }

    public IWidget initUILeisure(GuiData guiData, PanelSyncManager guiSyncManager,int index) {
        var componentPanel = guiSyncManager.panel("component_panel"+index, this::makeComponentPanel, true);
        // 返回按钮
        return new ButtonWidget<>()
                .size(18, 18)
                .overlay(new com.cleanroommc.modularui.drawable.ItemDrawable(
                        new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.CHEST)))
                .addTooltipLine(IKey.lang("cover.storage.title"))
                .onMousePressed(i -> {
                    if (componentPanel.isPanelOpen()) {
                        componentPanel.closePanel();
                    } else {
                        componentPanel.openPanel();
                    }
                    return true;
                });
    }

    private ModularPanel makeComponentPanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        // 计算行数（假设每个存储有9列）
        int rows = inventorySize / 9;

        // 注册槽位组
        syncManager.registerSlotGroup("storage_slots", 9);

        // 创建槽位网格
        List<List<IWidget>> slotRows = new ArrayList<>();

        for (int row = 0; row < rows; row++) {
            List<IWidget> rowSlots = new ArrayList<>();
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                rowSlots.add(
                        new ItemSlot()
                                .slot(SyncHandlers.itemSlot(storageHandler, slotIndex)
                                        .slotGroup("storage_slots"))
                                .size(18, 18)
                );
            }
            slotRows.add(rowSlots);
        }

        // 创建并返回面板
        return GTGuis.createPopupPanel("nuclear_components", 9 * 18 + 14, rows * 18 + 30)
                .child(IKey.lang("cover.storage.title").asWidget().pos(5, 5))
                .child(new Grid()
                        .top(20)
                        .left(7)
                        .right(7)
                        .height(rows * 18)
                        .minElementMargin(0, 0)
                        .minColWidth(18)
                        .minRowHeight(18)
                        .matrix(slotRows)
                );
    }
    @Override
    public void writeToNBT(@NotNull NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        tagCompound.setTag("Storage", this.storageHandler.serializeNBT());
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
        this.storageHandler.deserializeNBT(tagCompound.getCompoundTag("Storage"));
    }
}
