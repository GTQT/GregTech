package gregtech.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.GregTechAPI;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.PagedWidgetSyncHandler;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialFlag;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.util.GTLog;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;
import gregtech.common.mui.widget.ScrollableTextWidget;

import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.grid.AENetworkPowerTile;
import appeng.util.item.AEItemStack;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static gregtech.api.util.AE2PatternCompat.*;

public class MetaTileEntityMEOrePrefixPatternProvider extends MetaTileEntityAEPatternRegistrar
        implements IMEPatternProviderPart {

    ArrayList<PrefixEntry> inputPrefixes = new ArrayList<>();
    ArrayList<PrefixEntry> outputPrefixes = new ArrayList<>();
    ArrayList<String> blackList = new ArrayList<>();
    ArrayList<String> whiteTagList = new ArrayList<>();
    ArrayList<String> blackTagList = new ArrayList<>();
    ItemStackHandler extraInput = new ItemStackHandler(8);
    ItemStackHandler extraOutput = new ItemStackHandler(2);

    public MetaTileEntityMEOrePrefixPatternProvider(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    private static IAEItemStack[] collectInventory(ItemStack[] slots) {
        List<IAEItemStack> acc = new ArrayList<>();
        for (ItemStack stack : slots) {
            if (stack == null || stack == ItemStack.EMPTY) continue;
            IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
            if (aeStack != null) acc.add(aeStack);
        }
        return acc.toArray(new IAEItemStack[0]);
    }

    public static String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }

    public static List<String> stringToList(String str) {
        List<String> result = new ArrayList<>();
        if (str != null && !str.trim().isEmpty()) {
            String[] parts = str.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
        }
        return result;
    }

    public static String prefixEntriesToString(List<PrefixEntry> entries) {
        if (entries == null || entries.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(entries.get(i).prefix).append(":").append(entries.get(i).amount);
        }
        return sb.toString();
    }

    public static List<PrefixEntry> stringToPrefixEntries(String str) {
        List<PrefixEntry> result = new ArrayList<>();
        if (str != null && !str.trim().isEmpty()) {
            String[] parts = str.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                String[] pair = trimmed.split(":");
                String prefix = pair[0];
                int amount = pair.length > 1 ? Math.max(1, Integer.parseInt(pair[1])) : 1;
                result.add(new PrefixEntry(prefix, amount));
            }
        }
        return result;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMEOrePrefixPatternProvider(metaTileEntityId, getTier());
    }

    @Override
    public void update() {
        super.update();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        if (shouldRenderOverlay()) {
            SimpleOverlayRenderer overlay = Textures.ME_BUFFER_HATCH_OVERLAY;
            overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("ExtraInput", this.extraInput.serializeNBT());
        data.setTag("ExtraOutput", this.extraOutput.serializeNBT());

        data.setString("inputPrefixes", prefixEntriesToString(inputPrefixes));
        data.setString("outputPrefixes", prefixEntriesToString(outputPrefixes));

        data.setInteger("blackListSize", blackList.size());
        for (int i = 0; i < blackList.size(); i++) {
            data.setString("blackList" + i, blackList.get(i));
        }
        data.setInteger("whiteTagListSize", whiteTagList.size());
        for (int i = 0; i < whiteTagList.size(); i++) {
            data.setString("whiteTagList" + i, whiteTagList.get(i));
        }
        data.setInteger("blackTagListSize", blackTagList.size());
        for (int i = 0; i < blackTagList.size(); i++) {
            data.setString("blackTagList" + i, blackTagList.get(i));
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.extraInput.deserializeNBT(data.getCompoundTag("ExtraInput"));
        this.extraOutput.deserializeNBT(data.getCompoundTag("ExtraOutput"));

        List<PrefixEntry> loaded = stringToPrefixEntries(data.getString("inputPrefixes"));
        inputPrefixes.clear();
        if (loaded != null) inputPrefixes.addAll(loaded);

        loaded = stringToPrefixEntries(data.getString("outputPrefixes"));
        outputPrefixes.clear();
        if (loaded != null) outputPrefixes.addAll(loaded);

        blackList.clear();
        int size = data.getInteger("blackListSize");
        for (int i = 0; i < size; i++) {
            blackList.add(data.getString("blackList" + i));
        }
        whiteTagList.clear();
        size = data.getInteger("whiteTagListSize");
        for (int i = 0; i < size; i++) {
            whiteTagList.add(data.getString("whiteTagList" + i));
        }
        blackTagList.clear();
        size = data.getInteger("blackTagListSize");
        for (int i = 0; i < size; i++) {
            blackTagList.add(data.getString("blackTagList" + i));
        }
    }

    @Override
    public void setPatternDetails() {
        patternDetails = new ArrayList<>();
        List<ItemStack> patternSlot = createPatterns();
        for (int i = 0; i < patternSlot.size(); i++) {
            ItemStack pattern = patternSlot.get(i);
            if (pattern.isEmpty()) {
                patternDetails.add(i, null);
                continue;
            }

            if (pattern.getItem() instanceof ICraftingPatternItem patternItem) {
                patternDetails.add(i, patternItem.getPatternForItem(pattern, getWorld()));
            }
        }
    }

    @Override
    public List<ItemStack> createPatterns() {
        ArrayList<ItemStack> patterns = new ArrayList<>();

        // 1. 基础验证
        if (inputPrefixes.isEmpty() || outputPrefixes.isEmpty()) {
            return patterns;
        }

        // 2. 材料过滤
        List<MaterialFlag> whiteTag = MaterialFlag.getFlagListByName(whiteTagList);
        List<MaterialFlag> blackTag = MaterialFlag.getFlagListByName(blackTagList);

        for (Material material : GregTechAPI.materialManager.getRegisteredMaterials()) {
            if (blackList.contains(material.toString())) continue;
            if (!MaterialFlag.checkMaterialHasFlag(material, whiteTag, blackTag)) continue;

            // 3. 笛卡尔积：遍历所有输入输出前缀条目
            for (PrefixEntry inputEntry : inputPrefixes) {
                for (PrefixEntry outputEntry : outputPrefixes) {
                    String inPrefix = inputEntry.prefix;
                    String outPrefix = outputEntry.prefix;
                    boolean isFluidPattern = "fluid".equals(inPrefix) || "fluid".equals(outPrefix);

                    ItemStack inputStack;
                    ItemStack outputStack;

                    if ("fluid".equals(inPrefix)) {
                        if (!material.hasFluid()) continue;
                        FluidStack fluid = material.getFluid(inputEntry.amount);
                        inputStack = fluid == null ? ItemStack.EMPTY : toFluidDrop(fluid);
                    } else {
                        OrePrefix orePrefix = OrePrefix.getPrefix(inPrefix);
                        if (orePrefix == null) continue;
                        inputStack = OreDictUnifier.get(orePrefix, material, inputEntry.amount);
                    }

                    if ("fluid".equals(outPrefix)) {
                        if (!material.hasFluid()) continue;
                        FluidStack fluid = material.getFluid(outputEntry.amount);
                        outputStack = fluid == null ? ItemStack.EMPTY : toFluidDrop(fluid);
                    } else {
                        OrePrefix orePrefix = OrePrefix.getPrefix(outPrefix);
                        if (orePrefix == null) continue;
                        outputStack = OreDictUnifier.get(orePrefix, material, outputEntry.amount);
                    }

                    if (inputStack.isEmpty() || outputStack.isEmpty()) continue;

                    patterns.add(virtualCraftingPattern(inputStack, outputStack, true, isFluidPattern));
                }
            }
        }
        return patterns;
    }

    private ItemStack virtualCraftingPattern(
            ItemStack input,
            ItemStack output,
            boolean substitute,
            boolean isFluidPattern
    ) {
        // 1. 准备槽位 (AE2标准布局)
        ItemStack[] inputs = new ItemStack[9];
        ItemStack[] outputs = new ItemStack[3];

        // 1.1 主槽位
        inputs[0] = input.copy();
        outputs[0] = output.copy();

        // Extra item slots are local push metadata only; do not encode them into AE patterns.

        // 2. 无条件执行决策
        return isFluidPattern ?
                createFluidPattern(inputs, outputs, substitute) :
                createStandardPattern(inputs, outputs, substitute);
    }

    // 创建流体样板 (处理模式)
    @Override
    protected void wrapExtraInputsAsProgrammable(InventoryCrafting table) {
        int slotLimit = Math.min(9, table.getSizeInventory());
        for (int i = 1; i < slotLimit; i++) {
            table.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < extraInput.getSlots() && i + 1 < slotLimit; i++) {
            ItemStack extra = extraInput.getStackInSlot(i);
            if (extra.isEmpty()) continue;

            ItemStack wrapped = wrapAsProgrammable(extra);
            if (wrapped != null && !wrapped.isEmpty()) {
                table.setInventorySlotContents(i + 1, wrapped);
            }
        }
    }

    private ItemStack createFluidPattern(ItemStack[] inputs, ItemStack[] outputs, boolean substitute) {
        // 1. 创建流体样板
        return createProcessingPattern(inputs, outputs, substitute, true);

        // 2. 设置槽位数据

    }

    // 创建标准物品样板
    private ItemStack createStandardPattern(
            ItemStack[] inputs,
            ItemStack[] outputs,
            boolean substitute
    ) {
        // 1. 构建NBT
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList inTag = new NBTTagList();
        NBTTagList outTag = new NBTTagList();

        for (ItemStack i : inputs) inTag.appendTag(createItemTag(i));
        for (ItemStack i : outputs) outTag.appendTag(createItemTag(i));

        tag.setTag("in", inTag);
        tag.setTag("out", outTag);
        tag.setBoolean("crafting", false);  // 处理模式
        tag.setBoolean("substitute", substitute);

        // 2. 获取样板原型
        Optional<ItemStack> maybePattern = AEApi.instance()
                .definitions()
                .items()
                .encodedPattern()
                .maybeStack(1);

        if (!maybePattern.isPresent()) {
            GTLog.logger.error("Standard pattern item not found! Is AE2 loaded?");
            return ItemStack.EMPTY;
        }

        // 3. 注入NBT
        ItemStack patternStack = maybePattern.get();
        patternStack.setTagCompound(tag);
        return patternStack;
    }

    NBTBase createItemTag(final ItemStack i) {
        if (i == null) return new NBTTagCompound();
        return createPatternIngredientTag(i);
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
    }

    // ==================== IMEPatternProviderPart ====================

    @Override
    @Nullable
    public net.minecraftforge.items.IItemHandler getPatternSlot() {
        return null;
    }

    @Override
    public String getShowName() {
        return getMetaFullName();
    }

    @Override
    @Nullable
    public MultiblockControllerBase getController() {
        return super.getController();
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        int backgroundWidth = 9 * 18 + 18 + 14 + 5 + 18;
        int backgroundHeight = 18 + 18 * 7 + 94;
        guiSyncManager.registerSlotGroup("item_inv", extraInput.getSlots());

        List<List<IWidget>> widgetsPattern = new ArrayList<>();
        widgetsPattern.add(new ArrayList<>());
        for (int i = 0; i < extraInput.getSlots(); i++) {
            widgetsPattern.get(0)
                    .add(new ItemSlot()
                            .slot(SyncHandlers.itemSlot(extraInput, i)
                                    .slotGroup("item_inv")
                                    .accessibility(true, true)
                            )
                            .background(GTGuiTextures.SLOT)
                    );
        }

        widgetsPattern.add(new ArrayList<>());
        for (int i = 0; i < extraOutput.getSlots(); i++) {
            widgetsPattern.get(1)
                    .add(new ItemSlot()
                            .slot(SyncHandlers.itemSlot(extraOutput, i)
                                    .slotGroup("item_inv")
                                    .accessibility(true, true)
                            )
                            .background(GTGuiTextures.SLOT)
                    );
        }

        // 1. 创建同步值
        StringSyncValue inputPrefixesValue = new StringSyncValue(
                () -> prefixEntriesToString(inputPrefixes),
                str -> {
                    if (str != null) {
                        List<PrefixEntry> newList = stringToPrefixEntries(str);
                        inputPrefixes.clear();
                        inputPrefixes.addAll(newList);
                    }
                }
        );

        StringSyncValue outputPrefixesValue = new StringSyncValue(
                () -> prefixEntriesToString(outputPrefixes),
                str -> {
                    if (str != null) {
                        List<PrefixEntry> newList = stringToPrefixEntries(str);
                        outputPrefixes.clear();
                        outputPrefixes.addAll(newList);
                    }
                }
        );

        // 注册同步值
        guiSyncManager.syncValue("inputPrefixes", inputPrefixesValue);
        guiSyncManager.syncValue("outputPrefixes", outputPrefixesValue);

        List<List<IWidget>> weightText = new ArrayList<>();

        // ★ 输入矿辞管理行 ★
        weightText.add(new ArrayList<>());

        TextFieldWidget inputPrefixTextField = new TextFieldWidget()
                .widthRel(0.28f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .background(GTGuiTextures.DISPLAY);

        weightText.get(0).add(inputPrefixTextField);
        weightText.get(0).add(IKey.str(" x ").asWidget()
                .widthRel(0.05f)
                .height(20));

        TextFieldWidget inputAmountTextField = new TextFieldWidget()
                .widthRel(0.12f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (str.isEmpty()) return "1";
                    try {
                        int num = Integer.parseInt(str);
                        return String.valueOf(Math.max(1, num));
                    } catch (NumberFormatException e) { return "1"; }
                })
                .background(GTGuiTextures.DISPLAY);
        weightText.get(0).add(inputAmountTextField);

        // Add 按钮
        weightText.get(0).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String prefix = inputPrefixTextField.getText().trim();
                    int amount;
                    try {
                        amount = Math.max(1, Integer.parseInt(inputAmountTextField.getText()));
                    } catch (NumberFormatException e) { amount = 1; }
                    if (!prefix.isEmpty()) {
                        inputPrefixes.removeIf(e -> e.prefix.equals(prefix));
                        inputPrefixes.add(new PrefixEntry(prefix, amount));
                        inputPrefixesValue.setValue(prefixEntriesToString(inputPrefixes));
                        guiSyncManager.getPlayer().sendMessage(
                                new TextComponentTranslation(prefix + " x" + amount + " 已添加到输入矿辞"));
                        inputPrefixTextField.setText("");
                    }
                    return true;
                })
                .overlay(GTGuiTextures.PLUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("添加输入矿辞"))));

        // Remove 按钮
        weightText.get(0).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = inputPrefixTextField.getText().trim();
                    if (!value.isEmpty()) {
                        inputPrefixes.removeIf(e -> e.prefix.equals(value));
                        inputPrefixesValue.setValue(prefixEntriesToString(inputPrefixes));
                        guiSyncManager.getPlayer().sendMessage(
                                new TextComponentTranslation(value + " 已从输入矿辞移除"));
                        inputPrefixTextField.setText("");
                    }
                    return true;
                })
                .overlay(GTGuiTextures.MINUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("移除输入矿辞"))));

        // ★ 输出矿辞管理行 ★
        weightText.add(new ArrayList<>());

        TextFieldWidget outputPrefixTextField = new TextFieldWidget()
                .widthRel(0.28f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .background(GTGuiTextures.DISPLAY);

        weightText.get(1).add(outputPrefixTextField);
        weightText.get(1).add(IKey.str(" x ").asWidget()
                .widthRel(0.05f)
                .height(20));

        TextFieldWidget outputAmountTextField = new TextFieldWidget()
                .widthRel(0.12f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (str.isEmpty()) return "1";
                    try {
                        int num = Integer.parseInt(str);
                        return String.valueOf(Math.max(1, num));
                    } catch (NumberFormatException e) { return "1"; }
                })
                .background(GTGuiTextures.DISPLAY);
        weightText.get(1).add(outputAmountTextField);

        // Add 按钮
        weightText.get(1).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String prefix = outputPrefixTextField.getText().trim();
                    int amount;
                    try {
                        amount = Math.max(1, Integer.parseInt(outputAmountTextField.getText()));
                    } catch (NumberFormatException e) { amount = 1; }
                    if (!prefix.isEmpty()) {
                        outputPrefixes.removeIf(e -> e.prefix.equals(prefix));
                        outputPrefixes.add(new PrefixEntry(prefix, amount));
                        outputPrefixesValue.setValue(prefixEntriesToString(outputPrefixes));
                        guiSyncManager.getPlayer().sendMessage(
                                new TextComponentTranslation(prefix + " x" + amount + " 已添加到输出矿辞"));
                        outputPrefixTextField.setText("");
                    }
                    return true;
                })
                .overlay(GTGuiTextures.PLUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("添加输出矿辞"))));

        // Remove 按钮
        weightText.get(1).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = outputPrefixTextField.getText().trim();
                    if (!value.isEmpty()) {
                        outputPrefixes.removeIf(e -> e.prefix.equals(value));
                        outputPrefixesValue.setValue(prefixEntriesToString(outputPrefixes));
                        guiSyncManager.getPlayer().sendMessage(
                                new TextComponentTranslation(value + " 已从输出矿辞移除"));
                        outputPrefixTextField.setText("");
                    }
                    return true;
                })
                .overlay(GTGuiTextures.MINUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("移除输出矿辞"))));

        // 在初始化时创建同步值
        StringSyncValue blackListValue = new StringSyncValue(
                () -> listToString(blackList),
                str -> {
                    if (str != null) {
                        List<String> newList = stringToList(str);
                        blackList.clear();
                        blackList.addAll(newList);
                    }
                }
        );

        StringSyncValue whiteTagListValue = new StringSyncValue(
                () -> listToString(whiteTagList),
                str -> {
                    if (str != null) {
                        List<String> newList = stringToList(str);
                        whiteTagList.clear();
                        whiteTagList.addAll(newList);
                    }
                }
        );

        StringSyncValue blackTagListValue = new StringSyncValue(
                () -> listToString(blackTagList),
                str -> {
                    if (str != null) {
                        List<String> newList = stringToList(str);
                        blackTagList.clear();
                        blackTagList.addAll(newList);
                    }
                }
        );

        // 注册到同步管理器
        guiSyncManager.syncValue("blackList", blackListValue);
        guiSyncManager.syncValue("whiteTagList", whiteTagListValue);
        guiSyncManager.syncValue("blackTagList", blackTagListValue);

        // ★ 第三行：黑名单管理行 ★
        weightText.add(new ArrayList<>());

        // 创建文本框并保存引用
        TextFieldWidget blackListTextField = new TextFieldWidget()
                .widthRel(0.4f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .background(GTGuiTextures.DISPLAY);

        weightText.get(2).add(blackListTextField);

        // 添加到黑名单按钮
        weightText.get(2).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = blackListTextField.getText().trim();
                    if (!value.isEmpty() && !blackList.contains(value)) {
                        // 添加并同步
                        blackList.add(value);
                        blackListValue.setValue(listToString(blackList));

                        guiSyncManager.getPlayer().sendMessage(new TextComponentTranslation(value + "已添加到黑名单"));
                        blackListTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.PLUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("添加到黑名单"))));

        // 从黑名单移除按钮
        weightText.get(2).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = blackListTextField.getText().trim();
                    if (!value.isEmpty() && blackList.contains(value)) {
                        // 移除并同步
                        blackList.remove(value);
                        blackListValue.setValue(listToString(blackList));

                        guiSyncManager.getPlayer()
                                .sendMessage(new TextComponentTranslation(value + "已从黑名单中移除"));
                        blackListTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.MINUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("从黑名单移除"))));

        // 显示当前黑名单按钮
        weightText.get(2).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    if (blackList.isEmpty()) {
                        guiSyncManager.getPlayer().sendMessage(new TextComponentTranslation("黑名单列表为空"));
                        return true;
                    }

                    StringBuilder sb = new StringBuilder("黑名单列表：");
                    for (int i = 0; i < blackList.size(); i++) {
                        sb.append(blackList.get(i));
                        if (i < blackList.size() - 1) {
                            sb.append(", ");
                        }
                    }
                    guiSyncManager.getPlayer().sendMessage(new TextComponentTranslation(sb.toString()));

                    return true;
                })
                .overlay(GTGuiTextures.BUTTON_BLACKLIST)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("显示当前黑名单列表"))));

        List<List<IWidget>> weightTagList = new ArrayList<>();
        // ★ 第一行：白标签管理行 ★
        weightTagList.add(new ArrayList<>());

        // 白标签文本框
        TextFieldWidget whiteTagTextField = new TextFieldWidget()
                .widthRel(0.4f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .background(GTGuiTextures.DISPLAY);

        weightTagList.get(0).add(whiteTagTextField);

        // 添加到白标签按钮
        weightTagList.get(0).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = whiteTagTextField.getText().trim();
                    if (!value.isEmpty() && !whiteTagList.contains(value)) {
                        // 添加并同步
                        whiteTagList.add(value);
                        whiteTagListValue.setValue(listToString(whiteTagList));

                        guiSyncManager.getPlayer().sendMessage(new TextComponentTranslation(value + "已添加到白标签"));
                        whiteTagTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.PLUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("添加到白标签"))));

        // 从白标签移除按钮
        weightTagList.get(0).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = whiteTagTextField.getText().trim();
                    if (!value.isEmpty() && whiteTagList.contains(value)) {
                        // 移除并同步
                        whiteTagList.remove(value);
                        whiteTagListValue.setValue(listToString(whiteTagList));

                        guiSyncManager.getPlayer()
                                .sendMessage(new TextComponentTranslation(value + "已从白标签中移除"));
                        whiteTagTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.MINUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("从白标签移除"))));

        // ★ 第二行：黑标签管理行 ★
        weightTagList.add(new ArrayList<>());

        // 黑标签文本框
        TextFieldWidget blackTagTextField = new TextFieldWidget()
                .widthRel(0.4f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .background(GTGuiTextures.DISPLAY);

        weightTagList.get(1).add(blackTagTextField);

        // 添加到黑标签按钮
        weightTagList.get(1).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = blackTagTextField.getText().trim();
                    if (!value.isEmpty() && !blackTagList.contains(value)) {
                        // 添加并同步
                        blackTagList.add(value);
                        blackTagListValue.setValue(listToString(blackTagList));

                        guiSyncManager.getPlayer().sendMessage(new TextComponentTranslation(value + "已添加到黑标签"));
                        blackTagTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.PLUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("添加到黑标签"))));

        // 从黑标签移除按钮
        weightTagList.get(1).add(new ButtonWidget<>()
                .size(18, 18)
                .onMousePressed(mouseButton -> {
                    String value = blackTagTextField.getText().trim();
                    if (!value.isEmpty() && blackTagList.contains(value)) {
                        // 移除并同步
                        blackTagList.remove(value);
                        blackTagListValue.setValue(listToString(blackTagList));

                        guiSyncManager.getPlayer()
                                .sendMessage(new TextComponentTranslation(value + "已从黑标签中移除"));
                        blackTagTextField.setText(""); // 清空文本框
                    }
                    return true;
                })
                .overlay(GTGuiTextures.MINUS)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("从黑标签移除"))));

        // 创建用于显示的值（带前缀）和用于存储的值（纯数字）
        StringSyncValue displayXValue = new StringSyncValue(
                () -> "X:" + AEProxy_pos.getX(),  // 显示时带前缀
                str -> {
                    // 移除前缀并解析
                    if (str.startsWith("X:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("x:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(Integer.parseInt(str.trim()), AEProxy_pos.getY(),
                                AEProxy_pos.getZ());
                    } catch (NumberFormatException e) {
                        // 解析失败时保持原值
                        System.err.println("Invalid X coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayYValue = new StringSyncValue(
                () -> "Y:" + AEProxy_pos.getY(),
                str -> {
                    if (str.startsWith("Y:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("y:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(AEProxy_pos.getX(), Integer.parseInt(str.trim()),
                                AEProxy_pos.getZ());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Y coordinate: " + str);
                    }
                }
        );

        StringSyncValue displayZValue = new StringSyncValue(
                () -> "Z:" + AEProxy_pos.getZ(),
                str -> {
                    if (str.startsWith("Z:")) {
                        str = str.substring(2);
                    } else if (str.startsWith("z:")) {
                        str = str.substring(2);
                    }
                    try {
                        AEProxy_pos = new BlockPos(AEProxy_pos.getX(), AEProxy_pos.getY(),
                                Integer.parseInt(str.trim()));
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid Z coordinate: " + str);
                    }
                }
        );

        // 注册同步值
        BooleanSyncValue useProxyStateValue = new BooleanSyncValue(() -> useProxy, val -> useProxy = val);
        guiSyncManager.syncValue("useProxyStateValue", useProxyStateValue);

        List<List<IWidget>> weightsPos = new ArrayList<>();
        List<IWidget> row = new ArrayList<>();

        // 添加开关按钮
        row.add(new ToggleButton()
                .width(20)
                .height(20)
                .value(new BoolValue.Dynamic(useProxyStateValue::getBoolValue,
                        useProxyStateValue::setBoolValue))
                .overlay(GTGuiTextures.PROXY_OVERLAY)
                .tooltip(tooltip -> tooltip.addLine(IKey.str("无线代理模式"))));

        // 添加X坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    // 确保字符串以X:开头
                    if (!str.startsWith("X:") && !str.startsWith("x:")) {
                        if (str.isEmpty()) {
                            return "X:";
                        }
                        // 如果用户删除了前缀，自动添加回来
                        return "X:" + str;
                    }

                    // 提取数字部分进行验证
                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str; // 允许空数字部分（用户正在输入）
                    }

                    try {
                        // 验证数字部分
                        Long.parseLong(numPart.trim());
                        return str; // 验证通过
                    } catch (NumberFormatException e) {
                        // 验证失败，返回当前值
                        return displayXValue.getValue();
                    }
                })
                .value(displayXValue)
                .background(GTGuiTextures.DISPLAY)));

        // 添加Y坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (!str.startsWith("Y:") && !str.startsWith("y:")) {
                        if (str.isEmpty()) {
                            return "Y:";
                        }
                        return "Y:" + str;
                    }

                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str;
                    }

                    try {
                        Long.parseLong(numPart.trim());
                        return str;
                    } catch (NumberFormatException e) {
                        return displayYValue.getValue();
                    }
                })
                .value(displayYValue)
                .background(GTGuiTextures.DISPLAY)));

        // 添加Z坐标文本框
        row.add((new TextFieldWidget()
                .widthRel(0.25f)
                .height(20)
                .setTextColor(Color.WHITE.darker(1))
                .setValidator(str -> {
                    if (!str.startsWith("Z:") && !str.startsWith("z:")) {
                        if (str.isEmpty()) {
                            return "Z:";
                        }
                        return "Z:" + str;
                    }

                    String numPart = str.substring(2);
                    if (numPart.isEmpty()) {
                        return str;
                    }

                    try {
                        Long.parseLong(numPart.trim());
                        return str;
                    } catch (NumberFormatException e) {
                        return displayZValue.getValue();
                    }
                })
                .value(displayZValue)
                .background(GTGuiTextures.DISPLAY)));

        weightsPos.add(row);

        BooleanSyncValue blockStateValue = new BooleanSyncValue(this::isBlockedMode, this::setBlockedMode);
        guiSyncManager.syncValue("block_state", blockStateValue);

        BooleanSyncValue collapseStateValue = new BooleanSyncValue(this::isAutoCollapse, this::setAutoCollapse);
        guiSyncManager.syncValue("collapse_state", collapseStateValue);

        BooleanSyncValue exportStateValue = new BooleanSyncValue(this::isExport, this::setExport);
        guiSyncManager.syncValue("export_state", exportStateValue);

        var controller = new PagedWidget.Controller();
        guiSyncManager.syncValue("page_controller", new PagedWidgetSyncHandler(controller));

        return GTGuis.createPanel(this, backgroundWidth, backgroundHeight)
                .child(Flow.row()
                        .name("tab row")
                        .widthRel(1f)
                        .leftRel(0.5f)
                        .margin(3, 0)
                        .coverChildrenHeight()
                        .topRel(0f, 3, 1f)
                        .child(new PageButton(0, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("前缀模式"))
                                .overlay(HATCH))
                        .child(new PageButton(1, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("标签过滤"))
                                .overlay(FILTER))
                        .child(new PageButton(2, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang("网络代理"))
                                .overlay(PROXY))
                        .child(new PageButton(3, controller)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .addTooltipLine(IKey.lang(
                                        "gregtech.machine.me_ore_prefix_pattern_provider.ui.tab.link"))
                                .overlay(LINK))
                )
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new PagedWidget<>()
                        .top(18) // 调整 PagedWidget 的顶部位置为 18
                        .margin(0) // 移除 margin 避免偏移
                        .widthRel(1f) // 宽度设为父容器的 100%
                        .controller(controller)
                        .addPage(// 样板模式页面
                                Flow.column() // 使用列布局
                                        .top(0)
                                        .widthRel(0.8f)
                                        .leftRel(0.5f)
                                        .child(
                                                new Grid()
                                                        .height(36)
                                                        .leftRel(0.5f)
                                                        .widthRel(1.0f)
                                                        .matrix(widgetsPattern)
                                        )
                                        .child(
                                                new Grid()
                                                        .top(36)
                                                        .leftRel(0.5f)
                                                        .widthRel(1.0f)
                                                        .matrix(weightText)
                                        )

                        )
                        .addPage(
                                Flow.column() // 使用列布局
                                        .top(0)
                                        .widthRel(0.8f)
                                        .leftRel(0.5f)
                                        .child(
                                                new Grid()
                                                        .height(40)
                                                        .leftRel(0.5f)
                                                        .widthRel(1.0f)
                                                        .matrix(weightTagList)
                                        )
                                        .child(
                                                new ScrollableTextWidget()
                                                        .top(40)
                                                        .height(54)
                                                        .autoUpdate(true)
                                                        .leftRel(0.5f)
                                                        .widthRel(1.0f)
                                                        .alignment(Alignment.TopLeft)
                                                        .textBuilder(text -> {
                                                            text.addLine(IKey.lang("白标签列表"));
                                                            for (String tag : whiteTagList) {
                                                                text.addLine(tag);
                                                            }
                                                            text.addLine(IKey.lang("黑标签列表"));
                                                            for (String tag : blackTagList) {
                                                                text.addLine(tag);
                                                            }
                                                        })
                                        )

                        )
                        .addPage(// 代理模式页面
                                Flow.column() // 使用列布局
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .child(
                                                new Grid()
                                                        .height(25)
                                                        .minElementMargin(0, 0)
                                                        .minColWidth((int) (0.24f * backgroundWidth))
                                                        .minRowHeight(18)
                                                        .matrix(weightsPos)
                                        )
                                        .childIf(useProxy, () -> Flow.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("无线代理模式")))
                                                .childIf(useProxy, () -> {
                                                    TileEntity tileEntity = this.getWorld().getTileEntity(
                                                            AEProxy_pos);
                                                    if (tileEntity instanceof AENetworkPowerTile proxy) {
                                                        return Flow.column()
                                                                .widthRel(1f)
                                                                .child(new TextWidget<>(IKey.lang("连接至无线网络")))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "位置:" + proxy.getLocation()
                                                                )))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "名称:" +
                                                                                proxy.getBlockType().getLocalizedName()
                                                                )));
                                                    } else {
                                                        return Flow.column()
                                                                .widthRel(1f)
                                                                .child(new TextWidget<>(IKey.str("未找到无线网络代理")))
                                                                .child(new TextWidget<>(IKey.dynamic(() ->
                                                                        "坐标:" + AEProxy_pos.getX() + ", " +
                                                                                AEProxy_pos.getY() + ", " +
                                                                                AEProxy_pos.getZ()
                                                                )));
                                                    }
                                                })
                                        )
                                        .childIf(!useProxy, () -> Flow.column() // 创建多行文本列
                                                .widthRel(1f)
                                                .top(30)
                                                .margin(5, 0)
                                                .child(new TextWidget<>(IKey.str("有线代理模式")))
                                        )
                        )
                        .addPage(// 链接信息页面
                                Flow.column()
                                        .top(0)
                                        .widthRel(1f)
                                        .leftRel(0.5f)
                                        .margin(5, 0)
                                        .child(new TextWidget<>(IKey.lang(
                                                "gregtech.machine.me_ore_prefix_pattern_provider.ui.link.title")))
                                        .child(new TextWidget<>(IKey.dynamic(this::getLinkStatusText)))
                                        .child(new TextWidget<>(IKey.dynamic(this::getLinkTargetText)))
                                        .child(new TextWidget<>(IKey.dynamic(this::getLinkBufferText)))
                                        .child(new TextWidget<>(IKey.lang(
                                                "gregtech.machine.me_ore_prefix_pattern_provider.ui.link.hint")))
                        )
                )
                .child(Flow.column()
                        .pos(backgroundWidth - 7 - 36, backgroundHeight - 18 * 4 - 7 - 5)
                        .width(36).height(18 * 4 + 5)

                        .child(GTGuiTextures.getLogo(getUITheme()).asWidget()
                                .top(18 * 3 + 5)
                                .size(17)
                        )

                        .child(new ToggleButton()
                                .top(18 * 2)
                                .value(new BoolValue.Dynamic(blockStateValue::getBoolValue,
                                        blockStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_OUTPUT)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("阻挡模式"))))
                        .child(new ToggleButton()
                                .top(18 * 2)
                                .left(18)
                                .value(new BoolValue.Dynamic(exportStateValue::getBoolValue,
                                        exportStateValue::setBoolValue))
                                .overlay(GTGuiTextures.EXPORT_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("返回模式"))))

                        .child(new ToggleButton()
                                .top(18)
                                .value(new BoolValue.Dynamic(collapseStateValue::getBoolValue,
                                        collapseStateValue::setBoolValue))
                                .overlay(GTGuiTextures.BUTTON_DUAL_COLLAPSE)
                                .tooltip(tooltip -> tooltip.addLine(IKey.str("自动整理"))))

                        .child(new ButtonWidget<>()
                                .top(0)
                                .onMousePressed(mouseButton -> {
                                    handlePatternGenerate(guiSyncManager.getPlayer());
                                    return true;
                                })
                                .overlay(GTGuiTextures.PATTERN_OVERLAY)
                                .tooltip(tooltip -> tooltip.addLine(IKey.lang(
                                        "gregtech.machine.me_ore_prefix_pattern_provider.ui.generate.button"))))

                );
    }

    private void handlePatternGenerate(EntityPlayer player) {
        int patternCount = refreshGeneratedPatternDetails();
        if (patternCount <= 0) {
            setNeedPatternSync(true);
            player.sendStatusMessage(new TextComponentTranslation(
                    "gregtech.machine.me_ore_prefix_pattern_provider.ui.generate.none"), true);
            return;
        }

        setNeedPatternSync(true);
        boolean waitingForSync = mePatternChange();
        setNeedPatternSync(waitingForSync);

        String messageKey = waitingForSync
                ? "gregtech.machine.me_ore_prefix_pattern_provider.ui.generate.queued"
                : hasMaster()
                        ? "gregtech.machine.me_ore_prefix_pattern_provider.ui.generate.success"
                        : "gregtech.machine.me_ore_prefix_pattern_provider.ui.generate.unlinked";
        player.sendStatusMessage(new TextComponentTranslation(messageKey, patternCount), true);
    }

    private int refreshGeneratedPatternDetails() {
        setPatternDetails();
        if (patternDetails == null) {
            return 0;
        }
        int patternCount = 0;
        for (Object detail : patternDetails) {
            if (detail != null) {
                patternCount++;
            }
        }
        return patternCount;
    }

    private String getLinkStatusText() {
        if (hasMaster() && masterPos != null) {
            return I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.ui.link.status.linked",
                    masterPos.getX(), masterPos.getY(), masterPos.getZ());
        }
        if (masterSet && masterPos != null) {
            return I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.ui.link.status.waiting",
                    masterPos.getX(), masterPos.getY(), masterPos.getZ());
        }
        return I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.ui.link.status.none");
    }

    private String getLinkTargetText() {
        if (!hasMaster()) {
            return "";
        }
        return I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.ui.link.status.target",
                I18n.format(master.getMetaFullName()));
    }

    private String getLinkBufferText() {
        if (!hasMaster()) {
            return "";
        }
        int usedBuffers = 0;
        List<MetaTileEntityMEPatternProvider.PatternBuffer> bufferPool = master.getBufferPool();
        if (bufferPool == null) {
            return "";
        }
        for (MetaTileEntityMEPatternProvider.PatternBuffer buffer : bufferPool) {
            if (!buffer.isEmpty()) usedBuffers++;
        }
        return I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.ui.link.status.buffers",
                usedBuffers, master.getBufferCount());
    }

    @Override
    public void getSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> subItems) {
        super.getSubItems(creativeTab, subItems);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.tooltip.4"));
        tooltip.add(I18n.format("gregtech.machine.me_ore_prefix_pattern_provider.tooltip.5"));
        tooltip.add(I18n.format("gregtech.machine.me_pattern.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.me.data_stick_proxy"));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    public static class PrefixEntry {

        public String prefix;
        public int amount;

        public PrefixEntry(String prefix, int amount) {
            this.prefix = prefix;
            this.amount = amount;
        }
    }
}
