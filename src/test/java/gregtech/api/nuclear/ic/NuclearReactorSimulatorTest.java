package gregtech.api.nuclear.ic;

import gregtech.common.items.behaviors.nuclear.ReactorPlatingBehavior;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NuclearReactorSimulatorTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void growingTheGridKeepsComponentsAndReactorState() {
        NuclearReactorSimulator simulator = new NuclearReactorSimulator(3, 6);
        assertTrue(simulator.placeComponent(2, 5, new ItemStack(new Item())));
        simulator.setHeat(1500);
        simulator.getListToTransfer().add(new ItemStack(new Item()));
        simulator.setTransOut(true);

        simulator.resize(9, 6);

        assertEquals(9, simulator.getGridWidth());
        assertEquals(6, simulator.getGridHeight());
        assertEquals(1, simulator.getComponent(2, 5).getCount(), "components must keep their coordinate");
        assertEquals(1500, simulator.getCurrentHeat(), "heat must survive a resize");
        assertTrue(simulator.isTransOut(), "the pending transfer queue must survive a resize");
        assertEquals(1, simulator.getListToTransfer().size());
    }

    @Test
    void shrinkingTheGridKeepsOnlyTheComponentsThatStillFit() {
        NuclearReactorSimulator simulator = new NuclearReactorSimulator(9, 6);
        assertTrue(simulator.placeComponent(0, 0, new ItemStack(new Item())));
        assertTrue(simulator.placeComponent(8, 5, new ItemStack(new Item())));

        simulator.resize(3, 6);

        assertEquals(3, simulator.getGridWidth());
        assertFalse(simulator.getComponent(0, 0).isEmpty());
        assertFalse(simulator.isValidPosition(3, 0), "columns outside of the new width must no longer exist");
        assertTrue(simulator.getComponent(8, 5).isEmpty());
    }

    @Test
    void resizingToTheSameSizeChangesNothing() {
        NuclearReactorSimulator simulator = new NuclearReactorSimulator(4, 6);
        assertTrue(simulator.placeComponent(3, 0, new ItemStack(new Item())));

        simulator.resize(4, 6);

        assertFalse(simulator.getComponent(3, 0).isEmpty());
    }

    @Test
    void reactorPlatingNeverConsumesDurability() {
        // The registered plating items only have a durability of 1, so the simulator must never damage them.
        assertFalse(new ReactorPlatingBehavior(1, null, 1000, 0.1f).consumesDurability());
    }

    @Test
    void oneStepCreditsTwentyTicksOfFuelRodOutput() {
        NuclearReactorSimulator simulator = new NuclearReactorSimulator(3, 6);
        NBTTagCompound state = new NBTTagCompound();
        state.setInteger("GridWidth", 3);
        state.setInteger("GridHeight", 6);
        state.setLong("CurrentOutput", 512); // one uranium fuel rod, in EU/t

        simulator.readFromNBT(state);

        assertEquals(512, simulator.getCurrentOutput());
        assertEquals(512 * NuclearReactorSimulator.TICKS_PER_STEP, simulator.getEnergyPerStep());
    }
}
