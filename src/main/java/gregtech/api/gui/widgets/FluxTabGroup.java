package gregtech.api.gui.widgets;

import gregtech.api.gui.widgets.tab.TabListRenderer;

import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Flux-style tab navigation, including returning home with Escape. */
public class FluxTabGroup<T extends AbstractWidgetGroup> extends TabGroup<T> {

    public FluxTabGroup(int x, int y, TabListRenderer tabListRenderer) {
        super(x, y, tabListRenderer);
    }

    @SideOnly(Side.CLIENT)
    public void selectTabFromClient(int tabIndex) {
        if (selectedTabIndex == tabIndex) return;
        setSelectedTab(tabIndex);
        writeClientAction(2, buffer -> buffer.writeVarInt(tabIndex));
    }

    public void selectTabFromServer(int tabIndex) {
        if (selectedTabIndex != tabIndex) setSelectedTab(tabIndex);
        writeUpdateInfo(3, buffer -> buffer.writeVarInt(tabIndex));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readUpdateInfo(int id, PacketBuffer buffer) {
        if (id == 3) {
            int tabIndex = buffer.readVarInt();
            if (selectedTabIndex != tabIndex) setSelectedTab(tabIndex);
            return;
        }
        super.readUpdateInfo(id, buffer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean keyTyped(char charTyped, int keyCode) {
        if (super.keyTyped(charTyped, keyCode)) return true;
        if (keyCode == 1 && selectedTabIndex != 0) {
            selectTabFromClient(0);
            playButtonClickSound();
            return true;
        }
        return false;
    }
}
