package gregtech.common.item.behaviors;

import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IItemDurabilityManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A reactor-grid component that records neutron exposure and is exported once
 * its configured exposure is reached.
 */
public class IrradiationTargetBehavior extends NuclearComponentBehavior {

    private static final String REACTOR_DATA_TAG = "ReactorData";
    private static final String EXPOSURE_TAG = "IrradiationExposure";

    private final int requiredExposure;
    private final int minimumNeutronFlux;
    private final ItemStack irradiatedProduct;

    public IrradiationTargetBehavior(int requiredExposure, int minimumNeutronFlux,
                                     ItemStack irradiatedProduct) {
        super(Math.max(1, requiredExposure));
        this.requiredExposure = Math.max(1, requiredExposure);
        this.minimumNeutronFlux = Math.max(0, minimumNeutronFlux);
        this.irradiatedProduct = irradiatedProduct.copy();
    }

    @Nullable
    public static IrradiationTargetBehavior getInstanceFor(ItemStack itemStack) {
        if (itemStack.isEmpty() || !(itemStack.getItem() instanceof MetaItem)) return null;

        MetaItem<?>.MetaValueItem valueItem = ((MetaItem<?>) itemStack.getItem()).getItem(itemStack);
        if (valueItem == null) return null;

        IItemDurabilityManager durabilityManager = valueItem.getDurabilityManager();
        if (!(durabilityManager instanceof IrradiationTargetBehavior)) return null;

        return (IrradiationTargetBehavior) durabilityManager;
    }

    public boolean advanceExposure(ItemStack stack, int neutronFlux) {
        if (neutronFlux < minimumNeutronFlux) return false;

        int exposure = Math.min(requiredExposure, getExposure(stack) + neutronFlux);
        stack.getOrCreateSubCompound(REACTOR_DATA_TAG).setInteger(EXPOSURE_TAG, exposure);
        return exposure >= requiredExposure;
    }

    public ItemStack getIrradiatedProduct() {
        return irradiatedProduct.copy();
    }

    public int getRequiredExposure() {
        return requiredExposure;
    }

    public int getMinimumNeutronFlux() {
        return minimumNeutronFlux;
    }

    public int getExposure(ItemStack stack) {
        NBTTagCompound reactorData = stack.getSubCompound(REACTOR_DATA_TAG);
        return reactorData == null ? 0 : Math.min(requiredExposure, reactorData.getInteger(EXPOSURE_TAG));
    }

    @Override
    public boolean applyDamage(ItemStack itemStack, int damageApplied) {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, List<String> lines) {
        lines.add(I18n.format("gregtech.nuclear_reactor.irradiation.minimum_flux", minimumNeutronFlux));
        lines.add(I18n.format("gregtech.nuclear_reactor.irradiation.progress", getExposure(stack), requiredExposure));
    }
}
