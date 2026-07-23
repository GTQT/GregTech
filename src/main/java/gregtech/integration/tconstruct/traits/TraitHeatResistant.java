package gregtech.integration.tconstruct.traits;

import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;

import slimeknights.tconstruct.library.traits.AbstractTrait;

/**
 * Increases mining speed by {@value #NETHER_SPEED_BONUS} (30 %) while in the Nether (dimension -1).
 *
 * <p>
 * Assigned to materials with a blast furnace temperature ≥ 2500 K.
 */
public class TraitHeatResistant extends AbstractTrait {

    private static final float NETHER_SPEED_BONUS = 0.3f;

    public TraitHeatResistant() {
        super("gregtech_heat_resistant", 0xFF6600);
    }

    @Override
    public void miningSpeed(ItemStack tool, PlayerEvent.BreakSpeed event) {
        if (event.getEntityPlayer().world.provider.getDimension() == -1) {
            event.setNewSpeed(event.getNewSpeed() * (1.0f + NETHER_SPEED_BONUS));
        }
    }
}
