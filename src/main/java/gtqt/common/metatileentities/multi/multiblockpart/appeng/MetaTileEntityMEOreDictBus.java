package gtqt.common.metatileentities.multi.multiblockpart.appeng;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ImageCycleButtonWidget;
import gregtech.api.gui.widgets.ImageWidget;
import gregtech.api.gui.widgets.TextFieldWidget2;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.common.metatileentities.multi.multiblockpart.appeng.MetaTileEntityMEStockingBus;

import gregtech.common.metatileentities.multi.multiblockpart.appeng.stack.WrappedItemStack;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityMEOreDictBus extends MetaTileEntityMEStockingBus {

    String oreDictName= "在这里输入矿辞前缀";

    public MetaTileEntityMEOreDictBus(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new MetaTileEntityMEOreDictBus(metaTileEntityId);
    }

    @Override
    protected ModularUI.Builder createUITemplate(EntityPlayer player) {
        ModularUI.Builder builder = super.createUITemplate(player);
        builder.widget(new ImageCycleButtonWidget(7 + 18 * 4 + 1, 26, 16, 16, GuiTextures.BUTTON_AUTO_PULL,
                () -> autoPull, this::setAutoPull).setTooltipHoverString("gregtech.gui.me_bus.auto_pull_button"));

        builder.widget(new ImageWidget(59, 14, 110, 12, GuiTextures.DISPLAY));
        builder.widget(new TextFieldWidget2(60, 15, 108,10,
                this::getOreDict, this::setOreDict)
                .setMaxLength(10));
        return builder;
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
        if(oreDictName.isEmpty()||oreDictName.equals("在这里输入矿辞前缀")){
            super.refreshList();
            return;
        }
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

            IAEItemStack selectedStack = WrappedItemStack.fromItemStack(itemStack);
            if (selectedStack == null) continue;
            IAEItemStack configStack = selectedStack.copy().setStackSize(1);
            var slot = this.getAEItemHandler().getInventory()[index];
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
