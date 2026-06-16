package gregtech.api.pattern.element;

import gregtech.api.pattern.StructurePieceKey;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-instance metadata captured from a successful structure check.
 */
public final class FormedStructureMetadata {

    private static final String KEY_PIECE_REPEATS = "PieceRepeats";
    private static final String KEY_CHANNEL_VALUES = "ChannelValues";
    private static final String KEY_PIECE_CENTERS = "PieceCenters";

    private final Map<String, int[]> pieceRepeats;
    private final Map<String, Integer> channelValues;
    private final Map<String, BlockPos> pieceCenters;

    public FormedStructureMetadata(@NotNull Map<String, int[]> pieceRepeats,
                                   @NotNull Map<String, Integer> channelValues) {
        this(pieceRepeats, channelValues, Collections.emptyMap());
    }

    public FormedStructureMetadata(@NotNull Map<String, int[]> pieceRepeats,
                                   @NotNull Map<String, Integer> channelValues,
                                   @NotNull Map<String, BlockPos> pieceCenters) {
        Map<String, int[]> repeatCopies = new HashMap<>();
        pieceRepeats.forEach((name, repeats) -> repeatCopies.put(name, repeats.clone()));
        this.pieceRepeats = Collections.unmodifiableMap(repeatCopies);
        this.channelValues = Collections.unmodifiableMap(new HashMap<>(channelValues));
        this.pieceCenters = Collections.unmodifiableMap(new HashMap<>(pieceCenters));
    }

    public int getPieceRepeat(@NotNull String pieceName, int axisIndex) {
        int[] reps = pieceRepeats.get(pieceName);
        if (reps == null || axisIndex < 0 || axisIndex >= reps.length) return 0;
        return reps[axisIndex];
    }

    public int getPieceRepeat(@NotNull StructurePieceKey pieceKey, int axisIndex) {
        return getPieceRepeat(pieceKey.getName(), axisIndex);
    }

    @NotNull
    public int[] getPieceRepeats(@NotNull String pieceName) {
        int[] reps = pieceRepeats.get(pieceName);
        return reps == null ? new int[0] : reps.clone();
    }

    @NotNull
    public int[] getPieceRepeats(@NotNull StructurePieceKey pieceKey) {
        return getPieceRepeats(pieceKey.getName());
    }

    @Nullable
    public BlockPos getPieceCenter(@NotNull String pieceName) {
        return pieceCenters.get(pieceName);
    }

    @Nullable
    public BlockPos getPieceCenter(@NotNull StructurePieceKey pieceKey) {
        return getPieceCenter(pieceKey.getName());
    }

    public int getChannelValue(@NotNull String channelName) {
        return channelValues.getOrDefault(channelName, 0);
    }

    @NotNull
    @ApiStatus.Internal
    public Map<String, Integer> getChannelValues() {
        return channelValues;
    }

    @NotNull
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();

        NBTTagCompound repeatsTag = new NBTTagCompound();
        for (Map.Entry<String, int[]> entry : pieceRepeats.entrySet()) {
            repeatsTag.setIntArray(entry.getKey(), entry.getValue());
        }
        tag.setTag(KEY_PIECE_REPEATS, repeatsTag);

        NBTTagCompound valuesTag = new NBTTagCompound();
        for (Map.Entry<String, Integer> entry : channelValues.entrySet()) {
            valuesTag.setInteger(entry.getKey(), entry.getValue());
        }
        tag.setTag(KEY_CHANNEL_VALUES, valuesTag);

        NBTTagCompound centersTag = new NBTTagCompound();
        for (Map.Entry<String, BlockPos> entry : pieceCenters.entrySet()) {
            centersTag.setLong(entry.getKey(), entry.getValue().toLong());
        }
        tag.setTag(KEY_PIECE_CENTERS, centersTag);

        return tag;
    }

    @NotNull
    public static FormedStructureMetadata readFromNBT(@NotNull NBTTagCompound tag) {
        Map<String, int[]> repeats = new HashMap<>();
        NBTTagCompound repeatsTag = tag.getCompoundTag(KEY_PIECE_REPEATS);
        for (String key : repeatsTag.getKeySet()) {
            repeats.put(key, repeatsTag.getIntArray(key));
        }

        Map<String, Integer> values = new HashMap<>();
        NBTTagCompound valuesTag = tag.getCompoundTag(KEY_CHANNEL_VALUES);
        for (String key : valuesTag.getKeySet()) {
            values.put(key, valuesTag.getInteger(key));
        }

        Map<String, BlockPos> centers = new HashMap<>();
        NBTTagCompound centersTag = tag.getCompoundTag(KEY_PIECE_CENTERS);
        for (String key : centersTag.getKeySet()) {
            centers.put(key, BlockPos.fromLong(centersTag.getLong(key)));
        }

        return new FormedStructureMetadata(repeats, values, centers);
    }

    @NotNull
    public static FormedStructureMetadata fromCheckResult(
            @NotNull Map<String, int[]> pieceRepeats,
            @NotNull Map<String, Integer> channelValues) {
        return new FormedStructureMetadata(pieceRepeats, channelValues);
    }

    @NotNull
    public static FormedStructureMetadata fromCheckResult(
            @NotNull Map<String, int[]> pieceRepeats,
            @NotNull Map<String, Integer> channelValues,
            @NotNull Map<String, BlockPos> pieceCenters) {
        return new FormedStructureMetadata(pieceRepeats, channelValues, pieceCenters);
    }
}
