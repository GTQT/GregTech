package gregtech.integration.chisel.tools;

import gregtech.api.GTValues;
import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.ItemGTTool;

import static gregtech.common.items.ToolItems.register;

public final class ChiselToolItems {

    public static IGTTool CHISEL;

    public static void init() {
        CHISEL = register(ItemGTTool.Builder.of(GTValues.MODID, "chisel")
                .toolStats(b -> b.crafting().damagePerCraftingAction(2)
                        .cannotAttack().attackSpeed(-1.0F))
                .oreDict("toolChisel")
                .secondaryOreDicts("craftChisel")
                .toolClasses("chisel")
                .build());
    }
}
