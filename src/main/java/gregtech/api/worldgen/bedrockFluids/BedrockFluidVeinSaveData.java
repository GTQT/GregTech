package gregtech.api.worldgen.bedrockFluids;

import gregtech.api.GTValues;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BedrockFluidVeinSaveData extends WorldSavedData {

    private static BedrockFluidVeinSaveData INSTANCE;
    public static final String DATA_NAME = GTValues.MODID + ".bedrockFluidVeinData";

    public BedrockFluidVeinSaveData(String s) {
        super(s);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList fluidList = nbt.getTagList("veinInfo", 10);
        BedrockFluidVeinHandler.veinCache.clear();
        for (int i = 0; i < fluidList.tagCount(); i++) {
            NBTTagCompound tag = fluidList.getCompoundTagAt(i);
            ChunkPosDimension coords = ChunkPosDimension.readFromNBT(tag);
            if (coords != null) {
                BedrockFluidVeinHandler.FluidVeinWorldEntry info =
                        BedrockFluidVeinHandler.FluidVeinWorldEntry.readFromNBT(tag.getCompoundTag("info"));
                BedrockFluidVeinHandler.veinCache.put(coords, info);
            }
        }
        if (nbt.hasKey("version")) {
            BedrockFluidVeinHandler.saveDataVersion = nbt.getInteger("version");
        } else if (fluidList.isEmpty()) {
            BedrockFluidVeinHandler.saveDataVersion = BedrockFluidVeinHandler.MAX_FLUID_SAVE_DATA_VERSION;
        } else {
            BedrockFluidVeinHandler.saveDataVersion = 1;
        }
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound nbt) {
        NBTTagList fluidList = new NBTTagList();
        for (Map.Entry<ChunkPosDimension, BedrockFluidVeinHandler.FluidVeinWorldEntry> e :
                BedrockFluidVeinHandler.veinCache.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                NBTTagCompound tag = e.getKey().writeToNBT();
                tag.setTag("info", e.getValue().writeToNBT());
                fluidList.appendTag(tag);
            }
        }
        nbt.setTag("veinInfo", fluidList);
        nbt.setInteger("version", BedrockFluidVeinHandler.saveDataVersion);
        return nbt;
    }

    public static void setDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER && INSTANCE != null) {
            INSTANCE.markDirty();
        }
    }

    public static void setInstance(BedrockFluidVeinSaveData in) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
            INSTANCE = in;
        }
    }
}
