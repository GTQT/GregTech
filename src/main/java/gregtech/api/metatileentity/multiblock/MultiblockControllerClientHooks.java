package gregtech.api.metatileentity.multiblock;

import gregtech.api.pattern.BlockPatternTemplate;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.client.renderer.handler.MultiblockPreviewRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class MultiblockControllerClientHooks {

    private MultiblockControllerClientHooks() {}

    @SideOnly(Side.CLIENT)
    static void addInformation(@NotNull MultiblockControllerBase controller,
                               @NotNull List<String> tooltip) {
        TooltipBuilder.createDefault().build(controller, tooltip);
        TooltipBuilder.create().addPollution(controller.getPollutionAmount(), controller.getPollutionTicks())
                .build(controller, tooltip);
    }

    @SideOnly(Side.CLIENT)
    static void addStructureInformation(@NotNull MultiblockControllerBase controller,
                                        @Nullable BlockPatternTemplate patternTemplate,
                                        @NotNull List<String> tooltip) {
        TooltipBuilder.create().addStructure().build(controller, tooltip);

        if (patternTemplate != null) {
            List<String> structureDesc = patternTemplate.getStructureDescription();
            if (!structureDesc.isEmpty()) {
                tooltip.add("");
                for (String line : structureDesc) {
                    tooltip.add(formatStructureDescriptionLine(line));
                }
            }
        }
    }

    static void addToolUsages(@NotNull MultiblockControllerBase controller,
                              @NotNull List<String> tooltip) {
        if (controller instanceof gregtech.api.capability.IMultipleRecipeMaps) {
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.toggle_mode_covers"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        }
        if (controller.allowsExtendedFacing()) {
            tooltip.add(I18n.format("gregtech.tool_action.wrench.extended_facing"));
        } else {
            tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        }
    }

    static boolean onRightClickPreview(@NotNull MultiblockControllerBase controller,
                                       @NotNull EntityPlayer player,
                                       @NotNull EnumHand hand) {
        if (controller.getWorld().isRemote && !controller.isStructureFormed() && player.isSneaking() &&
                player.getHeldItem(hand).isEmpty()) {
            MultiblockPreviewRenderer.renderMultiBlockPreview(controller, 60000);
            return true;
        }
        return false;
    }

    static void refreshPreviewOnClient(@NotNull MultiblockControllerBase controller) {
        if (controller.getWorld() != null && controller.getWorld().isRemote) {
            MultiblockPreviewRenderer.refreshCurrentPreview(controller);
        }
    }

    @SideOnly(Side.CLIENT)
    static String[] getDescription(@NotNull MultiblockControllerBase controller) {
        String key = String.format("%s.multiblock.%s.description",
                controller.metaTileEntityId.getNamespace(), controller.metaTileEntityId.getPath());
        return I18n.hasKey(key) ? new String[] { I18n.format(key) } : new String[0];
    }

    @SideOnly(Side.CLIENT)
    private static String formatStructureDescriptionLine(@NotNull String rawLine) {
        String[] parts = rawLine.split(":", 4);
        if (parts.length < 2) return rawLine;

        switch (parts[0]) {
            case "casing": {
                String name = I18n.format(parts[1]);
                int min = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int max = parts.length > 3 ? Integer.parseInt(parts[3]) : min;
                if (min == max) {
                    return String.format("  %dx %s", max, name);
                } else {
                    return String.format("  %dx %s (%s %d)", max, name,
                            I18n.format("gregtech.multiblock.tooltip.at_least"), min);
                }
            }
            case "hatch": {
                String name = I18n.format("gregtech.multiblock.ability." + parts[1]);
                int min = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int max = parts.length > 3 ? Integer.parseInt(parts[3]) : 1;
                if (min == 0) {
                    return String.format("  0-%dx %s (%s)", max, name,
                            I18n.format("gregtech.multiblock.tooltip.optional"));
                } else if (min == max) {
                    return String.format("  %dx %s", max, name);
                } else {
                    return String.format("  %d-%dx %s", min, max, name);
                }
            }
            case "hatch_group": {
                String name = I18n.format("gregtech.multiblock.ability." + parts[1]);
                int min = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int max = parts.length > 3 ? Integer.parseInt(parts[3]) : min;
                if (max < 0) {
                    return String.format("  %s %dx %s",
                            I18n.format("gregtech.multiblock.tooltip.at_least"), min, name);
                } else if (min == max) {
                    return String.format("  %dx %s", max, name);
                } else {
                    return String.format("  %d-%dx %s", min, max, name);
                }
            }
            case "tiered": {
                String name = I18n.format(parts[1]);
                boolean uniform = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                if (uniform) {
                    return String.format("  %s (%s)", name,
                            I18n.format("gregtech.multiblock.tooltip.same_tier"));
                } else {
                    return "  " + name;
                }
            }
            case "channel": {
                return String.format("  %s", I18n.format("gregtech.multiblock.tooltip.sub_channel",
                        I18n.format(parts[1])));
            }
            default:
                return rawLine;
        }
    }
}
