package gregtech.api.recipes.builders;

import gregtech.api.GTValues;
import gregtech.api.items.metaitem.MetaItem;
import gregtech.api.items.metaitem.stats.IDataItem;
import gregtech.api.items.metaitem.stats.IItemBehaviour;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTStringUtils;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;

public abstract class ResearchRecipeBuilder<T extends ResearchRecipeBuilder<T>> {

    protected ItemStack researchStack;
    protected ItemStack dataStack;
    protected boolean ignoreNBT;
    protected String researchId;
    protected long eut;

    public T researchStack(@NotNull ItemStack researchStack) {
        if (!researchStack.isEmpty()) {
            this.researchStack = researchStack;
            this.ignoreNBT = true;
        }
        return (T) this;
    }

    public T researchStack(@NotNull ItemStack researchStack, boolean ignoreNBT) {
        if (!researchStack.isEmpty()) {
            this.researchStack = researchStack;
            this.ignoreNBT = ignoreNBT;
        }
        return (T) this;
    }

    public T dataStack(@NotNull ItemStack dataStack) {
        if (!dataStack.isEmpty()) {
            this.dataStack = dataStack;
        }
        return (T) this;
    }

    public T researchId(String researchId) {
        this.researchId = researchId;
        return (T) this;
    }

    public T EUt(long eut) {
        this.eut = eut;
        return (T) this;
    }

    protected void validateResearchItem() {
        if (researchStack == null) {
            throw new IllegalArgumentException("Research stack cannot be null or empty!");
        }

        if (researchId == null) {
            researchId = GTStringUtils.itemStackToString(researchStack);
        }

        if (dataStack == null) {
            dataStack = getDefaultDataItem();
        }

        boolean foundBehavior = false;
        if (dataStack.getItem() instanceof MetaItem<?>metaItem) {
            for (IItemBehaviour behaviour : metaItem.getBehaviours(dataStack)) {
                if (behaviour instanceof IDataItem) {
                    foundBehavior = true;
                    dataStack = dataStack.copy();
                    dataStack.setCount(1);
                    break;
                }
            }
        }
        if (!foundBehavior) {
            throw new IllegalArgumentException("Data ItemStack must have the IDataItem behavior");
        }
    }

    protected abstract ItemStack getDefaultDataItem();

    protected abstract AssemblyLineRecipeBuilder.ResearchRecipeEntry build();

    public static class ScannerRecipeBuilder extends ResearchRecipeBuilder<ScannerRecipeBuilder> {

        public static final int DEFAULT_SCANNER_DURATION = 1200; // 60 secs
        public static final int DEFAULT_SCANNER_EUT = GTValues.VA[GTValues.HV];

        private int duration;

        ScannerRecipeBuilder() {/**/}

        public ScannerRecipeBuilder duration(int duration) {
            this.duration = duration;
            return this;
        }

        @Override
        protected ItemStack getDefaultDataItem() {
            return AssemblyLineManager.getDefaultScannerItem();
        }

        @Override
        protected AssemblyLineRecipeBuilder.ResearchRecipeEntry build() {
            validateResearchItem();
            if (duration <= 0) duration = DEFAULT_SCANNER_DURATION;
            if (eut <= 0) eut = DEFAULT_SCANNER_EUT;
            return new AssemblyLineRecipeBuilder.ResearchRecipeEntry(researchId, researchStack, dataStack, ignoreNBT,
                    duration,
                    eut, 0);
        }
    }

    public static class StationRecipeBuilder extends ResearchRecipeBuilder<StationRecipeBuilder> {

        public static final int DEFAULT_STATION_EUT = GTValues.VA[GTValues.LuV];
        // By default, the total CWU needed will be 200 seconds if exactly enough CWU/t is provided.
        // Providing more CWU/t will allow it to take less time.
        public static final int DEFAULT_STATION_TOTAL_CWUT = 4000;

        private int cwut;
        private int totalCWU;

        StationRecipeBuilder() {/**/}

        public StationRecipeBuilder CWUt(int cwut) {
            this.cwut = cwut;
            this.totalCWU = cwut * DEFAULT_STATION_TOTAL_CWUT;
            return this;
        }

        public StationRecipeBuilder CWUt(int cwut, int totalCWU) {
            this.cwut = cwut;
            this.totalCWU = totalCWU;
            return this;
        }

        @Override
        protected ItemStack getDefaultDataItem() {
            return AssemblyLineManager.getDefaultResearchStationItem(cwut);
        }

        @Override
        protected AssemblyLineRecipeBuilder.ResearchRecipeEntry build() {
            validateResearchItem();
            if (cwut <= 0 || totalCWU <= 0) {
                throw new IllegalArgumentException("CWU/t and total CWU must both be set, and non-zero!");
            }
            if (cwut > totalCWU) {
                throw new IllegalArgumentException("Total CWU cannot be greater than CWU/t!");
            }

            // "duration" is the total CWU/t.
            // Not called duration in API because logic does not treat it like normal duration.
            int duration = totalCWU;
            if (eut <= 0) eut = DEFAULT_STATION_EUT;

            return new AssemblyLineRecipeBuilder.ResearchRecipeEntry(researchId, researchStack, dataStack, ignoreNBT,
                    duration,
                    eut, cwut);
        }
    }
}
