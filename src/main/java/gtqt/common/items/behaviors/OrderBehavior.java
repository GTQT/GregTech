package gtqt.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.factory.MetaItemGuiFactory;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OrderBehavior implements IItemBehaviour, ItemUIFactory {

    private static final String KEY_ORDER_NAME = "order_name";
    private static final String DEFAULT_NAME = "订单";
    private static final String KEY_DISPLAY = "display";
    private static final String KEY_NAME = "Name";

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack heldItem = player.getHeldItem(hand);
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, heldItem);
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack stack = guiData.getUsedItemStack();

        StringSyncValue nameValue = new StringSyncValue(
                () -> getShowName(stack),
                str -> {
                    if (str != null && !str.isEmpty()) {
                        setShowName(str, stack);
                    } else {
                        setShowName(DEFAULT_NAME, stack);
                    }
                }
        );

        var panel = GTGuis.createPanel(guiData.getUsedItemStack(), 80, 60);

        return panel.child(IKey.str("设置订单名称").asWidget().pos(5, 5))
                .child(new TextFieldWidget()
                        .widthRel(0.8f)
                        .height(20)
                        .pos(5, 20)
                        .setTextColor(Color.WHITE.darker(1))
                        .setValidator(str -> {
                            if (str == null || str.isEmpty())
                                return DEFAULT_NAME;
                            return str;
                        })
                        .value(nameValue)
                        .background(GTGuiTextures.DISPLAY));
    }

    /**
     * 从 NBT 中读取显示名称
     */
    private @NotNull String getShowName(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            return DEFAULT_NAME;
        }
        NBTTagCompound compound = stack.getTagCompound();
        String name = compound.getString(KEY_ORDER_NAME);
        return name.isEmpty() ? DEFAULT_NAME : name;
    }

    /**
     * 将显示名称写入 NBT
     */
    private void setShowName(String name, ItemStack stack) {
        NBTTagCompound compound = stack.getTagCompound();
        if (compound == null) {
            compound = new NBTTagCompound();
        }
        compound.setString(KEY_ORDER_NAME, name);

        NBTTagCompound display = compound.getCompoundTag(KEY_DISPLAY);
        display.setString(KEY_NAME, name+"订单");
        compound.setTag(KEY_DISPLAY, display);
        stack.setTagCompound(compound);
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        String name = getShowName(stack);
        if (!DEFAULT_NAME.equals(name)) {
            lines.add(TextFormatting.AQUA + "订单名称：" + TextFormatting.RESET + name);
        } else {
            lines.add(TextFormatting.GRAY + "订单名称：" + TextFormatting.RESET + DEFAULT_NAME);
        }

        lines.add(TextFormatting.DARK_GRAY + "右键打开界面修改名称");
        lines.add(TextFormatting.DARK_GRAY + "可以作为AE自动合成的大型机器产物");
        lines.add(TextFormatting.DARK_GRAY + "当此合成完成时，会自动取消，无需手动取消");
    }
}
