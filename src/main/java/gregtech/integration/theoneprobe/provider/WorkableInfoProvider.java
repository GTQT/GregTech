package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.logic.RecipeSlot;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.NumberFormat;
import mcjty.theoneprobe.api.TextStyleClass;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WorkableInfoProvider extends CapabilityInfoProvider<IWorkable> {

    private static final int MAX_SLOT_DISPLAY = 2;

    @Override
    public String getID() {
        return GTValues.MODID + ":workable_provider";
    }

    @NotNull
    @Override
    protected Capability<IWorkable> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_WORKABLE;
    }

    @Override
    protected void addProbeInfo(@NotNull IWorkable capability, @NotNull IProbeInfo probeInfo,
                                @NotNull EntityPlayer player, @NotNull TileEntity tileEntity,
                                @NotNull IProbeHitData data) {
        if (!capability.isActive()) return;

        int currentProgress = capability.getProgress();
        int maxProgress = capability.getMaxProgress();

        if (capability instanceof ComputationRecipeLogic logic && !logic.shouldShowDuration()) {
            // Show as total computation instead
            int color = capability.isWorkingEnabled() ? 0xFF00D4CE : 0xFFBB1C28;
            probeInfo.progress(currentProgress, maxProgress, probeInfo.defaultProgressStyle()
                    .suffix(" / " + maxProgress + " CWU")
                    .filledColor(color)
                    .alternateFilledColor(color)
                    .borderColor(0xFF555555));
            return;
        }

        // Standard progress bar (in cross-recipe mode, shows display slot progress)
        String text;
        if (maxProgress < 20) {
            text = " / " + maxProgress + " t";
        } else {
            currentProgress = Math.round(currentProgress / 20.0F);
            maxProgress = Math.round(maxProgress / 20.0F);
            text = " / " + TextFormattingUtil.formatNumbers(maxProgress) + " s";
        }

        if (maxProgress > 0) {
            int color = capability.isWorkingEnabled() ? 0xFF4CBB17 : 0xFFBB1C28;
            probeInfo.progress(currentProgress, maxProgress, probeInfo.defaultProgressStyle()
                    .suffix(text)
                    .filledColor(color)
                    .alternateFilledColor(color)
                    .borderColor(0xFF555555).numberFormat(NumberFormat.COMMAS));
        }

        // Cross-recipe parallel: show active slot details (up to 3 lines + ellipsis)
        if (capability instanceof MultiblockRecipeLogic logic &&
                logic.isCrossRecipeMode() && logic.getCrossRecipeScheduler() != null) {
            addCrossRecipeSlotInfo(probeInfo, logic.getCrossRecipeScheduler());
        }
    }

    /**
     * Appends cross-recipe parallel slot details to the TOP display.
     * Shows up to {@link #MAX_SLOT_DISPLAY} active slots, with "..." if more exist.
     */
    private static void addCrossRecipeSlotInfo(@NotNull IProbeInfo probeInfo,
                                               @NotNull CrossRecipeParallelScheduler scheduler) {
        List<RecipeSlot> slots = scheduler.getActiveSlots();
        if (slots.isEmpty()) return;

        int displayed = 0;
        for (RecipeSlot slot : slots) {
            if (!slot.isRunning()) continue;
            if (displayed >= MAX_SLOT_DISPLAY) {
                probeInfo.text(TextStyleClass.INFO + TextFormatting.GRAY.toString() + "...");
                break;
            }
            String slotText = formatSlotLine(slot);
            probeInfo.text(TextStyleClass.INFO + slotText);
            displayed++;
        }
    }

    /**
     * Formats a single RecipeSlot into a compact display line.
     * Format: "  #1: RecipeName x2 - 50%" or "  #1: 2.5s/8.0s (31%)"
     */
    @NotNull
    private static String formatSlotLine(@NotNull RecipeSlot slot) {
        int progress = slot.getProgressTime();
        int maxProgress = slot.getMaxProgressTime();
        float percent = maxProgress > 0 ? (float) progress / maxProgress * 100f : 0f;

        String name = slot.getRecipeDisplayName();
        int parallel = slot.getParallelCount();

        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.GRAY).append("  #").append(slot.getSlotIndex() + 1).append(": ");

        if (!name.isEmpty()) {
            sb.append(TextFormatting.YELLOW).append(name);
            if (parallel > 1) {
                sb.append(TextFormatting.AQUA).append(" x").append(parallel);
            }
            sb.append(TextFormatting.GRAY).append(" - ");
        }

        sb.append(TextFormatting.WHITE);
        if (maxProgress < 20) {
            sb.append(progress).append("/").append(maxProgress).append("t");
        } else {
            sb.append(String.format("%.1fs/%.1fs", progress / 20f, maxProgress / 20f));
        }
        sb.append(TextFormatting.GRAY).append(String.format(" (%.0f%%)", percent));

        return sb.toString();
    }
}
