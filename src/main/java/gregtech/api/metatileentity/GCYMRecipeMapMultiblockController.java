package gregtech.api.metatileentity;

import gregtech.api.capability.IAccelerateMultiblock;
import gregtech.api.capability.IOverclockMultiblock;
import gregtech.api.capability.IParallelMultiblock;
import gregtech.api.capability.impl.GCYMMultiblockRecipeLogic;
import gregtech.api.metatileentity.multiblock.MultiMapMultiblockController;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.util.GTUtility;
import gregtech.api.util.tooltips.GGCYMMMultiblockInformation;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class GCYMRecipeMapMultiblockController extends MultiMapMultiblockController
        implements IParallelMultiblock, IOverclockMultiblock, IAccelerateMultiblock {

    public GCYMRecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        this(metaTileEntityId, new RecipeMap<?>[]{recipeMap});
    }

    public GCYMRecipeMapMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?>[] recipeMaps) {
        super(metaTileEntityId, recipeMaps);
        this.recipeMapWorkable = new GCYMMultiblockRecipeLogic(this);
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        TooltipBuilder.create()
                .addIf(isParallel(), new GGCYMMMultiblockInformation())
                .addIf(ConfigHolder.globalMultiblocks.enableTieredCasings && isTiered(), new TiredMultiblockInformation())
                .build(this, tooltip);
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
                })
                .createOverclockButton((guiData, syncManager) -> {
                    var throttlePanel = syncManager.syncedPanel("overclock_panel", true, this::createOverclockThrottlePanel);
                    return new ButtonWidget<>()
                            .size(18)
                            .overlay(GTGuiTextures.OVERLAY_NO_FLEX.asIcon().size(16))
                            .addTooltipLine(IKey.lang("设备超频调整"))
                            .onMousePressed(mouseButton -> {
                                if (throttlePanel.isPanelOpen()) {
                                    throttlePanel.closePanel();
                                } else {
                                    throttlePanel.openPanel();
                                }
                                return true;
                            });
                })
                .createAccelerateButton((guiData, syncManager) -> {
                    var throttlePanel = syncManager.syncedPanel("accelerate_panel", true, this::createAccelerateThrottlePanel);
                    return new ButtonWidget<>()
                            .size(18)
                            .overlay(GTGuiTextures.OVERLAY_NO_FLEX.asIcon().size(16))
                            .addTooltipLine(IKey.lang("设备加速调整"))
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

    // 线程节流面板
    protected ModularPanel createParallelThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        IntSyncValue currentParallelValue = new IntSyncValue(this::getParallel, this::setParallel);
        syncManager.syncValue("currentParallelValue", currentParallelValue);

        IntSyncValue maxParallelValue = new IntSyncValue(
                this::getMaxParallel,
                value -> {}
        );
        syncManager.syncValue("maxParallelValue", maxParallelValue);

        return GTGuis.createPopupPanel("Parallel_throttle", 200, 60)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("机器线程设置")
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

    // 超频节流面板
    protected ModularPanel createOverclockThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        IntSyncValue currentDivisorValue = new IntSyncValue(
                () -> this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).isEmpty() ? 2 :
                        this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).get(0).getCurrentDivisor(),
                divisor -> {
                    if (!this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).isEmpty()) {
                        this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).get(0).setCurrentDivisor(divisor);
                    }
                });
        syncManager.syncValue("currentDivisorValue", currentDivisorValue);

        IntSyncValue maxDivisorValue = new IntSyncValue(
                () -> this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).isEmpty() ? 2 :
                        this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).get(0).getMaxDivisor(),
                value -> {});
        syncManager.syncValue("maxDivisorValue", maxDivisorValue);

        return GTGuis.createPopupPanel("overclock_throttle", 200, 60)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("机器超频设置")
                                .asWidget()
                                .heightRel(1.0f)))

                .child(Flow.row()
                        .top(24)
                        .height(20)
                        .child(new ButtonWidget<>()
                                .left(10).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("减小耗时除数")))
                                .onMousePressed(mouseButton -> {
                                    currentDivisorValue.setValue(MathHelper.clamp(
                                            currentDivisorValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 2,
                                            maxDivisorValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new ButtonWidget<>()
                                .left(110).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大耗时除数")))
                                .onMousePressed(mouseButton -> {
                                    currentDivisorValue.setValue(MathHelper.clamp(
                                            currentDivisorValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)), 2,
                                            maxDivisorValue.getValue()));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(true))))
                );
    }

    // 加速节流面板
    protected ModularPanel createAccelerateThrottlePanel(PanelSyncManager syncManager, IPanelHandler syncHandler) {
        IntSyncValue currentPercentageValue = new IntSyncValue(
                () -> this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).isEmpty() ? 100 :
                        this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).get(0).getCurrentPercentage(),
                percentage -> {
                    if (!this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).isEmpty()) {
                        this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).get(0).setCurrentPercentage(percentage);
                    }
                });
        syncManager.syncValue("currentPercentageValue", currentPercentageValue);

        IntSyncValue minPercentageValue = new IntSyncValue(
                () -> this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).isEmpty() ? 100 :
                        this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).get(0).getMinPercentage(),
                value -> {});
        syncManager.syncValue("minPercentageValue", minPercentageValue);

        return GTGuis.createPopupPanel("accelerate_throttle", 200, 60)
                .child(Flow.row()
                        .pos(4, 4)
                        .height(16)
                        .coverChildrenWidth()
                        .child(new ItemDrawable(getStackForm())
                                .asWidget()
                                .size(16)
                                .marginRight(4))
                        .child(IKey.lang("机器加速设置")
                                .asWidget()
                                .heightRel(1.0f)))

                .child(Flow.row()
                        .top(24)
                        .height(20)
                        .child(new ButtonWidget<>()
                                .left(10).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("减小耗时百分比")))
                                .onMousePressed(mouseButton -> {
                                    currentPercentageValue.setValue(MathHelper.clamp(
                                            currentPercentageValue.getValue() -
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)),
                                            minPercentageValue.getValue(), 100));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(false)))
                        )
                        .child(new ButtonWidget<>()
                                .left(110).widthRel(0.4f)
                                .height(20)
                                .tooltip(tooltip -> tooltip
                                        .addLine(IKey.lang("增大耗时百分比")))
                                .onMousePressed(mouseButton -> {
                                    currentPercentageValue.setValue(MathHelper.clamp(
                                            currentPercentageValue.getValue() +
                                                    GTUtility.getIncrementValue(MouseData.create(mouseButton)),
                                            minPercentageValue.getValue(), 100));
                                    return true;
                                })
                                .onUpdateListener(widget -> widget.overlay(GTUtility.createAdjustOverlay(true))))
                );
    }

    @Override
    public boolean isParallel() {
        return true;
    }

    @Override
    public int getParallel() {
        return this.getAbilities(MultiblockAbility.PARALLEL_HATCH).isEmpty() ? 1 :
                this.getAbilities(MultiblockAbility.PARALLEL_HATCH).get(0).getCurrentParallel();
    }

    @Override
    public void setParallel(int thread) {
        if(!this.getAbilities(MultiblockAbility.PARALLEL_HATCH).isEmpty()){
            this.getAbilities(MultiblockAbility.PARALLEL_HATCH).get(0).setCurrentParallel(thread);
        }
    }

    @Override
    public int getMaxParallel() {
        return this.getAbilities(MultiblockAbility.PARALLEL_HATCH).isEmpty() ? 1 :
                this.getAbilities(MultiblockAbility.PARALLEL_HATCH).get(0).getMaxParallel();
    }

    @Override
    public int getOverclockDurationDivisor() {
        return this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).isEmpty() ? 0 :
                this.getAbilities(MultiblockAbility.OVERCLOCK_HATCH).get(0).getCurrentDivisor();
    }

    @Override
    public float getAccelerateMultiplier(int recipeTier) {
        if (this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).isEmpty()) {
            return 1.0f;
        }
        return this.getAbilities(MultiblockAbility.ACCELERATE_HATCH).get(0).getEffectiveMultiplier(recipeTier);
    }

    public boolean isTiered() {
        return ConfigHolder.globalMultiblocks.enableTieredCasings;
    }


}
