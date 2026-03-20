package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingBus;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEOreDictBus extends MetaTileEntityMEStockingBus {

    protected String oreDictName= "在这里输入矿辞前缀";

    public MetaTileEntityMEOreDictBus(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEOreDictBus(metaTileEntityId, getTier());
    }


    @Override
    protected ModularPanel buildSettingsPopup(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        return super.buildSettingsPopup(syncManager, syncHandler)
                .child(IKey.lang("gregtech.machine.me.settings.minimum")
                        .asWidget()
                        .left(5)
                        .top(5 + 18 + 18 + 8))
                .child(new TextFieldWidget()
                        .left(5)
                        .top(15 + 18 + 18 + 8)
                        .size(100, 10)
                        .setNumbers(0, Integer.MAX_VALUE)
                        .setDefaultNumber(0)
                        .value(new IntSyncValue(this::getMinimumStackSize, this::setMinimumStackSize)))
                .child(IKey.lang("gregtech.machine.me.settings.ore_dict")
                        .asWidget()
                        .left(5)
                        .top(30 + 18 + 18 + 8))
                .child(new TextFieldWidget()
                        .left(5)
                        .top(35 + 18 + 18 + 8)
                        .size(100, 10)
                        .value(new StringSyncValue(this::getOreDict, this::setOreDict)))
                ;
    }

    @Override
    protected int getSettingsPopupHeight() {
        return super.getSettingsPopupHeight() + 20;
    }

    private void setOreDict(String s) {
        oreDictName = s;
        refreshList();
    }

    private String getOreDict() {
        return oreDictName;
    }

    @Override
    protected void refreshList() {
        IMEMonitor<IAEItemStack> monitor = getMonitor();
        if (monitor == null) {
            clearInventory(0);
            return;
        }

        IItemList<IAEItemStack> storageList = monitor.getStorageList();
        if (storageList == null) {
            clearInventory(0);
            return;
        }

        int index = 0;
        ExportOnlyAEStockingItemSlot[] inventory = getAEHandler().getInventory();
        for (IAEItemStack stack : storageList) {
            if (index >= CONFIG_SIZE) break;
            if (stack.getStackSize() == 0) continue;

            stack = monitor.extractItems(stack, Actionable.SIMULATE, getActionSource());
            if (stack == null || stack.getStackSize() == 0) continue;

            ItemStack itemStack = stack.createItemStack();
            if (itemStack == null || itemStack.isEmpty()) continue;

            // 检查物品是否有匹配的矿辞
            if (!hasMatchingOreDict(itemStack, oreDictName)) continue;

            // Ensure that it is valid to configure with this stack
            if (autoPullTest != null && !autoPullTest.test(itemStack)) continue;
            IAEItemStack selectedStack = stack.copy();
            IAEItemStack configStack = selectedStack.copy().setStackSize(1);
            ExportOnlyAEStockingItemSlot slot = inventory[index];
            slot.setConfig(configStack);
            slot.setStack(selectedStack);
            index++;
        }

        clearInventory(index);
    }

    /**
     * 检查物品是否有匹配指定正则表达式的矿辞
     * @param itemStack 要检查的物品
     * @param oreDictPattern 矿辞正则表达式
     * @return 如果有匹配的矿辞返回true，否则返回false
     */
    private boolean hasMatchingOreDict(ItemStack itemStack, String oreDictPattern) {
        int[] oreIDs = OreDictionary.getOreIDs(itemStack);
        for (int oreID : oreIDs) {
            String oreName = OreDictionary.getOreName(oreID);
            if (oreName.matches(oreDictPattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setString("oreDictName", oreDictName);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        oreDictName = data.getString("oreDictName");
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.me_bus.ore_dict.tooltip"));
    }
}
