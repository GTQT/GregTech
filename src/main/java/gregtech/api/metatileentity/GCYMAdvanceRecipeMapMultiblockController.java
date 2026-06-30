package gregtech.api.metatileentity;

import gregtech.api.capability.IParallelMultiblock;
import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.multiblock.AdvanceMultiMapMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.tooltips.GGCYMMMultiblockInformation;
import gregtech.api.util.tooltips.ThreadMultiblockInformation;
import gregtech.api.util.tooltips.TiredMultiblockInformation;
import gregtech.api.util.tooltips.TooltipBuilder;
import gregtech.common.ConfigHolder;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.MouseData;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class GCYMAdvanceRecipeMapMultiblockController extends AdvanceMultiMapMultiblockController
        implements IParallelMultiblock {

    public GCYMAdvanceRecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        this(metaTileEntityId, new RecipeMap<?>[]{recipeMap});
    }

    public GCYMAdvanceRecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?>[] recipeMaps) {
        super(metaTileEntityId, recipeMaps);
        recipeMapWorkable = new ArrayList<>();
        this.recipeMapWorkable.add(new GCYMMultiblockRecipeLogic(this));
    }

    @Override
    public void refreshThread(int currentThread) {
        if (currentThread == 0) return;
        if (!isActive()) {
            recipeMapWorkable = new ArrayList<>();
            for (int i = 0; i < currentThread; i++) {
                recipeMapWorkable.add(new GCYMMultiblockRecipeLogic(this));
            }
        }
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        return super.createUIFactory()
                .createParallelButton((guiData, syncManager) -> {
                    var throttlePanel = syncManager.syncedPanel("parallel_panel", true, this::createParallelThrottlePanel);
                    // 配置按钮 - 打开并行调整UI
                    return new ButtonWidget<>()
                            .size(18)
                            .overlay(GTGuiTextures.OVERLAY_PARALLEL.asIcon().size(16))
                            .addTooltipLine(IKey.lang("设备并行调整"))
                            .onMousePressed(mouseButton -> {
                                if (throttlePanel.isPanelOpen()) {
                                    throttlePanel.closePanel();
                                } else {
                                    throttlePanel.openPanel();
                                }
                                return true;
                            });
                });
    }

    // 并行面板
    protected ModularPanel createParallelThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        IntSyncValue currentParallelValue = new IntSyncValue(this::getParallel, this::setParallel);
        syncManager.syncValue("currentParallelValue", currentParallelValue);

        IntSyncValue maxParallelValue = new IntSyncValue(
                this::getMaxParallel,
                value -> {}
        );
        syncManager.syncValue("maxParallelValue", maxParallelValue);

        return GTGuis.createPopupPanel("parallel_throttle", 200, 60)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("机器并行设置")
                                .asWidget()
                                .heightRel(1.0f)))

                .child(Flow.row()
                        .top(24)
                        .height(20)
                        .child(new ButtonWidget<>()
                                .left(10).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("减小并行数量")))
                                .onMousePressed(mouseButton -> {
                                    currentParallelValue.setValue(MathHelper.clamp(
                                            currentParallelValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxParallelValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new ButtonWidget<>()
                                .left(110).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大并行数量")))
                                .onMousePressed(mouseButton -> {
                                    currentParallelValue.setValue(MathHelper.clamp(
                                            currentParallelValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 1,
                                            maxParallelValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(true))))
                );
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create()
                .addIf(isParallel(), new GGCYMMMultiblockInformation())
                .addIf(isParallel(), new ThreadMultiblockInformation())
                .addIf(ConfigHolder.globalMultiblocks.enableTieredCasings && isTiered(), new TiredMultiblockInformation())
                .build(this, tooltip);

    }

    @Override
    public boolean isParallel() {
        return true;
    }

    @Override
    public int getParallel() {
        return this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).isEmpty() ? 1 :
                this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).get(0).getCurrentParallel();
    }

    @Override
    public void setParallel(int thread) {
        if(!this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).isEmpty()){
            this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).get(0).setCurrentParallel(thread);
        }
    }

    @Override
    public int getMaxParallel() {
        return this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).isEmpty() ? 1 :
                this.getAbilities(GCYMMultiblockAbility.PARALLEL_HATCH).get(0).getMaxParallel();
    }

    public boolean isTiered() {
        return ConfigHolder.globalMultiblocks.enableTieredCasings;
    }
}
