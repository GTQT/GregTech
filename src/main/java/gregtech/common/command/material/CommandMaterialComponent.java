package gregtech.common.command.material;

import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.common.command.HandStacks;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

/**
 * 输出手持物品所属材料的化学式与组成成分。
 * 未定义成分的材料直接报错,不打印化学式——与 Lite-Core 的行为一致。
 */
public class CommandMaterialComponent extends CommandBase {

    @NotNull
    @Override
    public String getName() {
        return "component";
    }

    @NotNull
    @Override
    public String getUsage(@NotNull ICommandSender sender) {
        return "gregtech.command.material.component.usage";
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
        if (material.getMaterialComponents().isEmpty()) {
            throw new CommandException("gregtech.command.material.error.no_component");
        }

        String formula = material.getChemicalFormula();
        if (formula != null && !formula.isEmpty()) {
            sender.sendMessage(new TextComponentTranslation("gregtech.command.material.component.formula",
                    TextFormatting.YELLOW + formula));
        }

        String components = material.getMaterialComponents().stream()
                .map(CommandMaterialComponent::formatComponent)
                .collect(Collectors.joining(", "));
        sender.sendMessage(new TextComponentTranslation("gregtech.command.material.component.components",
                components));
    }

    private static String formatComponent(MaterialStack component) {
        return (component.amount > 1
                ? TextFormatting.GREEN.toString() + component.amount + "x " + TextFormatting.GOLD
                : TextFormatting.GOLD.toString()) + component.material.getLocalizedName();
    }
}
