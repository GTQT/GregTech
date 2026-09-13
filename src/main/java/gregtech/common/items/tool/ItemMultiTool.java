package gregtech.common.items.tool;

import gregtech.api.items.toolitem.IGTTool;
import gregtech.api.items.toolitem.IGTToolDefinition;
import gregtech.api.items.toolitem.ItemGTTool;
import gregtech.api.items.toolitem.ToolBuilder;

import gregtech.common.items.ToolItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A tool which can be switched to another mode by sneak-right-clicking, e.g. the Pocket Multitool.
 * <p>
 * All registered multi tools form a cycle in registration order, and each of them switches to the next one in that
 * cycle. The material and the accumulated damage are carried over to the switched tool, so the cycle behaves like a
 * single tool with several interchangeable heads.
 */
public class ItemMultiTool extends ItemGTTool {

    private static final List<IGTTool> MULTI_TOOLS = new ArrayList<>();

    protected ItemMultiTool(String domain, String id, int tier, IGTToolDefinition toolStats, SoundEvent sound,
                            boolean playSoundOnBlockDestroy, Set<String> toolClasses, String oreDict,
                            List<String> secondaryOreDicts, Supplier<ItemStack> markerItem) {
        super(domain, id, tier, toolStats, sound, playSoundOnBlockDestroy, toolClasses, oreDict, secondaryOreDicts,
                markerItem);
    }

    /**
     * Registers a multi tool, appending it to the switch cycle after all tools registered before it.
     */
    public static IGTTool register(@NotNull ToolBuilder<ItemMultiTool> builder) {
        IGTTool tool = ToolItems.register(builder);
        MULTI_TOOLS.add(tool);
        return tool;
    }

    @NotNull
    @Override
    public ActionResult<ItemStack> onItemRightClick(@NotNull World world, @NotNull EntityPlayer player,
                                                    @NotNull EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (player.isSneaking()) {
            if (!world.isRemote) {
                ItemStack nextTool = getNextTool().get(getToolMaterial(stack));
                nextTool.setItemDamage(stack.getItemDamage());
                player.setHeldItem(hand, nextTool);
            }
            return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
        }
        return super.onItemRightClick(world, player, hand);
    }

    private IGTTool getNextTool() {
        int index = MULTI_TOOLS.indexOf(this);
        return MULTI_TOOLS.get((index + 1) % MULTI_TOOLS.size());
    }

    public static class Builder extends ToolBuilder<ItemMultiTool> {

        @NotNull
        public static Builder of(@NotNull String domain, @NotNull String id) {
            return new Builder(domain, id);
        }

        public Builder(@NotNull String domain, @NotNull String id) {
            super(domain, id);
        }

        @Override
        public Supplier<ItemMultiTool> supply() {
            return () -> new ItemMultiTool(domain, id, tier, toolStats, sound, playSoundOnBlockDestroy, toolClasses,
                    oreDict, secondaryOreDicts, markerItem);
        }
    }
}
