package gregtech.api.gui.widgets;

import gregtech.api.gui.ModularUI;
import gregtech.api.gui.resources.FluxWirelessTextures;
import gregtech.api.gui.widgets.tab.ITabInfo;
import gregtech.api.gui.widgets.tab.TabListRenderer;
import gregtech.api.util.Position;

import java.util.List;

/** Exact top-tab positions used by Flux's GuiTabCore. */
public class FluxTabListRenderer extends TabListRenderer {

    private final int[] icons;

    public FluxTabListRenderer(int... icons) {
        this.icons = icons;
    }

    @Override
    public void renderTabs(ModularUI gui, Position offset, List<ITabInfo> tabs, int guiWidth, int guiHeight,
                           int selectedTabIndex) {
        for (int index = 0; index < tabs.size(); index++) {
            int icon = index < icons.length ? icons[index] : index;
            int[] position = getTabPos(index, guiWidth, guiHeight);
            tabs.get(index).renderTab(FluxWirelessTextures.button(icon * 16, index == selectedTabIndex ? 16 : 0, 16,
                    16), offset.x + position[0], offset.y + position[1], 16, 16, index == selectedTabIndex);
        }
    }

    @Override
    public int[] getTabPos(int tabIndex, int guiWidth, int guiHeight) {
        int icon = tabIndex < icons.length ? icons[tabIndex] : tabIndex;
        if (icon == 7) return new int[] { 148, -16, 16, 16 };

        int position = 0;
        for (int index = 0; index < tabIndex; index++) {
            int priorIcon = index < icons.length ? icons[index] : index;
            if (priorIcon != 7) position++;
        }
        return new int[] { 12 + position * 18, -16, 16, 16 };
    }
}
