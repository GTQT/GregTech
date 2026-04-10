package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityAEHostablePart;

import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.networking.IGridNode;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static gregtech.api.capability.GregtechDataCodes.UPDATE_ME_POS;

public class MetaTileEntityMEPatternManager extends MetaTileEntityAEHostablePart {

    ArrayList<BlockPos> pos = new ArrayList<>();
    String searchText = "";

    public MetaTileEntityMEPatternManager(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEPatternManager(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    // 将 BlockPos 列表转换为字符串
    private String blockPosListToString(List<BlockPos> posList) {
        if (posList == null || posList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < posList.size(); i++) {
            BlockPos pos = posList.get(i);
            sb.append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ());
            if (i < posList.size() - 1) {
                sb.append(";"); // 使用分号分隔不同的 BlockPos
            }
        }
        return sb.toString();
    }

    // 将字符串转换为 BlockPos 列表
    private List<BlockPos> stringToBlockPosList(String str) {
        if (Objects.equals(str, "null")) return null;
        List<BlockPos> result = new ArrayList<>();

        if (str == null || str.isEmpty()) {
            return result;
        }

        String[] posStrings = str.split(";");
        for (String posStr : posStrings) {
            String[] coords = posStr.split(",");
            if (coords.length == 3) {
                try {
                    int x = Integer.parseInt(coords[0]);
                    int y = Integer.parseInt(coords[1]);
                    int z = Integer.parseInt(coords[2]);
                    result.add(new BlockPos(x, y, z));
                } catch (NumberFormatException e) {
                    // 忽略格式错误的数据
                }
            }
        }

        return result;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {

        // 创建 BlockPos 列表的同步值
        StringSyncValue posListValue = new StringSyncValue(
                () -> blockPosListToString(this.pos),
                str -> {
                    if (str != null) {
                        List<BlockPos> newList = stringToBlockPosList(str);
                        this.pos.clear();
                        if (newList != null) this.pos.addAll(newList);
                    }
                }
        );
        posListValue.updateCacheFromSource(true);
        guiSyncManager.syncValue("posList", posListValue);

        StringSyncValue searchFieldValue = new StringSyncValue(() -> searchText, s -> {
            if (s == null)
                searchText = "";
            else
                searchText = s;
        });
        searchFieldValue.updateCacheFromSource(true);

        List<BlockPos> newList = stringToBlockPosList(posListValue.getStringValue());
        List<List<IWidget>> list = new ArrayList<>();

        String searchText = "";
        boolean add = false;
        if (searchFieldValue.getStringValue() == "") add = true;
        else searchText = searchFieldValue.getStringValue();

        int num = 0;
        if (newList != null) {
            for (BlockPos pos : newList) {
                TileEntity te = this.getWorld().getTileEntity(pos);
                if (te instanceof IGregTechTileEntity igtte) {
                    MetaTileEntity mte = igtte.getMetaTileEntity();
                    if (mte instanceof MetaTileEntityAECraftingPart patternProvider && !patternProvider.isHideInfo()) {
                        ItemStackHandler itemHandler = patternProvider.getPatternSlot();
                        int slots = itemHandler.getSlots();
                        int rowSize = patternProvider.getTier();
                        num++;
                        guiSyncManager.registerSlotGroup("pattern_slots" + num, slots);

                        List<IWidget> textWidgets = new ArrayList<>();

                        String text = IKey.lang(patternProvider.getShowName()) +
                                " X:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ();

                        if (patternProvider.getShowName().contains(searchText)) add = true;

                        textWidgets.add(new ButtonWidget<>()
                                .size(18, 18)
                                .overlay(new ItemDrawable(patternProvider.getStackForm()).asIcon().size(16))
                                .onMousePressed(mouseButton -> {
                                    patternProvider.noticePlayer("当前指向的样板总成：" + text,
                                            guiSyncManager.getPlayer());
                                    return true;
                                })
                                .addTooltipLine(IKey.str(text))
                        );

                        if (patternProvider.getController() != null) {
                            String recipeName;
                            if (patternProvider.getController().getRecipeLogic() != null) {
                                RecipeMap<?> map = patternProvider.getController().getRecipeLogic().getRecipeMap();
                                if (map != null) {
                                    recipeName = I18n.format(map.getTranslationKey());
                                } else recipeName = "None";
                            } else recipeName = "None";

                            if (recipeName.contains(searchText)) add = true;

                            String controllerName = IKey.lang(patternProvider.getController().getMetaFullName())
                                    .toString();
                            String controllerText = controllerName +
                                    " X:" + patternProvider.getController().getPos().getX() + " Y:" +
                                    patternProvider.getController().getPos().getY() + " Z:" +
                                    patternProvider.getController().getPos().getZ();

                            if (controllerName.contains(searchText)) add = true;

                            textWidgets.add(new ButtonWidget<>()
                                    .size(18, 18)
                                    .overlay(new ItemDrawable(patternProvider.getController().getStackForm()).asIcon()
                                            .size(16))
                                    .onMousePressed(mouseButton -> {
                                        patternProvider.getController()
                                                .noticePlayer("当前样板总成所属多方块：" + controllerText,
                                                        guiSyncManager.getPlayer());
                                        return true;
                                    })
                                    .addTooltipLine(controllerText + IKey.str(" 当前配方：" + recipeName))
                            );
                        }

                        if (add) {
                            list.add(textWidgets);

                            for (int i = 0; i < rowSize; i++) {
                                // 创建新行
                                List<IWidget> rowWidgets = new ArrayList<>();
                                for (int j = 0; j < rowSize; j++) {
                                    int index = i * rowSize + j;

                                    // 在槽位范围内的创建ItemSlot
                                    rowWidgets.add(new ItemSlot()
                                            .slot(SyncHandlers.itemSlot(itemHandler, index)
                                                    .slotGroup("pattern_slots" + num)
                                                    .accessibility(true, true))
                                            .background(GTGuiTextures.SLOT, GTGuiTextures.PATTERN_OVERLAY)
                                    );
                                }

                                // 将当前行添加到列表中
                                list.add(rowWidgets);
                            }
                        }
                    }
                }

            }
        }

        return GTGuis.createPanel(this, 176, 238)
                .child(IKey.lang(getMetaFullName())
                        .asWidget()
                        .top(7).left(7))
                .child(Column.column()
                        .leftRel(0.5f)
                        .top(18)
                        .widthRel(0.9f)
                        .height(18)
                        .child(new TextFieldWidget()
                                .leftRel(0.55f)
                                .widthRel(0.7f)
                                .top(0)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("搜索框")))
                                .value(searchFieldValue)
                                .setTextColor(Color.WHITE.darker(1))
                                .background(GTGuiTextures.DISPLAY))

                        .child(new ButtonWidget<>()
                                .leftRel(0.10f)
                                .top(0)
                                .onMousePressed(mouseButton -> {
                                    posListValue.setStringValue("null");
                                    return true;
                                })
                                .overlay(GTGuiTextures.PATTERN_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("刷新终端缓存，重新打开UI以刷新")))
                        )
                )
                .child(new Grid()
                        .scrollable(new VerticalScrollData())
                        .top(36)
                        .width(18 * 9 + 4)
                        .height(18 * 6)
                        .leftRel(0.5f)
                        .matrix(list)
                )
                .bindPlayerInventory();
    }

    @Override
    public void update() {
        super.update();
        if (pos.isEmpty()) {
            try {
                for (IGridNode grid : getProxy().getGrid().getMachineNodes(MetaTileEntityMEPatternProvider.class)) {
                    pos.add(grid.getGridBlock().getLocation().getPos());
                }
            } catch (Exception ignored) {}
            try {
                for (IGridNode grid : getProxy().getGrid().getMachineNodes(MetaTileEntityHugeMEPatternProvider.class)) {
                    pos.add(grid.getGridBlock().getLocation().getPos());
                }
            } catch (Exception ignored) {}
            writeCustomData(UPDATE_ME_POS, buffer -> {
                buffer.writeInt(pos.size());
                for (BlockPos blockPos : pos) {
                    buffer.writeBlockPos(blockPos);
                }
            });
            markDirty();
        }
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == UPDATE_ME_POS) {
            int size = buf.readInt();
            pos.clear();
            for (int i = 0; i < size; i++) {
                pos.add(buf.readBlockPos());
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        // 序列化 BlockPos 列表到 NBT
        if (!pos.isEmpty()) {
            NBTTagCompound posListTag = new NBTTagCompound();
            for (int i = 0; i < pos.size(); i++) {
                BlockPos blockPos = pos.get(i);
                NBTTagCompound posTag = new NBTTagCompound();
                posTag.setInteger("x", blockPos.getX());
                posTag.setInteger("y", blockPos.getY());
                posTag.setInteger("z", blockPos.getZ());
                posListTag.setTag("pos_" + i, posTag);
            }
            data.setTag("posList", posListTag);
            data.setInteger("posListSize", pos.size());
        }
        data.setString("searchText", searchText);
        return super.writeToNBT(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("posList") && data.hasKey("posListSize")) {
            NBTTagCompound posListTag = data.getCompoundTag("posList");
            int size = data.getInteger("posListSize");
            pos.clear();
            for (int i = 0; i < size; i++) {
                if (posListTag.hasKey("pos_" + i)) {
                    NBTTagCompound posTag = posListTag.getCompoundTag("pos_" + i);
                    BlockPos blockPos = new BlockPos(
                            posTag.getInteger("x"),
                            posTag.getInteger("y"),
                            posTag.getInteger("z")
                    );
                    pos.add(blockPos);
                }
            }
        }
        searchText = data.getString("searchText");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeInt(pos.size());
        for (BlockPos blockPos : pos) {
            buf.writeBlockPos(blockPos);
        }
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        int size = buf.readInt();
        pos.clear();
        for (int i = 0; i < size; i++) {
            pos.add(buf.readBlockPos());
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.FUSION_REACTOR_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }
}
