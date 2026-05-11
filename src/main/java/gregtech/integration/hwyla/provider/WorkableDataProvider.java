package gregtech.integration.hwyla.provider;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IWorkable;
import gregtech.api.capability.impl.ComputationRecipeLogic;
import gregtech.api.capability.impl.MultiblockRecipeLogic;
import gregtech.api.recipes.logic.CrossRecipeParallelScheduler;
import gregtech.api.recipes.logic.RecipeSlot;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WorkableDataProvider extends CapabilityDataProvider<IWorkable> {

    public static final WorkableDataProvider INSTANCE = new WorkableDataProvider();

    private static final int MAX_SLOT_DISPLAY = 2;

    @Override
    public void register(@NotNull IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, TileEntity.class);
        registrar.registerNBTProvider(this, TileEntity.class);
        registrar.addConfig(GTValues.MOD_NAME, "gregtech.workable");
    }

    @Override
    protected @NotNull Capability<IWorkable> getCapability() {
        return GregtechTileCapabilities.CAPABILITY_WORKABLE;
    }

    @Override
    protected NBTTagCompound getNBTData(IWorkable capability, NBTTagCompound tag) {
        NBTTagCompound subTag = new NBTTagCompound();
        subTag.setBoolean("Active", capability.isActive());
        if (capability.isActive()) {
            subTag.setBoolean("ShowAsComputation",
                    capability instanceof ComputationRecipeLogic logic && !logic.shouldShowDuration());
            subTag.setInteger("Progress", capability.getProgress());
            subTag.setInteger("MaxProgress", capability.getMaxProgress());

            // Cross-recipe parallel slot info
            if (capability instanceof MultiblockRecipeLogic logic &&
                    logic.isCrossRecipeMode() && logic.getCrossRecipeScheduler() != null) {
                writeCrossRecipeSlotNBT(subTag, logic.getCrossRecipeScheduler());
            }
        }
        tag.setTag("gregtech.IWorkable", subTag);
        return tag;
    }

    /**
     * Serializes active cross-recipe slot info into NBT for client-side display.
     */
    private static void writeCrossRecipeSlotNBT(NBTTagCompound tag, CrossRecipeParallelScheduler scheduler) {
        List<RecipeSlot> slots = scheduler.getActiveSlots();
        if (slots.isEmpty()) return;

        NBTTagList slotList = new NBTTagList();
        int count = 0;
        for (RecipeSlot slot : slots) {
            if (!slot.isRunning()) continue;
            if (count >= MAX_SLOT_DISPLAY) {
                tag.setBoolean("CrossRecipeHasMore", true);
                break;
            }
            NBTTagCompound slotTag = new NBTTagCompound();
            slotTag.setInteger("Index", slot.getSlotIndex());
            slotTag.setString("Name", slot.getRecipeDisplayName());
            slotTag.setInteger("Parallel", slot.getParallelCount());
            slotTag.setInteger("Progress", slot.getProgressTime());
            slotTag.setInteger("MaxProgress", slot.getMaxProgressTime());
            slotList.appendTag(slotTag);
            count++;
        }
        if (slotList.tagCount() > 0) {
            tag.setTag("CrossRecipeSlots", slotList);
        }
    }

    @NotNull
    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> tooltip, IWailaDataAccessor accessor,
                                     IWailaConfigHandler config) {
        if (!config.getConfig("gregtech.workable") || accessor.getTileEntity() == null) {
            return tooltip;
        }

        if (accessor.getNBTData().hasKey("gregtech.IWorkable")) {
            NBTTagCompound tag = accessor.getNBTData().getCompoundTag("gregtech.IWorkable");
            boolean active = tag.getBoolean("Active");
            if (active) {
                int progress = tag.getInteger("Progress");
                int maxProgress = tag.getInteger("MaxProgress");

                if (tag.getBoolean("ShowAsComputation")) {
                    tooltip.add(I18n.format("gregtech.waila.progress_computation", progress, maxProgress));
                }

                if (maxProgress == 0) {
                    tooltip.add(I18n.format("gregtech.waila.progress_idle"));
                } else if (maxProgress < 20) {
                    tooltip.add(I18n.format("gregtech.waila.progress_tick", progress, maxProgress));
                } else {
                    progress = Math.round(progress / 20.0F);
                    maxProgress = Math.round(maxProgress / 20.0F);
                    tooltip.add(I18n.format("gregtech.waila.progress_sec", progress, maxProgress));
                }

                // Cross-recipe parallel slot details
                if (tag.hasKey("CrossRecipeSlots")) {
                    addCrossRecipeSlotTooltip(tooltip, tag);
                }
            }
        }

        return super.getWailaBody(itemStack, tooltip, accessor, config);
    }

    /**
     * Reads cross-recipe slot info from NBT and appends formatted lines to the tooltip.
     */
    private static void addCrossRecipeSlotTooltip(List<String> tooltip, NBTTagCompound tag) {
        NBTTagList slotList = tag.getTagList("CrossRecipeSlots", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < slotList.tagCount(); i++) {
            NBTTagCompound slotTag = slotList.getCompoundTagAt(i);
            tooltip.add(formatSlotLine(slotTag));
        }
        if (tag.getBoolean("CrossRecipeHasMore")) {
            tooltip.add(TextFormatting.GRAY + "  ...");
        }
    }

    private static String formatSlotLine(NBTTagCompound slotTag) {
        int index = slotTag.getInteger("Index");
        String name = slotTag.getString("Name");
        int parallel = slotTag.getInteger("Parallel");
        int progress = slotTag.getInteger("Progress");
        int maxProgress = slotTag.getInteger("MaxProgress");
        float percent = maxProgress > 0 ? (float) progress / maxProgress * 100f : 0f;

        StringBuilder sb = new StringBuilder();
        sb.append(TextFormatting.GRAY).append("  #").append(index + 1).append(": ");

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
