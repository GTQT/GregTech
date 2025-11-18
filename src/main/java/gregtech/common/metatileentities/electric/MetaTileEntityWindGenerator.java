package gregtech.common.metatileentities.electric;

import com.cleanroommc.modularui.screen.UISettings;

import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.damagesources.DamageSources;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.TieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.client.renderer.texture.Textures;
import gregtech.core.advancement.AdvancementTriggers;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.SyncHandlers;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import gtqt.common.items.behaviors.WindRotorBehavior;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static gregtech.api.GTValues.V;

public class MetaTileEntityWindGenerator extends TieredMetaTileEntity {

    private final InventoryWindHolder inventory;
    private int weatherUpdateTimer = 0;
    private double heightEfficiency = 1.0;
    private double biomeEfficiency = 1.0;
    private double weatherEfficiency = 1.0;
    private int baseRotorDamage = 1;
    private int weatherRotorDamageBonus = 0;

    public MetaTileEntityWindGenerator(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, tier);
        this.inventory = new InventoryWindHolder();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityWindGenerator(metaTileEntityId, getTier());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.ENERGY_OUT.renderSided(getFrontFacing().getOpposite(), renderState, translation, pipeline);
        Textures.MUFFLER_OVERLAY.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 在加载时立即计算一次高度和生物群系效率（这些是固定的）
        calculateFixedEfficiencies();
    }

    @Override
    public void update() {
        super.update();

        if (!getWorld().isRemote) {
            // 每200tick（10秒）更新一次天气效率
            weatherUpdateTimer++;
            if (weatherUpdateTimer >= 200) {
                weatherUpdateTimer = 0;
                updateWeatherEfficiencies();
            }

            if (inventory.hasRotor()) {
                // 每tick都发电
                int totalEnergyGeneration = calculateEnergyGeneration();
                int totalRotorDamage = calculateRotorDamage();

                inventory.damageRotor(totalRotorDamage);
                energyContainer.changeEnergy(totalEnergyGeneration);
            }
        }
    }

    private void calculateFixedEfficiencies() {
        World world = getWorld();
        if (world == null) return;

        // 高度效率：每50格+10%，最高300%（3.0倍）
        int yLevel = getPos().getY();
        heightEfficiency = Math.min(3.0, 1.0 + (yLevel / 50.0) * 0.1);

        // 生物群系效率
        biomeEfficiency = getBiomeEfficiency(world);

        // 基础转子损害考虑高度因素
        if (yLevel > 150) {
            baseRotorDamage = 2; // 高海拔额外压力
        } else {
            baseRotorDamage = 1;
        }
    }

    private void updateWeatherEfficiencies() {
        World world = getWorld();
        if (world == null) return;

        // 天气效率
        if (world.isThundering()) {
            weatherEfficiency = 1.3; // 雷暴雨 +30%
            weatherRotorDamageBonus = 2; // 雷暴雨 +2损害
        } else if (world.isRaining()) {
            weatherEfficiency = 1.15; // 雨天 +15%
            weatherRotorDamageBonus = 1; // 雨天 +1损害
        } else {
            weatherEfficiency = 1.0; // 晴天 基础效率
            weatherRotorDamageBonus = 0; // 晴天 无额外损害
        }
    }

    private double getBiomeEfficiency(World world) {
        Biome biome = world.getBiome(getPos());
        String biomeName = biome.getBiomeName().toLowerCase();

        // 平原、海洋、山地等开阔地形有更高效率
        if (biomeName.contains("plain") || biomeName.contains("ocean") ||
                biomeName.contains("sea") || biomeName.contains("mountain") ||
                biomeName.contains("hill") || biomeName.contains("plateau")) {
            return 1.1; // 开阔地形 +10%
        }

        // 森林、丛林等封闭地形效率较低
        if (biomeName.contains("forest") || biomeName.contains("jungle") ||
                biomeName.contains("wood") || biomeName.contains("taiga")) {
            return 0.9; // 封闭地形 -10%
        }

        return 1.0; // 其他地形基础效率
    }

    private int calculateEnergyGeneration() {
        if (!inventory.hasRotor()) return 0;

        int baseEnergy = (int) V[inventory.getTier()];
        double totalEfficiency = heightEfficiency * weatherEfficiency * biomeEfficiency;
        return (int) (baseEnergy * totalEfficiency);
    }

    private int calculateRotorDamage() {
        return baseRotorDamage + weatherRotorDamageBonus;
    }

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    public void onRemoval() {
        super.onRemoval();
        var pos = getPos();
        if (!inventory.getStackInSlot(0).isEmpty()) {
            getWorld().spawnEntity(new EntityItem(getWorld(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    inventory.getStackInSlot(0)));
            inventory.extractItem(0, 1, false);
        }
    }

    @Override
    public boolean usesMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager guiSyncManager, UISettings settings) {
        guiSyncManager.registerSlotGroup("item_inv", 1);
        // TODO: Change the position of the name when it's standardized.
        return GTGuis.createPanel(this, 176, 166)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(5, 5))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7))
                .child(new ItemSlot()
                        .slot(SyncHandlers.itemSlot(inventory, 0)
                                .slotGroup("item_inv")
                                .changeListener(
                                        (newItem, onlyAmountChanged, client, init) -> inventory.onContentsChanged(0)))
                        .background(GTGuiTextures.SLOT, GTGuiTextures.TURBINE_OVERLAY)
                        .left(79).top(36));
    }

    @Override
    public boolean onRightClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                CuboidRayTraceResult hitResult) {
        return onRotorHolderInteract(playerIn) || super.onRightClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        return onRotorHolderInteract(playerIn) || super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        return onRotorHolderInteract(playerIn);
    }

    @Override
    public void onLeftClick(EntityPlayer player, EnumFacing facing, CuboidRayTraceResult hitResult) {
        onRotorHolderInteract(player);
    }

    private boolean onRotorHolderInteract(@NotNull EntityPlayer player) {
        if (player.isCreative()) return false;

        if (!getWorld().isRemote && inventory.hasRotor()) {
            applyDamage(player);
            AdvancementTriggers.ROTOR_HOLDER_DEATH.trigger((EntityPlayerMP) player);
            return true;
        }
        return inventory.hasRotor();
    }

    private void applyDamage(Entity entity) {
        float damageApplied = 20;
        entity.attackEntityFrom(DamageSources.getTurbineDamage(), damageApplied);
    }

    @Override
    public void clearMachineInventory(@NotNull List<@NotNull ItemStack> itemBuffer) {
        super.clearMachineInventory(itemBuffer);
        clearInventory(itemBuffer, inventory);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setTag("inventory", inventory.serializeNBT());
        data.setDouble("HeightEfficiency", heightEfficiency);
        data.setDouble("BiomeEfficiency", biomeEfficiency);
        data.setDouble("WeatherEfficiency", weatherEfficiency);
        data.setInteger("BaseRotorDamage", baseRotorDamage);
        data.setInteger("WeatherRotorDamageBonus", weatherRotorDamageBonus);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.inventory.deserializeNBT(data.getCompoundTag("inventory"));
        this.heightEfficiency = data.getDouble("HeightEfficiency");
        this.biomeEfficiency = data.getDouble("BiomeEfficiency");
        this.weatherEfficiency = data.getDouble("WeatherEfficiency");
        this.baseRotorDamage = data.getInteger("BaseRotorDamage");
        this.weatherRotorDamageBonus = data.getInteger("WeatherRotorDamageBonus");
    }

    // 获取当前总效率（可用于UI显示）
    public double getCurrentEfficiency() {
        return heightEfficiency * weatherEfficiency * biomeEfficiency;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("tooltip.wind_generator.tooltip1")); // 基础发电量为转子等级的电压值
        tooltip.add(I18n.format("tooltip.wind_generator.tooltip2")); // 高度影响发电效率
        tooltip.add(I18n.format("tooltip.wind_generator.tooltip3")); // 天气影响发电和转子损耗
        tooltip.add(I18n.format("tooltip.wind_generator.tooltip4")); // 生物群系影响发电效率
        tooltip.add(I18n.format("tooltip.wind_generator.tooltip5")); // 需要安装转子才能工作
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    private class InventoryWindHolder extends NotifiableItemStackHandler {

        public InventoryWindHolder() {
            super(MetaTileEntityWindGenerator.this, 1, null, false);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Nullable
        private WindRotorBehavior getWindRotorBehavior() {
            ItemStack stack = getStackInSlot(0);
            if (stack.isEmpty()) return null;

            return WindRotorBehavior.getInstanceFor(stack);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        private boolean hasRotor() {
            return getWindRotorBehavior() != null;
        }

        private double getRotorDurabilityPercent() {
            if (!hasRotor()) return 0;

            // noinspection ConstantConditions
            return getWindRotorBehavior().getDurabilityPercent(getStackInSlot(0));
        }

        private void damageRotor(int damageAmount) {
            if (!hasRotor()) return;
            // noinspection ConstantConditions
            getWindRotorBehavior().applyDamage(getStackInSlot(0), damageAmount);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return WindRotorBehavior.getInstanceFor(stack) != null && super.isItemValid(slot, stack);
        }

        public int getTier() {
            if (!hasRotor()) return 0;
            // noinspection ConstantConditions
            return getWindRotorBehavior().getTier();
        }
    }
}
