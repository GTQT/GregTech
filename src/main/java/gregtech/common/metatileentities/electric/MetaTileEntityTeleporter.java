package gregtech.common.metatileentities.electric;

import gregtech.api.capability.IControllable;
import gregtech.api.capability.IDataStickIntractable;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib3.GeckoLib;

import java.util.List;

import static gregtech.api.GTValues.VA;
import static gregtech.api.util.GTUtility.getMetaTileEntity;

public class MetaTileEntityTeleporter extends TieredMetaTileEntity implements IControllable, IDataStickIntractable {

    private boolean isWorkingEnabled;
    private int x;
    private int y;
    private int z;
    private int cooldownTicks;

    public MetaTileEntityTeleporter(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
    }

    public int calculateRange() {
        return (int) (Math.pow(2, getTier() - 1) * 16);
    }

    public boolean isPosValid(World world, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        MetaTileEntity metaTileEntity = getMetaTileEntity(world, pos);
        if (metaTileEntity instanceof MetaTileEntityTeleporter teleporter && teleporter.isWorkingEnabled &&
                teleporter != this) {
            teleporter.refreshCoolDownTicks();
            return true;
        }
        return false;
    }

    private void refreshCoolDownTicks() {
        cooldownTicks = 20;
    }

    public boolean consumeEnergy(boolean simulate, double range) {
        int energyCost = getEUCost(range);
        if (energyContainer.getEnergyStored() >= energyCost) {
            if (!simulate) {
                energyContainer.removeEnergy(energyCost);
            }
            return true;
        }
        return false;
    }

    public int getEUCost(double range) {
        int energyTier = Math.max(1, (int) (range / 16.0));
        return VA[1] * energyTier;
    }

    public boolean isValidRange(double range) {
        return range <= calculateRange();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        tooltip.add(I18n.format("gregtech.machine.teleporter.tooltip.desc"));
        tooltip.add(I18n.format("gregtech.machine.teleporter.tooltip.range", calculateRange()));
        tooltip.add(I18n.format("gregtech.machine.teleporter.tooltip.energy_consumption", VA[1]));
        tooltip.add(I18n.format("gregtech.machine.teleporter.tooltip.data_stick"));
        tooltip.add(I18n.format("gregtech.machine.teleporter.tooltip.screwdriver"));
    }

    public double calculateRange(int x, int y, int z) {
        BlockPos currentPos = getPos();
        return Math.sqrt(currentPos.distanceSq(x, y, z));
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityTeleporter(metaTileEntityId, getTier());
    }

    @Override
    public void update() {
        super.update();
        if (!getWorld().isRemote) {
            // 处理冷却时间
            if (cooldownTicks > 0) {
                cooldownTicks--;
            }

            if (isActive() && isWorkingEnabled && cooldownTicks == 0) {
                checkAndTeleportEntities();
            }
        }
    }

    private void checkAndTeleportEntities() {
        World world = getWorld();
        BlockPos currentPos = getPos();

        // 检查目标位置是否有效
        if (!isPosValid(world, x, y, z)) {
            return;
        }

        // 计算距离
        double range = calculateRange(x, y, z);
        if (!isValidRange(range)) {
            return;
        }

        // 检查能量是否足够
        if (!consumeEnergy(true, range)) {
            return;
        }
        consumeEnergy(false, range);

        // 获取站在当前传送器上方的所有实体（玩家、生物、物品等）
        AxisAlignedBB detectionBox = new AxisAlignedBB(
                currentPos.getX(), currentPos.getY() + 1, currentPos.getZ(),
                currentPos.getX() + 1, currentPos.getY() + 3, currentPos.getZ() + 1
        );

        List<Entity> entitiesOnTeleporter = world.getEntitiesWithinAABB(Entity.class, detectionBox,
                entity -> !entity.isDead && entity.isEntityAlive()); // 只传送活着的实体

        if (!entitiesOnTeleporter.isEmpty()) {
            BlockPos targetPos = new BlockPos(x, y, z);

            for (Entity entity : entitiesOnTeleporter) {
                teleportEntity(entity, targetPos, range);
            }
        }

        // 设置冷却时间（20 ticks = 1秒）
        cooldownTicks = 20;
    }

