package gregtech.common.metatileentities.multi.electric.godforge.color;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import org.apache.commons.lang3.builder.ToStringBuilder;

public class StarColorSetting {

    private final int r, g, b;
    private final float gamma;

    public StarColorSetting(int r, int g, int b, float gamma) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.gamma = gamma;
    }

    public int getColorR() {
        return r;
    }

    public int getColorG() {
        return g;
    }

    public int getColorB() {
        return b;
    }

    public float getGamma() {
        return gamma;
    }

    protected NBTTagCompound serialize() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("R", r);
        nbt.setInteger("G", g);
        nbt.setInteger("B", b);
        nbt.setFloat("Gamma", gamma);
        return nbt;
    }

    protected static StarColorSetting deserialize(NBTTagCompound nbt) {
        int r = nbt.getInteger("R");
        int g = nbt.getInteger("G");
        int b = nbt.getInteger("B");
        float gamma = nbt.getFloat("Gamma");
        return new StarColorSetting(r, g, b, gamma);
    }

    public static void writeToBuffer(PacketBuffer buf, StarColorSetting color) {
        buf.writeInt(color.r);
        buf.writeInt(color.g);
        buf.writeInt(color.b);
        buf.writeFloat(color.gamma);
    }

    public static StarColorSetting readFromBuffer(PacketBuffer buf) {
        int r = buf.readInt();
        int g = buf.readInt();
        int b = buf.readInt();
        float gamma = buf.readFloat();
        return new StarColorSetting(r, g, b, gamma);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StarColorSetting that = (StarColorSetting) o;

        if (r != that.r) return false;
        if (g != that.g) return false;
        if (b != that.b) return false;
        return gamma == that.gamma;
    }

    @Override
    public int hashCode() {
        int result = r;
        result = 31 * result + g;
        result = 31 * result + b;
        result = 31 * result + (gamma != +0.0f ? Float.floatToIntBits(gamma) : 0);
        return result;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("r", r)
            .append("g", g)
            .append("b", b)
            .append("gamma", gamma)
            .build();
    }
}
