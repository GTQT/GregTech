package gregtech.api.capability.impl;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputList;
import gregtech.api.recipes.ingredients.GTRecipeFluidInput;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;
import gregtech.api.recipes.ingredients.nbtmatch.NBTCondition;
import gregtech.api.recipes.ingredients.nbtmatch.NBTMatcher;
import gregtech.api.recipes.ingredients.nbtmatch.NBTTagType;
import gregtech.api.recipes.properties.RecipePropertyStorageImpl;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityItemBus;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraft.init.Items;
import net.minecraft.item.EnumDyeColor;
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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockInputRollbackTest {

    private static RecipeMap<SimpleRecipeBuilder> WATER_REDSTONE_MAP;
    private static RecipeMap<SimpleRecipeBuilder> IRON_WATER_MAP;
    private static RecipeMap<SimpleRecipeBuilder> DUAL_WATER_NBT_MAP;
    private static RecipeMap<SimpleRecipeBuilder> RED_BLUE_WATER_MAP;

    @BeforeAll
    static void init() {
        MultiblockRecipeLockAndInvalidationTest.bootstrap();
        WATER_REDSTONE_MAP = new RecipeMap<>(
                "test_input_rollback_water_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 1, 1);
        WATER_REDSTONE_MAP.compileRecipe(new Recipe(
                Arrays.asList(
                        new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1)),
                        new GTRecipeItemInput(new ItemStack(Items.REDSTONE, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000))),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 30L, false, new RecipePropertyStorageImpl(), WATER_REDSTONE_MAP.getPrimaryRecipeCategory()));

        IRON_WATER_MAP = new RecipeMap<>(
                "test_input_rollback_iron_water_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 3, 1);
        IRON_WATER_MAP.compileRecipe(new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000))),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 30L, false, new RecipePropertyStorageImpl(), IRON_WATER_MAP.getPrimaryRecipeCategory()));

        DUAL_WATER_NBT_MAP = new RecipeMap<>(
                "test_input_rollback_dual_water_nbt_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 3, 1);
        GTRecipeFluidInput anyWater = new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000));
        anyWater.setNBTMatchingCondition(NBTMatcher.ANY, NBTCondition.ANY);
        FluidStack cleanWaterTemplate = cleanWater(1000);
        GTRecipeFluidInput cleanWaterInput = new GTRecipeFluidInput(cleanWaterTemplate);
        cleanWaterInput.setNBTMatchingCondition(
                NBTMatcher.RECURSIVE_EQUAL_TO,
                NBTCondition.create(NBTTagType.BOOLEAN, "clean", true));
        DUAL_WATER_NBT_MAP.compileRecipe(new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Arrays.asList(anyWater, cleanWaterInput),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 30L, false, new RecipePropertyStorageImpl(), DUAL_WATER_NBT_MAP.getPrimaryRecipeCategory()));

        RED_BLUE_WATER_MAP = new RecipeMap<>(
                "test_input_rollback_red_blue_map", new SimpleRecipeBuilder(), r -> null, 2, 2, 2, 1);
        RED_BLUE_WATER_MAP.compileRecipe(new Recipe(
                Arrays.asList(
                        new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 1)),
                        new GTRecipeItemInput(new ItemStack(Items.REDSTONE, 1))),
                Collections.singletonList(new ItemStack(Items.DIAMOND, 1)),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000))),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 30L, false, new RecipePropertyStorageImpl(), RED_BLUE_WATER_MAP.getPrimaryRecipeCategory()));
        RED_BLUE_WATER_MAP.compileRecipe(new Recipe(
                Collections.singletonList(new GTRecipeItemInput(new ItemStack(Items.COAL, 1))),
                Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 1)),
                ChancedOutputList.empty(),
                Collections.singletonList(new GTRecipeFluidInput(new FluidStack(FluidRegistry.WATER, 1000))),
                Collections.emptyList(), ChancedOutputList.empty(), Collections.emptyList(),
                4, 32L, false, new RecipePropertyStorageImpl(), RED_BLUE_WATER_MAP.getPrimaryRecipeCategory()));
    }

    private static FluidStack cleanWater(int amount) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("clean", true);
        return new FluidStack(FluidRegistry.WATER, amount, tag);
    }

    private static final class TestPaintableFluidHatchPart extends MetaTileEntityMultiblockPart
            implements IMultiblockAbilityPart<IFluidTank> {

        final IFluidTank tank;

        TestPaintableFluidHatchPart(String id, IFluidTank tank, @Nullable EnumDyeColor color) {
            super(new ResourceLocation("gregtech", id), 1);
            this.tank = tank;
            if (color != null) {
                setPaintingColor(color.getColorValue());
            }
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestPaintableFluidHatchPart(metaTileEntityId.getPath(), tank, null);
        }

        @Override
        public MultiblockAbility<IFluidTank> getAbility() {
            return MultiblockAbility.IMPORT_FLUIDS;
        }

        @Override
        public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
            abilityInstances.add(tank);
        }
    }

    private static final class SequentialDrainOnlyTank implements IFluidTank {

        private final Deque<FluidStack> queue = new ArrayDeque<>();

        SequentialDrainOnlyTank(FluidStack... initial) {
            for (FluidStack f : initial) {
                if (f != null && f.amount > 0) queue.addLast(f.copy());
            }
        }

        @Override
        public @Nullable FluidStack getFluid() {
            return queue.peekFirst();
        }

        @Override
        public int getFluidAmount() {
            FluidStack head = queue.peekFirst();
            return head == null ? 0 : head.amount;
        }

        @Override
        public int getCapacity() {
            return 1000;
        }

        @Override
        public FluidTankInfo getInfo() {
            return new FluidTankInfo(getFluid(), getCapacity());
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            return 0;
        }

        @Override
        public @Nullable FluidStack drain(int maxDrain, boolean doDrain) {
            FluidStack head = queue.peekFirst();
            if (head == null || maxDrain <= 0) return null;
            int drained = Math.min(maxDrain, head.amount);
            FluidStack out = new FluidStack(head, drained);
            if (doDrain) {
                head.amount -= drained;
                if (head.amount <= 0) {
                    queue.pollFirst();
                }
            }
            return out;
        }
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

            IItemHandlerModifiable inInv = ctrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
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
        SequentialDrainOnlyTank customDrainOnly = new SequentialDrainOnlyTank(new FluidStack(FluidRegistry.WATER, 1000));
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                createSameBusRedstoneController(customDrainOnly, "sched_ext");
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
        logic.setThreadLimit(2);
        IItemHandlerModifiable inInv = ctrl.getAbilities(MultiblockAbility.IMPORT_ITEMS).get(0);
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

        dynamicTank.setCanFill(true);
        logic.flushPendingRollbacks();
        assertEquals(1000, dynamicTank.getFluidAmount());

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

    @Test
    void drainOnlyAndFillOnlyTanksPlusSpecificNbtWaterSelectDrainableMatchingTanks() {
        for (int threads : new int[] { 1, 2 }) {
            // 1. Tank 0 drain-only (canFill=false), Tank 1 fill-only (canDrain=false)
            FluidTank drainOnly = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
            drainOnly.setCanFill(false);
            FluidTank fillOnly = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
            fillOnly.setCanDrain(false);

            MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(IRON_WATER_MAP) {

                        @Override
                        public IMultipleTankHandler getInputFluidInventory() {
                            return new FluidTankList(true, drainOnly, fillOnly);
                        }
                    };
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
            logic.setAllowOverclocking(false);
            logic.setThreadLimit(threads);
            MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_dofo_in_" + threads), 1, false);
            MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_dofo_out_" + threads), 1, true);
            ctrl.addPart(inBus);
            ctrl.addPart(outBus);
            long startEnergy = ctrl.getEnergyContainer().getEnergyStored();

            inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            for (int t = 0; t < 6; t++) logic.updateWorkable();
            assertEquals(0, drainOnly.getFluidAmount());
            assertEquals(1000, fillOnly.getFluidAmount());
            assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
            assertEquals(startEnergy - 120L, ctrl.getEnergyContainer().getEnergyStored());

            // 2. [any Water, clean Water] with Tank 0 = drain-only plain Water, Tank 1 = refillable clean Water
            FluidTank plainDrainOnly = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
            plainDrainOnly.setCanFill(false);
            FluidTank cleanRefillable = new FluidTank(cleanWater(1000), 1000);

            MultiblockRecipeLockAndInvalidationTest.TestController nbtCtrl =
                    new MultiblockRecipeLockAndInvalidationTest.TestController(DUAL_WATER_NBT_MAP) {

                        @Override
                        public IMultipleTankHandler getInputFluidInventory() {
                            return new FluidTankList(true, plainDrainOnly, cleanRefillable);
                        }
                    };
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic nbtLogic = nbtCtrl.getTestLogic();
            nbtLogic.setAllowOverclocking(false);
            nbtLogic.setThreadLimit(threads);
            MetaTileEntityItemBus nbtIn = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_nbt_in_" + threads), 1, false);
            MetaTileEntityItemBus nbtOut = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_nbt_out_" + threads), 1, true);
            nbtCtrl.addPart(nbtIn);
            nbtCtrl.addPart(nbtOut);

            nbtIn.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            for (int t = 0; t < 6; t++) nbtLogic.updateWorkable();
            assertEquals(0, plainDrainOnly.getFluidAmount());
            assertEquals(0, cleanRefillable.getFluidAmount());
            assertEquals(1, nbtOut.getExportItems().getStackInSlot(0).getCount());
        }
    }

    @Test
    void switchingDrainOnlyFluidTankNeverConsumesLavaForWaterRecipe() {
        for (int threads : new int[] { 1, 2 }) {
            for (boolean bypassControllerFluidView : new boolean[] { false, true }) {
                SequentialDrainOnlyTank seqTank = new SequentialDrainOnlyTank(
                        new FluidStack(FluidRegistry.WATER, 1000),
                        new FluidStack(FluidRegistry.LAVA, 1000));
                FluidTank extraWater = new FluidTank(1000);
                extraWater.setCanFill(false);

                MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                        new MultiblockRecipeLockAndInvalidationTest.TestController(IRON_WATER_MAP) {

                            @Override
                            public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                                IMultipleTankHandler fluids = bypassControllerFluidView ?
                                        new FluidTankList(true, seqTank, extraWater) : getInputFluidInventory();
                                FluidStack req = new FluidStack(FluidRegistry.WATER, 1000);
                                FluidStack sim = fluids.drain(req, false);
                                if (sim == null || sim.amount < 1000) return false;
                                if (consumeIfSuccess) {
                                    FluidStack drained = fluids.drain(req, true);
                                    return drained != null && drained.amount == 1000;
                                }
                                return true;
                            }
                        };
                MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
                logic.setAllowOverclocking(false);
                logic.setThreadLimit(threads);
                String tag = threads + "_" + bypassControllerFluidView;
                MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                        new ResourceLocation("gregtech", "test_rb_seq_in_" + tag), 1, false);
                MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                        new ResourceLocation("gregtech", "test_rb_seq_out_" + tag), 1, true);
                ctrl.addPart(inBus);
                ctrl.addPart(outBus);
                ctrl.addPart(new TestPaintableFluidHatchPart("test_rb_seq_h0_" + tag, seqTank, null));
                ctrl.addPart(new TestPaintableFluidHatchPart("test_rb_seq_h1_" + tag, extraWater, null));
                long startEnergy = ctrl.getEnergyContainer().getEnergyStored();

                inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
                for (int t = 0; t < 8; t++) {
                    logic.updateWorkable();
                }
                if (!bypassControllerFluidView) {
                    assertEquals(FluidRegistry.WATER, seqTank.getFluid().getFluid());
                } else {
                    assertEquals(FluidRegistry.LAVA, seqTank.getFluid().getFluid());
                }
                assertEquals(1000, seqTank.getFluidAmount());
                assertEquals(1, inBus.getImportItems().getStackInSlot(0).getCount());
                assertEquals(0, outBus.getExportItems().getStackInSlot(0).getCount());
                assertEquals(startEnergy, ctrl.getEnergyContainer().getEnergyStored());

                // Supplying the 2nd 1,000 mB Water completes 1 Diamond while leaving all 1,000 mB Lava untouched
                extraWater.setFluid(new FluidStack(FluidRegistry.WATER, 1000));
                ctrl.addNotifiedInput(inBus.getImportItems());
                for (int t = 0; t < 6; t++) {
                    logic.updateWorkable();
                }
                assertEquals(FluidRegistry.LAVA, seqTank.getFluid().getFluid());
                assertEquals(1000, seqTank.getFluidAmount());
                assertEquals(0, inBus.getImportItems().getStackInSlot(0).getCount());
                assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
                assertEquals(startEnergy - 120L, ctrl.getEnergyContainer().getEnergyStored());
            }
        }
    }

    @Test
    void sameDrainOnlyTankSharedByMainAndAuxiliaryPreservesPhysicalWaterWhenInsufficient() {
        for (int threads : new int[] { 1, 2 }) {
            for (boolean bypassControllerFluidView : new boolean[] { false, true }) {
                FluidTank drainOnly = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 2000);
                drainOnly.setCanFill(false);
                MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                        new MultiblockRecipeLockAndInvalidationTest.TestController(IRON_WATER_MAP) {

                            @Override
                            public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                                IMultipleTankHandler fluids = bypassControllerFluidView ?
                                        new FluidTankList(false, drainOnly) : getInputFluidInventory();
                                FluidStack req = new FluidStack(FluidRegistry.WATER, 250);
                                FluidStack sim = fluids.drain(req, false);
                                if (sim == null || sim.amount < 250) return false;
                                if (consumeIfSuccess) {
                                    FluidStack drained = fluids.drain(req, true);
                                    return drained != null && drained.amount == 250;
                                }
                                return true;
                            }
                        };
                MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic logic = ctrl.getTestLogic();
                logic.setAllowOverclocking(false);
                logic.setThreadLimit(threads);
                String tag = threads + "_" + bypassControllerFluidView;
                MetaTileEntityItemBus inBus = new MetaTileEntityItemBus(
                        new ResourceLocation("gregtech", "test_rb_aux250_in_" + tag), 1, false);
                MetaTileEntityItemBus outBus = new MetaTileEntityItemBus(
                        new ResourceLocation("gregtech", "test_rb_aux250_out_" + tag), 1, true);
                ctrl.addPart(inBus);
                ctrl.addPart(outBus);
                ctrl.addPart(new TestPaintableFluidHatchPart("test_rb_aux250_h_" + tag, drainOnly, null));
                long startEnergy = ctrl.getEnergyContainer().getEnergyStored();

                // Only 1,000 mB supplied (needs 1,000 + 250 = 1,250 mB)
                inBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
                for (int t = 0; t < 9; t++) {
                    logic.updateWorkable();
                }
                assertEquals(bypassControllerFluidView ? 750 : 1000, drainOnly.getFluidAmount());
                assertEquals(1, inBus.getImportItems().getStackInSlot(0).getCount());
                assertEquals(0, outBus.getExportItems().getStackInSlot(0).getCount());
                assertEquals(startEnergy, ctrl.getEnergyContainer().getEnergyStored());

                // Supplying the missing 250 mB (total 1,250 mB supplied) consumes all 1,250 mB and outputs 1 Diamond
                drainOnly.setFluid(new FluidStack(FluidRegistry.WATER, bypassControllerFluidView ? 1000 : 1250));
                ctrl.addNotifiedInput(inBus.getImportItems());
                for (int t = 0; t < 6; t++) {
                    logic.updateWorkable();
                }
                assertEquals(0, drainOnly.getFluidAmount());
                assertEquals(0, inBus.getImportItems().getStackInSlot(0).getCount());
                assertEquals(1, outBus.getExportItems().getStackInSlot(0).getCount());
                assertEquals(startEnergy - 120L, ctrl.getEnergyContainer().getEnergyStored());
            }
        }
    }

    private static MultiblockRecipeLockAndInvalidationTest.TestController buildRedBlueController(
            String suffix,
            FluidTank redTank, MetaTileEntityItemBus redBus,
            FluidTank blueTank, MetaTileEntityItemBus blueBus,
            MetaTileEntityItemBus outBus, AtomicBoolean blockRedOnCommit) {
        MultiblockRecipeLockAndInvalidationTest.TestController ctrl =
                new MultiblockRecipeLockAndInvalidationTest.TestController(RED_BLUE_WATER_MAP) {

                    @Override
                    public boolean checkRecipe(@NotNull Recipe recipe, boolean consumeIfSuccess) {
                        boolean isRedRecipe = recipe.getInputs().size() == 2;
                        if (!isRedRecipe) return true;
                        IItemHandlerModifiable redInv = redBus.getImportItems();
                        for (int i = 0; i < redInv.getSlots(); i++) {
                            ItemStack s = redInv.getStackInSlot(i);
                            if (!s.isEmpty() && s.getItem() == Items.REDSTONE) {
                                if (consumeIfSuccess) {
                                    return redInv.extractItem(i, 1, false).getCount() == 1;
                                }
                                return true;
                            }
                        }
                        if (consumeIfSuccess && blockRedOnCommit.get()) {
                            redTank.setCanFill(false);
                        }
                        return false;
                    }
                };
        ctrl.setDistinct(true);
        ctrl.getTestLogic().setAllowOverclocking(false);
        ctrl.getTestLogic().setThreadLimit(2);
        redBus.setPaintingColor(EnumDyeColor.RED.getColorValue());
        blueBus.setPaintingColor(EnumDyeColor.BLUE.getColorValue());
        ctrl.addPart(redBus);
        ctrl.addPart(blueBus);
        ctrl.addPart(new TestPaintableFluidHatchPart("test_rb_red_hatch_" + suffix, redTank, EnumDyeColor.RED));
        ctrl.addPart(new TestPaintableFluidHatchPart("test_rb_blue_hatch_" + suffix, blueTank, EnumDyeColor.BLUE));
        ctrl.addPart(outBus);
        return ctrl;
    }

    @Test
    void reloadedColorChannelPendingFluidStaysIsolatedFromBlueChannelUnderBothFillSettings() {
        FluidTank origRedTank = new FluidTank(new FluidStack(FluidRegistry.WATER, 1000), 1000);
        FluidTank origBlueTank = new FluidTank(1000);
        MetaTileEntityItemBus origRedBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_orig_red_bus"), 2, false);
        MetaTileEntityItemBus origBlueBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_orig_blue_bus"), 2, false);
        MetaTileEntityItemBus origOutBus = new MetaTileEntityItemBus(
                new ResourceLocation("gregtech", "test_rb_orig_out_bus"), 2, true);
        AtomicBoolean blockRed = new AtomicBoolean(true);
        MultiblockRecipeLockAndInvalidationTest.TestController origCtrl = buildRedBlueController(
                "orig", origRedTank, origRedBus, origBlueTank, origBlueBus, origOutBus, blockRed);
        MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic origLogic = origCtrl.getTestLogic();

        // Tick 1: Red fails auxiliary Redstone and disables Red tank refill -> 1,000 mB saved as Red pending rollback
        origRedBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        origRedBus.getImportItems().setStackInSlot(1, new ItemStack(Items.REDSTONE, 1));
        origLogic.updateWorkable();
        assertEquals(0, origRedTank.getFluidAmount());
        assertEquals(0, origBlueTank.getFluidAmount());
        NBTTagCompound savedLogicNbt = origLogic.serializeNBT();

        // Live controller without reload: Blue cannot use Red's pending 1,000 mB Water
        origRedBus.getImportItems().setStackInSlot(0, ItemStack.EMPTY);
        origRedBus.getImportItems().setStackInSlot(1, ItemStack.EMPTY);
        origBlueBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
        origCtrl.addNotifiedInput(origBlueBus.getImportItems());
        for (int t = 0; t < 6; t++) origLogic.updateWorkable();
        assertEquals(1, origBlueBus.getImportItems().getStackInSlot(0).getCount());
        assertEquals(0, origOutBus.getExportItems().getStackInSlot(0).getCount());

        // Reload into fresh identical controllers with Blue canFill=true AND Blue canFill=false
        for (boolean blueCanFill : new boolean[] { true, false }) {
            FluidTank reloadRedTank = new FluidTank(1000);
            reloadRedTank.setCanFill(false);
            FluidTank reloadBlueTank = new FluidTank(1000);
            reloadBlueTank.setCanFill(blueCanFill);
            MetaTileEntityItemBus reloadRedBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_rel_red_" + blueCanFill), 2, false);
            MetaTileEntityItemBus reloadBlueBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_rel_blue_" + blueCanFill), 2, false);
            MetaTileEntityItemBus reloadOutBus = new MetaTileEntityItemBus(
                    new ResourceLocation("gregtech", "test_rb_rel_out_" + blueCanFill), 2, true);
            MultiblockRecipeLockAndInvalidationTest.TestController reloadCtrl = buildRedBlueController(
                    "rel_" + blueCanFill, reloadRedTank, reloadRedBus, reloadBlueTank, reloadBlueBus, reloadOutBus,
                    new AtomicBoolean(false));
            MultiblockRecipeLockAndInvalidationTest.TestMultiblockRecipeLogic reloadLogic = reloadCtrl.getTestLogic();
            long reloadStartEnergy = reloadCtrl.getEnergyContainer().getEnergyStored();

            reloadLogic.deserializeNBT(savedLogicNbt.copy());
            reloadBlueBus.getImportItems().setStackInSlot(0, new ItemStack(Items.COAL, 1));
            reloadCtrl.addNotifiedInput(reloadBlueBus.getImportItems());
            for (int t = 0; t < 8; t++) {
                reloadLogic.updateWorkable();
            }
            assertEquals(0, reloadBlueTank.getFluidAmount());
            assertEquals(0, reloadRedTank.getFluidAmount());
            assertEquals(1, reloadBlueBus.getImportItems().getStackInSlot(0).getCount());
            assertEquals(0, reloadOutBus.getExportItems().getStackInSlot(0).getCount());
            assertEquals(reloadStartEnergy, reloadCtrl.getEnergyContainer().getEnergyStored());

            // Red channel still owns its 1,000 mB Water after reload and completes when supplied 1 Iron + 2 Redstone
            reloadRedBus.getImportItems().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
            reloadRedBus.getImportItems().setStackInSlot(1, new ItemStack(Items.REDSTONE, 2));
            reloadCtrl.addNotifiedInput(reloadRedBus.getImportItems());
            for (int t = 0; t < 6; t++) {
                reloadLogic.updateWorkable();
            }
            assertEquals(1, reloadBlueBus.getImportItems().getStackInSlot(0).getCount());
            assertEquals(Items.DIAMOND, reloadOutBus.getExportItems().getStackInSlot(0).getItem());
            assertEquals(1, reloadOutBus.getExportItems().getStackInSlot(0).getCount());
        }
    }
}
