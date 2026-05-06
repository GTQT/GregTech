package gregtech.common.mui.multiblock.godforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import gregtech.api.GTValues;
import gregtech.api.mui.GTGuiTextures;

public class ForgeOfGodsGuiUtil {

    private static final ResourceLocation PRESS_SOUND = new ResourceLocation(GTValues.MODID, "fx_click");

    public static Runnable getButtonSound() {
        return ForgeOfGodsGuiUtil::playButtonSound;
    }

    private static void playButtonSound() {
        playButtonSoundClient();
    }

    @SideOnly(Side.CLIENT)
    private static void playButtonSoundClient() {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static ButtonWidget<?> panelCloseButton() {
        return ButtonWidget.panelCloseButton()
            .background(GTGuiTextures.CLOSE_BUTTON_HOLLOW)
            .overlay(IDrawable.EMPTY)
            .disableHoverBackground()
            .disableHoverOverlay()
            .clickSound(getButtonSound());
    }

    public static ButtonWidget<?> panelCloseButtonStandard() {
        return ButtonWidget.panelCloseButton()
            .clickSound(getButtonSound());
    }
}
