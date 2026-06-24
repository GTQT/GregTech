package gregtech.common.items.behaviors.spray;

import gregtech.api.color.ColorMode;
import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemColorProvider;
import gregtech.api.items.metaitem.stats.IItemNameProvider;
import gregtech.api.items.metaitem.stats.IMouseEventHandler;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.drawable.DynamicColorRectangle;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.items.MetaItems;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.BoolValue;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.value.IntValue;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.PageButton;
import com.cleanroommc.modularui.widgets.PagedWidget;
import com.cleanroommc.modularui.widgets.SliderWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import com.cleanroommc.modularui.widgets.ToggleButton;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

import static gregtech.api.util.ColorUtil.ARGBHelper;

public class CreativeSprayBehavior extends AbstractSprayBehavior implements ItemUIFactory, IItemColorProvider,
                                                                            IItemNameProvider, IMouseEventHandler {

    private static final String KEY_COLOR = "color";
    private static final String KEY_USES_RGB = "usesRGB";
    private static final String KEY_RGB_COLOR = "rgbColor";

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        ItemStack usedStack = guiData.getUsedItemStack();
        IntSyncValue colorSync = SyncHandlers.intNumber(() -> getColorOrdinal(usedStack),
                newColor -> setColorOrdinal(usedStack, newColor));
        guiSyncManager.syncValue(KEY_COLOR, 0, colorSync);
        BooleanSyncValue usesRGBSync = SyncHandlers.bool(() -> usesRGB(usedStack), bool -> useRGB(usedStack, bool));
        guiSyncManager.syncValue(KEY_USES_RGB, 0, usesRGBSync);
        // Doesn't use getColorInt because the slider widgets take a moment to update so it ended up showing the normal
        // can colors when you switched to RGB mode.
        IntSyncValue rgbColorSync = SyncHandlers.intNumber(
                () -> GTUtility.getOrCreateNbtCompound(usedStack).getInteger(KEY_RGB_COLOR),
                newColor -> setColor(usedStack, newColor));
        guiSyncManager.syncValue(KEY_RGB_COLOR, 0, rgbColorSync);

        PagedWidget.Controller pageController = new PagedWidget.Controller();

        return GTGuis.createPanel(usedStack, 176, 95)
                .child(Flow.row()
                        .widthRel(1.0f)
                        .leftRel(0.5f)
                        .margin(3, 0)
                        .coverChildrenHeight()
                        .topRel(0.0f, 3, 1.0f)
                        .child(new PageButton(0, pageController)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .overlay(new ItemDrawable(MetaItems.SPRAY_EMPTY.getStackForm())
                                        .asIcon()
                                        .size(16))
                                .addTooltipLine(IKey.lang("metaitem.spray.creative.mode.normal")))
                        .child(new PageButton(1, pageController)
                                .tab(GuiTextures.TAB_TOP, 0)
                                .overlay(GTGuiTextures.RGB_GRADIENT.asIcon()
                                        .size(16))
                                .addTooltipLine(IKey.lang("metaitem.spray.creative.mode.rgb"))))
                .child(IKey.lang("metaitem.spray.creative.name_base")
                        .asWidget()
                        .left(7)
                        .top(7))
                .child(new PagedWidget<>()
                        .margin(7, 7, 22, 7)
                        .widthRel(1.0f)
                        .heightRel(1.0f)
                        .controller(pageController)
                        .onPageChange(usesRGBSync::setIntValue)
                        .initialPage(usesRGBSync.getIntValue())
                        .addPage(SlotGroupWidget.builder()
                                .matrix("SCCCCCCCC",
                                        "CCCCCCCC")
                                .key('S', new ToggleButton()
                                        .size(18)
                                        .value(new BoolValue.Dynamic(
                                                () -> colorSync.getIntValue() == -1 && !usesRGBSync.getBoolValue(),
                                                $ -> {
                                                    if (!usesRGBSync.getBoolValue()) colorSync.setIntValue(-1);
                                                }))
                                        .overlay(new ItemDrawable(MetaItems.SPRAY_SOLVENT.getStackForm())
                                                .asIcon()
                                                .size(16))
                                        .addTooltipLine(IKey.lang("metaitem.spray.creative.solvent")))
                                .key('C', index -> {
                                    EnumDyeColor color = EnumDyeColor.values()[index];
                                    return new ToggleButton()
                                            .size(18)
                                            .value(new BoolValue.Dynamic(
                                                    () -> colorSync.getIntValue() == index &&
                                                            !usesRGBSync.getBoolValue(),
                                                    $ -> {
                                                        if (!usesRGBSync.getBoolValue()) colorSync.setIntValue(index);
                                                    }))
                                            .overlay(
                                                    new ItemDrawable(MetaItems.SPRAY_CAN_DYES.get(color).getStackForm())
                                                            .asIcon()
                                                            .size(16))
                                            .addTooltipLine(IKey.lang("metaitem.spray.creative." + color));
                                })
                                .build()
                                .topRel(0.5f).anchorTop(0.5f))
                        .addPage(Flow.column()
                                .widthRel(1.0f)
                                .heightRel(1.0f)
                                .child(createColorRow(ARGBHelper.RED, rgbColorSync, usesRGBSync::getBoolValue))
                                .child(createColorRow(ARGBHelper.GREEN, rgbColorSync, usesRGBSync::getBoolValue))
                                .child(createColorRow(ARGBHelper.BLUE, rgbColorSync, usesRGBSync::getBoolValue))));
    }

    protected static Flow createColorRow(@NotNull ARGBHelper helper, @NotNull IntSyncValue rgbColorSync,
                                         @NotNull BooleanSupplier allowSetting) {
        return Flow.row()
                .widthRel(1.0f)
                .coverChildrenHeight()
                .child(new TextFieldWidget()
                        .width(30)
                        .setNumbers(0, 255)
                        .value(new IntValue.Dynamic(() -> helper.isolateAndShift(rgbColorSync.getIntValue()),
                                colorDigit -> {
                                    if (allowSetting.getAsBoolean()) {
                                        int newColor = helper.replace(rgbColorSync.getIntValue(), colorDigit);
                                        rgbColorSync.setIntValue(newColor);
                                    }
                                })))
                .child(new SliderWidget()
                        .width(132)
                        .bounds(0.0D, 255.0d)
                        .value(new DoubleValue.Dynamic(
                                () -> (double) helper.isolateAndShift(rgbColorSync.getIntValue()),
                                colorDigit -> {
                                    if (allowSetting.getAsBoolean()) {
                                        int newColor = helper.replace(rgbColorSync.getIntValue(), (int) colorDigit);
                                        rgbColorSync.setIntValue(newColor);
                                    }
                                }))
                        .background(
                                new DynamicColorRectangle(() -> helper.isolateWithFullAlpha(rgbColorSync.getIntValue()))
                                        .asIcon()
                                        .margin(4, 0)
                                        .height(8))
                        .addTooltipLine(IKey.lang("metaitem.spray.creative.tip." + helper.toString().toLowerCase())));
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            MetaItemGuiFactory.open(player, hand);
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public @Nullable EnumDyeColor getColor(@NotNull ItemStack sprayCan) {
        NBTTagCompound tag = GTUtility.getOrCreateNbtCompound(sprayCan);
        if (tag.hasKey(KEY_COLOR, Constants.NBT.TAG_INT)) {
            int color = tag.getInteger(KEY_COLOR);
            if (color < 0 || color > 15) return null;
            return EnumDyeColor.values()[color];
        }

        return null;
    }

    @Override
    public int getColorInt(@NotNull ItemStack sprayCan) {
        NBTTagCompound tag = GTUtility.getOrCreateNbtCompound(sprayCan);
        return tag.getBoolean(KEY_USES_RGB) ? tag.getInteger(KEY_RGB_COLOR) : super.getColorInt(sprayCan);
    }

    @Override
    public @NotNull ColorMode getColorMode(@NotNull ItemStack sprayCan) {
        return usesRGB(sprayCan) ? ColorMode.ARGB : ColorMode.DYE;
    }

    public void setColor(@NotNull ItemStack sprayCan, @Nullable EnumDyeColor color) {
        GTUtility.getOrCreateNbtCompound(sprayCan).setInteger(KEY_COLOR, color == null ? -1 : color.ordinal());
    }

    public void setColorOrdinal(@NotNull ItemStack sprayCan, int ordinal) {
        GTUtility.getOrCreateNbtCompound(sprayCan).setInteger(KEY_COLOR,
                ordinal >= 0 && ordinal <= 15 ? ordinal : -1);
    }

    public void setColor(@NotNull ItemStack sprayCan, int argbColor) {
        GTUtility.getOrCreateNbtCompound(sprayCan).setInteger(KEY_RGB_COLOR, argbColor);
    }

    public boolean usesRGB(@NotNull ItemStack sprayCan) {
        return GTUtility.getOrCreateNbtCompound(sprayCan).getBoolean(KEY_USES_RGB);
    }

    public void useRGB(@NotNull ItemStack sprayCan, boolean bool) {
        GTUtility.getOrCreateNbtCompound(sprayCan).setBoolean(KEY_USES_RGB, bool);
    }

    @Override
    public int getItemStackColor(ItemStack sprayCan, int tintIndex) {
        return tintIndex == 1 ? getColorInt(sprayCan) : 0xFFFFFF;
    }

    @Override
    public String getItemStackDisplayName(ItemStack sprayCan, String unlocalizedName) {
        String colorString;
        if (usesRGB(sprayCan)) {
            colorString = String.format("0x%06X", getColorInt(sprayCan) & 0xFFFFFF);
        } else {
            EnumDyeColor color = getColor(sprayCan);
            colorString = color == null ? I18n.format("metaitem.spray.creative.solvent") :
                    I18n.format("metaitem.spray.creative." + color);
        }

        return I18n.format(unlocalizedName, colorString);
    }

    @Override
    public void handleMouseEventServer(@NotNull PacketBuffer buf, @NotNull EntityPlayerMP playerServer,
                                       @NotNull EnumHand hand, @NotNull ItemStack sprayCan) {
        switch (buf.readByte()) {
            case 0 -> MetaItemGuiFactory.open(playerServer, EnumHand.MAIN_HAND);
            case 1 -> setColor(sprayCan, buf.readInt());
            case 2 -> setColor(sprayCan, EnumDyeColor.values()[buf.readByte()]);
        }
    }
}
