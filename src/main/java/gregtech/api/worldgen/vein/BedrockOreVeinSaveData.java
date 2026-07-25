package gregtech.api.worldgen.vein;

import gregtech.api.GTValues;
import gregtech.api.worldgen.bedrockFluids.ChunkPosDimension;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class BedrockOreVeinSaveData extends WorldSavedData {

    private static BedrockOreVeinSaveData INSTANCE;
    public static final String DATA_NAME = GTValues.MODID + ".bedrockOreVeinData";

    public BedrockOreVeinSaveData(String s) {
        super(s);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        NBTTagList oreList = nbt.getTagList("veinInfo", 10);
        OreVeinHandler.veinCache.clear();
        for (int i = 0; i < oreList.tagCount(); i++) {
            NBTTagCompound tag = oreList.getCompoundTagAt(i);
            ChunkPosDimension coords = ChunkPosDimension.readFromNBT(tag);
            if (coords != null) {
                OreVeinHandler.OreVeinWorldEntry info =
                        OreVeinHandler.OreVeinWorldEntry.readFromNBT(tag.getCompoundTag("info"));
                OreVeinHandler.veinCache.put(coords, info);
            }
        }
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(@NotNull NBTTagCompound nbt) {
        NBTTagList oreList = new NBTTagList();
        for (Map.Entry<ChunkPosDimension, OreVeinHandler.OreVeinWorldEntry> e :
                OreVeinHandler.veinCache.entrySet()) {
            if (e.getKey() != null && e.getValue() != null) {
                NBTTagCompound tag = e.getKey().writeToNBT();
                tag.setTag("info", e.getValue().writeToNBT());
                oreList.appendTag(tag);
            }
        }
        nbt.setTag("veinInfo", oreList);
        return nbt;
    }

    public static void setDirty() {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER && INSTANCE != null) {
            INSTANCE.markDirty();
        }
    }

    public static void setInstance(BedrockOreVeinSaveData in) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER) {
            INSTANCE = in;
        }
    }
}
