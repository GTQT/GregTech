package gregtech.client.renderer.godforge;

import gregtech.common.metatileentities.multi.electric.godforge.color.ForgeOfGodsStarColor;
import gregtech.common.metatileentities.multi.electric.godforge.color.StarColorSetting;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.utils.Color;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.util.GTLog;
import gregtech.common.metatileentities.multi.electric.godforge.MetaTileEntityForgeOfGods;

public class GodforgeRenderTileEntity extends TileEntity {

    private float radius = 32;
    private float rotationSpeed = 10;
    private int ringCount = 1;
    private float rotAngle = 0, rotAxisX = 1, rotAxisY = 0, rotAxisZ = 0;
    private AxisAlignedBB renderBoundingBox;

    private ForgeOfGodsStarColor starColor = ForgeOfGodsStarColor.DEFAULT;

    private int currentColor = Color.rgb(
            ForgeOfGodsStarColor.DEFAULT_RED,
            ForgeOfGodsStarColor.DEFAULT_GREEN,
            ForgeOfGodsStarColor.DEFAULT_BLUE);
    private float gamma = ForgeOfGodsStarColor.DEFAULT_GAMMA;
    private long lastColorUpdateTime = 0;

    private float cycleStep;
    private int interpIndex;
    private int interpA;
    private int interpB;
    private float interpGammaA;
    private float interpGammaB;

    private static final String NBT_TAG = "FOG:";
    private static final String ROTATION_SPEED_NBT_TAG = NBT_TAG + "ROTATION";
    private static final String SIZE_NBT_TAG = NBT_TAG + "RADIUS";
    private static final String RINGS_NBT_TAG = NBT_TAG + "RINGS";
    private static final String ROT_ANGLE_NBT_TAG = NBT_TAG + "ROT_ANGLE";
    private static final String ROT_AXIS_X_NBT_TAG = NBT_TAG + "ROT_AXIS_X";
    private static final String ROT_AXIS_Y_NBT_TAG = NBT_TAG + "ROT_AXIS_Y";
    private static final String ROT_AXIS_Z_NBT_TAG = NBT_TAG + "ROT_AXIS_Z";
    private static final String STAR_COLOR_TAG = NBT_TAG + "STAR_COLOR";
    private static final String OWNER_X_NBT_TAG = NBT_TAG + "OWNER_X";
    private static final String OWNER_Y_NBT_TAG = NBT_TAG + "OWNER_Y";
    private static final String OWNER_Z_NBT_TAG = NBT_TAG + "OWNER_Z";

    public static final float BACK_PLATE_DISTANCE = -121.5f, BACK_PLATE_RADIUS = 13f;
    private static final double RING_RADIUS = 63;
    private static final double BEAM_LENGTH = 59;

