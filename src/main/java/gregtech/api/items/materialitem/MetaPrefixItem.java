package gregtech.api.items.materialitem;

import gregtech.api.GTValues;
import gregtech.api.damagesources.DamageSources;
import gregtech.api.items.armor.ArmorMetaItem;
import gregtech.api.items.metaitem.StandardMetaItem;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Material;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.material.info.MaterialIconSet;
import gregtech.api.unification.material.properties.DustProperty;
import gregtech.api.unification.material.properties.MaterialToolProperty;
import gregtech.api.unification.material.properties.PropertyKey;
import gregtech.api.unification.material.registry.MaterialRegistry;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.UnificationEntry;
import gregtech.common.ConfigHolder;
import gregtech.common.creativetab.GTCreativeTabs;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import it.unimi.dsi.fastutil.shorts.Short2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MetaPrefixItem extends StandardMetaItem {

    private final MaterialRegistry registry;
    private final OrePrefix prefix;

    public static final Map<OrePrefix, OrePrefix> purifyMap = new HashMap<>();
    public static final Map<OrePrefix, OrePrefix> hotMap = new HashMap<>();

    static {
        purifyMap.put(OrePrefix.crushed, OrePrefix.crushedPurified);
        purifyMap.put(OrePrefix.dustImpure, OrePrefix.dust);
        purifyMap.put(OrePrefix.dustPure, OrePrefix.dust);

        hotMap.put(OrePrefix.ingotHot, OrePrefix.nugget);
    }

    // Configuration flags - these should be set from your config system
    private static final boolean EASY_COOLING = ConfigHolder.recipes.easyCooling;
    private static final boolean EASY_CLEANING = ConfigHolder.recipes.easyCleaning;

    public MetaPrefixItem(@NotNull MaterialRegistry registry, @NotNull OrePrefix orePrefix) {
        super();
        this.registry = registry;
        this.prefix = orePrefix;
        this.setCreativeTab(GTCreativeTabs.TAB_GREGTECH_MATERIALS);
    }

    @Override
    public void registerSubItems() {
        for (Material material : registry) {
            short i = (short) registry.getIDForObject(material);
            if (prefix != null && canGenerate(prefix, material)) {
                addItem(i, new UnificationEntry(prefix, material).toString());
            }
        }
    }

    public void registerOreDict() {
        for (short metaItem : metaItems.keySet()) {
            Material material = getMaterial(metaItem);
            ItemStack item = new ItemStack(this, 1, metaItem);
            OreDictUnifier.registerOre(item, prefix, material);
            registerSpecialOreDict(item, material, prefix);
        }
    }

    private static void registerSpecialOreDict(ItemStack item, Material material, OrePrefix prefix) {
        if (prefix.getAlternativeOreName() != null) {
            OreDictUnifier.registerOre(item, prefix.getAlternativeOreName(), material);
        }

        if (material == Materials.Saltpeter) {
            OreDictUnifier.registerOre(item, prefix.name() + material.toCamelCaseString());
        }
    }

    protected static boolean canGenerate(OrePrefix orePrefix, Material material) {
        return orePrefix.doGenerateItem(material);
    }

    @NotNull
    @Override
    public String getItemStackDisplayName(@NotNull ItemStack itemStack) {
        Material material = getMaterial(itemStack);
        if (material == null || prefix == null) return "";
        return prefix.getLocalNameForItem(material);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected int getColorForItemStack(@NotNull ItemStack stack, int tintIndex) {
        if (tintIndex == 0) {
            Material material = getMaterial(stack);
            if (material == null)
                return 0xFFFFFF;
            return material.getMaterialRGB();
        }
        return super.getColorForItemStack(stack, tintIndex);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels() {
        Map<Short, ModelResourceLocation> alreadyRegistered = new Short2ObjectOpenHashMap<>();
        for (short metaItem : metaItems.keySet()) {
            MaterialIconSet materialIconSet = getMaterial(metaItem).getMaterialIconSet();

            short registrationKey = (short) (prefix.id + materialIconSet.id);
            if (!alreadyRegistered.containsKey(registrationKey)) {
                ResourceLocation resourceLocation = Objects.requireNonNull(prefix.materialIconType)
                        .getItemModelPath(materialIconSet);
                ModelBakery.registerItemVariants(this, resourceLocation);
                alreadyRegistered.put(registrationKey, new ModelResourceLocation(resourceLocation, "inventory"));
            }
            ModelResourceLocation resourceLocation = alreadyRegistered.get(registrationKey);
            metaItemsModels.put(metaItem, resourceLocation);
        }

        // Make some default models for meta prefix items without any materials associated
        if (metaItems.keySet().isEmpty()) {
            MaterialIconSet defaultIcon = MaterialIconSet.DULL;
            ResourceLocation defaultLocation = Objects.requireNonNull(OrePrefix.ingot.materialIconType)
                    .getItemModelPath(defaultIcon);
            ModelBakery.registerItemVariants(this, defaultLocation);
        }
    }

    @Override
    public int getItemStackLimit(@NotNull ItemStack stack) {
        if (prefix == null) return 64;
        return prefix.maxStackSize;
    }

    @Override
    public void onUpdate(@NotNull ItemStack itemStack, @NotNull World worldIn, @NotNull Entity entityIn, int itemSlot,
                         boolean isSelected) {
        super.onUpdate(itemStack, worldIn, entityIn, itemSlot, isSelected);
        if (metaItems.containsKey((short) itemStack.getItemDamage()) && entityIn instanceof EntityLivingBase entity) {
            if (entityIn.ticksExisted % 20 == 0) {
                if (prefix.heatDamageFunction == null) return;

                Material material = getMaterial(itemStack);
                if (material == null || !material.hasProperty(PropertyKey.BLAST)) return;

                float heatDamage = prefix.heatDamageFunction.apply(material.getBlastTemperature());
                ItemStack armor = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
                if (!armor.isEmpty() && armor.getItem() instanceof ArmorMetaItem<?>) {
                    ArmorMetaItem<?>.ArmorMetaValueItem metaValueItem = ((ArmorMetaItem<?>) armor.getItem())
                            .getItem(armor);
                    if (metaValueItem != null) heatDamage *= metaValueItem.getArmorLogic().getHeatResistance();
                }

                if (heatDamage > 0.0) {
                    entity.attackEntityFrom(DamageSources.getHeatDamage().setDamageBypassesArmor(), heatDamage);
                } else if (heatDamage < 0.0) {
                    entity.attackEntityFrom(DamageSources.getFrostDamage().setDamageBypassesArmor(), -heatDamage);
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@NotNull ItemStack itemStack, @Nullable World worldIn, @NotNull List<String> lines,
                               @NotNull ITooltipFlag tooltipFlag) {
        super.addInformation(itemStack, worldIn, lines, tooltipFlag);
        Material material = getMaterial(itemStack);
        if (prefix == null || material == null) return;
        addMaterialTooltip(lines, itemStack);
    }

    /**
     * For general use. Can return null if the stack metadata is an invalid material ID.
     * Requires the ItemStack's item to be a MetaPrefixItem.
     *
     * @return the material
     */
    @Nullable
    public Material getMaterial(@NotNull ItemStack stack) {
        return registry.getObjectById(stack.getMetadata());
    }

    /**
     * For registration use only. Assumes the metadata is a valid material ID.
     *
     * @return the material
     */
    @NotNull
    protected Material getMaterial(int metadata) {
        return Objects.requireNonNull(registry.getObjectById(metadata));
    }

    /**
     * Attempt to get a material from an ItemStack, whose item may not be a MetaPrefixItem.
     *
     * @return the material
     */
    @Nullable
    public static Material tryGetMaterial(@NotNull ItemStack itemStack) {
        if (itemStack.getItem() instanceof MetaPrefixItem metaPrefixItem) {
            return metaPrefixItem.getMaterial(itemStack);
        }
        return null;
    }

    public OrePrefix getOrePrefix() {
        return this.prefix;
    }

    @Override
    public int getItemBurnTime(@NotNull ItemStack itemStack) {
        Material material = getMaterial(itemStack);
        DustProperty property = material == null ? null : material.getProperty(PropertyKey.DUST);
        if (property != null) return (int) (property.getBurnTime() * prefix.getMaterialAmount(material) / GTValues.M);
        return super.getItemBurnTime(itemStack);
    }

    @Override
    public boolean isBeaconPayment(@NotNull ItemStack stack) {
        Material material = getMaterial(stack);
        if (material != null && this.prefix != OrePrefix.ingot && this.prefix != OrePrefix.gem) {
            MaterialToolProperty property = material.getProperty(PropertyKey.TOOL);
            return property != null && property.getToolHarvestLevel() >= 2;
        }
        return false;
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem itemEntity) {
        if (itemEntity.getEntityWorld().isRemote)
            return false;

        // Easy Cooling - Cooling hot items in water
        if (EASY_COOLING && hotMap.containsKey(this.prefix)) {
            boolean checkWater = true;
            BlockPos pos = itemEntity.getPosition();
            AxisAlignedBB boundingBox = new AxisAlignedBB(
                    itemEntity.posX - 2, itemEntity.posY - 2, itemEntity.posZ - 2,
                    itemEntity.posX + 2, itemEntity.posY + 2, itemEntity.posZ + 2);
            List<EntityPlayer> players1 = itemEntity.world.getEntitiesWithinAABB(EntityPlayer.class, boundingBox);

            Material mat = getMaterial(itemEntity.getItem());
            float heatDamage = prefix.heatDamageFunction.apply(mat.getBlastTemperature());

            for (int left = -1; left <= 1; left++) {
                for (int up = -1; up <= 1; up++) {
                    BlockPos checkPos = pos.add(left, 0, up);
                    IBlockState state = itemEntity.world.getBlockState(checkPos);
                    Block block = state.getBlock();
                    if (block != Blocks.WATER) {
                        checkWater = false;
                    }
                }
                if (!checkWater) {
                    break;
                }
            }

            if (checkWater) {
                ItemStack stack = itemEntity.getItem();
                int count = stack.getCount();
                ItemStack newStack = stack.copy();
                NBTTagCompound data = itemEntity.getEntityData();

                if (!data.hasKey("cooling")) {
                    itemEntity.getEntityData().setInteger("cooling", 0);
                }
                int cooling = data.getInteger("cooling");

                if (cooling < 200) {
                    if (cooling % 40 == 0) {
                        itemEntity.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.0F);
                        for (EntityPlayer player : players1) {
                            player.attackEntityFrom(DamageSources.getHeatDamage().setDamageBypassesArmor(), heatDamage);
                        }
                        data.setInteger("cooling", cooling + 1);
                    } else if (cooling % 10 == 0) {
                        itemEntity.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0F, 1.0F);
                        data.setInteger("cooling", cooling + 1);
                    } else {
                        data.setInteger("cooling", cooling + 1);
                    }
                } else {
                    itemEntity.getEntityData().removeTag("cooling");
                    itemEntity.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    itemEntity.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 1.0F, -2.0F);
                    itemEntity.playSound(SoundEvents.ENTITY_ITEM_BREAK, 1.0F, 1.0F);

                    ItemStack nuggetStack = OreDictUnifier.get(hotMap.get(prefix), mat, 9);
                    EntityItem nuggetEntity = new EntityItem(itemEntity.world, pos.getX(), pos.getY() + 0.25, pos.getZ(), nuggetStack);

                    if (count > 1) {
                        newStack.setCount(count - 1);
                        EntityItem overStack = new EntityItem(itemEntity.world, pos.getX(), pos.getY(), pos.getZ(), newStack);
                        itemEntity.world.spawnEntity(overStack);
                    }

                    List<EntityPlayer> players2 = itemEntity.world.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(2.0, 2.0, 2.0));
                    for (EntityPlayer player : players2) {
                        player.attackEntityFrom(DamageSources.getHeatDamage().setDamageBypassesArmor(), heatDamage + 0.5F);
                    }

                    itemEntity.world.spawnEntity(nuggetEntity);
                    itemEntity.setDead();
                }
                return false;
            }
        }

        // Easy Cleaning - Washing with water blocks
        if (EASY_CLEANING && purifyMap.containsKey(this.prefix)) {
            BlockPos pos = itemEntity.getPosition();
            IBlockState state = itemEntity.world.getBlockState(pos);
            Block block = state.getBlock();

            if (block == Blocks.WATER) {
                Material mat = getMaterial(itemEntity.getItem());
                int count = itemEntity.getItem().getCount();
                ItemStack replacementStack = OreDictUnifier.get(purifyMap.get(prefix), mat, 1);

                if (count > 1) {
                    ItemStack newStack = itemEntity.getItem().copy();
                    newStack.setCount(count - 1);
                    EntityItem overStack = new EntityItem(itemEntity.world, itemEntity.posX, itemEntity.posY, itemEntity.posZ, newStack);
                    itemEntity.world.spawnEntity(overStack);
                    overStack.setPickupDelay(10);
                }

                // Using a placeholder sound - you might want to replace this with the actual GTSoundEvents.BATH
                itemEntity.playSound(SoundEvents.BLOCK_WATER_AMBIENT, 0.5F, 1.0F);
                itemEntity.world.setBlockState(pos, Blocks.AIR.getDefaultState());
                itemEntity.setItem(replacementStack);
                return false;
            }
        }

        // Original cauldron cleaning behavior
        Material material = getMaterial(itemEntity.getItem());
        if (!purifyMap.containsKey(this.prefix))
            return false;

        BlockPos blockPos = new BlockPos(itemEntity);
        IBlockState blockState = itemEntity.getEntityWorld().getBlockState(blockPos);

        if (!(blockState.getBlock() instanceof BlockCauldron))
            return false;

        int waterLevel = blockState.getValue(BlockCauldron.LEVEL);
        if (waterLevel == 0)
            return false;

        itemEntity.getEntityWorld().setBlockState(blockPos,
                blockState.withProperty(BlockCauldron.LEVEL, waterLevel - 1));
        ItemStack replacementStack = OreDictUnifier.get(purifyMap.get(prefix), material,
                itemEntity.getItem().getCount());
        itemEntity.setItem(replacementStack);
        return false;
    }

    protected void addMaterialTooltip(@NotNull List<String> lines, @NotNull ItemStack itemStack) {
        if (this.prefix.tooltipFunc != null) {
            lines.addAll(this.prefix.tooltipFunc.apply(getMaterial(itemStack)));
        }
    }
}
