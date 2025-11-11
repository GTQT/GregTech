package gregtech.common.items.behaviors;

import com.cleanroommc.modularui.api.drawable.IDrawable;

import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.machines.IResearchRecipeMap;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.ItemStackHashStrategy;

import gregtech.api.util.KeyUtil;

import gregtech.common.items.MetaItems;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class DataItemBehavior implements IItemBehaviour, IDataItem {

    private final boolean requireDataBank;

    public DataItemBehavior() {
        this.requireDataBank = false;
    }

    public DataItemBehavior(boolean requireDataBank) {
        this.requireDataBank = requireDataBank;
    }

    @Override
    public boolean requireDataBank() {
        return requireDataBank;
    }

    @Override
    public void addInformation(@NotNull ItemStack itemStack, List<String> lines) {
        String researchId = AssemblyLineManager.readResearchId(itemStack);
        if (researchId == null) return;
        collectResearchItemsI18(researchId, lines);
    }

    public static void collectResearchItems(String id, List<IDrawable> lines) {
        Collection<Recipe> recipes = ((IResearchRecipeMap) RecipeMaps.ASSEMBLY_LINE_RECIPES)
                .getDataStickEntry(id);
        if (recipes != null && !recipes.isEmpty()) {
            lines.add(KeyUtil.lang("behavior.data_item.assemblyline.title"));
            Collection<ItemStack> added = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
            for (Recipe recipe : recipes) {
                ItemStack output = recipe.getOutputs().get(0);
                if (added.add(output)) {
                    lines.add(KeyUtil.lang("behavior.data_item.assemblyline.data", output.getDisplayName()));
                }
            }
        }
    }

    public static void collectResearchItemsI18(String id, List<String> lines) {
        Collection<Recipe> recipes = ((IResearchRecipeMap) RecipeMaps.ASSEMBLY_LINE_RECIPES)
                .getDataStickEntry(id);
        if (recipes != null && !recipes.isEmpty()) {
            lines.add(I18n.format("behavior.data_item.assemblyline.title"));
            Collection<ItemStack> added = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAllButCount());
            for (Recipe recipe : recipes) {
                ItemStack output = recipe.getOutputs().get(0);
                if (added.add(output)) {
                    lines.add(I18n.format("behavior.data_item.assemblyline.data", output.getDisplayName()));
                }
            }
        }
    }


    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX,
                                           float hitY, float hitZ, EnumHand hand) {
        ItemStack dataStick = player.getHeldItemMainhand();
        if (MetaItems.TOOL_DATA_STICK.isItemEqual(dataStick)) {
            updateLocationData(dataStick, pos);
            player.sendStatusMessage(new TextComponentTranslation("无线接入点坐标已写入"), true);
        }
        return EnumActionResult.PASS;
    }

    private void updateLocationData(ItemStack stack, BlockPos pos) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }

        NBTTagCompound tag = stack.getTagCompound();
        tag.setTag("CommonPos", writeLocationToTag(pos));
    }

    private NBTTagCompound writeLocationToTag(BlockPos pos) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("MainX", pos.getX());
        tag.setInteger("MainY", pos.getY());
        tag.setInteger("MainZ", pos.getZ());
        return tag;
    }
}
