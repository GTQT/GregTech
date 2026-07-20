package gregtech.integration.bbw.tools;

import gregtech.api.GTValues;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTTool;

import static gregtech.common.items.ToolItems.register;

public class BBWToolItems {

    public static IGTTool WAND;
    public static void init() {
        WAND = register(ItemGTTool.Builder.of(GTValues.MODID, "wand")
                .toolStats(b -> b.behaviors(WandBehavior.INSTANCE)
                        .cannotAttack().attackSpeed(-1.0F))
                .oreDict("toolWand")
                .toolClasses("wand")
                .build());
    }
}