    private static final float COLOR_CYCLE_SPEED = 16f;
    private BlockPos ownerPos;
    private long lastInvalidOwnerLogTime = 0;

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (renderBoundingBox == null) {
            double x = this.pos.getX();
            double y = this.pos.getY();
            double z = this.pos.getZ();

            renderBoundingBox = new AxisAlignedBB(
                    x - RING_RADIUS - BEAM_LENGTH,
                    y - RING_RADIUS - BEAM_LENGTH,
                    z - RING_RADIUS - BEAM_LENGTH,
                    x + RING_RADIUS + BEAM_LENGTH + 1,
                    y + RING_RADIUS + BEAM_LENGTH + 1,
                    z + RING_RADIUS + BEAM_LENGTH + 1);
        }
        return renderBoundingBox;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return Double.MAX_VALUE;
    }

    public void setStarRadius(float size) {
        this.radius = size;
    }

    public float getStarRadius() {
        return radius;
    }

    public float getRotationSpeed() {
        return rotationSpeed;
    }

    public void setRotationSpeed(float speed) {
        this.rotationSpeed = speed;
    }

    public float getColorR() {
        return Color.getRedF(currentColor);
    }

    public float getColorG() {
        return Color.getGreenF(currentColor);
    }

    public float getColorB() {
        return Color.getBlueF(currentColor);
    }

    public float getGamma() {
        return gamma;
    }

    public void setColor(ForgeOfGodsStarColor color) {
        this.starColor = color;
        if (this.starColor == null) {
            this.starColor = ForgeOfGodsStarColor.DEFAULT;
        }

        StarColorSetting colorSetting = starColor.getColor(0);
        currentColor = Color.rgb(colorSetting.getColorR(), colorSetting.getColorG(), colorSetting.getColorB());
        gamma = colorSetting.getGamma();

        if (starColor.numColors() > 1) {
            cycleStep = 0;
            interpA = currentColor;
            interpGammaA = gamma;
            colorSetting = starColor.getColor(1);
            interpB = Color.rgb(colorSetting.getColorR(), colorSetting.getColorG(), colorSetting.getColorB());
            interpGammaB = colorSetting.getGamma();
        }
    }

    public int getRingCount() {
        return ringCount;
    }

    public void setRingCount(int count) {
        if (count < 1) return;
        ringCount = count;
    }

    public float getRotAngle() {
        return rotAngle;
    }

    public float getRotAxisX() {
        return rotAxisX;
    }

    public float getRotAxisY() {
        return rotAxisY;
    }

    public float getRotAxisZ() {
        return rotAxisZ;
    }

    public void setRenderRotation(net.minecraft.util.EnumFacing direction) {
        switch (direction) {
            case SOUTH:
            case NORTH:
                rotAngle = 90;
                break;
            case WEST:
                rotAngle = 0;
                break;
            case EAST:
                rotAngle = 180;
                break;
            case UP:
            case DOWN:
                rotAngle = -90;
                break;
        }
        rotAxisX = 0;
        rotAxisY = direction.getXOffset() + direction.getZOffset();
        rotAxisZ = direction.getYOffset();

        updateToClient();
    }

    public void setOwnerPos(BlockPos ownerPos) {
        this.ownerPos = ownerPos;
        updateToClient();
    }

    public BlockPos getOwnerPosForDebug() {
        return ownerPos;
    }

    public boolean hasValidOwner() {
        if (world == null) {
            logInvalidOwner("world is null");
            return false;
        }
        if (ownerPos == null) {
            logInvalidOwner("ownerPos is null");
            return false;
        }
        if (!world.isBlockLoaded(ownerPos)) {
            logInvalidOwner("owner chunk is not loaded");
            return false;
        }

        TileEntity te = world.getTileEntity(ownerPos);
        if (!(te instanceof MetaTileEntityHolder)) {
            logInvalidOwner("owner tile is " + (te == null ? "null" : te.getClass().getName()));
            return false;
        }

        MetaTileEntity metaTileEntity = ((MetaTileEntityHolder) te).getMetaTileEntity();
        if (!(metaTileEntity instanceof MetaTileEntityForgeOfGods)) {
            logInvalidOwner("owner meta tile is " +
                    (metaTileEntity == null ? "null" : metaTileEntity.getClass().getName()));
            return false;
        }
        return true;
    }

    private void logInvalidOwner(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastInvalidOwnerLogTime >= 5000) {
            GTLog.logger.info("[FOG] GodforgeRenderTileEntity: invalid owner at {}, owner={}, reason={}",
                    pos, ownerPos, reason);
            lastInvalidOwnerLogTime = now;
        }
    }

    public float getLensDistance(int lensID) {
        switch (lensID) {
            case 0: return -61.5f;
            case 1: return -54.5f;
            case 2: return -44.5f;
            default: throw new IllegalStateException("Unexpected value: " + lensID);
        }
    }

    public float getLenRadius(int lensID) {
        switch (lensID) {
            case 0: return 1.1f;
            case 1: return 3.5f;
            case 2: return 5f;
            default: throw new IllegalStateException("Unexpected value: " + lensID);
        }
    }

    public float getStartAngle() {
        float x = -getLensDistance(getRingCount() - 1);
        float y = getLenRadius(getRingCount() - 1);
        float alpha = (float) Math.atan2(y, x);
        float beta = (float) Math.asin(radius / Math.sqrt(x * x + y * y));
        return alpha + ((float) Math.PI / 2 - beta);
    }

    public static float interpolate(float x0, float x1, float y0, float y1, float x) {
        return y0 + ((x - x0) * (y1 - y0)) / (x1 - x0);
    }

    public void incrementColors() {
        if (starColor.numColors() <= 1) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (lastColorUpdateTime == 0) {
            lastColorUpdateTime = currentTime;
            return;
        }

        long deltaTime = currentTime - lastColorUpdateTime;
        lastColorUpdateTime = currentTime;

        float increment = starColor.getCycleSpeed() * (deltaTime / COLOR_CYCLE_SPEED);
        cycleStep += increment;

        while (cycleStep >= 255.0f) {
            cycleStep -= 255.0f;
            cycleStarColors();
            currentColor = interpA;
            gamma = interpGammaA;
        }

        interpolateColors();
    }

    private void interpolateColors() {
        float position = cycleStep / 255.0f;
        currentColor = interpolateColor(interpA, interpB, position);
        gamma = interpGammaA + (interpGammaB - interpGammaA) * position;
    }

    private static int interpolateColor(int color1, int color2, float blend) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * blend);
        int g = (int) (g1 + (g2 - g1) * blend);
        int b = (int) (b1 + (b2 - b1) * blend);

        return (r << 16) | (g << 8) | b;
    }

    private void cycleStarColors() {
        interpA = interpB;
        interpGammaA = interpGammaB;

        interpIndex++;
        if (interpIndex >= starColor.numColors()) {
            interpIndex = 0;
        }
        StarColorSetting nextColor = starColor.getColor(interpIndex);
        interpB = Color.rgb(nextColor.getColorR(), nextColor.getColorG(), nextColor.getColorB());
        interpGammaB = nextColor.getGamma();
    }

    public void updateToClient() {
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        radius = compound.getFloat(SIZE_NBT_TAG);
        rotationSpeed = compound.getFloat(ROTATION_SPEED_NBT_TAG);
        ringCount = compound.getInteger(RINGS_NBT_TAG);
        rotAngle = compound.getFloat(ROT_ANGLE_NBT_TAG);
        rotAxisX = compound.getFloat(ROT_AXIS_X_NBT_TAG);
        rotAxisY = compound.getFloat(ROT_AXIS_Y_NBT_TAG);
        rotAxisZ = compound.getFloat(ROT_AXIS_Z_NBT_TAG);

        if (compound.hasKey(STAR_COLOR_TAG)) {
            NBTTagCompound colorTag = compound.getCompoundTag(STAR_COLOR_TAG);
            ForgeOfGodsStarColor color = ForgeOfGodsStarColor.deserialize(colorTag);
            if (color != null) {
                setColor(color);
            }
        }
        if (compound.hasKey(OWNER_X_NBT_TAG) && compound.hasKey(OWNER_Y_NBT_TAG) && compound.hasKey(OWNER_Z_NBT_TAG)) {
            ownerPos = new BlockPos(
                    compound.getInteger(OWNER_X_NBT_TAG),
                    compound.getInteger(OWNER_Y_NBT_TAG),
                    compound.getInteger(OWNER_Z_NBT_TAG));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setFloat(SIZE_NBT_TAG, radius);
        compound.setFloat(ROTATION_SPEED_NBT_TAG, rotationSpeed);
        compound.setInteger(RINGS_NBT_TAG, ringCount);
        compound.setFloat(ROT_ANGLE_NBT_TAG, rotAngle);
        compound.setFloat(ROT_AXIS_X_NBT_TAG, rotAxisX);
        compound.setFloat(ROT_AXIS_Y_NBT_TAG, rotAxisY);
        compound.setFloat(ROT_AXIS_Z_NBT_TAG, rotAxisZ);

        if (starColor != null) {
            compound.setTag(STAR_COLOR_TAG, starColor.serializeToNBT());
        }
        if (ownerPos != null) {
            compound.setInteger(OWNER_X_NBT_TAG, ownerPos.getX());
            compound.setInteger(OWNER_Y_NBT_TAG, ownerPos.getY());
            compound.setInteger(OWNER_Z_NBT_TAG, ownerPos.getZ());
        }
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
