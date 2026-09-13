package gregtech.common.items.armor;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.items.armor.ArmorLogicSuite;
import gregtech.api.util.GTUtility;
import gregtech.api.util.input.KeyBind;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor.ArmorProperties;
import net.minecraftforge.event.entity.living.LivingFallEvent;

import org.jetbrains.annotations.NotNull;

/**
 * Boots with a piston in each heel: they assist with stepping up and sprinting, let the wearer jump higher by
 * compressing the pistons on landing, and soften the fall instead of negating it.
 *
 * @see gregtech.common.EventHandlers#onEntityLivingFallEvent(LivingFallEvent)
 */
public class PistonBoots extends ArmorLogicSuite implements IStepAssist {

    /**
     * Raw fall distance the pistons absorb. Note that {@link #getProperties} reports the damage remaining after
     * this absorption as the absorption ratio, which is what the fall event uses to damage the wearer.
     */
    private static final double FALL_ABSORPTION = 5.0;

    private float charge = 0.0F;

    public PistonBoots(EntityEquipmentSlot slot, int energyPerUse, long maxCapacity, int tier) {
        super(energyPerUse, maxCapacity, tier, slot);
    }

    @Override
    public void onArmorTick(@NotNull World world, @NotNull EntityPlayer player, @NotNull ItemStack itemStack) {
        IElectricItem container = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        NBTTagCompound data = GTUtility.getOrCreateNbtCompound(itemStack);

        updateStepHeight(player);

        if (container == null) {
            return;
        }

        if (container.canUse(energyPerUse / 100) && (player.onGround) && KeyBind.VANILLA_FORWARD.isKeyDown(player) &&
                (player.isSprinting())) {
            byte consumerTicks = data.getByte("consumerTicks");

            ++consumerTicks;
            if (consumerTicks >= 10) {
                consumerTicks = 0;
                container.discharge(energyPerUse / 100, container.getTier(), true, false, false);
            }
            data.setByte("consumerTicks", consumerTicks);
            player.moveRelative(0.0F, 0.0F, 0.25F, 0.10F);
        }

        if (!world.isRemote) {
            boolean onGround = !data.hasKey("onGround") || data.getBoolean("onGround");
            if (onGround && !player.onGround && KeyBind.VANILLA_JUMP.isKeyDown(player)) {
                container.discharge(energyPerUse / 100, container.getTier(), true, false, false);
            }
            if (player.onGround != onGround) {
                data.setBoolean("onGround", player.onGround);
            }
        } else {
            if (container.canUse(energyPerUse / 100) && player.onGround) {
                this.charge = 1.0F;
            }

            if (player.motionY >= 0.0D && this.charge > 0.0F && !player.isInWater()) {
                if (KeyBind.VANILLA_JUMP.isKeyDown(player)) {
                    if (this.charge == 1.0F) {
                        player.motionX *= 1.4D;
                        player.motionZ *= 1.4D;
                        world.playSound(player, player.posX, player.posY, player.posZ, SoundEvents.BLOCK_PISTON_EXTEND,
                                SoundCategory.PLAYERS, 1f, 1f);
                    }

                    player.motionY += this.charge * 0.13F;
                    this.charge = (float) (this.charge * 0.7D);
                } else if (this.charge < 1.0F) {
                    this.charge = 0.0F;
                }
            }
        }

        player.inventoryContainer.detectAndSendChanges();
    }

    /**
     * Softens the fall of the wearer with the pistons: the impact is absorbed, and only what is left of it is taken
     * as damage, reduced further by the level of Jump Boost the wearer has.
     * <p>
     * Unlike the other GT boots, the fall damage of the wearer is not negated entirely and the pistons are what
     * takes the hit, hence this is called from the fall handler instead of the vanilla armor calculation.
     */
    public void onLivingFall(@NotNull EntityPlayerMP player, @NotNull ItemStack armor, @NotNull LivingFallEvent event) {
        event.setCanceled(true);
        player.fallDistance = 0;

        // The absorption ratio is the impact which is left for the wearer after the pistons took the rest, see
        // getProperties.
        ArmorProperties properties = getProperties(player, armor, DamageSource.FALL, event.getDistance(),
                EntityEquipmentSlot.FEET);
        double remaining = properties.AbsorbRatio;

        // The harder the pistons have to work, the more energy they draw from the boots.
        if (remaining > 0) {
            damageArmor(player, armor, DamageSource.FALL, (int) remaining, EntityEquipmentSlot.FEET);
        }

        PotionEffect jumpBoost = player.getActivePotionEffect(MobEffects.JUMP_BOOST);
        float boost = jumpBoost == null ? 0.0F : (float) (jumpBoost.getAmplifier() + 1);
        int damage = MathHelper.ceil((remaining - boost) * event.getDamageMultiplier());

        if (damage > 0) {
            player.attackEntityFrom(DamageSource.FALL, damage);
            playFallSound(player);
        }
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, @NotNull ItemStack armor, DamageSource source,
                                         double damage, EntityEquipmentSlot equipmentSlot) {
        IElectricItem container = armor.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        int damageLimit = Integer.MAX_VALUE;
        if (source == DamageSource.FALL) {
            if (energyPerUse > 0 && container != null) {
                damageLimit = (int) Math.min(damageLimit, 25.0 * container.getCharge() / (energyPerUse * 10.0D));
            }
            return new ArmorProperties(10, (damage <= FALL_ABSORPTION) ? 0 : damage - FALL_ABSORPTION, damageLimit);
        }
        return new ArmorProperties(0, 0.0D, 0);
    }

    @Override
    public EntityEquipmentSlot getEquipmentSlot(ItemStack itemStack) {
        return SLOT;
    }

    @Override
    public void damageArmor(EntityLivingBase entity, @NotNull ItemStack itemStack, DamageSource source, int damage,
                            EntityEquipmentSlot equipmentSlot) {
        IElectricItem item = itemStack.getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (item != null) {
            item.discharge((long) energyPerUse / 10 * damage, item.getTier(), true, false, false);
        }
    }

    @Override
    public double getDamageAbsorption() {
        return 0;
    }

    @Override
    public @NotNull String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        return "gregtech:textures/armor/piston_boots.png";
    }

    private void playFallSound(@NotNull EntityPlayerMP player) {
        World world = player.getEntityWorld();
        int x = MathHelper.floor(player.posX);
        int y = MathHelper.floor(player.posY - 0.20000000298023224D);
        int z = MathHelper.floor(player.posZ);
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);

        if (state.getMaterial() != Material.AIR) {
            SoundType soundType = state.getBlock().getSoundType(state, world, pos, player);
            player.playSound(soundType.getFallSound(), soundType.getVolume() * 0.5F, soundType.getPitch() * 0.75F);
        }
    }
}
