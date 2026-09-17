package gregtech.common.command.material;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.common.command.HandStacks;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

/**
 * 输出手持物品所属材料的基本信息:名称、注册模组、颜色、图标集、化学式。
 */
public class CommandMaterialInfo extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "info";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.material.info.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(@NotNull MinecraftServer server, @NotNull ICommandSender sender,
                        @NotNull String[] args) throws CommandException {
        MaterialStack materialStack = OreDictUnifier.getMaterial(HandStacks.getHeldItem(sender));
        if (materialStack == null) {
            throw new CommandException("gregtech.command.material.error.no_material");
        }
        Material material = materialStack.material;

        String name = TextFormatting.GREEN + material.getLocalizedName() + TextFormatting.WHITE;
        sender.sendMessage(new TextComponentTranslation("gregtech.command.material.info.name", name,
                TextFormatting.YELLOW + material.getModid()));

        String color = TextFormatting.BLUE + String.format("%06X", material.getMaterialRGB() & 0xFFFFFF);
        MaterialIconSet iconSet = material.getMaterialIconSet();
        String iconSetName = iconSet == null ? "NONE" : iconSet.name;
        sender.sendMessage(new TextComponentTranslation("gregtech.command.material.info.color", color,
                TextFormatting.GRAY + iconSetName));

        String formula = material.getChemicalFormula();
        if (formula != null && !formula.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("gregtech.command.material.info.formula",
                    TextFormatting.AQUA + formula));
        }
    }
}
