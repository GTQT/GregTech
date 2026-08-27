package gregtech.common.item.behaviors;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoolantCellBehaviorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void coolantCellUsesItsExistingOutputtableDurabilityLifecycle() {
        CoolantCellBehavior behavior = new CoolantCellBehavior(3, null, 1000, 300);
        ItemStack cell = new ItemStack(new Item());

        assertTrue(behavior.applyDamage(cell, 1));
        assertTrue(behavior.applyDamage(cell, 1));
        assertEquals(2, behavior.getPartDamage(cell));

        assertFalse(behavior.applyDamage(cell, 1));
        assertFalse(cell.isEmpty());
        assertEquals(2, behavior.getPartDamage(cell));
    }

    @Test
    void reactorPlatingUsesTheGenericNuclearComponentDurabilityLifecycle() {
        ReactorPlatingBehavior behavior = new ReactorPlatingBehavior(3, null, 10000, 0.45f);
        ItemStack plating = new ItemStack(new Item());

        assertTrue(behavior.applyDamage(plating, 1));
        assertTrue(behavior.applyDamage(plating, 1));
        assertEquals(2, behavior.getPartDamage(plating));

        assertFalse(behavior.applyDamage(plating, 1));
        assertTrue(plating.isEmpty());
    }
}
