package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockInputRollbackTest {

    private static RecipeMap<SimpleRecipeBuilder> WATER_REDSTONE_MAP;

    @BeforeAll
    static void init() {
        MultiblockRecipeLockAndInvalidationTest.bootstrap();
        WATER_REDSTONE_MAP = new RecipeMap<>(
                "test_input_rollback_water_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        Recipe recipe = new Recipe(
                Arrays.asList(
                        new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1)),
                        new GTRecipeItemInput(new ItemStack(Items.REDSTONE, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000))),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 30L, false, new RecipePropertyStorageImpl(), WATER_REDSTONE_MAP.getPrimaryRecipeCategory());
        WATER_REDSTONE_MAP.compileRecipe(recipe);
    }

    private static MultiblockRecipeLockAndInvalidationTest.TestController createSameBusRedstoneController(
            IFluidTank waterTank, String suffix) {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(WATER_REDSTONE_MAP) {

                    @Override
                    public IMultipleTankHandler getInputFluidInventory() {
                        return new FluidTankList(false, waterTank);
                    }

                    @Override
                    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                        IItemHandlerModifiable inv = getInputInventory();
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack s = inv.getStackInSlot(i);
                            if (!s.isEmpty() && s.getItem() == Items.REDSTONE) {
                                if (consumeIfSuccess) {
                                    return inv.extractItem(i, 1, false).getCount() == 1;
                                }
                                return true;
                            }
                        }
                        return false;
                    }
                };
        ctrl.getTestLogic().setAllowOverclocking(false);
        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_in_" + suffix), 2, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_out_" + suffix), 2, true);
        ctrl.addPart(inBus);
        ctrl.addPart(outBus);
        return ctrl;
    }

    @Test
    void forgeFluidTankWithCanFillFalseAndTruePreservesWaterAndRunsWhenSupplied() {
        for (boolean canFill : new boolean[] { false, true }) {
            FluidTank waterTank = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
            waterTank.setCanFill(canFill);
            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    createSameBusRedstoneController(waterTank, "forge_" + canFill);
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            long initialEnergy = ctrl.getEnergyContainer().getEnergyStored();

            IItemHandlerModifiable inInv = ctrl.getAbilities(
                    gregtech.api.metatileentity.multiblock.MultiblockAbility.IMPORT_ITEMS).get(0);
            inInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            inInv.setStackInSlot(1, new ItemStack(Items.REDSTONE, 1));

            for (int t = 0; t < 8; t++) {
                logic.updateWorkable();
            }
            assertEquals(1, inInv.getStackInSlot(0).getCount());
            assertEquals(1, inInv.getStackInSlot(1).getCount());
            assertEquals(1000, waterTank.getFluidAmount());
            assertEquals(0, logic.getProgressPercent());
            assertEquals(0, ctrl.getOutputInventory().getStackInSlot(0).getCount());
            assertEquals(initialEnergy, ctrl.getEnergyContainer().getEnergyStored());

            // Supplying the 2nd Redstone while keeping canFill unchanged completes 1 Diamond and spends 120 EU
            inInv.setStackInSlot(1, new ItemStack(Items.REDSTONE, 2));
            ctrl.addNotifiedInput(inInv);
            for (int t = 0; t < 6; t++) {
                logic.updateWorkable();
            }
            assertEquals(0, inInv.getStackInSlot(0).getCount());
            assertEquals(0, inInv.getStackInSlot(1).getCount());
            assertEquals(0, waterTank.getFluidAmount());
            assertEquals(1, ctrl.getOutputInventory().getStackInSlot(0).getCount());
            assertEquals(initialEnergy - 120L, ctrl.getEnergyContainer().getEnergyStored());
        }
    }

    @Test
    void customDrainOnlyIFluidTankUnderSchedulerPreservesWater() {
        IFluidTank customDrainOnly = new IFluidTank() {

            private FluidStack stack = new FluidStack(FluidRegistry.WATER, 1000);

            @Override
            public @Nullable FluidStack getFluid() {
                return stack;
            }

            @Override
            public int getFluidAmount() {
                return stack == null ? 0 : stack.amount;
            }

            @Override
            public int getCapacity() {
                return 1000;
            }

            @Override
            public FluidTankInfo getInfo() {
                return new FluidTankInfo(stack, 1000);
            }

            @Override
            public int fill(FluidStack resource, boolean doFill) {
                return 0;
            }

            @Override
            public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
                if (stack == null || maxDrain <= 0) return null;
                int drained = Math.min(maxDrain, stack.amount);
                FluidStack out = new FluidStack(stack, drained);
                if (doDrain) {
                    stack.amount -= drained;
                    if (stack.amount <= 0) stack = null;
                }
                return out;
            }
        };

        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                createSameBusRedstoneController(customDrainOnly, "sched_ext");
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setThreadLimit(2);
        IItemHandlerModifiable inInv = ctrl.getAbilities(
                gregtech.api.metatileentity.multiblock.MultiblockAbility.IMPORT_ITEMS).get(0);
        inInv.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        inInv.setStackInSlot(1, new ItemStack(Items.REDSTONE, 1));

        for (int t = 0; t < 8; t++) {
            logic.updateWorkable();
        }
        assertEquals(1, inInv.getStackInSlot(0).getCount());
        assertEquals(1, inInv.getStackInSlot(1).getCount());
        assertEquals(1000, customDrainOnly.getFluidAmount());

        inInv.setStackInSlot(1, new ItemStack(Items.REDSTONE, 2));
        ctrl.addNotifiedInput(inInv);
        for (int t = 0; t < 6; t++) {
            logic.updateWorkable();
        }
        assertEquals(0, customDrainOnly.getFluidAmount());
        assertEquals(1, ctrl.getOutputInventory().getStackInSlot(0).getCount());
    }

    @Test
    void midCommitBlockedFluidRollbackRetainsRemainderAcrossNbtAndRetriesFill() {
        FluidTank dynamicTank = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
        AtomicBoolean blockOnCommit = new AtomicBoolean(true);
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(WATER_REDSTONE_MAP) {

                    @Override
                    public IMultipleTankHandler getInputFluidInventory() {
                        return new FluidTankList(false, dynamicTank);
                    }

                    @Override
                    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                        if (consumeIfSuccess && blockOnCommit.get()) {
                            dynamicTank.setCanFill(false);
                            return false;
                        }
                        return true;
                    }
                };
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setAllowOverclocking(false);
        MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_dyn_in"), 2, false);
        MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_dyn_out"), 2, true);
        ctrl.addPart(inBus);
        ctrl.addPart(outBus);

        inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        inBus.getImportItems().setStackInSlot(1, new ItemStack(Items.REDSTONE, 1));
        logic.updateWorkable();
        assertEquals(0, dynamicTank.getFluidAmount());

        // Re-enabling refill flushes retained fluid back into dynamicTank before next search
        dynamicTank.setCanFill(true);
        logic.flushPendingRollbacks();
        assertEquals(1000, dynamicTank.getFluidAmount());

        // Trigger blocked rollback again, round-trip NBT while tank stays canFill=false, and verify retained fluid still runs recipe
        dynamicTank.setCanFill(true);
        ctrl.addNotifiedInput(inBus.getImportItems());
        logic.updateWorkable();
        assertEquals(0, dynamicTank.getFluidAmount());

        NBTTagCompound saved = logic.serializeNBT();
        logic.deserializeNBT(saved);
        blockOnCommit.set(false);
        ctrl.addNotifiedInput(inBus.getImportItems());
        for (int t = 0; t < 6; t++) {
            logic.updateWorkable();
        }
        assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
        assertEquals(0, dynamicTank.getFluidAmount());
    }
}
