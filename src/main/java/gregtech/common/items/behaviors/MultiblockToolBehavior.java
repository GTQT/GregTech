package gregtech.common.items.behaviors;

import gregtech.api.items.gui.ItemUIFactory;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.mui.factory.MetaItemGuiFactory;
import gregtech.api.util.GTUtility;
import gregtech.common.items.MetaItems;
import gregtech.common.items.behaviors.multiblock.MultiblockToolMode;
import gregtech.common.items.behaviors.multiblock.mover.MoverSessionManager;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

import com.cleanroommc.modularui.factory.HandGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** One item component dispatching preview/build, removal and transactional movement modes. */
public final class MultiblockToolBehavior implements IItemBehaviour, ItemUIFactory {
    private final StructureProjectorBehavior projector = new StructureProjectorBehavior();
    private final MultiblockRemovalBehavior remover = new MultiblockRemovalBehavior();
    private final MultiblockMoverBehavior mover = new MultiblockMoverBehavior();

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos,
                                           EnumFacing side, float hitX, float hitY, float hitZ,
                                           EnumHand hand) {
        switch (MultiblockToolMode.get(player.getHeldItem(hand))) {
            case REMOVE:
                return remover.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand);
            case MOVE:
                return mover.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand);
            default:
                return projector.onItemUseFirst(player, world, pos, side, hitX, hitY, hitZ, hand);
        }
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        switch (MultiblockToolMode.get(stack)) {
            case MOVE:
                return mover.onItemRightClick(world, player, hand);
            case PROJECT:
                return projector.onItemRightClick(world, player, hand);
            default:
                return pass(stack);
        }
    }

    @Override
    public void addPropertyOverride(@NotNull Item item) {
        item.addPropertyOverride(GTUtility.gregtechId("multiblock_tool_mode"),
                (stack, world, entity) -> MultiblockToolMode.get(stack).getId());
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        MultiblockToolMode mode = MultiblockToolMode.get(stack);
        lines.add(TextFormatting.AQUA + I18n.format("gregtech.multiblock_tool.current_mode",
                I18n.format(mode.getTranslationKey())));
        lines.add(TextFormatting.GRAY + I18n.format("gregtech.multiblock_tool.switch_hint"));
        switch (mode) {
            case REMOVE:
                remover.addInformation(stack, lines);
                break;
            case MOVE:
                mover.addInformation(stack, lines);
                break;
            default:
                projector.addInformation(stack, lines);
        }
    }

    @Override
    public ModularPanel buildUI(HandGuiData guiData, PanelSyncManager guiSyncManager,
                                UISettings settings) {
        return projector.buildUI(guiData, guiSyncManager, settings);
    }

    public static boolean isMultiblockTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return MetaItems.MULTIBLOCK_TOOL != null && MetaItems.MULTIBLOCK_TOOL.isItemEqual(stack)
                || MetaItems.MULTIBLOCK_BUILDER != null && MetaItems.MULTIBLOCK_BUILDER.isItemEqual(stack)
                || MetaItems.MULTIBLOCK_REMOVER != null && MetaItems.MULTIBLOCK_REMOVER.isItemEqual(stack);
    }

    public static void cycleMode(EntityPlayerMP player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!isMultiblockTool(stack)) return;
        MultiblockToolMode oldMode = MultiblockToolMode.get(stack);
        if (oldMode == MultiblockToolMode.MOVE && MoverSessionManager.INSTANCE.hasSession(player)) {
            MoverSessionManager.INSTANCE.cancel(player, stack, true, null);
        }
        MultiblockToolMode newMode = oldMode.next();
        MultiblockToolMode.set(stack, newMode);
        player.inventory.markDirty();
        player.getServerWorld().playSound(null, player.posX, player.posY, player.posZ,
                GTSoundEvents.CLICK, SoundCategory.PLAYERS, 0.7F, 1.0F + newMode.getId() * 0.08F);
        player.sendStatusMessage(new TextComponentTranslation(
                "gregtech.multiblock_tool.mode_changed",
                new TextComponentTranslation(newMode.getTranslationKey())), true);
    }
}
