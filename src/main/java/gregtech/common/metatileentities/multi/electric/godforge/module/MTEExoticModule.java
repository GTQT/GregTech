package gregtech.common.metatileentities.multi.electric.godforge.module;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.utils.serialization.ByteBufAdapters;
import com.cleanroommc.modularui.value.sync.GenericListSyncHandler;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.GodforgeRecipeMaps;
import gregtech.common.blocks.BlockGodforgeCasing;

public class MTEExoticModule extends MTEBaseModule {

    private boolean magmatterMode;
    private long ticker;
    private final List<ItemStack> exoticInputs = new ArrayList<>();
    private final List<ItemStack> possibleInputs = new ArrayList<>();

    public MTEExoticModule(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, GodforgeRecipeMaps.GODFORGE_EXOTIC_MATTER_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MTEExoticModule(metaTileEntityId);
    }

    @Override
    protected TraceabilityPredicate getCoilBlockPredicate() {
        return states(getCasingState(BlockGodforgeCasing.CasingType.TRANSCENDENTALLY_AMPLIFIED_MAGNETIC_CONFINEMENT_CASING));
    }

    public boolean isMagmatterModeOn() {
        return magmatterMode;
    }

    public void setMagmatterMode(boolean magmatterMode) {
        this.magmatterMode = magmatterMode;
    }

    public long getTicker() {
        return ticker;
    }

    public void setTicker(long ticker) {
        this.ticker = ticker;
    }

    public void refreshRecipe() {}

    public GenericListSyncHandler<ItemStack> getInputsSyncer() {
        return createItemStackListSyncer(exoticInputs);
    }

    public GenericListSyncHandler<ItemStack> getPossibleInputsSyncer() {
        return createItemStackListSyncer(possibleInputs);
    }

    private static GenericListSyncHandler<ItemStack> createItemStackListSyncer(List<ItemStack> source) {
        return GenericListSyncHandler.<ItemStack>builder()
            .getter(() -> source)
            .adapter(ByteBufAdapters.ITEM_STACK)
            .copy(stack -> stack == null ? ItemStack.EMPTY : stack.copy())
            .build();
    }

    public static class ExoticInputSlot extends ModularSlot {

        public ExoticInputSlot(int index, ItemStack stack) {
            super(new DisplayItemStackHandler(stack), 0);
            accessibility(false, false);
        }
    }

    public static class ExoticPossibleInputSlot extends ModularSlot {

        public ExoticPossibleInputSlot(int index, ItemStack stack) {
            super(new DisplayItemStackHandler(stack), 0);
            accessibility(false, false);
        }
    }

    private static final class DisplayItemStackHandler extends ItemStackHandler {

        private DisplayItemStackHandler(ItemStack stack) {
            super(1);
            setStackInSlot(0, stack == null ? ItemStack.EMPTY : stack.copy());
        }
    }
}
