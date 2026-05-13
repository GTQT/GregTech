package gregtech.integration.theoneprobe.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
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
     * Slots with the same recipe name and duration are merged into a single line.
     * Shows up to {@link #MAX_SLOT_DISPLAY} merged entries, with "..." if more exist.
     */
    private static void addCrossRecipeSlotInfo(@NotNull IProbeInfo probeInfo,
                                               @NotNull CrossRecipeParallelScheduler scheduler) {
        List<CrossRecipeParallelScheduler.MergedSlotDisplay> mergedSlots = scheduler.getMergedDisplaySlots();
        if (mergedSlots.isEmpty()) return;

        int displayed = 0;
        for (CrossRecipeParallelScheduler.MergedSlotDisplay merged : mergedSlots) {
            if (displayed >= MAX_SLOT_DISPLAY) {
                probeInfo.text(TextStyleClass.INFO + TextFormatting.GRAY.toString() + "...");
                break;
            }
            String slotText = formatMergedSlotLine(merged);
            probeInfo.text(TextStyleClass.INFO + slotText);
            displayed++;
        }
    }

    /**
     * Formats a merged slot display entry into a compact display line.
     * Format: "  #1: RecipeName x64(×4) - 3.5s/7.0s (50%)" when batched,
     *         "  #1: RecipeName x64 - 3.5s/7.0s (50%)" normally.
     */
    @NotNull
    private static String formatMergedSlotLine(
            @NotNull CrossRecipeParallelScheduler.MergedSlotDisplay merged) {
        float percent = merged.maxProgress > 0
                ? (float) merged.progress / merged.maxProgress * 100f : 0f;

        int displayCount = Math.max(merged.totalParallelCount, merged.totalOperations);
        boolean isBatched = merged.totalOperations > merged.totalParallelCount;

        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.GRAY).append("  #").append(merged.slotIndex + 1).append(": ");

        if (!merged.recipeName.isEmpty()) {
            sb.append(TextFormatting.YELLOW).append(merged.recipeName);
            if (displayCount > 1) {
                sb.append(TextFormatting.AQUA).append(" x").append(displayCount);
                if (isBatched) {
                    sb.append(TextFormatting.GOLD).append("(×").append(merged.totalOperations / Math.max(1, merged.totalParallelCount)).append(")");
                }
            }
            sb.append(TextFormatting.GRAY).append(" - ");
        }

        sb.append(TextFormatting.WHITE);
        if (merged.maxProgress < 20) {
            sb.append(merged.progress).append("/").append(merged.maxProgress).append("t");
        } else {
            sb.append(String.format("%.1fs/%.1fs", merged.progress / 20f, merged.maxProgress / 20f));
        }
        sb.append(TextFormatting.GRAY).append(String.format(" (%.0f%%)", percent));

        return sb.toString();
    }
}
