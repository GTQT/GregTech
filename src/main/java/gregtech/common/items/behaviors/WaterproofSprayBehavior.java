package gregtech.common.items.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GradientUtil;
import gregtech.common.items.MetaItems;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * The waterproof spray can: a spray can of {@value #CAPACITY} uses that makes the machines it is sprayed on immune to
 * the weather and terrain explosions of {@code MetaTileEntity#checkWeatherOrTerrainExplosion}.
 * <p>
 * It reuses {@link AbstractUsableBehaviour} for the use count, so the remaining uses are stored under the very same
 * {@code GT.UsesLeft} tag as the GT spray cans, and an empty can is replaced by {@link MetaItems#SPRAY_EMPTY}.
 *
 * @see gregtech.common.EventHandlers#onWaterproofSprayPlace(net.minecraftforge.event.world.BlockEvent.EntityPlaceEvent)
 */
public class WaterproofSprayBehavior extends AbstractUsableBehaviour implements IItemDurabilityManager {

    public static final int CAPACITY = 300;

    /** The color of the paint, matching the waterproof paint fluid. */
    public static final int SPRAY_COLOR = 0x2E6FA3;

    @NotNull
    private final Pair<Color, Color> durabilityBarColors;

    public WaterproofSprayBehavior() {
        super(CAPACITY);
        this.durabilityBarColors = GradientUtil.getGradient(SPRAY_COLOR, 10);
    }

    /** @return the behavior of the given stack, or {@code null} when it is not a waterproof spray can. */
    @Nullable
    public static WaterproofSprayBehavior getBehavior(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof MetaItem)) {
            return null;
        }
        MetaItem<?> metaItem = (MetaItem<?>) stack.getItem();
        for (IItemBehaviour behavior : metaItem.getBehaviours(stack)) {
            if (behavior instanceof WaterproofSprayBehavior) {
                return (WaterproofSprayBehavior) behavior;
            }
        }
        return null;
    }

    /** @return the empty can left behind when the paint is used up. */
    @NotNull
    public static ItemStack emptyStack() {
        return MetaItems.SPRAY_EMPTY == null ? ItemStack.EMPTY : MetaItems.SPRAY_EMPTY.getStackForm();
    }

    /** Uses up one charge of the can. */
    public void consume(@NotNull EntityPlayer player, @NotNull EnumHand hand, @NotNull ItemStack stack) {
        useItemDurability(player, hand, stack, emptyStack());
    }

    /**
     * Spraying a machine makes it waterproof. This runs before the block is activated, so returning success also keeps
     * the spray can from opening the machine UI on the same click.
     */
    @NotNull
    @Override
    public EnumActionResult onItemUseFirst(@NotNull EntityPlayer player, @NotNull World world, @NotNull BlockPos pos,
                                           @NotNull EnumFacing side, float hitX, float hitY, float hitZ,
                                           @NotNull EnumHand hand) {
        if (getMachine(world, pos) == null) {
            return EnumActionResult.PASS;
        }
        if (!world.isRemote) {
            if (!applyWaterproofAt(world, pos)) {
                // Already waterproof, so let the machine handle the click as usual.
                return EnumActionResult.PASS;
            }
            consume(player, hand, player.getHeldItem(hand));
            playSpraySound(player, world);
        }
        return EnumActionResult.SUCCESS;
    }

    /**
     * Makes the machine at the given position waterproof.
     *
     * @return whether the machine was actually waterproofed, which is not the case for clients, machines which are
     *         already waterproof and blocks which are no machines at all
     */
    public static boolean applyWaterproofAt(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return false;
        }
        MetaTileEntity mte = getMachine(world, pos);
        if (mte == null || mte.isWaterproof()) {
            return false;
        }
        mte.setWaterproof(true);
        return true;
    }

    /** @return the GT machine at the given position, or {@code null} when there is none. */
    @Nullable
    public static MetaTileEntity getMachine(@NotNull World world, @NotNull BlockPos pos) {
        TileEntity tileEntity = world.getTileEntity(pos);
        if (tileEntity instanceof IGregTechTileEntity) {
            return ((IGregTechTileEntity) tileEntity).getMetaTileEntity();
        }
        return null;
    }

    /** Plays the spray sound for everyone nearby. */
    public static void playSpraySound(@NotNull EntityPlayer player, @NotNull World world) {
        world.playSound(null, player.posX, player.posY, player.posZ, GTSoundEvents.SPRAY_CAN_TOOL,
                SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    @Override
    public void addInformation(@NotNull ItemStack itemStack, @NotNull List<String> lines) {
        lines.add(I18n.format("gregtech.tooltip.waterproof_spray_can.right_click"));
        lines.add(I18n.format("gregtech.tooltip.waterproof_spray_can.offhand"));
        lines.add(I18n.format("gregtech.tooltip.waterproof_spray_can.uses", getUsesLeft(itemStack)));
    }

    @Override
    public double getDurabilityForDisplay(@NotNull ItemStack itemStack) {
        return (double) getUsesLeft(itemStack) / (double) totalUses;
    }

    @NotNull
    @Override
    public Pair<Color, Color> getDurabilityColorsForDisplay(@NotNull ItemStack itemStack) {
        return durabilityBarColors;
    }

    @Override
    public boolean doDamagedStateColors(@NotNull ItemStack itemStack) {
        return false;
    }
}
