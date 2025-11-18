package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.recipes.RecipeMap;

import net.minecraft.client.resources.I18n;

import java.util.List;
import java.util.Objects;

public class RecipeComponent extends AbstractTooltipComponent {
    private final RecipeMap<?> recipeMap;

    public RecipeComponent() {
        this(null);
    }

    public RecipeComponent(RecipeMap<?> recipeMap) {
        this.recipeMap = recipeMap;
    }

    @Override
    public void addInformation(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        if(metaTileEntity instanceof MultiblockControllerBase) {
            String recipeName = recipeMap != null ?
                    recipeMap.getLocalizedName() : metaTileEntity.recipeMapsToString();
            if(Objects.equals(recipeName, ""))return;
            tooltip.add(I18n.format("gregtech.multiblock.multiple_recipemaps_recipes.tooltip", recipeName));
        }
    }
}
