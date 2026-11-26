package gregtech.api.recipes.properties;

import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.properties.impl.DimensionProperty;

import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipePropertyStorageTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    void dimensionPropertyRoundTripsNbtAndIsolatesCopiedStorage() {
        SimpleRecipeBuilder builder = new SimpleRecipeBuilder()
                .dimension(0, false)
                .dimension(7, false)
                .dimension(-1, true)
                .dimension(1, true);
        DimensionProperty.DimensionPropertyList originalDims = builder.getCompleteDimensionIDs();
        assertNotNull(originalDims);

        RecipePropertyStorageImpl saved = new RecipePropertyStorageImpl();
        assertTrue(saved.store(DimensionProperty.getInstance(), originalDims));
        NBTTagCompound nbt = saved.serializeNBT();

        RecipePropertyStorageImpl loaded = new RecipePropertyStorageImpl();
        loaded.deserializeNBT(nbt);
        RecipePropertyStorage copied = loaded.copy();
        assertEquals(1, copied.size());

        DimensionProperty.DimensionPropertyList loadedList =
                loaded.get(DimensionProperty.getInstance(), null);
        DimensionProperty.DimensionPropertyList copiedList =
                copied.get(DimensionProperty.getInstance(), null);
        assertNotNull(loadedList);
        assertNotNull(copiedList);

        assertEquals(Arrays.asList(0, 7), loadedList.whiteListDimensions);
        assertEquals(Arrays.asList(-1, 1), loadedList.blackListDimensions);
        assertEquals(Arrays.asList(0, 7), copiedList.whiteListDimensions);
        assertEquals(Arrays.asList(-1, 1), copiedList.blackListDimensions);
        assertTrue(copiedList.checkDimension(0));
        assertFalse(copiedList.checkDimension(-1));

        // Mutating copied list must not pollute loaded list
        copiedList.add(42, false);
        copiedList.add(-99, true);
        assertEquals(Arrays.asList(0, 7), loadedList.whiteListDimensions);
        assertEquals(Arrays.asList(-1, 1), loadedList.blackListDimensions);
    }
}