    private void teleportEntity(Entity entity, BlockPos targetPos, double range) {
        World world = getWorld();

        // 再次验证目标位置（防止在循环期间发生变化）
        if (!isPosValid(world, targetPos.getX(), targetPos.getY(), targetPos.getZ())) {
            return;
        }

        // 计算目标位置（传送到目标传送器上方）
        double targetX = targetPos.getX() + 0.5;
        double targetY = targetPos.getY() + 1.0;
        double targetZ = targetPos.getZ() + 0.5;

        // 对于物品实体，可以添加一些随机偏移以避免堆叠在一起
        if (entity instanceof EntityItem) {
            targetX += (world.rand.nextDouble() - 0.5) * 0.5;
            targetZ += (world.rand.nextDouble() - 0.5) * 0.5;
        }

        // 执行传送
        entity.setPositionAndUpdate(targetX, targetY, targetZ);

        // 只对玩家发送提示消息
        if (!world.isRemote && entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            player.sendStatusMessage(new TextComponentTranslation(
                    "gregtech.machine.teleporter.teleport_success",
                    TextFormattingUtil.formatNumbers(targetX),
                    TextFormattingUtil.formatNumbers(targetY),
                    TextFormattingUtil.formatNumbers(targetZ)
            ), true);
        }
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.TELEPORTER_OVERLAY.renderOrientedState(renderState, translation, pipeline,
                getFrontFacing(), true, true);

    }

    @Override
    public boolean isActive() {
        return energyContainer.getEnergyStored() > 0;
    }

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingEnabled) {
        this.isWorkingEnabled = isWorkingEnabled;
        markDirty();
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        if (!getWorld().isRemote) {
            setWorkingEnabled(!isWorkingEnabled);
            playerIn.sendStatusMessage(new TextComponentTranslation(
                    "gregtech.machine.teleporter.working_" + (isWorkingEnabled ? "enabled" : "disabled")
            ), true);
        }
        return true;
    }

    @Override
    public void onDataStickLeftClick(EntityPlayer player, ItemStack dataStick) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("TeleporterPos", writeLocationToTag());

        if (dataStick.getTagCompound() == null) {
            dataStick.setTagCompound(new NBTTagCompound());
        }
        dataStick.getTagCompound().setTag("TeleporterData", tag);
        dataStick.setTranslatableName("gregtech.machine.teleporter.data_stick_name");
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.teleporter.data_stick_saved"), true);
    }

    private NBTTagCompound writeLocationToTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("MainX", getPos().getX());
        tag.setInteger("MainY", getPos().getY());
        tag.setInteger("MainZ", getPos().getZ());
        return tag;
    }

    @Override
    public boolean onDataStickRightClick(EntityPlayer player, ItemStack dataStick) {
        if (dataStick.getTagCompound() == null || !dataStick.getTagCompound().hasKey("TeleporterData")) {
            return false;
        }

        NBTTagCompound teleporterData = dataStick.getTagCompound().getCompoundTag("TeleporterData");
        if (!teleporterData.hasKey("TeleporterPos")) {
            return false;
        }

        readLocationFromTag(teleporterData.getCompoundTag("TeleporterPos"));
        player.sendStatusMessage(new TextComponentTranslation("gregtech.machine.teleporter_link.data_stick_loaded",
                TextFormattingUtil.formatNumbers(x),
                TextFormattingUtil.formatNumbers(y),
                TextFormattingUtil.formatNumbers(z)), true);

        isWorkingEnabled = true;

        markDirty();
        return true;
    }

    private void readLocationFromTag(NBTTagCompound tag) {
        this.x = tag.getInteger("MainX");
        this.y = tag.getInteger("MainY");
        this.z = tag.getInteger("MainZ");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isWorkingEnabled", isWorkingEnabled);
        data.setInteger("x", x);
        data.setInteger("y", y);
        data.setInteger("z", z);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isWorkingEnabled = data.getBoolean("isWorkingEnabled");
        x = data.getInteger("x");
        y = data.getInteger("y");
        z = data.getInteger("z");
    }
}
