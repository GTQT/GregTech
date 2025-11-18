package gregtech.api.util.tooltips;

import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.recipes.RecipeMap;

import java.util.ArrayList;
import java.util.List;

public class TooltipBuilder {
    private final List<ITooltipComponent> components = new ArrayList<>();

    // 添加预定义的组件
    public TooltipBuilder addStructure() {
        components.add(new StructureComponent());
        return this;
    }

    public TooltipBuilder addRecipe() {
        components.add(new RecipeComponent());
        return this;
    }

    public TooltipBuilder addParallel(int parallel) {
        components.add(new ParallelComponent(parallel));
        return this;
    }

    public TooltipBuilder addSteamMachine(int parallel) {
        components.add(new SteamMachineComponent(parallel));
        return this;
    }

    public TooltipBuilder addRecipe(RecipeMap<?> recipeMap) {
        components.add(new RecipeComponent(recipeMap));
        return this;
    }

    public TooltipBuilder addBatch() {
        components.add(new BatchComponent());
        return this;
    }

    public TooltipBuilder addLaser() {
        components.add(new LaserComponent());
        return this;
    }

    public TooltipBuilder addBlast() {
        components.add(new BlastComponent());
        return this;
    }

    public TooltipBuilder addPerfectOC() {
        components.add(new PerfectOCTooltipComponent());
        return this;
    }

    // 添加自定义组件
    public TooltipBuilder add(ITooltipComponent component) {
        components.add(component);
        return this;
    }

    // 添加条件组件
    public TooltipBuilder addIf(boolean condition, ITooltipComponent component) {
        components.add(new ConditionalComponent(condition, component));
        return this;
    }

    // 构建并执行
    public void build(MultiblockControllerBase metaTileEntity, List<String> tooltip) {
        for (ITooltipComponent component : components) {
            component.addInformation(metaTileEntity, tooltip);
        }
    }

    // 创建默认建造者
    public static TooltipBuilder create() {
        return new TooltipBuilder();
    }

    // 创建包含基础信息的建造者
    public static TooltipBuilder createDefault() {
        return new TooltipBuilder()
                .addStructure()
                .addRecipe();
    }
}
