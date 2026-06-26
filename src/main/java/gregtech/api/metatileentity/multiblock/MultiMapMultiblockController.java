package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IMultipleRecipeMaps;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class MultiMapMultiblockController extends RecipeMapMultiblockController
        implements IMultipleRecipeMaps {

    // array of possible recipes, specific to each multi - used when the multi has multiple RecipeMaps
    private final RecipeMap<?>[] recipeMaps;

    // index of the current selected recipe - used when the multi has multiple RecipeMaps
    private int recipeMapIndex = 0;

    public MultiMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?>[] recipeMaps) {
        super(metaTileEntityId, recipeMaps[0]);
        this.recipeMaps = recipeMaps;
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        if (recipeMaps.length == 1) return true;
        if (!getWorld().isRemote) {
            if (!this.recipeMapWorkable.isActive()) {
                int index;
                RecipeMap<?>[] recipeMaps = getAvailableRecipeMaps();
                if (playerIn.isSneaking()) // cycle recipemaps backwards
                    index = (recipeMapIndex - 1 < 0 ? recipeMaps.length - 1 : recipeMapIndex - 1) % recipeMaps.length;
                else // cycle recipemaps forwards
                    index = (recipeMapIndex + 1) % recipeMaps.length;

                setRecipeMapIndex(index);
            } else {
                playerIn.sendStatusMessage(
                        new TextComponentTranslation("gregtech.multiblock.multiple_recipemaps.switch_message"), true);
            }
        }

        return true; // return true here on the client to keep the GUI closed
    }

    @Override
    public RecipeMap<?>[] getAvailableRecipeMaps() {
        return recipeMaps;
    }

    @Override
    public int getRecipeMapIndex() {
        return recipeMapIndex;
    }

    @Override
    public void setRecipeMapIndex(int index) {
        int previousIndex = this.recipeMapIndex;
        this.recipeMapIndex = index;
        if (!getWorld().isRemote) {
            writeCustomData(GregtechDataCodes.RECIPE_MAP_INDEX, buf -> buf.writeByte(index));
            recipeMapWorkable.forceRecipeRecheck();
            markDirty();
            if (previousIndex != index) {
                notifyStructureConfigChanged();
            }
        }
    }

    @Override
    public RecipeMap<?> getCurrentRecipeMap() {
        return getAvailableRecipeMaps()[recipeMapIndex];
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    protected Object getStructureConfigDependencyValue() {
        Map<String, Object> values = new LinkedHashMap<>(
                (Map<String, Object>) super.getStructureConfigDependencyValue());
        RecipeMap<?> currentRecipeMap = getCurrentRecipeMap();
        values.put("recipeMapIndex", recipeMapIndex);
        values.put("currentRecipeMap", currentRecipeMap == null ? null : currentRecipeMap.getUnlocalizedName());
        return values;
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return super.createUIFactory()
                .createGcymButton((guiData, syncManager) -> {
                    RecipeMap<?>[] recipeMaps = getAvailableRecipeMaps();
                    if (ArrayUtils.getLength(recipeMaps) <= 1) return null;

                    IntSyncValue activeMapIndex = new IntSyncValue(this::getRecipeMapIndex, this::setRecipeMapIndex);

                    return new CycleButtonWidget()
                            .overlay(GTGuiTextures.BUTTON_MULTI_MAP)
                            .background(GTGuiTextures.BUTTON)
                            .disableHoverBackground()
                            .value(activeMapIndex)
                            .length(recipeMaps.length)
                            .tooltipBuilder(t -> {
                                RecipeMap<?> map = recipeMaps[activeMapIndex.getIntValue()];
                                t.addLine(IKey.lang("gregtech.multiblock.multiple_recipemaps.value", IKey.lang(map.getTranslationKey())));
                            });
                });
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(recipeMapWorkable.isWorkingEnabled(), recipeMapWorkable.isActive())
                .addRecipeMapLine(getCurrentRecipeMap())
                .addEnergyUsageLine(this.getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(recipeMapWorkable.getMaxVoltage()))
                .addParallelsLine(recipeMapWorkable.getParallelLimit())
                .addWorkingStatusLine();

        // Cross-recipe parallel display (synced via builder to prevent client/server buffer desync)
        builder.addCrossRecipeOrProgressDisplay(recipeMapWorkable);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String recipeMapsToString() {
        StringBuilder recipeMapsString = new StringBuilder();
        RecipeMap<?>[] recipeMaps = getAvailableRecipeMaps();
        for (int i = 0; i < recipeMaps.length; i++) {
            recipeMapsString.append(recipeMaps[i].getLocalizedName());
            if (recipeMaps.length - 1 != i)
                recipeMapsString.append(", "); // For delimiting
        }
        return recipeMapsString.toString();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("RecipeMapIndex", recipeMapIndex);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        recipeMapIndex = data.getInteger("RecipeMapIndex");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(recipeMapIndex);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        recipeMapIndex = buf.readByte();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.RECIPE_MAP_INDEX) {
            recipeMapIndex = buf.readByte();
            scheduleRenderUpdate();
        }
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        T capabilityResult = super.getCapability(capability, side);
        if (capabilityResult == null && capability == GregtechTileCapabilities.CAPABILITY_MULTIPLE_RECIPEMAPS) {
            return GregtechTileCapabilities.CAPABILITY_MULTIPLE_RECIPEMAPS.cast(this);
        }
        return capabilityResult;
    }

    @Override
    public boolean isBatchAllowed() {
        return true;
    }
}
